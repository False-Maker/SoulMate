package com.soulmate.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soulmate.data.service.MindWatchService
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun ResonanceOrb(
    amplitude: Float,
    status: MindWatchService.WatchStatus,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val clamped = amplitude.coerceIn(0f, 1f)
    val smoothAmplitude by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "ResonanceOrbAmplitude"
    )

    val (baseTarget, accentTarget) = when (status) {
        MindWatchService.WatchStatus.NORMAL -> Color(0xFF46E3D6) to Color(0xFFB6FFF6)
        MindWatchService.WatchStatus.CAUTION -> Color(0xFFFFD37A) to Color(0xFFFF7AD9)
        MindWatchService.WatchStatus.WARNING -> Color(0xFFFF7A3D) to Color(0xFF9A5BFF)
        MindWatchService.WatchStatus.CRISIS -> Color(0xFFFF3B5E) to Color(0xFF6B2CFF)
    }

    val baseColor by animateColorAsState(
        targetValue = baseTarget,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "ResonanceOrbBaseColor"
    )
    val accentColor by animateColorAsState(
        targetValue = accentTarget,
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "ResonanceOrbAccentColor"
    )

    val infinite = rememberInfiniteTransition(label = "ResonanceOrbInfinite")
    val slowBreath by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ResonanceOrbBreath"
    )
    val pulsePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ResonanceOrbPulsePhase"
    )
    val particleRotationDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ResonanceOrbParticleRotation"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val drawSize = this.size
            val minDim = min(drawSize.width, drawSize.height)
            val coreBase = minDim * 0.18f
            val breath = 0.92f + 0.08f * slowBreath
            val ampBoost = 0.92f + 0.22f * smoothAmplitude

            val coreRadius = coreBase * breath * ampBoost
            val innerGlowRadius = minDim * (0.28f + 0.08f * smoothAmplitude) * breath
            val outerPulseRadius = minDim * (0.44f + 0.26f * smoothAmplitude) * (0.92f + 0.18f * slowBreath)

            val pulse = 1f - pulsePhase
            val outerAlpha = (0.10f + 0.60f * smoothAmplitude) * (0.35f + 0.65f * pulse)
            val innerAlpha = 0.22f + 0.55f * smoothAmplitude

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        baseColor.copy(alpha = outerAlpha),
                        accentColor.copy(alpha = outerAlpha * 0.25f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerPulseRadius
                ),
                radius = outerPulseRadius
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = innerAlpha * 0.65f),
                        baseColor.copy(alpha = innerAlpha * 0.22f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = innerGlowRadius
                ),
                radius = innerGlowRadius
            )

            val coreColor = Color(0xFFFFF8EC)
            drawCircle(
                color = coreColor.copy(alpha = 0.95f),
                radius = coreRadius,
                center = center
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.75f),
                        Color.Transparent
                    ),
                    center = center + Offset(-coreRadius * 0.22f, -coreRadius * 0.22f),
                    radius = coreRadius * 1.15f
                ),
                radius = coreRadius * 1.15f,
                center = center
            )

            val ringRadius = minDim * (0.52f + 0.08f * smoothAmplitude)
            val particleBaseAlpha = (0.10f + 0.55f * smoothAmplitude)
            val particleColor = lerp(baseColor, Color.White, 0.62f)
            val count = 12
            for (i in 0 until count) {
                val angleBase = particleRotationDeg + i * 360f / count + i * 17f
                val angleRad = Math.toRadians(angleBase.toDouble())
                val driftBase = particleRotationDeg + i * 31f
                val drift = 0.92f + 0.08f * sin(Math.toRadians(driftBase.toDouble())).toFloat()
                val ringRadiusF: Float = ringRadius
                val driftF: Float = drift
                val cosF = cos(angleRad).toFloat()
                val sinF = sin(angleRad).toFloat()
                val p = Offset(
                    x = cosF * ringRadiusF * driftF,
                    y = sinF * ringRadiusF * driftF
                )
                val flickerBase = particleRotationDeg * 1.6f + i * 47f
                val flicker = (0.5f + 0.5f * sin(Math.toRadians(flickerBase.toDouble())).toFloat())
                    .coerceIn(0.15f, 1f)
                val r = (1.2f + (i % 3) * 0.6f) * 1.dp.toPx()
                drawCircle(
                    color = particleColor.copy(alpha = particleBaseAlpha * flicker),
                    radius = r,
                    center = center + p
                )
            }
        }
    }
}

@Composable
fun ResonanceOrb(
    status: MindWatchService.WatchStatus,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    ResonanceOrb(
        amplitude = 0f,
        status = status,
        modifier = modifier,
        size = size
    )
}
