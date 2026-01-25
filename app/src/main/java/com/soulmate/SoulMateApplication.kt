package com.soulmate

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.soulmate.worker.CleanupWorker
import com.soulmate.worker.HeartbeatWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application class for SoulMate.
 * 
 * This class:
 * - Initializes Hilt for dependency injection
 * - Sets up the Heartbeat Worker for periodic proactive messages
 * - Sets up the Cleanup Worker for automatic data cleanup
 * - Handles app-level initialization
 * - Provides HiltWorkerFactory for @HiltWorker support
 */
@HiltAndroidApp
class SoulMateApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    override fun onCreate() {
        super.onCreate()
        
        // 验证关键配置
        validateConfiguration()
        
        // WorkManager is initialized automatically via Configuration.Provider
        val workManager = WorkManager.getInstance(this)
        
        // Schedule Heartbeat Worker
        scheduleHeartbeatWorker(workManager)
        
        // Schedule Cleanup Worker for automatic data cleanup
        CleanupWorker.schedule(this)
        
        // Note: Digital Human SDK is initialized in the UI layer (AvatarCoreService.bind)
        // because it requires a ViewGroup container.
    }
    
    /**
     * 验证应用配置，检查关键 API Key 和端点配置
     */
    private fun validateConfiguration() {
        val tag = "SoulMateApp"
        
        // 检查 Embedding 配置
        val embeddingApiKey = try {
            val field = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_API_KEY")
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
        
        val doubaoApiKey = BuildConfig.DOUBAO_API_KEY
        
        val embeddingEndpointId = try {
            val field = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_ENDPOINT_ID")
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
        
        val embeddingModelId = try {
            val field = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_MODEL_ID")
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
        
        // 验证 API Key
        val hasApiKey = embeddingApiKey.isNotEmpty() || doubaoApiKey.isNotEmpty()
        if (!hasApiKey) {
            Log.e(tag, "⚠️ 配置错误: Embedding API Key 未配置!")
            Log.e(tag, "   请在 local.properties 中设置以下之一:")
            Log.e(tag, "   - DOUBAO_EMBEDDING_API_KEY")
            Log.e(tag, "   - DOUBAO_API_KEY (作为回退)")
        } else {
            val usedKey = if (embeddingApiKey.isNotEmpty()) "DOUBAO_EMBEDDING_API_KEY" else "DOUBAO_API_KEY"
            Log.d(tag, "✓ Embedding API Key 已配置 (使用: $usedKey)")
        }
        
        // 验证端点/模型 ID
        val hasEndpointOrModel = embeddingEndpointId.isNotEmpty() || embeddingModelId.isNotEmpty()
        if (!hasEndpointOrModel) {
            Log.w(tag, "⚠️ 配置警告: Embedding 端点/模型 ID 未配置，将使用默认值")
            Log.w(tag, "   建议在 local.properties 中设置:")
            Log.w(tag, "   - DOUBAO_EMBEDDING_ENDPOINT_ID (推荐，如 ep-xxx)")
            Log.w(tag, "   - DOUBAO_EMBEDDING_MODEL_ID (备选)")
        } else {
            val usedConfig = if (embeddingEndpointId.isNotEmpty()) {
                "DOUBAO_EMBEDDING_ENDPOINT_ID=$embeddingEndpointId"
            } else {
                "DOUBAO_EMBEDDING_MODEL_ID=$embeddingModelId"
            }
            Log.d(tag, "✓ Embedding 端点/模型已配置: $usedConfig")
        }
        
        // 检查其他关键配置
        if (BuildConfig.DOUBAO_API_KEY.isEmpty()) {
            Log.w(tag, "⚠️ DOUBAO_API_KEY 未配置，可能影响 LLM 功能")
        }
        
        if (BuildConfig.DOUBAO_CHAT_ENDPOINT_ID.isEmpty()) {
            Log.w(tag, "⚠️ DOUBAO_CHAT_ENDPOINT_ID 未配置，可能影响对话功能")
        }
    }
    
    /**
     * Schedules the Heartbeat Worker to run periodically.
     * The worker will check user activity and send proactive messages when appropriate.
     */
    private fun scheduleHeartbeatWorker(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Can work offline
            .setRequiresBatteryNotLow(false)
            .build()
        
        val heartbeatWork = PeriodicWorkRequestBuilder<HeartbeatWorker>(
            15, TimeUnit.MINUTES // Run every 15 minutes
        )
            .setConstraints(constraints)
            .addTag(HeartbeatWorker.WORK_TAG)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            HeartbeatWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing work if already scheduled
            heartbeatWork
        )
    }
}

