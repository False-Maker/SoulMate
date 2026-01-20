package com.soulmate.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.soulmate.data.model.UserGender
import com.soulmate.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * OnboardingViewModel - 首次启动引导的 ViewModel
 * 
 * 负责：
 * - 保存用户性别选择
 * - 检查是否已完成引导
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    
    /**
     * 设置用户性别
     */
    fun setUserGender(gender: UserGender) {
        userPreferencesRepository.setUserGender(gender)
    }
    
    /**
     * 检查是否已完成引导
     */
    fun isOnboardingCompleted(): Boolean {
        return userPreferencesRepository.isOnboardingCompleted()
    }
}
