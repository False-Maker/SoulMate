我现在处在只读模式，不能直接改文件；你点一下确认退出只读后，我会立刻帮你把 key 写进本机配置并验证。

## 我将做的修改（仅本机 local.properties，不提交仓库）
- 文件：`d:\Demo\SoulMate\local.properties`
- 把下面两行替换为你刚发的 ARK_API_KEY 的值（不带引号）：
  - `DOUBAO_API_KEY=...`
  - `DOUBAO_EMBEDDING_API_KEY=...`（保持与 DOUBAO_API_KEY 一致）

## 随后验证
- 重新 Sync/Build 生成 BuildConfig
- 跑一次单元测试或 assemble，确认不会再报 401/Unauthorized

确认后我就开始执行。