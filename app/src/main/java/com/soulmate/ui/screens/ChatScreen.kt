package com.soulmate.ui.screens

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
import androidx.compose.foundation.layout.imePadding
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.slideInVertically
import com.soulmate.data.service.AvatarCoreService
import com.soulmate.ui.components.AvatarContainer
import com.soulmate.ui.components.FluidBackground
import com.soulmate.ui.components.GlassBubble
import com.soulmate.ui.components.pulsate
import com.soulmate.ui.components.TypingIndicator
import com.soulmate.ui.state.ChatMessage
import com.soulmate.ui.state.ChatState
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.theme.EtherealBlue
import com.soulmate.ui.theme.GlassSurface
import com.soulmate.ui.theme.RedMei
import com.soulmate.ui.theme.ChampagneGold
import com.soulmate.ui.theme.TextPrimary
import com.soulmate.ui.theme.TextSecondary
import com.soulmate.ui.viewmodel.ChatViewModel
import com.soulmate.ui.components.MemoryCardPopup
import com.soulmate.data.model.UIEvent
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

    ChatScreenContent(
        chatState = chatState,
        avatarCoreService = viewModel.avatarCoreService,
        isVoiceInputActive = isVoiceInputActive,
        voiceInputText = voiceInputText,
        currentUIEvent = currentUIEvent,
        onSendMessage = { viewModel.sendMessage(it) },
        onStartVoiceInput = { viewModel.startVoiceInput() },
        onStopVoiceInput = { viewModel.stopVoiceInput() },
        onClearError = { viewModel.clearError() },
        onDismissUIEvent = { viewModel.dismissUIEvent() },
        onNavigateBack = onNavigateBack,
        onNavigateToDigitalHuman = onNavigateToDigitalHuman
    )
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
    onSendMessage: (String) -> Unit,
    onStartVoiceInput: () -> Unit = {},
    onStopVoiceInput: () -> Unit = {},
    onClearError: () -> Unit,
    onDismissUIEvent: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToDigitalHuman: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    // Auto-scroll to bottom when new messages arrive or streaming updates
    LaunchedEffect(chatState.messages.size, chatState.currentStreamToken) {
        if (chatState.messages.isNotEmpty() || chatState.currentStreamToken.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Fallback
    ) {
        // 1. Background
        FluidBackground()

        // 2. Avatar Layer REMOVED - Digital Human moved to separate screen
        /*
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .align(Alignment.TopCenter)
        ) {
             ...
        }
        */
        
        // 3. Chat Interface Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 80.dp, bottom = 16.dp) 
            ) {
                // Streaming
                if (chatState.currentStreamToken.isNotEmpty()) {
                    item(key = "streaming") {
                        MessageBubble(
                            message = ChatMessage(
                                id = "streaming",
                                content = chatState.currentStreamToken,
                                isFromUser = false
                            ),
                            maxWidth = screenWidth * 0.8f,
                            isStreaming = true
                        )
                    }
                }

                // Loading
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

                // Messages
                items(
                    items = chatState.messages.reversed(),
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

                // Empty state
                if (chatState.messages.isEmpty() && !chatState.isLoading) {
                    item(key = "empty") {
                        EmptyStateContent()
                    }
                }
            }

            // Input Area
            ChatInputField(
                text = if (isVoiceInputActive) voiceInputText else inputText,
                onTextChange = { if (!isVoiceInputActive) inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                isEnabled = !chatState.isLoading,
                isVoiceInputActive = isVoiceInputActive,
                onStartVoiceInput = onStartVoiceInput,
                onStopVoiceInput = onStopVoiceInput
            )
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
                        Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            ) {
                Text(chatState.error ?: "")
            }
        }
        
        // Memory Card Popup
        MemoryCardPopup(
            uiEvent = currentUIEvent,
            visible = currentUIEvent != null,
            onDismiss = onDismissUIEvent
        )
        
        // Back Button (Top Z-Index)
        // Moved here to ensure it is always clickable and not blocked by other layers
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp) // Adjust for status bar overlap if needed, though padding usually handles it. check system bars.
                .size(48.dp)
                .background(
                    GlassSurface,
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
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
                    GlassSurface,
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = "Digital Human",
                tint = Color.White
            )
        }
    }
}

/**
 * Message bubble component
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    maxWidth: androidx.compose.ui.unit.Dp,
    isStreaming: Boolean = false
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

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
                    .background(RedMei)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        } else {
            // AI message: GlassBubble
            GlassBubble(
                modifier = Modifier.widthIn(max = maxWidth),
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White // Fix: Ensure text is white on dark glass
                    )
                    
                    if (!isStreaming) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dateFormat.format(Date(message.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
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
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
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
    isVoiceInputActive: Boolean = false,
    onStartVoiceInput: () -> Unit = {},
    onStopVoiceInput: () -> Unit = {}
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

    // Wrapped in a floating component on top of padding
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        GlassBubble(
            shape = RoundedCornerShape(32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Microphone
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .pulsate(enabled = isVoiceInputActive) // Add pulsation
                        .clip(CircleShape)
                        .background(
                            if (isVoiceInputActive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color.White.copy(alpha = 0.2f)
                            }
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
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "按住说话",
                        tint = Color.White,
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
                            text = if (isVoiceInputActive) "Listening..." else "Message...",
                            color = Color.White.copy(alpha = 0.5f)
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
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    singleLine = true
                )

                // Send Button
                if (!isVoiceInputActive) {
                    IconButton(
                        onClick = onSend,
                        enabled = isEnabled && text.isNotBlank(),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEnabled && text.isNotBlank()) {
                                    RedMei
                                } else {
                                    Color.White.copy(alpha = 0.1f)
                                }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
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
            onSendMessage = {},
            onClearError = {}
        )
    }
}
