package com.soulmate.ui.navigation

/**
 * Screen - 应用导航路由定义
 * 
 * 使用 sealed class 确保类型安全的导航
 */
sealed class Screen(val route: String) {
    /** 启动屏幕 */
    object Splash : Screen("splash")
    
    /** 首次启动引导屏幕（性别选择） */
    object Onboarding : Screen("onboarding")
    
    /** 主页屏幕 */
    object Home : Screen("home")
    
    /** 聊天屏幕 */
    object Chat : Screen("chat")
    
    /** 记忆花园屏幕 */
    object Garden : Screen("garden")
    
    /** 设置屏幕 */
    object Settings : Screen("settings")
    
    /** 数字人互动屏幕 */
    object DigitalHuman : Screen("digital_human")
}

