package com.soulmate.core.data.memory

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType

/**
 * MemoryEntity - 记忆实体（ObjectBox）
 * 
 * 用于 RAG 检索的长期记忆存储
 * 
 * @property tag 记忆类型标签：user_input / ai_output / manual / summary
 *               兼容旧数据：若 tag 为空，则读取时把旧 emotion 当作 tag 兼容解析（user -> user_input, ai -> ai_output）
 * @property sessionId 可选，关联会话ID
 * @property emotionLabel 真实情绪标签（与旧 emotion 字段分离）
 */
@Entity
data class MemoryEntity(
    @Id
    var id: Long = 0,
    
    var text: String? = null,
    
    @HnswIndex(dimensions = 2048, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null,
    
    var timestamp: Long = 0,
    
    /** 旧字段：当前被当作 user/ai 标签使用，保留以兼容旧数据 */
    var emotion: String? = null,
    
    /** 新字段：记忆类型标签 (user_input / ai_output / manual / summary) */
    @Index
    var tag: String? = null,
    
    /** 新字段：关联会话ID（可选） */
    var sessionId: Long? = null,
    
    /** 新字段：真实情绪标签（如 happy, sad, excited 等） */
    var emotionLabel: String? = null
) {
    /**
     * 获取有效的 tag，兼容旧数据
     * 若 tag 为空，则把旧 emotion 当作 tag 兼容解析
     */
    fun getEffectiveTag(): String {
        if (!tag.isNullOrBlank()) return tag!!
        return when (emotion?.lowercase()) {
            "user" -> "user_input"
            "ai" -> "ai_output"
            else -> "unknown"
        }
    }
    // Generated equals() and hashCode() for array content matching if needed, 
    // but data class handles it somewhat. However, arrays in data class equals() use reference equality.
    // Overriding is good practice for tests, but for OB it's fine.
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemoryEntity

        if (id != other.id) return false
        if (text != other.text) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (timestamp != other.timestamp) return false
        if (emotion != other.emotion) return false
        if (tag != other.tag) return false
        if (sessionId != other.sessionId) return false
        return emotionLabel == other.emotionLabel
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (emotion?.hashCode() ?: 0)
        result = 31 * result + (tag?.hashCode() ?: 0)
        result = 31 * result + (sessionId?.hashCode() ?: 0)
        result = 31 * result + (emotionLabel?.hashCode() ?: 0)
        return result
    }
}
