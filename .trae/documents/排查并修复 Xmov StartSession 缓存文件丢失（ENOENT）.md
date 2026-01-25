## 第4点：增强诊断日志（执行计划清单）

### 4.1 目标与产出
- 目标：从业务日志判断 ENOENT 发生阶段（init / 会话 / speak / 重绑）与触发条件（性别切换、生命周期）。
- 产出：一份可直接贴给 SDK 方的“时间序列日志”，包含 init、回调、destroy/rebind、异常堆栈。

### 4.2 需要改动的位置（只列明确落点）
- `AvatarCoreService.handleListenerCallback()`：[AvatarCoreService.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/data/service/AvatarCoreService.kt#L143-L178)
- `AvatarCoreService.initialize()`：[AvatarCoreService.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/data/service/AvatarCoreService.kt#L62-L141)
- `AvatarCoreService.bind()/destroy()/pause()/resume()`（同文件内，后续我会按实际函数位置插入）
- `AvatarContainer` 的 `factory/update`（用于记录重绑节奏）：[AvatarContainer.kt](file:///d:/Demo/SoulMate/app/src/main/java/com/soulmate/ui/components/AvatarContainer.kt#L76-L163)

### 4.3 具体执行步骤
1. 统一日志字段（所有关键日志都带上）
   - `sessionId`：本次 bind/init 的自增 id（Long），每次 init +1
   - `gender`：当前 userGender
   - `thread`：当前线程名
   - `containerId`：FrameLayout id
   - `hasInstance/isInitialized`：avatarInstance 是否存在、是否已初始化
2. 在 `initialize()` 增加 3 组日志
   - init 开始：打印 sessionId、gender、containerId、appId（不打印 appSecret）
   - init 反射成功（拿到 avatarInstance + 找到 initMethod）
   - init invoke 抛异常：打印异常类型 + message + 完整堆栈
3. 在 `handleListenerCallback()` 扩展回调覆盖
   - 目前已有：`onInitEvent`、`onVoiceStateChange`
   - 需要打开/处理的：`onReconnectEvent`、`onOfflineEvent`、`onDebugInfo`、`onNetworkInfo`、`onMessage`、`onStatusChange` 等（按 SDK 实际方法名匹配）
   - 对每个回调：打印 methodName、args（做长度/空保护），并带 sessionId
4. 在 `AvatarContainer` 的 factory/update 加“重绑时序日志”
   - factory bind 前后
   - update 里 gender 变化：destroy 前、destroy 后、rebind 前后
5. 日志降噪策略（避免刷屏影响性能）
   - debug 回调按采样率打印（例如每 N 次打印一次）
   - args 太长时截断到固定长度（例如 512/1024 字符）

### 4.4 验收标准
- 一次复现能从日志看出：
  - ENOENT 出现在 init invoke 期间还是 init 成功之后
  - 是否紧跟 destroy/rebind
  - SDK 回调在出错前最后一次事件是什么

---

## 第5点：业务侧兜底修复（执行计划清单）

### 5.1 目标与边界
- 目标：SDK 偶发读不到 `.bin` 时，应用自动恢复，不让用户卡在错误状态。
- 边界：不修改 AAR 内部，仅通过“目录保证 + 缓存清理 + 受控重试 + 串行化”降低/消除问题。

### 5.2 修复策略拆解（按风险从低到高）

#### 5.2.1 目录保证（低风险，先做）
1. 在每次 `initialize()` 前执行：确保 `context.filesDir/xmov_cache` 目录存在
2. 失败处理：
   - 如果 mkdirs 失败：日志告警 + 直接返回初始化失败（避免 SDK 走不可控分支）

#### 5.2.2 ENOENT 识别（核心触发条件）
1. 在所有反射调用点（至少 init、speak、可能还有其他 SDK 方法）catch Exception
2. 判定是否为“缓存缺失”
   - `e` 或 `e.cause` 是 `FileNotFoundException`
   - message 包含 `xmov_cache` 且包含 `ENOENT` 或 `No such file`
3. 把这类异常统一归类为 `AvatarState.Error("缓存缺失")`（对用户文案可更友好）

#### 5.2.3 受控恢复（清理 + 重建）
1. 触发条件：命中 5.2.2 的 ENOENT 识别
2. 恢复动作（严格顺序）：
   - step A：停止当前实例（调用 SDK pause/destroy，按现有封装能力）
   - step B：清理缓存
     - 优先：只清理本次角色目录（如果能从错误路径解析出 `jiangyan_14019_new` 这段）
     - 否则：清理整个 `filesDir/xmov_cache`
   - step C：延迟一个短冷却（1–3 秒，避免立刻再次命中同一竞态）
   - step D：重新 bind/init（使用同一 gender、同一容器）
3. 重试上限
   - 每个 sessionId 最多恢复 1 次（或全局 2 次），防止无限循环
4. 可观测性
   - 每次恢复都打日志：原因（异常 message）、清理了哪个目录、是否重建成功

#### 5.2.4 重绑串行化/节流（降低“先读后写”竞态）
1. `AvatarContainer` 的 gender 变化重绑改为“串行队列”
   - 同一时间只允许一个 destroy/bind 在跑
   - 新请求到来时：取消或合并为最后一次（debounce 300–500ms）
2. 严格保证顺序
   - destroy 完成后再 removeAllViews
   - removeAllViews 后再 bind
3. 失败重试
   - bind 失败不立即狂刷重试；等待下一次 compose update 或定时器回补一次

### 5.3 验证清单
1. 弱网/断网首次进入：应给出明确错误并在网络恢复后可重试成功
2. 快速切换性别 5 次：不应出现崩溃/白屏/无穷重绑
3. 复现 ENOENT：应触发一次自动清理 + 重建，并在日志中能看到完整链路

### 5.4 预计改动文件
- `AvatarCoreService.kt`：目录保证、ENOENT 识别、受控恢复（清理+重建）、日志
- `AvatarContainer.kt`：重绑串行化/节流（避免 destroy/bind 竞态）