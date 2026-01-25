package com.soulmate.core.data.brain

import com.soulmate.core.data.memory.MemoryEntity
import com.soulmate.core.data.memory.MemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class RAGServiceTest {

    @Test
    fun prepareContext_filtersLowSimilarityAndRecentEcho() {
        val now = System.currentTimeMillis()
        val query = "hello"
        val queryEmbedding = floatArrayOf(1f, 0f)

        val candidates = listOf(
            memory(
                text = "low_sim",
                embedding = unitVectorWithCosine(0.2f),
                timestamp = now - 10_000,
                tag = "manual"
            ),
            memory(
                text = query,
                embedding = floatArrayOf(1f, 0f),
                timestamp = now - 1_000,
                tag = "user_input"
            ),
            memory(
                text = "kept",
                embedding = unitVectorWithCosine(0.4f),
                timestamp = now - 10_000,
                tag = "manual"
            )
        )

        val repo = FakeMemoryRepository(candidates)
        val embedding = FakeEmbeddingService(mapOf(query to queryEmbedding))
        val service = RAGService(embedding, repo)

        val context = runBlockingUnit { service.prepareContext(query) }

        assertTrue(context.contains("kept"))
        assertFalse(context.contains("low_sim"))
        assertFalse(context.contains("[$query]"))
        assertFalse(context.contains("] $query"))
    }

    @Test
    fun prepareContext_deduplicatesByTextKeepingLatestTimestamp() {
        val now = System.currentTimeMillis()
        val query = "q"
        val queryEmbedding = floatArrayOf(1f, 0f)

        val candidates = listOf(
            memory(
                text = "same",
                embedding = floatArrayOf(1f, 0f),
                timestamp = now - 10_000,
                tag = "manual"
            ),
            memory(
                text = "same",
                embedding = floatArrayOf(1f, 0f),
                timestamp = now - 5_000,
                tag = "summary"
            )
        )

        val repo = FakeMemoryRepository(candidates)
        val embedding = FakeEmbeddingService(mapOf(query to queryEmbedding))
        val service = RAGService(embedding, repo)

        val context = runBlockingUnit { service.prepareContext(query) }

        assertTrue(context.contains("[summary] same"))
        assertFalse(context.contains("[manual] same"))
    }

    @Test
    fun prepareContext_ranksWithTimeDecay() {
        val now = System.currentTimeMillis()
        val query = "q"
        val queryEmbedding = floatArrayOf(1f, 0f)

        val oldAgeMs = Duration.ofDays(15).toMillis()
        val candidates = listOf(
            memory(
                text = "old",
                embedding = floatArrayOf(1f, 0f),
                timestamp = now - oldAgeMs,
                tag = "manual"
            ),
            memory(
                text = "new",
                embedding = unitVectorWithCosine(0.35f),
                timestamp = now,
                tag = "manual"
            )
        )

        val repo = FakeMemoryRepository(candidates)
        val embedding = FakeEmbeddingService(mapOf(query to queryEmbedding))
        val service = RAGService(embedding, repo)

        val context = runBlockingUnit { service.prepareContext(query) }

        val newIndex = context.indexOf("new")
        val oldIndex = context.indexOf("old")
        assertTrue(newIndex >= 0 && oldIndex >= 0)
        assertTrue(newIndex < oldIndex)
    }

    @Test
    fun prepareContext_passesDefaultAllowedTagsToRepository() {
        val query = "q"
        val queryEmbedding = floatArrayOf(1f, 0f)

        val allowedTagsSeen = AtomicReference<Set<String>>(emptySet())
        val repo = FakeMemoryRepository(
            candidates = emptyList(),
            onSearchWithTags = { _, _, allowedTags -> allowedTagsSeen.set(allowedTags) }
        )
        val embedding = FakeEmbeddingService(mapOf(query to queryEmbedding))
        val service = RAGService(embedding, repo)

        runBlockingUnit { service.prepareContext(query) }

        assertEquals(setOf("manual", "summary", "user_input"), allowedTagsSeen.get())
    }
    
    @Test
    fun prepareContext_excludesCurrentSessionRecentRounds_userInput() {
        val now = System.currentTimeMillis()
        val query = "q"
        val queryEmbedding = floatArrayOf(1f, 0f)
        val sessionId = 123L
        val excludeAfterTimestamp = now - 5_000  // 排除 5 秒内的记忆
        
        val candidates = listOf(
            // 同 session，在窗口内，user_input -> 应被排除
            memory(
                text = "recent_in_session",
                embedding = floatArrayOf(1f, 0f),
                timestamp = now - 2_000,  // 2 秒前，在窗口内
                tag = "user_input",
                sessionId = sessionId
            ),
            // 同 session，在窗口外，user_input -> 应保留
            memory(
                text = "old_in_session",
                embedding = floatArrayOf(1f, 0f),
                timestamp = now - 10_000,  // 10 秒前，在窗口外
                tag = "user_input",
                sessionId = sessionId
            ),
            // 不同 session，在窗口内，user_input -> 应保留
            memory(
                text = "recent_other_session",
                embedding = floatArrayOf(1f, 0f),
                timestamp = now - 2_000,  // 2 秒前
                tag = "user_input",
                sessionId = 456L  // 不同会话
            ),
            // 同 session，在窗口内，manual -> 应保留（manual 不受排除影响）
            memory(
                text = "manual_in_session",
                embedding = floatArrayOf(1f, 0f),
                timestamp = now - 2_000,
                tag = "manual",
                sessionId = sessionId
            )
        )
        
        val repo = FakeMemoryRepository(candidates)
        val embedding = FakeEmbeddingService(mapOf(query to queryEmbedding))
        val service = RAGService(embedding, repo)
        
        val context = runBlockingUnit { 
            service.prepareContext(
                userQuery = query, 
                sessionId = sessionId, 
                excludeAfterTimestamp = excludeAfterTimestamp
            ) 
        }
        
        // recent_in_session 应被排除
        assertFalse("recent_in_session should be excluded", context.contains("recent_in_session"))
        // old_in_session 应保留
        assertTrue("old_in_session should be kept", context.contains("old_in_session"))
        // recent_other_session 应保留
        assertTrue("recent_other_session should be kept", context.contains("recent_other_session"))
        // manual_in_session 应保留
        assertTrue("manual_in_session should be kept", context.contains("manual_in_session"))
    }
    
    @Test
    fun prepareContext_excludesCurrentSessionRecentRounds_aiOutput() {
        val now = System.currentTimeMillis()
        val query = "q"
        val queryEmbedding = floatArrayOf(1f, 0f)
        val sessionId = 123L
        val excludeAfterTimestamp = now - 5_000
        
        // 为了测试 ai_output，需要把它加入 allowedTags
        val allowedTags = setOf("manual", "summary", "user_input", "ai_output")
        
        val candidates = listOf(
            // 同 session，在窗口内，ai_output -> 应被排除
            memory(
                text = "ai_recent",
                embedding = floatArrayOf(1f, 0f),
                timestamp = now - 2_000,
                tag = "ai_output",
                sessionId = sessionId
            ),
            // 同 session，在窗口外，ai_output -> 应保留
            memory(
                text = "ai_old",
                embedding = floatArrayOf(1f, 0f),
                timestamp = now - 10_000,
                tag = "ai_output",
                sessionId = sessionId
            )
        )
        
        val repo = FakeMemoryRepository(candidates)
        val embedding = FakeEmbeddingService(mapOf(query to queryEmbedding))
        val service = RAGService(embedding, repo)
        
        val context = runBlockingUnit { 
            service.prepareContext(
                userQuery = query, 
                sessionId = sessionId,
                allowedTags = allowedTags,
                excludeAfterTimestamp = excludeAfterTimestamp
            ) 
        }
        
        assertFalse("ai_recent should be excluded", context.contains("ai_recent"))
        assertTrue("ai_old should be kept", context.contains("ai_old"))
    }
    
    @Test
    fun prepareContext_noExclusionWhenExcludeAfterTimestampIsNull() {
        val now = System.currentTimeMillis()
        val query = "q"
        val queryEmbedding = floatArrayOf(1f, 0f)
        val sessionId = 123L
        
        val candidates = listOf(
            memory(
                text = "recent_memory",
                embedding = floatArrayOf(1f, 0f),
                timestamp = now - 1_000,
                tag = "user_input",
                sessionId = sessionId
            )
        )
        
        val repo = FakeMemoryRepository(candidates)
        val embedding = FakeEmbeddingService(mapOf(query to queryEmbedding))
        val service = RAGService(embedding, repo)
        
        // excludeAfterTimestamp = null，不排除
        val context = runBlockingUnit { 
            service.prepareContext(
                userQuery = query, 
                sessionId = sessionId,
                excludeAfterTimestamp = null
            ) 
        }
        
        assertTrue("recent_memory should be kept when excludeAfterTimestamp is null", 
            context.contains("recent_memory"))
    }

    private fun memory(
        text: String,
        embedding: FloatArray,
        timestamp: Long,
        tag: String,
        sessionId: Long? = null
    ): MemoryEntity {
        return MemoryEntity(
            id = 0,
            text = text,
            embedding = embedding,
            timestamp = timestamp,
            emotion = null,
            tag = tag,
            sessionId = sessionId,
            emotionLabel = null
        )
    }

    private fun unitVectorWithCosine(cosine: Float): FloatArray {
        val clamped = cosine.coerceIn(-1f, 1f)
        val y = kotlin.math.sqrt((1f - clamped * clamped).toDouble()).toFloat()
        return floatArrayOf(clamped, y)
    }
}

private class FakeEmbeddingService(
    private val mapping: Map<String, FloatArray>
) : EmbeddingService {
    override suspend fun embed(text: String): FloatArray {
        return mapping[text] ?: error("No embedding mapping for: $text")
    }
}

private class FakeMemoryRepository(
    private val candidates: List<MemoryEntity>,
    private val onSearchWithTags: (FloatArray, Int, Set<String>) -> Unit = { _, _, _ -> }
) : MemoryRepository {
    override suspend fun save(text: String, emotion: String): Long = error("Not used in tests")
    override suspend fun saveWithTag(text: String, tag: String, sessionId: Long?, emotionLabel: String?): Long =
        error("Not used in tests")

    override suspend fun delete(id: Long) = error("Not used in tests")
    override suspend fun getAll(): List<MemoryEntity> = error("Not used in tests")
    override suspend fun search(queryEmbedding: FloatArray, limit: Int): List<MemoryEntity> = error("Not used in tests")

    override suspend fun searchWithTags(
        queryEmbedding: FloatArray,
        limit: Int,
        allowedTags: Set<String>
    ): List<MemoryEntity> {
        onSearchWithTags(queryEmbedding, limit, allowedTags)
        return candidates
    }

    override fun getMemoriesByDate() = error("Not used in tests")
    override suspend fun update(id: Long, text: String, emotion: String) = error("Not used in tests")
    override suspend fun updateWithTag(id: Long, text: String, tag: String, emotionLabel: String?) =
        error("Not used in tests")

    override suspend fun getRecentMemories(limit: Int): List<MemoryEntity> = error("Not used in tests")
}

private fun <T> runBlockingUnit(block: suspend () -> T): T {
    val result = AtomicReference<Result<T>>()
    kotlinx.coroutines.runBlocking {
        try {
            result.set(Result.success(block()))
        } catch (t: Throwable) {
            result.set(Result.failure(t))
        }
    }
    return result.get().getOrThrow()
}

