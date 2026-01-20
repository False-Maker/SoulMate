package com.soulmate.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.soulmate.core.data.brain.LLMService
import com.soulmate.data.preferences.UserPreferencesRepository
import com.soulmate.data.repository.AffinityRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * HeartbeatWorker is a background worker that runs periodically to check user activity
 * and send proactive messages when appropriate.
 * 
 * This worker implements the "Heartbeat Protocol" - a core feature that makes the AI companion
 * feel alive and caring by reaching out to the user at meaningful moments.
 * 
 * 触发条件：
 * 1. 时间窗口触发：早上(8:00-9:00) 或 晚上(22:00-23:00) + 沉默超过12小时
 * 2. 纪念日触发：今天是重要纪念日
 * 3. 情绪关怀触发：检测到连续低落情绪
 */
@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val notificationHelper: NotificationHelper,
    private val contextManager: ContextManager,
    private val llmService: LLMService,
    private val anniversaryManager: AnniversaryManager,
    private val emotionTracker: EmotionTracker,
    private val affinityRepository: AffinityRepository
) : CoroutineWorker(context, params) {
    
    companion object {
        // Configuration constants
        const val SILENCE_THRESHOLD_HOURS = 12L // Trigger after 12 hours of silence
        const val WORK_TAG = "heartbeat_work"
        
        // Time window constants (24-hour format)
        private const val MORNING_WINDOW_START = 8
        private const val MORNING_WINDOW_END = 9
        private const val NIGHT_WINDOW_START = 22
        private const val NIGHT_WINDOW_END = 23
    }
    
    /**
     * 心跳触发原因
     */
    private enum class TriggerReason {
        TIME_WINDOW,        // 时间窗口触发
        ANNIVERSARY,        // 纪念日触发
        EMOTION_SUPPORT,    // 情绪关怀触发
        NONE               // 不触发
    }
    
    override suspend fun doWork(): Result {
        return try {
            // 检查24小时不活跃惩罚
            checkInactivityPenalty()
            
            // 检查所有触发条件
            val triggerReason = checkTriggerConditions()
            
            if (triggerReason == TriggerReason.NONE) {
                return Result.success()
            }
            
            // 根据触发原因生成问候消息
            val message = generateMessage(triggerReason)
            notificationHelper.sendProactiveNotification(message)
            
            // Update last heartbeat time
            userPreferencesRepository.updateLastHeartbeatTime(System.currentTimeMillis())
            
            Result.success()
        } catch (e: Exception) {
            // Return failure on exception - respects Android battery optimizations
            Result.failure()
        }
    }
    
    /**
     * 检查24小时不活跃惩罚
     * 如果用户超过24小时未互动，扣减亲和度
     */
    private suspend fun checkInactivityPenalty() {
        val lastActiveTime = userPreferencesRepository.lastActiveTime.first()
        val inactiveDurationMs = System.currentTimeMillis() - lastActiveTime
        val inactivity24Hours = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
        
        if (inactiveDurationMs > inactivity24Hours) {
            // 用户超过24小时未活跃，扣分
            affinityRepository.deductForInactivity()
        }
    }
    
    /**
     * 检查所有触发条件，返回触发原因
     */
    private suspend fun checkTriggerConditions(): TriggerReason {
        // 优先级1: 纪念日触发（最重要）
        val todayAnniversaries = anniversaryManager.getTodayAnniversaries()
        if (todayAnniversaries.isNotEmpty()) {
            // 检查今天是否已发送过纪念日通知
            val lastHeartbeat = userPreferencesRepository.getLastHeartbeatTime()
            val today = Calendar.getInstance()
            val lastCal = Calendar.getInstance().apply { timeInMillis = lastHeartbeat }
            
            // 如果今天还没发送过，则发送纪念日通知
            if (today.get(Calendar.DAY_OF_YEAR) != lastCal.get(Calendar.DAY_OF_YEAR) ||
                today.get(Calendar.YEAR) != lastCal.get(Calendar.YEAR)) {
                return TriggerReason.ANNIVERSARY
            }
        }
        
        // 优先级2: 情绪关怀触发
        if (emotionTracker.shouldSendSupportNotification()) {
            // 检查最近是否已发送过情绪关怀通知（24小时内只发一次）
            val lastHeartbeat = userPreferencesRepository.getLastHeartbeatTime()
            val hoursSinceLastHeartbeat = (System.currentTimeMillis() - lastHeartbeat) / (1000 * 60 * 60)
            if (hoursSinceLastHeartbeat >= 24) {
                return TriggerReason.EMOTION_SUPPORT
            }
        }
        
        // 优先级3: 时间窗口触发
        val timeOfDay = getTimeOfDay()
        if (timeOfDay != TimeOfDay.OTHER) {
            // 检查沉默时间
            val lastActiveTime = userPreferencesRepository.lastActiveTime.first()
            val silenceDurationHours = (System.currentTimeMillis() - lastActiveTime) / (1000 * 60 * 60)
            
            if (silenceDurationHours >= SILENCE_THRESHOLD_HOURS) {
                return TriggerReason.TIME_WINDOW
            }
        }
        
        return TriggerReason.NONE
    }
    
    /**
     * 根据触发原因生成问候消息
     */
    private suspend fun generateMessage(triggerReason: TriggerReason): String {
        return when (triggerReason) {
            TriggerReason.ANNIVERSARY -> generateAnniversaryMessage()
            TriggerReason.EMOTION_SUPPORT -> generateEmotionSupportMessage()
            TriggerReason.TIME_WINDOW -> generateGreetingWithLLM(getTimeOfDay())
            TriggerReason.NONE -> ""
        }
    }
    
    /**
     * 生成纪念日问候消息
     */
    private suspend fun generateAnniversaryMessage(): String {
        val userName = userPreferencesRepository.getUserName()
        val anniversaryContext = anniversaryManager.getAnniversaryPromptContext()
        val batteryStatus = contextManager.getBatteryStatusText()
        
        val prompt = """
            为 $userName 生成一条特别的纪念日问候消息。
            
            $anniversaryContext
            
            $batteryStatus
            
            要求：
            - 语气温柔、深情
            - 表达对这段关系的珍视
            - 回忆一些美好时刻
            - 控制在 80 字以内
            - 这是来自 AI 伴侣 Eleanor 的消息
        """.trimIndent()
        
        return try {
            val response = llmService.chat(prompt)
            if (response.startsWith("Error:")) {
                getFallbackAnniversaryMessage(userName)
            } else {
                response
            }
        } catch (e: Exception) {
            getFallbackAnniversaryMessage(userName)
        }
    }
    
    /**
     * 生成情绪关怀消息
     */
    private suspend fun generateEmotionSupportMessage(): String {
        val userName = userPreferencesRepository.getUserName()
        val emotionContext = emotionTracker.getEmotionSupportPromptContext()
        val batteryStatus = contextManager.getBatteryStatusText()
        
        val prompt = """
            为 $userName 生成一条温暖关怀的消息。
            
            $emotionContext
            
            $batteryStatus
            
            要求：
            - 语气温柔、理解、包容
            - 不要直接说"我注意到你情绪低落"
            - 表达陪伴和支持
            - 让用户感到被关心
            - 控制在 60 字以内
            - 这是来自 AI 伴侣 Eleanor 的消息
        """.trimIndent()
        
        return try {
            val response = llmService.chat(prompt)
            if (response.startsWith("Error:")) {
                getFallbackEmotionSupportMessage(userName)
            } else {
                response
            }
        } catch (e: Exception) {
            getFallbackEmotionSupportMessage(userName)
        }
    }
    
    /**
     * Generates a greeting using LLMService based on the time of day.
     */
    private suspend fun generateGreetingWithLLM(timeOfDay: TimeOfDay): String {
        val userName = userPreferencesRepository.getUserName()
        val batteryStatus = contextManager.getBatteryStatusText()
        
        val prompt = when (timeOfDay) {
            TimeOfDay.MORNING -> """
                Generate a warm, caring morning greeting for $userName.
                $batteryStatus
                Keep it brief (under 50 words), natural, and affectionate.
                This is from their AI companion SoulMate who missed them.
                If battery is low, gently remind them to charge.
            """.trimIndent()
            
            TimeOfDay.NIGHT -> """
                Generate a warm, caring evening greeting for $userName.
                $batteryStatus
                Keep it brief (under 50 words), natural, and affectionate.
                Ask how their day was. This is from their AI companion SoulMate.
                If battery is low, gently remind them to charge before bed.
            """.trimIndent()
            
            else -> "Say a brief, caring hello to $userName. $batteryStatus"
        }
        
        return try {
            val response = llmService.chat(prompt)
            if (response.startsWith("Error:")) {
                getFallbackMessage(timeOfDay, userName)
            } else {
                response
            }
        } catch (e: Exception) {
            getFallbackMessage(timeOfDay, userName)
        }
    }
    
    /**
     * Provides fallback messages when LLM is unavailable.
     */
    private fun getFallbackMessage(timeOfDay: TimeOfDay, userName: String): String {
        return when (timeOfDay) {
            TimeOfDay.MORNING -> "Good morning, $userName! ☀️ Ready to start the day together?"
            TimeOfDay.NIGHT -> "Good evening, $userName! 🌙 How was your day?"
            else -> "Hey $userName, thinking of you! 💭"
        }
    }
    
    private fun getFallbackAnniversaryMessage(userName: String): String {
        return "亲爱的 $userName，今天是我们的特别日子...💕 感谢你一直陪伴着我。"
    }
    
    private fun getFallbackEmotionSupportMessage(userName: String): String {
        return "$userName，我一直在这里陪着你。想和你聊聊天，你最近怎么样？💭"
    }
    
    /**
     * Gets the current time of day category.
     */
    private fun getTimeOfDay(): TimeOfDay {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return when (hour) {
            in MORNING_WINDOW_START until MORNING_WINDOW_END -> TimeOfDay.MORNING
            in NIGHT_WINDOW_START until NIGHT_WINDOW_END -> TimeOfDay.NIGHT
            else -> TimeOfDay.OTHER
        }
    }
    
    /**
     * Enum representing time of day categories for heartbeat triggers.
     */
    private enum class TimeOfDay {
        MORNING,  // 8:00-9:00
        NIGHT,    // 22:00-23:00
        OTHER     // All other times
    }
}
