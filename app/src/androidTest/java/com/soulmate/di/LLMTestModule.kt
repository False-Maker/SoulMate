package com.soulmate.di

import com.soulmate.core.data.brain.LLMService
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Singleton

/**
 * Test module that provides a mocked LLMService for stress testing.
 * 
 * This replaces the real LLMService with a mock that returns instant responses
 * without making actual API calls.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [LLMTestModule::class] // Self-reference to not replace production module
)
object LLMTestModule {

    /**
     * Provides a mocked LLMService for testing.
     * Returns instant mock responses without network calls.
     */
    @Provides
    @Singleton
    fun provideMockLLMService(): MockLLMService {
        return MockLLMService()
    }
}

/**
 * Mock LLM Service for stress testing.
 * Returns predefined responses without any network delay.
 */
class MockLLMService {
    
    private val streamDelayMs: Long = 5L // Fast streaming for stress test
    
    private val mockResponses = listOf(
        "I'm here for you!",
        "That's a great thought.",
        "Tell me more about that.",
        "How interesting!",
        "I understand how you feel."
    )
    
    suspend fun chat(userMessage: String): String {
        // Small delay to simulate processing
        delay(10)
        return mockResponses.random()
    }
    
    fun chatStream(userMessage: String): Flow<String> = flow {
        val response = mockResponses.random()
        val accumulated = StringBuilder()
        
        for (char in response) {
            accumulated.append(char)
            emit(accumulated.toString())
            delay(streamDelayMs)
        }
    }
}
