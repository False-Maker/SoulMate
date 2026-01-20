package com.soulmate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.soulmate.data.model.UIEvent
import com.soulmate.data.service.AvatarCoreService
import com.soulmate.ui.components.*
import com.soulmate.ui.theme.*
import com.soulmate.ui.theme.RedMei
import com.soulmate.ui.theme.ChampagneGold
import com.soulmate.ui.theme.TextPrimary
import com.soulmate.ui.theme.TextSecondary

/**
 * HomeScreen - Ethereal Redesign
 * 
 * Implements the "Ethereal Connection" design:
 * - Full screen FluidBackground
 * - Avatar integrated in background
 * - Glassmorphism cards for menu actions
 */
@Composable
fun HomeScreen(
    avatarCoreService: AvatarCoreService? = null,
    onNavigateToChat: () -> Unit = {},
    onNavigateToGarden: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    // 视频弹窗状态
    var showVideoDialog by remember { mutableStateOf(false) }
    var currentVideoUrl by remember { mutableStateOf<String?>(null) }
    var currentCoverUrl by remember { mutableStateOf<String?>(null) }
    
    // 收集 UI 事件
    LaunchedEffect(avatarCoreService) {
        avatarCoreService?.uiEventFlow?.collect { event ->
            when (event.type) {
                UIEvent.TYPE_WIDGET_VIDEO -> {
                    event.videoUrl?.let { url ->
                        currentVideoUrl = url
                        currentCoverUrl = event.coverUrl
                        showVideoDialog = true
                    }
                }
                UIEvent.TYPE_WIDGET_CLOSE -> {
                    showVideoDialog = false
                    currentVideoUrl = null
                    currentCoverUrl = null
                }
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Base Layer: Fluid Background
        FluidBackground()

        // 2. Avatar Layer
        if (avatarCoreService != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                AvatarContainer(
                    avatarCoreService = avatarCoreService,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // 3. UI Layer (Glassmorphism)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .background(GlassSurface, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }

            // Center / Bottom Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SoulMate",
                        style = MaterialTheme.typography.displayMedium,
                        color = ChampagneGold
                    )
                    Text(
                        text = "Eternal Connection",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons (Using GlassBubble as buttons)
                GlassBubble(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(24.dp),
                    onClick = onNavigateToChat
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = ChampagneGold,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Start Chat",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Connect with Eleanor",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                GlassBubble(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(24.dp),
                    onClick = onNavigateToGarden
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = RedMei, // Red Mei for Memory Garden
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Memory Garden",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "View shared memories",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // Video Popup
        if (showVideoDialog && currentVideoUrl != null) {
            VideoBottomSheet(
                videoUrl = currentVideoUrl!!,
                coverUrl = currentCoverUrl,
                onDismiss = {
                    showVideoDialog = false
                    currentVideoUrl = null
                    currentCoverUrl = null
                }
            )
        }
    }
}
