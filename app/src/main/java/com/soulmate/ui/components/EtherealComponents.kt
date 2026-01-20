package com.soulmate.ui.components

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soulmate.ui.theme.*

/**
 * FluidBackground: 动态流体渐变背景 (Redesigned for Red Mei Theme)
 * Now uses the Stardust and Deep Night concept from Constellation UI.
 */
@Composable
fun FluidBackground(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightInk)
    ) {
        // 1. Stardust Layer (Stars)
        val infiniteTransition = rememberInfiniteTransition(label = "stars")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse
            ), label = "alpha"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            repeat(50) {
                val x = size.width * Math.random().toFloat()
                val y = size.height * Math.random().toFloat()
                val radius = (Math.random() * 3 + 1).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = Math.random().toFloat() * 0.5f),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
        }

        // 2. Ambient Glow (Red Mei & Deep Blue)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Top Right: Red Mei Glow (Warm)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(RedMei.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width, 0f),
                    radius = 800f
                ),
                center = Offset(size.width, 0f),
                radius = 800f
            )
            // Bottom Left: Deep Blue (Cold)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Blue.copy(alpha = 0.1f), Color.Transparent),
                    center = Offset(0f, size.height),
                    radius = 600f
                ),
                center = Offset(0f, size.height),
                radius = 600f
            )
        }
    }
}

/**
 * Modifier Extension for Pulsating Effect
 */
fun Modifier.pulsate(
    enabled: Boolean = true,
    scaleFactor: Float = 1.2f,
    durationMillis: Int = 1000
): Modifier = composed {
    if (!enabled) return@composed this
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulsate")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = scaleFactor,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    this.scale(scale)
}

/**
 * GlassBubble: 毛玻璃效果容器 (Redesigned for Red Mei Theme)
 * Uses darker glass background and Champagne Gold borders.
 */
@Composable
fun GlassBubble(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(16.dp)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF1A1A2E).copy(alpha = 0.7f)) // Darker Glass
            .border(1.dp, ChampagneGold.copy(alpha = 0.3f), shape) // Gold Border
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}
