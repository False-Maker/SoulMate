package com.soulmate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 定义三种风格的主题模式枚举
enum class SoulMateThemeMode {
    Tech, // 科技 (Deep Blue/Cyan)
    Warm, // 温馨 (Rose/Sunset)
    Fresh // 清新 (White/Sky Blue)
}

// 核心配色板数据结构
@Immutable
data class SoulMateColorPalette(
    val bgBase: Color,
    val bgGradientStart: Color,
    val bgGradientEnd: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentColor: Color,
    val accentBg: Color,
    val accentGlow: Color,
    val particleColor: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val bubbleAi: Color,
    val bubbleUser: Color
)

// 1. Tech Theme (Cyberpunk/Deep Space)
val TechThemePalette = SoulMateColorPalette(
    bgBase = Color(0xFF050B14),
    bgGradientStart = Color(0xFF0F172A),
    bgGradientEnd = Color(0xFF000000),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    accentColor = Color(0xFF22D3EE),
    accentBg = Color(0xFF06B6D4),
    accentGlow = Color(0x8022D3EE),
    particleColor = Color(0xFF22D3EE),
    cardBg = Color(0x660F172A),
    cardBorder = Color(0x3322D3EE),
    bubbleAi = Color(0x991E293B),
    bubbleUser = Color(0xFF0891B2)
)

// 2. Warm Theme (Sunset Rose)
val WarmThemePalette = SoulMateColorPalette(
    bgBase = Color(0xFF1C1014),
    bgGradientStart = Color(0xFF2D1B22),
    bgGradientEnd = Color(0xFF12080A),
    textPrimary = Color(0xFFFFF1F2),
    textSecondary = Color(0x99FDA4AF),
    accentColor = Color(0xFFFDA4AF),
    accentBg = Color(0xFFF43F5E),
    accentGlow = Color(0x80F43F5E),
    particleColor = Color(0xFFFDA4AF),
    cardBg = Color(0x4D4C0519),
    cardBorder = Color(0x33F43F5E),
    bubbleAi = Color(0x664C0519),
    bubbleUser = Color(0xFFE11D48)
)

// 3. Fresh Theme (Daylight)
val FreshThemePalette = SoulMateColorPalette(
    bgBase = Color(0xFFF0F4F8),
    bgGradientStart = Color(0xFFFFFFFF),
    bgGradientEnd = Color(0xFFF1F5F9),
    textPrimary = Color(0xFF1E293B),
    textSecondary = Color(0xFF64748B),
    accentColor = Color(0xFF0EA5E9),
    accentBg = Color(0xFF0EA5E9),
    accentGlow = Color(0x4D0EA5E9),
    particleColor = Color(0xFF38BDF8),
    cardBg = Color(0x99FFFFFF),
    cardBorder = Color(0xB3FFFFFF),
    bubbleAi = Color(0xFFFFFFFF),
    bubbleUser = Color(0xFF0EA5E9)
)

val LocalSoulMateColors = staticCompositionLocalOf { TechThemePalette }

@Composable
fun SoulMateTheme(
    themeMode: SoulMateThemeMode = SoulMateThemeMode.Tech,
    content: @Composable () -> Unit
) {
    val colors = when (themeMode) {
        SoulMateThemeMode.Tech -> TechThemePalette
        SoulMateThemeMode.Warm -> WarmThemePalette
        SoulMateThemeMode.Fresh -> FreshThemePalette
    }

    CompositionLocalProvider(LocalSoulMateColors provides colors) {
        MaterialTheme(
            content = content
        )
    }
}

// 方便调用的单例对象
object SoulMateTheme {
    val colors: SoulMateColorPalette
        @Composable
        get() = LocalSoulMateColors.current
}