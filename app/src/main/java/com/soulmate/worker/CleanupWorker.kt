package com.soulmate.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.soulmate.core.data.memory.MemoryRepository
import com.soulmate.data.preferences.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * CleanupWorker - 自动数据清理 Worker
 * 
 * 定期清理过期的历史数据，防止数据库无限增长占满用户存储。
 * 
 * 清理策略：
 * - 执行周期：每日一次
 * - 保留期限：默认保留最近 90 天的记忆
 * - 保护机制：手动创建的记忆（tag=manual）不会被清理
 * 
 * 使用方式：
 * 在 Application.onCreate() 中调用 CleanupWorker.schedule(context) 启动定期任务。
 */
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val memoryRepository: MemoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "CleanupWorker"
        const val WORK_NAME = "memory_cleanup_work"
        
        // 清理配置
        private const val RETENTION_DAYS = 90L  // 保留最近 90 天的数据
        private const val REPEAT_INTERVAL_HOURS = 24L  // 每 24 小时执行一次
        
        // 受保护的 tag（不会被自动清理）
        private val PROTECTED_TAGS = setOf("manual", "summary")
        
        /**
         * 调度定期清理任务
         * 
         * 在 Application 启动时调用此方法注册 Worker。
         * 使用 KEEP 策略，避免重复注册。
         * 
         * @param context Application Context
         */
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
                REPEAT_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .addTag(WORK_NAME)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // 如果已存在则保留，不重复创建
                workRequest
            )
            
            Log.d(TAG, "Cleanup worker scheduled: every $REPEAT_INTERVAL_HOURS hours, retain $RETENTION_DAYS days")
        }
        
        /**
         * 取消定期清理任务
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cleanup worker cancelled")
        }
    }
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting memory cleanup...")
            
            // 从用户配置读取保留天数
            val retentionDays = userPreferencesRepository.getMemoryRetentionDays()
            
            // 永久模式：retentionDays <= 0 时不删除任何记忆
            if (retentionDays <= 0) {
                Log.d(TAG, "Memory retention is set to permanent, skipping cleanup")
                return Result.success()
            }
            
            val cutoffTimestamp = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000)
            
            // 执行清理
            val deletedCount = cleanupOldMemories(cutoffTimestamp)
            
            Log.d(TAG, "Cleanup completed: deleted $deletedCount old memories (older than $retentionDays days)")
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
            Result.failure()
        }
    }
    
    /**
     * 清理过期记忆
     * 
     * @param cutoffTimestamp 截止时间戳，早于此时间的记忆将被清理
     * @return 删除的记忆数量
     */
    private suspend fun cleanupOldMemories(cutoffTimestamp: Long): Int {
        val allMemories = memoryRepository.getAll()
        var deletedCount = 0
        
        allMemories.forEach { memory ->
            // 跳过受保护的记忆类型
            val effectiveTag = memory.getEffectiveTag()
            if (effectiveTag in PROTECTED_TAGS) {
                return@forEach
            }
            
            // 检查是否过期
            if (memory.timestamp < cutoffTimestamp) {
                memoryRepository.delete(memory.id)
                deletedCount++
            }
        }
        
        return deletedCount
    }
}

