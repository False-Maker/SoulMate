package com.soulmate.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soulmate.core.data.brain.EmbeddingService
import com.soulmate.core.data.memory.MemoryEntity
import com.soulmate.core.data.memory.MemoryRepository
import com.soulmate.data.model.AnniversaryEntity
import com.soulmate.ui.components.MemoryNode
import com.soulmate.ui.components.MemoryType
import com.soulmate.ui.state.MemoryGardenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.abs

/**
 * MemoryGardenViewModel - 记忆花园视图模型
 * 
 * 管理记忆花园屏幕的状态和业务逻辑
 */
@HiltViewModel
class MemoryGardenViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val anniversaryManager: com.soulmate.worker.AnniversaryManager,
    private val embeddingService: EmbeddingService
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryGardenState())
    val state: StateFlow<MemoryGardenState> = _state.asStateFlow()

    private val _memoryNodes = MutableStateFlow<List<MemoryNode>>(emptyList())
    val memoryNodes: StateFlow<List<MemoryNode>> = _memoryNodes.asStateFlow()
    
    // 缓存全部记忆，用于搜索后恢复
    private var allMemoryNodes: List<MemoryNode> = emptyList()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        .withZone(ZoneId.systemDefault())

    init {
        loadMemories()
    }

    private fun loadMemories() {
        viewModelScope.launch {
            // Load anniversaries
            try {
                val anniversaries = anniversaryManager.getAllAnniversaries()
                _state.value = _state.value.copy(anniversaries = anniversaries)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            memoryRepository.getMemoriesByDate()
                .onStart { 
                    _state.value = _state.value.copy(isLoading = true) 
                }
                .catch { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载记忆失败"
                    )
                }
                .collect { memoriesByDay ->
                    _state.value = _state.value.copy(
                        memoriesByDay = memoriesByDay,
                        isLoading = false,
                        error = null
                    )
                    
                    // Generate constellations with anniversaries merged
                    val allMemories = memoriesByDay.values.flatten()
                    val nodes = generateConstellationCoordinates(allMemories, _state.value.anniversaries)
                    allMemoryNodes = nodes
                    _memoryNodes.value = nodes
                }
        }
    }

    /**
     * 生成星图坐标
     * 
     * 融合普通记忆和纪念日，使用确定性算法生成稳定坐标
     * - Y轴：基于时间映射（最旧在上，最新在下）
     * - X轴：基于内容哈希的确定性位置（相同记忆永远在同一位置）
     */
    /**
     * 生成星图坐标 - 星河流动 (Star River)
     * 
     * 布局逻辑：
     * 1. 过滤：隐藏琐碎的 user_input/ai_output，保留 CORE/MANUAL/ANNIVERSARY
     * 2. 排序：按时间倒序（最新在最前面/最下面？）。通常河流是从上往下流，上游是过去(Old)，下游是现在(New)？
     *    或者：星空通常是"深远"。
     *    现有逻辑是 Old(0.1) -> New(0.9). 让我们保持 Time Ascending (Top=Old, Bottom=New).
     * 3. 坐标：
     *    - Y轴：基于索引的线性分布（保证不拥挤），每页只展示固定数量的星星。
     *    - X轴：正弦波 (Sine Wave) + 随机扰动，形成"S"型流动。
     */
    private fun generateConstellationCoordinates(
        entities: List<MemoryEntity>,
        anniversaries: List<AnniversaryEntity>
    ): List<MemoryNode> {
        // 1. 预处理数据（转换 & 过滤）
        val anniversaryNodes = anniversaries.map { anniversary ->
            val timestamp = anniversaryToTimestamp(anniversary)
            UnifiedNode(
                id = -(anniversary.id.toInt() + 1000),
                timestamp = timestamp,
                title = anniversary.name,
                content = anniversary.message ?: "纪念日：${anniversary.name}",
                type = MemoryType.CORE // 纪念日总是核心
            )
        }

        val memoryNodes = entities.map { entity ->
            UnifiedNode(
                id = entity.id.toInt(),
                timestamp = entity.timestamp,
                title = generateTitle(entity.text),
                content = entity.text ?: "",
                type = determineMemoryType(entity),
                tag = entity.tag
            )
        }

        // 2. 过滤与排序
        var allNodes = (memoryNodes + anniversaryNodes)
            // 过滤策略：
            // - 显示 MANUAL (手动)
            // - 显示 CORE (高情绪/重要)
            // - 显示 SUMMARY (总结) (假设有)
            // - 隐藏 普通的 user_input / ai_output / normal (除非它是 manual)
            .filter { node ->
                when {
                    node.id < 0 -> true // 纪念日总是显示
                    node.type == MemoryType.CORE -> true
                    node.type == MemoryType.NEW -> true // Manual is typed as NEW in determineMemoryType? Check logic.
                    // 检查 tag
                    node.tag == "manual" -> true
                    // 过滤掉琐碎对话
                    node.tag == "user_input" || node.tag == "ai_output" -> false
                    // 默认其他 normal 即使不含 tag 也可能是手动存的旧数据？
                    // 稳妥起见，Strict filter: 只过滤明确知道是琐碎的 tags
                    else -> true
                }
            }
            .sortedBy { it.timestamp } // Old -> New (Top -> Bottom)

        if (allNodes.isEmpty()) return emptyList()

        // 3. 生成坐标 (Sine Wave Layout)
        // 参数配置
        val density = 0.15f // 节点垂直间距 (相对于屏幕高度) -> 这里需要改为绝对单位或逻辑单位
        // 为了支持 Scroll，我们不再限制在 0.1-0.9。
        // 我们定义：Y = index * density. 
        // 在 UI 层，我们将 Y * ScreenHeight 作为实际像素位置。
        
        return allNodes.mapIndexed { index, node ->
            // Y 轴：线性分布，避免拥挤
            // 头部留白 0.2
            val y = 0.2f + (index * density)

            // X 轴：正弦波 + 随机扰动
            // Frequency: 决定S型的弯曲程度。0.5 PI * index 
            val wavePhase = y * 3.0 // 随 Y 变化的相位
            val baseX = 0.5f + (kotlin.math.sin(wavePhase) * 0.35f) // Amplitude 0.35 (范围 0.15 - 0.85)

            // 添加微小的随机扰动，让它看起来不那么像数学公式
            // 确定性随机：基于 ID
            val randomOffset = deterministicRandom(node.id.toLong()) * 0.1f - 0.05f // +/- 0.05
            val x = (baseX + randomOffset).toFloat().coerceIn(0.1f, 0.9f)

            MemoryNode(
                id = node.id,
                date = dateFormatter.format(Instant.ofEpochMilli(node.timestamp)),
                title = node.title,
                content = node.content,
                type = node.type,
                xPercent = x,
                yPercent = y // 注意：这里 y 可能 > 1.0，UI 需要处理滚动
            )
        }
    }

    /**
     * 确定性随机数生成 (-1.0 ~ 1.0)
     */
    private fun deterministicRandom(seed: Long): Float {
        val x = kotlin.math.sin(seed.toDouble()) * 10000.0
        return (x - kotlin.math.floor(x)).toFloat() * 2 - 1
    }
    
    /**
     * 将纪念日实体转换为时间戳
     * 
     * 如果年份为空，使用当前年份
     */
    private fun anniversaryToTimestamp(anniversary: AnniversaryEntity): Long {
        val calendar = Calendar.getInstance()
        val year = anniversary.year ?: calendar.get(Calendar.YEAR)
        calendar.set(year, anniversary.month - 1, anniversary.day, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    /**
     * 从记忆文本生成标题
     * 
     * 提取前30个字符，如果文本更长则添加省略号
     */
    private fun generateTitle(text: String?): String {
        if (text.isNullOrBlank()) return "无标题"
        val trimmed = text.trim()
        return if (trimmed.length <= 30) {
            trimmed
        } else {
            trimmed.take(30) + "..."
        }
    }
    
    /**
     * 根据记忆实体确定记忆类型
     * 
     * 判断逻辑：
     * - NEW: 手动添加的记忆（tag == "manual"）
     * - CORE: 高情绪记忆或重要标签（emotionLabel 为强烈情绪，或 tag 为 summary）
     * - NORMAL: 其他普通记忆
     */
    private fun determineMemoryType(entity: MemoryEntity): MemoryType {
        val tag = entity.getEffectiveTag()
        
        // 手动记忆标记为 NEW
        if (tag == "manual") {
            return MemoryType.NEW
        }
        
        // 高情绪或总结标记为 CORE
        val emotionLabel = entity.emotionLabel?.lowercase()
        val strongEmotions = setOf("excited", "happy", "sad", "angry", "love", "surprised", "fear")
        if (emotionLabel in strongEmotions || tag == "summary") {
            return MemoryType.CORE
        }
        
        // 其他为普通记忆
        return MemoryType.NORMAL
    }

    // 内部统一节点类
    private data class UnifiedNode(
        val id: Int,
        val timestamp: Long,
        val title: String,
        val content: String,
        val type: MemoryType,
        val tag: String? = null // Added tag for filtering
    )

    // ==================== 搜索功能 ====================

    /**
     * 进入搜索模式
     */
    fun enterSearchMode() {
        _state.value = _state.value.copy(isSearchMode = true)
    }

    /**
     * 退出搜索模式，恢复全部记忆显示
     */
    fun exitSearchMode() {
        _state.value = _state.value.copy(
            isSearchMode = false,
            searchQuery = "",
            isSearching = false
        )
        _memoryNodes.value = allMemoryNodes
    }

    /**
     * 执行混合搜索
     * 
     * 同时执行两种搜索策略并合并结果：
     * 1. 普通记忆：语义搜索（向量检索）
     * 2. 纪念日：关键词匹配
     */
    fun searchMemories(query: String) {
        if (query.isBlank()) {
            exitSearchMode()
            return
        }

        _state.value = _state.value.copy(
            searchQuery = query,
            isSearching = true
        )

        viewModelScope.launch {
            try {
                // ========== 1. 普通记忆语义搜索 ==========
                val queryEmbedding = embeddingService.embed(query)
                val memorySearchResults = memoryRepository.searchWithTags(
                    queryEmbedding = queryEmbedding,
                    limit = 20
                )

                // ========== 2. 纪念日关键词搜索 ==========
                val matchedAnniversaries = searchAnniversaries(query)

                // ========== 3. 合并结果生成星图节点 ==========
                val searchNodes = generateConstellationCoordinates(
                    entities = memorySearchResults,
                    anniversaries = matchedAnniversaries
                )

                _memoryNodes.value = searchNodes
                _state.value = _state.value.copy(isSearching = false)

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSearching = false,
                    error = "搜索失败：${e.message}"
                )
            }
        }
    }

    /**
     * 在纪念日列表中执行关键词搜索
     * 
     * 匹配规则：纪念日名称或消息中包含查询词（忽略大小写）
     */
    private fun searchAnniversaries(query: String): List<AnniversaryEntity> {
        val lowerQuery = query.lowercase()
        return _state.value.anniversaries.filter { anniversary ->
            anniversary.name.lowercase().contains(lowerQuery) ||
            (anniversary.message?.lowercase()?.contains(lowerQuery) == true)
        }
    }

    /**
     * 更新搜索查询词（用于实时更新输入框状态）
     */
    fun updateSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    /**
     * 添加新记忆（手动记忆）
     * 
     * @param text 记忆内容
     * @param emotionLabel 情绪标签（可选，默认 "neutral"）
     */
    fun addMemory(text: String, emotionLabel: String = "neutral") {
        viewModelScope.launch {
            try {
                // 使用 manual tag 标记手动添加的记忆
                memoryRepository.saveWithTag(
                    text = text,
                    tag = "manual",
                    sessionId = null,
                    emotionLabel = emotionLabel
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message ?: "添加记忆失败"
                )
            }
        }
    }

    /**
     * 开始编辑记忆
     */
    fun startEditing(memory: MemoryEntity) {
        _state.value = _state.value.copy(editingMemory = memory)
    }

    /**
     * 取消编辑
     */
    fun cancelEditing() {
        _state.value = _state.value.copy(editingMemory = null)
    }

    /**
     * 更新记忆
     */
    fun updateMemory(id: Long, text: String, emotion: String) {
        viewModelScope.launch {
            try {
                memoryRepository.update(id, text, emotion)
                _state.value = _state.value.copy(editingMemory = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message ?: "更新记忆失败"
                )
            }
        }
    }

    /**
     * 删除记忆 - 立即传播到 ObjectBox
     */
    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            try {
                memoryRepository.delete(id)
                _state.value = _state.value.copy(editingMemory = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message ?: "删除记忆失败"
                )
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
