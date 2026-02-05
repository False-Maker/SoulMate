package com.soulmate.data.service

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.soulmate.core.data.avatar.IAvatarDriver
import com.soulmate.data.model.UIEvent
import com.soulmate.data.model.UserGender
import com.soulmate.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AvatarCoreService - Digital Human Service
 * 
 * Refactored to use IAvatarDriver for SDK abstraction.
 */
@Singleton
class AvatarCoreService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val avatarDriver: IAvatarDriver,
    private val asrService: AliyunASRService
) {
    
    companion object {
        private const val TAG = "AvatarCoreService"
    }
    
    private val _avatarState = MutableStateFlow<AvatarState>(AvatarState.Idle)
    val avatarState: StateFlow<AvatarState> = _avatarState.asStateFlow()
    
    private val _uiEventFlow = MutableSharedFlow<UIEvent>(extraBufferCapacity = 10)
    val uiEventFlow: SharedFlow<UIEvent> = _uiEventFlow.asSharedFlow()

    val audioAmplitude: StateFlow<Float>
        get() = avatarDriver.audioAmplitude
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // subscribe to driver state changes
        avatarDriver.setListener { state ->
            _avatarState.value = state
        }

        // Subscribe to user voice activity for Barge-in
        serviceScope.launch {
            asrService.voiceActivityState.collect { voiceState ->
                if (voiceState == VoiceState.SPEAKING && _avatarState.value == AvatarState.Speaking) {
                    Log.i(TAG, "User voice detected (Barge-in), interrupting avatar.")
                    stopSpeaking()
                    startListening()
                }
            }
        }
    }

    /**
     * Initialize/Bind the avatar to a container
     */
    suspend fun bind(activityContext: Context, containerView: ViewGroup, userGender: UserGender = UserGender.MALE): Boolean {
        Log.i(TAG, "Binding avatar for gender: $userGender")
        return avatarDriver.initialize(activityContext, containerView, userGender)
    }
    
    /**
     * Release resources
     */
    suspend fun release() {
        avatarDriver.release()
    }
    
    fun destroy() {
        avatarDriver.destroy()
    }
    
    fun pause() {
        avatarDriver.pause()
    }
    
    fun resume() {
        avatarDriver.resume()
    }
    
    fun clearAvatarCache(): Boolean {
        // Driver manages cache, but interface doesn't expose clearCache.
        // Assuming Driver handles recovery automatically.
        // If we need manual clear, we need to add it to interface or cast (bad).
        // For now, log and skip, or if critical, update interface.
        Log.w(TAG, "clearAvatarCache requested but not exposed by driver")
        return false
    }

    /**
     * Speak text
     */
    fun speak(text: String) {
        // Driver handles speaking and thinking state transition if needed internally for speaking
        avatarDriver.startSpeaking(text)
    }
    
    fun speakWithAction(text: String, actionSemantic: String) {
        // If driver supports actions
         avatarDriver.startSpeaking(text) // Fallback to normal speak for now or extend interface
         // Interface has playMotion but no speakWithAction.
         // We can update interface later if needed.
    }

    fun speakWithoutDelay(text: String) {
        avatarDriver.startSpeaking(text)
    }

    /**
     * Stop speaking
     */
    fun stopSpeaking() {
        avatarDriver.stopSpeaking()
    }
    
    /**
     * Start listening (animation only)
     */
    fun startListening() {
        avatarDriver.setState(AvatarState.Listening)
    }
    
    /**
     * Start thinking (animation only)
     */
    fun startThinking() {
        avatarDriver.setState(AvatarState.Thinking)
    }
    
    /**
     * Set emotion
     */
    fun setEmotion(emotion: String) {
        // Interface doesn't support setEmotion yet.
        // Placeholder
    }
    
    /**
     * Play gesture
     */
    fun playGesture(gesture: String) {
        avatarDriver.playMotion(gesture)
    }
    
    fun isInitialized(): Boolean = avatarDriver.isInitialized()
}
