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
        
        /** 最大亲密度分数 */
        const val MAX_SCORE = 1000
        
        /** 每次聊天获得的分数 */
        const val POINTS_PER_CHAT = 1
        
        /** 情感关键词额外分数 */
        const val POINTS_EMOTIONAL = 5
        
        /** 等级阈值 */
        const val THRESHOLD_FRIEND = 200
        const val THRESHOLD_CRUSH = 500
        const val THRESHOLD_LOVER = 800
        
        /** 情感关键词列表 */
        private val EMOTIONAL_KEYWORDS = listOf(
            // 爱意表达
            "爱", "喜欢", "想你", "想念", "宝贝", "亲爱的", "老公", "老婆",
            // 情感词汇
            "心情", "难过", "开心", "快乐", "伤心", "感动", "幸福", "温暖",
            // 亲昵表达
            "抱抱", "亲亲", "么么", "比心", "mua", "❤", "💕", "😘",
            // 思念
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
        return EMOTIONAL_KEYWORDS.any { keyword ->
            lowerText.contains(keyword.lowercase())
        }
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
        var points = POINTS_PER_CHAT
        
        // 如果包含情感关键词，额外加分
        if (containsEmotionalKeyword(userMessage)) {
            points += POINTS_EMOTIONAL
        }
        
        adjustScore(points)
        return points
    }
}
