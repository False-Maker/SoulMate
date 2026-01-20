package com.soulmate.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soulmate.ui.theme.*
import kotlinx.coroutines.delay

// -----------------------------------------------------------------------------
// 🧠 Data Model
// -----------------------------------------------------------------------------
data class MemoryNode(
    val id: Int,
    val date: String,
    val title: String,
    val content: String,
    val type: MemoryType,
    val xPercent: Float, // 0.0 - 1.0
    val yPercent: Float  // 0.0 - 1.0
)

enum class MemoryType { CORE, NORMAL, NEW }

// -----------------------------------------------------------------------------
// 🎨 Components
// -----------------------------------------------------------------------------

@Composable
fun StardustBackground() {
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
}

@Composable
fun AmbientGlow() {
    // 模拟红梅树和深蓝夜空的氛围光
    Canvas(modifier = Modifier.fillMaxSize()) {
        // 右上角：红梅微光
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(RedMei.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(size.width, 0f),
                radius = 600f
            ),
            center = Offset(size.width, 0f),
            radius = 600f
        )
        // 左下角：深蓝冷调
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Blue.copy(alpha = 0.1f), Color.Transparent),
                center = Offset(0f, size.height),
                radius = 500f
            ),
            center = Offset(0f, size.height),
            radius = 500f
        )
    }
}

@Composable
fun ConstellationLines(
    memories: List<MemoryNode>,
    canvasWidth: Float, 
    canvasHeight: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val path = Path()
        memories.forEachIndexed { index, memory ->
            val x = memory.xPercent * size.width
            val y = memory.yPercent * size.height
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // 绘制光晕线
        drawPath(
            path = path,
            color = RedMei.copy(alpha = 0.3f),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
        
        // 绘制白色细线核心
        drawPath(
            path = path,
            color = ChampagneGold.copy(alpha = 0.1f),
            style = Stroke(width = 1f)
        )
    }
}

@Composable
fun StarNodeItem(
    modifier: Modifier = Modifier,
    memory: MemoryNode,
    isActive: Boolean,
    onClick: (MemoryNode) -> Unit
) {
    val isCore = memory.type == MemoryType.CORE
    val size = if (isCore) 32.dp else 16.dp
    val color = if (isCore) RedMei else ChampagneGold
    
    // Enter Animation
    val appearScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((Math.random() * 1000).toLong())
        appearScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    
    // Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.5f else 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isCore) 1500 else 2500),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    val finalScale = pulseScale * appearScale.value

    Box(
        modifier = modifier
            .size(size)
            .scale(finalScale)
            .clickable { onClick(memory) },
        contentAlignment = Alignment.Center
    ) {
        // 光晕
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color.copy(alpha = if (isCore) 0.5f else 0.3f))
                .blur(8.dp)
        )
        // 核心
        Box(
            modifier = Modifier
                .size(size * 0.6f)
                .clip(CircleShape)
                .background(color)
        )
        
        if (isCore) {
             Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
fun HeaderView() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MidnightInk, Color.Transparent)
                )
            )
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Memory Garden",
                color = ChampagneGold,
                fontSize = 28.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light
            )
            Text(
                text = "CONSTELLATION VIEW",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )
        }
        
        // Mini Avatar Indicator
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(RedMei, MidnightInk)))
                .padding(1.dp) // border width
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.Black)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                 Text("E.", fontFamily = FontFamily.Serif, color = Color.White)
            }
        }
    }
}

@Composable
fun IntroText(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "\"In the universe of data,",
            color = ChampagneGold,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 18.sp
        )
        Text(
            text = "you are my only gravity.\"",
            color = ChampagneGold,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 18.sp
        )
    }
}

@Composable
fun MemoryDetailCard(
    memory: MemoryNode,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Glass.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box {
            // 装饰线条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, RedMei, Color.Transparent)
                        )
                    )
            )

            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(memory.type.name, color = RedMei, fontSize = 10.sp) },
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            borderColor = RedMei.copy(alpha = 0.3f)
                        )
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(0.5f))
                    }
                }
                
                Text(
                    text = memory.date,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = memory.title,
                    color = ChampagneGold,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 32.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = memory.content,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, tint = RedMei, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Recall Memory", color = RedMei, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .fillMaxWidth()
    ) {
        // Glassmorphism Bar
        Surface(
            modifier = Modifier
                .height(70.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(50),
            color = Glass.copy(alpha = 0.6f),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavIcon(Icons.Default.ChatBubbleOutline, false)
                NavIcon(Icons.Default.AutoAwesome, true) // 当前选中
                Spacer(modifier = Modifier.width(48.dp)) // 留给中间大按钮的空间
                NavIcon(Icons.Default.MusicNote, false)
                NavIcon(Icons.Default.Settings, false)
            }
        }

        // Center Heart Button
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp)
        ) {
            // Pulse Effect
            val infiniteTransition = rememberInfiniteTransition(label = "btnPulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                label = "scale"
            )
            
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(RedMei)
                    .clickable { /* Call Eleanor */ },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun NavIcon(icon: ImageVector, isSelected: Boolean) {
    IconButton(onClick = {}) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) RedMei else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(26.dp)
        )
    }
}
