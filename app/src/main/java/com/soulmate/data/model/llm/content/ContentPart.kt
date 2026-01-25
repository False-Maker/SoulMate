package com.soulmate.data.model.llm.content

/**
 * ContentPart - 多模态消息的单个部分
 * 
 * 支持三种类型：
 * - text: 文本
 * - image_url: 图片 URL
 * - video_url: 视频 URL（预留）
 * 
 * 文档：https://www.volcengine.com/docs/82379/1494384
 */
sealed interface ContentPart {
    val type: String
}

/**
 * 文本部分
 * JSON: {"type":"text","text":"..."}
 */
data class TextPart(
    val text: String
) : ContentPart {
    override val type: String = "text"
}

/**
 * 图片 URL 部分
 * JSON: {"type":"image_url","image_url":{"url":"...","detail":"auto"}}
 */
data class ImageUrlPart(
    val imageUrl: ImageUrlPayload
) : ContentPart {
    override val type: String = "image_url"
    
    companion object {
        /**
         * 快捷创建图片部分
         * @param url 图片 URL（公网 URL 或 data:image/...;base64,... 格式）
         * @param detail 解析精度：auto/low/high（默认 low，更省 token/延迟）
         */
        fun create(url: String, detail: String = "low"): ImageUrlPart {
            return ImageUrlPart(ImageUrlPayload(url = url, detail = detail))
        }
    }
}

/**
 * 图片 URL 载荷
 */
data class ImageUrlPayload(
    val url: String,
    val detail: String? = "auto"
)

/**
 * 视频 URL 部分（预留，Phase 2）
 * JSON: {"type":"video_url","video_url":{"url":"...","fps":1}}
 */
data class VideoUrlPart(
    val videoUrl: VideoUrlPayload
) : ContentPart {
    override val type: String = "video_url"
    
    companion object {
        fun create(url: String, fps: Int = 1): VideoUrlPart {
            return VideoUrlPart(VideoUrlPayload(url = url, fps = fps))
        }
    }
}

/**
 * 视频 URL 载荷
 */
data class VideoUrlPayload(
    val url: String,
    val fps: Int? = 1
)

