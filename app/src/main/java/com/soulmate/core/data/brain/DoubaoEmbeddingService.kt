package com.soulmate.core.data.brain

import android.util.Log
import android.util.LruCache
import com.soulmate.BuildConfig
import com.soulmate.core.util.RetryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DoubaoEmbeddingService - 火山引擎 Doubao Embedding API 实现
 * 
 * 使用火山引擎方舟 API 调用 Embedding 模型，将文本转换为向量。
 * 支持多模态向量化 API（Doubao-embedding-vision）。
 * 
 * 特性：
 * - LRU 缓存（容量 100，缓存高频对话向量，减少 API 调用）
 * - 指数退避重试（网络波动时自动重试 3 次）
 * - 失败时抛出异常，不使用随机向量降级
 * 
 * 配置项（在 local.properties 中设置）：
 * - DOUBAO_EMBEDDING_ENDPOINT_ID: Embedding 模型的接入点 ID（例如 ep-xxx）
 * - DOUBAO_EMBEDDING_API_KEY: API Key（如果与 LLM 不同）
 * 
 * 如果未设置专门的 Embedding 配置，将复用 LLM 的 API Key。
 * 
 * 注意：当前使用多模态向量化 API（/embeddings/multimodal），
 * 不兼容 OpenAI SDK，请求和响应格式与标准 embeddings API 不同。
 */
@Singleton
class DoubaoEmbeddingService @Inject constructor() : EmbeddingService {
    
    companion object {
        private const val TAG = "DoubaoEmbeddingService"
        
        // 火山引擎方舟 API 基础 URL
        private const val BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
        
        // 默认输出维度（可配置）
        // 对于 doubao-embedding-vision 模型，输出 2048 维向量
        // 需要与 MemoryEntity 的 HNSW 索引维度保持一致
        private const val DEFAULT_DIMENSION = 2048
        
        // 使用 doubao-embedding 作为默认模型
        private const val DEFAULT_EMBEDDING_MODEL = "doubao-embedding"
        
        // LRU 缓存容量（缓存最近 100 条高频对话的向量）
        private const val CACHE_CAPACITY = 100
    }
    
    // LRU 缓存：文本 -> 向量
    // 用于缓存高频对话，避免重复 API 调用
    private val embeddingCache = LruCache<String, FloatArray>(CACHE_CAPACITY)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * 获取 API Key
     * 优先使用专门的 Embedding API Key，否则复用 LLM 的 API Key
     */
    private fun getApiKey(): String {
        // 首先尝试使用专门的 Embedding API Key
        val embeddingApiKey = try {
            val field = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_API_KEY")
            val key = field.get(null) as? String ?: ""
            if (key.isNotEmpty()) {
                Log.d(TAG, "Using dedicated Embedding API Key (length: ${key.length})")
            }
            key
        } catch (e: Exception) {
            Log.d(TAG, "DOUBAO_EMBEDDING_API_KEY not found, will fallback to DOUBAO_API_KEY")
            ""
        }
        
        return if (embeddingApiKey.isNotEmpty()) {
            embeddingApiKey
        } else {
            // 复用 LLM 的 API Key
            val fallbackKey = BuildConfig.DOUBAO_API_KEY
            if (fallbackKey.isNotEmpty()) {
                Log.d(TAG, "Using fallback DOUBAO_API_KEY (length: ${fallbackKey.length})")
            } else {
                Log.w(TAG, "Both DOUBAO_EMBEDDING_API_KEY and DOUBAO_API_KEY are empty!")
            }
            fallbackKey
        }
    }
    
    /**
     * 获取 Embedding 模型 ID
     * 
     * 策略（Phase 1 更新）：
     * 1. 优先使用 DOUBAO_EMBEDDING_ENDPOINT_ID（新配置，如 ep-xxx）
     * 2. 否则回退到 DOUBAO_EMBEDDING_MODEL_ID（旧配置）
     * 3. 否则使用默认 embedding 模型名
     */
    private fun getModelId(): String {
        // 优先尝试新的端点配置
        try {
            val endpointField = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_ENDPOINT_ID")
            val endpointId = endpointField.get(null) as? String ?: ""
            if (endpointId.isNotEmpty()) {
                Log.d(TAG, "Using DOUBAO_EMBEDDING_ENDPOINT_ID: $endpointId")
                return endpointId
            } else {
                Log.d(TAG, "DOUBAO_EMBEDDING_ENDPOINT_ID is empty, trying fallback")
            }
        } catch (e: Exception) {
            Log.d(TAG, "DOUBAO_EMBEDDING_ENDPOINT_ID field not found, trying fallback: ${e.message}")
        }
        
        // 回退到旧配置
        return try {
            val field = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_MODEL_ID")
            val modelId = field.get(null) as? String ?: ""
            if (modelId.isNotEmpty()) {
                Log.d(TAG, "Using DOUBAO_EMBEDDING_MODEL_ID: $modelId")
                modelId
            } else {
                Log.w(TAG, "DOUBAO_EMBEDDING_MODEL_ID is empty, using default: $DEFAULT_EMBEDDING_MODEL")
                DEFAULT_EMBEDDING_MODEL
            }
        } catch (e: Exception) {
            Log.w(TAG, "DOUBAO_EMBEDDING_MODEL_ID field not found, using default: $DEFAULT_EMBEDDING_MODEL (${e.message})")
            DEFAULT_EMBEDDING_MODEL
        }
    }
    
    /**
     * 将文本转换为 embedding 向量
     * 
     * 【LRU 缓存】优先从缓存获取，命中则跳过网络请求，显著降低 API 调用频率。
     * 
     * 【重要】失败时直接抛出异常，不再使用随机向量降级。
     * 理由：随机向量会污染数据库，导致 RAG 检索出无关记忆，且脏数据难以清洗。
     * 宁缺毋滥：宁可失败也不要存储错误数据。
     * 
     * 【重试机制】网络波动时自动重试 3 次，使用指数退避策略。
     * 
     * @param text 输入文本
     * @return 向量数组
     * @throws EmbeddingException 当 API Key 未配置或所有重试都失败时抛出
     */
    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        // Step 1: 检查缓存是否命中
        embeddingCache.get(text)?.let { cachedEmbedding ->
            Log.d(TAG, "Cache hit for text: ${text.take(30)}...")
            return@withContext cachedEmbedding
        }
        
        val apiKey = getApiKey()
        val modelId = getModelId()
        
        // 详细日志记录配置状态
        Log.d(TAG, "Embedding request - Model: $modelId, API Key length: ${apiKey.length}, Text length: ${text.length}")
        
        if (apiKey.isEmpty()) {
            val errorMsg = "Embedding API Key 未配置，请在 local.properties 中设置 DOUBAO_EMBEDDING_API_KEY（或复用 DOUBAO_API_KEY）"
            Log.e(TAG, errorMsg)
            throw EmbeddingException(errorMsg)
        }
        
        if (modelId.isEmpty() || modelId == DEFAULT_EMBEDDING_MODEL) {
            Log.w(TAG, "Warning: Using default model ID '$modelId', consider setting DOUBAO_EMBEDDING_ENDPOINT_ID in local.properties")
        }
        
        // Step 2: 缓存未命中，执行网络请求（带重试策略）
        val embedding = RetryPolicy.withRetry(
            maxRetries = 3,
            shouldRetry = { e -> 
                // 网络相关错误可重试，配置错误不重试
                if (e is EmbeddingException) {
                    val msg = e.message ?: ""
                    val nonRetryable = msg.contains("API Key") || msg.contains("InvalidParameter") || msg.contains("400")
                    !nonRetryable
                } else {
                    true
                }
            }
        ) {
            executeEmbeddingRequest(text, apiKey, modelId)
        }
        
        // Step 3: 请求成功后，写入缓存
        embeddingCache.put(text, embedding)
        Log.d(TAG, "Cache miss, stored embedding for text: ${text.take(30)}...")
        
        embedding
    }
    
    /**
     * 执行实际的 Embedding API 请求
     * 
     * 使用多模态向量化 API（/embeddings/multimodal）
     * 请求格式: {"model": "ep-xxx", "input": [{"type": "text", "text": "..."}]}
     * 响应格式: {"data": {"embedding": [...]}, ...}
     */
    private fun executeEmbeddingRequest(text: String, apiKey: String, modelId: String): FloatArray {
        val url = "$BASE_URL/embeddings/multimodal"
        
        // 构建多模态 API 的 input 格式
        val inputItem = JSONObject().apply {
            put("type", "text")
            put("text", text)
        }
        
        val requestBody = JSONObject().apply {
            put("model", modelId)
            put("input", JSONArray().put(inputItem))
        }.toString()
        
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        Log.d(TAG, "Calling embedding API: URL=$url, Model=$modelId, Text preview=${text.take(50)}...")
        
        val response = try {
            client.newCall(request).execute()
        } catch (e: java.net.UnknownHostException) {
            val errorMsg = "网络连接失败：无法解析主机名。请检查网络连接。"
            Log.e(TAG, errorMsg, e)
            throw EmbeddingException(errorMsg, e)
        } catch (e: java.net.SocketTimeoutException) {
            val errorMsg = "网络请求超时：服务器响应时间过长。"
            Log.e(TAG, errorMsg, e)
            throw EmbeddingException(errorMsg, e)
        } catch (e: java.io.IOException) {
            val errorMsg = "网络请求失败: ${e.message}"
            Log.e(TAG, errorMsg, e)
            throw EmbeddingException(errorMsg, e)
        } catch (e: Exception) {
            val errorMsg = "未知网络错误: ${e.javaClass.simpleName} - ${e.message}"
            Log.e(TAG, errorMsg, e)
            throw EmbeddingException(errorMsg, e)
        }
        
        val responseCode = response.code
        val responseBodyString = try {
            response.body?.string() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read response body: ${e.message}")
            ""
        }
        
        if (!response.isSuccessful) {
            val errorMsg = when (responseCode) {
                401 -> "API Key 认证失败（401）。请检查 DOUBAO_EMBEDDING_API_KEY 或 DOUBAO_API_KEY 是否正确。"
                403 -> "API Key 权限不足（403）。请检查 API Key 是否有 Embedding 服务权限。"
                404 -> "API 端点不存在（404）。请检查 DOUBAO_EMBEDDING_ENDPOINT_ID 是否正确。"
                429 -> "API 调用频率超限（429）。请稍后重试。"
                500, 502, 503 -> "服务器错误（$responseCode）。请稍后重试。"
                else -> "Embedding API 调用失败: HTTP $responseCode"
            }
            Log.e(TAG, "$errorMsg - Response body: ${responseBodyString.take(200)}")
            throw EmbeddingException("$errorMsg - 响应: ${responseBodyString.take(100)}")
        }
        
        if (responseBodyString.isEmpty()) {
            val errorMsg = "Embedding API 返回空响应"
            Log.e(TAG, errorMsg)
            throw EmbeddingException(errorMsg)
        }
        
        try {
            val jsonResponse = JSONObject(responseBodyString)
            
            // 检查响应格式
            if (!jsonResponse.has("data")) {
                Log.e(TAG, "Invalid response format: missing 'data' field. Response: ${responseBodyString.take(200)}")
                throw EmbeddingException("API 响应格式错误：缺少 'data' 字段")
            }
            
            val dataObject = jsonResponse.getJSONObject("data")
            
            if (!dataObject.has("embedding")) {
                Log.e(TAG, "Invalid response format: missing 'embedding' field. Response: ${responseBodyString.take(200)}")
                throw EmbeddingException("API 响应格式错误：缺少 'embedding' 字段")
            }
            
            val embeddingArray = dataObject.getJSONArray("embedding")
            
            if (embeddingArray.length() == 0) {
                Log.e(TAG, "Invalid response format: empty embedding array")
                throw EmbeddingException("API 响应格式错误：embedding 数组为空")
            }
            
            val embedding = FloatArray(embeddingArray.length()) { i ->
                embeddingArray.getDouble(i).toFloat()
            }
            
            Log.d(TAG, "Successfully generated embedding with ${embedding.size} dimensions")
            return embedding
        } catch (e: org.json.JSONException) {
            val errorMsg = "API 响应解析失败: ${e.message}"
            Log.e(TAG, "$errorMsg - Response: ${responseBodyString.take(200)}", e)
            throw EmbeddingException(errorMsg, e)
        } catch (e: Exception) {
            val errorMsg = "处理 API 响应时出错: ${e.message}"
            Log.e(TAG, "$errorMsg - Response: ${responseBodyString.take(200)}", e)
            throw EmbeddingException(errorMsg, e)
        }
    }
}

/**
 * Embedding 服务异常
 * 用于明确区分 Embedding 相关的错误，便于上层处理
 */
class EmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause)
