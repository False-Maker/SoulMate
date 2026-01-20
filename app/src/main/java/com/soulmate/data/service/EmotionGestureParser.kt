package com.soulmate.data.service

/**
 * EmotionGestureParser - 解析 LLM 输出中的情绪和动作标签
 * 
 * 期望输入格式: [EMOTION:happy] [GESTURE:nod] 你今天看起来心情不错！
 * 解析后返回: ParsedResponse(emotion="happy", gesture="nod", text="你今天看起来心情不错！")
 */
object EmotionGestureParser {
    
    // 匹配 [EMOTION:xxx] 标签
    private val emotionPattern = Regex("""\[EMOTION:(\w+)]""", RegexOption.IGNORE_CASE)
    
    // 匹配 [GESTURE:xxx] 标签
    private val gesturePattern = Regex("""\[GESTURE:(\w+)]""", RegexOption.IGNORE_CASE)
    
    // 匹配 [Inner]: xxx 部分（包括内容直到 [Reply] 或结尾）
    private val innerPattern = Regex("""\[Inner\]:\s*.*?(?=\[Reply\]|$)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    
    // 匹配 [Reply]: 标签本身（保留其后的内容）
    private val replyTagPattern = Regex("""\[Reply\]:\s*""", RegexOption.IGNORE_CASE)
    
    // 默认值
    private const val DEFAULT_EMOTION = "neutral"
    private const val DEFAULT_GESTURE = "nod"
    
    /**
     * 解析结果数据类
     * @param emotion 情绪标签（如 happy, sad, neutral 等）
     * @param gesture 动作标签（如 nod, wave, think 等）
     * @param text 去除标签后的纯文本内容
     */
    data class ParsedResponse(
        val emotion: String,
        val gesture: String,
        val text: String
    )
    
    /**
     * 解析 LLM 输出，提取情绪和动作标签
     * 
     * @param rawResponse LLM 返回的原始文本
     * @return ParsedResponse 包含情绪、动作和纯文本
     */
    fun parse(rawResponse: String): ParsedResponse {
        // 提取情绪标签
        val emotionMatch = emotionPattern.find(rawResponse)
        val emotion = emotionMatch?.groupValues?.getOrNull(1)?.lowercase() ?: DEFAULT_EMOTION
        
        // 提取动作标签
        val gestureMatch = gesturePattern.find(rawResponse)
        val gesture = gestureMatch?.groupValues?.getOrNull(1)?.lowercase() ?: DEFAULT_GESTURE
        
        // 移除所有标签，获取纯文本
        var cleanText = rawResponse
            // 先移除 [Inner]: xxx 部分（内心独白）
            .replace(innerPattern, "")
            // 移除 [Reply]: 标签（保留后面的内容）
            .replace(replyTagPattern, "")
            // 移除情绪和动作标签
            .replace(emotionPattern, "")
            .replace(gesturePattern, "")
            // 移除纪念日标签 [ANNIVERSARY:...]
            .replace(Regex("""\[ANNIVERSARY:.*?]"""), "")
            .trim()
        
        // 清理可能残留的空格或换行
        cleanText = cleanText.replace(Regex("^\\s+"), "")
        
        return ParsedResponse(
            emotion = emotion,
            gesture = gesture,
            text = cleanText
        )
    }
    
    /**
     * 验证情绪标签是否有效
     */
    fun isValidEmotion(emotion: String): Boolean {
        val validEmotions = setOf("happy", "sad", "angry", "surprised", "neutral", "loving", "worried", "excited")
        return emotion.lowercase() in validEmotions
    }
    
    /**
     * 验证动作标签是否有效
     */
    fun isValidGesture(gesture: String): Boolean {
        val validGestures = setOf("nod", "shake_head", "wave", "think", "shrug", "bow", "clap", "heart")
        return gesture.lowercase() in validGestures
    }
}
