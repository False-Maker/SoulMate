package com.soulmate.core.data.memory

import com.soulmate.core.data.brain.EmbeddingService
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.reactive.DataObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import com.soulmate.core.data.memory.MemoryEntity_

class ObjectBoxMemoryRepository @Inject constructor(
    private val boxStore: BoxStore,
    private val embeddingService: EmbeddingService
) : MemoryRepository {

    private val box = boxStore.boxFor(MemoryEntity::class)

    override suspend fun save(text: String, emotion: String): Long = withContext(Dispatchers.IO) {
        val embedding = embeddingService.embed(text)
        val memory = MemoryEntity(
            text = text,
            embedding = embedding,
            timestamp = System.currentTimeMillis(),
            emotion = emotion
        )
        box.put(memory)
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        box.remove(id)
        Unit
    }

    override suspend fun getAll(): List<MemoryEntity> = withContext(Dispatchers.IO) {
        box.all
    }

    override suspend fun search(queryEmbedding: FloatArray, limit: Int): List<MemoryEntity> = withContext(Dispatchers.IO) {
        box.query(MemoryEntity_.embedding.nearestNeighbors(queryEmbedding, limit))
            .build()
            .find()
    }

    override fun getMemoriesByDate(): Flow<Map<LocalDate, List<MemoryEntity>>> = callbackFlow {
        val query = box.query().order(MemoryEntity_.timestamp).build()
        
        val observer = DataObserver<List<MemoryEntity>> { memories ->
            val grouped = memories.groupBy { memory ->
                Instant.ofEpochMilli(memory.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }.toSortedMap(reverseOrder())
            trySend(grouped)
        }
        
        val subscription = query.subscribe().observer(observer)
        
        awaitClose {
            subscription.cancel()
            query.close()
        }
    }

    override suspend fun update(id: Long, text: String, emotion: String) = withContext(Dispatchers.IO) {
        val memory = box.get(id)
        if (memory != null) {
            val embedding = embeddingService.embed(text)
            memory.text = text
            memory.emotion = emotion
            memory.embedding = embedding
            box.put(memory)
        }
        Unit
    }

    override suspend fun getRecentMemories(limit: Int): List<MemoryEntity> = withContext(Dispatchers.IO) {
        box.query()
            .orderDesc(MemoryEntity_.timestamp)
            .build()
            .find(0, limit.toLong())
    }
}

