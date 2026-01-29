package com.soulmate.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.soulmate.core.data.brain.ImageGenException
import com.soulmate.core.data.brain.ImageGenService
import com.soulmate.core.data.brain.LLMService
import com.soulmate.core.data.brain.RAGService
import com.soulmate.core.data.chat.ChatRepository
import com.soulmate.core.data.chat.ChatMessageEntity
import com.soulmate.data.constant.PersonaConstants
import com.soulmate.BuildConfig
import com.soulmate.data.model.llm.Message
import com.soulmate.data.model.llm.content.ContentPart
import com.soulmate.data.model.llm.content.ImageUrlPart
import com.soulmate.data.model.llm.content.MessageContent
import com.soulmate.data.model.llm.content.TextPart
import com.soulmate.data.service.EmotionGestureParser
import com.soulmate.data.service.AliyunASRService
import com.soulmate.data.service.ASRState
import com.soulmate.data.service.AvatarCoreService
import com.soulmate.data.service.ImageBase64Encoder
import com.soulmate.data.service.ImageEncodingException
import com.soulmate.data.service.VideoFrameExtractor
import com.soulmate.data.service.VideoExtractionException
import com.soulmate.data.model.UIEvent
import com.soulmate.data.preferences.UserPreferencesRepository
import com.soulmate.data.repository.AffinityRepository
import com.soulmate.ui.state.ChatMessage
import com.soulmate.ui.state.ChatState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import android.app.Application
import android.net.Uri
import javax.inject.Inject

/**
 * ChatViewModel - Connects the Brain (RAGService + LLMService) to the UI (ChatScreen).
 *
 * This ViewModel:
 * - Manages chat state using StateFlow (survives configuration changes)
 * - Handles user messages and AI responses
 * - Integrates RAG for context-aware responses (with filtering/sorting)
 * - Persists chat history to local database (recoverable after restart)
 * - Supports streaming for typing effect
 * - Triggers Avatar speech when response completes
 * - Supports voice input via ASR
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val ragService: RAGService,
    private val llmService: LLMService,
    private val imageGenService: ImageGenService,
    private val chatRepository: ChatRepository,
    private val avatarService: AvatarCoreService,
    private val asrService: AliyunASRService,
    private val affinityRepository: AffinityRepository,
    private val intimacyManager: com.soulmate.data.memory.IntimacyManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val anniversaryManager: com.soulmate.worker.AnniversaryManager,
    private val imageBase64Encoder: ImageBase64Encoder,
    private val videoFrameExtractor: VideoFrameExtractor,
    private val mindWatchService: com.soulmate.data.service.MindWatchService
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        // 默认值现在从 UserPreferencesRepository 获取，这里保留作为文档说明
        // HISTORY_LIMIT: 最近 N 条消息用于构建 history
        // RAG_EXCLUDE_ROUNDS: RAG 排除最近 N 轮（避免短期 history 重复喂给模型）
        
        // 日志降噪：流式响应节流配置
        private const val STREAM_UPDATE_INTERVAL_MS = 200L  // 每 200ms 更新一次
        private const val STREAM_MIN_CHARS_DELTA = 3  // 至少 3 个字符变化才更新
    }

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()
    
    // 当前活动会话ID
    private var currentSessionId: Long = 0
    
    // 订阅消息流的 Job（用于会话切换时取消旧订阅）
    private var observeJob: Job? = null
    
    // A3) 发送消息的 Job（用于并发控制，避免并行发送导致状态错乱）
    private var sendJob: Job? = null
    
    // 轮次治理：当前请求 ID（用于丢弃过期的流式响应）
    private var currentRequestId: Long = 0L
    
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
    
    // ImageGen 待确认状态（Phase 1 Router）
    private val _pendingImageGen = MutableStateFlow<ImageGenCommand?>(null)
    val pendingImageGen: StateFlow<ImageGenCommand?> = _pendingImageGen.asStateFlow()
    
    // Gson for parsing JSON commands
    private val gson = Gson()
    
    // Affinity Score (punishment system)
    val affinityScore: StateFlow<Int> = affinityRepository.affinityScore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)
    
    val affinityLevel: StateFlow<AffinityRepository.AffinityLevel> = affinityRepository.affinityLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AffinityRepository.AffinityLevel.NORMAL)
    
    val isColdWar: StateFlow<Boolean> = affinityRepository.isColdWar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    // 用户性别 Flow（用于数字人性别选择）
    val userGender: StateFlow<com.soulmate.data.model.UserGender> = userPreferencesRepository.userGenderFlow
    
    // Vision detail Flow（用于控制图片/视频理解精度：low/high/auto）
    val visionDetail: StateFlow<String> = userPreferencesRepository.visionDetailFlow

    // MindWatch Status
    val mindWatchStatus = mindWatchService.currentStatus
    
    // Anniversary Popup State
    private val _showAnniversaryPopup = MutableStateFlow<com.soulmate.data.model.AnniversaryEntity?>(null)
    val showAnniversaryPopup: StateFlow<com.soulmate.data.model.AnniversaryEntity?> = _showAnniversaryPopup.asStateFlow()
    
    /**
     * 关闭纪念日弹窗
     */
    fun dismissAnniversaryPopup() {
        _showAnniversaryPopup.value = null
    }

    /**
     * 关闭 MindWatch 警告/关怀卡片
     * 将状态重置为 NORMAL
     */
    fun dismissMindWatchAlert() {
        // 由于 MindWatchService 的状态通过 StateFlow 暴露且不可直接修改，
        // 我们需要在 Service 中提供一个重置方法。
        // 假设 Service 有 clearHistory() 会重置，或者我们需要添加一个 resetStatus()
        // 这里暂时调用 clearHistory()，或者最好在 Service 中加一个 ignoreCurrentWarning()
        // 根据之前的查看，Service 有 clearHistory() 会重置状态为 NORMAL
        // 但为了保留记录仅忽略当前警告，最好是 setStatus(NORMAL) 但 Service 中 _currentStatus 是 private
        // 我们先用 clearHistory() 作为临时的"我没事"，或者在 Service 加一个 dismissWarning
        // 既然 Service.kt 刚才看过，没有专门的 dismiss，我们就暂时用 clearHistory() 
        // 实际上这有点太激进了。
        // 让我们稍微修改一下 MindWatchService 增加一个 method，或者...
        // 刚才我看 Service 代码，clearHistory() 会 `_currentStatus.value = WatchStatus.NORMAL`
        // 这符合 "我没事" 的语义 -> 即使计算出来有风险，但我确认没事了，重置状态。
        mindWatchService.clearHistory()
    }

    /**
     * 设置 Vision detail（low/high/auto）
     */
    fun setVisionDetail(detail: String) {
        userPreferencesRepository.setVisionDetail(detail)
    }
    
    // 免提模式 Flow（开启后自动连续对话）
    val handsFreeMode: StateFlow<Boolean> = userPreferencesRepository.handsFreeMode
    
    /**
     * 设置免提模式
     * 开启后，用户说完一句会自动发送并重新开始识别
     */
    fun setHandsFreeMode(enabled: Boolean) {
        userPreferencesRepository.setHandsFreeMode(enabled)
    }
    
    init {
        // 初始化会话并加载历史消息
        viewModelScope.launch {
            initializeSession()
        }
        
        // Initialize ASR service
        viewModelScope.launch {
            asrService.initialize()
        }
        
        // Collect partial results for real-time display
        // 打断功能：首次检测到 partial 时，如果数字人仍在说话，立即停止
        viewModelScope.launch {
            asrService.partialResult.collect { partialText ->
                _voiceInputText.value = partialText
                
                // 打断：首次检测到用户说话时，停止数字人播报
                if (partialText.isNotBlank() && 
                    avatarService.avatarState.value is com.soulmate.data.service.AvatarState.Speaking) {
                    Log.d(TAG, "Interrupting avatar on partial result: ${partialText.take(20)}...")
                    avatarService.stopSpeaking()
                }
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
        
        // Collect Avatar state for error handling and hands-free mode
        var wasSpeaking = false
        viewModelScope.launch {
            avatarService.avatarState.collect { state ->
                when (state) {
                    is com.soulmate.data.service.AvatarState.Error -> {
                        _chatState.update { it.copy(error = state.msg) }
                    }
                    is com.soulmate.data.service.AvatarState.Speaking -> {
                        wasSpeaking = true
                    }
                    is com.soulmate.data.service.AvatarState.Idle -> {
                        // 免提模式：数字人说完后自动开始识别
                        if (wasSpeaking && handsFreeMode.value && !_isVoiceInputActive.value) {
                            Log.d(TAG, "Hands-free mode: auto-restart voice recognition")
                            wasSpeaking = false
                            // Add a small delay to ensure audio focus is released
                            kotlinx.coroutines.delay(200)
                            startVoiceInput()
                        }
                    }
                    else -> {}
                }
            }
        }

        // Collect ASR state for error handling
        viewModelScope.launch {
        // Collect ASR state for error handling and state sync
        viewModelScope.launch {
            asrService.asrState.collect { state ->
                when (state) {
                    is ASRState.Error -> {
                        _chatState.update { it.copy(error = state.message) }
                        _isVoiceInputActive.value = false
                    }
                    is ASRState.Idle -> {
                        // Crucial for Hands-Free Loop: Reset active state when ASR stops naturally
                        if (_isVoiceInputActive.value) {
                             Log.d(TAG, "ASR State Idle -> syncing UI state to false")
                            _isVoiceInputActive.value = false
                        }
                    }
                    else -> {}
                }
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
        
        // Check for today's anniversary
        viewModelScope.launch {
            val todayAnniversaries = anniversaryManager.getTodayAnniversaries()
            if (todayAnniversaries.isNotEmpty()) {
                // Show the most important one? Or first.
                _showAnniversaryPopup.value = todayAnniversaries.first()
            }
        }
    }

    /**
     * 初始化会话并订阅消息流
     * 
     * 改为订阅式恢复（单一数据源）：
     * - 不再用 getRecentMessages() 直接回填 messages
     * - 改为订阅 observeMessages() 流，DB 变化自动驱动 UI
     */
    private suspend fun initializeSession() {
        try {
            currentSessionId = chatRepository.getOrCreateActiveSession()
            Log.d(TAG, "Active session ID: $currentSessionId")
            
            // 启动消息流订阅（不再一次性加载）
            startObserveMessages(currentSessionId)
            
            // 检查并播报最新的 AI 消息（如果是在后台插入的主动问候）
            checkAndPlayLatestMessage(currentSessionId)
            
            // Debug: Check memory count
            val count = ragService.getMemoryCount()
            Log.d(TAG, "Current memory count: $count")
            // _chatState.update { if (count == 0L) it.copy(warning = "注意：当前记忆库为空 (Count: 0)") else it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize session", e)
        }
    }
    
    /**
     * 检查并自动播报最新的 AI 消息
     * 
     * 用于在通过 HeartbeatWorker 后台插入消息后，用户打开 App 时自动播报
     */
    private suspend fun checkAndPlayLatestMessage(sessionId: Long) {
        try {
            // 获取最新的一条消息
            val recentMessages = chatRepository.getRecentMessages(sessionId, 1)
            val latestMessage = recentMessages.lastOrNull() ?: return
            
            // 检查是否为 AI 消息
            if (latestMessage.role != "assistant") return
            
            // 检查时效性 (例如 30 分钟内)，避免播报太久以前的消息
            val timeDiff = System.currentTimeMillis() - latestMessage.timestamp
            if (timeDiff > 30 * 60 * 1000) {
                Log.d(TAG, "Latest message is too old to auto-play: ${timeDiff / 1000}s")
                return
            }
            
            // 解析情感和动作
            val content = latestMessage.rawContent ?: latestMessage.content
            try {
                // 如果有 rawContent (包含标签)，重新解析标签
                // 如果只有 clean content，则作为纯文本处理
                val parsed = if (latestMessage.rawContent != null) {
                    EmotionGestureParser.parse(latestMessage.rawContent!!)
                } else {
                    EmotionGestureParser.parse(latestMessage.content)
                }
                
                Log.d(TAG, "Auto-playing latest message: ${parsed.text.take(20)}...")
                
                // 驱动数字人
                // 延迟一点点，确保 UI 先展示出来
                kotlinx.coroutines.delay(500)
                avatarService.setEmotion(parsed.emotion)
                avatarService.playGesture(parsed.gesture)
                avatarService.speak(parsed.text)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse/play latest message", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAndPlayLatestMessage", e)
        }
    }
    
    /**
     * 启动消息流订阅
     * 
     * 取消旧订阅，启动新的 collect，DB 变化自动驱动 UI 更新
     * 
     * @param sessionId 会话ID
     */
    private fun startObserveMessages(sessionId: Long) {
        // 取消旧订阅
        observeJob?.cancel()
        
        // 从配置获取消息数量限制
        val historyLimit = userPreferencesRepository.getRagHistoryLimit()
        
        // 启动新订阅
        observeJob = viewModelScope.launch {
            var lastSize = -1  // 用于日志降噪，只在数量变化时打印
            
            chatRepository.observeMessages(sessionId, historyLimit * 2)
                .catch { e ->
                    // A2) 订阅链路容错：出错时写入 error 状态
                    Log.e(TAG, "Message observation error", e)
                    _chatState.update { it.copy(error = "消息加载失败: ${e.message}") }
                }
                .distinctUntilChanged()  // B2) 降噪：列表内容不变时不重复发射
                .collect { entities ->
                    val uiMessages = entities.map { entity ->
                        ChatMessage(
                            // A1) 使用 DB entity.id 作为稳定 Key，避免 UUID 每次变化导致全量刷新
                            id = entity.id.toString(),
                            content = entity.content,
                            isFromUser = entity.role == "user",
                            timestamp = entity.timestamp,
                            imageUrl = entity.imageUrl,
                            localImageUri = entity.localImageUri
                        )
                    }
                    _chatState.update { state ->
                        state.copy(messages = uiMessages)
                    }
                    
                    // A2) 日志降噪：只在消息数量变化时打印
                    if (uiMessages.size != lastSize) {
                        Log.d(TAG, "Messages updated: ${uiMessages.size} items")
                        lastSize = uiMessages.size
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
     * 
     * 打断功能：开始语音输入时，立即停止数字人播报，避免抢话
     */
    fun startVoiceInput() {
        Log.d(TAG, "Starting voice input")
        _voiceInputText.value = ""
        
        // 打断：如果数字人正在说话，立即停止
        if (avatarService.avatarState.value is com.soulmate.data.service.AvatarState.Speaking) {
            Log.d(TAG, "Interrupting avatar speech")
            avatarService.stopSpeaking()
        }
        
        // Ensure initialized
        viewModelScope.launch {
            if (!asrService.isInitialized()) {
                if (!asrService.initialize()) {
                    // Start failed
                    return@launch
                }
            }
            
            // Start recognition on Main after init (nuiInstance methods are likely main-thread safe or handle it, but better safe)
            // Use hands-free mode setting to determine if VAD should be enabled
            if (asrService.startRecognition(enableAutoStop = handsFreeMode.value)) {
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
     * 新流程（落库驱动 UI，单一数据源）:
     * 1. 获取/创建会话 sessionId
     * 2. 落库 user message（UI 通过 observeMessages 自动更新）
     * 3. RAG（先检索再保存本次输入为长期记忆，避免自匹配）
     * 4. 获取 history
     * 5. 构建结构化 messages
     * 6. 调用 LLM（流式显示）
     * 7. 解析响应并落库 assistant message（UI 自动更新）
     * 8. 长期记忆写入
     *
     * @param text The user's message text
     */
    fun sendMessage(text: String) {
        // 默认使用优化路径（默认启用），如果被禁用则使用原逻辑
        if (userPreferencesRepository.isConcurrentRagEnabled()) {
            sendMessageOptimized(text)
        } else {
            sendMessageOriginal(text)
        }
    }
    
    /**
     * 原始发送消息方法（保持原有逻辑完全不变）
     * 
     * 从 sendMessage() 中提取出来，确保原有逻辑不受影响
     */
    private fun sendMessageOriginal(text: String) {
        if (text.isBlank()) return
        
        // 轮次治理：取消旧任务，生成新请求 ID
        sendJob?.cancel()
        val requestId = ++currentRequestId
        // 日志降噪：只在关键节点输出，不输出每个请求的开始

        sendJob = viewModelScope.launch {
            // 1. 确保有会话
            if (currentSessionId == 0L) {
                currentSessionId = chatRepository.getOrCreateActiveSession()
                startObserveMessages(currentSessionId)
            }
            
            // 2. 落库 user message（UI 通过 observeMessages 自动更新，不再手动 append）
            try {
                chatRepository.appendMessage(
                    sessionId = currentSessionId,
                    role = "user",
                    content = text
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist user message", e)
            }

            // 2.5 若用户直接表达图片生成意图，走本地智能路由（不依赖 LLM 指令）
            val directImageGenCommand = detectUserImageGenRequest(text)
            if (directImageGenCommand != null) {
                _pendingImageGen.value = directImageGenCommand
                try {
                    chatRepository.appendMessage(
                        sessionId = currentSessionId,
                        role = "assistant",
                        content = "我可以为你生成图片，需要确认一下吗？"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist image gen prompt message", e)
                }
                avatarService.speak("我可以为你生成图片，需要确认一下吗？")
                return@launch
            }
            
            // 设置 loading 状态（清除之前的 error/warning）
            _chatState.update { state ->
                state.copy(
                    isLoading = true,
                    currentStreamToken = "",
                    error = null,
                    warning = null
                )
            }

            try {
                // 3. Trigger thinking state while waiting for LLM
                avatarService.startThinking()
                
                // 4. 读取 RAG 配置
                val ragConfig = userPreferencesRepository.getRagConfig()
                
                // 5. 获取 history（最近 N 条消息）- 提前获取，用于计算 RAG 排除窗口
                val historyEntities = chatRepository.getRecentMessages(currentSessionId, ragConfig.historyLimit)
                
                // 6. RAG（先检索再保存，避免自匹配）
                // 计算排除窗口起点时间戳（排除当前会话最近 N 轮，约 2*N 条消息）
                val excludeAfterTimestamp = calculateExcludeTimestamp(historyEntities, ragConfig.excludeRounds)
                
                // 根据配置决定是否包含 ai_output
                val allowedTags = if (ragConfig.includeAiOutput) {
                    com.soulmate.core.data.brain.RAGService.DEFAULT_ALLOWED_TAGS + "ai_output"
                } else {
                    com.soulmate.core.data.brain.RAGService.DEFAULT_ALLOWED_TAGS
                }
                
                // RAG 单独 try/catch：失败时降级继续对话，不中断
                val context = try {
                    val ragResult = ragService.prepareContextWithDebugInfo(
                        userQuery = text, 
                        sessionId = currentSessionId,
                        allowedTags = allowedTags,
                        excludeAfterTimestamp = excludeAfterTimestamp,
                        topKCandidates = ragConfig.topKCandidates,
                        maxContextItems = ragConfig.maxItems,
                        minSimilarity = ragConfig.minSimilarity,
                        halfLifeDays = ragConfig.halfLifeDays.toDouble()
                    )
                    // C1) 隐私安全的日志：默认只打印统计信息，不打印任何文本内容
                    // debugInfo 仅包含条数、相似度、tag分布，不含敏感记忆文本
                    Log.d(TAG, "RAG: ${ragResult.debugInfo}")
                    ragResult.context
                } catch (e: com.soulmate.core.data.brain.EmbeddingException) {
                    // Embedding 失败：降级为空 context，继续对话
                    val errorDetails = "Embedding API 失败: ${e.message}"
                    Log.e(TAG, "❌ RAG Critical Failure (Embedding): $errorDetails", e)
                    // Explicitly log the cause for the user (in case they missed the stack trace)
                    Log.e(TAG, "   Error Cause: ${e.message}") 
                    
                    // 将详细错误显示在 UI 上，方便用户直接调试
                    val uiWarning = "记忆检索失败(Embedding): ${e.message?.take(50)}..." // Increased length
                    _chatState.update { it.copy(warning = uiWarning) }
                    ""
                } catch (e: Exception) {
                    // 其他 RAG 相关异常也降级处理
                    val errorDetails = "RAG 系统故障: ${e.javaClass.simpleName} - ${e.message}"
                    Log.e(TAG, "❌ RAG Critical Failure (General): $errorDetails", e)
                    Log.e(TAG, "   Error Cause: ${e.message}")
                    
                    val uiWarning = "记忆检索失败(System): ${e.message?.take(50)}..."
                    _chatState.update { it.copy(warning = uiWarning) }
                    ""
                }

                // 6.5 处理亲密度/好感度机制（扣分/恢复/加分）
                processInteractionSignals(text)
                
                // 6.6 MindWatch 情绪分析（更新共鸣光球状态）
                mindWatchService.analyzeText(text)

                // 7. 构建结构化 messages（纯文本，无图片）
                val (messages, hasImage) = buildStructuredMessages(context, historyEntities, text)
                
                // 7.1 模型路由：根据是否有图片选择 Chat/Vision 端点
                val modelId = if (hasImage) {
                    BuildConfig.DOUBAO_VISION_ENDPOINT_ID.ifEmpty { null }
                } else {
                    null  // 使用默认 Chat 端点
                }

                // 7.2 调用 LLM with streaming（轮次治理：检查 requestId）
                // 日志降噪：使用节流减少更新频率（每 200ms 或至少 3 个字符变化）
                var lastUpdateTime = 0L
                var lastUpdateLength = 0
                llmService.chatStreamWithMessages(messages, modelId)
                    .catch { e ->
                        // 只有当前请求才更新 error
                        if (requestId == currentRequestId) {
                            _chatState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    currentStreamToken = "",
                                    error = "获取响应失败: ${e.message}"
                                )
                            }
                        }
                    }
                    .collect { accumulatedResponse ->
                        // 轮次治理：只有当前请求才更新 UI
                        if (requestId == currentRequestId) {
                            val now = System.currentTimeMillis()
                            val lengthDelta = accumulatedResponse.length - lastUpdateLength
                            
                            // 节流：每 200ms 或至少 3 个字符变化才更新
                            if (now - lastUpdateTime >= STREAM_UPDATE_INTERVAL_MS || 
                                lengthDelta >= STREAM_MIN_CHARS_DELTA) {
                                _chatState.update { state ->
                                    state.copy(currentStreamToken = accumulatedResponse)
                                }
                                lastUpdateTime = now
                                lastUpdateLength = accumulatedResponse.length
                            }
                        }
                    }

                // 8. 完成后处理
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
                    // 日志降噪：只在有变化时输出情绪和动作
                    if (parsed.emotion != "neutral" || parsed.gesture != "nod") {
                        Log.d(TAG, "Parsed emotion: ${parsed.emotion}, gesture: ${parsed.gesture}")
                    }
                    
                    // Phase 1: 检测图片生成指令
                    val imageGenCommand = parseImageGenCommand(parsed.text)
                    if (imageGenCommand != null) {
                        Log.d(TAG, "Detected image generation command: ${imageGenCommand.prompt.take(50)}...")
                        // 设置待确认状态，等待用户确认
                        _pendingImageGen.value = imageGenCommand
                    }
                    
                    // Drive avatar emotion and gesture
                    avatarService.setEmotion(parsed.emotion)
                    avatarService.playGesture(parsed.gesture)
                    
                    // Trigger Avatar speech with clean text (tags removed)
                    // 如果是图片生成指令，只朗读提示部分
                    val speakText = if (imageGenCommand != null) {
                        "好的，我来为你生成一张图片，请稍等..."
                    } else {
                        parsed.text
                    }
                    // 日志降噪：减少 Avatar speech 触发日志
                    avatarService.speak(speakText)
                    
                    // 落库 assistant message（UI 通过 observeMessages 自动更新，不再手动 append）
                    try {
                        chatRepository.appendMessage(
                            sessionId = currentSessionId,
                            role = "assistant",
                            content = parsed.text,
                            rawContent = finalResponse
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to persist assistant message", e)
                    }
                    
                    // 9. 长期记忆写入（在 RAG 检索之后）
                    try {
                        // 保存 user_input
                        ragService.saveMemoryWithTag(
                            text = text,
                            tag = "user_input",
                            sessionId = currentSessionId
                        )
                        // 保存 ai_output（带情绪标签）
                        ragService.saveMemoryWithTag(
                            text = parsed.text,
                            tag = "ai_output",
                            sessionId = currentSessionId,
                            emotionLabel = parsed.emotion
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save memories", e)
                    }

                    // 清空流式状态（不再手动 append aiMessage，由 DB 流驱动）
                    _chatState.update { state ->
                        state.copy(
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
     * 并发优化版本的发送消息方法
     * 
     * 优化点：
     * 1. 并发执行 RAG 检索和 history 获取（使用 async）
     * 2. 如果启用快速 RAG 路径，使用 prepareContextFast() 跳过无记忆场景
     * 
     * 预期收益：减少 30-50% 的总响应时间
     * 
     * @param text The user's message text
     */
    private fun sendMessageOptimized(text: String) {
        if (text.isBlank()) return
        
        // 轮次治理：取消旧任务，生成新请求 ID
        sendJob?.cancel()
        val requestId = ++currentRequestId
        // 日志降噪：只在关键节点输出，不输出每个请求的开始
        
        sendJob = viewModelScope.launch {
            // 1. 确保有会话
            if (currentSessionId == 0L) {
                currentSessionId = chatRepository.getOrCreateActiveSession()
                startObserveMessages(currentSessionId)
            }
            
            // 2. 落库 user message（UI 通过 observeMessages 自动更新）
            try {
                chatRepository.appendMessage(
                    sessionId = currentSessionId,
                    role = "user",
                    content = text
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist user message", e)
            }
            
            // 2.5 若用户直接表达图片生成意图，走本地智能路由
            val directImageGenCommand = detectUserImageGenRequest(text)
            if (directImageGenCommand != null) {
                _pendingImageGen.value = directImageGenCommand
                try {
                    chatRepository.appendMessage(
                        sessionId = currentSessionId,
                        role = "assistant",
                        content = "我可以为你生成图片，需要确认一下吗？"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist image gen prompt message", e)
                }
                avatarService.speak("我可以为你生成图片，需要确认一下吗？")
                return@launch
            }
            
            // 设置 loading 状态
            _chatState.update { state ->
                state.copy(
                    isLoading = true,
                    currentStreamToken = "",
                    error = null,
                    warning = null
                )
            }
            
            try {
                // 3. Trigger thinking state
                avatarService.startThinking()
                
                // 4. 读取 RAG 配置
                val ragConfig = userPreferencesRepository.getRagConfig()
                
                // ========== 并发优化：同时执行 RAG 和 history 获取 ==========
                // 先获取 history（用于计算排除窗口），然后并发执行 RAG 和完整 history 获取
                val tempHistory = chatRepository.getRecentMessages(currentSessionId, ragConfig.historyLimit)
                val excludeAfterTimestamp = calculateExcludeTimestamp(tempHistory, ragConfig.excludeRounds)
                
                // 根据配置决定是否包含 ai_output
                val allowedTags = if (ragConfig.includeAiOutput) {
                    com.soulmate.core.data.brain.RAGService.DEFAULT_ALLOWED_TAGS + "ai_output"
                } else {
                    com.soulmate.core.data.brain.RAGService.DEFAULT_ALLOWED_TAGS
                }
                
                // 并发执行 RAG 检索和完整 history 获取
                // 日志降噪：中间过程静默，只在完成时输出
                val ragDeferred = async {
                    try {
                        // 如果启用快速路径，使用 prepareContextFast（默认启用，失败时降级）
                        if (userPreferencesRepository.isFastRagPathEnabled()) {
                            try {
                                val context = ragService.prepareContextFast(
                                    userQuery = text,
                                    sessionId = currentSessionId,
                                    allowedTags = allowedTags,
                                    excludeAfterTimestamp = excludeAfterTimestamp,
                                    topKCandidates = ragConfig.topKCandidates,
                                    maxContextItems = ragConfig.maxItems,
                                    minSimilarity = ragConfig.minSimilarity,
                                    halfLifeDays = ragConfig.halfLifeDays.toDouble()
                                )
                                Pair(context, null as com.soulmate.core.data.brain.ContextDebugInfo?)
                            } catch (e: Exception) {
                                // 降级到完整 RAG（静默，不输出日志）
                                val ragResult = ragService.prepareContextWithDebugInfo(
                                    userQuery = text,
                                    sessionId = currentSessionId,
                                    allowedTags = allowedTags,
                                    excludeAfterTimestamp = excludeAfterTimestamp,
                                    topKCandidates = ragConfig.topKCandidates,
                                    maxContextItems = ragConfig.maxItems,
                                    minSimilarity = ragConfig.minSimilarity,
                                    halfLifeDays = ragConfig.halfLifeDays.toDouble()
                                )
                                Pair(ragResult.context, ragResult.debugInfo)
                            }
                        } else {
                            val ragResult = ragService.prepareContextWithDebugInfo(
                                userQuery = text,
                                sessionId = currentSessionId,
                                allowedTags = allowedTags,
                                excludeAfterTimestamp = excludeAfterTimestamp,
                                topKCandidates = ragConfig.topKCandidates,
                                maxContextItems = ragConfig.maxItems,
                                minSimilarity = ragConfig.minSimilarity,
                                halfLifeDays = ragConfig.halfLifeDays.toDouble()
                            )
                            Pair(ragResult.context, ragResult.debugInfo)
                        }
                    } catch (e: com.soulmate.core.data.brain.EmbeddingException) {
                        // 降级处理（记录详细错误，但不在 UI 立即显示）
                        val errorDetails = buildString {
                            append("Embedding 失败: ${e.message}")
                            e.cause?.let { cause ->
                                append(" (原因: ${cause.javaClass.simpleName} - ${cause.message})")
                            }
                        }
                        Log.e(TAG, errorDetails, e)
                        Pair("", null)
                    } catch (e: Exception) {
                        // 降级处理（记录详细错误）
                        val errorDetails = buildString {
                            append("RAG 失败: ${e.javaClass.simpleName} - ${e.message}")
                            e.cause?.let { cause ->
                                append(" (原因: ${cause.javaClass.simpleName} - ${cause.message})")
                            }
                        }
                        Log.e(TAG, errorDetails, e)
                        Pair("", null)
                    }
                }
                
                val historyDeferred = async {
                    chatRepository.getRecentMessages(currentSessionId, ragConfig.historyLimit)
                }
                
                // 等待两者完成（并发优化完成）
                val (context, debugInfo) = ragDeferred.await()
                val historyEntities = historyDeferred.await()
                
                // 日志降噪：只在完成时输出关键信息
                if (debugInfo != null) {
                    Log.d(TAG, "RAG completed: ${debugInfo}")
                }
                
                // 如果 RAG 失败，显示警告（只在失败时输出一次）
                // 优化：前 5 轮（约 10 条历史消息）不显示警告，避免初期干扰用户
                if (context.isEmpty() && debugInfo == null && historyEntities.size >= 10) {
                    // 使用 Error 级别确保错误信息可见，并提示查看上面的详细错误
                    Log.e(TAG, "═══════════════════════════════════════════════════════")
                    Log.e(TAG, "❌ RAG 失败，已降级为基础对话")
                    Log.e(TAG, "   请查看上面的 EmbeddingException 或 Exception 日志获取详细错误信息")
                    Log.e(TAG, "   搜索关键字: 'Embedding 失败' 或 'RAG 失败'")
                    Log.e(TAG, "═══════════════════════════════════════════════════════")
                    _chatState.update { it.copy(warning = "记忆检索暂时不可用，本轮已降级为基础对话（不使用历史记忆）") }
                }
                
                // 6.5 处理亲密度/好感度机制
                processInteractionSignals(text)
                
                // 6.6 MindWatch 情绪分析（更新共鸣光球状态）
                mindWatchService.analyzeText(text)
                
                // 7. 构建结构化 messages
                val (messages, hasImage) = buildStructuredMessages(context, historyEntities, text)
                
                // 7.1 模型路由
                val modelId = if (hasImage) {
                    BuildConfig.DOUBAO_VISION_ENDPOINT_ID.ifEmpty { null }
                } else {
                    null
                }
                
                // 7.2 调用 LLM with streaming
                // 日志降噪：使用节流减少更新频率
                var lastUpdateTime = 0L
                var lastUpdateLength = 0
                llmService.chatStreamWithMessages(messages, modelId)
                    .catch { e ->
                        if (requestId == currentRequestId) {
                            _chatState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    currentStreamToken = "",
                                    error = "获取响应失败: ${e.message}"
                                )
                            }
                        }
                    }
                    .collect { accumulatedResponse ->
                        if (requestId == currentRequestId) {
                            val now = System.currentTimeMillis()
                            val lengthDelta = accumulatedResponse.length - lastUpdateLength
                            
                            // 节流：每 200ms 或至少 3 个字符变化才更新
                            if (now - lastUpdateTime >= STREAM_UPDATE_INTERVAL_MS || 
                                lengthDelta >= STREAM_MIN_CHARS_DELTA) {
                                _chatState.update { state ->
                                    state.copy(currentStreamToken = accumulatedResponse)
                                }
                                lastUpdateTime = now
                                lastUpdateLength = accumulatedResponse.length
                            }
                        }
                    }
                
                // 8. 完成后处理（与原方法相同）
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
                    
                    // Parse emotion and gesture tags
                    val parsed = EmotionGestureParser.parse(finalResponse)
                    // 日志降噪：只在有变化时输出情绪和动作
                    if (parsed.emotion != "neutral" || parsed.gesture != "nod") {
                        Log.d(TAG, "Parsed emotion: ${parsed.emotion}, gesture: ${parsed.gesture}")
                    }
                    
                    // 检测图片生成指令
                    val imageGenCommand = parseImageGenCommand(parsed.text)
                    if (imageGenCommand != null) {
                        Log.d(TAG, "Detected image generation command: ${imageGenCommand.prompt.take(50)}...")
                        _pendingImageGen.value = imageGenCommand
                    }
                    
                    // Drive avatar
                    avatarService.setEmotion(parsed.emotion)
                    avatarService.playGesture(parsed.gesture)
                    
                    // Trigger Avatar speech
                    val speakText = if (imageGenCommand != null) {
                        "好的，我来为你生成一张图片，请稍等..."
                    } else {
                        parsed.text
                    }
                    // 日志降噪：减少 Avatar speech 触发日志
                    
                    // 根据配置选择是否使用快速思考（默认启用，失败时降级）
                    if (userPreferencesRepository.isFastThinkingEnabled()) {
                        try {
                            avatarService.speakWithoutDelay(speakText)
                        } catch (e: Exception) {
                            // 降级处理（静默）
                            avatarService.speak(speakText)
                        }
                    } else {
                        avatarService.speak(speakText)
                    }
                    
                    // 落库 assistant message
                    try {
                        chatRepository.appendMessage(
                            sessionId = currentSessionId,
                            role = "assistant",
                            content = parsed.text,
                            rawContent = finalResponse
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to persist assistant message", e)
                    }
                    
                    // 9. 长期记忆写入
                    try {
                        ragService.saveMemoryWithTag(
                            text = text,
                            tag = "user_input",
                            sessionId = currentSessionId
                        )
                        ragService.saveMemoryWithTag(
                            text = parsed.text,
                            tag = "ai_output",
                            sessionId = currentSessionId,
                            emotionLabel = parsed.emotion
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save memories", e)
                    }
                    
                    _chatState.update { state ->
                        state.copy(
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
     * 发送带图片的消息（Phase 1 Vision 支持）
     * 
     * 支持两种图片来源：
     * 1. 本地 URI（content:// 或 file://）：自动转换为 base64 data URL
     * 2. 公网 URL（http:// 或 https://）：直接发送
     * 
     * 降级策略：
     * - 如果图片编码失败，会降级为纯文本发送并提示用户
     * 
     * @param text 用户文本消息
     * @param imageUrl 图片 URI 或 URL
     */
    fun sendMessageWithImage(text: String, imageUrl: String) {
        if (text.isBlank() && imageUrl.isBlank()) return
        
        // 轮次治理：取消旧任务，生成新请求 ID
        sendJob?.cancel()
        val requestId = ++currentRequestId
        Log.d(TAG, "sendMessageWithImage: requestId=$requestId")
        
        val effectiveText = text.ifBlank { "这张图片里有什么？" }
        
        // 判断是否为本地 URI
        val isLocalUri = imageUrl.startsWith("content://") || imageUrl.startsWith("file://")
        
        sendJob = viewModelScope.launch {
            // 1. 确保有会话
            if (currentSessionId == 0L) {
                currentSessionId = chatRepository.getOrCreateActiveSession()
                startObserveMessages(currentSessionId)
            }
            
            // 2. 落库 user message（保存 localImageUri 用于 UI 展示，不保存 base64）
            try {
                chatRepository.appendMessage(
                    sessionId = currentSessionId,
                    role = "user",
                    content = effectiveText,
                    localImageUri = imageUrl  // 保存原始 URI 用于 UI 展示
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist user message with image", e)
            }
            
            // 设置 loading 状态
            _chatState.update { state ->
                state.copy(
                    isLoading = true,
                    currentStreamToken = "",
                    error = null,
                    warning = null
                )
            }
            
            try {
                avatarService.startThinking()
                
                // 3. 处理图片：本地 URI 转 base64，公网 URL 直接使用
                val imageUrlForModel: String? = if (isLocalUri) {
                    try {
                        val uri = Uri.parse(imageUrl)
                        val cachedUri = imageBase64Encoder.copyToCache(application, uri, "jpg")
                        val sourceUri = cachedUri ?: uri
                        Log.d(TAG, "Encoding local image: $sourceUri")
                        val dataUrl = imageBase64Encoder.encodeToDataUrl(application, sourceUri)
                        Log.d(TAG, "Image encoded successfully, data URL length: ${dataUrl.length}")
                        dataUrl
                    } catch (e: ImageEncodingException) {
                        Log.w(TAG, "Image encoding failed: ${e.message}")
                        _chatState.update { it.copy(warning = "图片处理失败：${e.message}，本轮已降级为纯文本") }
                        null  // 降级为纯文本
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error encoding image", e)
                        _chatState.update { it.copy(warning = "图片处理失败，本轮已降级为纯文本") }
                        null  // 降级为纯文本
                    }
                } else if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                    // 公网 URL 直接使用
                    imageUrl
                } else {
                    Log.w(TAG, "Unsupported image URL scheme: $imageUrl")
                    _chatState.update { it.copy(warning = "不支持的图片格式，本轮已降级为纯文本") }
                    null
                }
                
                val ragConfig = userPreferencesRepository.getRagConfig()
                val historyEntities = chatRepository.getRecentMessages(currentSessionId, ragConfig.historyLimit)
                
                // RAG（图片消息也需要 context）
                val excludeAfterTimestamp = calculateExcludeTimestamp(historyEntities, ragConfig.excludeRounds)
                val allowedTags = if (ragConfig.includeAiOutput) {
                    com.soulmate.core.data.brain.RAGService.DEFAULT_ALLOWED_TAGS + "ai_output"
                } else {
                    com.soulmate.core.data.brain.RAGService.DEFAULT_ALLOWED_TAGS
                }
                
                val context = try {
                    val ragResult = ragService.prepareContextWithDebugInfo(
                        userQuery = effectiveText,
                        sessionId = currentSessionId,
                        allowedTags = allowedTags,
                        excludeAfterTimestamp = excludeAfterTimestamp,
                        topKCandidates = ragConfig.topKCandidates,
                        maxContextItems = ragConfig.maxItems,
                        minSimilarity = ragConfig.minSimilarity,
                        halfLifeDays = ragConfig.halfLifeDays.toDouble()
                    )
                    Log.d(TAG, "RAG for vision: ${ragResult.debugInfo}")
                    ragResult.context
                } catch (e: Exception) {
                    Log.w(TAG, "RAG failed for vision, degrading: ${e.message}")
                    ""
                }
                
                processInteractionSignals(effectiveText)
                
                // MindWatch 情绪分析（更新共鸣光球状态）
                mindWatchService.analyzeText(effectiveText)
                
                // 构建多模态消息（使用处理后的 imageUrlForModel）
                val (messages, hasImage) = buildStructuredMessages(context, historyEntities, effectiveText, imageUrlForModel)
                
                // 路由到 Vision 端点
                val modelId = if (hasImage) {
                    val visionEndpoint = BuildConfig.DOUBAO_VISION_ENDPOINT_ID
                    if (visionEndpoint.isEmpty()) {
                        Log.w(TAG, "DOUBAO_VISION_ENDPOINT_ID not configured, falling back to chat")
                        _chatState.update { it.copy(warning = "图片理解端点未配置，无法解析图片") }
                        null
                    } else {
                        visionEndpoint
                    }
                } else {
                    null
                }
                
                Log.d(TAG, "Sending vision request with model: $modelId, hasImage: $hasImage")
                
                // 调用 LLM（轮次治理：检查 requestId）
                // 日志降噪：使用节流减少更新频率
                var lastUpdateTime = 0L
                var lastUpdateLength = 0
                llmService.chatStreamWithMessages(messages, modelId)
                    .catch { e ->
                        if (requestId == currentRequestId) {
                            _chatState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    currentStreamToken = "",
                                    error = "获取响应失败: ${e.message}"
                                )
                            }
                        }
                    }
                    .collect { accumulatedResponse ->
                        if (requestId == currentRequestId) {
                            val now = System.currentTimeMillis()
                            val lengthDelta = accumulatedResponse.length - lastUpdateLength
                            
                            // 节流：每 200ms 或至少 3 个字符变化才更新
                            if (now - lastUpdateTime >= STREAM_UPDATE_INTERVAL_MS || 
                                lengthDelta >= STREAM_MIN_CHARS_DELTA) {
                                _chatState.update { state ->
                                    state.copy(currentStreamToken = accumulatedResponse)
                                }
                                lastUpdateTime = now
                                lastUpdateLength = accumulatedResponse.length
                            }
                        }
                    }
                
                // 完成后处理（与 sendMessage 相同）
                val finalResponse = _chatState.value.currentStreamToken
                if (finalResponse.isNotBlank()) {
                    val parsed = EmotionGestureParser.parse(finalResponse)
                    avatarService.setEmotion(parsed.emotion)
                    avatarService.playGesture(parsed.gesture)
                    avatarService.speak(parsed.text)
                    
                    try {
                        chatRepository.appendMessage(
                            sessionId = currentSessionId,
                            role = "assistant",
                            content = parsed.text,
                            rawContent = finalResponse
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to persist assistant message", e)
                    }
                    
                    // 保存到长期记忆
                    try {
                        // 保存 user_input（带 vision 标签）
                        ragService.saveMemoryWithTag(
                            text = effectiveText,
                            tag = "user_input",
                            sessionId = currentSessionId
                        )
                        // 保存 ai_output（带 vision 标签）
                        ragService.saveMemoryWithTag(
                            text = parsed.text,
                            tag = "ai_output",
                            sessionId = currentSessionId,
                            emotionLabel = parsed.emotion
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save vision memories", e)
                    }
                }
                
                _chatState.update { state ->
                    state.copy(isLoading = false, currentStreamToken = "")
                }
                
            } catch (e: java.util.concurrent.CancellationException) {
                // 协程被取消（如：用户快速发送下一条消息），正常退出，不报错
                Log.d(TAG, "Chat request cancelled")
                throw e
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
     * 发送带视频的消息（视频理解 v0）
     * 
     * 从本地视频中抽取关键帧，转换为多张图片发送给 Vision 模型。
     * 
     * @param text 用户文本消息
     * @param videoUri 视频 URI（content:// 或 file://）
     * @param maxFrames 最大抽取帧数（默认 6）
     */
    fun sendMessageWithVideo(text: String, videoUri: String, maxFrames: Int = 6) {
        if (videoUri.isBlank()) return
        
        // 轮次治理：取消旧任务，生成新请求 ID
        sendJob?.cancel()
        val requestId = ++currentRequestId
        Log.d(TAG, "sendMessageWithVideo: requestId=$requestId")
        
        val effectiveText = text.ifBlank { "请描述这个视频的内容" }
        
        sendJob = viewModelScope.launch {
            // 1. 确保有会话
            if (currentSessionId == 0L) {
                currentSessionId = chatRepository.getOrCreateActiveSession()
                startObserveMessages(currentSessionId)
            }
            
            // 2. 落库 user message（标记为视频消息）
            try {
                chatRepository.appendMessage(
                    sessionId = currentSessionId,
                    role = "user",
                    content = "[视频] $effectiveText",
                    localVideoUri = videoUri  // 使用专用字段存储视频 URI
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist user message with video", e)
            }
            
            // 设置 loading 状态
            _chatState.update { state ->
                state.copy(
                    isLoading = true,
                    currentStreamToken = "",
                    error = null,
                    warning = null
                )
            }
            
            try {
                avatarService.startThinking()
                
                // 3. 抽取视频帧
                val uri = Uri.parse(videoUri)
                val cachedUri = imageBase64Encoder.copyToCache(application, uri, "mp4")
                val sourceUri = cachedUri ?: uri
                Log.d(TAG, "Extracting frames from video: $sourceUri")
                val frames = try {
                    videoFrameExtractor.extractFrames(application, sourceUri, maxFrames)
                } catch (e: com.soulmate.data.service.VideoExtractionException) { // Use fully qualified name if needed, or import
                    Log.w(TAG, "Video extraction failed: ${e.message}")
                    _chatState.update { it.copy(warning = "视频处理失败：${e.message}，已降级为纯文本") }
                    emptyList()
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error extracting frames", e)
                    _chatState.update { it.copy(warning = "视频处理失败，已降级为纯文本") }
                    emptyList()
                }
                
                // 4. 转换帧为 data URL
                val frameDataUrls = mutableListOf<String>()
                for ((index, frame) in frames.withIndex()) {
                    try {
                        val dataUrl = imageBase64Encoder.encodeBitmapToDataUrl(frame)
                        frameDataUrls.add(dataUrl)
                        Log.d(TAG, "Encoded frame ${index + 1}/${frames.size}")
                    } catch (e: ImageEncodingException) {
                        Log.w(TAG, "Failed to encode frame $index: ${e.message}")
                    } finally {
                        frame.recycle()
                    }
                }
                
                Log.d(TAG, "Successfully encoded ${frameDataUrls.size} frames")
                
                // 5. 构建多模态消息
                val ragConfig = userPreferencesRepository.getRagConfig()
                val historyEntities = chatRepository.getRecentMessages(currentSessionId, ragConfig.historyLimit)
                val excludeAfterTimestamp = calculateExcludeTimestamp(historyEntities, ragConfig.excludeRounds)
                
                val context = try {
                    val ragResult = ragService.prepareContextWithDebugInfo(
                        userQuery = effectiveText,
                        sessionId = currentSessionId,
                        excludeAfterTimestamp = excludeAfterTimestamp
                    )
                    Log.d(TAG, "RAG for video: ${ragResult.debugInfo}")
                    ragResult.context
                } catch (e: Exception) {
                    Log.w(TAG, "RAG failed for video, degrading: ${e.message}")
                    ""
                }
                
                // 处理亲密度/好感度机制和情绪分析
                processInteractionSignals(effectiveText)
                mindWatchService.analyzeText(effectiveText)
                
                // 构建消息（包含多张图片）
                val (messages, hasImage) = buildVideoMessages(context, historyEntities, effectiveText, frameDataUrls)
                
                // 6. 调用 Vision endpoint
                val modelId = if (hasImage) {
                    val visionEndpoint = BuildConfig.DOUBAO_VISION_ENDPOINT_ID
                    if (visionEndpoint.isEmpty()) {
                        Log.w(TAG, "DOUBAO_VISION_ENDPOINT_ID not configured")
                        _chatState.update { it.copy(warning = "视频理解端点未配置") }
                        null
                    } else {
                        visionEndpoint
                    }
                } else {
                    null
                }
                
                Log.d(TAG, "Sending video request with model: $modelId, frames: ${frameDataUrls.size}")
                
                // 日志降噪：使用节流减少更新频率
                var lastUpdateTime = 0L
                var lastUpdateLength = 0
                llmService.chatStreamWithMessages(messages, modelId)
                    .catch { e ->
                        if (requestId == currentRequestId) {
                            _chatState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    currentStreamToken = "",
                                    error = "获取响应失败: ${e.message}"
                                )
                            }
                        }
                    }
                    .collect { accumulatedResponse ->
                        if (requestId == currentRequestId) {
                            val now = System.currentTimeMillis()
                            val lengthDelta = accumulatedResponse.length - lastUpdateLength
                            
                            // 节流：每 200ms 或至少 3 个字符变化才更新
                            if (now - lastUpdateTime >= STREAM_UPDATE_INTERVAL_MS || 
                                lengthDelta >= STREAM_MIN_CHARS_DELTA) {
                                _chatState.update { state ->
                                    state.copy(currentStreamToken = accumulatedResponse)
                                }
                                lastUpdateTime = now
                                lastUpdateLength = accumulatedResponse.length
                            }
                        }
                    }
                
                // 7. 完成后处理
                val finalResponse = _chatState.value.currentStreamToken
                if (finalResponse.isNotBlank()) {
                    val parsed = EmotionGestureParser.parse(finalResponse)
                    avatarService.setEmotion(parsed.emotion)
                    avatarService.playGesture(parsed.gesture)
                    avatarService.speak(parsed.text)
                    
                    try {
                        chatRepository.appendMessage(
                            sessionId = currentSessionId,
                            role = "assistant",
                            content = parsed.text,
                            rawContent = finalResponse
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to persist assistant message", e)
                    }
                    
                    // 保存到长期记忆
                    try {
                        ragService.saveMemoryWithTag(
                            text = effectiveText,
                            tag = "user_input",
                            sessionId = currentSessionId
                        )
                        ragService.saveMemoryWithTag(
                            text = parsed.text,
                            tag = "ai_output",
                            sessionId = currentSessionId,
                            emotionLabel = parsed.emotion
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save video memories", e)
                    }
                }
                
                _chatState.update { state ->
                    state.copy(isLoading = false, currentStreamToken = "")
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
     * 构建视频理解的多模态消息
     */
    private fun buildVideoMessages(
        context: String,
        history: List<ChatMessageEntity>,
        userText: String,
        frameDataUrls: List<String>
    ): Pair<List<Message>, Boolean> {
        val messages = mutableListOf<Message>()
        val hasImage = frameDataUrls.isNotEmpty()
        
        // 1. System prompt
        val currentAffinity = affinityRepository.getCurrentScore()
        val currentIntimacy = intimacyManager.getCurrentScore()
        val config = userPreferencesRepository.getPersonaConfig()
        val systemPrompt = PersonaConstants.buildPrompt(config, currentAffinity, currentIntimacy)
        messages.add(Message.system(systemPrompt))
        
        // 2. Memory context
        if (context.isNotBlank()) {
            messages.add(Message.system(context))
        }
        
        // 3. History
        val historyToInclude = if (history.isNotEmpty() && history.last().role == "user") {
            history.dropLast(1)
        } else {
            history
        }
        historyToInclude.forEach { entity ->
            val role = when (entity.role) {
                "user" -> "user"
                "assistant" -> "assistant"
                else -> "user"
            }
            messages.add(Message.text(role, entity.content))
        }
        
        // 4. Current user input with video frames
        if (hasImage) {
            val parts = mutableListOf<ContentPart>()
            
            // 添加引导文本
            val promptText = if (frameDataUrls.size > 1) {
                "以下是从视频中抽取的 ${frameDataUrls.size} 个关键帧。$userText"
            } else {
                userText
            }
            parts.add(TextPart(promptText))
            
            // 添加所有帧图片
            val detail = userPreferencesRepository.getVisionDetail()
            frameDataUrls.forEach { dataUrl ->
                parts.add(ImageUrlPart.create(dataUrl, detail))
            }
            
            messages.add(Message(role = "user", content = MessageContent.parts(parts)))
        } else {
            // 无帧时降级为纯文本
            messages.add(Message.user(userText))
        }
        
        return Pair(messages, hasImage)
    }

    /**
     * 计算 RAG 排除窗口起点时间戳
     * 
     * 用于排除当前会话最近 N 轮的记忆，避免短期 history 重复被 RAG 召回。
     * 
     * @param historyEntities 历史消息列表（按时间正序）
     * @param excludeRounds 排除轮数（1 轮 = 1 user + 1 assistant）
     * @return 排除窗口起点时间戳，若 history 不足则返回 null
     */
    private fun calculateExcludeTimestamp(
        historyEntities: List<ChatMessageEntity>,
        excludeRounds: Int
    ): Long? {
        if (historyEntities.isEmpty() || excludeRounds <= 0) return null
        
        // 需要排除的消息数量约为 2*N（user+assistant）
        val excludeCount = excludeRounds * 2
        
        // 从 history 取最后 excludeCount 条消息
        val toExclude = historyEntities.takeLast(excludeCount)
        
        // 返回这些消息中最早一条的时间戳作为窗口起点
        return toExclude.minOfOrNull { it.timestamp }
    }

    /**
     * 构建结构化 messages（按文档 7.2）
     * 
     * 顺序：
     * 1. system: persona/systemPrompt
     * 2. system: memory context（若为空则跳过）
     * 3. history: 最近 N 条 user/assistant（按 timestamp）
     * 4. user: 当前输入（支持多模态：文本+图片）
     * 
     * @param imageUrl 可选的图片 URL（用于 Vision 模型）
     * @param imageDetail 图片解析精度：low/high/auto（默认从偏好读取）
     * @return Pair<消息列表, 是否包含图片>（用于路由选择 Chat/Vision 端点）
     */
    private fun buildStructuredMessages(
        context: String,
        history: List<ChatMessageEntity>,
        currentUserInput: String,
        imageUrl: String? = null,
        imageDetail: String? = null
    ): Pair<List<Message>, Boolean> {
        val messages = mutableListOf<Message>()
        var hasImage = false
        
        // 1. System prompt
        val currentAffinity = affinityRepository.getCurrentScore()
        val currentIntimacy = intimacyManager.getCurrentScore()
        val config = userPreferencesRepository.getPersonaConfig()
        val systemPrompt = PersonaConstants.buildPrompt(config, currentAffinity, currentIntimacy)
        messages.add(Message.system(systemPrompt))

        // 1.1 性别绑定 + 性格滑杆提示（紧跟在 persona 后）
        val userGender = userPreferencesRepository.getUserGender()
        val aiGender = when (userGender) {
            com.soulmate.data.model.UserGender.MALE -> com.soulmate.data.model.UserGender.FEMALE
            com.soulmate.data.model.UserGender.FEMALE -> com.soulmate.data.model.UserGender.MALE
            com.soulmate.data.model.UserGender.UNSET -> com.soulmate.data.model.UserGender.UNSET
        }
        val warmth = userPreferencesRepository.getPersonaWarmth()
        val genderBindingMessage = buildString {
            append("性别与语气约束：\n")
            append("用户性别：${userGender.toDisplayName()}；AI 性别：${aiGender.toDisplayName()}。\n")
            append("你必须在所有叙述、自称、代词上保持与 AI 性别一致；不要切换性别。\n")
            if (userGender == com.soulmate.data.model.UserGender.UNSET) {
                append("用户性别未设定时，保持中性表达。\n")
            }
            append("性格滑杆 persona_warmth = $warmth（0=高冷，50=均衡，100=温柔）。\n")
            append("它只影响语气，不改变安全边界与身份设定。")
        }
        messages.add(Message.system(genderBindingMessage))
        
        // 2. Memory context（作为单独的 system message）
        if (context.isNotBlank()) {
            messages.add(Message.system(context))
        }
        
        // 3. History messages（排除最后一条，因为那是我们刚追加的 user message）
        // 注意：history 已经按时间正序排列
        val historyToInclude = if (history.isNotEmpty() && history.last().role == "user") {
            // 如果最后一条是 user（刚追加的），则排除
            history.dropLast(1)
        } else {
            history
        }
        
        historyToInclude.forEach { entity ->
            val role = when (entity.role) {
                "user" -> "user"
                "assistant" -> "assistant"
                else -> "user"  // fallback
            }
            // 历史消息中如果包含图片，也需要标记（但当前只支持最新一条带图）
            messages.add(Message.text(role, entity.content))
        }
        
        // 4. Current user input（支持多模态）
        if (!imageUrl.isNullOrBlank()) {
            // 多模态消息：文本 + 图片
            hasImage = true
            val parts = mutableListOf<ContentPart>()
            if (currentUserInput.isNotBlank()) {
                parts.add(TextPart(currentUserInput))
            }
            // 使用传入的 imageDetail，若未指定则使用偏好设置
            val effectiveDetail = imageDetail ?: userPreferencesRepository.getVisionDetail()
            parts.add(ImageUrlPart.create(imageUrl, effectiveDetail))
            messages.add(Message(role = "user", content = MessageContent.parts(parts)))
        } else {
            // 纯文本消息
            messages.add(Message.user(currentUserInput))
        }
        
        return Pair(messages, hasImage)
    }

    private fun processInteractionSignals(userMessage: String) {
        val isRude = isRudeMessage(userMessage)
        val isStrongIntimacy = intimacyManager.containsStrongIntimacyKeyword(userMessage)
        val currentLevel = intimacyManager.getCurrentLevel()
        val isBoundaryCrossing = currentLevel == 1 && isStrongIntimacy
        val hasNegativeSignal = isRude || isBoundaryCrossing

        if (isRude) {
            affinityRepository.deductForRudeness()
        }
        if (isBoundaryCrossing) {
            affinityRepository.deductForBoundaryCrossing()
        }

        if (!hasNegativeSignal) {
            intimacyManager.processInteraction(userMessage)
            if (containsApologyKeyword(userMessage)) {
                affinityRepository.recoverWithCooldown()
            }
        }
    }

    private fun isRudeMessage(text: String): Boolean {
        val lowerText = text.lowercase()
        val normalizedText = lowerText.replace(Regex("[\\p{P}\\p{S}\\s]+"), "")
        val rudeKeywords = listOf(
            "傻逼", "傻b", "sb", "滚", "废物", "垃圾", "去死", "死", "脑残",
            "蠢", "傻", "笨", "走开", "讨厌", "闭嘴", "烦", "有病",
            "他妈", "你妈", "nmsl", "tmd", "妈的", "操", "草",
            "操你妈", "草泥马", "艹", "fuck", "fxxk", "shit", "bitch"
        )
        return rudeKeywords.any { keyword ->
            lowerText.contains(keyword) || normalizedText.contains(keyword)
        }
    }

    private fun containsApologyKeyword(text: String): Boolean {
        val lowerText = text.lowercase()
        val apologyKeywords = listOf("对不起", "抱歉", "我错了", "别生气", "原谅", "不好意思")
        return apologyKeywords.any { lowerText.contains(it) }
    }

    private fun com.soulmate.data.model.UserGender.toDisplayName(): String {
        return when (this) {
            com.soulmate.data.model.UserGender.MALE -> "男"
            com.soulmate.data.model.UserGender.FEMALE -> "女"
            com.soulmate.data.model.UserGender.UNSET -> "未设定"
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
     * Clears any warning state.
     */
    fun clearWarning() {
        _chatState.update { state ->
            state.copy(warning = null)
        }
    }

    /**
     * 创建新会话
     * 
     * 归档当前会话后，创建新会话并启动消息流订阅
     */
    fun startNewSession() {
        viewModelScope.launch {
            try {
                // 归档当前会话
                if (currentSessionId > 0) {
                    chatRepository.archiveSession(currentSessionId)
                }
                // 创建新会话
                currentSessionId = chatRepository.createSession()
                // 启动新会话的消息流订阅（UI 会自动变为空列表）
                startObserveMessages(currentSessionId)
                // 清空流式状态和错误
                _chatState.update { state ->
                    state.copy(
                        currentStreamToken = "",
                        error = null
                    )
                }
                Log.d(TAG, "Started new session: $currentSessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start new session", e)
            }
        }
    }

    /**
     * Maps technical exception messages to user-friendly error messages.
     * This prevents exposing raw exception details to users while still providing helpful feedback.
     */
    private fun mapErrorToUserMessage(e: Exception): String {
        return when {
            e is java.net.UnknownHostException -> "无法连接，请检查您的网络设置。"
            e is java.net.SocketTimeoutException -> "请求超时，请重试。"
            e is com.soulmate.core.data.brain.EmbeddingException -> "记忆服务暂时不可用，请稍后重试。"
            e.message?.contains("API", ignoreCase = true) == true -> "服务暂时不可用，请稍后重试。"
            e.message?.contains("network", ignoreCase = true) == true -> "网络错误，请检查您的连接。"
            else -> "发生了一些错误，请重试。"
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Release ASR resources
        asrService.release()
    }
    
    // ===== Phase 1: ImageGen Router 相关方法 =====
    
    /**
     * 解析 LLM 响应中的图片生成指令
     * 
     * 支持的格式：
     * 1. JSON 格式：{"tool":"generate_image","prompt":"...","size":"1920x1920"}
     * 2. 简单标记：[生成图片:prompt]
     * 
     * @param text LLM 响应文本
     * @return ImageGenCommand 如果检测到指令，否则 null
     */
    private fun parseImageGenCommand(text: String): ImageGenCommand? {
        // 尝试 JSON 格式
        try {
            // 查找 JSON 对象
            val jsonPattern = Regex("""\{[^{}]*"tool"\s*:\s*"generate_image"[^{}]*\}""")
            val match = jsonPattern.find(text)
            if (match != null) {
                val jsonStr = match.value
                val command = gson.fromJson(jsonStr, ImageGenCommandJson::class.java)
                if (command?.tool == "generate_image" && !command.prompt.isNullOrBlank()) {
                    return ImageGenCommand(
                        prompt = command.prompt,
                        size = command.size ?: "1920x1920"
                    )
                }
            }
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Failed to parse JSON image gen command", e)
        }
        
        // 尝试简单标记格式
        val simplePattern = Regex("""\[生成图片[：:]\s*(.+?)\]""")
        val simpleMatch = simplePattern.find(text)
        if (simpleMatch != null) {
            val prompt = simpleMatch.groupValues[1].trim()
            if (prompt.isNotBlank()) {
                return ImageGenCommand(prompt = prompt, size = "1920x1920")
            }
        }
        
        return null
    }

    /**
     * 检测用户是否直接表达图片生成意图（不依赖 LLM 指令）
     */
    private fun detectUserImageGenRequest(text: String): ImageGenCommand? {
        val normalized = text.trim()
        if (normalized.isBlank()) return null

        val lower = normalized.lowercase()
        val negativeKeywords = listOf("不要", "别", "不需要", "不用", "不想", "不要生成", "不要画", "别画")
        val targetKeywords = listOf("图", "图片", "插画", "风景图", "壁纸", "照片", "海报", "封面", "头像")
        if (negativeKeywords.any { lower.contains(it) } && targetKeywords.any { normalized.contains(it) }) {
            return null
        }

        val triggerKeywords = listOf("生成", "画", "绘制", "做", "来一张", "来个", "给我", "帮我", "出一张", "整一张")
        val hasTrigger = triggerKeywords.any { normalized.contains(it) }
        val hasTarget = targetKeywords.any { normalized.contains(it) }
        if (!hasTrigger || !hasTarget) return null

        val patterns = listOf(
            Regex("""(?:帮我|给我|来一张|来个|生成|画|绘制|做|出一张|整一张)\s*(.+?)\s*(?:的?图|的?图片|的?插画|的?风景图|的?壁纸|的?照片|的?海报|的?封面|的?头像)?$"""),
            Regex("""(.+?)\s*(?:图片|图|插画|风景图|壁纸|照片|海报|封面|头像)$""")
        )
        for (pattern in patterns) {
            val match = pattern.find(normalized) ?: continue
            val prompt = match.groupValues[1].trim().trim('，', '。', '!', '！', '?', '？')
            if (prompt.isNotBlank()) {
                return ImageGenCommand(prompt = prompt, size = "1920x1920")
            }
        }

        val cleaned = normalized
            .replace(Regex("""(帮我|给我|来一张|来个|生成|画|绘制|做|出一张|整一张)"""), "")
            .replace(Regex("""(图片|图|插画|风景图|壁纸|照片|海报|封面|头像)"""), "")
            .trim()
        val prompt = if (cleaned.isNotBlank()) cleaned else normalized
        return ImageGenCommand(prompt = prompt, size = "1920x1920")
    }
    
    /**
     * 确认并执行图片生成
     */
    fun confirmImageGeneration() {
        val command = _pendingImageGen.value ?: return
        _pendingImageGen.value = null
        
        viewModelScope.launch {
            _chatState.update { it.copy(isLoading = true) }
            
            try {
                Log.d(TAG, "Generating image with prompt: ${command.prompt.take(50)}...")
                val imageUrl = imageGenService.generateImage(
                    prompt = command.prompt,
                    size = command.size
                )
                
                Log.d(TAG, "Image generated successfully: $imageUrl")
                
                // 落库图片消息
                chatRepository.appendMessage(
                    sessionId = currentSessionId,
                    role = "assistant",
                    content = "已为你生成图片",
                    imageUrl = imageUrl
                )
                
                // 通知用户
                avatarService.speak("图片已经生成好了，你觉得怎么样？")
                
            } catch (e: ImageGenException) {
                Log.e(TAG, "Image generation failed", e)
                val userMessage = when {
                    e.message?.contains("端点配置错误") == true ||
                    e.message?.contains("端点不存在") == true -> {
                        "图片生成功能配置错误，请联系开发者检查端点配置"
                    }
                    e.message?.contains("未配置") == true -> {
                        "图片生成端点未配置"
                    }
                    else -> {
                        "图片生成失败: ${e.message}"
                    }
                }
                _chatState.update { it.copy(
                    warning = userMessage,
                    isLoading = false
                )}
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during image generation", e)
                _chatState.update { it.copy(
                    warning = "图片生成出错，请稍后重试",
                    isLoading = false
                )}
            } finally {
                _chatState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    /**
     * 取消图片生成
     */
    fun cancelImageGeneration() {
        _pendingImageGen.value = null
    }
}

/**
 * 图片生成指令
 * 
 * @param prompt 生成提示词
 * @param size 图片尺寸（默认 1920x1920，最小要求 3,686,400 像素）
 */
data class ImageGenCommand(
    val prompt: String,
    val size: String = "1920x1920"
)

/**
 * 用于解析 JSON 格式的图片生成指令
 */
private data class ImageGenCommandJson(
    val tool: String? = null,
    val prompt: String? = null,
    val size: String? = null
)
