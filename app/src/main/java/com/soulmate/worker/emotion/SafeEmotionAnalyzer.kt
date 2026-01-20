package com.soulmate.worker.emotion

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安全的情绪分析器包装器
 * 
 * 实现 "Try-Catch-Fallback" 模式：
 * 1. 首先尝试使用 ML 分析器（高精度）
 * 2. 如果 ML 不可用或出错，自动降级到关键词分析器
 * 
 * 这保证了系统永远不会因为 ML 模型问题而崩溃。
 */
@Singleton
class SafeEmotionAnalyzer @Inject constructor(
    private val mlAnalyzer: MLEmotionAnalyzer,
    private val keywordAnalyzer: KeywordEmotionAnalyzer
) : EmotionAnalyzer {
    
    companion object {
        private const val TAG = "SafeEmotionAnalyzer"
    }
    
    override suspend fun analyze(text: String): EmotionResult? {
        // 策略1: 尝试 ML 分析
        if (mlAnalyzer.isAvailable()) {
            try {
                val mlResult = mlAnalyzer.analyze(text)
                if (mlResult != null) {
                    Log.d(TAG, "Using ML result: ${mlResult.emotion} (${mlResult.confidence})")
                    return mlResult
                }
            } catch (e: Exception) {
                Log.w(TAG, "ML analysis failed, falling back to keyword: ${e.message}")
            }
        }
        
        // 策略2: 降级到关键词分析
        Log.d(TAG, "Using keyword fallback for: ${text.take(50)}...")
        return keywordAnalyzer.analyze(text)
    }
    
    override fun isAvailable(): Boolean = true  // SafeAnalyzer 永远可用
    
    override fun close() {
        mlAnalyzer.close()
        keywordAnalyzer.close()
    }
    
    /**
     * 获取当前使用的分析策略
     */
    fun getCurrentStrategy(): String = 
        if (mlAnalyzer.isAvailable()) "ML (MediaPipe BERT)" else "Keyword (Fallback)"
}
