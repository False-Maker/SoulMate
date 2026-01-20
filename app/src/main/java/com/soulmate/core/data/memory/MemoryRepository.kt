package com.soulmate.core.data.memory

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface MemoryRepository {
    suspend fun save(text: String, emotion: String): Long
    suspend fun delete(id: Long)
    suspend fun getAll(): List<MemoryEntity>
    suspend fun search(queryEmbedding: FloatArray, limit: Int): List<MemoryEntity>
    fun getMemoriesByDate(): Flow<Map<LocalDate, List<MemoryEntity>>>
    suspend fun update(id: Long, text: String, emotion: String)
    suspend fun getRecentMemories(limit: Int): List<MemoryEntity>
}
