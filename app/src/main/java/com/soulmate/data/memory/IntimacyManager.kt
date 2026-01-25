package com.soulmate.data.memory

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IntimacyManager - 亲密度管理器
 * 
 * 管理用户与AI之间的亲密度分数，实现关系渐进系统。
 * AI的人格会根据亲密度从"陌生人"逐渐演变为"恋人"。
 * 
 * 亲密度等级：
 * - Level 1 (0-199): 陌生人 - 礼貌但保持距离
 * - Level 2 (200-499): 朋友 - 轻松随意，开玩笑
 * - Level 3 (500-799): 暗恋 - 微妙的调情，关心
 * - Level 4 (800+): 恋人 - 深情的浪漫伴侣
 */
@Singleton
class IntimacyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val PREFS_NAME = "intimacy_preferences"
        private const val KEY_CURRENT_SCORE = "current_score"
        private const val KEY_LAST_BASE_GAIN_AT = "last_base_gain_at"
        private const val KEY_LAST_EMOTIONAL_GAIN_AT = "last_emotional_gain_at"
        
        /** 最大亲密度分数 */
        const val MAX_SCORE = 1000
        
        /** 每次聊天获得的分数 */
        const val POINTS_PER_CHAT = 1
        
        /** 情感关键词额外分数 */
        const val POINTS_EMOTIONAL_LOW = 2
        const val POINTS_EMOTIONAL_HIGH = 5
        
        /** 等级阈值 */
        const val THRESHOLD_FRIEND = 200
        const val THRESHOLD_CRUSH = 500
        const val THRESHOLD_LOVER = 800
        
        /** 基础加分节流 */
        private const val BASE_GAIN_COOLDOWN_MS = 60_000L

        /** 情感加分节流 */
        private const val EMOTIONAL_GAIN_COOLDOWN_MS = 10 * 60_000L

        /** 情感关键词列表（A组：情绪表达，可少量加分） */
        private val EMOTIONAL_KEYWORDS_A = listOf(
            "心情", "难过", "开心", "快乐", "伤心", "感动", "幸福", "温暖",
            "焦虑", "低落", "失落", "委屈", "害怕", "紧张", "压力", "疲惫"
        )

        /** 强亲密关键词列表（B组：示爱/亲密称呼，仅 Level2+ 加分） */
        private val EMOTIONAL_KEYWORDS_B = listOf(
            "爱你", "喜欢你", "想你", "想念", "宝贝", "亲爱的", "老公", "老婆",
            "抱抱", "亲亲", "么么", "比心", "mua", "❤", "💕", "😘",
            "好想", "很想", "特别想", "一直想"
        )
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _currentScore = MutableStateFlow(prefs.getInt(KEY_CURRENT_SCORE, 0))
    val currentScore: Flow<Int> = _currentScore.asStateFlow()
    
    /**
     * 获取当前亲密度分数
     */
    fun getCurrentScore(): Int {
        return _currentScore.value
    }
    
    /**
     * 调整亲密度分数
     * 
     * @param delta 分数变化量（正数增加，负数减少）
     * @return 调整后的分数
     */
    fun adjustScore(delta: Int): Int {
        val newScore = (_currentScore.value + delta).coerceIn(0, MAX_SCORE)
        prefs.edit().putInt(KEY_CURRENT_SCORE, newScore).apply()
        _currentScore.value = newScore
        return newScore
    }
    
    /**
     * 重置亲密度分数为0
     */
    fun resetScore() {
        prefs.edit().putInt(KEY_CURRENT_SCORE, 0).apply()
        _currentScore.value = 0
    }
    
    /**
     * 直接设置亲密度分数（用于调试/演示）
     * 
     * @param score 目标分数
     */
    fun setScore(score: Int) {
        val validScore = score.coerceIn(0, MAX_SCORE)
        prefs.edit().putInt(KEY_CURRENT_SCORE, validScore).apply()
        _currentScore.value = validScore
    }
    
    /**
     * 检测文本是否包含情感关键词
     * 
     * @param text 用户输入文本
     * @return 是否包含情感关键词
     */
    fun containsEmotionalKeyword(text: String): Boolean {
        val lowerText = text.lowercase()
        return EMOTIONAL_KEYWORDS_A.any { keyword -> lowerText.contains(keyword.lowercase()) }
    }

    /**
     * 检测是否包含强亲密关键词（B组）
     */
    fun containsStrongIntimacyKeyword(text: String): Boolean {
        val lowerText = text.lowercase()
        return EMOTIONAL_KEYWORDS_B.any { keyword -> lowerText.contains(keyword.lowercase()) }
    }
    
    /**
     * 获取当前亲密度等级
     * 
     * @return 1-4 的等级值
     */
    fun getCurrentLevel(): Int {
        val score = _currentScore.value
        return when {
            score >= THRESHOLD_LOVER -> 4
            score >= THRESHOLD_CRUSH -> 3
            score >= THRESHOLD_FRIEND -> 2
            else -> 1
        }
    }
    
    /**
     * 获取当前等级的中文名称
     */
    fun getCurrentLevelName(): String {
        return when (getCurrentLevel()) {
            1 -> "陌生人"
            2 -> "朋友"
            3 -> "暗恋"
            4 -> "恋人"
            else -> "未知"
        }
    }
    
    /**
     * 处理聊天交互，自动计算并调整分数
     * 
     * @param userMessage 用户消息
     * @return 本次获得的分数
     */
    fun processInteraction(userMessage: String): Int {
        val now = System.currentTimeMillis()
        var points = 0

        val lastBaseGainAt = prefs.getLong(KEY_LAST_BASE_GAIN_AT, 0L)
        if (now - lastBaseGainAt >= BASE_GAIN_COOLDOWN_MS) {
            points += POINTS_PER_CHAT
            prefs.edit().putLong(KEY_LAST_BASE_GAIN_AT, now).apply()
        }

        val lastEmotionalGainAt = prefs.getLong(KEY_LAST_EMOTIONAL_GAIN_AT, 0L)
        val canEmotionalGain = now - lastEmotionalGainAt >= EMOTIONAL_GAIN_COOLDOWN_MS
        val level = getCurrentLevel()
        val hasGroupA = containsEmotionalKeyword(userMessage)
        val hasGroupB = containsStrongIntimacyKeyword(userMessage)

        if (canEmotionalGain) {
            when {
                hasGroupB && level >= 2 -> {
                    points += POINTS_EMOTIONAL_HIGH
                    prefs.edit().putLong(KEY_LAST_EMOTIONAL_GAIN_AT, now).apply()
                }
                hasGroupA -> {
                    points += POINTS_EMOTIONAL_LOW
                    prefs.edit().putLong(KEY_LAST_EMOTIONAL_GAIN_AT, now).apply()
                }
            }
        }

        if (points > 0) {
            adjustScore(points)
        }
        return points
    }
}
