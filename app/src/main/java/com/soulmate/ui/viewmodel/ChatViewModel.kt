package com.soulmate.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.soulmate.core.data.brain.LLMService
import com.soulmate.core.data.brain.RAGService
import com.soulmate.data.constant.PersonaConstants
import com.soulmate.data.service.EmotionGestureParser
import com.soulmate.data.service.AliyunASRService
import com.soulmate.data.service.ASRState
import com.soulmate.data.service.AvatarCoreService
import com.soulmate.data.model.UIEvent
import com.soulmate.data.preferences.UserPreferencesRepository
import com.soulmate.data.repository.AffinityRepository
import com.soulmate.ui.state.ChatMessage
import com.soulmate.ui.state.ChatState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ChatViewModel - Connects the Brain (RAGService + LLMService) to the UI (ChatScreen).
 *
 * This ViewModel:
 * - Manages chat state using StateFlow (survives configuration changes)
 * - Handles user messages and AI responses
 * - Integrates RAG for context-aware responses
 * - Supports streaming for typing effect
 * - Triggers Avatar speech when response completes
 * - Supports voice input via ASR
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val ragService: RAGService,
    private val llmService: LLMService,
    private val avatarService: AvatarCoreService,
    private val asrService: AliyunASRService,
    private val affinityRepository: AffinityRepository,
    private val intimacyManager: com.soulmate.data.memory.IntimacyManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val anniversaryManager: com.soulmate.worker.AnniversaryManager
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()
    
    // ASR State
    val asrState: StateFlow<ASRState> = asrService.asrState
    
    // Avatar Service for UI binding
    val avatarCoreService: AvatarCoreService get() = avatarService
    
    // Voice input text (partial recognition result)
    private val _voiceInputText = MutableStateFlow("")
    val voiceInputText: StateFlow<String> = _voiceInputText.asStateFlow()
    
    // Is voice input mode active
    private val _isVoiceInputActive = MutableStateFlow(false)
    val isVoiceInputActive: StateFlow<Boolean> = _isVoiceInputActive.asStateFlow()
    
    // UIEvent for memory card popup
    private val _currentUIEvent = MutableStateFlow<UIEvent?>(null)
    val currentUIEvent: StateFlow<UIEvent?> = _currentUIEvent.asStateFlow()
    
    // Affinity Score (punishment system)
    val affinityScore: StateFlow<Int> = affinityRepository.affinityScore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)
    
    val affinityLevel: StateFlow<AffinityRepository.AffinityLevel> = affinityRepository.affinityLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AffinityRepository.AffinityLevel.NORMAL)
    
    val isColdWar: StateFlow<Boolean> = affinityRepository.isColdWar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    init {
        // Initialize ASR service
        viewModelScope.launch {
            asrService.initialize()
        }
        
        // Collect partial results for real-time display
        viewModelScope.launch {
            asrService.partialResult.collect { partialText ->
                _voiceInputText.value = partialText
            }
        }
        
        // Collect final recognition results and send as message
        viewModelScope.launch {
            asrService.recognitionResult.collect { finalText ->
                if (finalText.isNotBlank()) {
                    Log.d(TAG, "ASR recognition result: $finalText")
                    _voiceInputText.value = ""
                    sendMessage(finalText)
                }
            }
        }
        
        // Collect Avatar state for error handling
        viewModelScope.launch {
            avatarService.avatarState.collect { state ->
                if (state is com.soulmate.data.service.AvatarState.Error) {
                    _chatState.update { it.copy(error = state.msg) }
                }
            }
        }

        // Collect ASR state for error handling
        viewModelScope.launch {
            asrService.asrState.collect { state ->
                if (state is ASRState.Error) {
                    _chatState.update { it.copy(error = state.message) }
                    _isVoiceInputActive.value = false
                }
            }
        }
        
        // Collect UIEvent from Avatar SDK for memory card popup
        viewModelScope.launch {
            avatarService.uiEventFlow.collect { event ->
                Log.d(TAG, "Received UIEvent: type=${event.type}")
                if (event.isVideoEvent || event.isPhotoEvent || event.isMemoryCardEvent) {
                    _currentUIEvent.value = event
                } else if (event.isCloseEvent) {
                    _currentUIEvent.value = null
                }
            }
        }
    }

    /**
     * Toggle voice input mode.
     * Starts recording when enabled, stops when disabled.
     */
    fun toggleVoiceInput() {
        if (_isVoiceInputActive.value) {
            stopVoiceInput()
        } else {
            startVoiceInput()
        }
    }
    
    /**
     * Start voice input (ASR recognition).
     */
    fun startVoiceInput() {
        Log.d(TAG, "Starting voice input")
        _voiceInputText.value = ""
        
        // Ensure initialized
        viewModelScope.launch {
            if (!asrService.isInitialized()) {
                if (!asrService.initialize()) {
                    // Start failed
                    return@launch
                }
            }
            
            // Start recognition on Main after init (nuiInstance methods are likely main-thread safe or handle it, but better safe)
            if (asrService.startRecognition()) {
                _isVoiceInputActive.value = true
                // Set avatar to listening state
                avatarService.startListening()
            }
        }
    }
    
    /**
     * Stop voice input (ASR recognition).
     */
    fun stopVoiceInput() {
        Log.d(TAG, "Stopping voice input")
        asrService.stopRecognition()
        _isVoiceInputActive.value = false
        _voiceInputText.value = ""
    }
    
    /**
     * Cancel voice input without sending.
     */
    fun cancelVoiceInput() {
        Log.d(TAG, "Cancelling voice input")
        asrService.cancelRecognition()
        _isVoiceInputActive.value = false
        _voiceInputText.value = ""
    }
    
    /**
     * Dismiss the current UIEvent popup.
     */
    fun dismissUIEvent() {
        _currentUIEvent.value = null
    }

    /**
     * Sends a user message and gets AI response with streaming.
     *
     * Flow:
     * 1. Add user message to state immediately
     * 2. Set loading state
     * 3. Run RAG to get relevant context
     * 4. Build prompt with context + user message
     * 5. Call LLM with streaming
     * 6. Update currentStreamToken for typing effect
     * 7. On completion, add AI message to list
     *
     * @param text The user's message text
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(
            content = text,
            isFromUser = true
        )

        viewModelScope.launch {
            // 1. Add user message to state
            _chatState.update { state ->
                state.copy(
                    messages = state.messages + userMessage,
                    isLoading = true,
                    currentStreamToken = "",
                    error = null
                )
            }
            
            // Save user message to memory
            try {
                ragService.saveMemory(text, "user")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save user memory", e)
            }

            try {
                // 2. Trigger thinking state while waiting for LLM
                avatarService.startThinking()
                
                // 3. Run RAG to get context
                val context = ragService.prepareContext(text)

                // 4. Build the full prompt with system prompt, context, and user message
                val fullPrompt = buildPrompt(context, text)

                // 5. Call LLM with streaming and collect tokens
                llmService.chatStream(fullPrompt)
                    .catch { e ->
                        _chatState.update { state ->
                            state.copy(
                                isLoading = false,
                                currentStreamToken = "",
                                error = "Failed to get response: ${e.message}"
                            )
                        }
                    }
                    .collect { accumulatedResponse ->
                        // 5. Update currentStreamToken for typing effect
                        _chatState.update { state ->
                            state.copy(currentStreamToken = accumulatedResponse)
                        }
                    }

                // 7. On completion, parse emotion/gesture and add AI message
                val finalResponse = _chatState.value.currentStreamToken
                if (finalResponse.isNotBlank()) {
                    // Parse and save anniversaries
                    try {
                        val anniversaries = com.soulmate.data.service.AnniversaryParser.parse(finalResponse)
                        anniversaries.forEach { 
                            Log.d(TAG, "Detected anniversary: ${it.name}")
                            anniversaryManager.addAnniversary(it) 
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse anniversaries", e)
                    }

                    // Parse emotion and gesture tags from LLM response
                    val parsed = EmotionGestureParser.parse(finalResponse)
                    Log.d(TAG, "Parsed emotion: ${parsed.emotion}, gesture: ${parsed.gesture}")
                    
                    // Drive avatar emotion and gesture
                    avatarService.setEmotion(parsed.emotion)
                    avatarService.playGesture(parsed.gesture)
                    
                    // Trigger Avatar speech with clean text (tags removed)
                    Log.d(TAG, "LLM response complete, triggering Avatar speech")
                    avatarService.speak(parsed.text)
                    
                    // Save AI response to memory
                    ragService.saveMemory(finalResponse, "ai")

                    val aiMessage = ChatMessage(
                        content = parsed.text,  // Use clean text without emotion/gesture tags
                        isFromUser = false
                    )
                    _chatState.update { state ->
                        state.copy(
                            messages = state.messages + aiMessage,
                            isLoading = false,
                            currentStreamToken = ""
                        )
                    }
                } else {
                    _chatState.update { state ->
                        state.copy(
                            isLoading = false,
                            currentStreamToken = ""
                        )
                    }
                }

            } catch (e: Exception) {
                _chatState.update { state ->
                    state.copy(
                        isLoading = false,
                        currentStreamToken = "",
                        error = mapErrorToUserMessage(e)
                    )
                }
            }
        }
    }

    /**
     * Builds the complete prompt for the LLM including system prompt, context, and user message.
     *
     * @param context The RAG context from relevant memories
     * @param userMessage The current user message
     * @return Complete prompt string
     */
    private fun buildPrompt(context: String, userMessage: String): String {
        // Use dynamic prompt based on affinity and intimacy scores
        val currentAffinity = affinityRepository.getCurrentScore() // Direct fetch if possible, or use flow value
        val currentIntimacy = intimacyManager.getCurrentScore()
        val config = userPreferencesRepository.getPersonaConfig()
        val systemPrompt = PersonaConstants.buildPrompt(config, currentAffinity, currentIntimacy)

        return buildString {
            appendLine(systemPrompt)
            appendLine()
            if (context.isNotBlank()) {
                appendLine(context)
                appendLine()
            }
            appendLine("User: $userMessage")
            appendLine()
            appendLine("Eleanor:")
        }
    }

    /**
     * Clears any error state.
     */
    fun clearError() {
        _chatState.update { state ->
            state.copy(error = null)
        }
    }

    /**
     * Maps technical exception messages to user-friendly error messages.
     * This prevents exposing raw exception details to users while still providing helpful feedback.
     */
    private fun mapErrorToUserMessage(e: Exception): String {
        return when {
            e is java.net.UnknownHostException -> "Unable to connect. Please check your internet connection."
            e is java.net.SocketTimeoutException -> "Request timed out. Please try again."
            e.message?.contains("API", ignoreCase = true) == true -> "Service temporarily unavailable. Please try again later."
            e.message?.contains("network", ignoreCase = true) == true -> "Network error. Please check your connection."
            else -> "Something went wrong. Please try again."
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Release ASR resources
        asrService.release()
    }
}

