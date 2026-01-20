package com.soulmate.worker.emotion

import android.util.Log
import javax.inject.Inject

/**
 * 基于关键词匹配的情绪分析器 (兜底方案)
 * 
 * 这是从原 EmotionTracker 提取的逻辑，保证 100% 向后兼容。
 * 当 ML 分析器不可用时，自动降级使用此分析器。
 */
class KeywordEmotionAnalyzer @Inject constructor() : EmotionAnalyzer {
    
    companion object {
        private const val TAG = "KeywordEmotionAnalyzer"
        
        // 低落情绪关键词映射
        private val NEGATIVE_KEYWORDS = mapOf(
            "sad" to listOf("难过", "伤心", "悲伤", "哭", "痛苦", "失落", "sad", "upset", "cry"),
            "angry" to listOf("生气", "愤怒", "气死", "烦", "讨厌", "angry", "mad", "hate"),
            "worried" to listOf("担心", "焦虑", "紧张", "害怕", "恐惧", "worried", "anxious", "nervous"),
            "anxious" to listOf("焦虑", "不安", "惶恐", "anxiety", "uneasy"),
            "depressed" to listOf("抑郁", "绝望", "无望", "想死", "活着没意思", "depressed", "hopeless"),
            "lonely" to listOf("孤独", "寂寞", "没人理", "一个人", "lonely", "alone"),
            "frustrated" to listOf("沮丧", "挫败", "失败", "不顺", "frustrated", "defeated")
        )
        
        // 正面情绪关键词映射
        private val POSITIVE_KEYWORDS = mapOf(
            "happy" to listOf("开心", "高兴", "快乐", "幸福", "棒", "太好了", "happy", "joy", "great"),
            "excited" to listOf("兴奋", "激动", "期待", "盼望", "excited", "thrilled"),
            "loving" to listOf("爱", "喜欢", "想你", "爱你", "love", "like", "miss you"),
            "grateful" to listOf("感谢", "感恩", "谢谢", "多亏", "grateful", "thankful", "thanks"),
            "calm" to listOf("平静", "安心", "放松", "舒服", "calm", "relaxed", "peaceful"),
            "hopeful" to listOf("希望", "期望", "相信", "会好的", "hopeful", "optimistic")
        )
    }
    
    override suspend fun analyze(text: String): EmotionResult? {
        val lowerText = text.lowercase()
        
        // 先检查负面情绪（通常更需要关注）
        for ((emotion, keywords) in NEGATIVE_KEYWORDS) {
            for (keyword in keywords) {
                if (lowerText.contains(keyword)) {
                    Log.d(TAG, "Detected negative emotion '$emotion' from keyword '$keyword'")
                    return EmotionResult(emotion = emotion, confidence = 0.7f)
                }
            }
        }
        
        // 再检查正面情绪
        for ((emotion, keywords) in POSITIVE_KEYWORDS) {
            for (keyword in keywords) {
                if (lowerText.contains(keyword)) {
                    Log.d(TAG, "Detected positive emotion '$emotion' from keyword '$keyword'")
                    return EmotionResult(emotion = emotion, confidence = 0.7f)
                }
            }
        }
        
        // 无法识别，返回中性
        return EmotionResult(emotion = "neutral", confidence = 0.5f)
    }
    
    override fun isAvailable(): Boolean = true  // 关键词匹配永远可用
    
    override fun close() {
        // 无需清理
    }
}
