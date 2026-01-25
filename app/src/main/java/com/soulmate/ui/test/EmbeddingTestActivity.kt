package com.soulmate.ui.test

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.soulmate.BuildConfig
import com.soulmate.R
import com.soulmate.core.data.brain.DoubaoEmbeddingService
import com.soulmate.core.data.brain.EmbeddingException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Embedding 服务测试 Activity
 * 
 * 用于测试向量模型（Embedding）的配置和功能：
 * - 显示当前配置（API Key、端点 ID）
 * - 测试 Embedding API 调用
 * - 显示测试结果（向量维度、耗时、错误信息）
 */
@AndroidEntryPoint
class EmbeddingTestActivity : ComponentActivity() {

    @Inject
    lateinit var embeddingService: DoubaoEmbeddingService

    private lateinit var tvConfig: TextView
    private lateinit var tvResult: TextView
    private lateinit var tvLogs: TextView
    private lateinit var etTestText: EditText
    private lateinit var btnTest: Button
    private lateinit var btnTestDefault: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_embedding_test)

        tvConfig = findViewById(R.id.tvConfig)
        tvResult = findViewById(R.id.tvResult)
        tvLogs = findViewById(R.id.tvLogs)
        etTestText = findViewById(R.id.etTestText)
        btnTest = findViewById(R.id.btnTest)
        btnTestDefault = findViewById(R.id.btnTestDefault)

        // 显示配置信息
        displayConfiguration()

        // 测试按钮 - 使用自定义文本
        btnTest.setOnClickListener {
            val text = etTestText.text.toString().trim()
            if (text.isEmpty()) {
                appendLog("❌ 请输入测试文本")
            } else {
                testEmbedding(text)
            }
        }

        // 测试按钮 - 使用默认文本
        btnTestDefault.setOnClickListener {
            testEmbedding("你好，这是一个测试文本")
        }
    }

    /**
     * 显示当前配置信息
     */
    private fun displayConfiguration() {
        val configInfo = buildString {
            appendLine("📋 Embedding 配置信息")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            
            // API Key
            val embeddingApiKey = try {
                val field = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_API_KEY")
                field.get(null) as? String ?: ""
            } catch (e: Exception) {
                ""
            }
            
            val doubaoApiKey = BuildConfig.DOUBAO_API_KEY
            
            val apiKey = if (embeddingApiKey.isNotEmpty()) {
                "DOUBAO_EMBEDDING_API_KEY (长度: ${embeddingApiKey.length})"
            } else if (doubaoApiKey.isNotEmpty()) {
                "DOUBAO_API_KEY (长度: ${doubaoApiKey.length})"
            } else {
                "❌ 未配置"
            }
            
            appendLine("API Key: $apiKey")
            
            // 端点/模型 ID
            val embeddingEndpointId = try {
                val field = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_ENDPOINT_ID")
                field.get(null) as? String ?: ""
            } catch (e: Exception) {
                ""
            }
            
            val embeddingModelId = try {
                val field = BuildConfig::class.java.getDeclaredField("DOUBAO_EMBEDDING_MODEL_ID")
                field.get(null) as? String ?: ""
            } catch (e: Exception) {
                ""
            }
            
            val endpointOrModel = when {
                embeddingEndpointId.isNotEmpty() -> "DOUBAO_EMBEDDING_ENDPOINT_ID: $embeddingEndpointId"
                embeddingModelId.isNotEmpty() -> "DOUBAO_EMBEDDING_MODEL_ID: $embeddingModelId"
                else -> "使用默认值: doubao-embedding"
            }
            
            appendLine("端点/模型: $endpointOrModel")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
        
        tvConfig.text = configInfo
        appendLog("配置信息已加载")
    }

    /**
     * 测试 Embedding API
     */
    private fun testEmbedding(text: String) {
        appendLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLog("🚀 开始测试 Embedding API")
        appendLog("测试文本: \"$text\"")
        appendLog("文本长度: ${text.length} 字符")
        
        btnTest.isEnabled = false
        btnTestDefault.isEnabled = false
        tvResult.text = "测试中..."
        
        val startTime = System.currentTimeMillis()
        
        lifecycleScope.launch {
            try {
                val embedding = embeddingService.embed(text)
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                // 计算向量统计信息
                val min = embedding.minOrNull() ?: 0f
                val max = embedding.maxOrNull() ?: 0f
                val avg = embedding.average().toFloat()
                
                // 计算非零值数量
                val nonZeroCount = embedding.count { it != 0f }
                
                val resultInfo = buildString {
                    appendLine("✅ 测试成功！")
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine("向量维度: ${embedding.size}")
                    appendLine("耗时: ${duration}ms")
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine("向量统计:")
                    appendLine("  - 最小值: $min")
                    appendLine("  - 最大值: $max")
                    appendLine("  - 平均值: $avg")
                    appendLine("  - 非零值: $nonZeroCount / ${embedding.size}")
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine("前 10 个向量值:")
                    embedding.take(10).forEachIndexed { index, value ->
                        appendLine("  [$index] = $value")
                    }
                    if (embedding.size > 10) {
                        appendLine("  ... (共 ${embedding.size} 维)")
                    }
                }
                
                tvResult.text = resultInfo
                appendLog("✅ Embedding 生成成功")
                appendLog("   维度: ${embedding.size}, 耗时: ${duration}ms")
                appendLog("   统计: min=$min, max=$max, avg=$avg")
                
            } catch (e: EmbeddingException) {
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                val errorInfo = buildString {
                    appendLine("❌ 测试失败")
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine("错误类型: EmbeddingException")
                    appendLine("错误信息: ${e.message}")
                    e.cause?.let { cause ->
                        appendLine("原因: ${cause.javaClass.simpleName}")
                        appendLine("原因详情: ${cause.message}")
                    }
                    appendLine("耗时: ${duration}ms")
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine("💡 排查建议:")
                    when {
                        e.message?.contains("401") == true -> {
                            appendLine("  - 检查 API Key 是否正确")
                            appendLine("  - 确认 API Key 是否有 Embedding 服务权限")
                        }
                        e.message?.contains("404") == true -> {
                            appendLine("  - 检查端点 ID 是否正确")
                            appendLine("  - 在火山引擎控制台确认端点状态")
                        }
                        e.message?.contains("网络") == true -> {
                            appendLine("  - 检查网络连接")
                            appendLine("  - 确认可以访问 ark.cn-beijing.volces.com")
                        }
                        e.message?.contains("API Key 未配置") == true -> {
                            appendLine("  - 在 local.properties 中设置 DOUBAO_EMBEDDING_API_KEY")
                            appendLine("  - 或设置 DOUBAO_API_KEY 作为回退")
                        }
                        else -> {
                            appendLine("  - 查看 Logcat 中的详细错误日志")
                            appendLine("  - 搜索关键字: DoubaoEmbeddingService")
                        }
                    }
                }
                
                tvResult.text = errorInfo
                appendLog("❌ Embedding 生成失败: ${e.message}")
                e.cause?.let { cause ->
                    appendLog("   原因: ${cause.javaClass.simpleName} - ${cause.message}")
                }
                
            } catch (e: Exception) {
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                val errorInfo = buildString {
                    appendLine("❌ 测试失败")
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine("错误类型: ${e.javaClass.simpleName}")
                    appendLine("错误信息: ${e.message}")
                    e.cause?.let { cause ->
                        appendLine("原因: ${cause.javaClass.simpleName}")
                        appendLine("原因详情: ${cause.message}")
                    }
                    appendLine("耗时: ${duration}ms")
                }
                
                tvResult.text = errorInfo
                appendLog("❌ 未知错误: ${e.javaClass.simpleName} - ${e.message}")
                e.printStackTrace()
            } finally {
                btnTest.isEnabled = true
                btnTestDefault.isEnabled = true
            }
        }
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val logLine = "$time: $msg\n"
        runOnUiThread {
            tvLogs.append(logLine)
            // 自动滚动到底部
            val scrollView = findViewById<android.widget.ScrollView>(R.id.svLogs)
            scrollView.post {
                scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
            Log.d("EmbeddingTestActivity", msg)
        }
    }
}
