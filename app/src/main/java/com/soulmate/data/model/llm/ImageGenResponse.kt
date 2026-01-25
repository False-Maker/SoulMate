package com.soulmate.data.model.llm

/**
 * 图片生成响应模型
 * 
 * 用于解析 Doubao /images/generations API 响应
 * 文档：https://www.volcengine.com/docs/82379/1541523
 */
data class ImageGenResponse(
    val created: Long? = null,
    val data: List<ImageData>? = null,
    val error: ImageGenError? = null
)

/**
 * 生成的图片数据
 * 
 * @param url 图片 URL
 * @param b64_json Base64 编码的图片数据（如果请求 response_format=b64_json）
 * @param revised_prompt 模型修订后的提示词（可选）
 */
data class ImageData(
    val url: String? = null,
    val b64_json: String? = null,
    val revised_prompt: String? = null
)

/**
 * API 错误信息
 */
data class ImageGenError(
    val code: String? = null,
    val message: String? = null
)

