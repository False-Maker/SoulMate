package com.soulmate.data.model

import android.util.Log

/**
 * UIEventParser - 解析 Avatar SDK 返回的 XML 格式 UI 事件
 *
 * 使用正则表达式解析 <uievent> 标签块，提取事件类型和数据。
 * 
 * XML 格式示例：
 * ```xml
 * <uievent>
 *     <type>widget_video</type>
 *     <data>
 *         <video>https://...</video>
 *         <cover>https://...</cover>
 *         <axis_id>101</axis_id>
 *     </data>
 * </uievent>
 * ```
 */
object UIEventParser {
    
    private const val TAG = "UIEventParser"
    
    // 匹配 <uievent>...</uievent> 块
    private val uiEventPattern = Regex(
        """<uievent>(.*?)</uievent>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    
    // 匹配 <type>...</type>
    private val typePattern = Regex(
        """<type>(.*?)</type>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    
    // 匹配 <data>...</data>
    private val dataPattern = Regex(
        """<data>(.*?)</data>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    
    // 匹配任意 XML 标签 <tag>value</tag>
    private val tagPattern = Regex(
        """<(\w+)>(.*?)</\1>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    
    /**
     * 解析字符串中的所有 UIEvent
     * 
     * @param content 包含 <uievent> 标签的字符串（可能包含多个事件）
     * @return 解析出的 UIEvent 列表
     */
    fun parse(content: String?): List<UIEvent> {
        if (content.isNullOrBlank()) {
            return emptyList()
        }
        
        val events = mutableListOf<UIEvent>()
        
        try {
            uiEventPattern.findAll(content).forEach { matchResult ->
                val eventBlock = matchResult.groupValues[1]
                parseEventBlock(eventBlock)?.let { event ->
                    events.add(event)
                    Log.d(TAG, "Parsed UIEvent: type=${event.type}, data=${event.data}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing UIEvents from content", e)
        }
        
        return events
    }
    
    /**
     * 解析单个 <uievent> 块的内容
     */
    private fun parseEventBlock(block: String): UIEvent? {
        // 提取 type
        val type = typePattern.find(block)?.groupValues?.get(1)?.trim()
        if (type.isNullOrBlank()) {
            Log.w(TAG, "UIEvent block missing type: $block")
            return null
        }
        
        // 提取 data 块中的键值对
        val dataMap = mutableMapOf<String, String>()
        dataPattern.find(block)?.groupValues?.get(1)?.let { dataBlock ->
            tagPattern.findAll(dataBlock).forEach { tagMatch ->
                val key = tagMatch.groupValues[1].trim()
                val value = tagMatch.groupValues[2].trim()
                if (key.isNotBlank() && value.isNotBlank()) {
                    dataMap[key] = value
                }
            }
        }
        
        return UIEvent(
            type = type,
            data = dataMap,
            timestamp = System.currentTimeMillis()
        )
    }
}
