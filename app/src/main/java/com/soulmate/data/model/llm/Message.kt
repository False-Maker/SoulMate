package com.soulmate.data.model.llm

/**
 * Data class representing a chat message in OpenAI-compatible format.
 * 
 * @param role The role of the message sender: "system", "user", or "assistant"
 * @param content The text content of the message
 */
data class Message(
    val role: String,
    val content: String
)

