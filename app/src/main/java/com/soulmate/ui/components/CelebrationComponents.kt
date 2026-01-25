package com.soulmate.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.soulmate.ui.theme.SoulMateTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 惊喜时刻庆祝弹窗 (Pop-up Celebration)
 * 
 * 用于纪念日、升级或主要成就的庆祝。
 */
@Composable
fun PopUpCelebration(
    visible: Boolean,
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f) // Ensure it's on top of everything
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Confetti Effect
        ConfettiEffect(modifier = Modifier.fillMaxSize())

        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.5f) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            GlassBubble(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                backgroundColor = SoulMateTheme.colors.cardBg.copy(alpha = 0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Cake,
                        contentDescription = null,
                        tint = SoulMateTheme.colors.accentColor,
                        modifier = Modifier.size(64.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = SoulMateTheme.colors.textPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SoulMateTheme.colors.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SoulMateTheme.colors.accentColor
                        )
                    ) {
                        Text("一起庆祝!", color = Color.White)
                    }
                }
            }
        }
        
        // Close button top right
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.Close, null, tint = Color.White)
        }
    }
}

/**
 * 简单的粒子撒花效果
 */
@Composable
fun ConfettiEffect(modifier: Modifier = Modifier) {
    val particles = remember { List(50) { ConfettiParticle() } }
    
    Canvas(modifier = modifier) {
        particles.forEach { particle ->
            // Update logic would typically be in a LaunchedEffect loop for smooth animation
            // simplified here for static preview or simple loop
            // In a real implementation, use a frame loop
            drawCircle(
                color = particle.color,
                radius = 8.dp.toPx(),
                center = Offset(
                    x = size.width * particle.x,
                    y = size.height * particle.y
                )
            )
        }
    }
    
    // Animate particles
    LaunchedEffect(Unit) {
        while(true) {
            particles.forEach { it.update() }
            delay(16)
        }
    }
}

private class ConfettiParticle {
    var x = Random.nextFloat()
    var y = Random.nextFloat() * -1f // Start above
    var speed = Random.nextFloat() * 0.01f + 0.002f
    val color = listOf(
        Color(0xFF00E5FF), 
        Color(0xFFD500F9), 
        Color(0xFFFFD700),
        Color(0xFFFF4081)
    ).random()
    
    fun update() {
        y += speed
        if (y > 1.2f) {
            y = -0.2f
            x = Random.nextFloat()
        }
    }
}
