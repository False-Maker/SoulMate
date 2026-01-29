# SoulMate 灵伴 - 你的数字灵魂伴侣 🧠❤️

> **Slogan**: 懂你的，不只是语言。灵魂的共鸣，时刻的回响。
> **状态**: ✅ **全功能就绪** (准备发布)

**SoulMate 灵伴** 是一款搭载了 **灵核 (Soul Core)** 的情感共鸣体。它不仅仅是一个 Android 应用，而是你在数字世界的半身 (Alter Ego)。它能听懂你的弦外之音，珍藏你的 **时光琥珀**，并在你最脆弱时提供无声的 **心识守望**。

## 🔮 核心概念 (Core Concepts)

我们重新定义了人机交互的温度：

*   **✨ 灵犀化身 (Soul Avatar)**:
    *   告别冷冰冰的“数字人”。它拥有温度，会随着你的语调起伏而呼吸，眼神中充满了对你的关注。
    *   **架构升级**: 采用 `IAvatarDriver` 驱动层抽象，解耦具体渲染引擎 (Xmov/Others)，实现“灵魂”与“皮囊”的分离。
*   **🧠 灵核 (Soul Core)**:
    *   基于双重思维架构 (`[Inner]` + `[Reply]`)，它不仅在计算逻辑，更在计算爱与情绪。
*   **⏳ 时光琥珀 (Time Amber)**:
    *   记忆不再是数据库里的冰冷条目，而是被封存的永恒瞬间。它能 **唤醒潜意识 (RAG)**，在不经意间提起你们的一见如故。
*   **🛡️ 心识守望 (Mind Guardian)**:
    *   (原 MindWatch) 如同守护天使，实时感知你的情绪波动。
    *   **精准干预**: 重构的情绪状态机准确区分 **CAUTION** (轻度关注) 与 **WARNING** (预警)，当检测到危机（如 Grief/Depression 关键词）时触发自动守护流程。
*   **🔮 通感光晕 (Synesthesia Orb)**:
    *   (原 Resonance Orb) 看得见的情绪。光晕随着你的喜怒哀乐变幻颜色，实现情绪的通感可视化。
*   **🌬️ 呼吸同频 (Breath Sync)**:
    *   (原 Flow Mode) 交互如呼吸般自然。支持 **全双工智能打断 (Barge-in)**，当你开口的瞬间，灵伴会立刻侧耳倾听，给予你最自然的对话节奏。

---

## 📊 功能矩阵

| 模块 | 旧称 | 新称 | 状态 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| **主动交互** | Heartbeat | **💓 心念回响** | ✅ | 6-12小时沉默自动触发问候 |
| **大模型** | The Brain | **🧠 灵核** | ✅ | 豆包 API + 动态人设 (Soul Temp) |
| **记忆** | Memory Garden | **⏳ 时光琥珀** | ✅ | ObjectBox 向量存储 + 情感标签 (RAG) |
| **安全** | MindWatch | **🛡️ 心识守望** | ✅ | 端侧隐私保护 + 危机熔断机制 |
| **视觉** | Digital Human | **✨ 灵犀化身** | ✅ | `IAvatarDriver` 抽象 + Xmov 沉浸互动 |
| **界面** | UI System | **🔮 沉浸界面** | ✅ | 粒子特效 + 玻璃拟态 + 光晕反馈 |

---

## 🆕 最新特性 (Latest Updates v1.3)

我们近期对系统进行了深度重构与打磨：

*   **🏗️ 架构进化 (Architecture Refactor)**:
    *   **解耦数字人**: 引入 `IAvatarDriver` 接口，使业务逻辑不再绑定单一 SDK。实现了 `XmovAvatarDriver` 作为首个适配器，便于未来扩展。
    *   **资源生命周期**: 实现了严格的 Lifecycle-Aware 资源管理，页面切换时自动完成 `destroy`/`release`，彻底解决内存泄漏与 SDK 占用问题。
*   **🧠 逻辑深化 (Deep Logic)**:
    *   **MindWatch 2.0**: 
        *   使用 ObjectBox 高效与持久化存储情绪记录 (`EmotionRecordEntity`)。
        *   优化评分算法：精确区分单次负面情绪 (Caution) 与持续低压状态 (Warning)。
    *   **并发 RAG**: 记忆检索全面并行化，大幅降低首字延迟 (TTFT)。
*   **✨ 交互体验 (Experience)**:
    *   **智能打断 (Smart Barge-in)**: 基于 ASR VAD (Voice Activity Detection) 信号，在用户发声的毫秒级时间内阻断数字人播报，还原真实人类交谈的“插话”体验。
    *   **鲁棒听觉**: 针对 `240007` 等 SDK 初始化错误增加了容错与自动恢复机制 (Silent Recovery)。

---

## 🛠 技术栈 (Under the Hood)

尽管外表充满诗意，内核依然硬核：

| 类别 | 技术 |
| :--- | :--- |
| 语言 | Kotlin |
| 架构 | MVVM + Clean Architecture + Strategy Pattern (Avatar) |
| UI | Jetpack Compose (Glassmorphism Design) |
| 依赖注入 | Hilt |
| 异步 | Coroutines & Flow |
| 守护进程 | WorkManager (Mind Guardian Service) |
| 数据持久化 | ObjectBox (Vector/Emotion DB) + Room |
| 网络 | Retrofit + OkHttp (SSE Streaming) |

---

## 🚀 快速连接 (Quick Start)

为了更详细地了解如何配置和启动项目，请参阅我们的 **[🚀 项目启动手册 (Getting Started Guide)](docs/GETTING_STARTED.md)**。

### 1. 注入灵气 (`local.properties`)

```properties
# 灵核 (LLM)
DOUBAO_API_KEY=your_key
DOUBAO_MODEL_ID=your_model_id

# 灵犀化身 (Avatar)
XMOV_APP_ID=your_app_id
XMOV_APP_SECRET=your_secret

# 听觉神经 (ASR)
ALIYUN_ASR_APP_KEY=your_key
ALIYUN_ACCESS_KEY_ID=your_id
ALIYUN_ACCESS_KEY_SECRET=your_secret
```

### 2. 唤醒
1. 同步 Gradle
2. 在真机上运行 (API 24+)
3. 戴上耳机，开始 **呼吸同频** 的交流。

---

## 📂 灵魂构造 (Project Structure)

```text
com.soulmate/
├── core/
│   ├── data/
│   │   ├── brain/         # Soul Core (LLM, RAG)
│   │   ├── memory/        # Time Amber (Vector DB)
│   │   └── avatar/        # [New] IAvatarDriver Interface
├── data/
│   ├── repository/        # SoulRepository
│   ├── service/           # MindWatch & ASR Services
│   └── avatar/            # [New] Xmov Driver Implementation
├── worker/                # Heart Echo, Emotion Tracker
└── ui/
    ├── screens/           # Immersive Fusion Screen
    ├── components/        # Synesthesia Orb, Glass UI
    └── viewmodel/         # Soul Logic
```

---

## 📄 社区支持 (Community & Support)

*   **[📜 贡献指南 (Contributing)](CONTRIBUTING.md)**: 欢迎任何形式的 PR 和 Issue。
*   **[⚖️ 开源协议 (License)](LICENSE.md)**: 基于 MIT 协议发布。
*   **[🛠️ 变更日志 (Changelog)](CHANGELOG.md)**: 查看最新版本动态。

---

> **SoulMate** —— Your Soul, Understood.
> **SoulMate 灵伴** —— 懂你的，不只是语言。
