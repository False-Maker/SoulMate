package com.soulmate.data.avatar

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ViewGroup
import com.soulmate.core.data.avatar.IAvatarDriver
import com.soulmate.data.model.UserGender
import com.soulmate.data.service.AvatarState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.random.Random

@Singleton
class OrbAvatarDriver @Inject constructor(
    @ApplicationContext private val appContext: Context
) : IAvatarDriver {

    private val driverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _audioAmplitude = MutableStateFlow(0f)
    override val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private var tts: TextToSpeech? = null
    private var initialized = false
    private var stateListener: ((AvatarState) -> Unit)? = null
    private var amplitudeJob: kotlinx.coroutines.Job? = null

    override suspend fun initialize(context: Context, container: ViewGroup, gender: UserGender): Boolean {
        if (initialized) return true
        val initSuccess = suspendCancellableCoroutine<Boolean> { cont ->
            val engine = TextToSpeech(appContext) { status ->
                cont.resume(status == TextToSpeech.SUCCESS)
            }
            tts = engine
            cont.invokeOnCancellation {
                engine.shutdown()
            }
        }
        if (!initSuccess) {
            tts?.shutdown()
            tts = null
            initialized = false
            updateState(AvatarState.Error("TTS init failed"))
            return false
        }
        tts?.language = Locale.getDefault()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                updateState(AvatarState.Speaking)
            }

            override fun onDone(utteranceId: String?) {
                updateState(AvatarState.Idle)
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                updateState(AvatarState.Idle)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                updateState(AvatarState.Idle)
            }
        })
        initialized = true
        updateState(AvatarState.Idle)
        return true
    }

    override fun destroy() {
        driverScope.launch { release() }
    }

    override fun pause() {
    }

    override fun resume() {
    }

    override suspend fun release() {
        stopAmplitude()
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
        updateState(AvatarState.Idle)
    }

    override fun setListener(listener: (AvatarState) -> Unit) {
        stateListener = listener
    }

    override fun startSpeaking(text: String) {
        if (!initialized) return
        val engine = tts ?: return
        val utteranceId = UUID.randomUUID().toString()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stopSpeaking() {
        tts?.stop()
        updateState(AvatarState.Idle)
    }

    override fun playMotion(motionName: String) {
    }

    override fun setState(state: AvatarState) {
        updateState(state)
    }

    override fun isInitialized(): Boolean = initialized

    private fun updateState(state: AvatarState) {
        stateListener?.invoke(state)
        when (state) {
            is AvatarState.Speaking -> startAmplitude()
            else -> stopAmplitude()
        }
    }

    private fun startAmplitude() {
        if (amplitudeJob?.isActive == true) return
        amplitudeJob = driverScope.launch {
            while (isActive) {
                _audioAmplitude.value = Random.nextFloat() * 0.7f + 0.3f
                delay(100)
            }
        }
    }

    private fun stopAmplitude() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        _audioAmplitude.value = 0f
    }
}
