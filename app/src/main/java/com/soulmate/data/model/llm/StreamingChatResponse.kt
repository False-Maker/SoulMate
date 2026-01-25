package com.soulmate.data.model.llm

/**
 * Data class representing a streaming chat response chunk from OpenAI-compatible API.
 * 
 * SSE 流式响应格式 (每行以 "data: " 开头):
 * data: {"id":"...","choices":[{"delta":{"content":"Hello"}}]}
 * data: {"id":"...","choices":[{"delta":{"content":" World"}}]}
 * data: [DONE]
 * 
 * @param id Unique identifier for the completion
 * @param choices List of streaming choices (typically contains one choice)
 */
data class StreamingChatResponse(
    val id: String? = null,
    val choices: List<StreamingChoice>? = null
)

/**
 * Data class representing a streaming choice in the chat response.
 * 
 * @param delta The incremental content from the assistant
 * @param finishReason The reason why the response finished (null until final chunk)
 */
data class StreamingChoice(
    val delta: Delta? = null,
    val finishReason: String? = null
)

/**
 * Data class representing the delta (incremental content) in streaming response.
 * 
 * @param role The role (only present in first chunk, usually "assistant")
 * @param content The incremental text content
 */
data class Delta(
    val role: String? = null,
    val content: String? = null
)

