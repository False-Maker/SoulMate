package com.soulmate.data.memory

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock implementation of VectorSearchEngine for development and testing.
 * 
 * This implementation uses simple text matching and does not perform actual vector similarity search.
 * In production, replace this with a real implementation that:
 * - Generates embeddings using an ML model (e.g., sentence transformers)
 * - Performs cosine similarity or other vector operations
 * - Integrates with Digital Human SDK's memory system
 * 
 * TODO: Replace with production implementation using:
 * - TensorFlow Lite for on-device embeddings
 * - Or external vector database service
 * - Or Digital Human SDK's built-in memory capabilities
 */
@Singleton
class MockVectorSearchEngine @Inject constructor() : VectorSearchEngine {
    
    private val memoryStore = mutableListOf<MemoryEntity>()
    
    override suspend fun saveMemory(memory: MemoryEntity, embedding: FloatArray?) {
        // Simulate network/database delay
        delay(50)
        
        // Generate a mock embedding if not provided
        val embeddingToSave = embedding ?: generateMockEmbedding(memory.content)
        
        val memoryWithEmbedding = memory.copy(embeddingVector = embeddingToSave)
        memoryStore.add(memoryWithEmbedding)
    }
    
    override suspend fun searchSimilar(query: String, limit: Int): List<MemoryEntity> {
        // Simulate search delay
        delay(100)
        
        // Mock implementation: simple keyword matching
        // In production, this would use cosine similarity on embedding vectors
        val queryLower = query.lowercase()
        val results = memoryStore
            .map { memory ->
                val score = calculateMockSimilarity(queryLower, memory.content.lowercase())
                memory to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
        
        return results
    }
    
    override suspend fun searchByEmotion(emotionTag: String, limit: Int): List<MemoryEntity> {
        delay(50)
        return memoryStore
            .filter { it.emotionTag?.equals(emotionTag, ignoreCase = true) == true }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    override suspend fun deleteMemory(memoryId: Long) {
        delay(50)
        memoryStore.removeAll { it.id == memoryId }
    }
    
    override suspend fun getMemoriesByTimeRange(startTime: Long, endTime: Long): List<MemoryEntity> {
        delay(50)
        return memoryStore
            .filter { it.timestamp in startTime..endTime }
            .sortedByDescending { it.timestamp }
    }
    
    /**
     * Generates a mock embedding vector for testing purposes.
     * In production, this should use a real embedding model.
     */
    private fun generateMockEmbedding(text: String): FloatArray {
        // Mock: create a simple hash-based "embedding"
        // In production, use TensorFlow Lite or API call to generate real embeddings
        val dimension = 128 // Typical embedding dimension
        val embedding = FloatArray(dimension)
        val hash = text.hashCode()
        
        for (i in embedding.indices) {
            embedding[i] = ((hash + i) % 1000) / 1000f
        }
        
        return embedding
    }
    
    /**
     * Calculates a mock similarity score between query and content.
     * In production, this would use cosine similarity on embedding vectors.
     */
    private fun calculateMockSimilarity(query: String, content: String): Double {
        if (content.contains(query)) {
            return 0.8
        }
        
        // Simple word overlap
        val queryWords = query.split(" ").toSet()
        val contentWords = content.split(" ").toSet()
        val intersection = queryWords.intersect(contentWords).size
        val union = queryWords.union(contentWords).size
        
        return if (union > 0) intersection.toDouble() / union else 0.0
    }
}

