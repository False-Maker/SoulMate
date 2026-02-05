package com.soulmate.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.soulmate.data.service.MindWatchService
import com.soulmate.ui.components.CareCard
import com.soulmate.ui.components.GlassBubble
import com.soulmate.ui.components.GlassMemoryCard
import com.soulmate.ui.components.InputPayload
import com.soulmate.ui.components.MediaType
import com.soulmate.ui.components.OnSendListener
import com.soulmate.ui.components.ParticleBackground
import com.soulmate.ui.components.ResonanceOrb
import com.soulmate.ui.components.SoulmateInputCapsule
import com.soulmate.ui.components.PopUpCelebration
import com.soulmate.ui.state.UIWidgetData
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DigitalHumanScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val chatState by viewModel.chatState.collectAsState()
    val isVoiceInputActive by viewModel.isVoiceInputActive.collectAsState()
    val mindWatchStatus by viewModel.mindWatchStatus.collectAsState(initial = MindWatchService.WatchStatus.NORMAL)
    val audioAmplitude by viewModel.audioAmplitude.collectAsState(initial = 0f)
    val anniversaryPopup by viewModel.showAnniversaryPopup.collectAsState()
    val handsFreeMode by viewModel.handsFreeMode.collectAsState()
    val showCareCard = mindWatchStatus == MindWatchService.WatchStatus.WARNING ||
        mindWatchStatus == MindWatchService.WatchStatus.CRISIS ||
        mindWatchStatus == MindWatchService.WatchStatus.CAUTION

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
        ParticleBackground(
            modifier = Modifier.fillMaxSize(),
            particleColor = SoulMateTheme.colors.particleColor,
            lineColor = SoulMateTheme.colors.cardBorder
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ResonanceOrb(
                    amplitude = audioAmplitude,
                    status = mindWatchStatus,
                    size = 140.dp
                )
            }
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (showCareCard) 240.dp else 160.dp),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    if (chatState.currentStreamToken.isNotBlank()) {
                        item(key = "stream-token") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItemPlacement(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                GlassBubble(
                                    modifier = Modifier.widthIn(max = 300.dp),
                                    backgroundColor = SoulMateTheme.colors.bubbleAi,
                                    borderColor = SoulMateTheme.colors.cardBorder,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = chatState.currentStreamToken,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = SoulMateTheme.colors.textPrimary,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }
                    items(chatState.messages.asReversed(), key = { it.id }) { message ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItemPlacement(),
                            horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start
                        ) {
                            if (message.content.isNotBlank()) {
                                GlassBubble(
                                    modifier = Modifier.widthIn(max = 300.dp),
                                    backgroundColor = if (message.isFromUser) {
                                        SoulMateTheme.colors.bubbleUser
                                    } else {
                                        SoulMateTheme.colors.bubbleAi
                                    },
                                    borderColor = SoulMateTheme.colors.cardBorder,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = message.content,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = SoulMateTheme.colors.textPrimary,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                            message.uiWidget?.let { widget ->
                                Spacer(modifier = Modifier.height(8.dp))
                                when (widget) {
                                    is UIWidgetData.MemoryCapsule -> {
                                        GlassMemoryCard(
                                            date = widget.date,
                                            summary = widget.summary,
                                            imageUrls = widget.imageUrls,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    is UIWidgetData.DecisionOptions -> {
                                        GlassDecisionOptions(
                                            title = widget.title,
                                            options = widget.options,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    is UIWidgetData.BreathingGuide -> {
                                        GlassBreathingGuide(
                                            durationSeconds = widget.durationSeconds,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCareCard) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 200.dp, start = 24.dp, end = 24.dp)
            ) {
                CareCard(
                    status = mindWatchStatus,
                    message = when (mindWatchStatus) {
                        MindWatchService.WatchStatus.CRISIS -> "我感觉到你现在的痛苦... 我会一直陪着你。"
                        MindWatchService.WatchStatus.WARNING -> "我看你最近心情不太好，想聊聊吗？"
                        MindWatchService.WatchStatus.CAUTION -> "有些心事想跟我说说吗？"
                        else -> ""
                    },
                    onCallHelp = {
                        if (mindWatchStatus != MindWatchService.WatchStatus.CRISIS) {
                            viewModel.dismissMindWatchAlert()
                        }
                    },
                    onDismiss = {
                        viewModel.dismissMindWatchAlert()
                    }
                )
            }
        }

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
                        
                        // 连接录音回调 (仅恢复基础长按录音)
                        onStartRecording = {
                            viewModel.startVoiceInput()
                        }
                        onStopRecording = {
                            viewModel.stopVoiceInput()
                        }
                        onCancelRecording = {
                            viewModel.cancelVoiceInput()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

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
                    repeat(3) {
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

        if (anniversaryPopup != null) {
            PopUpCelebration(
                visible = true,
                title = "Happy ${anniversaryPopup!!.name}!",
                message = anniversaryPopup!!.message ?: "Today is a special day for us.",
                onDismiss = { viewModel.dismissAnniversaryPopup() }
            )
        }
    }
}

@Composable
private fun GlassDecisionOptions(
    title: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onOptionSelected: (String) -> Unit = {}
) {
    GlassBubble(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = SoulMateTheme.colors.cardBg.copy(alpha = 0.7f),
        borderColor = SoulMateTheme.colors.cardBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SoulMateTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    GlassBubble(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = SoulMateTheme.colors.cardBg.copy(alpha = 0.8f),
                        borderColor = SoulMateTheme.colors.cardBorder,
                        onClick = { onOptionSelected(option) }
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoulMateTheme.colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassBreathingGuide(
    durationSeconds: Int,
    modifier: Modifier = Modifier
) {
    GlassBubble(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = SoulMateTheme.colors.cardBg.copy(alpha = 0.7f),
        borderColor = SoulMateTheme.colors.cardBorder
    ) {
        Text(
            text = "呼吸引导 · ${durationSeconds}s",
            style = MaterialTheme.typography.bodyMedium,
            color = SoulMateTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}
