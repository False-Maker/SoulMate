## 0) 先对齐“当前项目已完成什么”（结合真实代码，不重复基础配置）

### 已完成（可直接测试）
- **基础配置已完成**：四个 endpoint + base_url + key 都已在本机 [local.properties](file:///d:/Demo/SoulMate/local.properties#L11-L18)
  - Chat：`DOUBAO_CHAT_ENDPOINT_ID=ep-20260124113633-dpnx2`
  - Vision：`DOUBAO_VISION_ENDPOINT_ID=ep-20260124113835-7szl5`
  - ImageGen：`DOUBAO_IMAGE_GEN_ENDPOINT_ID=ep-20260124113909-mggt5`
  - Embedding：`DOUBAO_EMBEDDING_ENDPOINT_ID=ep-20260124114105-cg6gv`
  - Base URL：`DOUBAO_BASE_URL=https://ark.cn-beijing.volces.com/api/v3/`
- **Chat（/chat/completions + 流式）已接通**：
  - [LLMApiService.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/data/repository/LLMApiService.kt#L17-L45)
  - [LLMService.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/core/data/brain/LLMService.kt#L62-L150)
- **Vision 多模态消息结构已接通**（messages.content string/object[]）：
  - [Message.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/data/model/llm/Message.kt)
  - [MessageContent.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/data/model/llm/content/MessageContent.kt)
  - [ContentPart.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/data/model/llm/content/ContentPart.kt)
  - Gson 适配器注册：[NetworkModule.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/di/NetworkModule.kt#L81-L107)
- **选图→看图问答（方案A：content://→base64 data URL）已完成**：
  - 选图入口：[ChatScreen.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/ui/screens/ChatScreen.kt#L161-L306)
  - 编码器：[ImageBase64Encoder.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/data/service/ImageBase64Encoder.kt)
  - 发送逻辑（失败降级+提示）：[ChatViewModel.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/ui/viewmodel/ChatViewModel.kt#L613-L689)
- **图片生成（/images/generations）已完成**：
  - [ImageGenApiService.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/data/repository/ImageGenApiService.kt)
  - [ImageGenService.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/core/data/brain/ImageGenService.kt)
- **文本向量化（/embeddings）已完成**（满足当前 RAG 文本检索）：
  - [DoubaoEmbeddingService.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/core/data/brain/DoubaoEmbeddingService.kt#L88-L119)

### 未完成（当前仓库确实没有代码）
- **真人/对端RTC语音通话、RTC视频通话（WebRTC/厂商RTC）**：仓库内没有 CallScreen/CallViewModel/WebRTC PeerConnection/信令客户端等实现；
  - 你看到的 [.trae/documents/音视频通话前端落地执行文档.md](file:///d:/Demo/SoulMate/.trae/documents/%E9%9F%B3%E8%A7%86%E9%A2%91%E9%80%9A%E8%AF%9D%E5%89%8D%E7%AB%AF%E8%90%BD%E5%9C%B0%E6%89%A7%E8%A1%8C%E6%96%87%E6%A1%A3.md) 是“待开发清单”，不是已落地代码。

---

## 1) 你要的“完整执行清单”= 从当前状态到完整目标的剩余待办
下面只列 **尚未做** 或 **可选增强** 的项（不把已完成再算一遍）。

---

## 2) 多模态剩余增强（可选，但属于“完整多模态”时必须）

### 2.1 让用户可控 Vision 成本：detail low/high 开关
- 现状：`ImageUrlPart.create(url, detail = "auto")`（[ContentPart.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/data/model/llm/content/ContentPart.kt#L36-L54)）
- 待办：
  - 在 UI 增加一个开关：默认 low，必要时 high
  - 传入 `ImageUrlPart.create(url, detail = "low"|"high")`
- 验收：同一张图 low/high 回答差异明显且 token/延迟可控。

### 2.2 视频理解（VideoUrlPart 目前“结构有了但入口没做”）
- 现状：`VideoUrlPart` 已定义（[ContentPart.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/data/model/llm/content/ContentPart.kt#L56-L78)），但 UI/发送链路没有“发视频”入口。
- 待办（两条路选一条）：
  - A：只支持“粘贴视频 URL”
  - B：支持“选视频/录视频”，并转成 base64 或先上传（更重）
- 验收：发视频→Vision endpoint→能回答（注意官方 fps/抽帧限制）。

---

## 3) 语音通话/视频通话：你必须先选定你要的“通话定义”
这一步不需要你补基础配置，但必须明确：你到底要哪一种“通话”。

### 3.1 路线一：与数字人“通话体验”（不做RTC，基于现有 ASR+数字人）
- 现状：你们已经有 ASR + 数字人播报；但缺“电话感三件套”。
- 待办清单：
  1) 免提端点（VAD 自动一句一句）——把当前 PTT 形态升级
  2) 打断（用户开口立即 stop 数字人播报）
  3) 轮次治理（取消上一轮 LLM job / 过期结果丢弃）
- 验收：免提可连续对话、不卡轮、不会抢话。

### 3.2 路线二：真人/对端RTC通话（WebRTC 或 厂商RTC）
- 现状：当前仓库 **完全未实现**，需要按文档从 0 落地。
- 待办清单（完全对齐你引用的执行文档，并绑定到本项目路径）：
  - 入口与路由：
    - 修改 [Screen.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/ui/navigation/Screen.kt)：加 VoiceCall/VideoCall
    - 修改 [SoulMateApp.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/ui/SoulMateApp.kt)：注册页面
    - 修改 [ChatScreen.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/ui/screens/ChatScreen.kt)：增加发起通话入口
  - 页面与组件（新增目录 `ui/screens/call/*`）：
    - VoiceCallScreen、VideoCallScreen、CallControls、RemoteVideoView、LocalPreviewView
  - 状态机（新增）：
    - CallViewModel、CallState
  - 信令层（新增，二选一）：
    - OkHttp WebSocket：CallSignalingClient/Protocol
    - 或 Socket.IO（项目已有依赖，可复用）：`io.socket:socket.io-client:2.1.0`（[build.gradle.kts](file:///d:/Demo/SoulMate/app/build.gradle.kts#L108-L116)）
  - RTC 媒体引擎（新增）：
    - WebRtcEngine/WebRtcPeer/AudioRouteManager
  - 权限与系统能力（修改）：
    - [AndroidManifest.xml](file:///d:/Demo/SoulMate/app/src/main/AndroidManifest.xml) 增加 CAMERA/前台服务等
  - 依赖（修改）：
    - [app/build.gradle.kts](file:///d:/Demo/SoulMate/app/build.gradle.kts) 增加 WebRTC artifact（当前没有）
- 验收：双端互打 3 分钟稳定；切前后台不掉线（若要求）；视频音画同步。

---

## 4) 官方文档链接（你要求写入清单）
- 鉴权与 Base URL：https://www.volcengine.com/docs/82379/1298459?lang=zh
- Chat/Vision（多模态 messages，走 /chat/completions）：https://www.volcengine.com/docs/82379/1494384?lang=zh
- 图片生成（/images/generations）：https://www.volcengine.com/docs/82379/1541523?lang=zh
- 文本向量化（/embeddings）：https://www.volcengine.com/docs/82379/1521766?lang=zh

---

## 5) 你接下来如何用这份清单
- 你现在测试：B/C/D/E（多模态+出图+向量）已经可测。
- 真正还缺的“完整大闭环”只剩：
  - 选做：2.1 detail 开关、2.2 视频入口
  - 必选其一：3.1 数字人通话体验增强 或 3.2 真RTC通话

我会在你选定 3.1 或 3.2 后，把对应部分再拆成可直接分配的任务颗粒（每条任务对应一个文件/模块 + 验收标准），避免再出现“清单看着完整但落不下来”。