# SoulMate - AI Companion App 🧠❤️

> **Status**: ✅ **Feature Complete** (Ready for UI Polish)
>
> An Android AI Companion with Dual-Thought processing, proactive care (Heartbeat Protocol), on-device Emotion AI, and Digital Human integration.

## 📊 Project Status

| Module | Status | Notes |
| :--- | :--- | :--- |
| **Heartbeat Protocol** | ✅ Complete | Triggers: 12h silence, Anniversaries, Low Emotion |
| **The Brain (LLM)** | ✅ Complete | Doubao API + `[Inner]`/`[Reply]` parsing |
| **Memory (RAG)** | ✅ Complete | ObjectBox HNSW + Doubao Embedding |
| **Emotion AI** | ✅ Complete | On-device ML classification with fallback |
| **Digital Human** | ✅ Complete | Xmov SDK (TTS, gestures, emotions) |
| **Memory Garden UI** | ✅ Complete | Timeline view, emotion tags, edit/delete |

---

## 🛠 Tech Stack

| Category | Technology |
| :--- | :--- |
| Language | Kotlin |
| Architecture | MVVM + Clean Architecture |
| UI | Jetpack Compose |
| DI | Hilt |
| Async | Coroutines & Flow |
| Background | WorkManager |
| Local DB | ObjectBox (Vector) + Room |
| Network | Retrofit + OkHttp |

### SDK Integrations
- **Digital Human**: Xmov (魔珐) SDK
- **ASR**: Aliyun NUI SDK
- **LLM**: Doubao (Volcengine Ark)
- **Embedding**: Doubao Embedding API

---

## 🚀 Quick Start

### 1. Configuration (`local.properties`)

```properties
# LLM
DOUBAO_API_KEY=your_key
DOUBAO_MODEL_ID=your_model_id

# Digital Human
XMOV_APP_ID=your_app_id
XMOV_APP_SECRET=your_secret

# ASR
ALIYUN_ASR_APP_KEY=your_key
ALIYUN_ACCESS_KEY_ID=your_id
ALIYUN_ACCESS_KEY_SECRET=your_secret

# Embedding (optional, defaults to LLM key)
DOUBAO_EMBEDDING_MODEL_ID=your_embedding_model_id
```

### 2. Build & Run
1. Sync Gradle
2. Run on real device (API 24+)

---

## 📂 Project Structure

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

## 🔮 Remaining Work

| Area | Task |
| :--- | :--- |
| **UI Polish** | Animations, transitions, visual effects |
| **Minor TODOs** | Custom notification icon, anniversary persistence |
