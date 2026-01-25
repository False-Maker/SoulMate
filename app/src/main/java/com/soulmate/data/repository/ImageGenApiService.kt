package com.soulmate.data.repository

import com.soulmate.data.model.llm.ImageGenRequest
import com.soulmate.data.model.llm.ImageGenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit 接口：图片生成 API
 * 
 * 调用 Doubao /images/generations 端点生成图片
 * 文档：https://www.volcengine.com/docs/82379/1541523
 */
interface ImageGenApiService {
    
    /**
     * 生成图片
     * 
     * @param request 图片生成请求
     * @return 包含图片 URL 或 Base64 数据的响应
     */
    @POST("images/generations")
    suspend fun generateImage(@Body request: ImageGenRequest): Response<ImageGenResponse>
}

