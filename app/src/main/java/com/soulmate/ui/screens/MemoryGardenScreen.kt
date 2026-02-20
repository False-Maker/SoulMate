package com.soulmate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.soulmate.ui.components.*
import com.soulmate.ui.theme.*
import com.soulmate.ui.viewmodel.MemoryGardenViewModel
import kotlinx.coroutines.delay

@Composable
fun MemoryGardenScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    viewModel: MemoryGardenViewModel = hiltViewModel()
) {
    val memoryNodes by viewModel.memoryNodes.collectAsState()
    val state by viewModel.state.collectAsState()
    
    // UI States
    var showAddDialog by remember { mutableStateOf(false) }
    var newMemoryText by remember { mutableStateOf("") }
    var activeMemory by remember { mutableStateOf<MemoryNode?>(null) } // For detail view if needed

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SoulMateTheme.colors.bgGradientStart,
                        SoulMateTheme.colors.bgGradientEnd
                    )
                )
            )
    ) {
        // 1. Background Layer: Particle System
        ParticleBackground(
            modifier = Modifier.fillMaxSize(),
            particleColor = SoulMateTheme.colors.particleColor,
            lineColor = SoulMateTheme.colors.cardBorder
        )

        // 2. Main Content: Galactic Timeline
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header (Fixed)
            HeaderView(
                isSearchMode = state.isSearchMode,
                searchQuery = state.searchQuery,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onSearchSubmit = { viewModel.searchMemories(it) },
                onSearchClose = { viewModel.exitSearchMode() },
                onSearchClick = { viewModel.enterSearchMode() },
                onMusicToggle = { /* Todo */ }
            )

            // Timeline List
            if (memoryNodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "暂无记忆，点击右下角星星添加",
                        color = SoulMateTheme.colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Intro Quote as Header Item
                    item {
                         IntroText(modifier = Modifier.padding(vertical = 24.dp))
                    }

                    itemsIndexed(memoryNodes) { index, memory ->
                        TimelineMemoryItem(
                            memory = memory,
                            index = index,
                            onClick = { activeMemory = memory }
                        )
                    }
                }
            }
        }
        
        // 3. Back Button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = SoulMateTheme.colors.textPrimary
            )
        }

        // 4. Memory Detail Overlay (Reusing MemoryDetailCard but as a bottom sheet-like overlay)
        if (activeMemory != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { activeMemory = null },
                contentAlignment = Alignment.Center
            ) {
                MemoryDetailCard(
                    memory = activeMemory!!,
                    onClose = { activeMemory = null },
                    modifier = Modifier.padding(32.dp)
                )
            }
        }

        // 5. Floating Action Buttons
        FloatingActionButtons(
            modifier = Modifier.align(Alignment.BottomEnd),
            onAddMemory = { showAddDialog = true }
        )
        
        // 6. Add Memory Dialog (Neon Style)
        if (showAddDialog) {
            // Custom Neon Dialog for Input
             Dialog(
                onDismissRequest = { showAddDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0F0F1A).copy(alpha = 0.95f))
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF00E5FF), Color(0xFFD500F9))
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "�?铭刻记忆",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TextField(
                            value = newMemoryText,
                            onValueChange = { newMemoryText = it },
                            placeholder = { Text("记录当下的感�?..", color = Color.Gray) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = { showAddDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("取消")
                            }
                            
                            Button(
                                onClick = { 
                                    if(newMemoryText.isNotBlank()) {
                                        viewModel.addMemory(newMemoryText)
                                        showAddDialog = false
                                        newMemoryText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFD500F9))),
                                        RoundedCornerShape(100)
                                    )
                            ) {
                                Text("铭刻", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineMemoryItem(
    memory: MemoryNode,
    index: Int,
    onClick: () -> Unit
) {
    val isLeft = index % 2 == 0
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side
        Box(modifier = Modifier.weight(1f)) {
            if (isLeft) {
                ParallaxGlassCard(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    backgroundColor = SoulMateTheme.colors.cardBg,
                    borderColor = SoulMateTheme.colors.cardBorder,
                    glowColor = SoulMateTheme.colors.accentGlow,
                    onClick = onClick
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = memory.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = SoulMateTheme.colors.textPrimary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = memory.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = SoulMateTheme.colors.textSecondary,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                 Text(
                    text = memory.date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoulMateTheme.colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                )
            }
        }
        
        // Center Spine
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            // Top Line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(50.dp) // Half height of card
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if(index==0) Color.Transparent else SoulMateTheme.colors.accentColor.copy(alpha=0.3f),
                                SoulMateTheme.colors.accentColor
                            )
                        )
                    )
            )
            
            // Dot
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(SoulMateTheme.colors.accentColor, CircleShape)
                    .border(2.dp, Color.White.copy(alpha=0.5f), CircleShape)
                    .shadow(8.dp, CircleShape, spotColor = SoulMateTheme.colors.accentColor)
            )
            
            // Bottom Line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(50.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                SoulMateTheme.colors.accentColor,
                                SoulMateTheme.colors.accentColor.copy(alpha=0.3f)
                            )
                        )
                    )
            )
        }
        
        // Right Side
        Box(modifier = Modifier.weight(1f)) {
            if (!isLeft) {
                ParallaxGlassCard(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    backgroundColor = SoulMateTheme.colors.cardBg,
                    borderColor = SoulMateTheme.colors.cardBorder,
                    glowColor = SoulMateTheme.colors.accentGlow,
                    onClick = onClick
                ) {
                    Column(
                         modifier = Modifier.padding(16.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = memory.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = SoulMateTheme.colors.textPrimary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                         Text(
                            text = memory.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = SoulMateTheme.colors.textSecondary,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Text(
                    text = memory.date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoulMateTheme.colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                )
            }
        }
    }
}

