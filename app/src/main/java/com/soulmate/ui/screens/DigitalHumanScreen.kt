package com.soulmate.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.soulmate.ui.components.AvatarContainer
import com.soulmate.ui.components.ParticleBackground
import com.soulmate.ui.components.GlassBubble
import com.soulmate.ui.components.pulsate
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.viewmodel.ChatViewModel
import com.soulmate.data.model.UserGender

import androidx.compose.ui.viewinterop.AndroidView
import com.soulmate.ui.components.SoulmateInputCapsule
import com.soulmate.ui.components.OnSendListener
import com.soulmate.ui.components.InputPayload
import com.soulmate.ui.components.MediaType

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding

/**
 * DigitalHumanScreen - 数字人互动屏幕
 *
 * 专注于与数字人的语音和视觉交互，移除文字聊天列表，提供沉浸式体验。
 */
@Composable
fun DigitalHumanScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val chatState by viewModel.chatState.collectAsState()
    val isVoiceInputActive by viewModel.isVoiceInputActive.collectAsState()
    val currentUIEvent by viewModel.currentUIEvent.collectAsState()
    val userGender by viewModel.userGender.collectAsState()
    val mindWatchStatus by viewModel.mindWatchStatus.collectAsState(initial = com.soulmate.data.service.MindWatchService.WatchStatus.NORMAL)
    val anniversaryPopup by viewModel.showAnniversaryPopup.collectAsState()
    val handsFreeMode by viewModel.handsFreeMode.collectAsState()
    
    // 调试日志：追踪性别选择
    android.util.Log.w("DigitalHumanScreen", ">>> userGender from Flow: $userGender")

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
        // 1. Background
        ParticleBackground(
            modifier = Modifier.fillMaxSize(),
            particleColor = SoulMateTheme.colors.particleColor,
            lineColor = SoulMateTheme.colors.cardBorder
        )

        // 2. Avatar Container (Full Screen)
        android.util.Log.w("DigitalHumanScreen", ">>> userGender: $userGender")
        
        when {
            userGender == UserGender.UNSET -> {
                android.util.Log.w("DigitalHumanScreen", ">>> Gender UNSET, showing placeholder")
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = SoulMateTheme.colors.accentColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在唤醒灵犀化身...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SoulMateTheme.colors.textSecondary
                        )
                    }
                }
            }
            viewModel.avatarCoreService != null -> {
                android.util.Log.w("DigitalHumanScreen", ">>> Creating AvatarContainer with gender: $userGender")
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarContainer(
                        avatarCoreService = viewModel.avatarCoreService!!,
                        userGender = userGender,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🤖",
                        style = MaterialTheme.typography.displayLarge
                    )
                }
            }
        }

        // Gradient Mask at bottom for controls visibility
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    )
                )
        )

        // 3. Care Card (Above Input, when MindWatch detects issues)
        val showCareCard = mindWatchStatus == com.soulmate.data.service.MindWatchService.WatchStatus.WARNING ||
                          mindWatchStatus == com.soulmate.data.service.MindWatchService.WatchStatus.CRISIS ||
                          mindWatchStatus == com.soulmate.data.service.MindWatchService.WatchStatus.CAUTION
        
        if (showCareCard) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 200.dp, start = 24.dp, end = 24.dp)
            ) {
                com.soulmate.ui.components.CareCard(
                    status = mindWatchStatus,
                    message = when (mindWatchStatus) {
                        com.soulmate.data.service.MindWatchService.WatchStatus.CRISIS -> "我感觉到你现在的痛苦... 我会一直陪着你。"
                        com.soulmate.data.service.MindWatchService.WatchStatus.WARNING -> "我看你最近心情不太好，想聊聊吗？"
                        com.soulmate.data.service.MindWatchService.WatchStatus.CAUTION -> "有些心事想跟我说说吗？"
                        else -> ""
                    },
                    onCallHelp = {
                        // Navigate to crisis resources or trigger help
                        // For now log, as we don't have navigation prop
                        android.util.Log.d("DigitalHumanScreen", "Call Help clicked")
                        // If it's just "Talk" (CAUTION/WARNING), we just dismiss
                        if (mindWatchStatus != com.soulmate.data.service.MindWatchService.WatchStatus.CRISIS) {
                            viewModel.dismissMindWatchAlert()
                        }
                    },
                    onDismiss = {
                        viewModel.dismissMindWatchAlert()
                    }
                )
            }
        }

        // 3.5. Status / Subtitles (Optional - displaying last message or status)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showCareCard) 280.dp else 120.dp, start = 24.dp, end = 24.dp)
        ) {
            // Display streaming text or last AI message if desired
            if (chatState.currentStreamToken.isNotEmpty()) {
                GlassBubble(
                    backgroundColor = SoulMateTheme.colors.bubbleAi,
                    borderColor = SoulMateTheme.colors.cardBorder
                ) {
                     Text(
                        text = chatState.currentStreamToken,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SoulMateTheme.colors.textPrimary,
                        modifier = Modifier.padding(16.dp)
                     )
                }
            }
        }

        // 4. Controls Layer (SoulmateInputCapsule)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    SoulmateInputCapsule(context).apply {
                        onSendListener = object : OnSendListener {
                            override fun onSend(payload: InputPayload) {
                                val textToSend = payload.text ?: ""
                                val mediaUri = payload.mediaUri

                                when (payload.mediaType) {
                                    MediaType.IMAGE -> {
                                        if (mediaUri != null) {
                                            viewModel.sendMessageWithImage(textToSend, mediaUri.toString())
                                        }
                                    }
                                    MediaType.VIDEO -> {
                                        if (mediaUri != null) {
                                            viewModel.sendMessageWithVideo(textToSend, mediaUri.toString())
                                        }
                                    }
                                    MediaType.NONE -> {
                                        if (textToSend.isNotBlank()) {
                                            viewModel.sendMessage(textToSend)
                                        }
                                    }
                                }
                            }
                        }
                        // 连接 Flow Mode 状态变化回调
                        onHandsFreeStateChanged = { isLocked ->
                            viewModel.setHandsFreeMode(isLocked)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 5. Resonance Orb (Top Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
        ) {
            com.soulmate.ui.components.ResonanceOrb(
                status = mindWatchStatus,
                size = 80.dp
            )
        }

        // 6. Back / Close Button (Top Left)
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp)
                .size(48.dp)
                .background(SoulMateTheme.colors.cardBg, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = SoulMateTheme.colors.textPrimary
            )
        }

        // 7. Flow Mode Visual Feedback (Listening Wave near input)
        if (handsFreeMode && isVoiceInputActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                // Simple wave indicator - static for now
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp, 24.dp)
                                .background(
                                    SoulMateTheme.colors.accentColor.copy(alpha = 0.8f),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }
            }
        }

        // 8. Pop-up Celebration (Global Overlay)
        if (anniversaryPopup != null) {
            com.soulmate.ui.components.PopUpCelebration(
                visible = true,
                title = "Happy ${anniversaryPopup!!.name}!",
                message = anniversaryPopup!!.message ?: "Today is a special day for us.",
                onDismiss = { viewModel.dismissAnniversaryPopup() }
            )
        }
    }
}
