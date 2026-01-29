package com.soulmate.data.avatar

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.ViewGroup
import com.soulmate.BuildConfig
import com.soulmate.core.data.avatar.IAvatarDriver
import com.soulmate.data.model.UserGender
import com.soulmate.data.preferences.UserPreferencesRepository
import com.soulmate.data.service.AvatarAudioPlayer
import com.soulmate.data.service.AvatarState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * XmovAvatarDriver - Xmov SDK Implementation
 */
@Singleton
class XmovAvatarDriver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : IAvatarDriver {

    companion object {
        private const val TAG = "XmovAvatarDriver"
        private const val GATEWAY_SERVER = BuildConfig.XMOV_GATEWAY_URL
        private const val CACHE_DIR_NAME = "xmov_cache"
        private const val MAX_RECOVERY_ATTEMPTS = 2
        private const val DEBUG_CALLBACK_SAMPLE_RATE = 10
        private const val MAX_ARG_LOG_LENGTH = 512
    }

    private val driverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // State
    private var instanceIsInitialized = false
    private var isAvatarInitialized = false // Mirror check
    private var containerView: ViewGroup? = null
    private var avatarInstance: Any? = null
    private var currentGender: UserGender? = null

    // Audio Player
    private val audioPlayer = AvatarAudioPlayer()
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // Callbacks to Service
    private var stateListener: ((AvatarState) -> Unit)? = null
    
    // Diagnostics
    private val sessionIdGenerator = AtomicLong(0)
    private var currentSessionId: Long = 0
    private var currentContainerId: Int = 0
    private var debugCallbackCounter = 0
    private var recoveryAttempts = 0
    private val recoveryMutex = Mutex()
    private val operationMutex = Mutex()

    // Audio Focus
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    
    override fun setListener(listener: (AvatarState) -> Unit) {
        stateListener = listener
    }
    
    private fun updateState(state: AvatarState) {
        stateListener?.invoke(state)
    }

    override suspend fun initialize(activityContext: Context, container: ViewGroup, gender: UserGender): Boolean {
        // Use mutex to serialize initialization
         return operationMutex.withLock {
            this.containerView = container
            this.currentContainerId = container.id
            this.currentGender = gender
            
            // Clean up previous
             if (avatarInstance != null) {
                Log.w(TAG, "${logPrefix()} Force releasing previous session")
                releaseInternal()
                delay(2000)
            }

            currentSessionId = sessionIdGenerator.incrementAndGet()
            
            val (appId, appSecret, avatarName) = when (gender) {
                UserGender.FEMALE -> {
                     val maleId = BuildConfig.XMOV_APP_ID_MALE
                     if (maleId.isEmpty()) Log.e(TAG, "Male ID empty")
                     Triple(maleId, BuildConfig.XMOV_APP_SECRET_MALE, "Male Avatar")
                }
                else -> Triple(BuildConfig.XMOV_APP_ID, BuildConfig.XMOV_APP_SECRET, "Female Avatar")
            }
            
            if (appId.isEmpty()) {
                 updateState(AvatarState.Error("App ID Empty"))
                 return@withLock false
            }

            Log.i(TAG, "${logPrefix()} Init start for $avatarName")

            // Aggressive Cleanup: Always try to get SDK instance and destroy it
            try {
                val avatarClass = Class.forName("com.xmov.metahuman.sdk.IXmovAvatar")
                val companionField = avatarClass.getDeclaredField("Companion")
                companionField.isAccessible = true
                val companion = companionField.get(null)
                val getMethod = companion.javaClass.getMethod("get")
                val existingInstance = getMethod.invoke(companion)
                
                if (existingInstance != null) {
                    Log.w(TAG, "Found existing SDK instance during init, forcing destroy.")
                    try {
                        existingInstance.javaClass.getMethod("destroy").invoke(existingInstance)
                    } catch (e: Exception) {
                        Log.w(TAG, "Force destroy failed", e)
                    }
                    delay(500) // Wait for server release
                }
            } catch (e: Exception) {
                // Ignore if class not found or other errors
                 Log.d(TAG, "Pre-init cleanup check failed (normal): ${e.message}")
            }

            if (!ensureCacheDirectory()) {
                updateState(AvatarState.Error("Cache dir failed"))
                return@withLock false
            }

            // Init logic
            val result = try {
                 // Try optimized init first if enabled
                 initializeInternal(activityContext, container, appId, appSecret)
            } catch (e: Exception) {
                 Log.e(TAG, "Init Exception", e)
                 false
            }
            
            if (result) {
                 instanceIsInitialized = true
                 isAvatarInitialized = true
            }
            
            result
        }
    }

    private fun initializeInternal(activityContext: Context, containerView: ViewGroup, appId: String, appSecret: String): Boolean {
        try {
            // Reflection and Init Logic
            val avatarClass = Class.forName("com.xmov.metahuman.sdk.IXmovAvatar")
            val companionField = avatarClass.getDeclaredField("Companion")
            companionField.isAccessible = true
            val companion = companionField.get(null)
            val getMethod = companion.javaClass.getMethod("get")
            avatarInstance = getMethod.invoke(companion)

            val configJson = JSONObject().apply {
                put("input_audio", false)
                put("output_audio", true)
                put("resolution", JSONObject().apply {
                    put("width", 720)
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

            val initConfigClass = Class.forName("com.xmov.metahuman.sdk.data.InitConfig")
            val initConfigConstructor = initConfigClass.constructors.firstOrNull { it.parameterTypes.size >= 4 }
            val initConfig = if (initConfigConstructor != null) {
                initConfigConstructor.newInstance(appId, appSecret, GATEWAY_SERVER, configJson)
            } else {
                val instance = initConfigClass.getDeclaredConstructor().newInstance()
                initConfigClass.getDeclaredField("appId").apply { isAccessible = true; set(instance, appId) }
                initConfigClass.getDeclaredField("appSecret").apply { isAccessible = true; set(instance, appSecret) }
                initConfigClass.getDeclaredField("gatewayServer").apply { isAccessible = true; set(instance, GATEWAY_SERVER) }
                initConfigClass.getDeclaredField("config").apply { isAccessible = true; set(instance, configJson) }
                instance
            }

            val listenerClass = Class.forName("com.xmov.metahuman.sdk.IAvatarListener")
            val listener = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                handleListenerCallback(method.name, args)
            }

            val initMethod = avatarInstance!!.javaClass.getMethod(
                "init",
                Context::class.java,
                ViewGroup::class.java,
                initConfigClass,
                listenerClass
            )
            initMethod.invoke(avatarInstance, activityContext, containerView, initConfig, listener)
            
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Init failed in internal", e)
             // Check ENOENT
             if (isCacheMissingError(e)) {
                 handleCacheMissingError(e)
             } else {
                 updateState(AvatarState.Error("SDK Init Error: ${e.message}"))
             }
            return false
        }
    }

    override fun destroy() {
        driverScope.launch { release() }
    }

    override fun pause() {
         avatarInstance?.let { instance ->
            try {
                instance.javaClass.getMethod("pause").invoke(instance)
            } catch (e: Exception) { Log.w(TAG, "pause failed", e) }
        }
    }

    override fun resume() {
        avatarInstance?.let { instance ->
             try {
                instance.javaClass.getMethod("resume").invoke(instance)
            } catch (e: NoSuchMethodException) {
                Log.w(TAG, "resume method not found in SDK, skipping")
            } catch (e: Exception) { 
                Log.w(TAG, "resume failed", e) 
            }
        }
    }

    override suspend fun release() {
        operationMutex.withLock {
            releaseInternal()
        }
    }
    
    // Start of internal release (called under lock)
    private suspend fun releaseInternal() {
         try {
            avatarInstance?.let { instance ->
                try {
                    instance.javaClass.getMethod("interrupt").invoke(instance)
                } catch (_: Exception) {}
                
                try {
                    instance.javaClass.getMethod("switchModel", Boolean::class.java).invoke(instance, true)
                    delay(1500)
                } catch (_: Exception) {}

                try {
                    instance.javaClass.getMethod("destroy").invoke(instance)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Release failed", e)
        }
        avatarInstance = null
        containerView = null
        instanceIsInitialized = false
        isAvatarInitialized = false
        updateState(AvatarState.Idle)
        audioPlayer.release()
        abandonAudioFocus()
    }

    override fun startSpeaking(text: String) {
        speak(text)
    }

    override fun stopSpeaking() {
        if (!isAvatarInitialized || avatarInstance == null) return
        try {
            avatarInstance!!.javaClass.getMethod("interrupt").invoke(avatarInstance)
            updateState(AvatarState.Idle)
            audioPlayer.stop()
            abandonAudioFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Stop speaking failed", e)
        }
    }

    override fun playMotion(motionName: String) {
        // Placeholder for playing motion
        // If SDK supports it, use gesture mapping.
    }

    override fun setState(state: AvatarState) {
        // When Service sets state, we might need to act
         when (state) {
            is AvatarState.Thinking -> {
                startThinking()
            }
            else -> {
                // Ignore other states or just update internal?
                // Usually Service updates StateFlow based on callbacks.
                // If Service explicitly sets Thinking, we perform Thinking action.
            }
        }
    }

    override fun isInitialized(): Boolean = instanceIsInitialized

    // ============ Internal Logic ============

    private fun startThinking() {
        Log.d(TAG, "Start Thinking Action")
        // If SDK supports thinking animation
        // e.g. playMotion("think") or check specific animation
        // As per prompt: "If the SDK supports it, play a specific 'thinking' animation immediately..."
        // I will attempt to think
        if (avatarInstance != null) {
            try {
                // Try Xmov methods "think" or similar if they exist, or playGesture
                 try {
                    avatarInstance!!.javaClass.getMethod("think").invoke(avatarInstance)
                } catch (e: NoSuchMethodException) {
                    // Fallback to random motion or gesture
                    // We can use the playGesture logic from AvatarCoreService if valid
                    // AvatarCoreService had `playGesture("think")` which mapped to "think".
                    playGestureInternal("think")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Think action failed", e)
            }
        }
    }
    
    private fun playGestureInternal(gesture: String) {
         if (avatarInstance == null) return
         try {
             val sdkGesture = when (gesture) {
                 "think" -> "think"
                 else -> "nod"
             }
             // Try common method names
             val methodNames = listOf("playGesture", "playAction", "setAction", "gesture", "action")
             for(name in methodNames) {
                 try {
                     avatarInstance!!.javaClass.getMethod(name, String::class.java).invoke(avatarInstance, sdkGesture)
                     return
                 } catch (e: NoSuchMethodException) {}
             }
         } catch (e: Exception) {
             Log.w(TAG, "Play gesture failed", e)
         }
    }

    private fun speak(text: String) {
        if (!isAvatarInitialized || avatarInstance == null) return
        
        driverScope.launch {
            // Notify Service we are about to speak (or Service already knows?)
            // updateState(AvatarState.Speaking) // SDK callback will do this
            requestAudioFocus()
            try {
                val speakMethod = avatarInstance!!.javaClass.getMethod(
                    "speak", 
                    String::class.java, 
                    Boolean::class.java, 
                    Boolean::class.java
                )
                speakMethod.invoke(avatarInstance, text, true, true)
            } catch (e: Exception) {
                Log.e(TAG, "Speak failed", e)
                updateState(AvatarState.Error("Speak failed: ${e.message}"))
            }
        }
    }

    private fun handleListenerCallback(methodName: String, args: Array<Any?>?): Any? {
        when (methodName) {
            "onInitEvent" -> {
                val code = args?.getOrNull(0) as? Int ?: -1
                val message = args?.getOrNull(1) as? String
                if (code == 0) {
                    instanceIsInitialized = true
                     isAvatarInitialized = true
                    recoveryAttempts = 0
                    updateState(AvatarState.Idle)
                } else {
                    instanceIsInitialized = false
                     isAvatarInitialized = false
                    val msg = message ?: "Init failed"
                    updateState(AvatarState.Error(msg))
                    if (isCacheMissingError(Exception(msg))) {
                        handleCacheMissingError(Exception(msg))
                    }
                }
            }
            "onVoiceStateChange" -> {
                val status = args?.getOrNull(0) as? String
                if (status == "voice_start") {
                    updateState(AvatarState.Speaking)
                } else if (status == "voice_end") {
                    updateState(AvatarState.Idle)
                    audioPlayer.markFinishSend(true)
                    abandonAudioFocus()
                }
            }
             "onError" -> {
                val errorMsg = args?.getOrNull(0)?.toString() ?: ""
                 if (isCacheMissingError(Exception(errorMsg))) {
                     handleCacheMissingError(Exception(errorMsg))
                 } else {
                     // Log but don't always change state to Error unless critical?
                     // SDK might report non-critical errors.
                 }
            }
            else -> {
                checkAudioCallback(methodName, args)
            }
        }
        return null
    }

    private fun checkAudioCallback(methodName: String, args: Array<Any?>?) {
         // Same logic as AvatarCoreService
         val isPossibleAudioCallback = methodName.contains("Audio", ignoreCase = true) || 
                                      methodName.contains("Stream", ignoreCase = true) ||
                                      methodName.contains("Pcm", ignoreCase = true) ||
                                      methodName.contains("Data", ignoreCase = true)
        if (isPossibleAudioCallback) {
            val audioData = args?.getOrNull(0) as? ByteArray
            if (audioData != null) {
                if (debugCallbackCounter == 0) audioPlayer.play()
                audioPlayer.setAudioData(audioData)
                debugCallbackCounter++
            }
        }
    }
    
    // Helpers
    private fun isCacheMissingError(e: Throwable): Boolean {
         val msg = e.message ?: ""
         return msg.contains(CACHE_DIR_NAME) || msg.contains("ENOENT") || msg.contains("No such file") || 
                (msg.contains("Failed to load resource") && msg.contains("char_data.bin"))
    }
    
    private fun handleCacheMissingError(e: Throwable) {
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            updateState(AvatarState.Error("Cache missing, recovery failed"))
            return
        }
        recoveryAttempts++
        updateState(AvatarState.Error("Recovering cache... ($recoveryAttempts)"))
        
        driverScope.launch {
            try {
                 val currentContainer = containerView
                 val gender = currentGender
                 if (currentContainer == null || gender == null) return@launch

                 operationMutex.withLock {
                     releaseInternal()
                     val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
                     cacheDir.deleteRecursively()
                     ensureCacheDirectory()
                     delay(500)
                     
                     // Re-bind
                     driverScope.launch {
                         // We need to call initialize again.
                         // initialize requires ActivityContext.
                         // But we only stored 'context' (Application) and 'container'.
                         // 'container.context' is usually Activity context!
                         initialize(currentContainer.context, currentContainer, gender)
                     }
                 }
            } catch (e: Exception) {
                Log.e(TAG, "Recovery failed", e)
            }
        }
    }

    private fun ensureCacheDirectory(): Boolean {
        val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
        return if (!cacheDir.exists()) cacheDir.mkdirs() else true
    }

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
                            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> audioPlayer.pause()
                            android.media.AudioManager.AUDIOFOCUS_GAIN -> audioPlayer.resume()
                            android.media.AudioManager.AUDIOFOCUS_LOSS -> audioPlayer.stop()
                        }
                    }
                }.build()
                audioManager.requestAudioFocus(focusRequest)
                audioFocusRequest = focusRequest
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio focus request failed", e)
        }
    }
    
    private fun abandonAudioFocus() {
         try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            audioFocusRequest = null
        } catch (e: Exception) {
            Log.e(TAG, "Audio focus abandon failed", e)
        }
    }

    private fun logPrefix(): String = "[XmovDriver|$currentSessionId]"
}
