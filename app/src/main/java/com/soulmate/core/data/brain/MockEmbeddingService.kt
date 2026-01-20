package com.soulmate.core.data.brain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Mock Embedding Service that generates simple hash-based embeddings.
 * 
 * This is a placeholder implementation for demo purposes.
 * For production, replace with a proper embedding API (e.g., Doubao embedding API).
 */
@Singleton
class MockEmbeddingService @Inject constructor() : EmbeddingService {
    
    companion object {
        private const val EMBEDDING_DIMENSION = 384 // Common embedding size
    }

    /**
     * Generates a simple hash-based embedding for the given text.
     * This is NOT a real semantic embedding - it's a deterministic placeholder.
     */
    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        // Use text hash as seed for reproducible "embeddings"
        val seed = text.hashCode().toLong()
        val random = Random(seed)
        
        FloatArray(EMBEDDING_DIMENSION) { 
            (random.nextFloat() * 2 - 1) // Values between -1 and 1
        }
    }
}
