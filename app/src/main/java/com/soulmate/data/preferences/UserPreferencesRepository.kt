package com.soulmate.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UserPreferencesRepository manages user preferences and activity tracking.
 * 
 * This repository stores:
 * - Last active time (when user last interacted with the app)
 * - Last heartbeat time (when last proactive message was sent)
 * - User preferences (name, settings, etc.)
 * 
 * In production, consider using DataStore instead of SharedPreferences for better
 * type safety and coroutine support.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val PREFS_NAME = "soulmate_preferences"
        private const val KEY_LAST_ACTIVE_TIME = "last_active_time"
        private const val KEY_LAST_HEARTBEAT_TIME = "last_heartbeat_time"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PERSONA_CONFIG = "persona_config" // Added key for PersonaConfig
        private const val KEY_USER_GENDER = "user_gender" // Added key for UserGender
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson() // Initialize Gson
    
    private val _lastActiveTime = MutableStateFlow(prefs.getLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis()))
    val lastActiveTime: Flow<Long> = _lastActiveTime.asStateFlow()
    
    /**
     * Updates the last active time to the current timestamp.
     */
    fun updateLastActiveTime() {
        val currentTime = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, currentTime).apply()
        _lastActiveTime.value = currentTime
    }
    
    /**
     * Updates the last heartbeat time.
     */
    fun updateLastHeartbeatTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_HEARTBEAT_TIME, timestamp).apply()
    }
    
    /**
     * Gets the last heartbeat time.
     */
    fun getLastHeartbeatTime(): Long {
        return prefs.getLong(KEY_LAST_HEARTBEAT_TIME, 0L)
    }
    
    /**
     * Gets the user's name.
     */
    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "Lucian") ?: "Lucian"
    }
    
    /**
     * Sets the user's name.
     */
    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    /**
     * Gets the current PersonaConfig.
     */
    fun getPersonaConfig(): com.soulmate.data.model.PersonaConfig {
        val json = prefs.getString(KEY_PERSONA_CONFIG, null)
        return if (json != null) {
            try {
                gson.fromJson(json, com.soulmate.data.model.PersonaConfig::class.java)
            } catch (e: Exception) {
                com.soulmate.data.model.PersonaConfig() // Fallback to default
            }
        } else {
            com.soulmate.data.model.PersonaConfig() // Default
        }
    }

    /**
     * Sets the PersonaConfig.
     */
    fun setPersonaConfig(config: com.soulmate.data.model.PersonaConfig) {
        val json = gson.toJson(config)
        prefs.edit().putString(KEY_PERSONA_CONFIG, json).apply()
        
        // Also sync the legacy user name for backward compatibility
        setUserName(config.userName)
    }

    /**
     * Gets the user's gender for avatar selection.
     */
    fun getUserGender(): com.soulmate.data.model.UserGender {
        val value = prefs.getString(KEY_USER_GENDER, null)
        return com.soulmate.data.model.UserGender.entries.find { it.name == value } 
            ?: com.soulmate.data.model.UserGender.UNSET
    }

    /**
     * Sets the user's gender.
     */
    fun setUserGender(gender: com.soulmate.data.model.UserGender) {
        prefs.edit().putString(KEY_USER_GENDER, gender.name).apply()
    }

    /**
     * Checks if onboarding (gender selection) is completed.
     */
    fun isOnboardingCompleted(): Boolean = getUserGender() != com.soulmate.data.model.UserGender.UNSET
}

