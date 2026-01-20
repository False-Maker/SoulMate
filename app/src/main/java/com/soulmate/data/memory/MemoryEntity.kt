package com.soulmate.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * MemoryEntity represents a single memory stored in the system.
 * This entity is used for RAG (Retrieval-Augmented Generation) to provide context
 * to the LLM during conversations.
 * 
 * @param id Unique identifier for the memory
 * @param content The actual text content of the memory
 * @param timestamp When this memory was created or last accessed
 * @param emotionTag Optional emotional context tag (e.g., "happy", "sad", "excited")
 * @param embeddingVector Vector representation for semantic search (stored as FloatArray)
 * 
 * Note: In production, embeddingVector should be stored efficiently (e.g., using Room's TypeConverter
 * or a separate vector database). For now, we'll use a simple approach.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val content: String,
    
    val timestamp: Long = System.currentTimeMillis(),
    
    val emotionTag: String? = null,
    
    // Embedding vector for semantic similarity search
    // TODO: Consider using a proper vector database (e.g., SQLite with vector extension, or external service)
    // For now, this will be stored as a serialized format
    val embeddingVector: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemoryEntity

        if (id != other.id) return false
        if (content != other.content) return false
        if (timestamp != other.timestamp) return false
        if (emotionTag != other.emotionTag) return false
        if (embeddingVector != null) {
            if (other.embeddingVector == null) return false
            if (!embeddingVector.contentEquals(other.embeddingVector)) return false
        } else if (other.embeddingVector != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (emotionTag?.hashCode() ?: 0)
        result = 31 * result + (embeddingVector?.contentHashCode() ?: 0)
        return result
    }
}

