package com.soulmate.data.memory

import com.soulmate.core.data.brain.EmbeddingService
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.soulmate.core.data.memory.MemoryEntity as ObjectBoxMemoryEntity
import com.soulmate.core.data.memory.MemoryEntity_

/**
 * ObjectBox-based implementation of VectorSearchEngine using HNSW (Hierarchical Navigable Small World) index.
 * 
 * This implementation provides real vector similarity search using ObjectBox's native
 * nearestNeighbors query capability with cosine distance.
 * 
 * Key features:
 * - Uses HNSW index for efficient approximate nearest neighbor search
 * - Generates embeddings via DoubaoEmbeddingService (1024 dimensions)
 * - Supports semantic similarity search, emotion-based filtering, and time-range queries
 */
@Singleton
class ObjectBoxVectorSearchEngine @Inject constructor(
    private val boxStore: BoxStore,
    private val embeddingService: EmbeddingService
) : VectorSearchEngine {

    private val box = boxStore.boxFor(ObjectBoxMemoryEntity::class)

    override suspend fun saveMemory(memory: MemoryEntity, embedding: FloatArray?) {
        withContext(Dispatchers.IO) {
            // Generate embedding if not provided
            val embeddingToSave = embedding ?: embeddingService.embed(memory.content)
            
            // Convert to ObjectBox entity and save
            val objectBoxEntity = ObjectBoxMemoryEntity(
                id = if (memory.id > 0) memory.id else 0,
                text = memory.content,
                embedding = embeddingToSave,
                timestamp = memory.timestamp,
                emotion = memory.emotionTag
            )
            box.put(objectBoxEntity)
        }
    }

    override suspend fun searchSimilar(query: String, limit: Int): List<MemoryEntity> {
        return withContext(Dispatchers.IO) {
            // Generate query embedding
            val queryEmbedding = embeddingService.embed(query)
            
            // Perform HNSW nearest neighbor search
            val results = box.query(MemoryEntity_.embedding.nearestNeighbors(queryEmbedding, limit))
                .build()
                .find()
            
            // Convert results to VectorSearchEngine's MemoryEntity format
            results.map { it.toMemoryEntity() }
        }
    }

    override suspend fun searchByEmotion(emotionTag: String, limit: Int): List<MemoryEntity> {
        return withContext(Dispatchers.IO) {
            box.query(MemoryEntity_.emotion.equal(emotionTag))
                .orderDesc(MemoryEntity_.timestamp)
                .build()
                .find(0, limit.toLong())
                .map { it.toMemoryEntity() }
        }
    }

    override suspend fun deleteMemory(memoryId: Long) {
        withContext(Dispatchers.IO) {
            box.remove(memoryId)
        }
    }

    override suspend fun getMemoriesByTimeRange(startTime: Long, endTime: Long): List<MemoryEntity> {
        return withContext(Dispatchers.IO) {
            box.query(
                MemoryEntity_.timestamp.between(startTime, endTime)
            )
                .orderDesc(MemoryEntity_.timestamp)
                .build()
                .find()
                .map { it.toMemoryEntity() }
        }
    }

    /**
     * Extension function to convert ObjectBox MemoryEntity to VectorSearchEngine's MemoryEntity.
     */
    private fun ObjectBoxMemoryEntity.toMemoryEntity(): MemoryEntity {
        return MemoryEntity(
            id = this.id,
            content = this.text ?: "",
            timestamp = this.timestamp,
            emotionTag = this.emotion,
            embeddingVector = this.embedding
        )
    }
}
