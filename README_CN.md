# SoulMate - AI 情感伴侣应用 🧠❤️

> **状态**: ✅ **功能开发完成** (准备进行 UI 美化)
>
> 一款基于 Android 的 AI 伴侣应用，具备双重思维处理、主动关怀（心跳协议）、端侧情绪 AI 及数字人集成。

## 📊 项目状态

| 模块 | 状态 | 说明 |
| :--- | :--- | :--- |
| **心跳协议** | ✅ 完成 | 触发条件：12小时沉默、纪念日、情绪低落 |
| **AI 大脑 (LLM)** | ✅ 完成 | 豆包 API + `[Inner]`/`[Reply]` 解析 |
| **记忆系统 (RAG)** | ✅ 完成 | ObjectBox HNSW + 豆包 Embedding |
| **情绪 AI** | ✅ 完成 | 端侧 ML 分类，带 fallback 降级 |
| **数字人** | ✅ 完成 | 魔珐 SDK (TTS、手势、表情) |
| **记忆花园 UI** | ✅ 完成 | 时间线视图、情绪标签、编辑/删除 |

---

## 🛠 技术栈

| 类别 | 技术 |
| :--- | :--- |
| 语言 | Kotlin |
| 架构 | MVVM + Clean Architecture |
| UI | Jetpack Compose |
| 依赖注入 | Hilt |
| 异步 | Coroutines & Flow |
| 后台 | WorkManager |
| 本地数据库 | ObjectBox (向量) + Room |
| 网络 | Retrofit + OkHttp |

### SDK 集成
- **数字人**: 魔珐 (Xmov) SDK
- **语音识别**: 阿里云 NUI SDK
- **大模型**: 豆包 (火山引擎 Ark)
- **向量嵌入**: 豆包 Embedding API

---

## 🚀 快速开始

### 1. 配置 (`local.properties`)

```properties
# LLM
DOUBAO_API_KEY=your_key
DOUBAO_MODEL_ID=your_model_id

# 数字人
XMOV_APP_ID=your_app_id
XMOV_APP_SECRET=your_secret

# 语音识别
ALIYUN_ASR_APP_KEY=your_key
ALIYUN_ACCESS_KEY_ID=your_id
ALIYUN_ACCESS_KEY_SECRET=your_secret

# Embedding (可选，默认复用 LLM key)
DOUBAO_EMBEDDING_MODEL_ID=your_embedding_model_id
```

### 2. 编译运行
1. 同步 Gradle
2. 在真机上运行 (API 24+)

---

## 📂 项目结构

```text
com.soulmate/
├── core/data/
│   ├── brain/         # LLMService, EmbeddingService
│   └── memory/        # MemoryEntity, MemoryRepository
├── data/
│   ├── repository/    # LLMRepository, AffinityRepository
│   └── service/       # AvatarCoreService
├── worker/            # HeartbeatWorker, EmotionTracker
└── ui/
    ├── screens/       # ChatScreen, MemoryGardenScreen
    └── components/    # AvatarContainer
```

---

## 🔮 剩余工作

| 方向 | 任务 |
| :--- | :--- |
| **UI 美化** | 动画、过渡效果、视觉优化 |
| **小优化** | 自定义通知图标、纪念日持久化 |
