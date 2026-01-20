package com.soulmate.ui.state

import java.util.UUID

/**
 * Data class representing a single chat message in the UI.
 *
 * @param id Unique identifier for the message
 * @param content The text content of the message
 * @param isFromUser True if the message is from the user, false if from AI
 * @param timestamp The time when the message was created
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
