package com.soulmate.data.repository

import com.soulmate.data.model.llm.ChatRequest
import com.soulmate.data.model.llm.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for communicating with OpenAI-compatible LLM API.
 * 
 * This service is used to send chat requests to cloud LLM providers
 * such as Doubao (火山方舟), DeepSeek, or other OpenAI-compatible APIs.
 */
interface LLMApiService {
    
    /**
     * Sends a chat request to the LLM API.
     * 
     * @param request The chat request containing messages and parameters
     * @return Response containing the chat completion result
     */
    @POST("chat/completions")
    suspend fun generateChat(@Body request: ChatRequest): Response<ChatResponse>
}

