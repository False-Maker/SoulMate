package com.soulmate.core.data.avatar

import android.content.Context
import android.view.ViewGroup
import com.soulmate.data.model.UserGender
import com.soulmate.data.service.AvatarState

/**
 * Interface for Avatar Drivers.
 * Decouples the specific SDK implementation (e.g. Xmov) from the business logic.
 */
interface IAvatarDriver {
    // Lifecycle management
    suspend fun initialize(context: Context, container: ViewGroup, gender: UserGender): Boolean
    fun destroy()
    fun pause()
    fun resume()
    suspend fun release()

    // Configuration
    fun setListener(listener: (AvatarState) -> Unit)

    // Actions
    fun startSpeaking(text: String)
    fun stopSpeaking()
    fun playMotion(motionName: String) // e.g., "nod", "shy", "thinking"

    // State Management
    fun setState(state: AvatarState) // IDLE, LISTENING, THINKING, SPEAKING
    
    fun isInitialized(): Boolean
}
