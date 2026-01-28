package com.soulmate.data.service

import android.content.Context
import android.media.AudioFormat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.alibaba.idst.nui.CommonUtils
import com.alibaba.idst.nui.Constants
import com.alibaba.idst.nui.INativeNuiCallback
import com.alibaba.idst.nui.NativeNui
import com.alibaba.idst.nui.AsrResult
import com.alibaba.idst.nui.KwsResult
import com.soulmate.BuildConfig
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
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * AliyunASRService - 阿里云语音识别服务
 * 
 * 使用阿里云 NUI SDK 实现实时语音识别功能。
 * 支持实时展示中间识别结果和最终识别结果。
 */
@Singleton
class AliyunASRService @Inject constructor(
    @ApplicationContext private val context: Context
) : INativeNuiCallback {

    companion object {
        private const val TAG = "AliyunASRService"
        
        // Audio configuration
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val WAVE_FRAME_SIZE = 20 * 2 * 1 * 16000 / 1000 // 20ms frame
    }

    // Coroutine scope for emitting events
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // State management
    private val _asrState = MutableStateFlow<ASRState>(ASRState.Idle)
    val asrState: StateFlow<ASRState> = _asrState.asStateFlow()

    // Recognition results
    private val _recognitionResult = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val recognitionResult: SharedFlow<String> = _recognitionResult.asSharedFlow()

    // Partial (intermediate) results
    private val _partialResult = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val partialResult: SharedFlow<String> = _partialResult.asSharedFlow()

    private var isInitialized = false
    private var audioRecorder: AudioRecord? = null
    private var isRecording = false
    
    // 保存最后的部分识别结果，用于在无法获取最终结果时作为备份
    private var lastPartialResult: String = ""
    
    // NUI SDK instance
    private val nuiInstance: NativeNui by lazy { NativeNui.GetInstance() }

    // Mutex for thread-safe initialization
    private val initMutex = Mutex()

    /**
     * 初始化 ASR 服务
     */
    suspend fun initialize(): Boolean {
        // First check (fast return)
        if (isInitialized) {
            Log.d(TAG, "Already initialized (fast path)")
            return true
        }

        return initMutex.withLock {
            // Double check inside lock
            if (isInitialized) {
                Log.d(TAG, "Already initialized (safe path)")
                return true
            }

            try {
                // 先释放可能存在的旧实例，避免单例冲突
                try {
                    nuiInstance.release()
                    Log.d(TAG, "Released previous NUI instance")
                } catch (e: Exception) {
                    Log.d(TAG, "No previous instance to release")
                }
                
                // Copy assets to workspace (Optimized)
                val workPath = CommonUtils.getModelPath(context)
                Log.d(TAG, "Workspace path: $workPath")
                
                // Copy assets on IO thread
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    copyAssets(workPath)
                }

                // Ensure debug directory exists
                val debugDir = File("$workPath/debug")
                if (!debugDir.exists()) {
                    debugDir.mkdirs()
                }

                // Generate token (Real token from Aliyun)
                val token = generateToken()
                
                if (token.isBlank()) {
                    Log.e(TAG, "Failed to generate token")
                    _asrState.value = ASRState.Error("获取Token失败")
                    return@withLock false
                }
                
                // Get device ID
                val deviceId = java.util.UUID.randomUUID().toString()

                // Build initialization parameters
                val initParams = buildInitParams(workPath, token, deviceId)
                
                Log.d(TAG, "Initializing NUI SDK with params: $initParams")

                // Initialize NUI instance
                val result = nuiInstance.initialize(
                    this,
                    initParams,
                    Constants.LogLevel.LOG_LEVEL_NONE,
                    false
                )

                if (result == Constants.NuiResultCode.SUCCESS) {
                    isInitialized = true
                    Log.i(TAG, "NUI SDK initialized successfully")
                    
                    // Set recognition parameters
                    setRecognitionParams()
                    
                    Log.d(TAG, "Initialization completed, ready for startDialog")
                    return@withLock true
                } else {
                    val errorMsg = getErrorCodeMessage(result)
                    Log.e(TAG, "NUI SDK initialization failed: result=$result, message=$errorMsg")
                    _asrState.value = ASRState.Error("ASR初始化失败: $errorMsg (代码:$result)")
                    isInitialized = false
                    return@withLock false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize NUI SDK", e)
                _asrState.value = ASRState.Error("ASR初始化异常: ${e.message}")
                return@withLock false
            }
        }
    }

    /**
     * 开始语音识别
     */
    // Silence detection variables
    private var enableAutoStop = false
    private var lastVoiceTime = 0L
    private var silenceCheckJob: Job? = null
    
    /**
     * 开始语音识别
     * @param enableAutoStop 是否启用自动停止（模拟 VAD，静音 1.5s 后自动发送）
     */
    suspend fun startRecognition(enableAutoStop: Boolean = false): Boolean {
        if (!isInitialized) {
            Log.d(TAG, "Not initialized, initializing now...")
            if (!initialize()) {
                _asrState.value = ASRState.Error("初始化失败")
                return false
            }
            
            // 首次初始化后，等待一小段时间确保 dialog 引擎完全创建
            Log.d(TAG, "First initialization completed, waiting for dialog engine to be ready...")
            kotlinx.coroutines.delay(800)
        }

        try {
            Log.d(TAG, "Starting recognition... AutoStop enabled: $enableAutoStop")
            
            if (!isInitialized) {
                Log.e(TAG, "SDK not initialized, cannot start dialog")
                _asrState.value = ASRState.Error("启动识别失败: SDK 未初始化")
                return false
            }
            
            if (!checkMicrophonePermission()) {
                Log.e(TAG, "Microphone permission not granted")
                _asrState.value = ASRState.Error("启动识别失败: 缺少麦克风权限")
                return false
            }
            
            this.enableAutoStop = enableAutoStop
            this.lastVoiceTime = 0L // Reset
            
            // Always use P2T since VAD mode was unreliable
            Log.d(TAG, "Calling startDialog with TYPE_P2T (Manual VAD handled by app)")
            _asrState.value = ASRState.Listening

            val result = nuiInstance.startDialog(
                Constants.VadMode.TYPE_P2T,
                "{}"
            )
            
            Log.d(TAG, "startDialog returned: result=$result")

            if (result != Constants.NuiResultCode.SUCCESS) {
                // 处理 240007: 根据用户反馈，此错误在首次启动时不影响功能，视为成功
                if (result == 240007) {
                    Log.w(TAG, "Dialog not created (240007), but treating as success per configuration.")
                    // 直接视为成功
                    startSilenceWatchdog() 
                    return true
                }

                val errorMsg = getErrorCodeMessage(result)
                Log.e(TAG, "Failed to start dialog: result=$result, message=$errorMsg")
                _asrState.value = ASRState.Error("启动识别失败: $errorMsg (错误码: $result)")
                return false
            }

            Log.i(TAG, "Recognition started successfully")
            startSilenceWatchdog() // Start watchdog
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when starting recognition", e)
            _asrState.value = ASRState.Error("启动识别失败: 缺少麦克风权限")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recognition", e)
            _asrState.value = ASRState.Error("启动识别异常: ${e.message}")
            return false
        }
    }
    
    private fun startSilenceWatchdog() {
        if (!enableAutoStop) return
        
        silenceCheckJob?.cancel()
        silenceCheckJob = serviceScope.launch {
            Log.d(TAG, "Silence watchdog started")
            while (isActive && isRecording) { // Only run while recording
                delay(200) // Check every 200ms
                
                if (lastVoiceTime > 0 && System.currentTimeMillis() - lastVoiceTime > 1500) {
                     Log.i(TAG, "Silence detected (>1.5s), stopping recognition automatically")
                     stopRecognition()
                     break
                }
            }
        }
    }

    /**
     * 停止语音识别（等待最终结果）
     * 如果 SDK 没有返回最终结果，会使用最后的部分识别结果作为备份
     */
    fun stopRecognition() {
        Log.d(TAG, "Stopping recognition... lastPartialResult='$lastPartialResult'")
        silenceCheckJob?.cancel() // Stop watchdog
        _asrState.value = ASRState.Recognizing
        
        // 保存当前的 partial result，因为 SDK 事件可能不会触发
        val currentPartialResult = lastPartialResult
        
        try {
            nuiInstance.stopDialog()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recognition", e)
        }
        
        // 启动协程，延迟后检查是否需要发送备份结果
        serviceScope.launch {
            // 等待一小段时间让 SDK 事件有机会触发
            kotlinx.coroutines.delay(500)
            
            // 如果 lastPartialResult 仍然有值（说明没有收到 EVENT_ASR_RESULT）
            // 或者状态仍然是 Recognizing，则发送备份结果
            if (lastPartialResult.isNotBlank()) {
                Log.i(TAG, "Sending backup result after timeout: $lastPartialResult")
                val resultToSend = lastPartialResult
                lastPartialResult = ""
                _recognitionResult.emit(resultToSend)
            } else if (currentPartialResult.isNotBlank() && _asrState.value == ASRState.Recognizing) {
                // 使用保存的结果作为备份
                Log.i(TAG, "Sending saved partial result: $currentPartialResult")
                _recognitionResult.emit(currentPartialResult)
            }
            
            _asrState.value = ASRState.Idle
            stopAudioRecording()
        }
    }

    /**
     * 取消语音识别（不等待结果）
     */
    fun cancelRecognition() {
        Log.d(TAG, "Cancelling recognition...")
        _asrState.value = ASRState.Idle
        stopAudioRecording()
        silenceCheckJob?.cancel()
        
        try {
            nuiInstance.cancelDialog()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel recognition", e)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "Releasing ASR resources...")
        stopAudioRecording()
        isInitialized = false
        _asrState.value = ASRState.Idle
        
        try {
            nuiInstance.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release NUI SDK", e)
        }
    }

    fun isInitialized(): Boolean = isInitialized

    // ==================== INativeNuiCallback Implementation ====================

    override fun onNuiEventCallback(
        event: Constants.NuiEvent,
        resultCode: Int,
        arg2: Int,
        kwsResult: KwsResult?,
        asrResult: AsrResult?
    ) {
        Log.d(TAG, "Event: $event, resultCode: $resultCode")

        when (event) {
            Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT -> {
                asrResult?.asrResult?.let { result ->
                    val text = parseAsrResult(result, false)
                    if (text.isNotBlank()) {
                        Log.d(TAG, "Partial result: $text")
                        // 保存最后的部分识别结果
                        lastPartialResult = text
                        
                        // Update silence detection timer
                        lastVoiceTime = System.currentTimeMillis()
                        
                        serviceScope.launch {
                            _partialResult.emit(text)
                        }
                    }
                }
            }
            
            Constants.NuiEvent.EVENT_ASR_RESULT -> {
                asrResult?.asrResult?.let { result ->
                    val text = parseAsrResult(result, true)
                    if (text.isNotBlank()) {
                        Log.i(TAG, "Final result: $text")
                        // 清空 partial result，因为我们收到了最终结果
                        lastPartialResult = ""
                        serviceScope.launch {
                            _recognitionResult.emit(text)
                        }
                    }
                }
            }
            
            Constants.NuiEvent.EVENT_DIALOG_EX -> {
                // Dialog finished
                Log.d(TAG, "Dialog finished")
                stopAudioRecording()
                
                // 如果没有收到 EVENT_ASR_RESULT，使用最后的 partial result 作为备份
                if (lastPartialResult.isNotBlank()) {
                    Log.i(TAG, "Using last partial result as final: $lastPartialResult")
                    val resultToSend = lastPartialResult
                    lastPartialResult = ""
                    serviceScope.launch {
                        _recognitionResult.emit(resultToSend)
                    }
                }
                
                _asrState.value = ASRState.Idle
            }
            
            Constants.NuiEvent.EVENT_ASR_ERROR -> {
                Log.e(TAG, "ASR error: $resultCode")
                stopAudioRecording()
                lastPartialResult = ""
                _asrState.value = ASRState.Error("识别错误: $resultCode")
            }
            
            else -> {
                Log.d(TAG, "Other event: $event")
            }
        }
    }

    override fun onNuiNeedAudioData(buffer: ByteArray, len: Int): Int {
        // Called when SDK needs audio data
        if (!isRecording || audioRecorder == null) {
            return 0
        }

        return try {
            val read = audioRecorder?.read(buffer, 0, len) ?: 0
            if (read < 0) {
                Log.e(TAG, "AudioRecord read error: $read")
                0
            } else {
                read
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read audio data", e)
            0
        }
    }

    override fun onNuiAudioStateChanged(state: Constants.AudioState) {
        Log.d(TAG, "Audio state changed: $state")
        
        when (state) {
            Constants.AudioState.STATE_OPEN -> {
                startAudioRecording()
            }
            Constants.AudioState.STATE_CLOSE -> {
                stopAudioRecording()
            }
            Constants.AudioState.STATE_PAUSE -> {
                // Pause audio recording
            }
            else -> {}
        }
    }

    override fun onNuiAudioRMSChanged(rms: Float) {
        // Audio RMS level changed - can be used for volume indicator
    }

    override fun onNuiVprEventCallback(event: Constants.NuiVprEvent?) {
        // Voice print recognition event
    }

    // ==================== Private Methods ====================

    private fun buildInitParams(workPath: String, token: String, deviceId: String): String {
        val params = JSONObject().apply {
            // 必需参数 (根据阿里云官方 SDK 示例)
            put("app_key", BuildConfig.ALIYUN_ASR_APP_KEY)
            put("token", token)
            put("device_id", deviceId)
            
            // WebSocket URL (必需)
            put("url", "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1")
            
            // 服务模式 (必需): 1 = FullCloud (纯云端)
            put("service_mode", Constants.ModeFullCloud)
            
            // 可选: workspace (V2.6.2+ 纯云端功能可不设置)
            // put("workspace", workPath)
            
            // 调试参数
            put("debug_path", "$workPath/debug")
            put("save_wav", "true")
        }
        Log.d(TAG, "Init params: ${params.toString()}")
        return params.toString()
    }

    private fun setRecognitionParams() {
        // 设置实时语音识别参数 (根据阿里云官方文档)
        val params = JSONObject().apply {
            // service_type=4 表示实时语音识别
            put("service_type", 4)
            
            put("nls_config", JSONObject().apply {
                put("sr_format", "opus")
                put("sample_rate", SAMPLE_RATE)
                put("enable_intermediate_result", true)
                put("enable_punctuation_prediction", true)
                put("enable_inverse_text_normalization", true)
            })
        }
        Log.d(TAG, "Recognition params: ${params.toString()}")
        nuiInstance.setParams(params.toString())
    }

    private fun parseAsrResult(result: String, isFinal: Boolean): String {
        return try {
            val json = JSONObject(result)
            val payload = json.optJSONObject("payload")
            payload?.optString("result", "") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ASR result", e)
            ""
        }
    }

    private fun startAudioRecording() {
        if (isRecording) {
            return
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            audioRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize.coerceAtLeast(WAVE_FRAME_SIZE * 4)
            )

            audioRecorder?.startRecording()
            isRecording = true
            Log.i(TAG, "Audio recording started")
        } catch (e: SecurityException) {
            Log.e(TAG, "No microphone permission", e)
            _asrState.value = ASRState.Error("没有麦克风权限")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            _asrState.value = ASRState.Error("录音启动失败")
        }
    }

    private fun stopAudioRecording() {
        if (!isRecording) {
            return
        }

        try {
            audioRecorder?.stop()
            audioRecorder?.release()
            audioRecorder = null
            isRecording = false
            Log.i(TAG, "Audio recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop audio recording", e)
        }
    }

    /**
     * 通过阿里云 OpenAPI 获取 Access Token
     * 使用 HMAC-SHA1 签名方式
     */
    private suspend fun generateToken(): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val accessKeyId = BuildConfig.ALIYUN_ACCESS_KEY_ID
                val accessKeySecret = BuildConfig.ALIYUN_ACCESS_KEY_SECRET
                
                if (accessKeyId.isBlank() || accessKeySecret.isBlank()) {
                    Log.e(TAG, "AccessKey ID or Secret is empty")
                    return@withContext ""
                }
                
                // 构建请求参数
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date())
                
                val signatureNonce = java.util.UUID.randomUUID().toString()
                
                val params = sortedMapOf(
                    "AccessKeyId" to accessKeyId,
                    "Action" to "CreateToken",
                    "Format" to "JSON",
                    "RegionId" to "cn-shanghai",
                    "SignatureMethod" to "HMAC-SHA1",
                    "SignatureNonce" to signatureNonce,
                    "SignatureVersion" to "1.0",
                    "Timestamp" to timestamp,
                    "Version" to "2019-02-28"
                )
                
                // 构建待签名字符串
                val sortedQueryString = params.entries.joinToString("&") { (key, value) ->
                    "${percentEncode(key)}=${percentEncode(value)}"
                }
                
                val stringToSign = "GET&${percentEncode("/")}&${percentEncode(sortedQueryString)}"
                
                // 计算签名
                val signature = calculateSignature(stringToSign, "$accessKeySecret&")
                
                // 构建最终 URL
                val finalUrl = "https://nls-meta.cn-shanghai.aliyuncs.com/?" +
                        sortedQueryString + "&Signature=${percentEncode(signature)}"
                
                Log.d(TAG, "Requesting token from: $finalUrl")
                
                // 发送 HTTP 请求
                val url = java.net.URL(finalUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Token response: $response")
                    
                    // 解析 JSON 响应
                    val jsonResponse = JSONObject(response)
                    val token = jsonResponse.optJSONObject("Token")?.optString("Id", "") ?: ""
                    
                    if (token.isNotBlank()) {
                        Log.i(TAG, "Token obtained successfully")
                        return@withContext token
                    } else {
                        Log.e(TAG, "Token not found in response: $response")
                    }
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.e(TAG, "Failed to get token, response code: $responseCode, error: $errorResponse")
                }
                
                ""
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate token", e)
                ""
            }
        }
    }
    
    /**
     * URL 编码（符合阿里云签名规范）
     */
    private fun percentEncode(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }
    
    /**
     * 使用 HMAC-SHA1 计算签名
     */
    private fun calculateSignature(stringToSign: String, secretKey: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA1")
        mac.init(javax.crypto.spec.SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val signData = mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(signData, android.util.Base64.NO_WRAP)
    }



    private fun copyAssets(destPath: String) {
        try {
            val destDir = File(destPath)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            // Copy nui.json
            copyAssetFile("nui.json", "$destPath/nui.json")
            
            // Copy cei.json
            copyAssetFile("cei.json", "$destPath/cei.json")
            
            // Copy tts directory
            val ttsDir = File("$destPath/tts")
            if (!ttsDir.exists()) {
                ttsDir.mkdirs()
            }
            copyAssetFile("tts/parameter.cfg", "$destPath/tts/parameter.cfg")
            
            Log.d(TAG, "Assets checking/copying completed")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy assets", e)
        }
    }

    private fun copyAssetFile(assetName: String, destPath: String) {
        val destFile = File(destPath)
        
        // Optimize: skip if file exists and has content
        if (destFile.exists() && destFile.length() > 0) {
            // Log.v(TAG, "Asset $assetName already exists, skipping copy")
            return
        }

        try {
            if (destFile.exists()) {
                destFile.delete()
            }
            
            context.assets.open(assetName).use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Copied asset $assetName to $destPath")
        } catch (e: IOException) {
            Log.w(TAG, "Asset file not found: $assetName")
        }
    }
    
    /**
     * 检查麦克风权限
     */
    private fun checkMicrophonePermission(): Boolean {
        return try {
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check microphone permission", e)
            false
        }
    }
    
    /**
     * 将错误码转换为可读的错误信息
     * 基于阿里云 NUI SDK 的错误码定义
     */
    private fun getErrorCodeMessage(errorCode: Int): String {
        return when (errorCode) {
            // 通用错误码
            140001 -> "引擎未创建，请检查是否成功初始化"
            140008 -> "鉴权失败，请检查 token 和 app_key"
            140011 -> "方法调用不符合当前状态"
            140013 -> "方法调用不符合当前状态（未初始化）"
            
            // Token 相关
            144003 -> "Token 过期或无效，请重新获取"
            240068 -> "403 Forbidden，Token 无效或过期"
            170807 -> "SecurityToken 过期或无效"
            
            // 初始化相关
            240005 -> "初始化参数无效，请检查 app_key、token、url 等参数"
            240006 -> "未设置回调监听器"
            240007 -> "Dialog 未创建，请确保 SDK 已正确初始化"
            240008 -> "SDK 内部核心引擎未成功初始化"
            240011 -> "SDK 未成功初始化"
            240040 -> "本地引擎初始化失败，资源文件可能损坏"
            
            // 网络相关
            240063 -> "SSL 错误，可能为 SSL 建连失败或 token 无效"
            240070 -> "鉴权失败，请查看日志确定具体问题"
            
            // 音频相关
            240052 -> "2秒未传入音频数据，请检查录音权限或录音模块是否被占用"
            41010105 -> "长时间未收到人声，触发静音超时"
            
            // 参数相关
            240002 -> "设置的参数不正确，请检查 JSON 参数格式"
            144103 -> "设置参数无效，请参考接口文档检查参数"
            
            // 服务相关
            40000004 -> "长时间未收到指令或音频"
            40000010 -> "账号试用期已过，请开通商用版或检查账号权限"
            10000016 -> "未成功连接服务，请检查参数及服务地址"
            
            // 其他
            999999 -> "库加载失败，可能是库不支持当前架构或库加载时崩溃"
            
            // 默认情况
            else -> "未知错误"
        }
    }
}

sealed class ASRState {
    object Idle : ASRState()
    object Listening : ASRState()
    object Recognizing : ASRState()
    data class Error(val message: String) : ASRState()
}
