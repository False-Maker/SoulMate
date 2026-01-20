package com.soulmate.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.soulmate.ui.navigation.Screen
import com.soulmate.ui.screens.ChatScreen
import com.soulmate.ui.screens.HomeScreen
import com.soulmate.ui.screens.MemoryGardenScreen
import com.soulmate.ui.screens.OnboardingScreen
import com.soulmate.ui.screens.SettingsScreen
import com.soulmate.ui.screens.SplashScreen
import com.soulmate.ui.screens.DigitalHumanScreen
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.delay

/**
 * SoulMateApp - 应用主 Composable
 * 
 * 托管 NavHost 和主题，管理应用级别的 UI 结构
 * 
 * @param navController Navigation controller for managing navigation
 * @param initialRoute Optional route to navigate to immediately (for deep links from notifications)
 */
@Composable
fun SoulMateApp(
    navController: NavHostController = rememberNavController(),
    initialRoute: String? = null
) {
    SoulMateTheme {
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.fillMaxSize(),
            // 底部向上滑入动画
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            // 底部向下滑出动画
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            // 返回时的进入动画
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            // 返回时的退出动画
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            // Splash Screen
            composable(Screen.Splash.route) {
                val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                
                SplashScreen(
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
                
                // Handle deep link or auto-navigate after splash
                LaunchedEffect(Unit) {
                    delay(2000)
                    
                    // 检查是否已完成首次引导
                    val isOnboardingCompleted = onboardingViewModel.isOnboardingCompleted()
                    
                    // Check if we should navigate to a specific screen (deep link from notification)
                    when {
                        // 首次启动，需要选择性别
                        !isOnboardingCompleted -> {
                            navController.navigate(Screen.Onboarding.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                        // 从通知深度链接进入聊天
                        initialRoute == "chat" -> {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                            navController.navigate(Screen.Chat.route)
                        }
                        // 默认: 导航到主页
                        else -> {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    }
                }
            }
            
            // Onboarding Screen (首次启动引导 - 性别选择)
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            
            // Home Screen
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToChat = {
                        navController.navigate(Screen.Chat.route)
                    },
                    onNavigateToGarden = {
                        navController.navigate(Screen.Garden.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }
            


            // Chat Screen
            composable(Screen.Chat.route) {
                ChatScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToDigitalHuman = {
                        navController.navigate(Screen.DigitalHuman.route)
                    }
                )
            }
            
            // Digital Human Screen
            composable(Screen.DigitalHuman.route) {
                DigitalHumanScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToTextChat = {
                       // Pop back to ChatScreen if it's in stack, or navigate
                       // Usually DigitalHuman is entered FROM ChatScreen, so popBackStack helps return to it.
                       // But providing explicit navigation is safer if entered from elsewhere?
                       // If we want to switch TO text chat, we assume we might be there.
                       // Use popBackStack() for now as it acts as a toggle.
                       navController.popBackStack()
                    }
                )
            }
            
            // Memory Garden Screen
            composable(Screen.Garden.route) {
                MemoryGardenScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // Settings Screen
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

