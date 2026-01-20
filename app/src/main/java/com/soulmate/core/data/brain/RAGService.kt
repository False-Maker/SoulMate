package com.soulmate.core.data.brain

import com.soulmate.core.data.memory.MemoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RAGService @Inject constructor(
    private val embeddingService: EmbeddingService,
    private val memoryRepository: MemoryRepository
) {

    suspend fun prepareContext(userQuery: String): String {
        val queryEmbedding = embeddingService.embed(userQuery)
        val relevantMemories = memoryRepository.search(queryEmbedding, limit = 5)

        if (relevantMemories.isEmpty()) {
            return ""
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val stringBuilder = StringBuilder("Relevant Memories:\n")

        relevantMemories.forEach { memory ->
            val time = dateFormat.format(Date(memory.timestamp))
            val text = memory.text ?: ""
            if (text.isNotBlank()) {
                stringBuilder.append("- [$time] $text\n")
            }
        }

        return stringBuilder.toString().trim()
    }
    suspend fun saveMemory(text: String, role: String) {
        // Tag memory with role (user/ai) for future filtering
        memoryRepository.save(text, role)
    }
}
