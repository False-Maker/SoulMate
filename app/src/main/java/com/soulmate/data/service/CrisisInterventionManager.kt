package com.soulmate.data.service

import android.content.Context
import android.util.Log
import com.soulmate.worker.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CrisisInterventionManager - 危机干预管理器
 * 
 * 功能：
 * 1. 危机事件记录和追踪
 * 2. 触发紧急通知给监护人
 * 3. 提供危机干预指导
 * 4. 管理紧急联系人
 */
@Singleton
class CrisisInterventionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationHelper: NotificationHelper
) {
    
    companion object {
        private const val TAG = "CrisisIntervention"
        
        // 危机级别
        const val LEVEL_LOW = 1      // 轻度 - 记录并观察
        const val LEVEL_MEDIUM = 2   // 中度 - 通知监护人
        const val LEVEL_HIGH = 3     // 高度 - 立即通知 + 提供资源
        const val LEVEL_CRITICAL = 4 // 危急 - 建议寻求专业帮助
    }
    
    /**
     * 危机事件
     */
    data class CrisisEvent(
        val id: String,
        val timestamp: Long,
        val level: Int,
        val triggerText: String,
        val keywords: List<String>,
        val handled: Boolean = false,
        val notes: String? = null
    )
    
    /**
     * 紧急联系人
     */
    data class EmergencyContact(
        val name: String,
        val phone: String,
        val relationship: String,
        val isPrimary: Boolean = false
    )
    
    /**
     * 危机资源
     */
    data class CrisisResource(
        val name: String,
        val type: String,  // "hotline", "website", "app"
        val contact: String,
        val description: String
    )
    
    // 危机事件历史
    private val crisisEvents = mutableListOf<CrisisEvent>()
    
    // 紧急联系人列表
    private val emergencyContacts = mutableListOf<EmergencyContact>()
    
    // 当前危机状态
    private val _isInCrisis = MutableStateFlow(false)
    val isInCrisis: StateFlow<Boolean> = _isInCrisis.asStateFlow()
    
    // 危机资源列表
    private val defaultResources = listOf(
        CrisisResource(
            name = "全国心理援助热线",
            type = "hotline",
            contact = "400-161-9995",
            description = "24小时免费心理援助热线"
        ),
        CrisisResource(
            name = "北京心理危机研究与干预中心",
            type = "hotline",
            contact = "010-82951332",
            description = "24小时心理危机干预热线"
        ),
        CrisisResource(
            name = "生命热线",
            type = "hotline",
            contact = "400-821-1215",
            description = "全天候心理支持服务"
        ),
        CrisisResource(
            name = "希望24热线",
            type = "hotline",
            contact = "400-161-9995",
            description = "24小时青少年心理援助"
        )
    )
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    /**
     * 处理危机事件
     */
    fun handleCrisis(
        triggerText: String,
        keywords: List<String>,
        level: Int = LEVEL_MEDIUM
    ) {
        val event = CrisisEvent(
            id = "crisis_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            level = level,
            triggerText = triggerText.take(200), // 限制长度
            keywords = keywords
        )
        
        crisisEvents.add(event)
        _isInCrisis.value = true
        
        Log.w(TAG, "Crisis event recorded: level=$level, keywords=$keywords")
        
        // 根据级别采取行动
        when (level) {
            LEVEL_LOW -> {
                // 仅记录
                Log.d(TAG, "Low-level concern recorded")
            }
            LEVEL_MEDIUM -> {
                // 通知监护人
                notifyGuardian(event)
            }
            LEVEL_HIGH -> {
                // 立即通知 + 显示资源
                notifyGuardian(event)
                showCrisisResources()
            }
            LEVEL_CRITICAL -> {
                // 紧急通知 + 资源 + 建议专业帮助
                notifyGuardian(event)
                showCrisisResources()
                suggestProfessionalHelp()
            }
        }
    }
    
    /**
     * 通知监护人
     */
    private fun notifyGuardian(event: CrisisEvent) {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val time = dateFormat.format(Date(event.timestamp))
        
        val levelText = when (event.level) {
            LEVEL_LOW -> "轻度关注"
            LEVEL_MEDIUM -> "需要关注"
            LEVEL_HIGH -> "高度警惕"
            LEVEL_CRITICAL -> "紧急"
            else -> "未知"
        }
        
        val message = "【MindWatch $levelText】$time 检测到需要关注的信号。" +
                "关键词: ${event.keywords.take(3).joinToString(", ")}。" +
                "建议尽快与 TA 沟通。"
        
        scope.launch {
            notificationHelper.sendProactiveNotification(message)
        }
        
        Log.d(TAG, "Guardian notified: $message")
    }
    
    /**
     * 显示危机资源
     */
    private fun showCrisisResources() {
        // 可以通过 UI 事件通知 UI 层显示资源
        Log.d(TAG, "Crisis resources should be displayed to user")
    }
    
    /**
     * 建议寻求专业帮助
     */
    private fun suggestProfessionalHelp() {
        val message = "检测到严重的情绪困扰信号。强烈建议寻求专业心理咨询帮助。" +
                "您可以拨打心理援助热线 400-161-9995（24小时免费）"
        
        scope.launch {
            notificationHelper.sendProactiveNotification(message)
        }
        
        Log.w(TAG, "Professional help suggested")
    }
    
    /**
     * 获取危机资源列表
     */
    fun getCrisisResources(): List<CrisisResource> {
        return defaultResources
    }
    
    /**
     * 添加紧急联系人
     */
    fun addEmergencyContact(contact: EmergencyContact) {
        emergencyContacts.add(contact)
        Log.d(TAG, "Emergency contact added: ${contact.name}")
    }
    
    /**
     * 获取紧急联系人列表
     */
    fun getEmergencyContacts(): List<EmergencyContact> {
        return emergencyContacts.toList()
    }
    
    /**
     * 获取危机事件历史
     */
    fun getCrisisHistory(): List<CrisisEvent> {
        return crisisEvents.toList()
    }
    
    /**
     * 标记危机事件为已处理
     */
    fun markEventAsHandled(eventId: String, notes: String? = null) {
        val index = crisisEvents.indexOfFirst { it.id == eventId }
        if (index >= 0) {
            crisisEvents[index] = crisisEvents[index].copy(
                handled = true,
                notes = notes
            )
            Log.d(TAG, "Event $eventId marked as handled")
        }
        
        // 检查是否还有未处理的事件
        if (crisisEvents.none { !it.handled && it.level >= LEVEL_MEDIUM }) {
            _isInCrisis.value = false
        }
    }
    
    /**
     * 重置危机状态
     */
    fun resetCrisisState() {
        _isInCrisis.value = false
        Log.d(TAG, "Crisis state reset")
    }
    
    /**
     * 清除所有记录
     */
    fun clearAllRecords() {
        crisisEvents.clear()
        _isInCrisis.value = false
        Log.d(TAG, "All crisis records cleared")
    }
}
