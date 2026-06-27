# Debug Session: connect-mismatch-stall
- **Status**: [OPEN]
- **Issue**: 客户端 `GD656Conquest` 版本高于服务端时，连接卡在“正在连接至服务器”，日志重复出现 `handleDisconnection() called twice`，最终崩溃或无法切到 Forge 模组差异界面。
- **Debug Server**: http://127.0.0.1:7777/event
- **Log File**: .dbg/trae-debug-log-connect-mismatch-stall.ndjson

## Reproduction Steps
1. 服务端保持旧版 `GD656Conquest`，客户端安装新版 `GD656Conquest`。
2. 启动客户端并尝试连接该服务器。
3. 观察是否卡在“正在连接至服务器”。
4. 检查客户端日志中是否重复出现 `handleDisconnection() called twice`。

## Hypotheses & Verification
| ID | Hypothesis | Likelihood | Effort | Evidence |
|----|------------|------------|--------|----------|
| A | Forge 已写入 `FML_MOD_MISMATCH_DATA`，但 `ConnectScreen` 没有成功切换到 `ModMismatchDisconnectedScreen`，连接流程停留在旧 screen 上继续 tick。 | High | Med | Pending |
| B | 连接对象在模组差异断开时被重复调用 `disconnect/handleDisconnection`，第二次处理发生时 `PacketListener` 已为空。 | High | Med | Pending |
| C | 当前客户端模组环境中的其它 mixin 改写了 `ConnectScreen` 或 `Screen` tick 时序，放大了 Forge 原本边缘的断线竞态。 | Med | Med | Pending |
| D | 仅 `GD656Conquest` 的网络版本不匹配触发了某条自定义频道异常路径，导致 Forge mismatch 流程未完整收口。 | Med | Med | Pending |
| E | 我们新增的 `ModMismatchDisconnectedScreen` 提示 mixin 并未触发；问题发生在它之前。 | High | Low | Pending |

## Log Evidence
- 已添加运行时插桩：
  - `ConnectScreenDebugMixin`：记录连接界面进入与 tick 中 `Connection` / `PacketListener` / `mismatchData` 状态
  - `ConnectionDebugMixin`：记录 `disconnect` 与 `handleDisconnection` 的进入次数和 listener 状态
  - `ModMismatchDisconnectedScreenMixin`：记录差异界面是否真的初始化与渲染
- 调试服务器健康检查通过：`/health` 返回 `200`

## Verification Conclusion
[待补充]
