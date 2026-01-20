package com.soulmate.di

import com.soulmate.BuildConfig
import com.soulmate.data.repository.LLMApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
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
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Provides a Retrofit instance configured for the LLM API.
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
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
}

