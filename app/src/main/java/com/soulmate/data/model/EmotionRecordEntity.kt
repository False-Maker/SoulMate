package com.soulmate.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * EmotionRecordEntity - 情绪记录实体
 *
 * 用于持久化 MindWatchService 的监测数据，支持生成月度报告
 */
@Entity
data class EmotionRecordEntity(
    @Id
    var id: Long = 0,

    /** 记录时间戳 */
    @Index
    var timestamp: Long = System.currentTimeMillis(),

    /** 情绪评分 (-10 ~ 10) */
    var score: Int = 0,

    /** 情绪标签 (happy, sad, etc.) */
    var emotionLabel: String? = null,

    /** 触发的关键词列表 (逗号分隔字符串存储) */
    var keywords: String? = null
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
