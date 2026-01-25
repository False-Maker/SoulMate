package com.soulmate.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.soulmate.R
import com.soulmate.ui.components.ParallaxGlassCard
import com.soulmate.ui.components.ParticleBackground
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.theme.SoulMateThemeMode

/**
 * HomeScreen - Ethereal & Tech Redesign
 *
 * Implements the new high-fidelity design with:
 * - Dynamic Theme Switching (Tech/Warm/Fresh)
 * - Particle Background
 * - Glassmorphism Cards
 * - Center Avatar with Glow
 */
@Composable
fun HomeScreen(
    currentThemeMode: SoulMateThemeMode,
    onThemeChange: (SoulMateThemeMode) -> Unit,
    onNavigateToChat: () -> Unit = {},
    onNavigateToDigitalHuman: () -> Unit = {},
    onNavigateToGarden: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    // 1. State Management: Theme Mode (Moved to Parent)

    // Remove local SoulMateTheme wrapper, as it is now provided by the parent
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
        // 2. Background Layer: Particle System
        ParticleBackground(
            modifier = Modifier.fillMaxSize(),
            particleColor = SoulMateTheme.colors.particleColor,
            lineColor = SoulMateTheme.colors.cardBorder
        )

        // 3. Content Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- Top Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SoulMate",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SoulMateTheme.colors.textPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Theme Toggle Button
                    IconButton(
                        onClick = {
                            val newMode = when (currentThemeMode) {
                                SoulMateThemeMode.Tech -> SoulMateThemeMode.Warm
                                SoulMateThemeMode.Warm -> SoulMateThemeMode.Fresh
                                SoulMateThemeMode.Fresh -> SoulMateThemeMode.Tech
                            }
                            onThemeChange(newMode)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = "切换主题",
                            tint = SoulMateTheme.colors.textPrimary
                        )
                    }

                    // Settings Button
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = SoulMateTheme.colors.textPrimary
                        )
                    }
                }
            }

            // --- Center Avatar ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onNavigateToDigitalHuman),
                contentAlignment = Alignment.Center
            ) {
                // Animation for pulsing effect
                val infiniteTransition = rememberInfiniteTransition(label = "avatarPulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                // Glow Effect
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    SoulMateTheme.colors.accentGlow,
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Avatar Image
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground_soulmate),
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent) // Ensure transparent background
                        .border(2.dp, SoulMateTheme.colors.accentColor, CircleShape)
                )
            }

            // --- Bottom Cards ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: Start Chat
                ParallaxGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    backgroundColor = SoulMateTheme.colors.cardBg,
                    borderColor = SoulMateTheme.colors.cardBorder,
                    glowColor = SoulMateTheme.colors.accentGlow,
                    onClick = onNavigateToChat
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = SoulMateTheme.colors.accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "唤醒灵犀",
                            style = MaterialTheme.typography.titleLarge,
                            color = SoulMateTheme.colors.textPrimary
                        )
                    }
                }


                // Card 3: Memory Garden
                ParallaxGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    backgroundColor = SoulMateTheme.colors.cardBg,
                    borderColor = SoulMateTheme.colors.cardBorder,
                    glowColor = SoulMateTheme.colors.accentGlow,
                    onClick = onNavigateToGarden
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = SoulMateTheme.colors.accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "时光琥珀",
                            style = MaterialTheme.typography.titleLarge,
                            color = SoulMateTheme.colors.textPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
