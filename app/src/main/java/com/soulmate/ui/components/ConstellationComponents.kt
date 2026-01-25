package com.soulmate.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    canvasHeight: Float,
    modifier: Modifier = Modifier
) {
    val lineColor = SoulMateTheme.colors.cardBorder
    val glowColor = SoulMateTheme.colors.accentColor

    Canvas(modifier = modifier) {
        if (memories.isEmpty()) return@Canvas

        val path = Path()
        
        // Memories are already sorted by time (Y-axis) in ViewModel
        val first = memories.first()
        var prevX = first.xPercent * canvasWidth
        var prevY = first.yPercent * canvasHeight
        path.moveTo(prevX, prevY)
        
        for (i in 1 until memories.size) {
            val curr = memories[i]
            val currX = curr.xPercent * canvasWidth
            val currY = curr.yPercent * canvasHeight
            
            // 使用三次贝塞尔曲线绘制平滑的 S 型连接
            // 控制点位于两点垂直距离的 50% 处，形成垂直流动的趋势
            val deltaY = currY - prevY
            val controlY1 = prevY + deltaY * 0.5f
            val controlY2 = currY - deltaY * 0.5f
            
            // Control Point 1: keep X of prev, move Y down
            // Control Point 2: keep X of curr, move Y up
            path.cubicTo(
                prevX, controlY1,
                currX, controlY2,
                currX, currY
            )
            
            prevX = currX
            prevY = currY
        }

        // 绘制光晕线 (宽且透明)
        drawPath(
            path = path,
            color = glowColor.copy(alpha = 0.2f),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        
        // 绘制白色细线核心
        drawPath(
            path = path,
            color = lineColor.copy(alpha = 0.4f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
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
    val color = if (isCore) SoulMateTheme.colors.accentColor else SoulMateTheme.colors.particleColor
    
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
    val isLeft = memory.xPercent < 0.5f

    // 根容器：位于星星中心位置（由外部 offset 决定），不限制大小以便显示外部文字
    Box(modifier = modifier) {
        // 1. 文本标签 (Core 显示完整标题，普通显示日期)
        if (isCore || isActive) {
            Column(
                modifier = Modifier
                    .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .offset(x = if (isLeft) 24.dp else (-24).dp)
                    .widthIn(max = 160.dp),
                horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End
            ) {
                Text(
                    text = memory.title,
                    style = TextStyle(
                        color = SoulMateTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            blurRadius = 4f
                        )
                    ),
                    maxLines = 1
                )
                Text(
                    text = memory.date,
                    style = TextStyle(
                        color = SoulMateTheme.colors.textSecondary,
                        fontSize = 10.sp,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            blurRadius = 4f
                        )
                    )
                )
            }
        } else {
            // 普通节点仅在下方微弱显示日期
            Text(
                text = memory.date.substring(5), // 01.24
                style = TextStyle(
                    color = SoulMateTheme.colors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        blurRadius = 2f
                    )
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 16.dp)
            )
        }

        // 2. 星星本体 (居中)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
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
                    tint = SoulMateTheme.colors.textPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun HeaderView(
    isMusicPlaying: Boolean = false,
    onMusicToggle: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    isSearchMode: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchSubmit: (String) -> Unit = {},
    onSearchClose: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SoulMateTheme.colors.bgBase, Color.Transparent)
                )
            )
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearchMode) {
            // 搜索模式：显示搜索输入框
            SearchInputField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onSubmit = onSearchSubmit,
                onClose = onSearchClose,
                modifier = Modifier.weight(1f)
            )
        } else {
            // 正常模式：显示标题
            Column {
                Text(
                    text = "记忆花园",
                    color = SoulMateTheme.colors.accentColor,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = "星图视图",
                    color = SoulMateTheme.colors.textSecondary,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Music Toggle
                IconButton(
                    onClick = onMusicToggle,
                    modifier = Modifier
                        .size(40.dp)
                        .background(SoulMateTheme.colors.cardBg, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMusicPlaying) Icons.Default.MusicNote else Icons.Default.MusicOff,
                        contentDescription = "Music Toggle",
                        tint = if (isMusicPlaying) SoulMateTheme.colors.accentColor else SoulMateTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Search Button
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(SoulMateTheme.colors.cardBg, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = SoulMateTheme.colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 搜索输入框组件
 */
@Composable
fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember { mutableStateOf(query) }
    
    // 同步外部query变化
    LaunchedEffect(query) {
        if (query != textFieldValue) {
            textFieldValue = query
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 搜索图标
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = SoulMateTheme.colors.textPrimary,
            modifier = Modifier.size(20.dp)
        )

        // 输入框
        BasicTextField(
            value = textFieldValue,
            onValueChange = { 
                textFieldValue = it
                onQueryChange(it)
            },
            textStyle = TextStyle(
                color = SoulMateTheme.colors.textPrimary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Serif
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .background(SoulMateTheme.colors.cardBg, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (textFieldValue.isEmpty()) {
                        Text(
                            text = "搜索记忆...",
                            color = SoulMateTheme.colors.textSecondary,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                    innerTextField()
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onSubmit(textFieldValue) }
            )
        )

        // 搜索按钮
        IconButton(
            onClick = { onSubmit(textFieldValue) },
            modifier = Modifier
                .size(36.dp)
                .background(SoulMateTheme.colors.accentColor.copy(alpha = 0.2f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Search",
                tint = SoulMateTheme.colors.accentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        // 关闭按钮
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(36.dp)
                .background(SoulMateTheme.colors.cardBg, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Search",
                tint = SoulMateTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun IntroText(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "“在数据的浩瀚宇宙中，",
            color = SoulMateTheme.colors.textPrimary,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 18.sp
        )
        Text(
            text = "你是由于我的唯一引力。”",
            color = SoulMateTheme.colors.textPrimary,
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
        colors = CardDefaults.cardColors(containerColor = SoulMateTheme.colors.cardBg),
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
                            listOf(Color.Transparent, SoulMateTheme.colors.accentColor, Color.Transparent)
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
                        label = { Text(getMemoryTypeName(memory.type), color = SoulMateTheme.colors.accentColor, fontSize = 10.sp) },
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            borderColor = SoulMateTheme.colors.accentColor.copy(alpha = 0.3f)
                        )
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SoulMateTheme.colors.textSecondary)
                    }
                }
                
                Text(
                    text = memory.date,
                    color = SoulMateTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = memory.title,
                    color = SoulMateTheme.colors.textPrimary,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 32.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = memory.content,
                    color = SoulMateTheme.colors.textPrimary.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, tint = SoulMateTheme.colors.accentColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("回溯记忆", color = SoulMateTheme.colors.accentColor, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun getMemoryTypeName(type: MemoryType): String {
    return when(type) {
        MemoryType.CORE -> "核心记忆"
        MemoryType.NORMAL -> "普通记忆"
        MemoryType.NEW -> "新记忆"
    }
}

@Composable
fun FloatingActionButtons(
    modifier: Modifier = Modifier,
    onAddMemory: () -> Unit = {}
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Add Memory Button (Star)
        SmallFloatingActionButton(
            onClick = onAddMemory,
            containerColor = SoulMateTheme.colors.cardBg,
            contentColor = SoulMateTheme.colors.accentColor,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.Star, contentDescription = "添加记忆", modifier = Modifier.size(24.dp))
        }
    }
}

