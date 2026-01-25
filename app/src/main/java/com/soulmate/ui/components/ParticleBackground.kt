package com.soulmate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.sqrt
import kotlin.random.Random

data class Particle(
    var x: Float,
    var y: Float,
    val size: Float,
    val speedX: Float,
    val speedY: Float,
    var life: Float,
    var opacity: Float
)

/**
 * 粒子背景组件 - 带生命周期感知的省电优化版
 * 
 * 优化点：
 * - 使用 LocalLifecycleOwner 监听生命周期
 * - 仅在页面 RESUMED（可见）时运行动画
 * - 应用退到后台时自动暂停，节省 CPU 和电量
 * 
 * @param modifier 修饰符
 * @param particleColor 粒子颜色
 * @param lineColor 连线颜色
 */
@Composable
fun ParticleBackground(
    modifier: Modifier = Modifier,
    particleColor: Color,
    lineColor: Color
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val particles = remember { mutableListOf<Particle>() }
    
    // 获取生命周期所有者
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 初始化粒子
    LaunchedEffect(size) {
        if (size.width > 0 && particles.isEmpty()) {
            repeat(50) {
                particles.add(
                    Particle(
                        x = Random.nextFloat() * size.width,
                        y = Random.nextFloat() * size.height,
                        size = Random.nextFloat() * 4f + 1f,
                        speedX = Random.nextFloat() - 0.5f,
                        speedY = Random.nextFloat() - 0.5f,
                        life = Random.nextFloat(),
                        opacity = Random.nextFloat() * 0.5f + 0.1f
                    )
                )
            }
        }
    }

    // 动画循环 (Lifecycle Aware)
    // 优化：仅在 RESUMED 状态下运行动画，节省电量
    val frameState = remember { mutableStateOf(0L) }
    
    LaunchedEffect(lifecycleOwner) {
        // 使用 repeatOnLifecycle 确保只在 RESUMED 状态运行
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                withFrameNanos {
                    frameState.value = it
                }
            }
        }
    }

    Canvas(
        modifier = modifier.onSizeChanged { size = it }
    ) {
        // 读取状态触发重绘
        frameState.value 

        particles.forEach { p ->
            // 更新逻辑
            p.x += p.speedX
            p.y += p.speedY
            p.life += 0.01f
            p.opacity = 0.3f + kotlin.math.sin(p.life) * 0.2f

            // 边界检查
            if (p.x > size.width) p.x = 0f
            if (p.x < 0) p.x = size.width.toFloat()
            if (p.y > size.height) p.y = 0f
            if (p.y < 0) p.y = size.height.toFloat()

            // 绘制粒子
            drawCircle(
                color = particleColor.copy(alpha = p.opacity.coerceIn(0f, 1f)),
                center = Offset(p.x, p.y),
                radius = p.size
            )
        }

        // 绘制连线 (Constellation effect)
        // 性能优化：距离判断平方，减少开方运算
        for (i in 0 until particles.size) {
            for (j in i + 1 until particles.size) {
                val p1 = particles[i]
                val p2 = particles[j]
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                val distSq = dx * dx + dy * dy
                
                // 距离阈值 (例如 120px ^ 2 = 14400)
                if (distSq < 14400) {
                    val alpha = (1f - sqrt(distSq) / 120f) * 0.15f
                    if (alpha > 0) {
                        drawLine(
                            color = lineColor.copy(alpha = alpha.coerceIn(0f, 1f)),
                            start = Offset(p1.x, p1.y),
                            end = Offset(p2.x, p2.y),
                            strokeWidth = 2f
                        )
                    }
                }
            }
        }
    }
}
