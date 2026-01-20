package com.soulmate.data.model.llm

/**
 * Data class representing a chat response from OpenAI-compatible API.
 * 
 * @param id Unique identifier for the completion
 * @param choices List of completion choices (typically contains one choice)
 */
data class ChatResponse(
    val id: String,
    val choices: List<Choice>
)

/**
 * Data class representing a choice in the chat response.
 * 
 * @param message The message content from the assistant
 * @param finishReason The reason why the response finished (e.g., "stop", "length")
 */
data class Choice(
    val message: Message,
    val finishReason: String?
)

