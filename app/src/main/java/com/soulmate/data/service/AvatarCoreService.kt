package com.soulmate.data.service

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.soulmate.BuildConfig
import com.soulmate.data.model.UIEvent
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
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "AvatarCoreService"
        private const val GATEWAY_SERVER = "https://nebula-agent.xingyun3d.com/user/v1/ttsa/session"
    }
    
    private val _avatarState = MutableStateFlow<AvatarState>(AvatarState.Idle)
    val avatarState: StateFlow<AvatarState> = _avatarState.asStateFlow()
    
    private val _uiEventFlow = MutableSharedFlow<UIEvent>(extraBufferCapacity = 10)
    val uiEventFlow: SharedFlow<UIEvent> = _uiEventFlow.asSharedFlow()
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var isAvatarInitialized = false
    private var containerView: ViewGroup? = null
    private var avatarInstance: Any? = null
    
    /**
     * 初始化 SDK（使用反射调用避免 Kotlin 版本兼容性问题）
     * @param activityContext 必须传 Activity 而非 ApplicationContext，否则 GL 渲染无法显示
     */
    fun initialize(activityContext: Context, containerView: ViewGroup, appId: String, appSecret: String): Boolean {
        Log.d(TAG, "Initializing Xmov SDK with Activity context...")
        this.containerView = containerView
        
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
                    put("width", 720)   // Lower resolution for better compatibility
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
            
            // Log.d(TAG, "SDK init called successfully") // Removed logs
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SDK via reflection", e)
            _avatarState.value = AvatarState.Error("SDK 初始化异常: ${e.message}")
            return false
        }
    }
    
    /**
     * 处理 SDK 回调（通过动态代理）
     */
    private fun handleListenerCallback(methodName: String, args: Array<Any?>?): Any? {
        when (methodName) {
            "onInitEvent" -> {
                val code = args?.getOrNull(0) as? Int ?: -1
                val message = args?.getOrNull(1) as? String
                // Log.d(TAG, "onInitEvent code:$code, message:$message") // Removed logs
                if (code == 0) {
                    isAvatarInitialized = true
                    _avatarState.value = AvatarState.Idle
                } else {
                    isAvatarInitialized = false
                    _avatarState.value = AvatarState.Error(message ?: "初始化失败")
                }
            }
            "onVoiceStateChange" -> {
                val status = args?.getOrNull(0) as? String
                // Log.d(TAG, "onVoiceStateChange status:$status") // Removed logs
                when (status) {
                    "voice_start" -> _avatarState.value = AvatarState.Speaking
                    "voice_end" -> _avatarState.value = AvatarState.Idle
                }
            }
            "onWidgetEvent" -> {
                // Log.d(TAG, "onWidgetEvent: ${args?.getOrNull(0)}") // Removed logs
                // 处理 UI 事件
            }
            // "onStateChange", "onStatusChange", "onMessage", "onNetworkInfo", 
            // "onStateRenderChange", "onDebugInfo", "onReconnectEvent", "onOfflineEvent" -> {
            //    Log.d(TAG, "$methodName: ${args?.joinToString()}")
            // }
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
            
            try {
                val speakMethod = avatarInstance!!.javaClass.getMethod(
                    "speak", 
                    String::class.java, 
                    Boolean::class.java, 
                    Boolean::class.java
                )
                speakMethod.invoke(avatarInstance, ssml, true, true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to speak with action", e)
                _avatarState.value = AvatarState.Error("播报失败: ${e.message}")
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
                Log.e(TAG, "Failed to speak", e)
                _avatarState.value = AvatarState.Error("播报失败: ${e.message}")
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
     */
    fun release() {
        Log.d(TAG, "Releasing SDK resources")
        try {
            avatarInstance?.let { instance ->
                val destroyMethod = instance.javaClass.getMethod("destroy")
                destroyMethod.invoke(instance)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy SDK", e)
        }
        avatarInstance = null
        isAvatarInitialized = false
        _avatarState.value = AvatarState.Idle
        // Note: We don't cancel serviceScope here as this service is a Singleton
        // and might be reused or re-initialized.
    }
    
    fun isInitialized(): Boolean = isAvatarInitialized
    
    /**
     * 绑定到 UI 容器并初始化
     * @param activityContext 必须传 Activity context
     * @param userGender 用户性别，用于选择对应的数字人
     */
    fun bind(activityContext: Context, container: ViewGroup, userGender: com.soulmate.data.model.UserGender = com.soulmate.data.model.UserGender.MALE): Boolean {
        // 根据用户性别选择对应的数字人 API Key
        // 男性用户 → 女性数字人 (默认 Eleanor)
        // 女性用户 → 男性数字人
        val (appId, appSecret) = when (userGender) {
            com.soulmate.data.model.UserGender.FEMALE -> BuildConfig.XMOV_APP_ID_MALE to BuildConfig.XMOV_APP_SECRET_MALE
            else -> BuildConfig.XMOV_APP_ID to BuildConfig.XMOV_APP_SECRET
        }
        
        Log.d(TAG, "Binding avatar for user gender: $userGender, using appId: ${appId.take(8)}...")
        
        return initialize(
            activityContext,
            container,
            appId,
            appSecret
        )
    }
    
    fun resume() {
        Log.d(TAG, "Resume - SDK handles automatically")
    }
    
    fun pause() {
        Log.d(TAG, "Pause - SDK handles automatically")
    }
    
    fun destroy() {
        release()
    }
}
