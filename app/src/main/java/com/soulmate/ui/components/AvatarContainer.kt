package com.soulmate.ui.components

import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import com.soulmate.data.model.UserGender
import com.soulmate.data.service.AvatarCoreService
import com.soulmate.SoulMateApplication
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.soulmate.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * EntryPoint for accessing UserPreferencesRepository in Composable
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UserPreferencesRepositoryEntryPoint {
    fun userPreferencesRepository(): UserPreferencesRepository
}

private const val TAG = "AvatarContainer"

/** 重绑序列号生成器（用于时序日志） */
private val rebindSeqGenerator = AtomicLong(0)

/** 重绑节流延迟（ms）- 原版本 */
private const val REBIND_DEBOUNCE_MS = 300L

/** 重绑节流延迟（ms）- 优化版本 */
private const val REBIND_DEBOUNCE_MS_FAST = 100L

/**
 * AvatarContainer - 数字人显示容器组件
 * 
 * 这个 Composable 使用 AndroidView 来桥接 Xmov SDK 的原生 View 需求。
 * 它负责：
 * 1. 创建 FrameLayout 容器供 SDK 渲染
 * 2. 调用 AvatarCoreService.bind() 进行绑定
 * 3. 通过 LifecycleEventObserver 桥接 Android 生命周期事件
 * 4. 重绑串行化与节流（避免快速切换导致竞态）
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
    userGender: UserGender,  // 去掉默认值，强制调用方显式传入
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 获取 UserPreferencesRepository 以检查优化开关
    val userPreferencesRepository = remember {
        try {
            val application = context.applicationContext as? SoulMateApplication
            if (application != null) {
                EntryPointAccessors.fromApplication(
                    application,
                    UserPreferencesRepositoryEntryPoint::class.java
                ).userPreferencesRepository()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get UserPreferencesRepository: ${e.message}")
            null
        }
    }
    
    // 根据优化开关选择 debounce 延迟（默认启用优化，失败时使用原延迟）
    val debounceDelay = remember(userPreferencesRepository) {
        try {
            if (userPreferencesRepository?.isFastRebindEnabled() == true) {
                REBIND_DEBOUNCE_MS_FAST
            } else {
                REBIND_DEBOUNCE_MS
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check fast rebind setting, using default: ${e.message}")
            REBIND_DEBOUNCE_MS
        }
    }
    
    // ========== 状态管理 ==========
    // 记录上一次成功绑定的性别
    var lastBoundGender by remember { mutableStateOf<UserGender?>(null) }
    
    // 重绑串行化互斥锁
    val rebindMutex = remember { Mutex() }
    
    // 最新请求的性别（用于节流/debounce）
    var pendingGender by remember { mutableStateOf<UserGender?>(null) }
    
    // 记录容器引用（用于协程中访问）
    var containerRef by remember { mutableStateOf<FrameLayout?>(null) }
    
    // 容器 ID（用于日志）
    var containerId by remember { mutableLongStateOf(0L) }
    
    // ========== 重绑节流处理（debounce） ==========
    LaunchedEffect(userGender) {
        // 如果性别变化，设置待处理请求
        if (userGender != lastBoundGender && containerRef != null) {
            val seq = rebindSeqGenerator.incrementAndGet()
            Log.d(TAG, "[seq=$seq|cid=$containerId] >>> REBIND_REQUEST | from=$lastBoundGender to=$userGender")
            
            pendingGender = userGender
            
            // 节流延迟（根据优化开关选择）
            delay(debounceDelay)
            
            // 检查是否仍是最新请求
            if (pendingGender == userGender) {
                Log.d(TAG, "[seq=$seq|cid=$containerId] --- REBIND_DEBOUNCE_OK | executing for $userGender")
                
                // 串行化执行重绑
                rebindMutex.withLock {
                    val container = containerRef ?: return@withLock
                    
                    Log.i(TAG, "[seq=$seq|cid=$containerId] >>> REBIND_START | from=$lastBoundGender to=$userGender")
                    
                    // Step 1: 销毁旧实例
                    Log.d(TAG, "[seq=$seq|cid=$containerId] --- DESTROY_BEFORE")
                    avatarCoreService.destroy()
                    Log.d(TAG, "[seq=$seq|cid=$containerId] --- DESTROY_AFTER")
                    
                    // Step 2: 清空旧 View
                    Log.d(TAG, "[seq=$seq|cid=$containerId] --- REMOVE_VIEWS_BEFORE")
                    container.removeAllViews()
                    Log.d(TAG, "[seq=$seq|cid=$containerId] --- REMOVE_VIEWS_AFTER")
                    
                    // Step 3: 重新绑定
                    Log.d(TAG, "[seq=$seq|cid=$containerId] --- BIND_BEFORE | gender=$userGender")
                    val ok = avatarCoreService.bind(context, container, userGender)
                    Log.d(TAG, "[seq=$seq|cid=$containerId] --- BIND_AFTER | success=$ok")
                    
                    if (ok) {
                        lastBoundGender = userGender
                        Log.i(TAG, "[seq=$seq|cid=$containerId] <<< REBIND_SUCCESS | gender=$userGender")
                    } else {
                        Log.w(TAG, "[seq=$seq|cid=$containerId] <<< REBIND_FAILED | gender=$userGender, will retry on next update")
                    }
                }
                
                pendingGender = null
            } else {
                Log.d(TAG, "[seq=$seq|cid=$containerId] --- REBIND_CANCELLED | superseded by $pendingGender")
            }
        }
    }
    
    // ========== 生命周期事件桥接 ==========
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Log.d(TAG, "[cid=$containerId] >>> LIFECYCLE_EVENT | event=$event")
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // 重新进入页面（或后台返回），如果 Session 已销毁，则重新绑定
                    // 注意：如果是首次进入，AndroidView factory 会处理 bind，这里需要避免重复
                    // 但 bind 内部有 checks，多调一次无害
                    val container = containerRef
                    if (container != null && !avatarCoreService.isInitialized()) {
                        Log.i(TAG, "[cid=$containerId] --- LIFECYCLE_START_REBIND")
                        val ok = avatarCoreService.bind(context, container, userGender)
                        if (ok) lastBoundGender = userGender
                    } else {
                        avatarCoreService.resume()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Resume 通常紧随 Start，这里只需确保 resume 状态
                    avatarCoreService.resume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    avatarCoreService.pause()
                }
                Lifecycle.Event.ON_STOP -> {
                    // 离开页面或进入后台：立即销毁 Session 以释放房间
                    // 这是解决 "Room Rate Limit" 的关键，确保不占用连接
                    Log.i(TAG, "[cid=$containerId] --- LIFECYCLE_STOP_DESTROY")
                    avatarCoreService.destroy()
                }
                else -> { /* 其他生命周期事件不处理 */ }
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            Log.i(TAG, "[cid=$containerId] >>> DISPOSE | removing observer and destroying SDK")
            lifecycleOwner.lifecycle.removeObserver(observer)
            avatarCoreService.destroy()  // 双重保险
        }
    }
    
    // ========== AndroidView 桥接 ==========
    AndroidView(
        factory = { ctx ->
            val seq = rebindSeqGenerator.incrementAndGet()
            
            FrameLayout(ctx).apply {
                // 设置唯一 ID 用于调试
                id = android.view.View.generateViewId()
                containerId = id.toLong()
                containerRef = this
                
                Log.i(TAG, "[seq=$seq|cid=$id] >>> FACTORY_START | gender=$userGender")
                
                // 首次创建容器时绑定
                removeAllViews()  // 保险起见先清空
                
                Log.d(TAG, "[seq=$seq|cid=$id] --- FACTORY_BIND_BEFORE | gender=$userGender")
                val ok = avatarCoreService.bind(context, this, userGender)
                Log.d(TAG, "[seq=$seq|cid=$id] --- FACTORY_BIND_AFTER | success=$ok")
                
                if (ok) {
                    lastBoundGender = userGender
                    Log.i(TAG, "[seq=$seq|cid=$id] <<< FACTORY_SUCCESS | gender=$userGender")
                } else {
                    Log.w(TAG, "[seq=$seq|cid=$id] <<< FACTORY_FAILED | gender=$userGender")
                }

                // 添加 LayoutChangeListener 以实现 CenterCrop 效果，确保画面填满屏幕且居中
                addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                    val width = right - left
                    val height = bottom - top
                    if (width == 0 || height == 0) return@addOnLayoutChangeListener

                    val child = getChildAt(0) ?: return@addOnLayoutChangeListener

                    // 目标宽高比 (数字人原始比例)
                    val targetRatio = 9f / 16f
                    val viewRatio = width.toFloat() / height

                    var scale = 1f
                    
                    // 计算 Scale 以实现 CenterCrop (填满屏幕)
                    if (viewRatio > targetRatio) {
                        // 屏幕比内容宽 (例如平板)，内容高度适配，宽度有黑边 -> 放大以填满宽度
                        scale = width.toFloat() / (height * targetRatio)
                    } else {
                        // 屏幕比内容高 (例如长屏手机)，内容宽度适配，高度有黑边 -> 放大以填满高度
                        scale = height.toFloat() / (width / targetRatio)
                    }

                    // 应用缩放
                    child.scaleX = scale
                    child.scaleY = scale
                    
                    // 确保中心点对齐
                    child.pivotX = width / 2f
                    child.pivotY = height / 2f
                    
                    // 额外下移处理：数字人整体站位偏上，需要通过 TranslationY 将其下移居中
                    // 设定为屏幕高度的 16%，进一步下移以满足用户视觉需求 (Original 0.12f, increased by ~80-120px equivalent)
                    child.translationY = height * 0.16f
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { container ->
            // 更新容器引用
            containerRef = container
            containerId = container.id.toLong()
            
            // 兜底检查：如果 factory 因时序问题未成功绑定，这里尝试一次
            if (lastBoundGender == null && !avatarCoreService.isInitialized()) {
                val seq = rebindSeqGenerator.incrementAndGet()
                Log.d(TAG, "[seq=$seq|cid=$containerId] >>> UPDATE_FALLBACK_BIND | gender=$userGender")
                
                container.removeAllViews()
                val ok = avatarCoreService.bind(context, container, userGender)
                if (ok) {
                    lastBoundGender = userGender
                    Log.i(TAG, "[seq=$seq|cid=$containerId] <<< UPDATE_FALLBACK_SUCCESS | gender=$userGender")
                } else {
                    Log.w(TAG, "[seq=$seq|cid=$containerId] <<< UPDATE_FALLBACK_FAILED | gender=$userGender")
                }
            }
            // 注意：性别变化的重绑已经由 LaunchedEffect 处理，这里不再重复处理
            // 这样可以实现节流和串行化
        }
    )
}

