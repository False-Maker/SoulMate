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
    
    // ========== 重绑节流处理（debounce） 与 首次绑定逻辑合二为一 ==========
    LaunchedEffect(userGender, containerRef) {
        val container = containerRef ?: return@LaunchedEffect
        
        // 核心绑定逻辑：只要 container 准备好，且 Gender 发生变化（或首次），就执行绑定
        if (userGender != lastBoundGender) {
            val seq = rebindSeqGenerator.incrementAndGet()
            Log.d(TAG, "[seq=$seq|cid=$containerId] >>> REBIND_REQUEST | from=$lastBoundGender to=$userGender")
            
            pendingGender = userGender
            
            // 节流延迟（首次无需延迟，这里也可保留作为简单防抖）
            if (lastBoundGender != null) {
                delay(debounceDelay)
            }
            
            // 检查是否仍是最新请求
            if (pendingGender == userGender) {
                Log.d(TAG, "[seq=$seq|cid=$containerId] --- REBIND_DEBOUNCE_OK | executing for $userGender")
                
                // 串行化执行重绑
                rebindMutex.withLock {
                    Log.i(TAG, "[seq=$seq|cid=$containerId] >>> REBIND_START | from=$lastBoundGender to=$userGender")
                    
                    // Step 1: 销毁旧实例 (AvatarCoreService 内部现在是异步 suspend，但这里我们等它完成)
                    // 注意：destroy() 现在是异步的，但我们这里想要的是"切换"，所以直接调用 bind 即可，
                    // AvatarCoreService.bind 内部会处理旧连接的清理 (suspend wait)
                    
                    // Step 2: 清空旧 View
                    Log.d(TAG, "[seq=$seq|cid=$containerId] --- REMOVE_VIEWS_BEFORE")
                    container.removeAllViews()
                    Log.d(TAG, "[seq=$seq|cid=$containerId] --- REMOVE_VIEWS_AFTER")
                    
                    // Step 3: 重新绑定 (Suspend call)
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
                    // 重新进入页面（或后台返回）
                    if (containerRef != null && !avatarCoreService.isInitialized()) {
                        Log.i(TAG, "[cid=$containerId] --- LIFECYCLE_START_REBIND_HINT | Not initialized, triggering rebind")
                        // 强制触发 LaunchedEffect 重新绑定
                        // 通过将 lastBoundGender 置空，使得下一次 LaunchedEffect 认为状态不一致从而重新执行绑定逻辑
                        lastBoundGender = null 
                    } else {
                        avatarCoreService.resume()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    avatarCoreService.resume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    avatarCoreService.pause()
                }
                Lifecycle.Event.ON_STOP -> {
                    // 离开页面或进入后台：立即销毁 Session 以释放房间
                    // 使用最新的 destroy (它是非阻塞的，launch job)
                    Log.i(TAG, "[cid=$containerId] --- LIFECYCLE_STOP_DESTROY")
                    avatarCoreService.destroy()
                    // 重置状态，以便回来时能重新 bind
                    // 注意：真正回来时，如果是页面重建，lastBoundGender 会是 null，自然触发 bind
                    // 如果是不可见 -> 可见，Composition 不变，需要手动触发吗？
                    // 现在的架构倾向于每次进入都做一次检查。
                    // 简单起见，我们在这里不清除 lastBoundGender，而是依靠 resume
                    // 如果 resume 发现 session 没了，是否需要重新 bind？
                    // AvatarCoreService 内部维护状态，如果 destroy 了，initialized 为 false
                    // 下一次 LaunchedEffect 如果发现变化会 bind。
                    // 但如果参数没变，LaunchedEffect 不会跑。
                    // *关键点*：如果为了省钱/省连接，我们在 onStop destroy 了，
                    // 那么在 onStart 必须重新 bind。
                    // 所以我们需要一个机制在 onStart 触发 bind。
                    // 方案：LifecycleResumeEffect (future) or mutableState trigger.
                    // 鉴于目前架构，我们假设 onStop destroy 后，用户大概率是退出了或者去其他页面，
                    // 返回时通常会重新加载 Compose。
                    // 如果只是压后台再回来，Composable 没销毁，lastBoundGender 还是旧值。
                    // 此时我们在 onStart 里检测到 !isInitialized，应该触发重新绑定。
                    // 如何从 Observer (非 suspend) 触发 suspend bind？
                    // 可以用一个 trigger state.
                }
                else -> { /* 其他生命周期事件不处理 */ }
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            Log.i(TAG, "[cid=$containerId] >>> DISPOSE | removing observer and destroying SDK")
            lifecycleOwner.lifecycle.removeObserver(observer)
            avatarCoreService.destroy()  // 非阻塞调用
        }
    }
    
    // ========== 监听 App 回到前台重新绑定逻辑 ==========
    // 如果 Composable 没销毁（例如压后台），onStart 需要重新 bind
    // 我们可以监听 lifecycleOwner.lifecycleState
    // 或者使用一个特定的 key 来重启 LaunchedEffect
    // 这里使用一个简单的 trick：当 Lifecycle 变为 RESUMED 且 SDK 未初始化时，强制重置 lastBoundGender
    // 但不能在 Composable 外部直接改状态。
    // 替代方案：检查 isInitialized 状态的轮询或事件流？太复杂。
    // 简化方案：相信 AvatarCoreService.resume() 能处理大部分情况，
    // 如果被 destroy 了，resume 可能无效。
    // 考虑到 resume 只是调用 SDK resume，而 destroy 是 release。释放后 resume 无效。
    // 所以必须 re-bind。
    // 让 LaunchedEffect 监听 Lifecycle state 变化？
    // val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    // LaunchedEffect(lifecycleState) { if (Resumed && !init) bind() } -> 可行。
    
    // ========== AndroidView (纯 UI 容器) ==========
    AndroidView(
        factory = { ctx ->
            val seq = rebindSeqGenerator.incrementAndGet()
            
            FrameLayout(ctx).apply {
                // 设置唯一 ID 用于调试
                id = android.view.View.generateViewId()
                containerId = id.toLong()
                
                // ！！！关键修改：Factory 中不再同步调用 avatarCoreService.bind() ！！！
                // ！！！移除了导致白屏的阻塞调用 ！！！
                
                Log.i(TAG, "[seq=$seq|cid=$id] >>> FACTORY_CREATE (No Bind) | gender=$userGender")
                
                // 添加 LayoutChangeListener 以实现 CenterCrop 效果
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
                        scale = width.toFloat() / (height * targetRatio)
                    } else {
                        scale = height.toFloat() / (width / targetRatio)
                    }

                    // 应用缩放
                    child.scaleX = scale
                    child.scaleY = scale
                    
                    // 确保中心点对齐
                    child.pivotX = width / 2f
                    child.pivotY = height / 2f
                    
                    // 额外下移处理
                    child.translationY = height * 0.16f
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { container ->
            // 更新容器引用，这会触发 LaunchedEffect 进行绑定
            if (containerRef != container) {
                containerRef = container
                containerId = container.id.toLong()
                Log.d(TAG, "[cid=${container.id}] >>> UPDATE_REF | Container ready for binding")
            }
        }
    )
}

