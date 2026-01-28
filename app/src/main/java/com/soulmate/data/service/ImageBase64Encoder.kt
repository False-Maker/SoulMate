package com.soulmate.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ImageBase64Encoder - 图片编码工具
 * 
 * 将本地 content:// URI 转换为 data:image/jpeg;base64,... 格式
 * 用于发送给 Vision 模型。
 * 
 * 特性：
 * - 所有操作在 IO 线程执行
 * - 自动缩放：最长边 ≤ 1024，像素 ≤ 4MP
 * - 压缩：JPEG quality 70
 * - 内存限制：base64 输出 ≤ 4MB
 * 
 * @author SoulMate
 */
@Singleton
class ImageBase64Encoder @Inject constructor() {
    
    companion object {
        private const val TAG = "ImageBase64Encoder"
        
        // 目标最长边
        private const val MAX_DIMENSION = 1024
        
        // 最大像素数（约 4MP）
        private const val MAX_PIXELS = 4_000_000
        
        // JPEG 压缩质量
        private const val JPEG_QUALITY = 70
        
        // 最大输出 base64 长度（4MB 文本）
        private const val MAX_BASE64_LENGTH = 4 * 1024 * 1024
        
        // 最大输入文件大小（20MB）
        private const val MAX_INPUT_FILE_SIZE = 20L * 1024 * 1024
    }
    
    /**
     * 将本地 URI 转换为 data URL
     * 
     * @param context Android Context
     * @param uri 本地图片 URI（content:// 或 file://）
     * @return data:image/jpeg;base64,... 格式的字符串
     * @throws ImageEncodingException 如果编码失败
     */
    suspend fun encodeToDataUrl(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Encoding image: $uri")
            
            // 1. 检查文件大小
            val fileSize = getFileSize(context, uri)
            if (fileSize > MAX_INPUT_FILE_SIZE) {
                throw ImageEncodingException("图片文件过大（>${MAX_INPUT_FILE_SIZE / 1024 / 1024}MB）")
            }
            Log.d(TAG, "File size: ${fileSize / 1024}KB")
            
            // 2. 先读取图片尺寸（不加载像素）
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            openInputStreamFallback(context, uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
                options // Return non-null object to satisfy use block
            } ?: run {
                Log.e(TAG, "Failed to open input stream for bounds check: $uri")
                throw ImageEncodingException("无法打开图片流")
            }
            
            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            Log.d(TAG, "Read dimensions: ${originalWidth}x${originalHeight}")
            
            if (originalWidth <= 0 || originalHeight <= 0) {
                // 有些图片格式可能导致 decodeStream 返回 true 但宽高为 -1，或者直接返回 false
                // 这里增加更明确的错误日志
                Log.e(TAG, "Invalid dimensions read: ${originalWidth}x${originalHeight}. Mime: ${options.outMimeType}")
                throw ImageEncodingException("无法读取图片尺寸 (格式: ${options.outMimeType ?: "unknown"})")
            }
            Log.d(TAG, "Original size: ${originalWidth}x${originalHeight}")
            
            // 3. 计算缩放因子
            val sampleSize = calculateSampleSize(originalWidth, originalHeight)
            Log.d(TAG, "Using inSampleSize: $sampleSize")
            
            // 4. 加载缩放后的图片
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // 省内存
            }
            
            val bitmap = openInputStreamFallback(context, uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: throw ImageEncodingException("无法解码图片")
            
            Log.d(TAG, "Decoded size: ${bitmap.width}x${bitmap.height}")
            
            // 5. 二次缩放（如果 inSampleSize 不能精确到目标尺寸）
            val scaledBitmap = scaleIfNeeded(bitmap)
            if (scaledBitmap !== bitmap) {
                bitmap.recycle()
            }
            Log.d(TAG, "Final size: ${scaledBitmap.width}x${scaledBitmap.height}")
            
            // 6. 压缩为 JPEG
            val outputStream = ByteArrayOutputStream()
            val compressed = scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            scaledBitmap.recycle()
            
            if (!compressed) {
                throw ImageEncodingException("图片压缩失败")
            }
            
            val jpegBytes = outputStream.toByteArray()
            Log.d(TAG, "Compressed size: ${jpegBytes.size / 1024}KB")
            
            // 7. Base64 编码
            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            
            // 8. 检查输出大小
            if (base64.length > MAX_BASE64_LENGTH) {
                throw ImageEncodingException("编码后图片过大，请选择较小的图片")
            }
            Log.d(TAG, "Base64 length: ${base64.length / 1024}KB")
            
            // 9. 构建 data URL
            val dataUrl = "data:image/jpeg;base64,$base64"
            Log.d(TAG, "Encoding complete")
            
            dataUrl
        } catch (e: ImageEncodingException) {
            throw e
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM while encoding image", e)
            throw ImageEncodingException("图片过大，内存不足")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode image", e)
            throw ImageEncodingException("图片处理失败: ${e.message}")
        }
    }
    
    suspend fun copyToCache(context: Context, uri: Uri, defaultExtension: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri)
            val extension = when {
                mimeType?.contains("jpeg") == true || mimeType?.contains("jpg") == true -> "jpg"
                mimeType?.contains("png") == true -> "png"
                mimeType?.contains("webp") == true -> "webp"
                mimeType?.contains("gif") == true -> "gif"
                mimeType?.contains("mp4") == true -> "mp4"
                mimeType?.contains("quicktime") == true -> "mov"
                else -> defaultExtension
            }
            
            val fileName = "media_cache_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
            val outputFile = File(context.cacheDir, fileName)
            
            val inputStream = openInputStreamFallback(context, uri) ?: return@withContext null
            inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            
            Uri.fromFile(outputFile)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy to cache: $uri", e)
            null
        }
    }
    /**
     * 获取文件大小
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return -1L
                val file = File(path)
                if (file.exists()) file.length() else -1L
            } else {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                    } else -1L
                } ?: -1L
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot query file size, will check during read", e)
            -1L
        }
    }

    /**
     * 打开图片流（带兜底）
     * 
     * 某些内容提供者可能在 openInputStream 返回 null，
     * 这里尝试使用 FileDescriptor 方式兜底。
     */
    private fun openInputStreamFallback(context: Context, uri: Uri): java.io.InputStream? {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path ?: run {
                    Log.w(TAG, "file:// URI has null path: $uri")
                    return null
                }
                val file = File(path)
                Log.d(TAG, "Opening file:// URI - path=$path, exists=${file.exists()}, canRead=${file.canRead()}, length=${file.length()}")
                
                // 如果直接路径不存在，尝试使用 canonicalPath（解决软链接问题）
                val targetFile = if (file.exists()) {
                    file
                } else {
                    val canonicalFile = file.canonicalFile
                    Log.d(TAG, "Trying canonical path: ${canonicalFile.absolutePath}, exists=${canonicalFile.exists()}")
                    if (canonicalFile.exists()) canonicalFile else null
                }
                
                if (targetFile != null && targetFile.exists()) {
                    java.io.FileInputStream(targetFile)
                } else {
                    Log.w(TAG, "File does not exist: $path (canonical: ${file.canonicalPath})")
                    null
                }
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: context.contentResolver.openFileDescriptor(uri, "r")?.let { pfd ->
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open image stream: $uri", e)
            null
        }
    }
    
    /**
     * 计算 inSampleSize
     * 
     * 目标：
     * 1. 最长边 ≤ MAX_DIMENSION
     * 2. 总像素 ≤ MAX_PIXELS
     */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        
        // 按最长边计算
        val longerSide = maxOf(width, height)
        while (longerSide / sampleSize > MAX_DIMENSION) {
            sampleSize *= 2
        }
        
        // 按总像素计算
        while ((width / sampleSize) * (height / sampleSize) > MAX_PIXELS) {
            sampleSize *= 2
        }
        
        return sampleSize
    }
    
    /**
     * 二次缩放（如果 inSampleSize 后仍超过目标）
     */
    private fun scaleIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val longerSide = maxOf(width, height)
        if (longerSide <= MAX_DIMENSION) {
            return bitmap
        }
        
        val scale = MAX_DIMENSION.toFloat() / longerSide
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 将 Bitmap 转换为 data URL（用于视频帧编码）
     * 
     * @param bitmap 输入的 Bitmap（会自动缩放和压缩）
     * @return data:image/jpeg;base64,... 格式的字符串
     * @throws ImageEncodingException 如果编码失败
     */
    suspend fun encodeBitmapToDataUrl(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Encoding bitmap: ${bitmap.width}x${bitmap.height}")
            
            // 1. 缩放（如果需要）
            val scaledBitmap = scaleIfNeeded(bitmap)
            val needRecycle = scaledBitmap !== bitmap
            Log.d(TAG, "Scaled size: ${scaledBitmap.width}x${scaledBitmap.height}")
            
            // 2. 压缩为 JPEG
            val outputStream = ByteArrayOutputStream()
            val compressed = scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            
            if (needRecycle) {
                scaledBitmap.recycle()
            }
            
            if (!compressed) {
                throw ImageEncodingException("图片压缩失败")
            }
            
            val jpegBytes = outputStream.toByteArray()
            Log.d(TAG, "Compressed size: ${jpegBytes.size / 1024}KB")
            
            // 3. Base64 编码
            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            
            // 4. 检查输出大小
            if (base64.length > MAX_BASE64_LENGTH) {
                throw ImageEncodingException("编码后图片过大")
            }
            
            // 5. 构建 data URL
            "data:image/jpeg;base64,$base64"
        } catch (e: ImageEncodingException) {
            throw e
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM while encoding bitmap", e)
            throw ImageEncodingException("图片过大，内存不足")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode bitmap", e)
            throw ImageEncodingException("图片处理失败: ${e.message}")
        }
    }
}

/**
 * 图片编码异常
 * 
 * 用于 UI 层捕获并显示友好提示
 */
class ImageEncodingException(message: String) : Exception(message)

