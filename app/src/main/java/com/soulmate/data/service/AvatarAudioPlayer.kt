package com.soulmate.data.service

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue

/**
 * 数字人音频播放器
 * 用于播放从 Xmov SDK 接收到的 PCM 音频数据
 */
class AvatarAudioPlayer {
    
    enum class PlayState {
        IDLE,
        PLAYING,
        PAUSE,
        RELEASE
    }
    
    companion object {
        private const val TAG = "AvatarAudioPlayer"
        private const val SAMPLE_RATE = 16000
        private const val MAGNIFICATION = 2
    }
    
    private var playState = PlayState.IDLE
    private var isFinishSend = false
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var audioTrack: AudioTrack? = null
    private var playerThread: Thread? = null
    
    private var totalBytes = 0L
    private var playedBytes = 0L
    
    init {
        initializeAudioTrack()
    }
    
    /**
     * 初始化 AudioTrack
     */
    private fun initializeAudioTrack() {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * MAGNIFICATION
            
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize,
                AudioTrack.MODE_STREAM
            )
            
            if (audioTrack?.state == AudioTrack.STATE_UNINITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed")
                audioTrack = null
            } else {
                Log.i(TAG, "AudioTrack initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
            audioTrack = null
        }
    }
    
    /**
     * 设置音频数据
     */
    fun setAudioData(data: ByteArray) {
        if (data.isEmpty()) return
        
        totalBytes += data.size
        audioQueue.offer(data)
    }
    
    /**
     * 标记音频数据发送完成
     */
    fun markFinishSend(isFinish: Boolean) {
        isFinishSend = isFinish
        Log.d(TAG, "markFinishSend: $isFinish")
    }
    
    /**
     * 开始播放
     */
    fun play() {
        if (playState == PlayState.RELEASE) {
            Log.w(TAG, "Cannot play: player is released")
            return
        }
        
        if (audioTrack == null) {
            Log.w(TAG, "AudioTrack is null, reinitializing...")
            initializeAudioTrack()
            if (audioTrack == null) {
                Log.e(TAG, "Failed to reinitialize AudioTrack")
                return
            }
        }
        
        playState = PlayState.PLAYING
        isFinishSend = false
        
        // 启动播放线程
        if (playerThread?.isAlive != true) {
            playerThread = Thread {
                playAudioLoop()
            }.apply {
                isDaemon = true
                start()
            }
        }
        
        // 启动 AudioTrack
        try {
            audioTrack?.play()
            Log.d(TAG, "Audio playback started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack", e)
        }
    }
    
    /**
     * 音频播放循环
     */
    private fun playAudioLoop() {
        Log.d(TAG, "Audio playback thread started")
        
        while (playState != PlayState.RELEASE) {
            if (playState == PlayState.PLAYING) {
                if (audioQueue.isEmpty()) {
                    if (isFinishSend) {
                        // 所有数据已发送完成，等待队列清空
                        Log.d(TAG, "All audio data sent, waiting for queue to empty...")
                        // 给一点时间让最后的数据播放完
                        try {
                            Thread.sleep(100)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                        
                        if (audioQueue.isEmpty()) {
                            Log.d(TAG, "Audio playback completed")
                            playState = PlayState.IDLE
                            resetCounters()
                        }
                    } else {
                        // 数据还在传输中，等待
                        try {
                            Thread.sleep(10)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                    continue
                }
                
                // 从队列中取出音频数据
                val audioData = try {
                    audioQueue.take()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                
                // 写入 AudioTrack
                try {
                    val written = audioTrack?.write(audioData, 0, audioData.size) ?: 0
                    if (written < 0) {
                        Log.e(TAG, "AudioTrack write error: $written")
                    } else {
                        playedBytes += written
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write audio data", e)
                }
            } else {
                // 非播放状态，等待
                try {
                    Thread.sleep(20)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        
        Log.d(TAG, "Audio playback thread exited")
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        playState = PlayState.IDLE
        audioQueue.clear()
        resetCounters()
        
        try {
            audioTrack?.apply {
                flush()
                pause()
                stop()
            }
            Log.d(TAG, "Audio playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop AudioTrack", e)
        }
    }
    
    /**
     * 暂停播放
     */
    fun pause() {
        playState = PlayState.PAUSE
        try {
            audioTrack?.pause()
            Log.d(TAG, "Audio playback paused")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause AudioTrack", e)
        }
    }
    
    /**
     * 恢复播放
     */
    fun resume() {
        playState = PlayState.PLAYING
        try {
            audioTrack?.play()
            Log.d(TAG, "Audio playback resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume AudioTrack", e)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        playState = PlayState.RELEASE
        stop()
        
        try {
            audioTrack?.release()
            audioTrack = null
            Log.d(TAG, "AudioTrack released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release AudioTrack", e)
        }
    }
    
    /**
     * 重置计数器
     */
    private fun resetCounters() {
        totalBytes = 0
        playedBytes = 0
        isFinishSend = false
    }
}
