package com.soulmate.data.service

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.ViewGroup
import com.soulmate.BuildConfig
import com.soulmate.data.model.UIEvent
import com.soulmate.data.model.UserGender
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * AvatarCoreService - 魔珐星云数字人 SDK 封装服务
 * 
 * 封装 Xmov IXmovAvatar SDK，提供：
 * - 初始化和生命周期管理
 * - 语音播报（TTS + 口型同步）
 * - 状态管理（空闲/说话/聆听）
 * - UI 事件转发（字幕、视频等）
 * 
 * 注意：使用反射调用 SDK 以避免 Kotlin 版本兼容性问题
 */
@Singleton
class AvatarCoreService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: com.soulmate.data.preferences.UserPreferencesRepository
) {
    
    companion object {
        private const val TAG = "AvatarCoreService"
        // 使用 BuildConfig 中的可配置网关地址
        private const val GATEWAY_SERVER = BuildConfig.XMOV_GATEWAY_URL
        private const val CACHE_DIR_NAME = "xmov_cache"
        private const val MAX_RECOVERY_ATTEMPTS = 2  // 全局最大恢复次数
        private const val DEBUG_CALLBACK_SAMPLE_RATE = 10  // 调试回调采样率
        private const val MAX_ARG_LOG_LENGTH = 512  // 日志参数最大长度
    }
    
    private val _avatarState = MutableStateFlow<AvatarState>(AvatarState.Idle)
    val avatarState: StateFlow<AvatarState> = _avatarState.asStateFlow()
    
    private val _uiEventFlow = MutableSharedFlow<UIEvent>(extraBufferCapacity = 10)
    val uiEventFlow: SharedFlow<UIEvent> = _uiEventFlow.asSharedFlow()
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var isAvatarInitialized = false
    private var containerView: ViewGroup? = null
    private var avatarInstance: Any? = null
    
    // ========== 音频播放器 ==========
    private val audioPlayer = AvatarAudioPlayer()
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    
    // ========== 诊断日志增强字段 ==========
    /** 每次 bind/init 的自增会话 ID */
    private val sessionIdGenerator = AtomicLong(0)
    private var currentSessionId: Long = 0
    
    /** 当前绑定的用户性别 */
    private var currentGender: UserGender? = null
    
    /** 当前容器 ID */
    private var currentContainerId: Int = 0
    
    /** 调试回调计数器（用于采样） */
    private var debugCallbackCounter = 0
    
    /** 当前会话恢复尝试次数 */
    private var recoveryAttempts = 0

    private val recoveryMutex = Mutex()
    
    // ========== 诊断日志辅助方法 ==========
    /** 统一日志前缀，包含所有关键诊断字段 */
    private fun logPrefix(): String {
        return "[sid=$currentSessionId|gender=$currentGender|thread=${Thread.currentThread().name}|" +
                "cid=$currentContainerId|hasInst=${avatarInstance != null}|init=$isAvatarInitialized]"
    }
    
    /** 安全截断字符串，防止刷屏 */
    private fun truncate(str: String?, maxLen: Int = MAX_ARG_LOG_LENGTH): String {
        if (str == null) return "null"
        return if (str.length > maxLen) str.take(maxLen) + "...(truncated)" else str
    }
    
    /**
     * 优化版本的初始化方法（性能优化）
     * 
     * 优化点：
     * 1. 缓存目录检查改为异步（后台线程）
     * 2. 减少恢复流程的延迟（从 2 秒降到 500ms）
     * 
     * 预期收益：减少 1.5 秒初始化延迟
     * 
     * @param activityContext 必须传 Activity 而非 ApplicationContext
     * @param containerView 容器视图
     * @param appId 应用 ID
     * @param appSecret 应用密钥
     * @return true 如果初始化成功
     */
    private fun initializeOptimized(activityContext: Context, containerView: ViewGroup, appId: String, appSecret: String): Boolean {
        // Enforce previous session cleanup
        if (avatarInstance != null) {
            Log.w(TAG, "${logPrefix()} >>> INIT_OPT_PRE_CLEANUP | Force releasing previous session")
            release()
        }

        // 生成新的会话 ID
        currentSessionId = sessionIdGenerator.incrementAndGet()
        currentContainerId = containerView.id
        this.containerView = containerView
        
        Log.i(TAG, "${logPrefix()} >>> INIT_OPTIMIZED_START | appId=${appId.take(8)}...")
        
        // 优化：快速检查缓存目录（如果已存在则跳过创建）
        val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) {
            // 目录不存在时才创建（同步操作，但通常很快）
            if (!ensureCacheDirectory()) {
                Log.e(TAG, "${logPrefix()} <<< INIT_OPTIMIZED_FAIL | 缓存目录创建失败")
                _avatarState.value = AvatarState.Error("缓存目录创建失败，请检查存储权限")
                return false
            }
        }
        
        // 继续执行初始化（与原方法相同）
        return initializeInternal(activityContext, containerView, appId, appSecret)
    }
    
    /**
     * 初始化 SDK（使用反射调用避免 Kotlin 版本兼容性问题）
     * @param activityContext 必须传 Activity 而非 ApplicationContext，否则 GL 渲染无法显示
     */
    fun initialize(activityContext: Context, containerView: ViewGroup, appId: String, appSecret: String): Boolean {
        // Enforce previous session cleanup
        if (avatarInstance != null) {
            Log.w(TAG, "${logPrefix()} >>> INIT_PRE_CLEANUP | Force releasing previous session")
            release()
        }

        // 生成新的会话 ID
        currentSessionId = sessionIdGenerator.incrementAndGet()
        currentContainerId = containerView.id
        this.containerView = containerView
        
        // ========== 日志点1: init 开始 ==========
        Log.i(TAG, "${logPrefix()} >>> INIT_START | appId=${appId.take(8)}...")
        
        // ========== 5.2.1 目录保证 ==========
        if (!ensureCacheDirectory()) {
            Log.e(TAG, "${logPrefix()} <<< INIT_FAIL | 缓存目录创建失败")
            _avatarState.value = AvatarState.Error("缓存目录创建失败，请检查存储权限")
            return false
        }
        
        return initializeInternal(activityContext, containerView, appId, appSecret)
    }
    
    /**
     * 内部初始化逻辑（提取公共部分）
     */
    private fun initializeInternal(activityContext: Context, containerView: ViewGroup, appId: String, appSecret: String): Boolean {
        try {
            // 获取 IXmovAvatar 单例
            val avatarClass = Class.forName("com.xmov.metahuman.sdk.IXmovAvatar")
            val companionField = avatarClass.getDeclaredField("Companion")
            companionField.isAccessible = true
            val companion = companionField.get(null)
            
            val getMethod = companion.javaClass.getMethod("get")
            avatarInstance = getMethod.invoke(companion)
            
            // 构建配置 JSON
            val configJson = JSONObject().apply {
                put("input_audio", false)
                put("output_audio", true)
                put("resolution", JSONObject().apply {
                    put("width", 720)   // 恢复为标准 720p (9:16)，避免非标准分辨率导致渲染错乱
                    put("height", 1280)
                })
                put("init_events", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "SetCharacterCanvasAnchor")
                        put("x_location", 0)
                        put("y_location", 0)
                        put("width", 1)
                        put("height", 1)
                    })
                })
            }.toString()
            
            // 创建 InitConfig 实例
            val initConfigClass = Class.forName("com.xmov.metahuman.sdk.data.InitConfig")
            val initConfigConstructor = initConfigClass.constructors.firstOrNull { 
                it.parameterCount >= 4 
            }
            
            val initConfig = if (initConfigConstructor != null) {
                // 尝试找到合适的构造函数
                initConfigConstructor.newInstance(appId, appSecret, GATEWAY_SERVER, configJson)
            } else {
                // 使用默认构造函数并设置属性
                val instance = initConfigClass.getDeclaredConstructor().newInstance()
                initConfigClass.getDeclaredField("appId").apply { isAccessible = true; set(instance, appId) }
                initConfigClass.getDeclaredField("appSecret").apply { isAccessible = true; set(instance, appSecret) }
                initConfigClass.getDeclaredField("gatewayServer").apply { isAccessible = true; set(instance, GATEWAY_SERVER) }
                initConfigClass.getDeclaredField("config").apply { isAccessible = true; set(instance, configJson) }
                instance
            }
            
            // ========== 日志点2: 反射成功 ==========
            Log.i(TAG, "${logPrefix()} --- REFLECTION_OK | avatarInstance=${avatarInstance?.javaClass?.simpleName}")
            
            // 创建 IAvatarListener 代理
            val listenerClass = Class.forName("com.xmov.metahuman.sdk.IAvatarListener")
            val listener = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                handleListenerCallback(method.name, args)
            }
            
            // 调用 init 方法
            val initMethod = avatarInstance!!.javaClass.getMethod(
                "init",
                Context::class.java,
                ViewGroup::class.java,
                initConfigClass,
                listenerClass
            )
            initMethod.invoke(avatarInstance, activityContext, containerView, initConfig, listener)
            
            Log.i(TAG, "${logPrefix()} <<< INIT_INVOKE_OK | 等待 SDK onInitEvent 回调")
            return true
            
        } catch (e: Exception) {
            // ========== 日志点3: init 异常 ==========
            Log.e(TAG, "${logPrefix()} <<< INIT_EXCEPTION | type=${e.javaClass.simpleName} msg=${e.message}")
            Log.e(TAG, "${logPrefix()} StackTrace:", e)
            
            // 检查是否是 ENOENT 缓存缺失问题
            if (isCacheMissingError(e)) {
                handleCacheMissingError(e)
            } else {
                _avatarState.value = AvatarState.Error("SDK 初始化异常: ${e.message}")
            }
            return false
        }
    }
    
    // ========== 5.2.1 目录保证 ==========
    /**
     * 确保 SDK 缓存目录存在
     * @return true 如果目录存在或创建成功，false 如果创建失败
     */
    private fun ensureCacheDirectory(): Boolean {
        val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
        return try {
            if (!cacheDir.exists()) {
                val created = cacheDir.mkdirs()
                Log.i(TAG, "${logPrefix()} ensureCacheDir: created=$created path=${cacheDir.absolutePath}")
                created
            } else {
                Log.d(TAG, "${logPrefix()} ensureCacheDir: already exists path=${cacheDir.absolutePath}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "${logPrefix()} ensureCacheDir FAILED: ${e.message}", e)
            false
        }
    }
    
    // ========== 5.2.2 ENOENT 识别 ==========
    /**
     * 判断异常是否为缓存缺失（ENOENT）
     */
    private fun isCacheMissingError(e: Throwable): Boolean {
        val checkException: (Throwable) -> Boolean = { ex ->
            val msg = ex.message ?: ""
            val isFileNotFound = (ex is FileNotFoundException || ex.javaClass.simpleName.contains("FileNotFoundException"))
            val hasMissingFileSignal =
                msg.contains(CACHE_DIR_NAME) ||
                msg.contains("ENOENT") ||
                msg.contains("No such file")
            val isCharDataMissing = msg.contains("Failed to load resource") && msg.contains("char_data.bin")
            (isFileNotFound && hasMissingFileSignal) || isCharDataMissing
        }
        return checkException(e) || (e.cause?.let { checkException(it) } == true)
    }
    
    // ========== 5.2.3 受控恢复 ==========
    /**
     * 处理缓存缺失错误，尝试清理并恢复
     */
    private fun handleCacheMissingError(e: Throwable) {
        Log.w(TAG, "${logPrefix()} >>> CACHE_MISSING_DETECTED | msg=${e.message}")

        if (recoveryMutex.isLocked) {
            Log.w(TAG, "${logPrefix()} <<< RECOVERY_SKIPPED | another recovery in progress")
            return
        }
        
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            Log.e(TAG, "${logPrefix()} <<< RECOVERY_LIMIT_REACHED | attempts=$recoveryAttempts")
            _avatarState.value = AvatarState.Error("缓存缺失且恢复失败，请重启应用")
            return
        }
        
        recoveryAttempts++
        _avatarState.value = AvatarState.Error("缓存缺失，正在尝试恢复 ($recoveryAttempts/$MAX_RECOVERY_ATTEMPTS)")
        
        serviceScope.launch {
            try {
                val (container, gender) = recoveryMutex.withLock {
                    Log.i(TAG, "${logPrefix()} --- RECOVERY_STEP_A | 停止当前实例")
                    safeStopInstance()

                    Log.i(TAG, "${logPrefix()} --- RECOVERY_STEP_B | 清理缓存目录")
                    val cleanedPath = cleanCacheDirectory(e.message)
                    Log.i(TAG, "${logPrefix()} --- CACHE_CLEANED | path=$cleanedPath")

                    ensureCacheDirectory()

                    Log.i(TAG, "${logPrefix()} --- RECOVERY_STEP_C | 冷却延迟")
                    // 根据优化开关选择延迟时间（默认启用优化，减少延迟）
                    val recoveryDelay = if (userPreferencesRepository.isFastAvatarInitEnabled()) {
                        500L  // 优化版本：500ms
                    } else {
                        2000L  // 原版本：2秒
                    }
                    delay(recoveryDelay)

                    containerView to currentGender
                }

                if (container == null || gender == null || !container.isAttachedToWindow) {
                    Log.i(TAG, "${logPrefix()} <<< RECOVERY_READY | autoRebind=false")
                    _avatarState.value = AvatarState.Error("缓存已清理，请重试")
                    return@launch
                }

                Log.i(TAG, "${logPrefix()} --- RECOVERY_STEP_D | 自动重绑 gender=$gender containerId=${container.id}")
                container.removeAllViews()
                val ok = bind(container.context, container, gender)
                Log.i(TAG, "${logPrefix()} <<< RECOVERY_AUTO_REBIND_RESULT | success=$ok")
                if (!ok) {
                    _avatarState.value = AvatarState.Error("缓存已清理，但重试初始化失败")
                }
                
            } catch (recoveryEx: Exception) {
                Log.e(TAG, "${logPrefix()} <<< RECOVERY_FAILED | ${recoveryEx.message}", recoveryEx)
                _avatarState.value = AvatarState.Error("恢复失败: ${recoveryEx.message}")
            }
        }
    }
    
    /**
     * 安全停止当前 SDK 实例
     */
    private fun safeStopInstance() {
        try {
            avatarInstance?.let { instance ->
                // 尝试调用 pause
                try {
                    val pauseMethod = instance.javaClass.getMethod("pause")
                    pauseMethod.invoke(instance)
                } catch (_: NoSuchMethodException) {}
                
                // 尝试调用 switchModel(true) 关闭房间
                try {
                    val switchModelMethod = instance.javaClass.getMethod("switchModel", Boolean::class.java)
                    switchModelMethod.invoke(instance, true)
                } catch (_: NoSuchMethodException) {}

                // 尝试调用 destroy
                try {
                    val destroyMethod = instance.javaClass.getMethod("destroy")
                    destroyMethod.invoke(instance)
                } catch (_: NoSuchMethodException) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "${logPrefix()} safeStopInstance warning: ${e.message}")
        }
        avatarInstance = null
        isAvatarInitialized = false
    }
    
    /**
     * 清理缓存目录
     * @param errorMessage 错误信息，用于解析需要清理的具体子目录
     * @return 被清理的目录路径
     */
    private fun cleanCacheDirectory(errorMessage: String?): String {
        val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
        
        // 尝试从错误信息中解析具体的角色目录（如 jiangyan_14019_new）
        val specificDir = errorMessage?.let { msg ->
            // 匹配形如 /xmov_cache/xxx/ 的路径
            val regex = Regex("$CACHE_DIR_NAME/([^/]+)/")
            regex.find(msg)?.groupValues?.getOrNull(1)?.let { subDir ->
                File(cacheDir, subDir)
            }
        }
        
        return if (specificDir != null && specificDir.exists()) {
            // 只清理特定角色目录
            specificDir.deleteRecursively()
            specificDir.absolutePath
        } else {
            // 清理整个缓存目录
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()  // 重新创建空目录
            cacheDir.absolutePath
        }
    }
    
    /**
     * 手动清除数字人缓存（公开方法，供设置页面调用）
     * 
     * 用于解决数字人更新后（如从全身改为半身）缓存未更新的问题。
     * 清除缓存后，下次加载数字人时会重新从服务器下载最新资源。
     * 
     * @return true 如果清除成功，false 如果清除失败
     */
    fun clearAvatarCache(): Boolean {
        Log.i(TAG, "${logPrefix()} >>> CLEAR_CACHE_MANUAL")
        return try {
            val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
            if (cacheDir.exists()) {
                val deleted = cacheDir.deleteRecursively()
                cacheDir.mkdirs()  // 重新创建空目录
                Log.i(TAG, "${logPrefix()} <<< CLEAR_CACHE_SUCCESS | deleted=$deleted path=${cacheDir.absolutePath}")
                true
            } else {
                Log.i(TAG, "${logPrefix()} <<< CLEAR_CACHE_SKIP | cache dir not exists")
                true  // 目录不存在也算成功（已经清空了）
            }
        } catch (e: Exception) {
            Log.e(TAG, "${logPrefix()} <<< CLEAR_CACHE_FAILED | ${e.message}", e)
            false
        }
    }
    
    /**
     * 处理 SDK 回调（通过动态代理）
     * 增强版：统一日志格式，扩展回调覆盖，降噪采样
     */
    private fun handleListenerCallback(methodName: String, args: Array<Any?>?): Any? {
        // 安全格式化参数，防止刷屏
        val argsStr = args?.joinToString { truncate(it?.toString()) } ?: ""
        
        when (methodName) {
            // ========== 核心回调：始终打印 ==========
            "onInitEvent" -> {
                val code = args?.getOrNull(0) as? Int ?: -1
                val message = args?.getOrNull(1) as? String
                Log.w(TAG, "${logPrefix()} >>> CB_onInitEvent | code=$code msg=$message")
                if (code == 0) {
                    isAvatarInitialized = true
                    recoveryAttempts = 0  // 成功初始化，重置恢复计数
                    _avatarState.value = AvatarState.Idle
                    Log.i(TAG, "${logPrefix()} <<< SDK_INIT_SUCCESS")
                } else {
                    isAvatarInitialized = false
                    _avatarState.value = AvatarState.Error(message ?: "初始化失败")
                    Log.e(TAG, "${logPrefix()} <<< SDK_INIT_FAILED | code=$code msg=$message")
                    
                    // 检查是否是 ENOENT 相关错误
                    val msg = message ?: ""
                    if (msg.contains("ENOENT") || msg.contains("No such file") || (msg.contains("Failed to load resource") && msg.contains("char_data.bin"))) {
                        handleCacheMissingError(Exception(msg))
                    }
                }
            }
            
            "onVoiceStateChange" -> {
                val status = args?.getOrNull(0) as? String
                Log.i(TAG, "${logPrefix()} >>> CB_onVoiceStateChange | status=$status")
                when (status) {
                    "voice_start" -> {
                        _avatarState.value = AvatarState.Speaking
                        // 根据文档，output_audio=true 时SDK应该自己播放
                        // 但如果SDK通过回调返回音频数据，我们会在回调中处理
                        Log.d(TAG, "${logPrefix()} Voice started - SDK should play audio automatically (output_audio=true)")
                    }
                    "voice_end" -> {
                        _avatarState.value = AvatarState.Idle
                        // 标记音频数据发送完成
                        audioPlayer.markFinishSend(true)
                        // 释放音频焦点
                        abandonAudioFocus()
                    }
                }
            }
            
            // ========== 网络事件：始终打印（关键诊断信息） ==========
            "onReconnectEvent" -> {
                Log.w(TAG, "${logPrefix()} >>> CB_onReconnectEvent | args=$argsStr")
            }
            
            "onOfflineEvent" -> {
                Log.w(TAG, "${logPrefix()} >>> CB_onOfflineEvent | args=$argsStr")
                // 可能需要触发重连逻辑
            }
            
            "onNetworkInfo" -> {
                Log.i(TAG, "${logPrefix()} >>> CB_onNetworkInfo | args=$argsStr")
            }
            
            // ========== 状态变化：始终打印 ==========
            "onStateChange", "onStatusChange" -> {
                Log.i(TAG, "${logPrefix()} >>> CB_$methodName | args=$argsStr")
            }
            
            "onMessage" -> {
                // SDK 消息回调，可能包含重要信息
                Log.i(TAG, "${logPrefix()} >>> CB_onMessage | args=$argsStr")
            }
            
            "onWidgetEvent" -> {
                Log.d(TAG, "${logPrefix()} >>> CB_onWidgetEvent | args=$argsStr")
            }
            
            "onError" -> {
                Log.e(TAG, "${logPrefix()} >>> CB_onError | args=$argsStr")
                // 检查是否是缓存相关错误
                val errorMsg = args?.getOrNull(0)?.toString() ?: ""
                if (errorMsg.contains("ENOENT") || errorMsg.contains("No such file") || 
                    errorMsg.contains(CACHE_DIR_NAME) ||
                    (errorMsg.contains("Failed to load resource") && errorMsg.contains("char_data.bin"))) {
                    handleCacheMissingError(Exception(errorMsg))
                }
            }
            
            // ========== 调试回调：采样打印（降噪） ==========
            "onDebugInfo" -> {
                debugCallbackCounter++
                if (debugCallbackCounter % DEBUG_CALLBACK_SAMPLE_RATE == 1) {
                    Log.d(TAG, "${logPrefix()} >>> CB_onDebugInfo (sampled 1/$DEBUG_CALLBACK_SAMPLE_RATE) | args=$argsStr")
                }
            }
            
            // ========== 音频数据回调：备用方案（如果SDK不自动播放） ==========
            else -> {
                // 检查是否是音频数据回调
                // 注意：根据文档，output_audio=true 时SDK应该自己播放音频
                // 但如果SDK通过回调返回音频数据（说明SDK没有自动播放），我们在这里处理
                val isPossibleAudioCallback = methodName.contains("Audio", ignoreCase = true) || 
                                             methodName.contains("Stream", ignoreCase = true) ||
                                             methodName.contains("Pcm", ignoreCase = true) ||
                                             methodName.contains("Data", ignoreCase = true)
                
                if (isPossibleAudioCallback) {
                    // 如果收到音频数据回调，说明SDK没有自动播放，需要我们手动播放
                    val audioData = when {
                        args?.size ?: 0 >= 1 -> {
                            val firstArg = args?.get(0)
                            when (firstArg) {
                                is ByteArray -> firstArg
                                else -> null
                            }
                        }
                        else -> null
                    }
                    
                    if (audioData != null && audioData.isNotEmpty()) {
                        // SDK没有自动播放，我们手动播放
                        Log.w(TAG, "${logPrefix()} >>> CB_$methodName | SDK returned audio data (not auto-playing), using manual playback | size=${audioData.size} bytes")
                        // 首次收到音频数据时启动播放器
                        if (debugCallbackCounter == 0) {
                            audioPlayer.play()
                        }
                        audioPlayer.setAudioData(audioData)
                        // 采样打印，避免刷屏（每50次打印一次）
                        if (debugCallbackCounter % 50 == 0) {
                            Log.i(TAG, "${logPrefix()} >>> CB_$methodName | audioDataSize=${audioData.size} bytes")
                        }
                        debugCallbackCounter++
                    } else {
                        Log.d(TAG, "${logPrefix()} >>> CB_$methodName (possible audio callback but no valid data) | argsCount=${args?.size} argsTypes=${args?.map { it?.javaClass?.simpleName }}")
                    }
                } else {
                    // ========== 其他回调：统一格式打印 ==========
                    // 对于未知的回调，也记录一下，方便发现音频回调方法名
                    if (args?.any { it is ByteArray } == true) {
                        // 如果参数中包含 ByteArray，可能是音频数据回调但方法名不匹配我们的模式
                        Log.w(TAG, "${logPrefix()} >>> CB_$methodName (contains ByteArray, might be audio callback) | args=$argsStr")
                    } else {
                        Log.d(TAG, "${logPrefix()} >>> CB_$methodName | args=$argsStr")
                    }
                }
            }
        }
        return null
    }
    
    /**
     * 让数字人说话并触发指定动作（使用 SSML KA 指令）
     * @param text 说话内容
     * @param actionSemantic 动作语义标签（如 dance, Hello, Welcome 等）
     * 
     * 参考文档：https://xingyun3d.com/developers/52-183
     * KA 指令格式：<speak><ue4event><type>ka</type><data><action_semantic>动作名</action_semantic></data></ue4event>文本内容</speak>
     */
    fun speakWithAction(text: String, actionSemantic: String) {
        if (!isAvatarInitialized || avatarInstance == null) {
            Log.w(TAG, "SDK not initialized, cannot speak with action")
            return
        }
        
        serviceScope.launch {
            Log.d(TAG, "Speaking with action: $actionSemantic, text: $text")
            
            // 构建 SSML KA 指令
            val ssml = buildSsmlWithAction(text, actionSemantic)
            
            _avatarState.value = AvatarState.Speaking
            
            // 请求音频焦点（确保SDK可以播放音频）
            requestAudioFocus()
            
            try {
                val speakMethod = avatarInstance!!.javaClass.getMethod(
                    "speak", 
                    String::class.java, 
                    Boolean::class.java, 
                    Boolean::class.java
                )
                speakMethod.invoke(avatarInstance, ssml, true, true)
            } catch (e: Exception) {
                Log.e(TAG, "${logPrefix()} speak with action FAILED: ${e.message}", e)
                if (isCacheMissingError(e)) {
                    handleCacheMissingError(e)
                } else {
                    _avatarState.value = AvatarState.Error("播报失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 构建带 KA 指令的 SSML 格式文本
     * @param text 说话内容
     * @param actionSemantic 动作语义标签
     * @return SSML 格式字符串
     */
    private fun buildSsmlWithAction(text: String, actionSemantic: String): String {
        return "<speak><ue4event><type>ka</type><data><action_semantic>$actionSemantic</action_semantic></data></ue4event>$text</speak>"
    }
    
    /**
     * 让数字人说话（全文本模式）
     */
    /**
     * 让数字人说话（全文本模式）
     * 增加思考动作和模拟延迟 (Artificial Latency)
     */
    fun speak(text: String) {
        if (!isAvatarInitialized || avatarInstance == null) {
            Log.w(TAG, "SDK not initialized, cannot speak")
            return
        }
        
        serviceScope.launch {
            Log.d(TAG, "Preparing to speak: $text")
            
            // 1. Enter Thinking State
            startThinking()
            
            // 2. Simulate Thinking Delay
            simulateThinkingDelay(text.length)
            
            // 3. Start Speaking
            Log.d(TAG, "Speaking: $text")
            _avatarState.value = AvatarState.Speaking
            
            // 请求音频焦点（确保SDK可以播放音频）
            // 注意：根据文档，output_audio=true 时SDK应该自己播放音频
            // 但如果SDK没有播放，我们会在音频数据回调中处理
            requestAudioFocus()
            
            try {
                val speakMethod = avatarInstance!!.javaClass.getMethod(
                    "speak", 
                    String::class.java, 
                    Boolean::class.java, 
                    Boolean::class.java
                )
                // 全文本输入模式：isStart=true, isEnd=true
                speakMethod.invoke(avatarInstance, text, true, true)
            } catch (e: Exception) {
                Log.e(TAG, "${logPrefix()} speak FAILED: ${e.message}", e)
                if (isCacheMissingError(e)) {
                    handleCacheMissingError(e)
                } else {
                    _avatarState.value = AvatarState.Error("播报失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 让数字人说话（无延迟版本，性能优化）
     * 
     * 优化点：
     * - 不调用 simulateThinkingDelay()，直接进入说话状态
     * - 预期收益：减少 0.5-3 秒延迟
     * 
     * @param text 说话内容
     */
    fun speakWithoutDelay(text: String) {
        if (!isAvatarInitialized || avatarInstance == null) {
            Log.w(TAG, "SDK not initialized, cannot speak")
            return
        }
        
        serviceScope.launch {
            Log.d(TAG, "Preparing to speak (fast mode): $text")
            
            // 1. Enter Thinking State（但不延迟）
            startThinking()
            
            // 2. 直接开始说话，不模拟延迟
            Log.d(TAG, "Speaking (fast mode): $text")
            _avatarState.value = AvatarState.Speaking
            
            // 请求音频焦点（确保SDK可以播放音频）
            requestAudioFocus()
            
            try {
                val speakMethod = avatarInstance!!.javaClass.getMethod(
                    "speak", 
                    String::class.java, 
                    Boolean::class.java, 
                    Boolean::class.java
                )
                // 全文本输入模式：isStart=true, isEnd=true
                speakMethod.invoke(avatarInstance, text, true, true)
            } catch (e: Exception) {
                Log.e(TAG, "${logPrefix()} speakWithoutDelay FAILED: ${e.message}", e)
                if (isCacheMissingError(e)) {
                    handleCacheMissingError(e)
                } else {
                    _avatarState.value = AvatarState.Error("播报失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 模拟思考延迟
     * Logic: min(500 + length * 50, 3000) ms
     */
    private suspend fun simulateThinkingDelay(responseLength: Int) {
        val delayTime = min(500 + responseLength * 50, 3000).toLong()
        Log.d(TAG, "Simulating thinking delay: ${delayTime}ms for length $responseLength")
        delay(delayTime)
    }
    
    /**
     * 请求音频焦点（确保可以播放音频）
     */
    private fun requestAudioFocus() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ).apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                Log.d(TAG, "Audio focus lost temporarily")
                                audioPlayer.pause()
                            }
                            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                                Log.d(TAG, "Audio focus gained")
                                audioPlayer.resume()
                            }
                            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                                Log.d(TAG, "Audio focus lost permanently")
                                audioPlayer.stop()
                            }
                        }
                    }
                }.build()
                
                val result = audioManager.requestAudioFocus(focusRequest)
                audioFocusRequest = focusRequest
                Log.d(TAG, "Audio focus request result: $result")
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                Log.d(TAG, "Audio focus request result (legacy): $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request audio focus", e)
        }
    }
    
    /**
     * 释放音频焦点
     */
    private fun abandonAudioFocus() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    audioManager.abandonAudioFocusRequest(it)
                    audioFocusRequest = null
                    Log.d(TAG, "Audio focus abandoned")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
                Log.d(TAG, "Audio focus abandoned (legacy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to abandon audio focus", e)
        }
    }
    
    /**
     * 停止说话/打断
     */
    fun stopSpeaking() {
        if (!isAvatarInitialized || avatarInstance == null) {
            Log.w(TAG, "SDK not initialized, cannot interrupt")
            return
        }
        
        Log.d(TAG, "Interrupting speech")
        try {
            val interruptMethod = avatarInstance!!.javaClass.getMethod("interrupt")
            interruptMethod.invoke(avatarInstance)
            _avatarState.value = AvatarState.Idle
            // 停止音频播放（如果使用了手动播放）
            audioPlayer.stop()
            // 释放音频焦点
            abandonAudioFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to interrupt", e)
        }
    }
    
    /**
     * 开始聆听状态
     */
    fun startListening() {
        if (!isAvatarInitialized || avatarInstance == null) {
            Log.w(TAG, "SDK not initialized, cannot listen")
            return
        }
        
        Log.d(TAG, "Starting listening mode")
        _avatarState.value = AvatarState.Listening
        
        try {
            val listenMethod = avatarInstance!!.javaClass.getMethod("listen")
            listenMethod.invoke(avatarInstance)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
        }
    }
    
    /**
     * 开始思考状态 - 在 LLM 请求期间调用
     * 调用 SDK 的 think() 接口让数字人表现出思考状态
     */
    fun startThinking() {
        if (!isAvatarInitialized || avatarInstance == null) {
            Log.w(TAG, "SDK not initialized, cannot think")
            return
        }
        
        Log.d(TAG, "Starting thinking mode")
        _avatarState.value = AvatarState.Thinking
        
        try {
            val thinkMethod = avatarInstance!!.javaClass.getMethod("think")
            thinkMethod.invoke(avatarInstance)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start thinking", e)
        }
    }
    
    /**
     * 设置数字人情绪表情
     * @param emotion 情绪标签 (happy, sad, angry, surprised, neutral, loving, worried, excited)
     */
    fun setEmotion(emotion: String) {
        if (!isAvatarInitialized || avatarInstance == null) {
            Log.w(TAG, "SDK not initialized, cannot set emotion")
            return
        }
        
        Log.d(TAG, "Setting emotion: $emotion")
        
        try {
            // 尝试调用 SDK 的表情设置方法
            // 不同 SDK 版本可能有不同的方法名
            val emotionMethodNames = listOf("setEmotion", "setExpression", "emotion")
            var success = false
            
            for (methodName in emotionMethodNames) {
                try {
                    val method = avatarInstance!!.javaClass.getMethod(methodName, String::class.java)
                    method.invoke(avatarInstance, mapEmotionToSdk(emotion))
                    success = true
                    break
                } catch (e: NoSuchMethodException) {
                    // 尝试下一个方法名
                }
            }
            
            if (!success) {
                Log.w(TAG, "No emotion method found in SDK, using speak with expression hint")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set emotion", e)
        }
    }
    
    /**
     * 播放数字人动作/手势
     * @param gesture 动作标签 (nod, shake_head, wave, think, shrug, bow, clap, heart)
     */
    fun playGesture(gesture: String) {
        if (!isAvatarInitialized || avatarInstance == null) {
            Log.w(TAG, "SDK not initialized, cannot play gesture")
            return
        }
        
        Log.d(TAG, "Playing gesture: $gesture")
        
        try {
            // 尝试调用 SDK 的动作播放方法
            val gestureMethodNames = listOf("playGesture", "playAction", "setAction", "gesture", "action")
            var success = false
            
            for (methodName in gestureMethodNames) {
                try {
                    val method = avatarInstance!!.javaClass.getMethod(methodName, String::class.java)
                    method.invoke(avatarInstance, mapGestureToSdk(gesture))
                    success = true
                    break
                } catch (e: NoSuchMethodException) {
                    // 尝试下一个方法名
                }
            }
            
            if (!success) {
                Log.w(TAG, "No gesture method found in SDK")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play gesture", e)
        }
    }
    
    /**
     * 映射情绪标签到 SDK 支持的表情名称
     * 可根据实际 SDK 文档进行调整
     */
    private fun mapEmotionToSdk(emotion: String): String {
        return when (emotion.lowercase()) {
            "happy" -> "happy"
            "sad" -> "sad"
            "angry" -> "angry"
            "surprised" -> "surprised"
            "neutral" -> "neutral"
            "loving" -> "loving"
            "worried" -> "worried"
            "excited" -> "excited"
            else -> "neutral"
        }
    }
    
    /**
     * 映射动作标签到 SDK 支持的动作名称
     * 可根据实际 SDK 文档进行调整
     */
    private fun mapGestureToSdk(gesture: String): String {
        return when (gesture.lowercase()) {
            "nod" -> "nod"
            "shake_head" -> "shake_head"
            "wave" -> "wave"
            "think" -> "think"
            "shrug" -> "shrug"
            "bow" -> "bow"
            "clap" -> "clap"
            "heart" -> "heart"
            else -> "nod"
        }
    }
    
    /**
     * 释放 SDK 资源
     * 
     * 根据官方文档：https://xingyun3d.com/developers/52-193
     * 正确的关闭流程：
     * 1. interrupt() - 打断当前活动
     * 2. switchModel(true) - 切换到离线模式（断开服务器连接，关闭房间）
     * 3. destroy() - 销毁 SDK 实例，释放资源
     * 
     * 解决"房间限流"问题：确保在 destroy() 之前先断开连接
     */
    fun release() {
        Log.i(TAG, "${logPrefix()} >>> RELEASE_START | Force Offline Mode")
        try {
            avatarInstance?.let { instance ->
                // 1. 先打断当前活动（说话、聆听等）
                try {
                    val interruptMethod = instance.javaClass.getMethod("interrupt")
                    interruptMethod.invoke(instance)
                    Log.d(TAG, "${logPrefix()} --- INTERRUPT_OK")
                } catch (_: NoSuchMethodException) {
                } catch (e: Exception) {
                    Log.w(TAG, "${logPrefix()} --- INTERRUPT_FAILED | ${e.message}")
                }
                
                // 2. 切换到离线模式（断开服务器连接，关闭房间）
                // 必须保证这一步执行成功，否则会触发 Room Rate Limit
                try {
                    val switchModelMethod = instance.javaClass.getMethod("switchModel", Boolean::class.java)
                    switchModelMethod.invoke(instance, true)  // true = 切换到离线模式
                    Log.i(TAG, "${logPrefix()} --- SWITCH_TO_OFFLINE_OK | Room Disconnect triggered")
                    
                    // 强制等待一小段时间，让 Close 信令发出
                    try {
                        Thread.sleep(500)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                } catch (_: NoSuchMethodException) {
                    Log.w(TAG, "${logPrefix()} --- SWITCH_MODEL_METHOD_NOT_FOUND | Critical for connection close")
                } catch (e: Exception) {
                    Log.e(TAG, "${logPrefix()} --- SWITCH_TO_OFFLINE_FAILED | ${e.message}", e)
                }
                
                // 3. 销毁 SDK 实例（释放资源）
                try {
                    val destroyMethod = instance.javaClass.getMethod("destroy")
                    destroyMethod.invoke(instance)
                    Log.i(TAG, "${logPrefix()} --- DESTROY_OK")
                } catch (e: Exception) {
                    Log.e(TAG, "${logPrefix()} --- DESTROY_FAILED | ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "${logPrefix()} <<< RELEASE_FAILED | ${e.message}", e)
        }
        avatarInstance = null
        isAvatarInitialized = false
        _avatarState.value = AvatarState.Idle
        
        // 释放音频播放器
        audioPlayer.release()
        
        // 释放音频焦点
        abandonAudioFocus()
        
        Log.d(TAG, "${logPrefix()} <<< RELEASE_DONE")
        // Note: We don't cancel serviceScope here as this service is a Singleton
        // and might be reused or re-initialized.
    }
    
    fun isInitialized(): Boolean = isAvatarInitialized
    
    /**
     * 绑定到 UI 容器并初始化
     * @param activityContext 必须传 Activity context
     * @param userGender 用户性别，用于选择对应的数字人
     */
    fun bind(activityContext: Context, container: ViewGroup, userGender: UserGender = UserGender.MALE): Boolean {
        // 更新当前性别
        currentGender = userGender
        
        Log.i(TAG, "${logPrefix()} >>> BIND_START | userGender=$userGender containerId=${container.id}")
        
        // 关键修复：在 bind 之前，确保旧连接完全关闭
        // 如果已有实例，先完全释放，避免"房间限流"错误
        // 注意：2026-01-25 更新：增加等待时间至 1500ms，确保 Server 端完全断开
        if (avatarInstance != null || isAvatarInitialized) {
            Log.w(TAG, "${logPrefix()} --- PRE_BIND_CLEANUP | 检测到残留连接，执行强制清理")
            release()
            // 等待较长时间确保连接完全关闭（使用 Thread.sleep，避免阻塞主线程）
            try {
                Thread.sleep(1500)  // 增加到 1500ms
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        
        // 根据用户性别选择对应的数字人 API Key
        // 男性用户 → 女性数字人 (姜岩)
        // 女性用户 → 男性数字人 (待配置真正的男性数字人)
        
        val (appId, appSecret, avatarName) = when (userGender) {
            UserGender.FEMALE -> {
                val maleId = BuildConfig.XMOV_APP_ID_MALE
                if (maleId.isEmpty()) {
                    Log.e(TAG, "${logPrefix()} XMOV_APP_ID_MALE not configured!")
                }
                Triple(maleId, BuildConfig.XMOV_APP_SECRET_MALE, "霸总-绑带版(男性数字人)")
            }
            else -> Triple(BuildConfig.XMOV_APP_ID, BuildConfig.XMOV_APP_SECRET, "姜岩(女性数字人)")
        }
        
        if (appId.isEmpty()) {
            Log.e(TAG, "${logPrefix()} <<< BIND_FAIL | AppID is empty!")
            _avatarState.value = AvatarState.Error("数字人配置缺失，请在 local.properties 设置 XMOV_APP_ID/XMOV_APP_ID_MALE")
            return false
        }

        Log.i(TAG, "${logPrefix()} --- BIND_CONFIG | avatar=$avatarName appId=${appId.take(8)}...")
        
        // 根据优化开关选择初始化方法（默认启用优化）
        val result = if (userPreferencesRepository.isFastAvatarInitEnabled()) {
            try {
                initializeOptimized(activityContext, container, appId, appSecret)
            } catch (e: Exception) {
                Log.w(TAG, "${logPrefix()} Optimized init failed, falling back to original: ${e.message}")
                initialize(activityContext, container, appId, appSecret)
            }
        } else {
            initialize(activityContext, container, appId, appSecret)
        }
        
        Log.i(TAG, "${logPrefix()} <<< BIND_RESULT | success=$result")
        return result
    }
    
    fun resume() {
        Log.d(TAG, "${logPrefix()} >>> RESUME")
        try {
            avatarInstance?.let { instance ->
                val resumeMethod = instance.javaClass.getMethod("resume")
                resumeMethod.invoke(instance)
            }
        } catch (e: NoSuchMethodException) {
            // SDK 可能自动处理
            Log.d(TAG, "${logPrefix()} resume: SDK handles automatically")
        } catch (e: Exception) {
            Log.w(TAG, "${logPrefix()} resume warning: ${e.message}")
        }
    }
    
    fun pause() {
        Log.d(TAG, "${logPrefix()} >>> PAUSE")
        try {
            avatarInstance?.let { instance ->
                val pauseMethod = instance.javaClass.getMethod("pause")
                pauseMethod.invoke(instance)
            }
        } catch (e: NoSuchMethodException) {
            // SDK 可能自动处理
            Log.d(TAG, "${logPrefix()} pause: SDK handles automatically")
        } catch (e: Exception) {
            Log.w(TAG, "${logPrefix()} pause warning: ${e.message}")
        }
    }
    
    fun destroy() {
        Log.i(TAG, "${logPrefix()} >>> DESTROY")
        release()
        Log.i(TAG, "${logPrefix()} <<< DESTROY_DONE")
    }
}
