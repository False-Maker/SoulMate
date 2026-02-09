package com.soulmate.core.data.chat

import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.reactive.DataObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import com.soulmate.data.model.UIWidgetParser
import com.soulmate.ui.state.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBoxChatRepository - 基于 ObjectBox 的会话存储实现
 */
@Singleton
class ObjectBoxChatRepository @Inject constructor(
    private val boxStore: BoxStore
) : ChatRepository {

    private val sessionBox = boxStore.boxFor(ChatSessionEntity::class)
    private val messageBox = boxStore.boxFor(ChatMessageEntity::class)

    override suspend fun getOrCreateActiveSession(): Long = withContext(Dispatchers.IO) {
        // 查找最近更新的未归档会话
        val activeSession = sessionBox.query(ChatSessionEntity_.isArchived.equal(false))
            .orderDesc(ChatSessionEntity_.updatedAt)
            .build()
            .findFirst()
        
        if (activeSession != null) {
            activeSession.id
        } else {
            // 创建新会话
            createSession(null)
        }
    }

    override suspend fun createSession(title: String?): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val session = ChatSessionEntity(
            title = title,
            createdAt = now,
            updatedAt = now,
            isArchived = false
        )
        sessionBox.put(session)
    }

    override suspend fun getSession(sessionId: Long): ChatSessionEntity? = withContext(Dispatchers.IO) {
        sessionBox.get(sessionId)
    }

    override fun observeMessages(sessionId: Long, limit: Int): Flow<List<ChatMessageEntity>> = callbackFlow {
        val query = messageBox.query(ChatMessageEntity_.sessionId.equal(sessionId))
            .orderDesc(ChatMessageEntity_.timestamp)
            .build()
        
        val observer = DataObserver<List<ChatMessageEntity>> { messages ->
            // 取最近 limit 条，然后反转为时间正序（旧在前，新在后）
            val limited = messages.take(limit).reversed()
            trySend(limited)
        }
        
        val subscription = query.subscribe().observer(observer)
        
        awaitClose {
            subscription.cancel()
            query.close()
        }
    }

    override suspend fun getRecentMessages(sessionId: Long, limit: Int): List<ChatMessageEntity> = withContext(Dispatchers.IO) {
        messageBox.query(ChatMessageEntity_.sessionId.equal(sessionId))
            .orderDesc(ChatMessageEntity_.timestamp)
            .build()
            .find(0, limit.toLong())
            .reversed()  // 反转为时间正序（旧在前，新在后）
    }

    override suspend fun appendMessage(
        sessionId: Long,
        role: String,
        content: String,
        rawContent: String?,
        imageUrl: String?,
        localImageUri: String?,
        localVideoUri: String?
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val widget = UIWidgetParser.parse(rawContent ?: content).widget
        val uiMessage = ChatMessage(
            content = content,
            isFromUser = role == "user",
            timestamp = now,
            imageUrl = imageUrl,
            localImageUri = localImageUri,
            uiWidget = widget
        )
        val message = ChatMessageEntity.fromUiModel(
            sessionId = sessionId,
            role = role,
            message = uiMessage,
            rawContent = rawContent,
            localVideoUri = localVideoUri
        )
        val messageId = messageBox.put(message)
        
        // 同时更新会话时间戳
        updateSessionTimestampInternal(sessionId)
        
        // 如果是第一条 user 消息且会话无标题，则自动生成标题
        if (role == "user") {
            val session = sessionBox.get(sessionId)
            if (session != null && session.title.isNullOrBlank()) {
                val autoTitle = if (content.length > 20) {
                    content.take(20) + "..."
                } else {
                    content
                }
                session.title = autoTitle
                sessionBox.put(session)
            }
        }
        
        messageId
    }

    override suspend fun updateSessionTimestamp(sessionId: Long) = withContext(Dispatchers.IO) {
        updateSessionTimestampInternal(sessionId)
    }
    
    private fun updateSessionTimestampInternal(sessionId: Long) {
        val session = sessionBox.get(sessionId)
        if (session != null) {
            session.updatedAt = System.currentTimeMillis()
            sessionBox.put(session)
        }
    }

    override suspend fun updateSessionTitle(sessionId: Long, title: String) = withContext(Dispatchers.IO) {
        val session = sessionBox.get(sessionId)
        if (session != null) {
            session.title = title
            sessionBox.put(session)
        }
        Unit
    }

    override suspend fun archiveSession(sessionId: Long) = withContext(Dispatchers.IO) {
        val session = sessionBox.get(sessionId)
        if (session != null) {
            session.isArchived = true
            sessionBox.put(session)
        }
        Unit
    }

    override suspend fun getAllSessions(includeArchived: Boolean): List<ChatSessionEntity> = withContext(Dispatchers.IO) {
        if (includeArchived) {
            sessionBox.query()
                .orderDesc(ChatSessionEntity_.updatedAt)
                .build()
                .find()
        } else {
            sessionBox.query(ChatSessionEntity_.isArchived.equal(false))
                .orderDesc(ChatSessionEntity_.updatedAt)
                .build()
                .find()
        }
    }
}
