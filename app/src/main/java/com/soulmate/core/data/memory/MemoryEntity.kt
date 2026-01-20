package com.soulmate.core.data.memory

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType

@Entity
data class MemoryEntity(
    @Id
    var id: Long = 0,
    
    var text: String? = null,
    
    @HnswIndex(dimensions = 1024, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null,
    
    var timestamp: Long = 0,
    
    var emotion: String? = null
) {
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
        return emotion == other.emotion
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (emotion?.hashCode() ?: 0)
        return result
    }
}
