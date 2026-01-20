package com.soulmate.worker.emotion

/**
 * 情绪分析结果
 * 
 * @property emotion 识别到的情绪标签 (如 "happy", "sad", "neutral")
 * @property confidence 置信度 (0.0 - 1.0)
 */
data class EmotionResult(
    val emotion: String,
    val confidence: Float
)

/**
 * 情绪分析策略接口
 * 
 * 采用策略模式，支持多种情绪分析实现：
 * - KeywordEmotionAnalyzer: 基于关键词匹配 (兜底方案)
 * - MLEmotionAnalyzer: 基于 MediaPipe BERT 模型 (高精度)
 */
interface EmotionAnalyzer {
    
    /**
     * 分析文本中的情绪
     * 
     * @param text 待分析的文本
     * @return 情绪分析结果，如果无法识别则返回 null
     */
    suspend fun analyze(text: String): EmotionResult?
    
    /**
     * 检查分析器是否可用
     * 用于决定是否需要降级到备用方案
     */
    fun isAvailable(): Boolean
    
    /**
     * 释放资源 (如 ML 模型)
     */
    fun close()
}
