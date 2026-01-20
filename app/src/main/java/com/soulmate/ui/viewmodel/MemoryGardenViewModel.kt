package com.soulmate.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soulmate.core.data.memory.MemoryEntity
import com.soulmate.core.data.memory.MemoryRepository
import com.soulmate.ui.state.MemoryGardenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MemoryGardenViewModel - 记忆花园视图模型
 * 
 * 管理记忆花园屏幕的状态和业务逻辑
 */
@HiltViewModel
class MemoryGardenViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val anniversaryManager: com.soulmate.worker.AnniversaryManager
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryGardenState())
    val state: StateFlow<MemoryGardenState> = _state.asStateFlow()

    private val _memoryNodes = MutableStateFlow<List<com.soulmate.ui.components.MemoryNode>>(emptyList())
    val memoryNodes: StateFlow<List<com.soulmate.ui.components.MemoryNode>> = _memoryNodes.asStateFlow()

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
                    
                    // Generate constellations
                    val allMemories = memoriesByDay.values.flatten()
                    _memoryNodes.value = generateConstellationCoordinates(allMemories)
                }
        }
    }

    private fun generateConstellationCoordinates(entities: List<MemoryEntity>): List<com.soulmate.ui.components.MemoryNode> {
        if (entities.isEmpty()) return emptyList()
        
        val sorted = entities.sortedBy { it.timestamp }
        val minTime = sorted.first().timestamp
        val maxTime = sorted.last().timestamp
        val timeSpan = (maxTime - minTime).coerceAtLeast(1)
        
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd")
            .withZone(java.time.ZoneId.systemDefault())

        return sorted.mapIndexed { index, entity ->
            // Y mapped to time (oldest at top 0.1, newest at bottom 0.9)
            // Or maybe reverse? Let's stick to time flow top->down
            val timeProgress = if (timeSpan > 0) (entity.timestamp - minTime).toFloat() / timeSpan else 0.5f
            val y = 0.1f + (timeProgress * 0.8f) 
            
            // X mapped to ID hash (pseudo-random but stable)
            val random = kotlin.random.Random(entity.id)
            val x = 0.1f + (random.nextFloat() * 0.8f)

            // Type mapping
            val type = when {
                // Anniversary logic could be here if we linked it, but for now rely on emotion
                entity.emotion?.lowercase() in listOf("love", "excited", "爱", "兴奋", "happy", "开心") -> com.soulmate.ui.components.MemoryType.CORE
                else -> com.soulmate.ui.components.MemoryType.NORMAL
            }
            
            // Title logic - truncate text or generic
            val titleText = if ((entity.text?.length ?: 0) > 10) "Memory #$index" else "Small Moment"

            com.soulmate.ui.components.MemoryNode(
                id = entity.id.toInt(),
                date = dateFormatter.format(java.time.Instant.ofEpochMilli(entity.timestamp)),
                title = titleText, // Placeholder title logic
                content = entity.text ?: "",
                type = type,
                xPercent = x,
                yPercent = y
            )
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
