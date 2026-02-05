package com.soulmate.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.soulmate.ui.theme.SoulMateTheme

/**
 * 带有 3D 视差效果的玻璃卡片
 * 
 * 交互效果：
 * - 手指按压卡片时，卡片向手指方向倾斜（符合物理直觉）
 * - 手指在卡片上移动时，倾斜角度跟随手指位置实时更新
 * - 手指离开时，卡片平滑复位至水平
 * 
 * 计算逻辑：
 * - offX = (touchX - centerX) / centerX  -> 水平偏移比例 [-1, 1]
 * - offY = (touchY - centerY) / centerY  -> 垂直偏移比例 [-1, 1]
 * - rotationY = offX * 10f  (左右倾斜)
 * - rotationX = -offY * 10f (上下倾斜，取负是因为坐标系方向)
 */
@Composable
fun ParallaxGlassCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    borderColor: Color,
    glowColor: Color = Color.Transparent,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    // 目标旋转角度（基于手指位置计算）
    var targetRotationX by remember { mutableFloatStateOf(0f) }
    var targetRotationY by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }
    
    // 组件尺寸（用于计算中心点）
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    
    // 平滑动画：手指移动时快速跟随，离开时平滑复位
    val animatedRotationX by animateFloatAsState(
        targetValue = targetRotationX,
        animationSpec = tween(durationMillis = if (isPressed) 50 else 300),
        label = "rotationX"
    )
    val animatedRotationY by animateFloatAsState(
        targetValue = targetRotationY,
        animationSpec = tween(durationMillis = if (isPressed) 50 else 300),
        label = "rotationY"
    )

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                componentSize = size
            }
            .graphicsLayer {
                rotationX = animatedRotationX
                rotationY = animatedRotationY
                cameraDistance = 12f * density
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        
                        when (event.type) {
                            PointerEventType.Press, PointerEventType.Move -> {
                                // 获取第一个触摸点
                                val position = event.changes.firstOrNull()?.position
                                if (position != null && componentSize.width > 0 && componentSize.height > 0) {
                                    isPressed = true
                                    
                                    // 计算中心点
                                    val centerX = componentSize.width / 2f
                                    val centerY = componentSize.height / 2f
                                    
                                    // 计算偏移比例 [-1, 1]
                                    val offX = (position.x - centerX) / centerX
                                    val offY = (position.y - centerY) / centerY
                                    
                                    // 限制范围并映射到旋转角度
                                    // rotationY: 正值向右倾，负值向左倾
                                    // rotationX: 正值向上倾（顶部远离），负值向下倾（底部远离）
                                    targetRotationY = offX.coerceIn(-1f, 1f) * 10f
                                    targetRotationX = -offY.coerceIn(-1f, 1f) * 10f
                                }
                            }
                            PointerEventType.Release, PointerEventType.Exit -> {
                                // 手指离开，复位至水平
                                isPressed = false
                                targetRotationX = 0f
                                targetRotationY = 0f
                            }
                        }
                    }
                }
            }
            .shadow(
                elevation = if (isPressed) 16.dp else 8.dp,
                spotColor = glowColor,
                ambientColor = glowColor,
                shape = RoundedCornerShape(24.dp)
            )
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        borderColor.copy(alpha = 0.6f),
                        borderColor.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
    ) {
        content()
        
        // 模拟光泽扫过效果 (静态或简单动画)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.0f),
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.0f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
        )
    }
}

@Composable
fun GlassBubble(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = SoulMateTheme.colors.cardBg,
    borderColor: Color = SoulMateTheme.colors.cardBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    Box(
        modifier = clickableModifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(1.dp, borderColor, shape)
    ) {
        content()
    }
}

@Composable
fun GlassMemoryCard(
    date: String,
    summary: String,
    imageUrls: List<String>,
    modifier: Modifier = Modifier
) {
    GlassBubble(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = SoulMateTheme.colors.cardBg.copy(alpha = 0.8f),
        borderColor = SoulMateTheme.colors.cardBorder
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelMedium,
                color = SoulMateTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = SoulMateTheme.colors.textPrimary
            )
            if (imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    imageUrls.take(3).forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
    }
}
