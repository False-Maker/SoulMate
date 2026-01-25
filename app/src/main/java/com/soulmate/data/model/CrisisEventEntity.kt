package com.soulmate.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * CrisisEventEntity - 危机事件实体
 *
 * 用于持久化 CrisisInterventionManager 的危机记录
 */
@Entity
data class CrisisEventEntity(
    @Id
    var id: Long = 0,

    /** 事件ID (字符串标识符) */
    @Index
    var eventId: String? = null,

    /** 发生时间 */
    @Index
    var timestamp: Long = System.currentTimeMillis(),

    /** 危机等级 (1-4) */
    var level: Int = 0,

    /** 触发内容的摘要 */
    var triggerText: String? = null,

    /** 触发的关键词 (逗号分隔) */
    var keywords: String? = null,

    /** 是否已处理 */
    var handled: Boolean = false,

    /** 处理备注 */
    var notes: String? = null
) {
    // 辅助方法：获取关键词列表
    fun getKeywordList(): List<String> {
        return keywords?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    // 辅助方法：设置关键词列表
    fun setKeywordList(list: List<String>) {
        keywords = list.joinToString(",")
    }
}
