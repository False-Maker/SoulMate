package com.soulmate

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
        
        // WorkManager is initialized automatically via Configuration.Provider
        val workManager = WorkManager.getInstance(this)
        
        // Schedule Heartbeat Worker
        scheduleHeartbeatWorker(workManager)
        
        // Note: Digital Human SDK is initialized in the UI layer (AvatarCoreService.bind)
        // because it requires a ViewGroup container.
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

