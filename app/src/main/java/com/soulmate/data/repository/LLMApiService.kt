package com.soulmate.data.repository

import com.soulmate.data.model.llm.ChatRequest
import com.soulmate.data.model.llm.ChatResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * Retrofit interface for communicating with OpenAI-compatible LLM API.
 * 
 * This service is used to send chat requests to cloud LLM providers
 * such as Doubao (火山方舟), DeepSeek, or other OpenAI-compatible APIs.
 */
interface LLMApiService {
    
    /**
     * Sends a chat request to the LLM API (non-streaming).
     * 
     * @param request The chat request containing messages and parameters (stream=false)
     * @return Response containing the chat completion result
     */
    @POST("chat/completions")
    suspend fun generateChat(@Body request: ChatRequest): Response<ChatResponse>
    
    /**
     * Sends a streaming chat request to the LLM API.
     * 
     * 使用 @Streaming 注解避免 Retrofit 缓存整个响应体，实现真正的流式传输。
     * 返回的 ResponseBody 需要手动解析 SSE (Server-Sent Events) 格式。
     * 
     * SSE 格式示例:
     * data: {"choices":[{"delta":{"content":"Hello"}}]}
     * data: {"choices":[{"delta":{"content":" World"}}]}
     * data: [DONE]
     * 
     * @param request The chat request containing messages and parameters (stream=true)
     * @return Response containing SSE stream body
     */
    @Streaming
    @POST("chat/completions")
    suspend fun generateChatStream(@Body request: ChatRequest): Response<ResponseBody>
}

