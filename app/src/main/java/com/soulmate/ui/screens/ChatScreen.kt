package com.soulmate.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soulmate.data.service.MindWatchService
import com.soulmate.data.service.ASRState
import com.soulmate.ui.components.GlassBubble
import com.soulmate.ui.components.ParticleBackground
import com.soulmate.ui.state.ChatMessage
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * ChatScreen - Main chat screen for AI interaction
 *
 * Features:
 * - Message list with user and AI bubbles
 * - Voice input support with ASR
 * - MindWatch status display
 * - Streaming response display
 * - Navigation to settings and garden
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToGarden: () -> Unit = {}
) {
    val chatState by viewModel.chatState.collectAsState()
    val mindWatchStatus by viewModel.mindWatchStatus.collectAsState()
    val asrState by viewModel.asrState.collectAsState()
    val voiceInputText by viewModel.voiceInputText.collectAsState()
    val isVoiceInputActive by viewModel.isVoiceInputActive.collectAsState()
    val affinityScore by viewModel.affinityScore.collectAsState()
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatState.messages.size, chatState.currentStreamToken) {
        if (chatState.messages.isNotEmpty() || chatState.currentStreamToken.isNotBlank()) {
            coroutineScope.launch {
                listState.animateScrollToItem(chatState.messages.size)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SoulMateTheme.colors.bgGradientStart,
                        SoulMateTheme.colors.bgGradientEnd
                    )
                )
            )
    ) {
        // Background
        ParticleBackground(
            modifier = Modifier.fillMaxSize(),
            particleColor = SoulMateTheme.colors.particleColor,
            lineColor = SoulMateTheme.colors.cardBorder
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(SoulMateTheme.colors.cardBg.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = SoulMateTheme.colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "灵伴",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SoulMateTheme.colors.textPrimary
                    )
                    Text(
                        text = "共鸣值: $affinityScore",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoulMateTheme.colors.textSecondary
                    )
                }

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .background(SoulMateTheme.colors.cardBg.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = SoulMateTheme.colors.textPrimary
                    )
                }
            }

            // MindWatch Status Card (only show when not NORMAL)
            if (mindWatchStatus != MindWatchService.WatchStatus.NORMAL) {
                MindWatchStatusCard(
                    status = mindWatchStatus,
                    onDismiss = { viewModel.dismissMindWatchAlert() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Warning/Warning Messages
            chatState.warning?.let { warning ->
                val warningColor = Color(0xFFFF8C00) // Orange for warnings
                GlassBubble(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    backgroundColor = warningColor.copy(alpha = 0.2f),
                    borderColor = warningColor.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = warningColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = SoulMateTheme.colors.textPrimary
                        )
                    }
                }
            }

            // Error Messages
            chatState.error?.let { error ->
                GlassBubble(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    backgroundColor = Color.Red.copy(alpha = 0.2f),
                    borderColor = Color.Red.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = SoulMateTheme.colors.textPrimary
                        )
                    }
                }
            }

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatState.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Streaming response bubble
                if (chatState.currentStreamToken.isNotBlank()) {
                    item {
                        MessageBubble(
                            message = ChatMessage(
                                id = "streaming",
                                content = chatState.currentStreamToken,
                                isFromUser = false,
                                timestamp = System.currentTimeMillis()
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            isStreaming = true
                        )
                    }
                }
            }

            // Voice Input Display (when active)
            if (isVoiceInputActive || voiceInputText.isNotBlank()) {
                GlassBubble(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    backgroundColor = SoulMateTheme.colors.accentColor.copy(alpha = 0.1f),
                    borderColor = SoulMateTheme.colors.accentColor.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isVoiceInputActive) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = SoulMateTheme.colors.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = voiceInputText.ifBlank { "正在聆听..." },
                                style = MaterialTheme.typography.bodyLarge,
                                color = SoulMateTheme.colors.textPrimary
                            )
                        }
                        
                        when (asrState) {
                            is ASRState.Error -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = (asrState as ASRState.Error).message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Red
                                )
                            }
                            is ASRState.Idle -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "初始化语音识别...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SoulMateTheme.colors.textSecondary
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Input Area (placeholder - text input would go here)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice Input Button
                val iconColor by animateColorAsState(
                    targetValue = if (isVoiceInputActive) 
                        SoulMateTheme.colors.accentColor 
                    else 
                        SoulMateTheme.colors.textSecondary,
                    label = "iconColor"
                )
                
                IconButton(
                    onClick = { viewModel.toggleVoiceInput() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (isVoiceInputActive) 
                                SoulMateTheme.colors.accentColor.copy(alpha = 0.2f) 
                            else 
                                SoulMateTheme.colors.cardBg,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isVoiceInputActive) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (isVoiceInputActive) "停止语音输入" else "开始语音输入",
                        tint = iconColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Text input placeholder
                GlassBubble(
                    modifier = Modifier.weight(1f),
                    backgroundColor = SoulMateTheme.colors.cardBg.copy(alpha = 0.6f),
                    borderColor = SoulMateTheme.colors.cardBorder
                ) {
                    Text(
                        text = "输入消息...",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoulMateTheme.colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Send button (disabled for now - text input not implemented)
                IconButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            SoulMateTheme.colors.cardBg.copy(alpha = 0.6f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        tint = SoulMateTheme.colors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * MindWatch Status Card - Displays current emotional monitoring status
 *
 * @param status The current WatchStatus from MindWatchService
 * @param onDismiss Callback when user dismisses the alert
 * @param modifier Modifier for card
 */
@Composable
fun MindWatchStatusCard(
    status: MindWatchService.WatchStatus,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, message, color) = when (status) {
        MindWatchService.WatchStatus.CRISIS -> Triple(
            "深度守护",
            "检测到您情绪状态异常，我们一直在您身边。",
            Color(0xFFFF4500) // Red-Orange
        )
        MindWatchService.WatchStatus.WARNING -> Triple(
            "共鸣阴雨",
            "您的心情有些低落，让我陪您聊聊天吧。",
            Color(0xFFFF8C00) // Orange
        )
        MindWatchService.WatchStatus.CAUTION -> Triple(
            "灵犀提示",
            "注意保持心情愉快哦。",
            SoulMateTheme.colors.accentColor
        )
        MindWatchService.WatchStatus.NORMAL -> Triple(
            "状态正常",
            "",
            SoulMateTheme.colors.accentColor
        )
    }

    GlassBubble(
        modifier = modifier,
        backgroundColor = color.copy(alpha = 0.15f),
        borderColor = color.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = SoulMateTheme.colors.textPrimary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SoulMateTheme.colors.cardBg.copy(alpha = 0.8f),
                    contentColor = SoulMateTheme.colors.textPrimary
                )
            ) {
                Text("我没事")
            }
        }
    }
}

/**
 * Message Bubble - Displays a single chat message
 *
 * @param message The ChatMessage to display
 * @param modifier Modifier for bubble
 * @param isStreaming Whether this is a streaming response (typing effect)
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val isFromUser = message.isFromUser
    
    Row(
        modifier = modifier,
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    ) {
        GlassBubble(
            modifier = Modifier.widthIn(max = 300.dp),
            backgroundColor = if (isFromUser) {
                SoulMateTheme.colors.accentColor.copy(alpha = 0.3f)
            } else {
                SoulMateTheme.colors.cardBg.copy(alpha = 0.8f)
            },
            borderColor = if (isFromUser) {
                SoulMateTheme.colors.accentColor.copy(alpha = 0.5f)
            } else {
                SoulMateTheme.colors.cardBorder
            }
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Content
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoulMateTheme.colors.textPrimary
                )
                
                // Image support
                if (message.localImageUri != null || message.imageUrl != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Placeholder for image - would use AsyncImage in full implementation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(
                                SoulMateTheme.colors.cardBorder.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "图片",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoulMateTheme.colors.textSecondary
                        )
                    }
                }

                // Timestamp (only show when not streaming)
                if (!isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = SoulMateTheme.colors.textSecondary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Format timestamp for display
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        else -> "${diff / 86400_000}天前"
    }
}
