package com.soulmate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.soulmate.ui.SoulMateApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity - 主界面入口
 * 
 * 使用 Jetpack Compose 构建 UI，托管 SoulMateApp Composable
 * 
 * 功能：
 * - 启用 Edge-to-Edge 显示
 * - 设置 Compose 内容
 * - 处理系统栏颜色
 * - 处理推送通知权限请求 (Android 13+)
 * - 处理从通知深度链接到聊天屏幕
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject
    lateinit var avatarCoreService: com.soulmate.data.service.AvatarCoreService

    @javax.inject.Inject
    lateinit var aliyunASRService: com.soulmate.data.service.AliyunASRService
    
    // Permission request launcher for POST_NOTIFICATIONS (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result handled - user can still use app without notifications
        // We don't need to do anything here as it's an optional enhancement
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用 Edge-to-Edge 显示，让内容延伸到系统栏下方
        enableEdgeToEdge()
        
        // 允许内容绘制到系统栏区域
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Request notification permission on Android 13+ (Tiramisu)
        requestNotificationPermissionIfNeeded()
        
        // Check if we should navigate to a specific screen (deep link from notification)
        val navigateTo = intent.getStringExtra("navigate_to")
        
        // 设置 Compose 内容
        setContent {
            SoulMateApp(initialRoute = navigateTo)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 关键修复：App 销毁时强制释放数字人资源，防止后台持续计费
        // 之前因为缺少这一步，导致 App 关闭后 WebSocket 连接可能仍保持，产生高额费用
        avatarCoreService.destroy()
        aliyunASRService.release()
    }
    
    /**
     * Requests POST_NOTIFICATIONS permission on Android 13+ if not already granted.
     * This permission is required to show notifications on Android 13+.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show rationale if needed, then request
                    // For now, just request directly
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Directly request permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}

