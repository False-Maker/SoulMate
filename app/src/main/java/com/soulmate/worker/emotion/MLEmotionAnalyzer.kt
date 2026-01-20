package com.soulmate.worker.emotion

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 基于 MediaPipe BERT 的情绪分析器 (高精度方案)
 * 
 * 使用 Google MediaPipe 在设备端运行 BERT 模型进行情绪分类。
 * 优势：
 * - 能识别反话、隐含情绪
 * - 离线可用
 * - 隐私安全（数据不出设备）
 */
class MLEmotionAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) : EmotionAnalyzer {
    
    companion object {
        private const val TAG = "MLEmotionAnalyzer"
        private const val MODEL_PATH = "bert_classifier.tflite"
        
        // MediaPipe 模型输出的分类标签映射到我们的情绪标签
        private val LABEL_MAPPING = mapOf(
            "positive" to "happy",
            "negative" to "sad", 
            "neutral" to "neutral",
            // BERT 情感分类标签
            "joy" to "happy",
            "sadness" to "sad",
            "anger" to "angry",
            "fear" to "worried",
            "surprise" to "excited",
            "disgust" to "frustrated",
            "love" to "loving"
        )
    }
    
    private var textClassifier: TextClassifier? = null
    private var isModelLoaded = false
    
    init {
        initializeModel()
    }
    
    private fun initializeModel() {
        try {
            // 检查模型文件是否存在
            val assetFiles = context.assets.list("") ?: emptyArray()
            if (MODEL_PATH !in assetFiles) {
                Log.w(TAG, "Model file '$MODEL_PATH' not found in assets. ML analyzer disabled.")
                return
            }
            
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)
                .build()
            
            val options = TextClassifier.TextClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .build()
            
            textClassifier = TextClassifier.createFromOptions(context, options)
            isModelLoaded = true
            Log.i(TAG, "MediaPipe BERT model loaded successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ML model: ${e.message}. Falling back to keyword analyzer.")
            isModelLoaded = false
        }
    }
    
    override suspend fun analyze(text: String): EmotionResult? = withContext(Dispatchers.Default) {
        val classifier = textClassifier ?: return@withContext null
        
        try {
            val result = classifier.classify(text)
            
            // 获取最高置信度的分类结果
            val topCategory = result.classificationResult()
                .classifications()
                .firstOrNull()
                ?.categories()
                ?.maxByOrNull { it.score() }
            
            if (topCategory != null) {
                val originalLabel = topCategory.categoryName().lowercase()
                val mappedEmotion = LABEL_MAPPING[originalLabel] ?: originalLabel
                
                Log.d(TAG, "ML classified: '$originalLabel' -> '$mappedEmotion' (score: ${topCategory.score()})")
                
                return@withContext EmotionResult(
                    emotion = mappedEmotion,
                    confidence = topCategory.score()
                )
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "ML inference failed: ${e.message}")
            null
        }
    }
    
    override fun isAvailable(): Boolean = isModelLoaded
    
    override fun close() {
        textClassifier?.close()
        textClassifier = null
        isModelLoaded = false
    }
}
