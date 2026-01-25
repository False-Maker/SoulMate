package com.soulmate.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * EphemeralSubtitle - 短暂的字幕组件
 * for displaying AI responses in a subtitle style
 */
@Composable
fun EphemeralSubtitle(
    text: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && text.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * StreamingTextOverlay - 实时语音转文字反馈
 */
@Composable
fun StreamingTextOverlay(
    text: String,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    if (isListening) {
        Box(
            modifier = modifier
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // simple pulsing dot
                PulsingDot()
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text.ifEmpty { "Listening..." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun PulsingDot() {
    // A simple static dot for now, can be animated later
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(Color.Red, shape = RoundedCornerShape(50))
    )
}

/**
 * MultimodalInputBar - 底部多模态输入栏 (The "Arc")
 *
 * Supports:
 * - Voice Input (Hold to speak)
 * - Text Input (Expandable)
 * - Media Input (Image/Video Picker)
 */
@Composable
fun MultimodalInputBar(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isVoiceActive: Boolean,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    selectedImageUri: android.net.Uri?,
    selectedVideoUri: android.net.Uri?,
    onClearMedia: () -> Unit,
    handsFreeMode: Boolean = false,
    onToggleHandsFree: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isTextMode by remember { mutableStateOf(false) }
    var isMediaMenuExpanded by remember { mutableStateOf(false) } // State for media selection menu

    // If we have selected media, we should show the send button and maybe force text mode or at least show previews
    val hasMedia = selectedImageUri != null || selectedVideoUri != null
    
    // Auto-switch to text mode if we have text content
    LaunchedEffect(inputText) {
        if (inputText.isNotEmpty()) {
            isTextMode = true
        }
    }

    // Glass Container for the Input Bar
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .background(
                color = Color.Black.copy(alpha = 0.3f), // Glassy dark background
                shape = RoundedCornerShape(32.dp)
            )
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(32.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp) // Inner padding
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // --- Media Preview Layer ---
            AnimatedVisibility(visible = hasMedia) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 12.dp, top = 8.dp) // Add top padding inside container
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedImageUri != null) {
                        coil.compose.AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Selected Image",
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    if (selectedVideoUri != null) {
                         // Video thumbnail placeholder or simple icon
                         Box(modifier = Modifier
                             .size(60.dp)
                             .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                             contentAlignment = Alignment.Center
                         ) {
                             androidx.compose.material.icons.Icons.Default.Videocam.let {
                                 androidx.compose.material3.Icon(it, contentDescription = null, tint = Color.White)
                             }
                         }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    androidx.compose.material3.IconButton(onClick = onClearMedia) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // --- Input Controls Row ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            
            // 1. Text/Keyboard Toggle
            androidx.compose.material3.IconButton(
                onClick = { isTextMode = !isTextMode },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (isTextMode) androidx.compose.material.icons.Icons.Default.KeyboardHide else androidx.compose.material.icons.Icons.Default.Keyboard,
                    contentDescription = "Toggle Keyboard",
                    tint = Color.White
                )
            }

            // 2. Center: Voice Button OR Text Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isTextMode || hasMedia) {
                    // Text Input Field
                    androidx.compose.material3.OutlinedTextField(
                        value = inputText,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape),
                        placeholder = { Text("Send a message...", color = Color.Gray) },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White
                        ),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend() }),
                        trailingIcon = {
                             androidx.compose.material3.IconButton(onClick = onSend) {
                                 androidx.compose.material3.Icon(
                                     imageVector = androidx.compose.material.icons.Icons.Default.Send,
                                     contentDescription = "Send",
                                     tint = Color.Cyan
                                 )
                             }
                        }
                    )
                } else {
                    // Voice Button (Hold to Speak)
                    // We use a Box with pointerInput to detect hold
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                    colors = if (isVoiceActive) 
                                        listOf(Color.Cyan.copy(alpha = 0.8f), Color.Blue.copy(alpha = 0.4f)) 
                                    else 
                                        listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.05f))
                                ),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                            .pointerInput(handsFreeMode) {
                                detectVerticalDragGestures(
                                    onDragStart = { },
                                    onDragEnd = { 
                                        if (!handsFreeMode) onStopVoice() 
                                    },
                                    onDragCancel = { 
                                        if (!handsFreeMode) onStopVoice() 
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        // Drag Up (Negative Y) -> Toggle Lock Logic
                                        if (dragAmount < -10) {
                                            if (!handsFreeMode) {
                                                 // Unlocked -> Lock
                                                 onToggleHandsFree(true)
                                            } else {
                                                 // Locked -> Unlock
                                                 onToggleHandsFree(false)
                                                 onStopVoice()
                                            }
                                        }
                                        // Removed Drag Down unlock per user request
                                    }
                                )
                            }
                            .pointerInput(handsFreeMode) {
                                detectTapGestures(
                                    onPress = {
                                        if (handsFreeMode) {
                                            // Tap to stop hands-free (IMMEDIATE)
                                            onToggleHandsFree(false)
                                            onStopVoice()
                                        } else {
                                            // Hold to speak (Normal Mode)
                                            onStartVoice()
                                            tryAwaitRelease()
                                            // Only stop if we haven't switched to hands-free during the hold (e.g. via drag)
                                            if (!handsFreeMode) {
                                                onStopVoice()
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (handsFreeMode) {
                            // Flow Mode Visual: Wave
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "Wave")
                                repeat(3) { index ->
                                    val height by infiniteTransition.animateFloat(
                                        initialValue = 10f,
                                        targetValue = 24f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(500, delayMillis = index * 100, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "BarHeight"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp, height.dp)
                                            .background(Color.Cyan, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        } else {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Mic,
                                contentDescription = "Hold to Speak",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // 3. Media Picker (Right)
            Box {
                androidx.compose.material3.IconButton(
                    onClick = { isMediaMenuExpanded = true },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Image,
                        contentDescription = "Pick Media",
                        tint = Color.White
                    )
                }
                
                DropdownMenu(
                    expanded = isMediaMenuExpanded,
                    onDismissRequest = { isMediaMenuExpanded = false },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.9f))
                ) {
                    DropdownMenuItem(
                        text = { Text("📷 图片 (Picture)", color = Color.White) },
                        onClick = { 
                            isMediaMenuExpanded = false
                            onPickImage() 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🎥 视频 (Video)", color = Color.White) },
                        onClick = { 
                            isMediaMenuExpanded = false
                            onPickVideo() 
                        }
                    )
                }
            }
        }
    }
    } // End of Glass Container Box
}
