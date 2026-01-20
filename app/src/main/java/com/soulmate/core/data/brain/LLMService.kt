package com.soulmate.core.data.brain

import com.soulmate.BuildConfig
import com.soulmate.data.model.llm.ChatRequest
import com.soulmate.data.model.llm.Message
import com.soulmate.data.repository.LLMApiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMService @Inject constructor(
    private val llmApiService: LLMApiService
) {

    private val streamDelayMs: Long = 20L

    /**
     * Sends a message to the LLM and returns the complete response.
     *
     * @param userMessage The message to send to the LLM
     * @return The complete response string
     */
    suspend fun chat(userMessage: String): String {
        return try {
            val messages = listOf(Message(role = "user", content = userMessage))
            
            // Use ID from BuildConfig, fallback to a sensible default if empty (though it should be set)
            val modelId = BuildConfig.DOUBAO_MODEL_ID.ifEmpty { "deepseek-chat" }
            
            val request = ChatRequest(
                model = modelId,
                messages = messages,
                stream = false,
                temperature = 0.7
            )
            
            val response = llmApiService.generateChat(request)
            
            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                content ?: "Error: Empty response from API"
            } else {
                "Error: API Request Failed: ${response.code()} ${response.message()}"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Sends a message to the LLM and streams the response character by character.
     * This creates a "typing" effect in the UI.
     *
     * @param userMessage The message to send to the LLM
     * @return Flow emitting accumulated response string as characters are "typed"
     */
    fun chatStream(userMessage: String): Flow<String> = flow {
        // Since the current API implementation is not streaming, we simulate streaming
        // by fetching the full response and then emitting it character by character.
        val fullResponse = chat(userMessage)
        val accumulated = StringBuilder()
        
        for (char in fullResponse) {
            accumulated.append(char)
            emit(accumulated.toString())
            delay(streamDelayMs)
        }
    }
}
