package com.soulmate.data.model.llm.content

import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * MessageContent 的 Gson 自定义适配器
 * 
 * 序列化规则：
 * - TextContent → JSON string: "hello"
 * - PartsContent → JSON array: [{"type":"text","text":"..."}, ...]
 * 
 * 反序列化规则：
 * - JSON string → TextContent
 * - JSON array → PartsContent
 */
class MessageContentJsonAdapter : JsonSerializer<MessageContent>, JsonDeserializer<MessageContent> {
    
    override fun serialize(
        src: MessageContent,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        return when (src) {
            is MessageContent.TextContent -> {
                // 纯文本 → JSON string
                JsonPrimitive(src.text)
            }
            is MessageContent.PartsContent -> {
                // 多模态 → JSON array
                val array = JsonArray()
                src.parts.forEach { part ->
                    val obj = serializeContentPart(part)
                    array.add(obj)
                }
                array
            }
        }
    }
    
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): MessageContent {
        return when {
            json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                // JSON string → TextContent
                MessageContent.TextContent(json.asString)
            }
            json.isJsonArray -> {
                // JSON array → PartsContent
                val parts = json.asJsonArray.mapNotNull { element ->
                    deserializeContentPart(element.asJsonObject)
                }
                MessageContent.PartsContent(parts)
            }
            else -> {
                // Fallback: 尝试解析为空文本
                MessageContent.TextContent("")
            }
        }
    }
    
    /**
     * 序列化单个 ContentPart
     */
    private fun serializeContentPart(part: ContentPart): JsonObject {
        val obj = JsonObject()
        obj.addProperty("type", part.type)
        
        when (part) {
            is TextPart -> {
                obj.addProperty("text", part.text)
            }
            is ImageUrlPart -> {
                val imageUrlObj = JsonObject().apply {
                    addProperty("url", part.imageUrl.url)
                    part.imageUrl.detail?.let { addProperty("detail", it) }
                }
                obj.add("image_url", imageUrlObj)
            }
            is VideoUrlPart -> {
                val videoUrlObj = JsonObject().apply {
                    addProperty("url", part.videoUrl.url)
                    part.videoUrl.fps?.let { addProperty("fps", it) }
                }
                obj.add("video_url", videoUrlObj)
            }
        }
        
        return obj
    }
    
    /**
     * 反序列化单个 ContentPart
     */
    private fun deserializeContentPart(obj: JsonObject): ContentPart? {
        val type = obj.get("type")?.asString ?: return null
        
        return when (type) {
            "text" -> {
                val text = obj.get("text")?.asString ?: ""
                TextPart(text)
            }
            "image_url" -> {
                val imageUrlObj = obj.getAsJsonObject("image_url") ?: return null
                val url = imageUrlObj.get("url")?.asString ?: return null
                val detail = imageUrlObj.get("detail")?.asString
                ImageUrlPart(ImageUrlPayload(url, detail))
            }
            "video_url" -> {
                val videoUrlObj = obj.getAsJsonObject("video_url") ?: return null
                val url = videoUrlObj.get("url")?.asString ?: return null
                val fps = videoUrlObj.get("fps")?.asInt
                VideoUrlPart(VideoUrlPayload(url, fps))
            }
            else -> null
        }
    }
}

