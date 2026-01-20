package com.soulmate.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class AnniversaryEntity(
    @Id var id: Long = 0,
    var type: String,
    var name: String,
    var month: Int,
    var day: Int,
    var year: Int? = null, // 年份可选，例如生日可能只知道日期
    var message: String? = null, // 可选的自定义消息
    var createdByAI: Boolean = true,
    var timestamp: Long = System.currentTimeMillis()
)
