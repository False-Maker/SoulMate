package com.soulmate.core.util

import android.util.Log
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min
import kotlin.math.pow

/**
 * RetryPolicy - 指数退避重试策略
 * 
 * 用于 API 调用失败时的自动重试，避免移动端网络波动导致对话直接失败。
 * 
 * 策略：
 * - 最大重试次数：3 次
 * - 初始延迟：1 秒
 * - 最大延迟：8 秒
 * - 退避公式：delay = min(initialDelay * 2^attempt, maxDelay)
 * - 可重试异常：网络相关异常（IOException, SocketTimeoutException, UnknownHostException）
 */
object RetryPolicy {
    private const val TAG = "RetryPolicy"
    
    // 默认配置
    private const val DEFAULT_MAX_RETRIES = 3
    private const val DEFAULT_INITIAL_DELAY_MS = 1000L
    private const val DEFAULT_MAX_DELAY_MS = 8000L
    
    /**
     * 执行带重试的操作
     * 
     * @param maxRetries 最大重试次数（默认 3 次）
     * @param initialDelayMs 初始延迟（默认 1000ms）
     * @param maxDelayMs 最大延迟（默认 8000ms）
     * @param shouldRetry 自定义重试条件（默认仅重试网络相关异常）
     * @param operation 需要执行的操作
     * @return 操作结果
     * @throws Exception 当所有重试都失败时抛出最后一个异常
     */
    suspend fun <T> withRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        shouldRetry: (Exception) -> Boolean = ::isRetryableException,
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null
        
        repeat(maxRetries + 1) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                
                // 检查是否应该重试
                if (!shouldRetry(e) || attempt >= maxRetries) {
                    Log.e(TAG, "Operation failed (attempt ${attempt + 1}/${maxRetries + 1}), not retrying", e)
                    throw e
                }
                
                // 计算指数退避延迟
                val delayMs = calculateDelay(attempt, initialDelayMs, maxDelayMs)
                Log.w(TAG, "Operation failed (attempt ${attempt + 1}/${maxRetries + 1}), retrying in ${delayMs}ms: ${e.message}")
                
                delay(delayMs)
            }
        }
        
        // 不应该到达这里，但为了类型安全
        throw lastException ?: IllegalStateException("Unexpected retry state")
    }
    
    /**
     * 计算指数退避延迟
     * 
     * 公式: delay = min(initialDelay * 2^attempt, maxDelay)
     */
    private fun calculateDelay(attempt: Int, initialDelayMs: Long, maxDelayMs: Long): Long {
        val exponentialDelay = initialDelayMs * 2.0.pow(attempt.toDouble()).toLong()
        return min(exponentialDelay, maxDelayMs)
    }
    
    /**
     * 判断异常是否可重试
     * 
     * 仅对网络相关的临时性异常进行重试，其他异常（如参数错误）直接抛出。
     */
    private fun isRetryableException(e: Exception): Boolean {
        return when (e) {
            // 网络相关异常 - 可重试
            is IOException,
            is SocketTimeoutException,
            is UnknownHostException -> true
            
            // API 错误 - 检查是否是临时性错误（5xx）
            else -> {
                val message = e.message?.lowercase() ?: ""
                // 服务端临时错误可重试
                message.contains("500") ||
                message.contains("502") ||
                message.contains("503") ||
                message.contains("504") ||
                message.contains("timeout") ||
                message.contains("connection")
            }
        }
    }
}

