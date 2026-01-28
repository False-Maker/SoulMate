package com.soulmate.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.soulmate.data.service.CrisisInterventionManager
import com.soulmate.data.service.EmotionalHealthReportGenerator
import com.soulmate.data.service.MindWatchService
import com.soulmate.ui.components.GlassBubble
import com.soulmate.ui.components.ParticleBackground
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.viewmodel.SafetyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EmotionalReportScreen(
    viewModel: SafetyViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val reportState by viewModel.reportState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()

    // 自动加载周报
    LaunchedEffect(Unit) {
        viewModel.loadWeeklyReport()
    }

    // 处理导出结果
    LaunchedEffect(exportResult) {
        exportResult?.let { file ->
            // 分享文件
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出共鸣周报"))
            viewModel.clearExportResult()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SoulMateTheme.colors.bgGradientStart,
                        SoulMateTheme.colors.bgGradientEnd
                    )
                )
            )
    ) {
        ParticleBackground(
            modifier = Modifier.fillMaxSize(),
            particleColor = SoulMateTheme.colors.particleColor,
            lineColor = SoulMateTheme.colors.cardBorder
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(SoulMateTheme.colors.cardBg, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = SoulMateTheme.colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "共鸣周报",
                    style = MaterialTheme.typography.displaySmall,
                    color = SoulMateTheme.colors.textPrimary
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Export Button
                IconButton(
                    onClick = { viewModel.exportPdf() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(SoulMateTheme.colors.accentColor.copy(alpha = 0.2f), CircleShape)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = SoulMateTheme.colors.accentColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "导出 PDF",
                            tint = SoulMateTheme.colors.accentColor
                        )
                    }
                }
            }

            if (reportState == null && isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SoulMateTheme.colors.accentColor)
                }
            } else {
                reportState?.let { report ->
                    ReportContent(report)
                }
            }
        }
    }
}

@Composable
private fun ReportContent(report: EmotionalHealthReportGenerator.HealthReport) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Status Overview
        StatusOverviewCard(report)

        // 2. Emotion Trend Chart
        Text(
            text = "情绪走势 (近7天)",
            style = MaterialTheme.typography.titleMedium,
            color = SoulMateTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 8.dp)
        )
        EmotionTrendChart(report.emotionTrend)

        // 3. Heatmap
        Text(
            text = "情绪热力概览",
            style = MaterialTheme.typography.titleMedium,
            color = SoulMateTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 8.dp)
        )
        // Heatmap simplified as a row of days for weekly report
        WeeklyHeatmap(report.emotionTrend)

        // 4. Crisis Timeline
        if (report.crisisEvents.isNotEmpty()) {
            Text(
                text = "关键事件记录",
                style = MaterialTheme.typography.titleMedium,
                color = SoulMateTheme.colors.textPrimary,
                modifier = Modifier.padding(start = 8.dp)
            )
            CrisisTimeline(report.crisisEvents)
        }

        // 5. Summary & Recommendation
        GlassBubble(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "灵犀寄语",
                    style = MaterialTheme.typography.titleSmall,
                    color = SoulMateTheme.colors.accentColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = report.recommendation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoulMateTheme.colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun StatusOverviewCard(report: EmotionalHealthReportGenerator.HealthReport) {
    val statusColor = when (report.status) {
        MindWatchService.WatchStatus.NORMAL -> Color(0xFF4CAF50)
        MindWatchService.WatchStatus.CAUTION -> Color(0xFFFFC107)
        MindWatchService.WatchStatus.WARNING -> Color(0xFFFF9800)
        MindWatchService.WatchStatus.CRISIS -> Color(0xFFF44336)
    }
    
    val statusText = when (report.status) {
        MindWatchService.WatchStatus.NORMAL -> "状态良好"
        MindWatchService.WatchStatus.CAUTION -> "需要关注"
        MindWatchService.WatchStatus.WARNING -> "警惕"
        MindWatchService.WatchStatus.CRISIS -> "危机干预"
    }

    GlassBubble(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(statusColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(statusColor, CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = "平均情绪分: ${String.format("%.1f", report.averageScore)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoulMateTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun EmotionTrendChart(trend: List<Pair<Long, Float>>) {
    GlassBubble(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (trend.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = SoulMateTheme.colors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "暂无情绪数据",
                        color = SoulMateTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "开始对话后将自动记录",
                        color = SoulMateTheme.colors.textSecondary.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            return@GlassBubble
        }

        Canvas(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {
            
            val width = size.width
            val height = size.height
            val points = trend.map { it.second }
            
            // Y-axis scaling: -10 to 10 mapped to height to 0
            val maxScore = 10f
            val minScore = -10f
            val range = maxScore - minScore
            
            // Draw background zones (Green/Yellow/Red)
            // Top (0 to 10) -> Green
            drawRect(
                color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(width, height * 0.5f)
            )
            // Bottom (-10 to 0) -> Red/Orange
            drawRect(
                color = Color(0xFFF44336).copy(alpha = 0.1f),
                topLeft = Offset(0f, height * 0.5f),
                size = androidx.compose.ui.geometry.Size(width, height * 0.5f)
            )
            
            // Zero line
            drawLine(
                color = Color.Gray.copy(alpha = 0.5f),
                start = Offset(0f, height * 0.5f),
                end = Offset(width, height * 0.5f),
                strokeWidth = 2f
            )

            // 单点情况：只画一个点和标签
            if (points.size == 1) {
                val score = points[0]
                val normalizedY = 1f - (score - minScore) / range
                val x = width / 2  // 居中显示
                val y = normalizedY * height
                
                // Draw larger point for single data
                drawCircle(
                    color = if (score >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    radius = 8.dp.toPx(),
                    center = Offset(x, y)
                )
                // Draw outer ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = 12.dp.toPx(),
                    center = Offset(x, y),
                    style = Stroke(width = 2.dp.toPx())
                )
                return@Canvas
            }

            val stepX = width / (points.size - 1)
            
            val path = Path()
            points.forEachIndexed { index, score ->
                // Normalize score to 0..1 (flipped Y)
                // 10 -> 0, -10 -> height
                val normalizedY = 1f - (score - minScore) / range
                val x = index * stepX
                val y = normalizedY * height
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    // Cubic Bezier for smooth curve
                    val prevX = (index - 1) * stepX
                    val prevY = (1f - (points[index - 1] - minScore) / range) * height
                    
                    val conX1 = prevX + stepX / 2
                    val conY1 = prevY
                    val conX2 = prevX + stepX / 2
                    val conY2 = y
                    
                    path.cubicTo(conX1, conY1, conX2, conY2, x, y)
                }
                
                // Draw point
                drawCircle(
                    color = if (score >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            }
            
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun WeeklyHeatmap(trend: List<Pair<Long, Float>>) {
    val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        trend.forEach { (timestamp, score) ->
            val color = when {
                score > 5 -> Color(0xFF2E7D32) // Deep Green
                score > 0 -> Color(0xFF4CAF50) // Light Green
                score > -2 -> Color(0xFFFFC107) // Yellow
                score > -5 -> Color(0xFFFF9800) // Orange
                else -> Color(0xFFC62828) // Red
            }
            
            GlassBubble(
                modifier = Modifier.width(60.dp),
                shape = RoundedCornerShape(8.dp),
                backgroundColor = color.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dateFormat.format(Date(timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = SoulMateTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(color, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format("%.1f", score),
                        style = MaterialTheme.typography.labelSmall,
                        color = SoulMateTheme.colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun CrisisTimeline(events: List<CrisisInterventionManager.CrisisEvent>) {
    Column {
        val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        events.forEachIndexed { index, event ->
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                // Timeline Line
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(32.dp)
                ) {
                    val dotColor = if (event.level >= 3) Color.Red else Color.Yellow
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(dotColor, CircleShape)
                    )
                    if (index < events.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .weight(1f)
                                .background(Color.Gray.copy(alpha = 0.5f))
                        )
                    }
                }
                
                // Event Card
                GlassBubble(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = dateFormat.format(Date(event.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = SoulMateTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "触发: ${event.keywords.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SoulMateTheme.colors.textPrimary
                        )
                        if (event.handled) {
                            Text(
                                text = "✓ 已干预",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        } else {
                            Text(
                                text = "! 待处理",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                }
            }
        }
    }
}
