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
 */
@Singleton
class DoubaoEmbeddingService @Inject constructor() : EmbeddingService {
    
    companion object {
        private const val TAG = "DoubaoEmbeddingService"
        private const val BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
        private const val DEFAULT_DIMENSION = 2048
        private const val DEFAULT_EMBEDDING_MODEL = "doubao-embedding"
        private const val CACHE_CAPACITY = 100
    }
    
    // LRU 缓存：文本 -> 向量
    private val embeddingCache = LruCache<String, FloatArray>(CACHE_CAPACITY)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private fun getApiKey(): String {
        // 优先尝试使用专门的 Embedding API Key
        val embeddingApiKey = try {
            val field = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_API_KEY")
            val key = field.get(null) as? String ?: ""
            if (key.isNotEmpty()) {
                Log.d(TAG, "Using dedicated Embedding API Key (length: ${key.length})")
            }
            key
        } catch (e: Exception) {
            ""
        }
        
        return if (embeddingApiKey.isNotEmpty()) {
            embeddingApiKey
        } else {
            // 复用 LLM 的 API Key
            BuildConfig.DOUBAO_API_KEY
        }
    }
    
    private fun getModelId(): String {
        // 1. 优先尝试新的端点配置
        try {
            val endpointField = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_ENDPOINT_ID")
            val endpointId = endpointField.get(null) as? String ?: ""
            if (endpointId.isNotEmpty()) {
                Log.d(TAG, "Using DOUBAO_EMBEDDING_ENDPOINT_ID: $endpointId")
                return endpointId
            }
        } catch (e: Exception) {
            // ignore
        }
        
        // 2. 回退到旧配置
        return try {
            val field = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_MODEL_ID")
            val modelId = field.get(null) as? String ?: ""
            if (modelId.isNotEmpty()) {
                modelId
            } else {
                DEFAULT_EMBEDDING_MODEL
            }
        } catch (e: Exception) {
            DEFAULT_EMBEDDING_MODEL
        }
    }
    
    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        // Step 1: 检查缓存
        embeddingCache.get(text)?.let { cachedEmbedding ->
            Log.d(TAG, "Cache hit for text: ${text.take(30)}...")
            return@withContext cachedEmbedding
        }
        
        val apiKey = getApiKey()
        val modelId = getModelId()
        
        if (apiKey.isEmpty()) {
            throw EmbeddingException("Embedding API Key 未配置")
        }
        
        // Step 2: 执行网络请求（带重试）
        val embedding: FloatArray = RetryPolicy.withRetry(
            maxRetries = 3,
            shouldRetry = { e -> 
                if (e is EmbeddingException) {
                    val msg = e.message ?: ""
                    // 不重试配置产生的错误
                    !(msg.contains("API Key") || msg.contains("400") || msg.contains("InvalidParameter"))
                } else {
                    true
                }
            }
        ) {
            executeEmbeddingRequest(text, apiKey, modelId)
        }
        
        // Step 3: 写入缓存
        embeddingCache.put(text, embedding)
        Log.d(TAG, "Cache miss, stored embedding (Dim: ${embedding.size})")
        
        embedding
    }
    
    /**
     * 执行实际的 Embedding API 请求
     * 策略：默认标准 API -> 失败则尝试多模态 API
     */
    private fun executeEmbeddingRequest(text: String, apiKey: String, modelId: String): FloatArray {
        try {
            return executeStandardEmbeddingRequest(text, apiKey, modelId)
        } catch (e: Exception) {
            Log.d(TAG, "Standard API failed, switching to Multimodal API... (${e.message})")
            try {
                return executeMultimodalEmbeddingRequest(text, apiKey, modelId)
            } catch (e2: Exception) {
                throw EmbeddingException("All embedding APIs failed (Std: ${e.message}, Multi: ${e2.message})", e)
            }
        }
    }

    private fun executeStandardEmbeddingRequest(text: String, apiKey: String, modelId: String): FloatArray {
        val url = "$BASE_URL/embeddings"
        val requestBody = JSONObject().apply {
            put("model", modelId)
            put("input", JSONArray().put(text))
        }.toString()
        return sendRequestAndParse(url, apiKey, requestBody, isMultimodal = false)
    }

    private fun executeMultimodalEmbeddingRequest(text: String, apiKey: String, modelId: String): FloatArray {
        val url = "$BASE_URL/embeddings/multimodal"
        val inputItem = JSONObject().apply {
            put("type", "text")
            put("text", text)
        }
        val requestBody = JSONObject().apply {
            put("model", modelId)
            put("input", JSONArray().put(inputItem))
        }.toString()
        return sendRequestAndParse(url, apiKey, requestBody, isMultimodal = true)
    }

    private fun sendRequestAndParse(url: String, apiKey: String, requestBody: String, isMultimodal: Boolean): FloatArray {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
            
        val response = client.newCall(request).execute()
        val responseBodyString = response.body?.string() ?: ""
        
        if (!response.isSuccessful) {
            throw EmbeddingException("HTTP ${response.code}: $responseBodyString")
        }
        
        val jsonResponse = JSONObject(responseBodyString)
        if (!jsonResponse.has("data")) {
            throw EmbeddingException("Missing 'data' field")
        }
        
        if (isMultimodal) {
            val dataObject = jsonResponse.getJSONObject("data")
            val embeddingArray = dataObject.getJSONArray("embedding")
            return parseEmbeddingArray(embeddingArray)
        } else {
            val dataArray = jsonResponse.getJSONArray("data")
            if (dataArray.length() == 0) throw EmbeddingException("Empty data array")
            val firstItem = dataArray.getJSONObject(0)
            val embeddingArray = firstItem.getJSONArray("embedding")
            return parseEmbeddingArray(embeddingArray)
        }
    }

    private fun parseEmbeddingArray(jsonArray: JSONArray): FloatArray {
        return FloatArray(jsonArray.length()) { i ->
            jsonArray.getDouble(i).toFloat()
        }
    }
}

class EmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause)
