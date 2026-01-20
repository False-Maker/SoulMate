package com.soulmate.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContextManager provides device context information for the Heartbeat Protocol.
 * 
 * This enables environment-aware AI responses by providing:
 * - Battery level and charging status
 * 
 * Future extensions could include:
 * - Location context (home/work/commuting)
 * - Time zone and local time
 * - Network connectivity status
 * - Screen on/off state
 */
@Singleton
class ContextManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Gets the current battery level as a percentage (0-100).
     * 
     * @return Battery level percentage, or -1 if unable to determine
     */
    fun getBatteryLevel(): Int {
        val batteryStatus = getBatteryStatusIntent() ?: return -1
        
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            -1
        }
    }
    
    /**
     * Checks if the device is currently charging.
     * 
     * @return true if charging (AC or USB), false otherwise
     */
    fun isCharging(): Boolean {
        val batteryStatus = getBatteryStatusIntent() ?: return false
        
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }
    
    /**
     * Gets a human-readable battery status text suitable for LLM prompts.
     * 
     * Examples:
     * - "User has 10% battery (low, not charging)"
     * - "User has 85% battery (charging)"
     * - "User has 100% battery (fully charged)"
     * 
     * @return A descriptive string about battery status
     */
    fun getBatteryStatusText(): String {
        val level = getBatteryLevel()
        val charging = isCharging()
        
        if (level < 0) {
            return "Unable to determine battery status."
        }
        
        val statusDescription = when {
            level == 100 && charging -> "fully charged"
            level >= 80 && charging -> "charging, almost full"
            charging -> "charging"
            level <= 15 -> "low, not charging"
            level <= 30 -> "moderate, not charging"
            else -> "good"
        }
        
        return "User has $level% battery ($statusDescription)."
    }
    
    /**
     * Gets the battery status broadcast intent.
     * Uses a sticky broadcast to get the current battery state.
     */
    private fun getBatteryStatusIntent(): Intent? {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return context.registerReceiver(null, intentFilter)
    }
}
