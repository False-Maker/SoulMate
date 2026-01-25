package com.soulmate.core.data.memory

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * MemoryRepository - 记忆存储接口
 * 
 * 提供记忆的 CRUD 和向量检索能力
 */
interface MemoryRepository {
    
    /**
     * 保存记忆（旧接口，保持兼容）
     * 
     * @param text 记忆文本
     * @param emotion 情感/角色标签（旧用法：user/ai）
     * @return 记忆ID
     */
    suspend fun save(text: String, emotion: String): Long
    
    /**
     * 保存记忆（增强接口）
     * 
     * @param text 记忆文本
     * @param tag 记忆类型标签：user_input / ai_output / manual / summary
     * @param sessionId 关联会话ID（可选）
     * @param emotionLabel 真实情绪标签（可选）
     * @return 记忆ID
     */
    suspend fun saveWithTag(
        text: String,
        tag: String,
        sessionId: Long? = null,
        emotionLabel: String? = null
    ): Long
    
    /**
     * 批量保存记忆（用于长文本切片存储）
     * 
     * @param chunks 切片后的文本列表
     * @param tag 记忆类型标签
     * @param sessionId 关联会话ID（可选）
     * @param emotionLabel 真实情绪标签（可选）
     * @param sharedTimestamp 共享时间戳（所有 chunk 使用相同时间戳，便于关联）
     * @return 保存的记忆ID列表
     */
    suspend fun saveChunks(
        chunks: List<String>,
        tag: String,
        sessionId: Long? = null,
        emotionLabel: String? = null,
        sharedTimestamp: Long = System.currentTimeMillis()
    ): List<Long>
    
    /**
     * 删除记忆
     */
    suspend fun delete(id: Long)

    /**
     * 清除所有记忆
     */
    suspend fun clearAll()
    
    /**
     * 获取所有记忆
     */
    suspend fun getAll(): List<MemoryEntity>
    
    /**
     * 向量检索记忆（旧接口，保持兼容）
     * 
     * @param queryEmbedding 查询向量
     * @param limit 返回数量限制
     * @return 相似记忆列表
     */
    suspend fun search(queryEmbedding: FloatArray, limit: Int): List<MemoryEntity>
    
    /**
     * 向量检索记忆（增强接口，支持 tag 过滤）
     * 
     * @param queryEmbedding 查询向量
     * @param limit 返回数量限制
     * @param allowedTags 允许的 tag 集合（为空则不过滤）
     * @return 相似记忆列表
     */
    suspend fun searchWithTags(
        queryEmbedding: FloatArray,
        limit: Int,
        allowedTags: Set<String> = emptySet()
    ): List<MemoryEntity>
    
    /**
     * 按日期分组获取记忆（响应式）
     */
    fun getMemoriesByDate(): Flow<Map<LocalDate, List<MemoryEntity>>>
    
    /**
     * 更新记忆
     * 
     * @param id 记忆ID
     * @param text 新文本
     * @param emotion 新情感标签
     */
    suspend fun update(id: Long, text: String, emotion: String)
    
    /**
     * 更新记忆（增强接口）
     * 
     * @param id 记忆ID
     * @param text 新文本
     * @param tag 新类型标签
     * @param emotionLabel 新情绪标签
     */
    suspend fun updateWithTag(
        id: Long,
        text: String,
        tag: String,
        emotionLabel: String? = null
    )
    
    /**
     * 获取最近记忆
     * 
     * @param limit 返回数量限制
     * @return 记忆列表，按时间倒序
     */
    suspend fun getRecentMemories(limit: Int): List<MemoryEntity>
    
    /**
     * 获取记忆总数
     */
    suspend fun getMemoryCount(): Long
    
    /**
     * 快速检查是否有任何记忆（用于性能优化）
     * 
     * 用于快速路径：如果没有任何记忆，可以跳过 Embedding API 调用
     * 这是一个轻量级查询，比 getMemoryCount() > 0 更高效
     * 
     * @return true 如果至少有一条记忆，false 如果没有记忆
     */
    suspend fun hasAnyMemory(): Boolean
    /**
     * 检查并清除维度不匹配的记忆
     * 用于处理模型切换（如 1536 -> 2048 维）导致的向量数据不兼容问题
     */
    suspend fun validateAndClearIfDimensionMismatch(expectedDim: Int) {
        // 默认空实现
    }
}
