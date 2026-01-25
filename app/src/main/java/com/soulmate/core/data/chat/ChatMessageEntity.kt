package com.soulmate.core.data.chat

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * ChatMessageEntity - 聊天消息实体
 * 
 * 用于持久化每条聊天消息，支持重启后恢复对话历史
 */
@Entity
data class ChatMessageEntity(
    @Id
    var id: Long = 0,
    
    /** 所属会话ID（索引，用于按会话查消息） */
    @Index
    var sessionId: Long = 0,
    
    /** 消息角色：user / assistant / system */
    var role: String = "user",
    
    /** 干净文本内容（不包含 emotion/gesture tag） */
    var content: String = "",
    
    /** 原始内容（可选，存模型原始输出，含标签，方便复盘/重新解析） */
    var rawContent: String? = null,
    
    /** 消息时间戳（索引，用于排序） */
    @Index
    var timestamp: Long = System.currentTimeMillis(),
    
    /** 图片 URL（可选，用于 ImageGen 生成的图片消息） */
    var imageUrl: String? = null,
    
    /** 本地图片 URI（可选，用于用户发送的本地图片） */
    var localImageUri: String? = null,

    /** 本地视频 URI（可选，用于用户发送的本地视频） */
    var localVideoUri: String? = null
)

