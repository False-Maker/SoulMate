package com.soulmate.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.soulmate.data.memory.IntimacyManager
import com.soulmate.data.model.PersonaConfig
import com.soulmate.data.preferences.UserPreferencesRepository
import com.soulmate.data.repository.AffinityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

/**
 * SettingsViewModel - 设置页面的ViewModel
 * 
 * 管理设置页面的状态，包括亲密度信息的读取和调试功能。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val intimacyManager: IntimacyManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val affinityRepository: AffinityRepository,
    private val mindWatchService: com.soulmate.data.service.MindWatchService,
    private val anniversaryManager: com.soulmate.worker.AnniversaryManager,
    private val memoryRepository: com.soulmate.core.data.memory.MemoryRepository,
    private val avatarCoreService: com.soulmate.data.service.AvatarCoreService
) : ViewModel() {
    
    /**
     * 当前亲密度分数的Flow
     */
    val currentScore: Flow<Int> = intimacyManager.currentScore

    /**
     * 当前好感度分数 Flow
     */
    val affinityScore: Flow<Int> = affinityRepository.affinityScore

    /**
     * 当前好感度等级 Flow
     */
    val affinityLevel: Flow<AffinityRepository.AffinityLevel> = affinityRepository.affinityLevel
    
    /**
     * MindWatch Status
     */
    val mindWatchStatus = mindWatchService.currentStatus
    


    /**
     * 下一个纪念日
     */
    val nextAnniversary = kotlinx.coroutines.flow.flow {
        emit(anniversaryManager.getNextAnniversary())
    }
    
    /**
     * 人设配置 StateFlow
     */
    private val _personaConfig = MutableStateFlow(userPreferencesRepository.getPersonaConfig())
    val personaConfig: Flow<PersonaConfig> = _personaConfig.asStateFlow()
    
    /**
     * 用户性别 Flow（用于数字人性别选择）
     */
    val userGender: Flow<com.soulmate.data.model.UserGender> = userPreferencesRepository.userGenderFlow
    
    /**
     * 记忆保留天数 Flow（0 表示永久保留）
     */
    val memoryRetentionDays: Flow<Long> = userPreferencesRepository.memoryRetentionDaysFlow
    
    // Memory Count (Total memories stored)
    val memoryCount: Flow<Long> = kotlinx.coroutines.flow.flow {
        // This should ideally be a reactive query, but simple count is fine for now
        emit(memoryRepository.getMemoryCount()) 
    }

    /**
     * 性格滑杆 Flow（0..100）
     */
    val personaWarmth: Flow<Int> = userPreferencesRepository.personaWarmthFlow
    
    /**
     * 获取当前等级名称
     */
    fun getCurrentLevelName(): String {
        return intimacyManager.getCurrentLevelName()
    }
    
    /**
     * 更新人设配置
     */
    fun updatePersonaConfig(config: PersonaConfig) {
        userPreferencesRepository.setPersonaConfig(config)
        _personaConfig.value = config
    }
    
    /**
     * 更新用户性别
     */
    fun updateUserGender(gender: com.soulmate.data.model.UserGender) {
        userPreferencesRepository.setUserGender(gender)
    }
    
    /**
     * 更新记忆保留天数
     * @param days 保留天数，0 表示永久保留
     */
    fun updateMemoryRetentionDays(days: Long) {
        userPreferencesRepository.setMemoryRetentionDays(days)
    }

    /**
     * 更新性格滑杆
     */
    fun updatePersonaWarmth(value: Int) {
        userPreferencesRepository.setPersonaWarmth(value)
    }
    
    /**
     * 调试功能：设置亲密度分数（用于演示）
     * 
     * @param score 目标分数
     */
    fun setCheatScore(score: Int) {
        intimacyManager.setScore(score)
    }
    
    /**
     * 重置亲密度分数
     */
    fun resetScore() {
        intimacyManager.resetScore()
    }
    
    /**
     * 清除数字人缓存
     * 
     * 用于解决数字人更新后（如从全身改为半身）缓存未更新的问题。
     * 清除缓存后，下次加载数字人时会重新从服务器下载最新资源。
     * 
     * @return true 如果清除成功，false 如果清除失败
     */
    fun clearAvatarCache(): Boolean {
        return avatarCoreService.clearAvatarCache()
    }

    /**
     * 重置用户记忆（清除所有并重置为初始状态）
     */
    fun resetMemoryForUser(onSuccess: () -> Unit, onError: (String) -> Unit) {
        // Calculate "last year today"
        // Current: 2026-01-25 -> Last year: 2025-01-25
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.YEAR, -1)
        val dateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.CHINA)
        val dateStr = dateFormat.format(calendar.time)
        val memoryText = "第一次见面时间是$dateStr"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                memoryRepository.clearAll()
                // Removed automatic insertion of default first meeting memory
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "重置失败")
                }
            }
        }
    }
}
