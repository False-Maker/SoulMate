package com.soulmate.data.memory

/**
 * VectorSearchEngine interface for semantic similarity search.
 * This interface abstracts the vector search functionality needed for RAG (Retrieval-Augmented Generation).
 * 
 * In production, this could be implemented using:
 * - SQLite with vector extensions
 * - External vector databases (e.g., Pinecone, Weaviate, Qdrant)
 * - On-device ML models for embeddings (e.g., TensorFlow Lite)
 * - Integration with Digital Human SDK's memory capabilities
 */
interface VectorSearchEngine {
    
    /**
     * Saves a memory with its embedding vector for future retrieval.
     * 
     * @param memory The memory entity to save
     * @param embedding Optional embedding vector. If null, the engine should generate it.
     */
    suspend fun saveMemory(memory: MemoryEntity, embedding: FloatArray? = null)
    
    /**
     * Searches for similar memories based on semantic similarity.
     * 
     * @param query The search query string
     * @param limit Maximum number of results to return
     * @return List of MemoryEntity objects sorted by relevance (most similar first)
     */
    suspend fun searchSimilar(query: String, limit: Int = 10): List<MemoryEntity>
    
    /**
     * Searches for memories by emotion tag.
     * 
     * @param emotionTag The emotion tag to filter by
     * @param limit Maximum number of results to return
     * @return List of MemoryEntity objects with matching emotion tags
     */
    suspend fun searchByEmotion(emotionTag: String, limit: Int = 10): List<MemoryEntity>
    
    /**
     * Deletes a memory by ID.
     * 
     * @param memoryId The ID of the memory to delete
     */
    suspend fun deleteMemory(memoryId: Long)
    
    /**
     * Gets all memories within a time range.
     * 
     * @param startTime Start timestamp (inclusive)
     * @param endTime End timestamp (inclusive)
     * @return List of MemoryEntity objects within the time range
     */
    suspend fun getMemoriesByTimeRange(startTime: Long, endTime: Long): List<MemoryEntity>
}

