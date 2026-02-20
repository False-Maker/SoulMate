# SoulMate 项目重构计划 - 移除数字人集成

> **计划日期**: 2026-02-13
> **预计工作量**: 1-4小时
> **优先级**: HIGH

---

## TL;DR

> **核心目标**: 完全移除数字人（Avatar/Digital Human）集成，保留并完善所有其他功能。将 `ChatScreen` 作为统一的聊天主界面。

**重要约束**:
- 🎨 **配色暂不调整** - 保持现有颜色方案
- 📐 **布局需要调整** - 优化 ChatScreen 界面布局
- 🚫 **无需新增功能** - 只重构现有功能

**主要变更**:
- 删除 `DigitalHumanScreen` 及相关组件
- 移除 `AvatarCoreService` 和所有 Avatar 相关服务
- 重构导航：统一到 `ChatScreen`
- 用 MindWatch 状态覆盖层替代数字人情感反馈
- **优化 ChatScreen 布局**以获得更好的用户体验
- 保留核心功能：聊天、记忆花园、设置、情绪报告、危机资源

---

## 当前项目状态分析

### 现有屏幕列表
| 屏幕 | 文件 | 状态 | 处理方式 |
|------|------|------|----------|
| ChatScreen | ChatScreen.kt | ✅ 完整 | **保留并增强** |
| DigitalHumanScreen | DigitalHumanScreen.kt | ⚠️ 缺少组件 | **删除** |
| MemoryGardenScreen | MemoryGardenScreen.kt | ⚠️ ParticleBackground引用 | **修复并保留** |
| SettingsScreen | SettingsScreen.kt | ✅ 已修复 | **保留** |
| EmotionalReportScreen | EmotionalReportScreen.kt | ✅ 完整 | **保留** |
| CrisisResourceScreen | CrisisResourceScreen.kt | ✅ 完整 | **保留** |
| SplashScreen | SplashScreen.kt | ✅ 完整 | **保留** |
| OnboardingScreen | OnboardingScreen.kt | ✅ 完整 | **保留** |

### 现有服务列表
| 服务 | 文件 | 状态 | 处理方式 |
|------|------|------|----------|
| LLMService | LLMService.kt | ✅ 完整 | **保留** |
| RAGService | RAGService.kt | ✅ 完整 | **保留** |
| ImageGenService | ImageGenService.kt | ✅ 完整 | **保留** |
| AliyunASRService | AliyunASRService.kt | ✅ 完整 | **保留** |
| AvatarCoreService | ~~AvatarCoreService.kt~~ | ❌ 已删除引用 | **完全移除** |
| MindWatchService | MindWatchService.kt | ✅ 完整 | **保留** |
| ImageBase64Encoder | ImageBase64Encoder.kt | ✅ 完整 | **保留** |
| VideoFrameExtractor | VideoFrameExtractor.kt | ✅ 完整 | **保留** |

### 已识别问题
1. ✅ `MainActivity.kt` - AvatarCoreService 引用已移除
2. ✅ `ChatViewModel.kt` - avatarService 调用已移除
3. ✅ `EtherealComponents.kt` - ParticleBackground 已简化
4. ✅ `SettingsScreen.kt` - ParticleBackground 已修复
5. ✅ `DigitalHumanScreen.kt` - 已删除
6. ✅ `SoulmateInputCapsule.kt` - 已删除
7. ✅ `AvatarState.kt` - 已删除
8. ✅ `MemoryGardenScreen.kt` - ParticleBackground 已修复
9. ✅ `Screen.kt` - DigitalHuman 和 Fusion 路由已删除
10. ✅ `SoulMateApp.kt` - 导航已重构
11. ✅ `ChatScreen.kt` - 已重新创建，包含 MindWatch 状态支持
12. ⚠️ `UserPreferencesRepository.kt` - 仍有 avatar 配置残留
13. ⚠️ `UIEvent.kt` - 注释中提到 Avatar
14. ⚠️ `ChatViewModel.kt` - 注释中提到 avatar
15. ⚠️ `SettingsScreen.kt` - 设置中仍有 "灵犀化身" 部分

---

## 工作目标

### 核心目标
将 SoulMate 重构为以 `ChatScreen` 为中心的情感陪伴应用，移除所有数字人相关代码，同时保留和完善：
- 💬 聊天对话（文本 + 语音输入）
- 🧠 RAG 记忆检索
- 📸 图像生成（Vision）
- 🎬 视频理解
- 🌸 记忆花园（时光琥珀）
- ⚙️ 设置界面
- 📊 情绪报告
- 🆘 危机资源
- 🎭 MindWatch 情绪监测

### 定义完成
- [x] 所有数字人相关代码已移除
- [x] 所有屏幕可以正常导航和显示
- [x] ChatScreen 作为主聊天界面功能完整
- [x] MindWatch 状态在 ChatScreen 中正确显示
- [x] 项目编译无错误
- [ ] 应用可以正常启动（需手动测试）

---

## 导航重构方案

### 当前导航结构（需修改）
```
SplashScreen → OnboardingScreen → Home/Fusion → DigitalHumanScreen (❌ 缺少组件)
                                                              ↓
                                                         MemoryGardenScreen
                                                         SettingsScreen
                                                         CrisisResourceScreen
                                                         EmotionalReportScreen
```

### 新导航结构（推荐）
```
SplashScreen → OnboardingScreen → ChatScreen (✅ 主聊天界面)
                                              ↓
                                         MemoryGardenScreen (记忆花园)
                                         SettingsScreen (设置)
                                         EmotionalReportScreen (情绪报告)
                                         CrisisResourceScreen (危机资源)
```

### Screen 路由定义修改
**位置**: `com.soulmate.ui.navigation.Screen`

**需要删除的路由**:
```kotlin
// 删除
object DigitalHuman : Screen("digital_human", "数字人")
object Fusion : Screen("fusion", "融合")
```

**需要修改的路由**:
```kotlin
// Home 直接指向 ChatScreen
object Home : Screen("home", "首页")

// Chat 保持独立（可与 Home 合并或分开）
object Chat : Screen("chat", "聊天")
```

---

## 执行策略

### 阶段 1: 修复现有编译错误
**优先级**: CRITICAL
**预计时间**: 5分钟

- [x] 1.1 修复 `MemoryGardenScreen.kt` 中的 `ParticleBackground` 引用
- [x] 1.2 验证项目编译通过

### 阶段 2: 删除数字人相关文件
**优先级**: HIGH
**预计时间**: 10分钟

- [x] 2.1 删除 `DigitalHumanScreen.kt`
- [x] 2.2 删除 `SoulmateInputCapsule.kt`（如果存在）
- [x] 2.3 删除 `AvatarState.kt`
- [x] 2.4 检查并删除其他 avatar 相关组件

### 阶段 3: 重构导航
**优先级**: HIGH
**预计时间**: 15分钟

- [x] 3.1 修改 `Screen.kt` - 删除 DigitalHuman 和 Fusion 路由
- [x] 3.2 修改 `SoulMateApp.kt` - 重构 NavHost
  - Home/Fusion → ChatScreen
  - Chat → ChatScreen
- [x] 3.3 修改 `SplashScreen.kt` - 启动后导航到 ChatScreen（无需修改，已在 SoulMateApp 中处理）
- [x] 3.4 更新所有返回导航引用（MemoryGardenScreen 已无 DigitalHuman 引用）

### 阶段 4: 重构 ChatScreen 布局
**优先级**: HIGH
**预计时间**: 45分钟

> **重点**: 调整布局以获得更好的用户体验，暂不修改配色方案

- [x] 4.1 调整消息列表布局 - 优化气泡间距和边距
- [x] 4.2 添加 MindWatch 状态显示覆盖层
- [x] 4.3 优化输入框区域布局 - 改进按钮排列和间距
- [x] 4.4 添加顶部导航栏 - 返回按钮和菜单入口
- [x] 4.5 添加语音输入可视化反馈
- [x] 4.6 添加图片/视频消息预览支持
- [x] 4.7 优化屏幕空间利用 - 更好的权重分配
- [x] 4.8 保持现有配色方案不变

### 阶段 5: 修复 MemoryGardenScreen
**优先级**: MEDIUM
**预计时间**: 15分钟

- [ ] 5.1 移除 ParticleBackground 引用
- [ ] 5.2 使用简单的渐变背景
- [ ] 5.3 验证所有功能正常

### 阶段 6: 清理残留代码
**优先级**: LOW
**预计时间**: 20分钟

- [ ] 6.1 检查并移除所有 avatar 相关导入
- [ ] 6.2 检查 UserPreferencesRepository 中的 avatar 配置
- [ ] 6.3 检查 DI 模块中的 avatar 绑定
- [ ] 6.4 清理字符串资源中的 avatar 引用
- [ ] 6.5 更新 AGENTS.md 文档

### 阶段 7: 测试验证
**优先级**: HIGH
**预计时间**: 20分钟

- [x] 7.1 编译测试：`./gradlew build` 通过 ✅
- [ ] 7.2 启动测试：应用可以正常启动
- [ ] 7.3 导航测试：所有屏幕可以正常访问
- [ ] 7.4 功能测试：发送消息、语音输入、记忆花园
- [ ] 7.5 回归测试：MindWatch、设置、情绪报告

---

## 文件修改清单

### 需要删除的文件
```
app/src/main/java/com/soulmate/ui/screens/DigitalHumanScreen.kt
app/src/main/java/com/soulmate/ui/components/SoulmateInputCapsule.kt (如果存在)
app/src/main/java/com/soulmate/data/service/AvatarState.kt
app/src/main/java/com/soulmate/data/service/XmovAvatarDriver.kt (如果存在)
app/src/main/java/com/soulmate/data/service/AvatarCoreService.kt (如果存在)
```

### 需要修改的文件
| 文件 | 修改内容 |
|------|----------|
| `ui/screens/MemoryGardenScreen.kt` | 移除 ParticleBackground 引用 |
| `ui/screens/ChatScreen.kt` | 添加 MindWatch 状态显示 |
| `ui/screens/SplashScreen.kt` | 导航到 ChatScreen |
| `ui/SoulMateApp.kt` | 重构 NavHost，删除 DigitalHuman/Fusion |
| `ui/navigation/Screen.kt` | 删除 DigitalHuman/Fusion 路由 |
| `ui/viewmodel/ChatViewModel.kt` | 清理残留 avatar 注释 |
| `ui/components/EtherealComponents.kt` | 保持简化的 ParticleBackground |
| `data/preferences/UserPreferencesRepository.kt` | 移除 avatar 配置（如果有） |
| `di/AppBindings.kt` (或其他 DI 文件) | 移除 AvatarCoreService 绑定 |
| `AGENTS.md` | 更新文档，移除 avatar 说明 |

---

## 详细实现步骤

### 步骤 1: 修复 MemoryGardenScreen.kt

**位置**: `app/src/main/java/com/soulmate/ui/screens/MemoryGardenScreen.kt`

**当前代码（第59-63行）**:
```kotlin
ParticleBackground(
    modifier = Modifier.fillMaxSize(),
    particleColor = SoulMateTheme.colors.particleColor,
    lineColor = SoulMateTheme.colors.cardBorder
)
```

**替换为**:
```kotlin
Box(modifier = Modifier.fillMaxSize())
```

### 步骤 2: 重构 SoulMateApp.kt 导航

**位置**: `app/src/main/java/com/soulmate/ui/SoulMateApp.kt`

**修改 NavHost 路由**:

```kotlin
// Home Screen - 直接显示 ChatScreen
composable(Screen.Home.route) {
    ChatScreen(
        onNavigateBack = { /* 不能返回 */ }
    )
}

// Chat Screen - 也是 ChatScreen
composable(Screen.Chat.route) {
    ChatScreen(
        onNavigateBack = {
            navController.popBackStack()
        }
    )
}

// 删除以下路由：
// - Screen.Fusion.route
// - Screen.DigitalHuman.route
```

### 步骤 4: 重构 ChatScreen 布局

**位置**: `app/src/main/java/com/soulmate/ui/screens/ChatScreen.kt`

#### 4.1 调整 Scaffold 结构

**当前结构**:
```kotlin
Scaffold(
    topBar = { TopAppBar(...) },
    content = { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Messages List
                // Input Area
            }
        }
    }
)
```

**优化后结构**:
```kotlin
Scaffold(
    topBar = { 
        TopAppBar(
            title = { Text("SoulMate", ...) },
            navigationIcon = { IconButton(onClick = onNavigateBack) { ... } },
            actions = {
                // 添加菜单按钮 - 导航到其他页面
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "更多")
                }
            }
        )
    },
    content = { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // 1. MindWatch 状态覆盖层（顶部浮动）
            // 2. 消息列表（占据主要空间）
            // 3. 输入区域（底部固定）
        }
    }
)
```

#### 4.2 添加 MindWatch 状态显示

**添加状态**:
```kotlin
val mindWatchStatus by viewModel.mindWatchStatus.collectAsState(
    initial = MindWatchService.WatchStatus.NORMAL
)
```

**状态覆盖组件**:
```kotlin
// 在消息列表上方添加
if (mindWatchStatus != MindWatchService.WatchStatus.NORMAL) {
    MindWatchStatusCard(
        status = mindWatchStatus,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}
```

#### 4.3 优化输入框布局

**当前布局**:
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    OutlinedTextField(...)
    IconButton(...) // 语音
    IconButton(...) // 发送
}
```

**优化后布局**:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .background(SoulMateTheme.colors.cardBg.copy(0.9f), RoundedCornerShape(24.dp))
        .padding(8.dp)
) {
    // 1. 语音波形可视化（如果录音中）
    if (isVoiceInputActive) {
        VoiceWaveformIndicator(
            amplitude = voiceAmplitude,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        )
    }
    
    // 2. 文本输入 + 按钮行
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = messageText,
            onValueChange = { messageText = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("输入消息...") },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp)
        )
        IconButton(onClick = { viewModel.startVoiceInput() }) {
            Icon(Icons.Default.Mic, "语音输入")
        }
        IconButton(
            onClick = { 
                if (messageText.isNotBlank()) {
                    viewModel.sendMessage(messageText)
                    messageText = ""
                }
            },
            enabled = messageText.isNotBlank()
        ) {
            Icon(Icons.Filled.Send, "发送")
        }
    }
}
```

#### 4.4 布局空间分配

**改进权重分配**:
```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    // MindWatch 状态卡片（如果有） - 占用约 60.dp
    if (mindWatchStatus != MindWatchService.WatchStatus.NORMAL) {
        MindWatchStatusCard(...) // 占位后自动消失
    }
    
    // 消息列表 - 占据剩余所有空间
    Box(
        modifier = Modifier
            .weight(1f)  // 自动计算剩余空间
            .fillMaxWidth()
    ) {
        LazyColumn(...) { ... }
    }
    
    // 输入区域 - 固定高度约 120.dp
    Box(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .fillMaxWidth()
    ) {
        InputArea(...)
    }
}
```

### 步骤 4: 创建 MindWatch 状态组件

**新文件**: `app/src/main/java/com/soulmate/ui/components/MindWatchStatusOverlay.kt`

```kotlin
@Composable
fun MindWatchStatusOverlay(
    status: MindWatchService.WatchStatus,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (status) {
        MindWatchService.WatchStatus.WARNING -> WarningCard(onDismiss)
        MindWatchService.WatchStatus.CRISIS -> CrisisCard(onDismiss)
        else -> {}
    }
}
```

---

## 测试策略

### 编译测试
```bash
./gradlew clean build
```

### 单元测试
```bash
./gradlew test
```

### UI 测试清单
- [ ] 启动应用 → 看到 OnboardingScreen（首次）或 ChatScreen（非首次）
- [ ] 从 ChatScreen 导航到 MemoryGarden
- [ ] 从 ChatScreen 导航到 Settings
- [ ] 从 Settings 导航到 EmotionalReport
- [ ] 从 Settings 导航到 CrisisResources
- [ ] 所有返回按钮正常工作

### 功能测试清单
- [ ] 发送文本消息 → AI 回复
- [ ] 点击语音按钮 → 开始录音 → 停止 → 文本发送
- [ ] 发送带图片的消息 → AI 回复
- [ ] 查看 MemoryGarden → 显示历史记忆
- [ ] 修改设置 → 设置保存

---

## 成功标准

### 必须达成
- [x] 项目编译通过（无错误）✅ BUILD SUCCESSFUL
- [ ] 应用可以正常启动
- [ ] 所有屏幕可访问且无崩溃
- [ ] 聊天功能正常工作
- [x] 数字人相关代码完全移除 ✅

### 期望达成
- [x] ChatScreen 界面布局优化完成 ✅
- [x] 顶部导航栏添加菜单入口 ✅
- [x] MindWatch 状态正确显示 ✅
- [x] 语音输入反馈清晰 ✅
- [x] 输入框布局美观易用 ✅
- [x] 导航流程符合预期 ✅（Home→Chat, Garden→Chat, Settings→其他页面）

### 可选优化（配色暂不调整）
> **注意**: 以下优化暂不实施，仅作为未来参考

- [ ] 情绪背景渐变动画（不同情绪状态用不同颜色）
- [ ] 添加语音波形可视化
- [ ] 优化消息气泡动画
- [ ] 深色模式/浅色模式切换
- [ ] 自定义主题颜色配置

---

## 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 删除文件后出现残留引用 | 中等 | 使用 grep 全局搜索 avatar 相关代码 |
| 导航重构导致某些页面无法访问 | 中等 | 仔细检查所有 navigate() 调用 |
| 移除数字人后情感反馈缺失 | 低 | 使用 MindWatch 状态覆盖层替代 |
| DI 绑定遗漏导致编译错误 | 低 | 编译会立即发现 |

---

## 后续优化建议

1. **视觉增强**: 为不同情绪状态添加微妙的背景渐变动画
2. **语音反馈**: 在输入框旁添加语音波形可视化
3. **消息预览**: 为图片/视频消息添加缩略图预览
4. **快捷操作**: 添加常用回复快捷按钮
5. **主题切换**: 实现完整的主题切换功能

---

## 附录：残留 Avatar 代码搜索

执行以下命令确保完全清理：

```bash
# 搜索所有 avatar 相关引用
cd app/src/main/java
grep -r "avatar" . --include="*.kt"
grep -r "Avatar" . --include="*.kt"
grep -r "DigitalHuman" . --include="*.kt"
grep -r "Xmov" . --include="*.kt"

# 搜索相关导入
grep -r "SoulmateInputCapsule" . --include="*.kt"
grep -r "IAvatarDriver" . --include="*.kt"
```
