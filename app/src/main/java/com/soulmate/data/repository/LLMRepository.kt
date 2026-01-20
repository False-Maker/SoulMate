package com.soulmate.data.repository

import android.util.Log
import com.soulmate.data.constant.PersonaConstants
import com.soulmate.data.memory.IntimacyManager
import com.soulmate.data.memory.MemoryEntity
import com.soulmate.data.model.llm.ChatRequest
import com.soulmate.data.model.llm.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLMRepository handles communication with the Large Language Model (LLM) API.
 * 
 * This repository is responsible for:
 * - Sending user messages to the LLM
 * - Including relevant context memories (RAG - Retrieval-Augmented Generation)
 * - Managing conversation history
 * - Handling API responses and errors
 * - Managing intimacy progression based on user interactions
 */
@Singleton
class LLMRepository @Inject constructor(
    private val llmApiService: LLMApiService,
    private val intimacyManager: IntimacyManager,
    private val affinityRepository: AffinityRepository
) {
    
    companion object {
        private const val TAG = "LLMRepository"
    }
    
    /**
     * Sends a chat message to the LLM with context from past memories.
     * Also processes intimacy score based on user message content.
     * 
     * @param userMessage The user's message
     * @param history List of past conversation memories
     * @return Flow of Result containing the response string or error
     */
    fun chat(userMessage: String, history: List<MemoryEntity>): Flow<Result<String>> = flow {
        try {
            // 0. Process intimacy score based on user message
            val pointsEarned = intimacyManager.processInteraction(userMessage)
            val currentScore = intimacyManager.getCurrentScore()
            val currentLevel = intimacyManager.getCurrentLevelName()
            Log.d(TAG, "Intimacy: +$pointsEarned points, total=$currentScore, level=$currentLevel")
            
            // 1. Convert history + userMessage into a list of Message objects
            val messages = buildMessageList(history, userMessage)
            
            // 2. Add System Prompt at the beginning (based on intimacy level)
            val systemPrompt = buildSystemPrompt()
            val messagesWithSystem = listOf(
                Message(role = "system", content = systemPrompt)
            ) + messages
            
            // 3. Create chat request
            val request = ChatRequest(
                messages = messagesWithSystem,
                stream = false,
                temperature = 0.7
            )
            
            // 4. Call the API
            val response = llmApiService.generateChat(request)
            
            if (response.isSuccessful && response.body() != null) {
                // Extract the text response from choices[0].message.content
                val content = response.body()!!.choices.firstOrNull()?.message?.content
                if (content != null && content.isNotEmpty()) {
                    // Check for [DEDUCT] tag (rude behavior detected)
                    val hasDeductTag = content.contains("[DEDUCT]")
                    if (hasDeductTag) {
                        Log.w(TAG, "[DEDUCT] tag detected - user rudeness penalty")
                        affinityRepository.deductForRudeness()
                    }
                    
                    // Remove [DEDUCT] tag from content before parsing
                    val cleanedContent = content.replace("[DEDUCT]", "").trim()
                    
                    // Parse [Inner] and [Reply]
                    val (innerThought, spokenReply) = parseResponse(cleanedContent)
                    
                    if (innerThought.isNotEmpty()) {
                        Log.d(TAG, "Inner Thought: $innerThought")
                        // TODO: Potential future use - update emotional state based on inner thought
                    }
                    
                    emit(Result.success(spokenReply))
                } else {
                    emit(Result.failure(Exception("Empty response from LLM API")))
                }
            } else {
                val errorMessage = response.errorBody()?.string() 
                    ?: "Unknown error: ${response.code()}"
                emit(Result.failure(Exception("API error: $errorMessage")))
            }
        } catch (e: Exception) {
            // 5. Emit error message on failure
            emit(Result.failure(e))
        }
    }
    
    /**
     * Converts history and user message into a list of Message objects.
     * 
     * @param history List of past conversation memories
     * @param userMessage The current user message
     * @return List of Message objects representing the conversation
     */
    private fun buildMessageList(history: List<MemoryEntity>, userMessage: String): List<Message> {
        val messages = mutableListOf<Message>()
        
        // Convert history memories to messages
        // Note: MemoryEntity doesn't have role information, so we'll treat them as user messages
        // In a real implementation, you might want to store role information in MemoryEntity
        history.forEach { memory ->
            messages.add(Message(role = "user", content = memory.content))
            // If there's an emotion tag, we could add it as context
            // For now, we'll just use the content
        }
        
        // Add the current user message
        messages.add(Message(role = "user", content = userMessage))
        
        return messages
    }
    
    /**
     * Parses the raw response content to separate Inner Monologue and Spoken Reply.
     * Expected format:
     * [Inner]: ...
     * [Reply]: ...
     */
    private fun parseResponse(rawContent: String): Pair<String, String> {
        // Regex to match [Inner]: (content) [Reply]: (content)
        // DOT_MATCHES_ALL is needed to match newlines
        val pattern = Regex("\\[Inner\\]:\\s*(.*?)\\s*\\[Reply\\]:\\s*(.*)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        
        val matchResult = pattern.find(rawContent)
        if (matchResult != null && matchResult.groupValues.size >= 3) {
            val inner = matchResult.groupValues[1].trim()
            val reply = matchResult.groupValues[2].trim()
            return Pair(inner, reply)
        }
        
        // Fallback: If format not found, return empty inner and full content as reply
        return Pair("", rawContent)
    }

    /**
     * Builds the system prompt for Eleanor based on current intimacy level.
     * Uses dynamic persona selection from PersonaConstants.
     */
    private fun buildSystemPrompt(): String {
        val intimacyScore = intimacyManager.getCurrentScore()
        val affinityScore = affinityRepository.getCurrentScore()
        val prompt = PersonaConstants.getPromptByAffinity(affinityScore, intimacyScore)
        Log.d(TAG, "Using prompt: affinity=$affinityScore, intimacy=$intimacyScore, level=${intimacyManager.getCurrentLevelName()}, coldWar=${affinityScore < 50}")
        return prompt
    }
}
