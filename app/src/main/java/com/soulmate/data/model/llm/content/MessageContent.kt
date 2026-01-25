package com.soulmate.data.model.llm.content

/**
 * MessageContent - 消息内容的密封类
 * 
 * 支持两种形态：
 * 1. TextContent: 纯文本 → JSON 序列化为 string
 * 2. PartsContent: 多模态内容 → JSON 序列化为 object[]
 * 
 * 文档：https://www.volcengine.com/docs/82379/1494384
 */
sealed class MessageContent {
    
    /**
     * 纯文本内容
     * JSON: "content": "hello"
     */
    data class TextContent(val text: String) : MessageContent()
    
    /**
     * 多模态内容（文本 + 图片/视频）
     * JSON: "content": [{"type":"text","text":"..."}, {"type":"image_url","image_url":{...}}]
     */
    data class PartsContent(val parts: List<ContentPart>) : MessageContent()
    
    /**
     * 提取纯文本内容
     * - TextContent: 直接返回 text
     * - PartsContent: 提取所有 TextPart 的文本拼接
     */
    fun extractText(): String {
        return when (this) {
            is TextContent -> text
            is PartsContent -> parts.filterIsInstance<TextPart>()
                .joinToString("") { it.text }
        }
    }
    
    companion object {
        /**
         * 快捷创建纯文本内容
         */
        fun text(content: String): MessageContent = TextContent(content)
        
        /**
         * 快捷创建多模态内容
         */
        fun parts(vararg parts: ContentPart): MessageContent = PartsContent(parts.toList())
        
        /**
         * 快捷创建多模态内容
         */
        fun parts(parts: List<ContentPart>): MessageContent = PartsContent(parts)
    }
}

