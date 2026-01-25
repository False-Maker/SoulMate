package com.soulmate.ui.screens

import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Snackbar
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soulmate.data.model.UserGender
import com.soulmate.ui.components.AvatarContainer
import com.soulmate.ui.components.EphemeralSubtitle
import com.soulmate.ui.components.HistoryDrawer
import com.soulmate.ui.components.MultimodalInputBar
import com.soulmate.ui.components.StreamingTextOverlay
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.viewmodel.ChatViewModel
import com.soulmate.ui.components.MemoryCardPopup
import com.soulmate.ui.components.TypingIndicator
import com.soulmate.ui.components.GlassNotification
import com.soulmate.ui.components.NeonDialog
import com.soulmate.ui.components.NotificationType
import com.soulmate.ui.theme.GlassSurface
import kotlinx.coroutines.delay
import com.soulmate.ui.components.ResonanceOrb
import com.soulmate.ui.components.CareCard
import com.soulmate.ui.components.PopUpCelebration
import com.soulmate.data.service.MindWatchService


/**
 * ImmersiveFusionScreen - 沉浸式融合屏幕 (The "Her" Interface)
 * 
 * 核心交互中心：
 * 1. Layer 0: 全屏数字人 (AvatarContainer)
 * 2. Layer 1: 隐形 UI (Subtitles) - 显示 AI 回复
 * 3. Layer 2: 悬浮输入 (Input Arc) - 语音/文本/多模态
 * 4. Layer 3: 历史抽屉 (History Drawer) - 上滑查看历史
 */
import com.soulmate.ui.theme.SoulMateThemeMode

@Composable
fun ImmersiveFusionScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToGarden: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    currentThemeMode: SoulMateThemeMode = SoulMateThemeMode.Tech,
    onThemeChange: (SoulMateThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    val userGender by viewModel.userGender.collectAsState()
    val chatState by viewModel.chatState.collectAsState()
    val isVoiceInputActive by viewModel.isVoiceInputActive.collectAsState()
    val voiceInputText by viewModel.voiceInputText.collectAsState()
    val currentUIEvent by viewModel.currentUIEvent.collectAsState()
    val pendingImageGen by viewModel.pendingImageGen.collectAsState()
    val mindWatchStatus by viewModel.mindWatchStatus.collectAsState(initial = MindWatchService.WatchStatus.NORMAL)
    val anniversaryPopup by viewModel.showAnniversaryPopup.collectAsState()
    val handsFreeMode by viewModel.handsFreeMode.collectAsState()

    
    // UI States
    var isHistoryVisible by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Subtitle Logic
    var visibleSubtitleText by remember { mutableStateOf("") }
    var isSubtitleVisible by remember { mutableStateOf(false) }

    LaunchedEffect(chatState.currentStreamToken) {
        if (chatState.currentStreamToken.isNotEmpty()) {
            visibleSubtitleText = chatState.currentStreamToken
            isSubtitleVisible = true
        } else {
             if (visibleSubtitleText.isNotEmpty()) {
                 delay(5000)
                 isSubtitleVisible = false
             }
        }
    }
    
    LaunchedEffect(chatState.messages.lastOrNull()) {
        val lastMsg = chatState.messages.lastOrNull()
        if (lastMsg != null && !lastMsg.isFromUser && chatState.currentStreamToken.isEmpty()) {
             visibleSubtitleText = lastMsg.content
             isSubtitleVisible = true
             delay(5000)
             isSubtitleVisible = false
        }
    }

    // Media Pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedImageUri = uri
        selectedVideoUri = null 
    }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedVideoUri = uri
        selectedImageUri = null
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
             viewModel.startVoiceInput()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Restoring dark background to fix white screen issue.
            // Using dynamic SoulMateTheme colors to support theme switching.
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SoulMateTheme.colors.bgBase,
                        SoulMateTheme.colors.bgGradientStart,
                        SoulMateTheme.colors.bgGradientEnd
                    )
                )
            )
            // Gesture listener for swipe up to show history
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -50) { // Swipe Up
                        isHistoryVisible = true
                    }
                }
            }
    ) {
        // --- Layer -1: Particle Effect ---
        // Subtle background particles behind the avatar
        com.soulmate.ui.components.ParticleBackground(
            modifier = Modifier.fillMaxSize(),
            particleColor = SoulMateTheme.colors.particleColor,
            lineColor = SoulMateTheme.colors.cardBorder
        )

        // --- Layer 0: Full Screen Avatar ---
        if (viewModel.avatarCoreService != null && userGender != UserGender.UNSET) {
            AvatarContainer(
                avatarCoreService = viewModel.avatarCoreService!!,
                userGender = userGender,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder while loading
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 androidx.compose.material3.CircularProgressIndicator(color = Color.White)
            }
        }

        // --- Gradient Mask (Bottom) ---
        // 确保底部的文字和输入控件在复杂背景上依然清晰
        // Fixed: Use standard black/dark colors instead of gray to match theme
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.95f) // Darker bottom
                        )
                    )
                )
        )

        // --- Layer 0.5: Resonance Orb (Top Left) ---
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp)
        ) {
            ResonanceOrb(
                status = mindWatchStatus,
                size = 80.dp
            )
        }

        // --- Layer 1.2: Care Card (Above Subtitles/Input) ---
        if (mindWatchStatus == MindWatchService.WatchStatus.WARNING || 
            mindWatchStatus == MindWatchService.WatchStatus.CRISIS) {
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
                        else -> ""
                    },
                    onCallHelp = { /* TODO */ },
                    onDismiss = { /* TODO */ }
                )
            }
        }


        // --- Layer 1: Ephemeral Subtitles ---
        Box(
             modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp, start = 32.dp, end = 32.dp) // Leave space for input bar
        ) {
            EphemeralSubtitle(
                text = visibleSubtitleText,
                isVisible = isSubtitleVisible && !isHistoryVisible
            )
        }
        
        // --- Layer 1.5: Streaming User Speech Feedback ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp)
        ) {
            StreamingTextOverlay(
                text = voiceInputText,
                isListening = isVoiceInputActive
            )
        }
        
        // --- Layer 1.6: Typing Indicator ---
        AnimatedVisibility(
             visible = chatState.isLoading && chatState.currentStreamToken.isEmpty(),
             enter = fadeIn(),
             exit = fadeOut(),
             modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
        ) {
             TypingIndicator(
                 modifier = Modifier
                     .background(GlassSurface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                     .padding(horizontal = 16.dp, vertical = 8.dp),
                 dotColor = Color.White
             )
        }

        // --- Layer 2: Multimodal Input (The Arc) ---
        AnimatedVisibility(
            visible = !isHistoryVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
             MultimodalInputBar(
                 inputText = inputText,
                 onTextChange = { inputText = it },
                 onSend = {
                     if (selectedImageUri != null) {
                         viewModel.sendMessageWithImage(inputText, selectedImageUri.toString())
                         selectedImageUri = null
                     } else if (selectedVideoUri != null) {
                         viewModel.sendMessageWithVideo(inputText, selectedVideoUri.toString())
                         selectedVideoUri = null
                     } else {
                         viewModel.sendMessage(inputText)
                     }
                     inputText = ""
                 },
                 isVoiceActive = isVoiceInputActive,
                 onStartVoice = { 
                     // Check permission
                     if (ContextCompat.checkSelfPermission(
                             context,
                             android.Manifest.permission.RECORD_AUDIO
                         ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                     ) {
                         viewModel.startVoiceInput()
                     } else {
                         permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                     }
                 },
                 onStopVoice = { viewModel.stopVoiceInput() },
                 onPickImage = {
                      photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                 },
                 onPickVideo = {
                      videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                      )
                 },
                 selectedImageUri = selectedImageUri,
                 selectedVideoUri = selectedVideoUri,
                 onClearMedia = { 
                     selectedImageUri = null
                     selectedVideoUri = null 
                 },
                 handsFreeMode = handsFreeMode,
                 onToggleHandsFree = { locked ->
                     viewModel.setHandsFreeMode(locked)
                 }
             )
        }
        
        // --- Top Controls (Menu) ---
        // Top Right Menu for Navigation (Restoring missing features)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
        ) {
            var expanded by remember { mutableStateOf(false) }
            
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = Color.White
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.9f))
            ) {
                DropdownMenuItem(
                    text = { Text("🔮 时光琥珀", color = Color.White) },
                    onClick = { 
                        expanded = false
                        onNavigateToGarden() 
                    }
                )
                DropdownMenuItem(
                    text = { Text("🎨 切换主题: ${currentThemeMode.name}", color = Color.White) },
                    onClick = { 
                        // Cycle themes: Tech -> Warm -> Fresh -> Tech
                        val nextTheme = when(currentThemeMode) {
                            SoulMateThemeMode.Tech -> SoulMateThemeMode.Warm
                            SoulMateThemeMode.Warm -> SoulMateThemeMode.Fresh
                            SoulMateThemeMode.Fresh -> SoulMateThemeMode.Tech
                        }
                        onThemeChange(nextTheme)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("⚙️ 设置", color = Color.White) },
                    onClick = { 
                        expanded = false
                        onNavigateToSettings() 
                    }
                )
            }
        }

        // --- Layer 3: History Drawer ---
        HistoryDrawer(
            messages = chatState.messages,
            isVisible = isHistoryVisible,
            onClose = { isHistoryVisible = false }
        )
        
        // --- Layer 4: Popups and Overlays ---
        
        // Memory Card Popup
        MemoryCardPopup(
            uiEvent = currentUIEvent,
            visible = currentUIEvent != null,
            onDismiss = { viewModel.dismissUIEvent() }
        )
        
        // Image Gen Confirmation (Neon Style)
        if (pendingImageGen != null) {
            NeonDialog(
                title = "✨ 捕捉到灵光",
                content = pendingImageGen!!.prompt,
                onConfirm = { viewModel.confirmImageGeneration() },
                onDismiss = { viewModel.cancelImageGeneration() },
                confirmText = "🔮 显化",
                dismissText = "放弃"
            )
        }
        
        // Notifications (Glass Style) - Stacked from top
        // Error Notification
        GlassNotification(
            message = chatState.error ?: "",
            type = NotificationType.Error,
            isVisible = chatState.error != null,
            onDismiss = { viewModel.clearError() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp) // Below Menu
        )
        
        // Warning Notification
        GlassNotification(
            message = chatState.warning ?: "",
            type = NotificationType.Warning,
            isVisible = chatState.warning != null && chatState.error == null, // Show warning only if no error to avoid overlap
            onDismiss = { viewModel.clearWarning() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp)
        )
    }
    
    // --- Layer 5: Global Celebration ---
    if (anniversaryPopup != null) {
        PopUpCelebration(
            visible = true,
            title = "Happy ${anniversaryPopup!!.name}!",
            message = anniversaryPopup!!.message ?: "Today is a special day for us.",
            onDismiss = { viewModel.dismissAnniversaryPopup() }
        )
    }
}
