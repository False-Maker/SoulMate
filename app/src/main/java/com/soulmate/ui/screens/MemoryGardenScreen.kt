package com.soulmate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soulmate.ui.components.*
import com.soulmate.ui.theme.*
import com.soulmate.ui.viewmodel.MemoryGardenViewModel
import kotlinx.coroutines.delay

@Composable
fun MemoryGardenScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: MemoryGardenViewModel = hiltViewModel()
) {
    val memoryNodes by viewModel.memoryNodes.collectAsState()
    var activeMemory by remember { mutableStateOf<MemoryNode?>(null) }
    var showIntro by remember { mutableStateOf(true) }

    // Intro 自动消失
    LaunchedEffect(Unit) {
        delay(4000)
        showIntro = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightInk)
    ) {
        // 1. 背景层：星尘与氛围光晕
        StardustBackground()
        AmbientGlow()

        // 2. 核心层：星图连接与节点
        // 使用 BoxWithConstraints 获取屏幕尺寸以计算绝对坐标
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp, top = 80.dp) // 留出导航和Header空间
        ) {
            val width = maxWidth.value
            val height = maxHeight.value

            // A. 绘制连线 (Canvas 层)
            ConstellationLines(
                memories = memoryNodes,
                canvasWidth = width,
                canvasHeight = height
            )

            // B. 绘制节点 (Interactive Layer)
            memoryNodes.forEach { memory ->
                val offsetX = (memory.xPercent * width).dp
                val offsetY = (memory.yPercent * height).dp

                StarNodeItem(
                    modifier = Modifier.offset(x = offsetX - 12.dp, y = offsetY - 12.dp), // Center the node
                    memory = memory,
                    isActive = activeMemory?.id == memory.id,
                    onClick = { activeMemory = it }
                )
            }
        }

        // 3. UI 层：顶部标题
        Box(modifier = Modifier.statusBarsPadding()) {
            HeaderView()
        }
        
        // Back Button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = ChampagneGold
            )
        }

        // 4. UI 层：Intro 文字
        if (showIntro) {
            IntroText(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 5. UI 层：详情卡片 (底部弹出)
        activeMemory?.let { memory ->
            MemoryDetailCard(
                memory = memory,
                onClose = { activeMemory = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
            )
        }

        // 6. UI 层：底部导航
        BottomNavBar(modifier = Modifier.align(Alignment.BottomCenter))
    }
}
