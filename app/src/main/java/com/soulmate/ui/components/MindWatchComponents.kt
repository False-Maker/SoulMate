package com.soulmate.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soulmate.data.service.MindWatchService
import com.soulmate.ui.theme.SoulMateTheme

/**
 * 共鸣光球 (Resonance Orb)
 * 
 * 一个动态呼吸的光球，颜色随情绪状态变化。
 * 直观展示 AI 对用户情绪的感知（共鸣）。
 */
/**
 * 共鸣光球 (Resonance Orb - Ethereal Design)
 *
 * 一个极具生命感的灵动光球，由核心、星云、光晕和粒子组成。
 * 颜色随 MindWatch 情绪状态流转。
 */
@Composable
fun ResonanceOrb(
    status: MindWatchService.WatchStatus,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    // 1. 颜色定义 (定义基调色) - 调整为更融合背景的配色
    val baseColor = when (status) {
        MindWatchService.WatchStatus.NORMAL -> Color(0xFF3E6CEB)    // EtherealBlue (Deep & Calm)
        MindWatchService.WatchStatus.CAUTION -> Color(0xFFFFD700)   // Gold
        MindWatchService.WatchStatus.WARNING -> Color(0xFFFF8C00)   // Orange
        MindWatchService.WatchStatus.CRISIS -> Color(0xFFFF4500)    // Red
    }

    // 辅助色 (用于渐变)
    val secondaryColor = when (status) {
        MindWatchService.WatchStatus.NORMAL -> Color(0xFF322368)    // EtherealViolet (Darker, blends with BG)
        MindWatchService.WatchStatus.CAUTION -> Color(0xFFFF4081)   // Pink
        MindWatchService.WatchStatus.WARNING -> Color(0xFFFFD700)   // Gold
        MindWatchService.WatchStatus.CRISIS -> Color(0xFF8B0000)    // Dark Red
    }

    // 动画状态
    val colorState by animateColorAsState(targetValue = baseColor, animationSpec = tween(2000), label = "BaseColor")
    val secColorState by animateColorAsState(targetValue = secondaryColor, animationSpec = tween(2000), label = "SecColor")

    val infiniteTransition = rememberInfiniteTransition(label = "OrbAnim")

    // 动画 1: 核心呼吸 (快速搏动)
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CorePulse"
    )

    // 动画 2: 星云旋转 (慢速)
    val nebulaRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "NebulaRot"
    )

    // 动画 3: 光晕呼吸 (极慢速)
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HaloBreath"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: 外部光晕 (Halo) - 极柔和背景，降低透明度
        Canvas(modifier = Modifier.size(size)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colorState.copy(alpha = haloAlpha * 0.6f), // 降低透明度
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.toPx() / 2
                ),
                radius = size.toPx() / 2
            )
        }

        // Layer 2: 星云层 (Nebula Gradient) - 降低不透明度以融入背景
        Canvas(modifier = Modifier
            .size(size * 0.85f)
            .graphicsLayer { rotationZ = nebulaRotation }
        ) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        colorState.copy(alpha = 0.2f), // Lower alpha
                        secColorState.copy(alpha = 0.4f),
                        colorState.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                )
            )
        }

        
        // Layer 3: 反向星云层 (Counter Nebula) - 反向旋转
        Canvas(modifier = Modifier
            .size(size * 0.7f)
            .graphicsLayer { rotationZ = -nebulaRotation * 1.5f }
        ) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        secColorState.copy(alpha = 0.3f),
                        Color.Transparent,
                        colorState.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
        }

        // Layer 4: 核心 (Core) - 高亮搏动
        Canvas(modifier = Modifier.size(size * 0.4f)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        colorState.copy(alpha = 0.8f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = (size.toPx() * 0.4f / 2)
                ),
                radius = (size.toPx() * 0.4f / 2) * corePulse
            )
        }
        
        // Layer 5: 粒子装饰 (简化版)
        // 实际上可以用随机点，这里用几个固定的高亮轨道点模拟
        Canvas(modifier = Modifier
            .size(size * 0.9f)
            .graphicsLayer { rotationZ = nebulaRotation * 0.5f }
        ) {
            val r = size.toPx() * 0.9f / 2
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = 2.dp.toPx(),
                center = center + Offset(r * 0.8f, 0f)
            )
            drawCircle(
                color = secColorState.copy(alpha = 0.5f),
                radius = 3.dp.toPx(),
                center = center + Offset(-r * 0.6f, r * 0.5f)
            )
        }
    }
}

/**
 * 关怀卡片 (Care Card)
 * 
 * 当 MindWatch 检测到异常状态时，在聊天流中插入的特殊卡片。
 * 提供情感支持和求助入口。
 */
@Composable
fun CareCard(
    status: MindWatchService.WatchStatus,
    message: String,
    onCallHelp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, title, color) = when (status) {
        MindWatchService.WatchStatus.CRISIS -> Triple(Icons.Default.Phone, "深度守护", Color(0xFFFF4500)) // Red-Orange
        MindWatchService.WatchStatus.WARNING -> Triple(Icons.Default.Warning, "共鸣阴雨", Color(0xFFFF8C00)) // Orange
        else -> Triple(Icons.Default.Favorite, "灵犀提示", SoulMateTheme.colors.accentColor)
    }

    GlassBubble(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = color.copy(alpha = 0.15f),
        borderColor = color.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = SoulMateTheme.colors.textPrimary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row {
                Button(
                    onClick = onCallHelp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = color,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (status == MindWatchService.WatchStatus.CRISIS) "寻求帮助" else "倾诉")
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = SoulMateTheme.colors.textSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SoulMateTheme.colors.cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("我没事")
                }
            }
        }
    }
}
