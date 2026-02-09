package com.soulmate.data.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.soulmate.ui.state.UIWidgetData

data class ParsedResult(
    val cleanText: String,
    val widget: UIWidgetData?
)

object UIWidgetParser {
    private val gson = Gson()
    private val widgetPattern = Regex(
        """\{[\s\S]*?"widget"\s*:\s*".+?"[\s\S]*?}""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    fun parseJson(json: String?): UIWidgetData? {
        if (json.isNullOrBlank()) return null
        return parseWidget(json)
    }

    fun toJson(widget: UIWidgetData?): String? {
        if (widget == null) return null
        val payload = when (widget) {
            is UIWidgetData.MemoryCapsule -> mapOf(
                "widget" to "memory_capsule",
                "data" to mapOf(
                    "date" to widget.date,
                    "summary" to widget.summary,
                    "images" to widget.imageUrls
                )
            )
            is UIWidgetData.DecisionOptions -> mapOf(
                "widget" to "decision_options",
                "data" to mapOf(
                    "title" to widget.title,
                    "options" to widget.options
                )
            )
            is UIWidgetData.BreathingGuide -> mapOf(
                "widget" to "breathing_guide",
                "data" to mapOf(
                    "duration_seconds" to widget.durationSeconds
                )
            )
        }
        return gson.toJson(payload)
    }

    fun parse(rawText: String): ParsedResult {
        val match = widgetPattern.find(rawText) ?: return ParsedResult(rawText, null)
        val jsonStr = match.value
        val cleanText = rawText.replace(jsonStr, "").trim()
        val widget = parseWidget(jsonStr)
        return ParsedResult(cleanText, widget)
    }

    private fun parseWidget(jsonStr: String): UIWidgetData? {
        return try {
            val root = gson.fromJson(jsonStr, JsonObject::class.java) ?: return null
            val widgetType = root.get("widget")?.asString?.lowercase() ?: return null
            val data = root.getAsJsonObject("data") ?: return null
            when (widgetType) {
                "memory_capsule" -> parseMemoryCapsule(data)
                "decision_options" -> parseDecisionOptions(data)
                "breathing_guide" -> parseBreathingGuide(data)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMemoryCapsule(data: JsonObject): UIWidgetData? {
        val date = data.get("date")?.asString ?: return null
        val summary = data.get("summary")?.asString ?: return null
        val images = data.get("images")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            ?: emptyList()
        return UIWidgetData.MemoryCapsule(date = date, summary = summary, imageUrls = images)
    }

    private fun parseDecisionOptions(data: JsonObject): UIWidgetData? {
        val title = data.get("title")?.asString ?: return null
        val options = data.get("options")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            ?: return null
        return UIWidgetData.DecisionOptions(title = title, options = options)
    }

    private fun parseBreathingGuide(data: JsonObject): UIWidgetData? {
        val duration = data.get("duration_seconds")?.asInt
            ?: data.get("durationSeconds")?.asInt
            ?: return null
        return UIWidgetData.BreathingGuide(durationSeconds = duration)
    }
}
