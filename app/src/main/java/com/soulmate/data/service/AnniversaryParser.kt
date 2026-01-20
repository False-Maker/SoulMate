package com.soulmate.data.service

import com.soulmate.data.model.AnniversaryEntity

/**
 * AnniversaryParser - 解析 [ANNIVERSARY:...] 标签
 */
object AnniversaryParser {

    // [ANNIVERSARY:type|name|month-day|year]
    // year is optional: [ANNIVERSARY:birthday|name|3-15|] or [ANNIVERSARY:birthday|name|3-15|1990]
    private val pattern = Regex("""\[ANNIVERSARY:([^|]+)\|([^|]+)\|(\d{1,2})-(\d{1,2})\|?(\d{4})?]""")

    fun parse(text: String): List<AnniversaryEntity> {
        return pattern.findAll(text).mapNotNull { matchResult ->
            try {
                val (type, name, monthStr, dayStr, yearStr) = matchResult.destructured
                AnniversaryEntity(
                    type = type.trim(),
                    name = name.trim(),
                    month = monthStr.toInt(),
                    day = dayStr.toInt(),
                    year = if (yearStr.isNotEmpty()) yearStr.toInt() else null,
                    createdByAI = true
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }.toList()
    }
}
