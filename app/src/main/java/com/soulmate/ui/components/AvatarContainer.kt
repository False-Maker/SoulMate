package com.soulmate.ui.components

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.soulmate.data.model.UserGender
import com.soulmate.data.service.AvatarCoreService

/**
 * AvatarContainer - 数字人显示容器组件
 * 
 * 这个 Composable 使用 AndroidView 来桥接 Xmov SDK 的原生 View 需求。
 * 它负责：
 * 1. 创建 FrameLayout 容器供 SDK 渲染
 * 2. 调用 AvatarCoreService.bind() 进行绑定
 * 3. 通过 LifecycleEventObserver 桥接 Android 生命周期事件
 * 
 * 使用方式：
 * ```kotlin
 * AvatarContainer(
 *     avatarCoreService = viewModel.avatarCoreService,
 *     userGender = UserGender.MALE,
 *     modifier = Modifier.fillMaxSize()
 * )
 * ```
 */
@Composable
fun AvatarContainer(
    avatarCoreService: AvatarCoreService,
    userGender: UserGender = UserGender.MALE,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 生命周期事件桥接
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    avatarCoreService.resume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    avatarCoreService.pause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    avatarCoreService.destroy()
                }
                else -> { /* 其他生命周期事件不处理 */ }
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            avatarCoreService.destroy()  // 确保组件移除时销毁 SDK 资源
        }
    }
    
    // AndroidView 桥接
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                // 设置唯一 ID 用于调试
                id = android.view.View.generateViewId()
                
                // 绑定到 AvatarCoreService，传入用户性别以选择对应的数字人
                avatarCoreService.bind(context, this, userGender)
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { _ ->
            // 如果需要在 recomposition 时更新 View，可以在这里处理
        }
    )
}

