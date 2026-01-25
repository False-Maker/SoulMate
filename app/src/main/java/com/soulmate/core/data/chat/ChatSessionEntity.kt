package com.soulmate.core.data.chat

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * ChatSessionEntity - 会话实体
 * 
 * 用于持久化会话信息，支持重启后恢复对话历史
 */
@Entity
data class ChatSessionEntity(
    @Id
    var id: Long = 0,
    
    /** 会话标题（可选，默认由首条 user 内容截断生成） */
    var title: String? = null,
    
    /** 会话创建时间 */
    var createdAt: Long = System.currentTimeMillis(),
    
    /** 会话更新时间（用于排序最近会话） */
    @Index
    var updatedAt: Long = System.currentTimeMillis(),
    
    /** 是否已归档（可选，用于隐藏旧会话） */
    var isArchived: Boolean = false
)

