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
import javax.inject.Singleton

/**
 * ObjectBoxMemoryRepository - 基于 ObjectBox 的记忆存储实现
 */
@Singleton
class ObjectBoxMemoryRepository @Inject constructor(
    private val boxStore: BoxStore,
    private val embeddingService: EmbeddingService
) : MemoryRepository {

    private val box = boxStore.boxFor(MemoryEntity::class)

    override suspend fun save(text: String, emotion: String): Long = withContext(Dispatchers.IO) {
        val embedding = embeddingService.embed(text)
        // 兼容旧调用：将 emotion 同时写入 tag（做映射）
        val tag = when (emotion.lowercase()) {
            "user" -> "user_input"
            "ai" -> "ai_output"
            else -> emotion  // 其他情况（如 happy）保持原样
        }
        val memory = MemoryEntity(
            text = text,
            embedding = embedding,
            timestamp = System.currentTimeMillis(),
            emotion = emotion,  // 保留旧字段写入
            tag = tag
        )
        box.put(memory)
    }

    override suspend fun saveWithTag(
        text: String,
        tag: String,
        sessionId: Long?,
        emotionLabel: String?
    ): Long = withContext(Dispatchers.IO) {
        val embedding = embeddingService.embed(text)
        val memory = MemoryEntity(
            text = text,
            embedding = embedding,
            timestamp = System.currentTimeMillis(),
            tag = tag,
            sessionId = sessionId,
            emotionLabel = emotionLabel,
            // 同时写入旧 emotion 字段以兼容（tag -> emotion 反向映射）
            emotion = when (tag) {
                "user_input" -> "user"
                "ai_output" -> "ai"
                else -> tag
            }
        )
        val id = box.put(memory)
        android.util.Log.d("MemoryRepository", "Saved memory with tag: $tag, ID: $id")
        id
    }

    override suspend fun saveChunks(
        chunks: List<String>,
        tag: String,
        sessionId: Long?,
        emotionLabel: String?,
        sharedTimestamp: Long
    ): List<Long> = withContext(Dispatchers.IO) {
        if (chunks.isEmpty()) return@withContext emptyList()
        
        val memories = chunks.mapIndexed { index, chunkText ->
            val embedding = embeddingService.embed(chunkText)
            // 在 tag 中标记 part_x 便于调试（仅当有多个 chunk 时）
            val chunkTag = if (chunks.size > 1) "${tag}_part_${index + 1}" else tag
            MemoryEntity(
                text = chunkText,
                embedding = embedding,
                timestamp = sharedTimestamp,
                tag = chunkTag,
                sessionId = sessionId,
                emotionLabel = emotionLabel,
                // 同时写入旧 emotion 字段以兼容
                emotion = when (tag) {
                    "user_input" -> "user"
                    "ai_output" -> "ai"
                    else -> tag
                }
            )
        }
        // 批量插入，ObjectBox 的 put(List) 不返回 ID 列表
        // 需要逐个插入并收集 ID
        memories.map { memory ->
            box.put(memory)
        }
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        box.remove(id)
        Unit
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        box.removeAll()
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

    override suspend fun searchWithTags(
        queryEmbedding: FloatArray,
        limit: Int,
        allowedTags: Set<String>
    ): List<MemoryEntity> = withContext(Dispatchers.IO) {
        // ObjectBox 向量检索不能直接组合其他条件，所以先取更多候选，再过滤
        val candidates = box.query(MemoryEntity_.embedding.nearestNeighbors(queryEmbedding, limit * 4))
            .build()
            .find()
        
        if (allowedTags.isEmpty()) {
            candidates.take(limit)
        } else {
            candidates.filter { memory ->
                val effectiveTag = memory.getEffectiveTag()
                // 支持智能切片后的 _part_x 后缀 tag
                // 例如：allowedTags 包含 "user_input"，则 "user_input_part_1" 也匹配
                effectiveTag in allowedTags || allowedTags.any { allowedTag ->
                    effectiveTag.startsWith("${allowedTag}_part_")
                }
            }.take(limit)
        }
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
            // 同步更新 tag
            memory.tag = when (emotion.lowercase()) {
                "user" -> "user_input"
                "ai" -> "ai_output"
                else -> emotion
            }
            box.put(memory)
        }
        Unit
    }

    override suspend fun updateWithTag(
        id: Long,
        text: String,
        tag: String,
        emotionLabel: String?
    ) = withContext(Dispatchers.IO) {
        val memory = box.get(id)
        if (memory != null) {
            val embedding = embeddingService.embed(text)
            memory.text = text
            memory.tag = tag
            memory.emotionLabel = emotionLabel
            memory.embedding = embedding
            // 同步更新旧 emotion 字段
            memory.emotion = when (tag) {
                "user_input" -> "user"
                "ai_output" -> "ai"
                else -> tag
            }
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

    override suspend fun getMemoryCount(): Long = withContext(Dispatchers.IO) {
        box.count()
    }
    
    override suspend fun hasAnyMemory(): Boolean = withContext(Dispatchers.IO) {
        // 使用 limit(1) 进行快速检查，比 count() 更高效
        box.query()
            .build()
            .findFirst() != null
    }

    override suspend fun validateAndClearIfDimensionMismatch(expectedDim: Int) = withContext(Dispatchers.IO) {
        try {
            // 检查第一条有向量的记录
            val firstMemory = box.query(MemoryEntity_.embedding.notNull()).build().findFirst()
            
            if (firstMemory != null) {
                val embedding = firstMemory.embedding ?: return@withContext
                if (embedding.size != expectedDim) {
                    android.util.Log.w("MemoryRepository", "检测到记忆维度不匹配！期望 $expectedDim 但发现 ${embedding.size}。正在清除所有记忆以防止 RAG 失败。")
                    box.removeAll()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MemoryRepository", "验证记忆维度时出错", e)
        }
    }
}

