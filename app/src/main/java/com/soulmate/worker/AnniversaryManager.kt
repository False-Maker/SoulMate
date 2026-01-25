package com.soulmate.worker

import android.content.Context
import android.util.Log
import com.soulmate.data.model.AnniversaryEntity
import com.soulmate.data.model.AnniversaryEntity_
import com.soulmate.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.Box
import io.objectbox.BoxStore
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AnniversaryManager - 管理用户纪念日
 * 
 * 功能：
 * - 存储和读取重要纪念日（初次相遇日、生日等）
 * - 检查今天是否是某个纪念日
 * - 提供纪念日信息用于 LLM 生成个性化问候
 */
@Singleton
class AnniversaryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val boxStore: BoxStore,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    
    companion object {
        private const val TAG = "AnniversaryManager"
        
        // 预设的重要纪念日类型
        const val TYPE_FIRST_MEET = "first_meet"       // 初次相遇日
        const val TYPE_BIRTHDAY = "birthday"           // 用户生日
        const val TYPE_ANNIVERSARY = "anniversary"     // 周年纪念
        const val TYPE_CUSTOM = "custom"               // 自定义纪念日
    }
    
    private val anniversaryBox: Box<AnniversaryEntity> by lazy {
        boxStore.boxFor(AnniversaryEntity::class.java)
    }

    // init block removed to prevent hardcoded data initialization
    
    // initDefaultAnniversaries removed
    
    /**
     * 获取所有纪念日列表
     */
    fun getAllAnniversaries(): List<AnniversaryEntity> {
        return anniversaryBox.query().order(AnniversaryEntity_.month).build().find()
    }
    
    /**
     * 检查今天是否有纪念日
     * @return 今天匹配的纪念日列表
     */
    fun getTodayAnniversaries(): List<AnniversaryEntity> {
        val today = Calendar.getInstance()
        val currentMonth = today.get(Calendar.MONTH) + 1 // Calendar.MONTH 是 0-based
        val currentDay = today.get(Calendar.DAY_OF_MONTH)
        
        // ObjectBox query for month and day
        return anniversaryBox.query()
            .equal(AnniversaryEntity_.month, currentMonth.toLong())
            .equal(AnniversaryEntity_.day, currentDay.toLong())
            .build()
            .find()
            .also { matchedList ->
                if (matchedList.isNotEmpty()) {
                    Log.d(TAG, "Found ${matchedList.size} anniversaries today: ${matchedList.map { it.name }}")
                }
            }
    }
    
    /**
     * 检查是否是特定类型的纪念日
     */
    fun isTodayAnniversary(type: String): Boolean {
        return getTodayAnniversaries().any { it.type == type }
    }
    
    /**
     * 计算纪念日经过的年数
     */
    fun getYearsSince(anniversary: AnniversaryEntity): Int? {
        val year = anniversary.year ?: return null
        val today = Calendar.getInstance()
        val currentYear = today.get(Calendar.YEAR)
        return currentYear - year
    }
    
    /**
     * 生成纪念日提示词供 LLM 使用
     */
    fun getAnniversaryPromptContext(): String? {
        val todayAnniversaries = getTodayAnniversaries()
        
        if (todayAnniversaries.isEmpty()) {
            return null
        }
        
        val sb = StringBuilder("【重要提醒】今天是特别的日子：\n")
        
        todayAnniversaries.forEach { anniversary ->
            sb.append("- ${anniversary.name}")
            
            // 如果有年份，计算周年
            val years = getYearsSince(anniversary)
            if (years != null && years > 0) {
                sb.append("（${years}周年）")
            }
            
            // 添加自定义消息
            anniversary.message?.let {
                sb.append("\n  ${it}")
            }
            sb.append("\n")
        }
        
        sb.append("\n请在问候中自然地提及这个特别的日子，表达你对这段关系的珍视。")
        
        return sb.toString()
    }
    
    /**
     * 检查明天是否有纪念日（用于提前提醒）
     */
    fun getTomorrowAnniversaries(): List<AnniversaryEntity> {
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }
        val month = tomorrow.get(Calendar.MONTH) + 1
        val day = tomorrow.get(Calendar.DAY_OF_MONTH)
        
        return anniversaryBox.query()
            .equal(AnniversaryEntity_.month, month.toLong())
            .equal(AnniversaryEntity_.day, day.toLong())
            .build()
            .find()
    }
    
    /**
     * 添加自定义纪念日
     */
    fun addAnniversary(anniversary: AnniversaryEntity) {
        // Prevent duplicates based on type and name? Or just add.
        // Let's check if exists first to be safe, or just put.
        // Assuming update if ID exists, but here we might be creating new.
        // For simplicity, just add.
        if (anniversary.id == 0L) {
             // Check if same type/name/date exists to avoid duplicates?
             // Skipping for now, just put.
        }
        anniversaryBox.put(anniversary)
        Log.d(TAG, "Added anniversary: ${anniversary.name} on ${anniversary.month}/${anniversary.day}")
    }
    
    /**
     * 删除纪念日
     */
    fun removeAnniversary(id: Long) {
        anniversaryBox.remove(id)
        Log.d(TAG, "Removed anniversary with id: $id")
    }

    /**
     * 获取下一个即将到来的纪念日
     */
    fun getNextAnniversary(): Pair<AnniversaryEntity, Int>? {
        val all = getAllAnniversaries()
        if (all.isEmpty()) return null
        
        val today = Calendar.getInstance()
        val currentMonth = today.get(Calendar.MONTH) + 1
        val currentDay = today.get(Calendar.DAY_OF_MONTH)
        
        // Find the closest one
        // Simply sort by (month * 100 + day) ?
        // Need to handle wrap around year
        
        val sorted = all.map { entity ->
             val entityDate = entity.month * 100 + entity.day
             val todayDate = currentMonth * 100 + currentDay
             
             var daysUntil = 0
             // Rough calculation
             val thisYearDate = Calendar.getInstance().apply { 
                 set(Calendar.MONTH, entity.month - 1)
                 set(Calendar.DAY_OF_MONTH, entity.day)
             }
             
             if (thisYearDate.before(today)) {
                 thisYearDate.add(Calendar.YEAR, 1)
             }
             
             val diff = thisYearDate.timeInMillis - today.timeInMillis
             daysUntil = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff).toInt()
             
             entity to daysUntil
        }.sortedBy { it.second }
        
        return sorted.firstOrNull()
    }
}
