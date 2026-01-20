package com.soulmate.data.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EmotionalHealthReportGenerator - 情绪健康报告生成器
 * 
 * 功能：
 * 1. 生成月度/周度情绪健康报告
 * 2. 导出 PDF 格式报告
 * 3. 提供数据可视化支持
 */
@Singleton
class EmotionalHealthReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mindWatchService: MindWatchService,
    private val crisisInterventionManager: CrisisInterventionManager
) {
    
    companion object {
        private const val TAG = "HealthReportGenerator"
        private const val PAGE_WIDTH = 595  // A4 width in points
        private const val PAGE_HEIGHT = 842 // A4 height in points
    }
    
    /**
     * 报告类型
     */
    enum class ReportType {
        WEEKLY,     // 周报
        MONTHLY,    // 月报
        CUSTOM      // 自定义时间段
    }
    
    /**
     * 健康报告数据
     */
    data class HealthReport(
        val type: ReportType,
        val startDate: Date,
        val endDate: Date,
        val averageScore: Float,
        val emotionTrend: List<Pair<Long, Float>>,
        val crisisEvents: List<CrisisInterventionManager.CrisisEvent>,
        val status: MindWatchService.WatchStatus,
        val recommendation: String,
        val summary: String
    )
    
    /**
     * 生成周报
     */
    fun generateWeeklyReport(): HealthReport {
        return generateReport(ReportType.WEEKLY, 7)
    }
    
    /**
     * 生成月报
     */
    fun generateMonthlyReport(): HealthReport {
        return generateReport(ReportType.MONTHLY, 30)
    }
    
    /**
     * 生成报告
     */
    private fun generateReport(type: ReportType, days: Int): HealthReport {
        val endDate = Date()
        val startDate = Date(System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L)
        
        val watchReport = mindWatchService.generateReport(days)
        val emotionTrend = mindWatchService.getEmotionTrend(days)
        
        val crisisEvents = crisisInterventionManager.getCrisisHistory()
            .filter { it.timestamp >= startDate.time }
        
        val summary = generateSummary(watchReport, crisisEvents, days)
        
        return HealthReport(
            type = type,
            startDate = startDate,
            endDate = endDate,
            averageScore = watchReport.averageScore,
            emotionTrend = emotionTrend,
            crisisEvents = crisisEvents,
            status = watchReport.status,
            recommendation = watchReport.recommendation,
            summary = summary
        )
    }
    
    /**
     * 生成报告摘要
     */
    private fun generateSummary(
        watchReport: MindWatchService.WatchReport,
        crisisEvents: List<CrisisInterventionManager.CrisisEvent>,
        days: Int
    ): String {
        val sb = StringBuilder()
        
        sb.append("过去${days}天的情绪健康状况：\n\n")
        
        // 整体状态
        val statusText = when (watchReport.status) {
            MindWatchService.WatchStatus.NORMAL -> "良好"
            MindWatchService.WatchStatus.CAUTION -> "需要关注"
            MindWatchService.WatchStatus.WARNING -> "警惕"
            MindWatchService.WatchStatus.CRISIS -> "危机"
        }
        sb.append("• 整体状态：$statusText\n")
        
        // 情绪得分
        val scoreDesc = when {
            watchReport.averageScore > 2 -> "积极向上"
            watchReport.averageScore > 0 -> "基本稳定"
            watchReport.averageScore > -3 -> "有轻微波动"
            watchReport.averageScore > -5 -> "情绪低落"
            else -> "需要特别关注"
        }
        sb.append("• 情绪趋势：$scoreDesc（平均分：${String.format("%.1f", watchReport.averageScore)}）\n")
        
        // 危机事件
        if (crisisEvents.isNotEmpty()) {
            sb.append("• 需关注事件：${crisisEvents.size}次\n")
            val handledCount = crisisEvents.count { it.handled }
            sb.append("  - 已处理：$handledCount 次\n")
            sb.append("  - 待处理：${crisisEvents.size - handledCount} 次\n")
        } else {
            sb.append("• 需关注事件：无\n")
        }
        
        // 关键词统计
        if (watchReport.warningKeywordsFound.isNotEmpty()) {
            sb.append("• 出现的警示词：${watchReport.warningKeywordsFound.take(5).joinToString(", ")}\n")
        }
        
        return sb.toString()
    }
    
    /**
     * 导出报告为 PDF
     */
    fun exportToPdf(report: HealthReport): File? {
        return try {
            val document = PdfDocument()
            
            // 创建页面
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            
            drawReportContent(page.canvas, report)
            
            document.finishPage(page)
            
            // 保存文件
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "emotional_health_report_${dateFormat.format(Date())}.pdf"
            val file = File(context.getExternalFilesDir(null), fileName)
            
            FileOutputStream(file).use { outputStream ->
                document.writeTo(outputStream)
            }
            
            document.close()
            
            Log.d(TAG, "PDF exported: ${file.absolutePath}")
            file
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export PDF", e)
            null
        }
    }
    
    /**
     * 绘制报告内容到 Canvas
     */
    private fun drawReportContent(canvas: Canvas, report: HealthReport) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
        }
        
        val headerPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 16f
            isFakeBoldText = true
        }
        
        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
        }
        
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        var y = 60f
        
        // 标题
        canvas.drawText("情绪健康报告", 50f, y, titlePaint)
        y += 40f
        
        // 时间范围
        val typeText = when (report.type) {
            ReportType.WEEKLY -> "周报"
            ReportType.MONTHLY -> "月报"
            ReportType.CUSTOM -> "自定义报告"
        }
        canvas.drawText(typeText, 50f, y, headerPaint)
        y += 25f
        
        canvas.drawText(
            "${dateFormat.format(report.startDate)} - ${dateFormat.format(report.endDate)}",
            50f, y, bodyPaint
        )
        y += 40f
        
        // 状态概览
        canvas.drawText("状态概览", 50f, y, headerPaint)
        y += 25f
        
        val statusText = when (report.status) {
            MindWatchService.WatchStatus.NORMAL -> "✅ 正常"
            MindWatchService.WatchStatus.CAUTION -> "⚠️ 需要关注"
            MindWatchService.WatchStatus.WARNING -> "🔶 警告"
            MindWatchService.WatchStatus.CRISIS -> "🔴 危机"
        }
        canvas.drawText("当前状态: $statusText", 50f, y, bodyPaint)
        y += 20f
        
        canvas.drawText("平均情绪得分: ${String.format("%.1f", report.averageScore)}", 50f, y, bodyPaint)
        y += 40f
        
        // 摘要
        canvas.drawText("详细摘要", 50f, y, headerPaint)
        y += 25f
        
        // 分行绘制摘要
        val summaryLines = report.summary.split("\n")
        for (line in summaryLines) {
            if (line.isNotBlank()) {
                canvas.drawText(line, 50f, y, bodyPaint)
                y += 18f
            }
        }
        y += 20f
        
        // 建议
        canvas.drawText("建议", 50f, y, headerPaint)
        y += 25f
        
        // 处理长文本换行
        val recommendationWords = report.recommendation.chunked(40)
        for (chunk in recommendationWords) {
            canvas.drawText(chunk, 50f, y, bodyPaint)
            y += 18f
        }
        y += 30f
        
        // 危机事件
        if (report.crisisEvents.isNotEmpty()) {
            canvas.drawText("需关注事件记录", 50f, y, headerPaint)
            y += 25f
            
            val eventDateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            for (event in report.crisisEvents.take(5)) {
                val time = eventDateFormat.format(Date(event.timestamp))
                val handled = if (event.handled) "✓" else "○"
                canvas.drawText(
                    "$handled $time - ${event.keywords.take(2).joinToString(", ")}",
                    60f, y, bodyPaint
                )
                y += 18f
            }
        }
        
        // 页脚
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
        }
        canvas.drawText(
            "由 SoulMate MindWatch 生成 | ${dateFormat.format(Date())}",
            50f, PAGE_HEIGHT - 30f, footerPaint
        )
    }
    
    /**
     * 获取报告保存目录
     */
    fun getReportDirectory(): File? {
        return context.getExternalFilesDir("reports")?.also {
            if (!it.exists()) it.mkdirs()
        }
    }
    
    /**
     * 列出已保存的报告
     */
    fun listSavedReports(): List<File> {
        return getReportDirectory()
            ?.listFiles { file -> file.name.endsWith(".pdf") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
}
