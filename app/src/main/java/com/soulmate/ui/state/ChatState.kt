package com.soulmate.ui.state

/**
 * Data class representing the complete state of the chat screen.
 *
 * This state is managed by ChatViewModel and observed by ChatScreen.
 * Using StateFlow ensures screen rotation doesn't lose chat history.
 *
 * @param messages List of all chat messages in the conversation
 * @param isLoading True when awaiting LLM response
 * @param currentStreamToken Current streaming token being "typed" (for typing effect)
 * @param error Error message if any operation failed, null otherwise
 */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val currentStreamToken: String = "",
    val error: String? = null
)
