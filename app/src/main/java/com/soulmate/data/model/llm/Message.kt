package com.soulmate.data.model.llm

import com.soulmate.data.model.llm.content.MessageContent

/**
 * Data class representing a chat message in OpenAI-compatible format.
 * 
 * 支持两种 content 格式（按火山引擎文档）：
 * 1. 纯文本：content 序列化为 JSON string
 * 2. 多模态：content 序列化为 JSON array（包含 text/image_url/video_url）
 * 
 * @param role The role of the message sender: "system", "user", or "assistant"
 * @param content The message content (text or multimodal parts)
 */
data class Message(
    val role: String,
    val content: MessageContent
) {
    companion object {
        /**
         * 快捷创建纯文本消息
         */
        fun text(role: String, content: String): Message {
            return Message(role = role, content = MessageContent.text(content))
        }
        
        /**
         * 快捷创建 system 消息
         */
        fun system(content: String): Message = text("system", content)
        
        /**
         * 快捷创建 user 消息
         */
        fun user(content: String): Message = text("user", content)
        
        /**
         * 快捷创建 assistant 消息
         */
        fun assistant(content: String): Message = text("assistant", content)
    }
}
