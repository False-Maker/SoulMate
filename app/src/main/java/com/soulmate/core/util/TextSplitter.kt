package com.soulmate.core.util

/**
 * TextSplitter - Dify 风格的智能文本切片工具
 * 
 * 核心算法：递归字符切分 (Recursive Character Text Splitting) + 滑动窗口重叠
 * 
 * 参考：Dify / LangChain 的切片机制
 * - 优先使用语义分隔符（段落 -> 行 -> 句子）切分
 * - 支持 chunk overlap 防止语义断裂
 * 
 * @see <a href="https://github.com/langgenius/dify">Dify GitHub</a>
 */
object TextSplitter {
    
    /**
     * 默认分隔符列表（按优先级从高到低）
     * 
     * 逻辑：优先保持段落完整，然后是行，最后是句子标点
     */
    private val DEFAULT_SEPARATORS = listOf(
        "\n\n",   // 段落（空行分隔）
        "\n",     // 换行
        "。",     // 中文句号
        "！",     // 中文感叹号
        "？",     // 中文问号
        "；",     // 中文分号
        ".",      // 英文句号
        "!",      // 英文感叹号
        "?",      // 英文问号
        ";",      // 英文分号
        "，",     // 中文逗号
        ",",      // 英文逗号
        " "       // 空格（最后的退化选项）
    )
    
    /**
     * 智能切分文本
     * 
     * @param text 待切分的长文本
     * @param chunkSize 目标块大小（字符数），默认 500
     * @param chunkOverlap 重叠大小（字符数），默认 50
     * @param separators 分隔符列表，默认使用预定义列表
     * @return 切分后的文本块列表
     */
    fun split(
        text: String,
        chunkSize: Int = 500,
        chunkOverlap: Int = 50,
        separators: List<String> = DEFAULT_SEPARATORS
    ): List<String> {
        // 空文本直接返回
        if (text.isBlank()) return emptyList()
        
        // 文本长度小于 chunkSize，无需切分
        if (text.length <= chunkSize) return listOf(text)
        
        // 递归切分
        val chunks = recursiveSplit(text, separators, chunkSize)
        
        // 合并过小的块，并添加重叠
        return mergeWithOverlap(chunks, chunkSize, chunkOverlap)
    }
    
    /**
     * 递归切分：尝试使用分隔符列表切分文本
     * 
     * 逻辑：
     * 1. 使用当前优先级最高的分隔符切分
     * 2. 如果切出的块仍超过 chunkSize，递归使用下一级分隔符
     * 3. 如果已是最后一个分隔符，强制按字符切断
     */
    private fun recursiveSplit(
        text: String,
        separators: List<String>,
        chunkSize: Int
    ): List<String> {
        if (text.length <= chunkSize) return listOf(text)
        if (separators.isEmpty()) {
            // 所有分隔符用尽，强制按字符切断
            return forceChunk(text, chunkSize)
        }
        
        val separator = separators.first()
        val remainingSeparators = separators.drop(1)
        
        // 使用当前分隔符切分
        val parts = splitBySeparator(text, separator)
        
        // 如果没有切开（只有一块），尝试下一级分隔符
        if (parts.size <= 1) {
            return recursiveSplit(text, remainingSeparators, chunkSize)
        }
        
        // 对每个切出的部分检查：如果仍然过长，递归处理
        val result = mutableListOf<String>()
        for (part in parts) {
            if (part.length <= chunkSize) {
                result.add(part)
            } else {
                // 递归使用同级或下一级分隔符
                result.addAll(recursiveSplit(part, remainingSeparators, chunkSize))
            }
        }
        
        return result
    }
    
    /**
     * 使用指定分隔符切分文本，保留分隔符在前一个片段末尾
     * 
     * 例如：按 "。" 切分 "你好。世界。" -> ["你好。", "世界。"]
     */
    private fun splitBySeparator(text: String, separator: String): List<String> {
        if (!text.contains(separator)) return listOf(text)
        
        val result = mutableListOf<String>()
        var remaining = text
        
        while (remaining.isNotEmpty()) {
            val index = remaining.indexOf(separator)
            if (index == -1) {
                // 没有更多分隔符，添加剩余部分
                if (remaining.isNotBlank()) {
                    result.add(remaining)
                }
                break
            }
            
            // 包含分隔符的片段
            val part = remaining.substring(0, index + separator.length)
            if (part.isNotBlank()) {
                result.add(part)
            }
            remaining = remaining.substring(index + separator.length)
        }
        
        return result
    }
    
    /**
     * 强制按字符切断（最后的退化策略）
     */
    private fun forceChunk(text: String, chunkSize: Int): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            val end = minOf(index + chunkSize, text.length)
            result.add(text.substring(index, end))
            index = end
        }
        return result
    }
    
    /**
     * 合并过小的块，并添加重叠
     * 
     * 逻辑：
     * 1. 将连续的小块合并，使每个最终块尽量接近 chunkSize
     * 2. 在相邻块之间添加 overlap 重叠内容
     */
    private fun mergeWithOverlap(
        chunks: List<String>,
        chunkSize: Int,
        chunkOverlap: Int
    ): List<String> {
        if (chunks.isEmpty()) return emptyList()
        if (chunks.size == 1) return chunks
        
        // 第一步：合并过小的块
        val merged = mergeSmallChunks(chunks, chunkSize)
        
        if (merged.size <= 1 || chunkOverlap <= 0) return merged
        
        // 第二步：添加重叠
        return addOverlap(merged, chunkOverlap)
    }
    
    /**
     * 合并过小的块
     * 
     * 将连续的小块合并，直到接近 chunkSize
     */
    private fun mergeSmallChunks(chunks: List<String>, chunkSize: Int): List<String> {
        val result = mutableListOf<String>()
        val currentChunk = StringBuilder()
        
        for (chunk in chunks) {
            if (currentChunk.isEmpty()) {
                currentChunk.append(chunk)
            } else if (currentChunk.length + chunk.length <= chunkSize) {
                // 可以合并
                currentChunk.append(chunk)
            } else {
                // 当前块已满，保存并开始新块
                result.add(currentChunk.toString())
                currentChunk.clear()
                currentChunk.append(chunk)
            }
        }
        
        // 添加最后一个块
        if (currentChunk.isNotEmpty()) {
            result.add(currentChunk.toString())
        }
        
        return result
    }
    
    /**
     * 添加块间重叠
     * 
     * 从前一个块的末尾取 overlap 字符，添加到下一个块的开头
     */
    private fun addOverlap(chunks: List<String>, overlap: Int): List<String> {
        if (chunks.size <= 1) return chunks
        
        val result = mutableListOf<String>()
        result.add(chunks.first())
        
        for (i in 1 until chunks.size) {
            val prevChunk = chunks[i - 1]
            val currentChunk = chunks[i]
            
            // 从前一个块末尾取 overlap 字符作为重叠前缀
            val overlapSize = minOf(overlap, prevChunk.length)
            val overlapText = prevChunk.takeLast(overlapSize)
            
            // 避免重复：如果当前块已经以 overlapText 开头，则不添加
            if (!currentChunk.startsWith(overlapText)) {
                result.add(overlapText + currentChunk)
            } else {
                result.add(currentChunk)
            }
        }
        
        return result
    }
    
    /**
     * 判断文本是否需要切分
     * 
     * @param text 待检查的文本
     * @param threshold 长度阈值，默认 500
     * @return 是否需要切分
     */
    fun needsSplitting(text: String, threshold: Int = 500): Boolean {
        return text.length > threshold
    }
    
    /**
     * 带元数据的切分结果
     */
    data class ChunkResult(
        /** 切分后的文本块列表 */
        val chunks: List<String>,
        /** 原始文本长度 */
        val originalLength: Int,
        /** 是否进行了切分 */
        val wasSplit: Boolean,
        /** 切分后的块数 */
        val chunkCount: Int
    )
    
    /**
     * 智能切分文本（带元数据）
     * 
     * @param text 待切分的长文本
     * @param chunkSize 目标块大小
     * @param chunkOverlap 重叠大小
     * @return 带元数据的切分结果
     */
    fun splitWithMetadata(
        text: String,
        chunkSize: Int = 500,
        chunkOverlap: Int = 50
    ): ChunkResult {
        val chunks = split(text, chunkSize, chunkOverlap)
        return ChunkResult(
            chunks = chunks,
            originalLength = text.length,
            wasSplit = chunks.size > 1,
            chunkCount = chunks.size
        )
    }
}

