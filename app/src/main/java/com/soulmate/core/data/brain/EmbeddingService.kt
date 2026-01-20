package com.soulmate.core.data.brain

interface EmbeddingService {
    suspend fun embed(text: String): FloatArray
}
