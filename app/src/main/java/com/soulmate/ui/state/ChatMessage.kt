package com.soulmate.ui.state

import java.util.UUID

sealed class UIWidgetData {
    data class MemoryCapsule(
        val date: String,
        val summary: String,
        val imageUrls: List<String>
    ) : UIWidgetData()

    data class BreathingGuide(
        val durationSeconds: Int
    ) : UIWidgetData()

    data class DecisionOptions(
        val title: String,
        val options: List<String>
    ) : UIWidgetData()
}

/**
 * Data class representing a single chat message in the UI.
 *
 * @param id Unique identifier for the message
 * @param content The text content of the message
 * @param isFromUser True if the message is from the user, false if from AI
 * @param timestamp The time when the message was created
 * @param imageUrl Optional image URL for image messages (Phase 1 ImageGen)
 * @param localImageUri Optional local image URI for user-sent images (Phase 1 Vision)
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUrl: String? = null,
    val localImageUri: String? = null,
    val uiWidget: UIWidgetData? = null
)
