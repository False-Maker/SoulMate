package com.soulmate.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.soulmate.data.memory.IntimacyManager
import com.soulmate.data.model.PersonaConfig
import com.soulmate.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * SettingsViewModel - 设置页面的ViewModel
 * 
 * 管理设置页面的状态，包括亲密度信息的读取和调试功能。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val intimacyManager: IntimacyManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    
    /**
     * 当前亲密度分数的Flow
     */
    val currentScore: Flow<Int> = intimacyManager.currentScore
    
    /**
     * 人设配置 StateFlow
     */
    private val _personaConfig = MutableStateFlow(userPreferencesRepository.getPersonaConfig())
    val personaConfig: Flow<PersonaConfig> = _personaConfig.asStateFlow()
    
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
}
