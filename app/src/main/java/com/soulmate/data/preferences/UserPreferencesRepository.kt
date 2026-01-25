package com.soulmate.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        private const val KEY_PERSONA_WARMTH = "persona_warmth" // 0..100
        
        // 记忆保留策略
        private const val KEY_MEMORY_RETENTION_DAYS = "memory_retention_days"
        const val DEFAULT_MEMORY_RETENTION_DAYS = 0L  // 0 表示永久保留
        
        // RAG 配置项
        private const val KEY_RAG_HISTORY_LIMIT = "rag_history_limit"
        private const val KEY_RAG_TOP_K_CANDIDATES = "rag_top_k_candidates"
        private const val KEY_RAG_MAX_ITEMS = "rag_max_items"
        private const val KEY_RAG_MIN_SIMILARITY = "rag_min_similarity"
        private const val KEY_RAG_HALF_LIFE_DAYS = "rag_half_life_days"
        private const val KEY_RAG_EXCLUDE_ROUNDS = "rag_exclude_rounds"
        private const val KEY_RAG_INCLUDE_AI_OUTPUT = "rag_include_ai_output"
        private const val KEY_RAG_LOG_VERBOSE = "rag_log_verbose"
        
        // RAG 默认值
        const val DEFAULT_RAG_HISTORY_LIMIT = 16
        const val DEFAULT_RAG_TOP_K_CANDIDATES = 20
        const val DEFAULT_RAG_MAX_ITEMS = 5
        const val DEFAULT_RAG_MIN_SIMILARITY = 0.30f
        const val DEFAULT_RAG_HALF_LIFE_DAYS = 14.0f
        const val DEFAULT_RAG_EXCLUDE_ROUNDS = 4
        const val DEFAULT_RAG_INCLUDE_AI_OUTPUT = false
        const val DEFAULT_RAG_LOG_VERBOSE = false

        // Persona warmth 默认值
        const val DEFAULT_PERSONA_WARMTH = 50
        
        // Vision detail 配置
        private const val KEY_VISION_DETAIL = "vision_detail"
        const val DEFAULT_VISION_DETAIL = "low"  // low/high/auto
        
        // 免提模式
        private const val KEY_HANDS_FREE_MODE = "hands_free_mode"
        const val DEFAULT_HANDS_FREE_MODE = false
        
        // 性能优化开关（默认全部为 false，保持原有行为）
        private const val KEY_ENABLE_CONCURRENT_RAG = "enable_concurrent_rag"
        private const val KEY_ENABLE_FAST_RAG_PATH = "enable_fast_rag_path"
        private const val KEY_ENABLE_FAST_THINKING = "enable_fast_thinking"
        private const val KEY_ENABLE_FAST_AVATAR_INIT = "enable_fast_avatar_init"
        private const val KEY_ENABLE_FAST_REBIND = "enable_fast_rebind"
        private const val KEY_USE_OPTIMIZED_TIMEOUTS = "use_optimized_timeouts"
        private const val KEY_ENABLE_OPTIMIZED_STREAMING = "enable_optimized_streaming"
        private const val KEY_ENABLE_OPTIMIZED_RAG = "enable_optimized_rag"
        
        // 性能优化默认值（全部为 true，默认启用优化，失败时自动降级）
        const val DEFAULT_ENABLE_CONCURRENT_RAG = true
        const val DEFAULT_ENABLE_FAST_RAG_PATH = true
        const val DEFAULT_ENABLE_FAST_THINKING = true
        const val DEFAULT_ENABLE_FAST_AVATAR_INIT = true
        const val DEFAULT_ENABLE_FAST_REBIND = true
        const val DEFAULT_USE_OPTIMIZED_TIMEOUTS = false  // 超时优化保持关闭，避免影响稳定性
        const val DEFAULT_ENABLE_OPTIMIZED_STREAMING = false  // 流式优化保持关闭，需要更多测试
        const val DEFAULT_ENABLE_OPTIMIZED_RAG = false  // RAG 优化保持关闭，需要更多测试
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson() // Initialize Gson
    
    private val _lastActiveTime = MutableStateFlow(prefs.getLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis()))
    val lastActiveTime: Flow<Long> = _lastActiveTime.asStateFlow()
    
    // 用户性别可观察 StateFlow（避免只读一次导致设置更改后不生效）
    // 声明为 StateFlow 以便 ViewModel 直接暴露，初始值来自 prefs
    private val _userGender = MutableStateFlow(getUserGenderFromPrefs())
    val userGenderFlow: kotlinx.coroutines.flow.StateFlow<com.soulmate.data.model.UserGender> = _userGender.asStateFlow()
    
    // 记忆保留天数 Flow（0 表示永久保留）
    private val _memoryRetentionDays = MutableStateFlow(prefs.getLong(KEY_MEMORY_RETENTION_DAYS, DEFAULT_MEMORY_RETENTION_DAYS))
    val memoryRetentionDaysFlow: Flow<Long> = _memoryRetentionDays.asStateFlow()

    // 性格滑杆 Flow（0..100）
    private val _personaWarmth = MutableStateFlow(prefs.getInt(KEY_PERSONA_WARMTH, DEFAULT_PERSONA_WARMTH))
    val personaWarmthFlow: Flow<Int> = _personaWarmth.asStateFlow()
    
    // Vision detail Flow（low/high/auto）
    private val _visionDetail = MutableStateFlow(prefs.getString(KEY_VISION_DETAIL, DEFAULT_VISION_DETAIL) ?: DEFAULT_VISION_DETAIL)
    val visionDetailFlow: kotlinx.coroutines.flow.StateFlow<String> = _visionDetail.asStateFlow()
    
    /**
     * 从 SharedPreferences 读取用户性别（内部方法）
     */
    private fun getUserGenderFromPrefs(): com.soulmate.data.model.UserGender {
        val value = prefs.getString(KEY_USER_GENDER, null)
        return com.soulmate.data.model.UserGender.entries.find { it.name == value } 
            ?: com.soulmate.data.model.UserGender.UNSET
    }
    
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
    fun getUserGender(): com.soulmate.data.model.UserGender = getUserGenderFromPrefs()

    /**
     * Sets the user's gender.
     * 同时更新 SharedPreferences 和 StateFlow，确保订阅方能立即拿到最新值。
     */
    fun setUserGender(gender: com.soulmate.data.model.UserGender) {
        prefs.edit().putString(KEY_USER_GENDER, gender.name).apply()
        _userGender.value = gender
    }

    /**
     * 获取性格滑杆值（0..100）
     */
    fun getPersonaWarmth(): Int = prefs.getInt(KEY_PERSONA_WARMTH, DEFAULT_PERSONA_WARMTH)

    /**
     * 设置性格滑杆值（0..100）
     */
    fun setPersonaWarmth(value: Int) {
        val normalized = value.coerceIn(0, 100)
        prefs.edit().putInt(KEY_PERSONA_WARMTH, normalized).apply()
        _personaWarmth.value = normalized
    }
    
    /**
     * 获取 Vision detail 设置（low/high/auto）
     * 用于控制图片/视频理解的精度与成本
     */
    fun getVisionDetail(): String = prefs.getString(KEY_VISION_DETAIL, DEFAULT_VISION_DETAIL) ?: DEFAULT_VISION_DETAIL
    
    /**
     * 设置 Vision detail（low/high/auto）
     */
    fun setVisionDetail(detail: String) {
        val normalized = when (detail.lowercase()) {
            "low", "high", "auto" -> detail.lowercase()
            else -> DEFAULT_VISION_DETAIL
        }
        prefs.edit().putString(KEY_VISION_DETAIL, normalized).apply()
        _visionDetail.value = normalized
    }
    
    // ========== 免提模式 ==========
    
    private val _handsFreeMode = MutableStateFlow(
        prefs.getBoolean(KEY_HANDS_FREE_MODE, DEFAULT_HANDS_FREE_MODE)
    )
    val handsFreeMode: StateFlow<Boolean> = _handsFreeMode.asStateFlow()
    
    /**
     * 获取免提模式状态
     */
    fun isHandsFreeMode(): Boolean = prefs.getBoolean(KEY_HANDS_FREE_MODE, DEFAULT_HANDS_FREE_MODE)
    
    /**
     * 设置免提模式
     * 开启后，用户说完一句会自动发送并重新开始识别
     */
    fun setHandsFreeMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HANDS_FREE_MODE, enabled).apply()
        _handsFreeMode.value = enabled
    }

    /**
     * Checks if onboarding (gender selection) is completed.
     */
    fun isOnboardingCompleted(): Boolean = getUserGender() != com.soulmate.data.model.UserGender.UNSET
    
    // ========== 记忆保留策略 ==========
    
    /**
     * 获取记忆保留天数
     * @return 保留天数，0 表示永久保留
     */
    fun getMemoryRetentionDays(): Long = prefs.getLong(KEY_MEMORY_RETENTION_DAYS, DEFAULT_MEMORY_RETENTION_DAYS)
    
    /**
     * 设置记忆保留天数
     * @param days 保留天数，0 表示永久保留
     */
    fun setMemoryRetentionDays(days: Long) {
        prefs.edit().putLong(KEY_MEMORY_RETENTION_DAYS, days).apply()
        _memoryRetentionDays.value = days
    }
    
    // ========== RAG 配置 ==========
    
    /**
     * 获取 RAG 历史消息数量限制
     */
    fun getRagHistoryLimit(): Int = prefs.getInt(KEY_RAG_HISTORY_LIMIT, DEFAULT_RAG_HISTORY_LIMIT)
    
    /**
     * 设置 RAG 历史消息数量限制
     */
    fun setRagHistoryLimit(limit: Int) {
        prefs.edit().putInt(KEY_RAG_HISTORY_LIMIT, limit).apply()
    }
    
    /**
     * 获取 RAG 初始检索候选数量
     */
    fun getRagTopKCandidates(): Int = prefs.getInt(KEY_RAG_TOP_K_CANDIDATES, DEFAULT_RAG_TOP_K_CANDIDATES)
    
    /**
     * 设置 RAG 初始检索候选数量
     */
    fun setRagTopKCandidates(topK: Int) {
        prefs.edit().putInt(KEY_RAG_TOP_K_CANDIDATES, topK).apply()
    }
    
    /**
     * 获取 RAG 最终上下文条数
     */
    fun getRagMaxItems(): Int = prefs.getInt(KEY_RAG_MAX_ITEMS, DEFAULT_RAG_MAX_ITEMS)
    
    /**
     * 设置 RAG 最终上下文条数
     */
    fun setRagMaxItems(maxItems: Int) {
        prefs.edit().putInt(KEY_RAG_MAX_ITEMS, maxItems).apply()
    }
    
    /**
     * 获取 RAG 最低相似度阈值
     */
    fun getRagMinSimilarity(): Float = prefs.getFloat(KEY_RAG_MIN_SIMILARITY, DEFAULT_RAG_MIN_SIMILARITY)
    
    /**
     * 设置 RAG 最低相似度阈值
     */
    fun setRagMinSimilarity(minSimilarity: Float) {
        prefs.edit().putFloat(KEY_RAG_MIN_SIMILARITY, minSimilarity).apply()
    }
    
    /**
     * 获取 RAG 时间衰减半衰期（天）
     */
    fun getRagHalfLifeDays(): Float = prefs.getFloat(KEY_RAG_HALF_LIFE_DAYS, DEFAULT_RAG_HALF_LIFE_DAYS)
    
    /**
     * 设置 RAG 时间衰减半衰期（天）
     */
    fun setRagHalfLifeDays(halfLifeDays: Float) {
        prefs.edit().putFloat(KEY_RAG_HALF_LIFE_DAYS, halfLifeDays).apply()
    }
    
    /**
     * 获取 RAG 排除最近 N 轮数
     */
    fun getRagExcludeRounds(): Int = prefs.getInt(KEY_RAG_EXCLUDE_ROUNDS, DEFAULT_RAG_EXCLUDE_ROUNDS)
    
    /**
     * 设置 RAG 排除最近 N 轮数
     */
    fun setRagExcludeRounds(excludeRounds: Int) {
        prefs.edit().putInt(KEY_RAG_EXCLUDE_ROUNDS, excludeRounds).apply()
    }
    
    /**
     * 获取是否在 RAG 中包含 AI 输出
     */
    fun getRagIncludeAiOutput(): Boolean = prefs.getBoolean(KEY_RAG_INCLUDE_AI_OUTPUT, DEFAULT_RAG_INCLUDE_AI_OUTPUT)
    
    /**
     * 设置是否在 RAG 中包含 AI 输出
     */
    fun setRagIncludeAiOutput(includeAiOutput: Boolean) {
        prefs.edit().putBoolean(KEY_RAG_INCLUDE_AI_OUTPUT, includeAiOutput).apply()
    }
    
    /**
     * 获取 RAG 是否输出详细日志
     */
    fun getRagLogVerbose(): Boolean = prefs.getBoolean(KEY_RAG_LOG_VERBOSE, DEFAULT_RAG_LOG_VERBOSE)
    
    /**
     * 设置 RAG 是否输出详细日志
     */
    fun setRagLogVerbose(logVerbose: Boolean) {
        prefs.edit().putBoolean(KEY_RAG_LOG_VERBOSE, logVerbose).apply()
    }
    
    /**
     * 获取完整的 RAG 配置
     */
    fun getRagConfig(): RagConfig = RagConfig(
        historyLimit = getRagHistoryLimit(),
        topKCandidates = getRagTopKCandidates(),
        maxItems = getRagMaxItems(),
        minSimilarity = getRagMinSimilarity(),
        halfLifeDays = getRagHalfLifeDays(),
        excludeRounds = getRagExcludeRounds(),
        includeAiOutput = getRagIncludeAiOutput(),
        logVerbose = getRagLogVerbose()
    )
    
    // ========== 性能优化开关 ==========
    
    /**
     * 是否启用并发 RAG（RAG 和 history 获取并行执行）
     */
    fun isConcurrentRagEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_CONCURRENT_RAG, DEFAULT_ENABLE_CONCURRENT_RAG)
    
    /**
     * 设置是否启用并发 RAG
     */
    fun setConcurrentRagEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_CONCURRENT_RAG, enabled).apply()
    }
    
    /**
     * 是否启用快速 RAG 路径（跳过无记忆场景的 Embedding API 调用）
     */
    fun isFastRagPathEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_FAST_RAG_PATH, DEFAULT_ENABLE_FAST_RAG_PATH)
    
    /**
     * 设置是否启用快速 RAG 路径
     */
    fun setFastRagPathEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_FAST_RAG_PATH, enabled).apply()
    }
    
    /**
     * 是否启用快速思考（移除思考延迟模拟）
     */
    fun isFastThinkingEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_FAST_THINKING, DEFAULT_ENABLE_FAST_THINKING)
    
    /**
     * 设置是否启用快速思考
     */
    fun setFastThinkingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_FAST_THINKING, enabled).apply()
    }
    
    /**
     * 是否启用快速数字人初始化
     */
    fun isFastAvatarInitEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_FAST_AVATAR_INIT, DEFAULT_ENABLE_FAST_AVATAR_INIT)
    
    /**
     * 设置是否启用快速数字人初始化
     */
    fun setFastAvatarInitEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_FAST_AVATAR_INIT, enabled).apply()
    }
    
    /**
     * 是否启用快速重绑（减少 debounce 延迟）
     */
    fun isFastRebindEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_FAST_REBIND, DEFAULT_ENABLE_FAST_REBIND)
    
    /**
     * 设置是否启用快速重绑
     */
    fun setFastRebindEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_FAST_REBIND, enabled).apply()
    }
    
    /**
     * 是否使用优化超时配置
     */
    fun isOptimizedTimeoutsEnabled(): Boolean = prefs.getBoolean(KEY_USE_OPTIMIZED_TIMEOUTS, DEFAULT_USE_OPTIMIZED_TIMEOUTS)
    
    /**
     * 设置是否使用优化超时配置
     */
    fun setOptimizedTimeoutsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_OPTIMIZED_TIMEOUTS, enabled).apply()
    }
    
    /**
     * 是否启用优化流式解析
     */
    fun isOptimizedStreamingEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_OPTIMIZED_STREAMING, DEFAULT_ENABLE_OPTIMIZED_STREAMING)
    
    /**
     * 设置是否启用优化流式解析
     */
    fun setOptimizedStreamingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_OPTIMIZED_STREAMING, enabled).apply()
    }
    
    /**
     * 是否启用优化 RAG（减少候选数量等）
     */
    fun isOptimizedRagEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_OPTIMIZED_RAG, DEFAULT_ENABLE_OPTIMIZED_RAG)
    
    /**
     * 设置是否启用优化 RAG
     */
    fun setOptimizedRagEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_OPTIMIZED_RAG, enabled).apply()
    }
}

/**
 * RAG 配置数据类
 */
data class RagConfig(
    val historyLimit: Int = UserPreferencesRepository.DEFAULT_RAG_HISTORY_LIMIT,
    val topKCandidates: Int = UserPreferencesRepository.DEFAULT_RAG_TOP_K_CANDIDATES,
    val maxItems: Int = UserPreferencesRepository.DEFAULT_RAG_MAX_ITEMS,
    val minSimilarity: Float = UserPreferencesRepository.DEFAULT_RAG_MIN_SIMILARITY,
    val halfLifeDays: Float = UserPreferencesRepository.DEFAULT_RAG_HALF_LIFE_DAYS,
    val excludeRounds: Int = UserPreferencesRepository.DEFAULT_RAG_EXCLUDE_ROUNDS,
    val includeAiOutput: Boolean = UserPreferencesRepository.DEFAULT_RAG_INCLUDE_AI_OUTPUT,
    val logVerbose: Boolean = UserPreferencesRepository.DEFAULT_RAG_LOG_VERBOSE
)

