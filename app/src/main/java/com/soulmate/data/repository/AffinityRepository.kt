package com.soulmate.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AffinityRepository - 亲和度管理器（惩罚系统）
 * 
 * 管理用户与AI之间的亲和度分数，实现惩罚机制。
 * 用户的粗鲁行为或长时间不互动会降低分数。
 * 
 * 亲和度等级：
 * - LOVE (80-100): 甜蜜状态，AI表现热情
 * - NORMAL (50-79): 正常状态
 * - COLD (<50): 冷战状态，AI表现冷淡
 * 
 * 与 IntimacyManager 的区别：
 * - IntimacyManager: 管理正向亲密度成长 (0-1000)
 * - AffinityRepository: 管理负向惩罚机制 (0-100)
 */
@Singleton
class AffinityRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "AffinityRepository"
        private const val PREFS_NAME = "affinity_preferences"
        private const val KEY_AFFINITY_SCORE = "affinity_score"
        
        /** 默认亲和度分数 */
        const val DEFAULT_SCORE = 60
        
        /** 分数范围 */
        const val MIN_SCORE = 0
        const val MAX_SCORE = 100
        
        /** 等级阈值 */
        const val THRESHOLD_LOVE = 80
        const val THRESHOLD_COLD = 50
        
        /** 扣分值 */
        const val DEDUCT_RUDE = -5      // 粗鲁言语
        const val DEDUCT_INACTIVE = -2   // 24小时不活跃
    }
    
    /**
     * 亲和度等级
     */
    enum class AffinityLevel {
        LOVE,   // 80-100: 甜蜜
        NORMAL, // 50-79: 正常
        COLD    // <50: 冷战
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _currentScore = MutableStateFlow(prefs.getInt(KEY_AFFINITY_SCORE, DEFAULT_SCORE))
    
    /**
     * 观察当前亲和度分数
     */
    val affinityScore: Flow<Int> = _currentScore.asStateFlow()
    
    /**
     * 观察当前亲和度等级
     */
    val affinityLevel: Flow<AffinityLevel> = _currentScore.map { score ->
        when {
            score >= THRESHOLD_LOVE -> AffinityLevel.LOVE
            score >= THRESHOLD_COLD -> AffinityLevel.NORMAL
            else -> AffinityLevel.COLD
        }
    }
    
    /**
     * 观察是否处于冷战状态
     */
    val isColdWar: Flow<Boolean> = _currentScore.map { score ->
        score < THRESHOLD_COLD
    }
    
    /**
     * 获取当前亲和度分数
     */
    fun getCurrentScore(): Int {
        return _currentScore.value
    }
    
    /**
     * 获取当前亲和度等级
     */
    fun getCurrentLevel(): AffinityLevel {
        val score = _currentScore.value
        return when {
            score >= THRESHOLD_LOVE -> AffinityLevel.LOVE
            score >= THRESHOLD_COLD -> AffinityLevel.NORMAL
            else -> AffinityLevel.COLD
        }
    }
    
    /**
     * 获取当前等级的中文名称
     */
    fun getCurrentLevelName(): String {
        return when (getCurrentLevel()) {
            AffinityLevel.LOVE -> "甜蜜"
            AffinityLevel.NORMAL -> "正常"
            AffinityLevel.COLD -> "冷战"
        }
    }
    
    /**
     * 调整亲和度分数
     * 
     * @param delta 分数变化量（正数增加，负数减少）
     * @return 调整后的分数
     */
    fun adjustScore(delta: Int): Int {
        val oldScore = _currentScore.value
        val newScore = (oldScore + delta).coerceIn(MIN_SCORE, MAX_SCORE)
        
        prefs.edit().putInt(KEY_AFFINITY_SCORE, newScore).apply()
        _currentScore.value = newScore
        
        Log.d(TAG, "Affinity score adjusted: $oldScore -> $newScore (delta: $delta)")
        
        // 检查是否进入冷战状态
        if (oldScore >= THRESHOLD_COLD && newScore < THRESHOLD_COLD) {
            Log.w(TAG, "⚠️ ColdWar state triggered! Score dropped below $THRESHOLD_COLD")
        }
        
        return newScore
    }
    
    /**
     * 因粗鲁言语扣分
     */
    fun deductForRudeness(): Int {
        Log.d(TAG, "Deducting for rude behavior: $DEDUCT_RUDE")
        return adjustScore(DEDUCT_RUDE)
    }
    
    /**
     * 因长时间不活跃扣分
     */
    fun deductForInactivity(): Int {
        Log.d(TAG, "Deducting for inactivity: $DEDUCT_INACTIVE")
        return adjustScore(DEDUCT_INACTIVE)
    }
    
    /**
     * 恢复亲和度（例如：用户道歉后）
     * 
     * @param amount 恢复的分数
     */
    fun recover(amount: Int = 3): Int {
        Log.d(TAG, "Recovering affinity: +$amount")
        return adjustScore(amount)
    }
    
    /**
     * 重置亲和度为默认值
     */
    fun reset() {
        prefs.edit().putInt(KEY_AFFINITY_SCORE, DEFAULT_SCORE).apply()
        _currentScore.value = DEFAULT_SCORE
        Log.d(TAG, "Affinity score reset to default: $DEFAULT_SCORE")
    }
    
    /**
     * 直接设置亲和度分数（用于调试/演示）
     * 
     * @param score 目标分数
     */
    fun setScore(score: Int) {
        val validScore = score.coerceIn(MIN_SCORE, MAX_SCORE)
        prefs.edit().putInt(KEY_AFFINITY_SCORE, validScore).apply()
        _currentScore.value = validScore
        Log.d(TAG, "Affinity score set to: $validScore")
    }
}
