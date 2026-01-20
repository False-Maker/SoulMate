package com.soulmate.data.model.llm

/**
 * Data class representing a chat request in OpenAI-compatible format.
 * 
 * @param model The model identifier (e.g., "deepseek-chat", "moonshot-v1-8k")
 * @param messages List of messages in the conversation
 * @param stream Whether to stream the response (default: false)
 * @param temperature Sampling temperature (0.0 to 2.0, default: 0.7)
 */
data class ChatRequest(
    val model: String = "deepseek-chat",
    val messages: List<Message>,
    val stream: Boolean = false,
    val temperature: Double = 0.7
)

