package com.soulmate.data.model

/**
 * UIEvent - 代表从 Avatar SDK 接收到的 UI 事件
 *
 * 事件格式参考 demo_speak_value.txt 中的 XML 结构：
 * ```xml
 * <uievent>
 *     <type>widget_video</type>
 *     <data>
 *         <video>https://...</video>
 *         <cover>https://...</cover>
 *         <axis_id>101</axis_id>
 *     </data>
 * </uievent>
 * ```
 *
 * @param type 事件类型，如 "widget_video", "widget_close"
 * @param data 事件数据，包含如 video URL, cover URL, axis_id 等
 * @param timestamp 事件创建时间戳
 */
data class UIEvent(
    val type: String,
    val data: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        // 事件类型常量 - 视频相关
        const val TYPE_WIDGET_VIDEO = "widget_video"
        const val TYPE_WIDGET_CLOSE = "widget_close"
        
        // 事件类型常量 - 记忆/照片相关（新增）
        const val TYPE_PHOTO = "photo"
        const val TYPE_MEMORY_CARD = "memory_card"
        const val TYPE_IMAGE = "image"
        
        // 数据字段常量
        const val KEY_VIDEO = "video"
        const val KEY_COVER = "cover"
        const val KEY_AXIS_ID = "axis_id"
        
        // 新增数据字段（记忆相关）
        const val KEY_IMAGE_URL = "image_url"
        const val KEY_TITLE = "title"
        const val KEY_DESCRIPTION = "description"
        const val KEY_MEMORY_ID = "memory_id"
        const val KEY_DATE = "date"
    }
    
    /**
     * 获取视频 URL
     */
    val videoUrl: String?
        get() = data[KEY_VIDEO]?.trim()
    
    /**
     * 获取封面图 URL
     */
    val coverUrl: String?
        get() = data[KEY_COVER]?.trim()
    
    /**
     * 获取 axis_id
     */
    val axisId: String?
        get() = data[KEY_AXIS_ID]?.trim()
    
    /**
     * 判断是否为视频事件
     */
    val isVideoEvent: Boolean
        get() = type == TYPE_WIDGET_VIDEO
    
    /**
     * 判断是否为关闭事件
     */
    val isCloseEvent: Boolean
        get() = type == TYPE_WIDGET_CLOSE
    
    /**
     * 判断是否为照片事件
     */
    val isPhotoEvent: Boolean
        get() = type == TYPE_PHOTO || type == TYPE_IMAGE
    
    /**
     * 判断是否为记忆卡片事件
     */
    val isMemoryCardEvent: Boolean
        get() = type == TYPE_MEMORY_CARD
    
    /**
     * 获取图片 URL（照片/记忆卡片）
     */
    val imageUrl: String?
        get() = data[KEY_IMAGE_URL]?.trim() ?: data[KEY_COVER]?.trim()
    
    /**
     * 获取标题
     */
    val title: String?
        get() = data[KEY_TITLE]?.trim()
    
    /**
     * 获取描述
     */
    val description: String?
        get() = data[KEY_DESCRIPTION]?.trim()
    
    /**
     * 获取记忆 ID
     */
    val memoryId: Long?
        get() = data[KEY_MEMORY_ID]?.toLongOrNull()
    
    /**
     * 获取日期
     */
    val date: String?
        get() = data[KEY_DATE]?.trim()
}
