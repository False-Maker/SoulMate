## 目标
- Embedding 默认与 LLM 同步（同一个 modelId+key），你后续再填专用 embedding endpoint。
- Embedding/RAG 失败时：聊天不中断（降级继续对话），并给出**明确的非阻断提示**，避免你误判“它是对的”。

## 1) Embedding 与 LLM 同步（代码侧默认策略）
### 1.1 修改 DoubaoEmbeddingService 默认 modelId
- 文件：[DoubaoEmbeddingService.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/core/data/brain/DoubaoEmbeddingService.kt)
- 改动：
  - `getModelId()`：
    - 若 `DOUBAO_EMBEDDING_MODEL_ID` 配置非空且不是 `doubao-embedding` → 用它
    - 否则 → 用 `BuildConfig.DOUBAO_MODEL_ID`
  - 这样即使 local.properties 里暂时不给 embedding 配置，也会“和 LLM 同步”。

## 2) 降级不中断（关键：Embedding 挂了也能拿到 LLM 回复）
### 2.1 ChatViewModel：RAG 单独 try/catch
- 文件：[ChatViewModel.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/ui/viewmodel/ChatViewModel.kt)
- 改动：把 `ragService.prepareContextWithDebugInfo(...)` 从大 try 块里拆出局部 try/catch：
  - 捕获 `EmbeddingException`/网络异常 → `context = ""` → 继续 `buildStructuredMessages()` → 调用 LLM。
  - 不再让异常落入最外层 catch（否则会直接终止本轮对话）。

## 3) 增加“模型调用失败/降级提示”（你要求的点）
### 3.1 扩展 ChatState：增加 warning 字段
- 文件：[ChatState.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/ui/state/ChatState.kt)
- 新增：`warning: String? = null`（默认 null）。

### 3.2 ChatScreen：展示 warning Snackbar（非阻断）
- 文件：[ChatScreen.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/ui/screens/ChatScreen.kt)
- 参照现有 error Snackbar 再做一个 warning Snackbar：
  - 文案示例：`记忆检索暂时不可用，本轮已降级为基础对话（不使用历史记忆）`
  - 提供“关闭”按钮，调用 `onClearWarning`。
  - 颜色使用非 error 的 container（比如 secondary/tertiary），避免误导。

### 3.3 ChatViewModel：在降级处写入 warning，并提供 clearWarning()
- 文件：[ChatViewModel.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/ui/viewmodel/ChatViewModel.kt)
- 在捕获 embedding/RAG 异常的分支里：
  - `_chatState.update { it.copy(warning = "…已降级…") }`
- 新增 `clearWarning()`。

## 4) 验证（必须覆盖你的测试点）
- 用你当前的错误配置（embedding 404）：
  - 发送“你好”应能得到 LLM 回复；同时出现 warning 提示“已降级”。
- 把 embedding endpoint 配对后：
  - warning 不出现；RAG 正常工作。
- 真实 LLM 网络错误仍走原来的 error Snackbar（不混淆）。

确认后我会按 1→3→2 的顺序落地，并跑一次 `:app:assembleDebug`。