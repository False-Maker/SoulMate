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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.soulmate.ui.navigation.Screen
// import com.soulmate.ui.screens.ChatScreen
import com.soulmate.ui.screens.HomeScreen
import com.soulmate.ui.screens.MemoryGardenScreen
import com.soulmate.ui.screens.OnboardingScreen
import com.soulmate.ui.screens.SettingsScreen
import com.soulmate.ui.screens.SplashScreen
import com.soulmate.ui.screens.DigitalHumanScreen
import com.soulmate.ui.screens.CrisisResourceScreen
import com.soulmate.ui.screens.CrisisResourceScreen
import com.soulmate.ui.screens.EmotionalReportScreen
import com.soulmate.ui.screens.ImmersiveFusionScreen
import com.soulmate.ui.theme.SoulMateTheme
import com.soulmate.ui.theme.SoulMateThemeMode
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
    // Hoist Theme State here to be shared across all screens
    var themeMode by remember { mutableStateOf(SoulMateThemeMode.Tech) }

    SoulMateTheme(themeMode = themeMode) {
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.fillMaxSize(),
            // 优化转场动画：使用标准的水平滑动 + 淡入淡出，更加自然丝滑
            // 进入页面：从右侧滑入
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            // 退出页面：向左侧滑出
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            // 返回时的进入：从左侧滑回（恢复）
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            // 返回时的退出：向右侧滑出
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
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
            
            // Home Screen (Now Fusion Screen)
            composable(Screen.Home.route) {
                ImmersiveFusionScreen(
                    onNavigateBack = {
                        // In Home/Fusion, back might exit app or go to bg
                    },
                    onNavigateToGarden = {
                        navController.navigate(Screen.Garden.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                    currentThemeMode = themeMode,
                    onThemeChange = { newMode -> themeMode = newMode }
                )
            }

            


            // Chat Screen (Redirect to Digital Human as ChatScreen is removed)
            composable(Screen.Chat.route) {
                // Redirect to Digital Human
                DigitalHumanScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // Digital Human Screen
            composable(Screen.DigitalHuman.route) {
                DigitalHumanScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // Memory Garden Screen
            composable(Screen.Garden.route) {
                MemoryGardenScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToChat = {
                        // Redirect to Digital Human instead of Chat
                        val popped = navController.popBackStack(Screen.DigitalHuman.route, false)
                        if (!popped) {
                            navController.navigate(Screen.DigitalHuman.route) {
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
            
            // Settings Screen
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToResources = {
                        navController.navigate(Screen.CrisisResources.route)
                    },
                    onNavigateToReport = {
                        navController.navigate(Screen.EmotionalReport.route)
                    }
                )
            }
            
            // Crisis Resources Screen
            composable(Screen.CrisisResources.route) {
                CrisisResourceScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // Immersive Fusion Screen (Main Hub)
            composable(Screen.Fusion.route) {
                ImmersiveFusionScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToGarden = {
                        navController.navigate(Screen.Garden.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                    currentThemeMode = themeMode,
                    onThemeChange = { newMode -> themeMode = newMode }
                )
            }
            
            // Emotional Report Screen
            composable(Screen.EmotionalReport.route) {
                EmotionalReportScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
