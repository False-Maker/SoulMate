package com.soulmate.core.data.brain

import android.util.Log
import com.soulmate.BuildConfig
import com.soulmate.core.util.RetryPolicy
import com.soulmate.data.model.llm.ImageGenRequest
import com.soulmate.data.repository.ImageGenApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ImageGenService - 图片生成服务
 * 
 * 封装 Doubao 图片生成 API 调用，提供：
 * - 单图/多图生成
 * - 重试机制
 * - 错误处理
 * 
 * 使用的端点配置：DOUBAO_IMAGE_GEN_ENDPOINT_ID
 */
@Singleton
class ImageGenService @Inject constructor(
    private val imageGenApiService: ImageGenApiService
) {
    companion object {
        private const val TAG = "ImageGenService"
    }
    
    /**
     * 生成单张图片
     * 
     * @param prompt 生成提示词
     * @param size 图片尺寸（默认 1920x1920，最小要求 3,686,400 像素）
     * @return 生成的图片 URL
     * @throws ImageGenException 当生成失败时抛出
     */
    suspend fun generateImage(
        prompt: String,
        size: String = "1920x1920"
    ): String {
        val urls = generateImages(prompt, size, 1)
        return urls.firstOrNull() 
            ?: throw ImageGenException("未生成任何图片")
    }
    
    /**
     * 生成多张图片
     * 
     * @param prompt 生成提示词
     * @param size 图片尺寸（默认 1920x1920，最小要求 3,686,400 像素）
     * @param n 生成数量
     * @return 生成的图片 URL 列表
     * @throws ImageGenException 当生成失败时抛出
     */
    suspend fun generateImages(
        prompt: String,
        size: String = "1920x1920",
        n: Int = 1
    ): List<String> {
        val modelId = getModelId()
        
        if (modelId.isEmpty()) {
            throw ImageGenException("图片生成端点未配置，请设置 DOUBAO_IMAGE_GEN_ENDPOINT_ID")
        }
        
        Log.d(TAG, "Generating $n image(s) with prompt: ${prompt.take(50)}...")
        
        val request = ImageGenRequest(
            model = modelId,
            prompt = prompt,
            size = size,
            n = n
        )
        
        return RetryPolicy.withRetry(
            maxRetries = 2,
            shouldRetry = { e ->
                // 配置错误（404、未配置）不重试
                if (e is ImageGenException && (
                    e.message?.contains("端点配置错误") == true ||
                    e.message?.contains("端点不存在") == true ||
                    e.message?.contains("未配置") == true
                )) {
                    false
                } else {
                    true
                }
            }
        ) {
            executeGenerateRequest(request)
        }
    }
    
    /**
     * 获取图片生成端点 ID
     */
    private fun getModelId(): String {
        return try {
            val field = BuildConfig::class.java.getDeclaredField("DOUBAO_IMAGE_GEN_ENDPOINT_ID")
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 执行生成请求
     */
    private suspend fun executeGenerateRequest(request: ImageGenRequest): List<String> {
        val response = try {
            imageGenApiService.generateImage(request)
        } catch (e: Exception) {
            throw ImageGenException("网络请求失败: ${e.message}", e)
        }
        
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string() ?: "Unknown error"
            Log.e(TAG, "ImageGen API error: ${response.code()} - $errorBody")
            
            // 识别 404 配置错误
            if (response.code() == 404) {
                val friendlyMessage = if (errorBody.contains("InvalidEndpointOrModel.NotFound")) {
                    "图片生成端点配置错误，请检查 local.properties 中的 DOUBAO_IMAGE_GEN_ENDPOINT_ID 是否正确，或端点是否已过期"
                } else {
                    "图片生成端点不存在或无权访问（404），请检查配置"
                }
                throw ImageGenException(friendlyMessage)
            }
            
            throw ImageGenException("图片生成失败: ${response.code()} - $errorBody")
        }
        
        val body = response.body()
        
        // 检查 API 层面的错误
        body?.error?.let { error ->
            throw ImageGenException("API 错误: ${error.code} - ${error.message}")
        }
        
        val urls = body?.data?.mapNotNull { it.url } ?: emptyList()
        
        if (urls.isEmpty()) {
            throw ImageGenException("API 未返回图片 URL")
        }
        
        Log.d(TAG, "Successfully generated ${urls.size} image(s)")
        return urls
    }
}

/**
 * 图片生成服务异常
 */
class ImageGenException(message: String, cause: Throwable? = null) : Exception(message, cause)

