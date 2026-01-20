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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Mic
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
import com.soulmate.ui.components.FluidBackground
import com.soulmate.ui.components.GlassBubble
import com.soulmate.ui.components.pulsate
import com.soulmate.ui.theme.EtherealBlue
import com.soulmate.ui.theme.GlassSurface
import com.soulmate.ui.viewmodel.ChatViewModel

/**
 * DigitalHumanScreen - 数字人互动屏幕
 *
 * 专注于与数字人的语音和视觉交互，移除文字聊天列表，提供沉浸式体验。
 */
@Composable
fun DigitalHumanScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToTextChat: () -> Unit = {}
) {
    val chatState by viewModel.chatState.collectAsState()
    val isVoiceInputActive by viewModel.isVoiceInputActive.collectAsState()
    val currentUIEvent by viewModel.currentUIEvent.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Background
        FluidBackground()

        // 2. Avatar Container (Full Screen)
        if (viewModel.avatarCoreService != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AvatarContainer(
                    avatarCoreService = viewModel.avatarCoreService!!,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
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

        // 3. Status / Subtitles (Optional - displaying last message or status)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp, start = 24.dp, end = 24.dp)
        ) {
            // Display streaming text or last AI message if desired
            if (chatState.currentStreamToken.isNotEmpty()) {
                GlassBubble {
                     Text(
                        text = chatState.currentStreamToken,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                     )
                }
            }
        }

        // 4. Controls Layer
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            VoiceControlPanel(
                isVoiceInputActive = isVoiceInputActive,
                onStartVoiceInput = { viewModel.startVoiceInput() },
                onStopVoiceInput = { viewModel.stopVoiceInput() },
                onNavigateToTextChat = onNavigateToTextChat
            )
        }

        // 5. Back / Close Button (Top Left)
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp)
                .size(48.dp)
                .background(GlassSurface, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun VoiceControlPanel(
    isVoiceInputActive: Boolean,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onNavigateToTextChat: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Switch to Text Chat Button
        IconButton(
            onClick = onNavigateToTextChat,
            modifier = Modifier
                .size(56.dp)
                .background(GlassSurface, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubble,
                contentDescription = "Text Chat",
                tint = Color.White
            )
        }

        // Microphone Button (Large, Central)
        Box(
            modifier = Modifier
                .size(80.dp)
                .pulsate(enabled = isVoiceInputActive)
                .clip(CircleShape)
                .background(
                    if (isVoiceInputActive) EtherealBlue else GlassSurface
                )
                .pointerInput(Unit) {
                     detectTapGestures(
                        onPress = {
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                onStartVoiceInput()
                                tryAwaitRelease()
                                onStopVoiceInput()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Hold to Speak",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // Placceholder for symmetry or other action (e.g. Camera/Memory)
        // For now just spacer to keep Mic centered if we had 3 items, but with 2 it's lopsided.
        // Let's add a dummy invisible box or just center the Row in the parent.
        // Actually, let's keep it simple.
        Spacer(modifier = Modifier.size(56.dp)) 
    }
}
