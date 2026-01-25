package com.soulmate.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.soulmate.BuildConfig
import com.soulmate.data.model.llm.content.MessageContent
import com.soulmate.data.model.llm.content.MessageContentJsonAdapter
import com.soulmate.data.repository.ImageGenApiService
import com.soulmate.data.repository.LLMApiService
import com.soulmate.data.preferences.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for providing network-related dependencies.
 * 
 * This module provides:
 * - OkHttpClient with logging and timeout configuration
 * - Retrofit instance for API calls
 * - LLMApiService implementation
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    /**
     * Base URL for the LLM API (Doubao/火山方舟).
     */
    private val BASE_URL: String
        get() = BuildConfig.DOUBAO_BASE_URL.ifEmpty { "https://ark.cn-beijing.volces.com/api/v3" }
    
    /**
     * Provides an OkHttpClient with logging interceptor and timeout configuration.
     * 
     * 根据优化开关选择使用优化版本或原版本（默认使用原版本，保持稳定性）
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        userPreferencesRepository: UserPreferencesRepository
    ): OkHttpClient {
        // 如果启用优化超时，使用优化版本（默认关闭）
        if (userPreferencesRepository.isOptimizedTimeoutsEnabled()) {
            return provideOptimizedOkHttpClient()
        }
        
        return provideOkHttpClientOriginal()
    }
    
    /**
     * 原版本的 OkHttpClient（保持原有配置）
     */
    private fun provideOkHttpClientOriginal(): OkHttpClient {
        // Logging interceptor for debugging - PRIVACY: Only log headers, never log body content
        // which may contain user prompts and AI responses
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        // Authorization interceptor to inject API key
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            
            // Use Doubao API key from BuildConfig
            val apiKey = BuildConfig.DOUBAO_API_KEY
            
            val newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()
            
            chain.proceed(newRequest)
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            // 流式请求需要更长的读取超时（LLM 生成可能需要较长时间）
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // 连接池优化：配置连接池大小和空闲连接保持时间
            .connectionPool(ConnectionPool(
                maxIdleConnections = 5,  // 最大空闲连接数
                keepAliveDuration = 5,   // 空闲连接保持时间（分钟）
                timeUnit = TimeUnit.MINUTES
            ))
            // 启用 HTTP/2 多路复用（如果服务器支持）
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }
    
    /**
     * 优化版本的 OkHttpClient（性能优化）
     * 
     * 优化点：
     * - 连接超时：30 秒 → 10 秒
     * - 读取超时：120 秒 → 60 秒（流式请求）
     * 
     * 预期收益：失败时更快失败，成功时无影响
     * 
     * 注意：默认关闭，需要手动启用
     */
    private fun provideOptimizedOkHttpClient(): OkHttpClient {
        // Logging interceptor for debugging
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        // Authorization interceptor to inject API key
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            
            val apiKey = BuildConfig.DOUBAO_API_KEY
            
            val newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()
            
            chain.proceed(newRequest)
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)  // 优化：从 30 秒降到 10 秒
            .readTimeout(60, TimeUnit.SECONDS)    // 优化：从 120 秒降到 60 秒
            .writeTimeout(30, TimeUnit.SECONDS)
            // 连接池优化：配置连接池大小和空闲连接保持时间
            .connectionPool(ConnectionPool(
                maxIdleConnections = 5,  // 最大空闲连接数
                keepAliveDuration = 5,   // 空闲连接保持时间（分钟）
                timeUnit = TimeUnit.MINUTES
            ))
            // 启用 HTTP/2 多路复用（如果服务器支持）
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }
    
    /**
     * Provides a Gson instance with custom adapters for multimodal content.
     * 
     * 注册 MessageContentJsonAdapter，支持：
     * - TextContent → JSON string
     * - PartsContent → JSON array
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(MessageContent::class.java, MessageContentJsonAdapter())
            .create()
    }
    
    /**
     * Provides a Retrofit instance configured for the LLM API.
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    /**
     * Provides the LLMApiService implementation.
     */
    @Provides
    @Singleton
    fun provideLLMApiService(retrofit: Retrofit): LLMApiService {
        return retrofit.create(LLMApiService::class.java)
    }
    
    /**
     * Provides the ImageGenApiService implementation.
     * 
     * 复用同一个 Retrofit 实例，因为 Base URL 相同。
     */
    @Provides
    @Singleton
    fun provideImageGenApiService(retrofit: Retrofit): ImageGenApiService {
        return retrofit.create(ImageGenApiService::class.java)
    }
}

