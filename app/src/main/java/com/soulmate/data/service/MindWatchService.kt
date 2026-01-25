package com.soulmate.data.service

import android.content.Context
import android.util.Log
import com.soulmate.core.data.memory.MemoryRepository
import com.soulmate.data.model.EmotionRecordEntity
import com.soulmate.data.model.EmotionRecordEntity_
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MindWatchService - 心灵守护服务（MindWatch System）
 * 
 * 核心功能：
 * 1. 关键词追踪 - 监测对话中的敏感词汇
 * 2. 情绪曲线 - 追踪用户情绪变化趋势
 * 3. 危机熔断 - 检测到危机信号时触发干预
 * 
 * 这是一个商业化功能，用于家长/监护人监测被关护者的心理健康状态。
 * Update: 使用 ObjectBox 持久化存储情绪数据
 */
@Singleton
class MindWatchService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val boxStore: BoxStore
) {
    
    companion object {
        private const val TAG = "MindWatchService"
        
        // 危机关键词（需要立即关注）
        private val CRISIS_KEYWORDS = setOf(
            "自杀", "不想活", "死", "结束", "跳楼", "割腕",
            "suicide", "kill myself", "end it", "die"
        )
        
        // 警告关键词（需要持续关注）
        private val WARNING_KEYWORDS = setOf(
            "抑郁", "绝望", "没意思", "活着累", "讨厌自己", "没人爱我",
            "孤独", "害怕", "焦虑", "失眠", "噩梦",
            "心情不好", "心情差", "不开心", "难过", "沮丧",
            "depressed", "hopeless", "lonely", "anxiety", "nightmare",
            "sad", "unhappy", "upset", "down"
        )
        
        // 正面关键词（情绪改善信号）
        private val POSITIVE_KEYWORDS = setOf(
            "开心", "快乐", "感谢", "幸福", "希望", "期待",
            "好起来", "变好", "阳光", "微笑",
            "happy", "grateful", "hopeful", "better", "smile"
        )
        
        // 情绪评分权重
        const val CRISIS_SCORE = -10
        const val WARNING_SCORE = -3
        const val POSITIVE_SCORE = 2
        const val NEUTRAL_SCORE = 0
    }
    
    /**
     * 监测状态
     */
    enum class WatchStatus {
        NORMAL,     // 正常
        CAUTION,    // 需要关注
        WARNING,    // 警告
        CRISIS      // 危机
    }
    
    /**
     * 情绪数据点 (DTO for UI/Reports)
     */
    data class EmotionDataPoint(
        val timestamp: Long,
        val score: Int,
        val emotion: String?,
        val keywords: List<String>
    )
    
    /**
     * 监测报告
     */
    data class WatchReport(
        val status: WatchStatus,
        val emotionTrend: List<EmotionDataPoint>,
        val averageScore: Float,
        val crisisKeywordsFound: List<String>,
        val warningKeywordsFound: List<String>,
        val recommendation: String
    )
    
    // ObjectBox Box
    private val emotionBox = boxStore.boxFor(EmotionRecordEntity::class)
    
    // 当前监测状态
    private val _currentStatus = MutableStateFlow(WatchStatus.NORMAL)
    val currentStatus: StateFlow<WatchStatus> = _currentStatus.asStateFlow()
    
    // 是否启用监测
    private var isEnabled = true // 默认启用，或者从配置读取
    
    init {
        // 初始化时根据历史数据计算状态
        recalculateStatus()
    }
    
    /**
     * 启用 MindWatch 监测
     */
    fun enable() {
        isEnabled = true
        Log.d(TAG, "MindWatch enabled")
    }
    
    /**
     * 禁用 MindWatch 监测
     */
    fun disable() {
        isEnabled = false
        Log.d(TAG, "MindWatch disabled")
    }
    
    /**
     * 分析文本内容
     * @param text 要分析的文本
     * @param emotion 可选的情绪标签
     * @return 分析结果，如果检测到危机则返回 true
     */
    fun analyzeText(text: String, emotion: String? = null): Boolean {
        if (!isEnabled) return false
        
        val lowerText = text.lowercase()
        
        // 检测关键词
        val foundCrisis = CRISIS_KEYWORDS.filter { lowerText.contains(it.lowercase()) }
        val foundWarning = WARNING_KEYWORDS.filter { lowerText.contains(it.lowercase()) }
        val foundPositive = POSITIVE_KEYWORDS.filter { lowerText.contains(it.lowercase()) }
        
        // 计算情绪分数
        val score = calculateScore(foundCrisis.size, foundWarning.size, foundPositive.size)
        
        // 下面这段逻辑避免全是0分的记录充斥数据库，但为了画图连贯性，建议还是记录
        // 或者只记录有意义的变化/有关键词/有情绪的情况
        // 这里策略：只要有 emotion 标签或者有关键词 或者分数不为0 就记录
        if (score != 0 || !emotion.isNullOrBlank() || foundCrisis.isNotEmpty() || foundWarning.isNotEmpty()) {
            val entity = EmotionRecordEntity(
                timestamp = System.currentTimeMillis(),
                score = score,
                emotionLabel = emotion,
                keywords = (foundCrisis + foundWarning + foundPositive).joinToString(",")
            )
            emotionBox.put(entity)
        }
        
        // 简单日志
        if (foundCrisis.isNotEmpty()) {
            Log.w(TAG, "CRISIS keywords detected: $foundCrisis")
        }
        
        // 更新状态
        updateStatus(foundCrisis.isNotEmpty(), foundWarning.isNotEmpty())
        
        return foundCrisis.isNotEmpty()
    }
    
    /**
     * 计算情绪分数
     */
    private fun calculateScore(crisisCount: Int, warningCount: Int, positiveCount: Int): Int {
        // 限制分数范围在 -10 到 10
        val rawScore = crisisCount * CRISIS_SCORE + 
                       warningCount * WARNING_SCORE + 
                       positiveCount * POSITIVE_SCORE
        return rawScore.coerceIn(-10, 10)
    }
    
    /**
     * 更新监测状态 (基于数据库最近记录)
     */
    private fun updateStatus(hasCrisis: Boolean, hasWarning: Boolean) {
        if (hasCrisis) {
            _currentStatus.value = WatchStatus.CRISIS
            return
        }
        
        // 获取最近记录重新计算
        recalculateStatus()
    }
    
    private fun recalculateStatus() {
        // 查询最近 10 条记录
        val recentRecords = emotionBox.query()
            .orderDesc(EmotionRecordEntity_.timestamp)
            .build()
            .find(0, 10)
        
        if (recentRecords.isEmpty()) {
            _currentStatus.value = WatchStatus.NORMAL
            return
        }
        
        val recentAvg = recentRecords.map { it.score }.average()
        val recentWarnings = recentRecords.take(5).count { it.score <= WARNING_SCORE }
        
        val newStatus = when {
            recentWarnings >= 3 -> WatchStatus.WARNING
            recentAvg < -5 -> WatchStatus.WARNING
            recentAvg < -2 -> WatchStatus.CAUTION
            else -> WatchStatus.NORMAL
        }
        
        if (_currentStatus.value != newStatus) {
            Log.d(TAG, "Status changed: ${_currentStatus.value} -> $newStatus")
            _currentStatus.value = newStatus
        }
    }
    
    /**
     * 生成监测报告
     */
    fun generateReport(days: Int = 7): WatchReport {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        
        // 查询时间范围内的记录
        val recentDataEntities = emotionBox.query(EmotionRecordEntity_.timestamp.greater(cutoffTime))
            .order(EmotionRecordEntity_.timestamp)
            .build()
            .find()
            
        // 转换为 DTO
        val recentData = recentDataEntities.map { entity ->
            EmotionDataPoint(
                timestamp = entity.timestamp,
                score = entity.score,
                emotion = entity.emotionLabel,
                keywords = entity.getKeywordList()
            )
        }
        
        val crisisKeywords = recentData.flatMap { it.keywords }
            .filter { it.lowercase() in CRISIS_KEYWORDS.map { k -> k.lowercase() } }
            .distinct()
            
        val warningKeywords = recentData.flatMap { it.keywords }
            .filter { it.lowercase() in WARNING_KEYWORDS.map { k -> k.lowercase() } }
            .distinct()
        
        val avgScore = if (recentData.isNotEmpty()) {
            recentData.map { it.score }.average().toFloat()
        } else {
            0f
        }
        
        val recommendation = generateRecommendation(avgScore, crisisKeywords, warningKeywords)
        
        return WatchReport(
            status = _currentStatus.value,
            emotionTrend = recentData,
            averageScore = avgScore,
            crisisKeywordsFound = crisisKeywords,
            warningKeywordsFound = warningKeywords,
            recommendation = recommendation
        )
    }
    
    /**
     * 生成建议
     */
    private fun generateRecommendation(
        avgScore: Float, 
        crisisKeywords: List<String>, 
        warningKeywords: List<String>
    ): String {
        return when {
            crisisKeywords.isNotEmpty() -> 
                "检测到危机信号，建议立即与被监护人沟通，必要时寻求专业心理咨询帮助。"
            avgScore < -5 -> 
                "情绪状态持续低落，建议增加陪伴和关心，必要时寻求专业帮助。"
            avgScore < -2 -> 
                "情绪有波动，建议多关注，适当增加互动和支持。"
            warningKeywords.isNotEmpty() -> 
                "偶有负面情绪表达，属于正常范围，建议保持日常关注。"
            else -> 
                "情绪状态良好，请继续保持关心和陪伴。"
        }
    }
    
    /**
     * 获取情绪趋势数据（用于图表展示）
     */
    fun getEmotionTrend(days: Int = 7): List<Pair<Long, Float>> {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        
        val records = emotionBox.query(EmotionRecordEntity_.timestamp.greater(cutoffTime))
            .order(EmotionRecordEntity_.timestamp)
            .build()
            .find()
            
        return records
            .groupBy { 
                // 按相关时间点聚合，这里按小时或者按天，根据 UI 需求
                // 为了画曲线平滑，建议按 "每次对话" 或者 "每小时"
                // 这里按天聚合防止点太多
                it.timestamp / TimeUnit.DAYS.toMillis(1)
            }
            .map { (day, points) ->
                val avgScore = points.map { it.score }.average().toFloat()
                Pair(day * TimeUnit.DAYS.toMillis(1), avgScore)
            }
            .sortedBy { it.first }
    }
    
    /**
     * 清除历史数据
     */
    fun clearHistory() {
        emotionBox.removeAll()
        _currentStatus.value = WatchStatus.NORMAL
        Log.d(TAG, "History cleared")
    }
    
    /**
     * 获取最近的记录（供调试或首页显示）
     */
    fun getRecentRecords(limit: Int = 20): List<EmotionRecordEntity> {
        return emotionBox.query()
            .orderDesc(EmotionRecordEntity_.timestamp)
            .build()
            .find(0, limit.toLong())
    }
}
