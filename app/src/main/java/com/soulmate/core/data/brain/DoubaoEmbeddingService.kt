package com.soulmate.core.data.brain

import android.util.Log
import com.soulmate.BuildConfig
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
 * API 兼容 OpenAI Embeddings 格式。
 * 
 * 配置项（在 local.properties 中设置）：
 * - DOUBAO_EMBEDDING_MODEL_ID: Embedding 模型的接入点 ID（例如 ep-xxx）
 * - DOUBAO_EMBEDDING_API_KEY: API Key（如果与 LLM 不同）
 * 
 * 如果未设置专门的 Embedding 配置，将复用 LLM 的 API Key。
 */
@Singleton
class DoubaoEmbeddingService @Inject constructor() : EmbeddingService {
    
    companion object {
        private const val TAG = "DoubaoEmbeddingService"
        
        // 火山引擎方舟 API 基础 URL
        private const val BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
        
        // 默认输出维度（可配置）
        // 对于 doubao-embedding 模型，支持 512, 1024, 2048
        // 我们使用 1024 以兼容现有的 HNSW 索引配置（需要调整 MemoryEntity）
        private const val DEFAULT_DIMENSION = 1024
        
        // 使用 doubao-embedding 作为默认模型
        private const val DEFAULT_EMBEDDING_MODEL = "doubao-embedding"
    }
    
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
            field.get(null) as? String ?: ""
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
    
    /**
     * 获取 Embedding 模型 ID
     */
    private fun getModelId(): String {
        return try {
            val field = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_MODEL_ID")
            val modelId = field.get(null) as? String ?: ""
            if (modelId.isNotEmpty()) modelId else DEFAULT_EMBEDDING_MODEL
        } catch (e: Exception) {
            DEFAULT_EMBEDDING_MODEL
        }
    }
    
    /**
     * 将文本转换为 embedding 向量
     * 
     * @param text 输入文本
     * @return 向量数组
     */
    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val modelId = getModelId()
        
        if (apiKey.isEmpty()) {
            Log.w(TAG, "No API key configured, falling back to mock embedding")
            return@withContext generateMockEmbedding(text)
        }
        
        try {
            val requestBody = JSONObject().apply {
                put("model", modelId)
                put("input", JSONArray().put(text))
                // 请求指定维度（如果模型支持）
                put("encoding_format", "float")
                // 可选：指定维度
                // put("dimensions", DEFAULT_DIMENSION)
            }.toString()
            
            val request = Request.Builder()
                .url("$BASE_URL/embeddings")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "Calling embedding API with model: $modelId")
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Embedding API error: ${response.code} - $errorBody")
                return@withContext generateMockEmbedding(text)
            }
            
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val jsonResponse = JSONObject(responseBody)
            
            // 解析 embedding 结果
            // 格式: { "data": [{ "embedding": [0.1, 0.2, ...], "index": 0 }], ... }
            val dataArray = jsonResponse.getJSONArray("data")
            if (dataArray.length() == 0) {
                throw Exception("No embedding data in response")
            }
            
            val embeddingArray = dataArray.getJSONObject(0).getJSONArray("embedding")
            val embedding = FloatArray(embeddingArray.length()) { i ->
                embeddingArray.getDouble(i).toFloat()
            }
            
            Log.d(TAG, "Successfully generated embedding with ${embedding.size} dimensions")
            embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate embedding", e)
            // 降级到 mock embedding
            generateMockEmbedding(text)
        }
    }
    
    /**
     * 生成 mock embedding（作为降级方案）
     */
    private fun generateMockEmbedding(text: String): FloatArray {
        Log.w(TAG, "Using mock embedding for: ${text.take(50)}...")
        val seed = text.hashCode().toLong()
        val random = kotlin.random.Random(seed)
        return FloatArray(DEFAULT_DIMENSION) { 
            (random.nextFloat() * 2 - 1)
        }
    }
}
