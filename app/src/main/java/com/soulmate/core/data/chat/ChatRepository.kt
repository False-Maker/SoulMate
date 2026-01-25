package com.soulmate.core.data.chat

import kotlinx.coroutines.flow.Flow

/**
 * ChatRepository - 会话与消息存储接口
 * 
 * 提供会话持久化和消息管理能力，支持重启后恢复对话历史
 */
interface ChatRepository {
    
    /**
     * 获取或创建当前活动会话
     * 
     * 逻辑：
     * - 如果存在未归档的会话，返回最近更新的那个
     * - 否则创建新会话
     * 
     * @return 活动会话ID
     */
    suspend fun getOrCreateActiveSession(): Long
    
    /**
     * 创建新会话
     * 
     * @param title 会话标题（可选）
     * @return 新会话ID
     */
    suspend fun createSession(title: String? = null): Long
    
    /**
     * 获取会话信息
     * 
     * @param sessionId 会话ID
     * @return 会话实体，不存在则返回 null
     */
    suspend fun getSession(sessionId: Long): ChatSessionEntity?
    
    /**
     * 观察会话消息列表（响应式）
     * 
     * @param sessionId 会话ID
     * @param limit 返回消息数量限制
     * @return 消息列表 Flow
     */
    fun observeMessages(sessionId: Long, limit: Int = 50): Flow<List<ChatMessageEntity>>
    
    /**
     * 获取最近消息（一次性查询）
     * 
     * @param sessionId 会话ID
     * @param limit 返回消息数量限制
     * @return 消息列表，按时间戳升序排列
     */
    suspend fun getRecentMessages(sessionId: Long, limit: Int): List<ChatMessageEntity>
    
    /**
     * 追加消息到会话
     * 
     * @param sessionId 会话ID
     * @param role 消息角色（user / assistant / system）
     * @param content 干净文本内容
     * @param rawContent 原始内容（可选，含标签）
     * @param imageUrl 图片 URL（可选，用于 ImageGen 消息）
     * @param localImageUri 本地图片 URI（可选，用于用户发送的本地图片）
     * @return 新消息ID
     */
    suspend fun appendMessage(
        sessionId: Long,
        role: String,
        content: String,
        rawContent: String? = null,
        imageUrl: String? = null,
        localImageUri: String? = null,
        localVideoUri: String? = null
    ): Long
    
    /**
     * 更新会话时间戳
     * 
     * @param sessionId 会话ID
     */
    suspend fun updateSessionTimestamp(sessionId: Long)
    
    /**
     * 更新会话标题
     * 
     * @param sessionId 会话ID
     * @param title 新标题
     */
    suspend fun updateSessionTitle(sessionId: Long, title: String)
    
    /**
     * 归档会话
     * 
     * @param sessionId 会话ID
     */
    suspend fun archiveSession(sessionId: Long)
    
    /**
     * 获取所有会话列表（按更新时间倒序）
     * 
     * @param includeArchived 是否包含已归档会话
     * @return 会话列表
     */
    suspend fun getAllSessions(includeArchived: Boolean = false): List<ChatSessionEntity>
}

