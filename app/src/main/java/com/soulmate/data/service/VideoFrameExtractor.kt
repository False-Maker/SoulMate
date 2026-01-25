package com.soulmate.data.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VideoFrameExtractor - 视频帧提取器
 * 
 * 从本地视频中均匀采样提取关键帧，用于视频理解（多模态）。
 * 
 * 特性：
 * - 使用 MediaMetadataRetriever 进行帧提取
 * - 支持 content:// 和 file:// URI
 * - 均匀时间采样（而非固定 fps）以保证覆盖
 * - 所有操作在 IO 线程执行
 * 
 * @author SoulMate
 */
@Singleton
class VideoFrameExtractor @Inject constructor() {
    
    companion object {
        private const val TAG = "VideoFrameExtractor"
        
        // 默认最大帧数
        const val DEFAULT_MAX_FRAMES = 6
        
        // 最小视频时长（毫秒）
        private const val MIN_DURATION_MS = 100L
        
        // 最大视频时长（10 分钟）
        private const val MAX_DURATION_MS = 10 * 60 * 1000L
    }
    
    /**
     * 从视频中提取帧
     * 
     * @param context Android Context
     * @param videoUri 视频 URI（content:// 或 file://）
     * @param maxFrames 最大帧数（默认 6）
     * @return 提取的帧列表（Bitmap）
     * @throws VideoExtractionException 如果提取失败
     */
    suspend fun extractFrames(
        context: Context,
        videoUri: Uri,
        maxFrames: Int = DEFAULT_MAX_FRAMES
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        
        try {
            Log.d(TAG, "Extracting frames from: $videoUri (maxFrames=$maxFrames)")
            
            // 1. 设置数据源
            try {
                retriever.setDataSource(context, videoUri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set data source", e)
                throw VideoExtractionException("无法打开视频文件")
            }
            
            // 2. 获取视频时长
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            
            if (durationMs < MIN_DURATION_MS) {
                throw VideoExtractionException("视频时长过短")
            }
            
            if (durationMs > MAX_DURATION_MS) {
                Log.w(TAG, "Video duration ($durationMs ms) exceeds limit, will sample from first 10 minutes")
            }
            
            val effectiveDuration = minOf(durationMs, MAX_DURATION_MS)
            Log.d(TAG, "Video duration: ${effectiveDuration}ms")
            
            // 3. 计算采样时间点（均匀分布）
            val actualFrameCount = minOf(maxFrames, (effectiveDuration / 1000).toInt().coerceAtLeast(1))
            val timePoints = calculateSampleTimePoints(effectiveDuration, actualFrameCount)
            Log.d(TAG, "Sampling ${timePoints.size} frames at: ${timePoints.map { it / 1000 }}s")
            
            // 4. 提取帧
            val frames = mutableListOf<Bitmap>()
            
            for ((index, timeUs) in timePoints.withIndex()) {
                try {
                    val frame = retriever.getFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    
                    if (frame != null) {
                        frames.add(frame)
                        Log.d(TAG, "Extracted frame ${index + 1}/${timePoints.size}: ${frame.width}x${frame.height}")
                    } else {
                        Log.w(TAG, "Failed to extract frame at ${timeUs / 1000000}s")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error extracting frame at ${timeUs / 1000000}s", e)
                    // 继续尝试其他帧
                }
            }
            
            if (frames.isEmpty()) {
                throw VideoExtractionException("无法从视频中提取任何帧")
            }
            
            Log.d(TAG, "Successfully extracted ${frames.size} frames")
            frames
            
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }
    }
    
    /**
     * 获取视频时长（毫秒）
     * 
     * @param context Android Context
     * @param videoUri 视频 URI
     * @return 视频时长（毫秒），如果无法获取则返回 null
     */
    suspend fun getVideoDuration(context: Context, videoUri: Uri): Long? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get video duration", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
    
    /**
     * 计算均匀采样的时间点
     * 
     * @param durationMs 视频时长（毫秒）
     * @param frameCount 要提取的帧数
     * @return 采样时间点列表（微秒）
     */
    private fun calculateSampleTimePoints(durationMs: Long, frameCount: Int): List<Long> {
        if (frameCount <= 0) return emptyList()
        if (frameCount == 1) return listOf(durationMs * 500) // 取中间帧
        
        val durationUs = durationMs * 1000
        val interval = durationUs / (frameCount + 1)
        
        return (1..frameCount).map { i ->
            interval * i
        }
    }
}

/**
 * 视频帧提取异常
 */
class VideoExtractionException(message: String) : Exception(message)
