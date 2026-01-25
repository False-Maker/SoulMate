package com.soulmate.data.model.llm

/**
 * 图片生成请求模型
 * 
 * 用于调用 Doubao /images/generations API
 * 文档：https://www.volcengine.com/docs/82379/1541523
 *
 * @param model 模型端点 ID（如 ep-xxx）
 * @param prompt 生成提示词
 * @param size 图片尺寸（如 "1920x1920"，最小要求 3,686,400 像素）
 * @param n 生成数量（默认 1）
 */
data class ImageGenRequest(
    val model: String,
    val prompt: String,
    val size: String = "1920x1920",
    val n: Int = 1
)

