## 根因判断（基于当前工程实际）
- 工程里可用的是 `androidx.compose.ui.platform.LocalLifecycleOwner`（AvatarContainer 也在用）。
- `androidx.lifecycle.compose.LocalLifecycleOwner` 在本工程环境下解析失败，因此会报 Unresolved reference。
- ParticleBackground.kt 同时声明了两次 `lifecycleOwner`，触发 Conflicting declarations，并连锁引发协程挂起函数误报。

## 修改方案
- 仅保留 Compose 平台提供的生命周期所有者：
  - 保留 `import androidx.compose.ui.platform.LocalLifecycleOwner`。
  - 删除代码中 `androidx.lifecycle.compose.LocalLifecycleOwner.current` 那一行及相关重复声明。
- 将 LaunchedEffect + repeatOnLifecycle 统一引用这一份 `lifecycleOwner`，写法保持：
  - `lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while(isActive) { withFrameNanos { ... } } }`

## 验证
- 重新编译 app 模块（至少 Kotlin 编译阶段），确认三条报错全部消失。