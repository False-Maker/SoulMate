package com.soulmate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import com.soulmate.ui.components.ParticleBackground
import com.soulmate.ui.theme.SoulMateTheme

/**
 * SplashScreen - 启动屏幕
 *
 * 显示应用 Logo 和加载指示器
 */
@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit = {}
) {
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
            ),
        contentAlignment = Alignment.Center
    ) {
        // 1. Background
        ParticleBackground(
            modifier = Modifier.fillMaxSize(),
            particleColor = SoulMateTheme.colors.particleColor,
            lineColor = SoulMateTheme.colors.cardBorder
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo placeholder
            Text(
                text = "SoulMate",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = SoulMateTheme.colors.textPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "你的 AI 灵魂伴侣",
                style = MaterialTheme.typography.bodyLarge,
                color = SoulMateTheme.colors.textSecondary
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = SoulMateTheme.colors.accentColor,
                strokeWidth = 3.dp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    SoulMateTheme {
        SplashScreen()
    }
}
