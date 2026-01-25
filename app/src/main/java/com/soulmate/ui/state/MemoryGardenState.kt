package com.soulmate.ui.state

import com.soulmate.core.data.memory.MemoryEntity
import com.soulmate.data.model.AnniversaryEntity
import java.time.LocalDate

/**
 * MemoryGardenState - 记忆花园屏幕状态
 * 
 * @param memoriesByDay 按日期分组的记忆列表，按日期降序排列
 * @param anniversaries 纪念日列表
 * @param isLoading 是否正在加载
 * @param editingMemory 当前编辑中的记忆（用于编辑对话框）
 * @param error 错误信息
 * @param isSearchMode 是否处于搜索模式
 * @param searchQuery 当前搜索词
 * @param isSearching 是否正在执行搜索
 */
data class MemoryGardenState(
    val memoriesByDay: Map<LocalDate, List<MemoryEntity>> = emptyMap(),
    val anniversaries: List<AnniversaryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val editingMemory: MemoryEntity? = null,
    val error: String? = null,
    val isSearchMode: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false
)
