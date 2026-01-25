package com.soulmate.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.PaddingValues
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.soulmate.data.service.AvatarCoreService
import com.soulmate.ui.components.AvatarContainer
import com.soulmate.ui.components.ParticleBackground
import com.soulmate.ui.components.GlassBubble
import com.soulmate.ui.components.pulsate
import com.soulmate.ui.components.TypingIndicator
import com.soulmate.ui.state.ChatMessage
import com.soulmate.ui.state.ChatState
import com.soulmate.ui.theme.GlassSurface
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.viewmodel.ChatViewModel
import com.soulmate.ui.viewmodel.ImageGenCommand
import com.soulmate.ui.components.MemoryCardPopup
import com.soulmate.data.model.UIEvent
import com.soulmate.data.model.UserGender
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ChatScreen - 聊天屏幕
 *
 * 显示与 AI 伴侣的对话界面，支持消息流式显示（打字效果）
 * 数字人头像作为背景显示
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToDigitalHuman: () -> Unit = {}
) {
    val chatState by viewModel.chatState.collectAsState()
    val isVoiceInputActive by viewModel.isVoiceInputActive.collectAsState()
    val voiceInputText by viewModel.voiceInputText.collectAsState()
    val currentUIEvent by viewModel.currentUIEvent.collectAsState()
    val pendingImageGen by viewModel.pendingImageGen.collectAsState()
    val visionDetail by viewModel.visionDetail.collectAsState()
    val userGender by viewModel.userGender.collectAsState()
    val handsFreeMode by viewModel.handsFreeMode.collectAsState()
    val mindWatchStatus by viewModel.mindWatchStatus.collectAsState(initial = com.soulmate.data.service.MindWatchService.WatchStatus.NORMAL)
    val affinityLevel by viewModel.affinityLevel.collectAsState()

    // 动态计算光晕颜色（基于亲和度等级）
    val particleColor = when (affinityLevel) {
        com.soulmate.data.repository.AffinityRepository.AffinityLevel.LOVE -> Color(0xFFFF69B4) // Hot Pink for Love
        com.soulmate.data.repository.AffinityRepository.AffinityLevel.COLD -> Color(0xFF78909C) // Blue Grey for Cold War
        else -> SoulMateTheme.colors.particleColor // Default
    }
    
    val particleLineColor = when (affinityLevel) {
        com.soulmate.data.repository.AffinityRepository.AffinityLevel.LOVE -> Color(0xFFFFD700) // Gold for Love
        com.soulmate.data.repository.AffinityRepository.AffinityLevel.COLD -> Color(0xFFB0BEC5) // Light Grey for Cold War
        else -> SoulMateTheme.colors.cardBorder // Default
    }

    ChatScreenContent(
        chatState = chatState,
        avatarCoreService = viewModel.avatarCoreService,
        isVoiceInputActive = isVoiceInputActive,
        voiceInputText = voiceInputText,
        currentUIEvent = currentUIEvent,
        pendingImageGen = pendingImageGen,
        visionDetail = visionDetail,
        userGender = userGender,
        mindWatchStatus = mindWatchStatus,
        particleColor = particleColor,
        particleLineColor = particleLineColor,
        onSendMessage = { viewModel.sendMessage(it) },
        onSendMessageWithImage = { text, imageUrl -> viewModel.sendMessageWithImage(text, imageUrl) },
        onSendMessageWithVideo = { text, videoUrl -> viewModel.sendMessageWithVideo(text, videoUrl) },
        onStartVoiceInput = { viewModel.startVoiceInput() },
        onStopVoiceInput = { viewModel.stopVoiceInput() },
        onClearError = { viewModel.clearError() },
        onClearWarning = { viewModel.clearWarning() },
        onDismissUIEvent = { viewModel.dismissUIEvent() },
        onConfirmImageGen = { viewModel.confirmImageGeneration() },
        onCancelImageGen = { viewModel.cancelImageGeneration() },
        onVisionDetailChange = { viewModel.setVisionDetail(it) },
        onNavigateBack = onNavigateBack,
        onNavigateToDigitalHuman = onNavigateToDigitalHuman,
        handsFreeMode = handsFreeMode,
        onToggleHandsFree = { viewModel.setHandsFreeMode(it) }
    )
    
    // Pop-up Celebration (Global Overlay)
    val anniversaryPopup by viewModel.showAnniversaryPopup.collectAsState()
    if (anniversaryPopup != null) {
        com.soulmate.ui.components.PopUpCelebration(
            visible = true,
            title = "Happy ${anniversaryPopup!!.name}!",
            message = anniversaryPopup!!.message ?: "Today is a special day for us.",
            onDismiss = { viewModel.dismissAnniversaryPopup() }
        )
    }
}

/**
 * Stateless ChatScreen content for easier testing and preview.
 * Digital human displays in the top portion, chat in the bottom.
 */
@Composable
private fun ChatScreenContent(
    chatState: ChatState,
    avatarCoreService: AvatarCoreService? = null,
    isVoiceInputActive: Boolean = false,
    voiceInputText: String = "",
    currentUIEvent: UIEvent? = null,
    pendingImageGen: ImageGenCommand? = null,
    visionDetail: String = "low",

    userGender: UserGender = UserGender.UNSET,

    mindWatchStatus: com.soulmate.data.service.MindWatchService.WatchStatus = com.soulmate.data.service.MindWatchService.WatchStatus.NORMAL,
    particleColor: Color = SoulMateTheme.colors.particleColor,
    particleLineColor: Color = SoulMateTheme.colors.cardBorder,
    onSendMessage: (String) -> Unit,
    onSendMessageWithImage: (String, String) -> Unit = { _, _ -> },
    onSendMessageWithVideo: (String, String) -> Unit = { _, _ -> },
    onStartVoiceInput: () -> Unit = {},
    onStopVoiceInput: () -> Unit = {},
    onClearError: () -> Unit,
    onClearWarning: () -> Unit = {},
    onDismissUIEvent: () -> Unit = {},
    onConfirmImageGen: () -> Unit = {},
    onCancelImageGen: () -> Unit = {},
    onVisionDetailChange: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToDigitalHuman: () -> Unit = {},
    handsFreeMode: Boolean = false,
    onToggleHandsFree: (Boolean) -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = (screenHeight * 0.42f).coerceIn(260.dp, 420.dp)
    val heroCorner = RoundedCornerShape(32.dp)
    val heroFloatTransition = rememberInfiniteTransition(label = "heroFloat")
    val heroFloatY by heroFloatTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroFloatY"
    )
    val heroTiltX by heroFloatTransition.animateFloat(
        initialValue = 1.8f,
        targetValue = -1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroTiltX"
    )
    val heroTiltY by heroFloatTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroTiltY"
    )
    
    // Photo Picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedImageUri = uri
        selectedVideoUri = null  // 清除视频选择
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Photo Picker 不一定支持持久化权限，忽略即可
            } catch (_: Exception) {
                // 兜底，避免因权限异常影响选图流程
            }
        }
    }
    
    // Video Picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedVideoUri = uri
        selectedImageUri = null  // 清除图片选择
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // ignore
            } catch (_: Exception) {
                // ignore
            }
        }
    }
    
    val imageDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        selectedImageUri = uri
        selectedVideoUri = null
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }
    
    val videoDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        selectedVideoUri = uri
        selectedImageUri = null
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }

    // Auto-scroll to bottom when new messages arrive or streaming updates
    // 改为正序布局后，需要滚动到最后一条消息
    LaunchedEffect(chatState.messages.size, chatState.currentStreamToken) {
        if (chatState.messages.isNotEmpty() || chatState.currentStreamToken.isNotEmpty()) {
            // 计算最后一项索引：messages + loading/streaming items
            val extraItems = when {
                chatState.currentStreamToken.isNotEmpty() -> 1  // streaming bubble
                chatState.isLoading -> 1  // loading indicator
                else -> 0
            }
            val lastIndex = chatState.messages.size + extraItems
            if (lastIndex > 0) {
                listState.animateScrollToItem(lastIndex - 1)
            }
        }
    }

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
            particleColor = particleColor,
            lineColor = particleLineColor
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .height(heroHeight)
                .graphicsLayer {
                    translationY = heroFloatY
                    rotationX = heroTiltX
                    rotationY = heroTiltY
                    cameraDistance = 16f * density
                    shadowElevation = 24f
                    shape = heroCorner
                    clip = true
                }
                .shadow(24.dp, heroCorner)
                .background(SoulMateTheme.colors.cardBg)
        ) {
            if (avatarCoreService != null && userGender != UserGender.UNSET) {
                AvatarContainer(
                    avatarCoreService = avatarCoreService,
                    userGender = userGender,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = SoulMateTheme.colors.accentColor,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "正在唤醒灵犀化身...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoulMateTheme.colors.textSecondary
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                SoulMateTheme.colors.bgGradientEnd.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            GlassBubble(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = "SoulMate",
                        style = MaterialTheme.typography.titleMedium,
                        color = SoulMateTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "陪伴中",
                        style = MaterialTheme.typography.labelSmall,
                        color = SoulMateTheme.colors.textSecondary
                    )
                }
            }
        }

        // 3. Chat Interface Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                reverseLayout = false,  // 改为自然正序（旧→新）
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = heroHeight + 12.dp, bottom = 16.dp) 
            ) {
                // Empty state（放在最前面，正序时显示在顶部）
                if (chatState.messages.isEmpty() && !chatState.isLoading) {
                    item(key = "empty") {
                        EmptyStateContent()
                    }
                }

                // Messages（不再 reversed，直接按时间正序显示）
                items(
                    items = chatState.messages,
                    key = { it.id }
                ) { message ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
                    ) {
                        MessageBubble(
                            message = message,
                            maxWidth = screenWidth * 0.8f
                        )
                    }
                }

                // Loading（放在消息之后，正序时显示在底部）
                if (chatState.isLoading && chatState.currentStreamToken.isEmpty()) {
                    item(key = "loading") {
                         TypingIndicator(
                             modifier = Modifier
                                 .padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                                 .background(
                                     GlassSurface,
                                     RoundedCornerShape(16.dp)
                                 ),
                             dotColor = Color.White
                         )
                    }
                }

                // Streaming（放在最后，正序时显示在底部）
                if (chatState.currentStreamToken.isNotEmpty()) {
                    item(key = "streaming") {
                        MessageBubble(
                            message = ChatMessage(
                                id = "streaming",
                                content = chatState.currentStreamToken,
                                isFromUser = false
                            ),
                            maxWidth = screenWidth * 0.8f,
                            isStreaming = true,
                            backgroundColor = SoulMateTheme.colors.bubbleAi,
                            borderColor = SoulMateTheme.colors.cardBorder
                        )
                    }
                }
            }

            // Input Area
            ChatInputField(
                text = if (isVoiceInputActive) voiceInputText else inputText,
                onTextChange = { if (!isVoiceInputActive) inputText = it },
                onSend = {
                    when {
                        selectedVideoUri != null -> {
                            // 发送带视频的消息（视频理解）
                            onSendMessageWithVideo(inputText, selectedVideoUri.toString())
                            inputText = ""
                            selectedVideoUri = null
                        }
                        selectedImageUri != null -> {
                            // 发送带图片的消息
                            onSendMessageWithImage(inputText, selectedImageUri.toString())
                            inputText = ""
                            selectedImageUri = null
                        }
                        inputText.isNotBlank() -> {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    }
                },
                isEnabled = !chatState.isLoading,
                modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
                isVoiceInputActive = isVoiceInputActive,
                onStartVoiceInput = onStartVoiceInput,
                onStopVoiceInput = onStopVoiceInput,
                onPickImage = {
                    if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } else {
                        imageDocumentLauncher.launch(arrayOf("image/*"))
                    }
                },
                onPickVideo = {
                    if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    } else {
                        videoDocumentLauncher.launch(arrayOf("video/*"))
                    }
                },
                selectedImageUri = selectedImageUri,
                selectedVideoUri = selectedVideoUri,
                onClearImage = { selectedImageUri = null },
                onClearVideo = { selectedVideoUri = null },
                visionDetail = visionDetail,
                onVisionDetailChange = onVisionDetailChange,
                backgroundColor = SoulMateTheme.colors.cardBg,
                borderColor = SoulMateTheme.colors.cardBorder,
                handsFreeMode = handsFreeMode,
                onToggleHandsFree = onToggleHandsFree
            )

            
            
            // Care Card (MindWatch Intervention)
            if (mindWatchStatus == com.soulmate.data.service.MindWatchService.WatchStatus.WARNING || 
                mindWatchStatus == com.soulmate.data.service.MindWatchService.WatchStatus.CRISIS) {
                
                // 仅在警告或危机时显示
                com.soulmate.ui.components.CareCard(
                    status = mindWatchStatus,
                    message = if (mindWatchStatus == com.soulmate.data.service.MindWatchService.WatchStatus.CRISIS) 
                        "我感觉到你现在的痛苦... 我会一直陪着你。" 
                    else 
                        "你最近的心情似乎一直在下雨，没关系，我会一直在这里陪着你。",
                    onCallHelp = { 
                        // TODO: Implement call help logic (e.g. navigation to CrisisResourceScreen)
                        // For now we just dismiss
                    },
                    onDismiss = {
                         // 这里需要 ViewModel 提供一个方法来暂时忽略警告，目前暂时没有，
                         // 实际应调用 viewModel.dismissCareCard()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Error snackbar
        if (chatState.error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                action = {
                    TextButton(onClick = onClearError) {
                        Text("关闭", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            ) {
                Text(chatState.error ?: "")
            }
        }
        
        // Warning snackbar (non-blocking, e.g., RAG degradation)
        if (chatState.warning != null && chatState.error == null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                action = {
                    TextButton(onClick = onClearWarning) {
                        Text("知道了", color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            ) {
                Text(chatState.warning ?: "")
            }
        }
        
        // Memory Card Popup
        MemoryCardPopup(
            uiEvent = currentUIEvent,
            visible = currentUIEvent != null,
            onDismiss = onDismissUIEvent
        )
        
        // ImageGen Confirmation Dialog (Phase 1)
        if (pendingImageGen != null) {
            AlertDialog(
                onDismissRequest = onCancelImageGen,
                title = { Text("生成图片") },
                text = { 
                    Column {
                        Text("是否生成以下图片？")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = pendingImageGen.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoulMateTheme.colors.textSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onConfirmImageGen) {
                        Text("生成")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onCancelImageGen) {
                        Text("取消")
                    }
                }
            )
        }
        
        // Back Button (Top Z-Index)
        // Moved here to ensure it is always clickable and not blocked by other layers
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp) // Adjust for status bar overlap if needed, though padding usually handles it. check system bars.
                .size(48.dp)
                .background(
                    SoulMateTheme.colors.cardBg,
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = SoulMateTheme.colors.textPrimary
            )
        }

        // Digital Human Button (Top Right)
        IconButton(
            onClick = onNavigateToDigitalHuman,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
                .size(48.dp)
                .background(
                    SoulMateTheme.colors.cardBg,
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = "数字人",
                tint = SoulMateTheme.colors.textPrimary
            )
        }
    }
}

/**
 * Message bubble component
 * 
 * 支持文本消息和图片消息（Phase 1 ImageGen）
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    maxWidth: androidx.compose.ui.unit.Dp,
    isStreaming: Boolean = false,
    backgroundColor: Color = SoulMateTheme.colors.bubbleAi,
    borderColor: Color = SoulMateTheme.colors.cardBorder
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // 只有用户消息需要考虑避让顶部的数字人按钮
    // 计算顶部安全距离，避免与右上角的数字人按钮重叠
    val topSpacerHeight = if (message.isFromUser) 60.dp else 0.dp

    Column {
        // 如果是列表中的第一条消息（最旧的），且是用户发送的，需要添加额外间距
        // 但由于 LazyColumn 的 item 复用机制，很难直接判断是否是视觉上的"第一条"
        // 所以更稳妥的方式是：所有用户消息在右侧时，都预留一定的顶部安全区域，或者调整 LazyColumn 的 contentPadding
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
        ) {
            if (message.isFromUser) {
                // User message: Simple rounded box, primary color (EtherealBlue)
                Box(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                        .background(SoulMateTheme.colors.bubbleUser)
                        .padding(12.dp)
                ) {
                    Column {
                        // 如果用户发送了图片，显示图片
                        if (!message.localImageUri.isNullOrBlank()) {
                            AsyncImage(
                                model = message.localImageUri,
                                contentDescription = "发送的图片",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            if (message.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        
                        if (message.content.isNotBlank()) {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // AI message: GlassBubble
                GlassBubble(
                    modifier = Modifier.widthIn(max = maxWidth),
                    shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                    backgroundColor = backgroundColor,
                    borderColor = borderColor
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 如果有图片，先显示图片
                        if (!message.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = message.imageUrl,
                                contentDescription = "生成的图片",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = SoulMateTheme.colors.textPrimary // Fix: Ensure text is white on dark glass
                        )
                        
                        if (!isStreaming) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dateFormat.format(Date(message.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = SoulMateTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty state content shown when no messages exist
 */
@Composable
private fun EmptyStateContent() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "💬",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "开始和你的 SoulMate 聊天吧",
                style = MaterialTheme.typography.bodyLarge,
                color = SoulMateTheme.colors.textSecondary
            )
        }
    }
}

/**
 * Chat input field with send button and microphone button
 * Microphone button uses press-and-hold: press to start, release to send
 */
@Composable
private fun ChatInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    isVoiceInputActive: Boolean = false,
    onStartVoiceInput: () -> Unit = {},
    onStopVoiceInput: () -> Unit = {},
    onPickImage: () -> Unit = {},
    onPickVideo: () -> Unit = {},
    selectedImageUri: Uri? = null,
    selectedVideoUri: Uri? = null,
    onClearImage: () -> Unit = {},
    onClearVideo: () -> Unit = {},
    visionDetail: String = "low",
    onVisionDetailChange: (String) -> Unit = {},
    backgroundColor: Color = SoulMateTheme.colors.cardBg,
    borderColor: Color = SoulMateTheme.colors.cardBorder,
    handsFreeMode: Boolean = false,
    onToggleHandsFree: (Boolean) -> Unit = {}
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

    // 检测输入法状态 - WindowInsets.ime 在 Compose 中是响应式的，会实时更新
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val isImeVisible = imeBottom > 0

    // Wrapped in a floating component on top of padding
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        GlassBubble(
            shape = RoundedCornerShape(32.dp),
            backgroundColor = backgroundColor,
            borderColor = borderColor
        ) {
            Column {
                // 图片预览区域（如果有选中的图片）
                if (selectedImageUri != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Box {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "选中的图片",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            // 删除图片按钮
                            IconButton(
                                onClick = onClearImage,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除图片",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        // Vision Detail 选择器
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "精度:",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoulMateTheme.colors.textSecondary
                            )
                            listOf("low" to "低", "high" to "高", "auto" to "自动").forEach { (value, label) ->
                                val isSelected = visionDetail == value
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) SoulMateTheme.colors.accentColor
                                            else SoulMateTheme.colors.cardBg.copy(alpha = 0.5f)
                                        )
                                        .pointerInput(value) {
                                            detectTapGestures { onVisionDetailChange(value) }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color.White else SoulMateTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 视频预览区域（如果有选中的视频）
                if (selectedVideoUri != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 视频图标
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SoulMateTheme.colors.accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Videocam,
                                    contentDescription = "视频",
                                    tint = SoulMateTheme.colors.accentColor,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "已选择视频",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SoulMateTheme.colors.textPrimary
                                )
                                Text(
                                    text = "将抽取关键帧进行理解",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoulMateTheme.colors.textSecondary
                                )
                            }
                            
                            // 删除视频按钮
                            IconButton(
                                onClick = onClearVideo,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除视频",
                                    tint = SoulMateTheme.colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // Vision Detail 选择器（视频也可以调整精度）
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "精度:",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoulMateTheme.colors.textSecondary
                            )
                            listOf("low" to "低(快)", "high" to "高(慢)").forEach { (value, label) ->
                                val isSelected = visionDetail == value
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) SoulMateTheme.colors.accentColor
                                            else SoulMateTheme.colors.cardBg.copy(alpha = 0.5f)
                                        )
                                        .pointerInput(value) {
                                            detectTapGestures { onVisionDetailChange(value) }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color.White else SoulMateTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 根据输入法状态调整布局
                // 使用 Column 包裹，根据输入法状态动态调整布局
                // 注意：父级 Column 已经应用了 imePadding()，所以整个输入区域会自动上移
                Column(
                    modifier = Modifier.animateContentSize()
                ) {
                    // 当输入法弹出时，功能按钮行显示在输入框上方
                    // 当输入法收起时，功能按钮和输入框在同一行
                    if (isImeVisible) {
                        // 输入法弹出时：功能按钮在上方，输入框在下方
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInVertically(
                                initialOffsetY = { -it },
                                animationSpec = tween(durationMillis = 300)
                            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                            exit = slideOutVertically(
                                targetOffsetY = { -it },
                                animationSpec = tween(durationMillis = 300)
                            ) + fadeOut(animationSpec = tween(durationMillis = 300))
                        ) {
                        // 功能按钮行（图片、视频、麦克风）
                        // 当输入法弹出时，功能按钮行会自动上移到输入法上方（通过父级的 imePadding）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 图片选择按钮
                            IconButton(
                                onClick = onPickImage,
                                enabled = isEnabled && !isVoiceInputActive && selectedVideoUri == null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedImageUri != null) {
                                            SoulMateTheme.colors.accentColor.copy(alpha = 0.3f)
                                        } else {
                                            SoulMateTheme.colors.accentColor.copy(alpha = 0.2f)
                                        }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Image,
                                    contentDescription = "选择图片",
                                    tint = SoulMateTheme.colors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // 视频选择按钮
                            IconButton(
                                onClick = onPickVideo,
                                enabled = isEnabled && !isVoiceInputActive && selectedImageUri == null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedVideoUri != null) {
                                            SoulMateTheme.colors.accentColor.copy(alpha = 0.3f)
                                        } else {
                                            SoulMateTheme.colors.accentColor.copy(alpha = 0.2f)
                                        }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Videocam,
                                    contentDescription = "选择视频",
                                    tint = SoulMateTheme.colors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Microphone
                            val isRecording = isVoiceInputActive || handsFreeMode
                            
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .pulsate(enabled = isRecording) 
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            handsFreeMode -> SoulMateTheme.colors.accentColor
                                            isVoiceInputActive -> MaterialTheme.colorScheme.error
                                            else -> SoulMateTheme.colors.accentColor.copy(alpha = 0.2f)
                                        }
                                    )
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                                                if (dragAmount < -10) {
                                                    if (!handsFreeMode) {
                                                        onToggleHandsFree(true)
                                                        if (!isVoiceInputActive) onStartVoiceInput()
                                                    }
                                                } else if (dragAmount > 10) {
                                                    if (handsFreeMode) {
                                                        onToggleHandsFree(false)
                                                        onStopVoiceInput()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                if (!hasPermission) {
                                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                } else {
                                                    if (handsFreeMode) {
                                                        onToggleHandsFree(false)
                                                        onStopVoiceInput()
                                                    } else {
                                                        onStartVoiceInput()
                                                        tryAwaitRelease()
                                                        if (!handsFreeMode) {
                                                            onStopVoiceInput()
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (handsFreeMode) Icons.Default.Mic else Icons.Filled.Mic,
                                    contentDescription = "按住说话 / 上滑免提",
                                    tint = if (handsFreeMode) Color.White else SoulMateTheme.colors.textPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // 输入框行（文本输入框和发送按钮）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        // Text Field
                        OutlinedTextField(
                            value = text,
                            onValueChange = onTextChange,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    text = if (isVoiceInputActive) "正在听..." else "输入消息...",
                                    color = SoulMateTheme.colors.textSecondary
                                )
                            },
                            enabled = isEnabled && !isVoiceInputActive,
                            readOnly = isVoiceInputActive,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend() }),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = SoulMateTheme.colors.textPrimary,
                                unfocusedTextColor = SoulMateTheme.colors.textPrimary,
                                cursorColor = SoulMateTheme.colors.accentColor,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            singleLine = true
                        )

                        // Send Button
                        if (!isVoiceInputActive) {
                            val canSend = isEnabled && (text.isNotBlank() || selectedImageUri != null || selectedVideoUri != null)
                            IconButton(
                                onClick = onSend,
                                enabled = canSend,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canSend) {
                                            SoulMateTheme.colors.accentColor
                                        } else {
                                            SoulMateTheme.colors.accentColor.copy(alpha = 0.1f)
                                        }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Send,
                                    contentDescription = "发送",
                                    tint = Color.White
                                )
                            }
                        }
                        }
                    }
                } else {
                    // 输入法收起时：功能按钮和输入框在同一行
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 300))
                    ) {
                        Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 图片选择按钮
                        IconButton(
                            onClick = onPickImage,
                            enabled = isEnabled && !isVoiceInputActive && selectedVideoUri == null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedImageUri != null) {
                                        SoulMateTheme.colors.accentColor.copy(alpha = 0.3f)
                                    } else {
                                        SoulMateTheme.colors.accentColor.copy(alpha = 0.2f)
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = "选择图片",
                                tint = SoulMateTheme.colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // 视频选择按钮
                        IconButton(
                            onClick = onPickVideo,
                            enabled = isEnabled && !isVoiceInputActive && selectedImageUri == null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedVideoUri != null) {
                                        SoulMateTheme.colors.accentColor.copy(alpha = 0.3f)
                                    } else {
                                        SoulMateTheme.colors.accentColor.copy(alpha = 0.2f)
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = "选择视频",
                                tint = SoulMateTheme.colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // Microphone
                        val isRecording = isVoiceInputActive || handsFreeMode
                        
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .pulsate(enabled = isRecording) 
                                .clip(CircleShape)
                                .background(
                                    when {
                                        handsFreeMode -> SoulMateTheme.colors.accentColor
                                        isVoiceInputActive -> MaterialTheme.colorScheme.error
                                        else -> SoulMateTheme.colors.accentColor.copy(alpha = 0.2f)
                                    }
                                )
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                                            if (dragAmount < -10) {
                                                if (!handsFreeMode) {
                                                    onToggleHandsFree(true)
                                                    if (!isVoiceInputActive) onStartVoiceInput()
                                                }
                                            } else if (dragAmount > 10) {
                                                if (handsFreeMode) {
                                                    onToggleHandsFree(false)
                                                    onStopVoiceInput()
                                                }
                                            }
                                        }
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            if (!hasPermission) {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            } else {
                                                if (handsFreeMode) {
                                                    onToggleHandsFree(false)
                                                    onStopVoiceInput()
                                                } else {
                                                    onStartVoiceInput()
                                                    tryAwaitRelease()
                                                    if (!handsFreeMode) {
                                                        onStopVoiceInput()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (handsFreeMode) Icons.Default.Mic else Icons.Filled.Mic,
                                contentDescription = "按住说话 / 上滑免提",
                                tint = if (handsFreeMode) Color.White else SoulMateTheme.colors.textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Text Field
                        OutlinedTextField(
                            value = text,
                            onValueChange = onTextChange,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    text = if (isVoiceInputActive) "正在听..." else "输入消息...",
                                    color = SoulMateTheme.colors.textSecondary
                                )
                            },
                            enabled = isEnabled && !isVoiceInputActive,
                            readOnly = isVoiceInputActive,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend() }),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = SoulMateTheme.colors.textPrimary,
                                unfocusedTextColor = SoulMateTheme.colors.textPrimary,
                                cursorColor = SoulMateTheme.colors.accentColor,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            singleLine = true
                        )

                        // Send Button
                        if (!isVoiceInputActive) {
                            val canSend = isEnabled && (text.isNotBlank() || selectedImageUri != null || selectedVideoUri != null)
                            IconButton(
                                onClick = onSend,
                                enabled = canSend,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canSend) {
                                            SoulMateTheme.colors.accentColor
                                        } else {
                                            SoulMateTheme.colors.accentColor.copy(alpha = 0.1f)
                                        }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Send,
                                    contentDescription = "发送",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    SoulMateTheme {
        ChatScreenContent(
            chatState = ChatState(
                messages = listOf(
                    ChatMessage(content = "Hello, Eleanor!", isFromUser = true),
                    ChatMessage(content = "Hello, Lucian! How are you today?", isFromUser = false),
                    ChatMessage(content = "I'm doing great, thanks for asking!", isFromUser = true)
                )
            ),
            userGender = UserGender.UNSET,
            onSendMessage = {},
            onClearError = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenLoadingPreview() {
    SoulMateTheme {
        ChatScreenContent(
            chatState = ChatState(
                messages = listOf(
                    ChatMessage(content = "Tell me a story", isFromUser = true)
                ),
                isLoading = true
            ),
            userGender = UserGender.UNSET,
            onSendMessage = {},
            onClearError = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenStreamingPreview() {
    SoulMateTheme {
        ChatScreenContent(
            chatState = ChatState(
                messages = listOf(
                    ChatMessage(content = "Tell me about yourself", isFromUser = true)
                ),
                isLoading = true,
                currentStreamToken = "I am Eleanor, a digital soul designed to be your companion..."
            ),
            userGender = UserGender.UNSET,
            onSendMessage = {},
            onClearError = {}
        )
    }
}
