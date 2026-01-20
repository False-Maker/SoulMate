package com.soulmate.worker

import android.content.Context
import android.util.Log
import com.soulmate.core.data.memory.MemoryRepository
import com.soulmate.worker.emotion.SafeEmotionAnalyzer
import com.soulmate.worker.emotion.EmotionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EmotionTracker - 情绪追踪器
 * 
 * 功能：
 * - 追踪用户最近对话中的情绪模式
 * - 检测连续低落情绪
 * - 提供情绪分析报告供心跳协议使用
 * 
 * 升级 v2.0:
 * - 集成 MediaPipe BERT 模型进行高精度情感分析
 * - 采用 "Try-Catch-Fallback" 模式，自动降级到关键词匹配
 */
@Singleton
class EmotionTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val emotionAnalyzer: SafeEmotionAnalyzer
) {
    
    companion object {
        private const val TAG = "EmotionTracker"
        
        // 低落情绪标签
        private val NEGATIVE_EMOTIONS = setOf("sad", "angry", "worried", "anxious", "depressed", "lonely", "frustrated")
        
        // 正面情绪标签
        private val POSITIVE_EMOTIONS = setOf("happy", "excited", "loving", "grateful", "calm", "hopeful")
        
        // 连续低落情绪阈值
        const val CONSECUTIVE_NEGATIVE_THRESHOLD = 3
        
        // 分析时间窗口（小时）
        const val ANALYSIS_WINDOW_HOURS = 48L
    }
    
    init {
        Log.i(TAG, "EmotionTracker initialized with strategy: ${emotionAnalyzer.getCurrentStrategy()}")
    }
    
    /**
     * 分析单条消息的情绪
     * 
     * @param text 待分析的消息文本
     * @return 情绪标签 (如 "happy", "sad", "neutral")
     */
    suspend fun analyzeText(text: String): String {
        val result = emotionAnalyzer.analyze(text)
        return result?.emotion ?: "neutral"
    }
    
    /**
     * 分析单条消息并返回完整结果
     */
    suspend fun analyzeTextWithConfidence(text: String): EmotionResult? {
        return emotionAnalyzer.analyze(text)
    }
    
    /**
     * 情绪分析结果
     */
    data class EmotionAnalysis(
        val recentEmotions: List<String>,
        val negativeCount: Int,
        val positiveCount: Int,
        val neutralCount: Int,
        val isConsecutiveNegative: Boolean,
        val dominantEmotion: String?,
        val needsSupport: Boolean
    )
    
    /**
     * 分析最近的情绪模式
     */
    suspend fun analyzeRecentEmotions(): EmotionAnalysis {
        val windowStart = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(ANALYSIS_WINDOW_HOURS)
        
        // 获取最近的记忆
        val recentMemories = memoryRepository.getRecentMemories(10)
        
        // 提取情绪标签
        val emotions = recentMemories
            .mapNotNull { it.emotion }
            .take(10)
        
        if (emotions.isEmpty()) {
            return EmotionAnalysis(
                recentEmotions = emptyList(),
                negativeCount = 0,
                positiveCount = 0,
                neutralCount = 0,
                isConsecutiveNegative = false,
                dominantEmotion = null,
                needsSupport = false
            )
        }
        
        // 统计情绪类型
        val negativeCount = emotions.count { it.lowercase() in NEGATIVE_EMOTIONS }
        val positiveCount = emotions.count { it.lowercase() in POSITIVE_EMOTIONS }
        val neutralCount = emotions.size - negativeCount - positiveCount
        
        // 检查连续低落情绪
        val isConsecutiveNegative = checkConsecutiveNegative(emotions)
        
        // 找出主导情绪
        val dominantEmotion = emotions.groupingBy { it.lowercase() }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        
        // 判断是否需要关怀
        val needsSupport = isConsecutiveNegative || 
            (negativeCount > emotions.size / 2 && emotions.size >= 3)
        
        Log.d(TAG, "Emotion analysis: negative=$negativeCount, positive=$positiveCount, " +
                "consecutive_negative=$isConsecutiveNegative, needs_support=$needsSupport")
        
        return EmotionAnalysis(
            recentEmotions = emotions,
            negativeCount = negativeCount,
            positiveCount = positiveCount,
            neutralCount = neutralCount,
            isConsecutiveNegative = isConsecutiveNegative,
            dominantEmotion = dominantEmotion,
            needsSupport = needsSupport
        )
    }
    
    /**
     * 检查是否有连续的低落情绪
     */
    private fun checkConsecutiveNegative(emotions: List<String>): Boolean {
        if (emotions.size < CONSECUTIVE_NEGATIVE_THRESHOLD) {
            return false
        }
        
        var consecutiveCount = 0
        for (emotion in emotions) {
            if (emotion.lowercase() in NEGATIVE_EMOTIONS) {
                consecutiveCount++
                if (consecutiveCount >= CONSECUTIVE_NEGATIVE_THRESHOLD) {
                    return true
                }
            } else {
                consecutiveCount = 0
            }
        }
        
        return false
    }
    
    /**
     * 生成情绪关怀提示词供 LLM 使用
     */
    suspend fun getEmotionSupportPromptContext(): String? {
        val analysis = analyzeRecentEmotions()
        
        if (!analysis.needsSupport) {
            return null
        }
        
        val sb = StringBuilder("【情绪关怀提醒】")
        
        if (analysis.isConsecutiveNegative) {
            sb.append("检测到用户最近连续表达了低落情绪。")
        } else {
            sb.append("用户最近的情绪状态偏低。")
        }
        
        analysis.dominantEmotion?.let { emotion ->
            sb.append("主要情绪表现为：$emotion。")
        }
        
        sb.append("\n\n请用温柔、关怀的语气问候用户，但不要直接指出你注意到他们情绪低落。")
        sb.append("可以：")
        sb.append("\n1. 表达你一直在这里陪伴他们")
        sb.append("\n2. 温和地询问最近的状况")
        sb.append("\n3. 提供情感支持，让他们知道可以倾诉")
        sb.append("\n4. 回忆一些美好的共同记忆")
        
        return sb.toString()
    }
    
    /**
     * 检查是否需要发送关怀通知
     */
    suspend fun shouldSendSupportNotification(): Boolean {
        val analysis = analyzeRecentEmotions()
        return analysis.needsSupport
    }
}
