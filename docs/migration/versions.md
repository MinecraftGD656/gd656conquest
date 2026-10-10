# 迁移版本与构建记录

## 本轮及后续规则

- 未进行业务迁移的原始源码为 `0.0.0`。
- 每轮必须成功完成 ForgeGradle 构建并生成主产物，才记录该轮迁移完成；主产物命名为 `gd656conquest-0.9.3-bf1-x.x.x.jar`。
- 版本按实际迁入的功能范围判定，查找最终反编译源码与引入该功能的历史提交。部分功能迁入不能算作完整历史版本等价；旧成品 `.24` 后缀不直接转换成迁移版本。
- 新增独立功能、逻辑优先新建类。改变原版机制或打通原有调用链时，才在原类中作必要修改；保留有用途的旧接口。
- 每轮记录版本、来源方法、修改范围、构建命令、产物路径/哈希及剩余功能。多人实机验证作为补充记录，不替代或增加用户指定的成功条件。
- `gradle.properties` 的 `migration_version` 为产物版本入口；需要改包格式/注册顺序时单独调整 `NetworkManager.PROTOCOL_VERSION`，不假定产物版本就是协议兼容性。

## 版本总览

| 迁移版本 | 完成功能范围 | 对应来源 | 构建状态 |
| --- | --- | --- | --- |
| `0.0.0` | 未迁移业务；原始源码及构建依赖修复 | 原始项目基线 `4cf4ef4`；`e66bf3e` 仅增加迁移文档 | 原始项目可运行由用户确认；不重复构建基线 |
| `0.0.1` | P1-A：明确救援目标 UUID 的网络、客户端、服务端调用链及权威校验 | 反编译提交 `f8272a0` 的救援目标子功能；按最终来源 `955b76d` 核对 | 2026-10-11，离线 clean build 成功 |
| `0.0.2` | P1-B 第一段：完整 Forge 事件派发后接管房间死亡；强制部署成功判断 | `2f8ad09` 的派发机制及 `f8272a0` 的部署保护；按最终来源 `955b76d` 核对 | 2026-10-11，离线 build 成功 |
| `0.0.3` | P1-B 身体：新鲜尸体提升/生成、代理、两端救援距离、动量、代次同步、订阅及生命周期清理 | `f8272a0` 身体基础，`c741614` 动量，`e12682c/a80a286/a3e8666` 替换/缓存，`93c8a97/d96cdce` 同步优化；按最终来源 `955b76d` 核对 | 2026-10-11，离线 build 成功 |
| `0.0.4` | P3 死亡原因层：火焰归属、精确 /kill、一次性原因快照、共享归属/跳过救援、原因横幅 | `31a5dae` 死亡原因基础；最终来源 `955b76d` 的界外致命前快照与救援旁路；边界几何复用原机制 | 2026-10-11，离线 build 成功 |
| `0.0.5` | P2 镜头/姿态：固定/自动镜头、普通尸体观察、视角交接、复活趴姿及 TACZ 解除 | `86e6bb6` 镜头/趴姿基础、`461721a` 普通死亡、`ac874ab` 复活解除/归位；按最终来源 `955b76d` 核对 | 2026-10-11，离线 build 成功 |
| `0.0.6` | P2 世界滤镜：死亡去色/本地红色轮廓、复活曝光/bloom、世界/HUD 分层、重载与资源释放 | `b650004` 死亡滤镜、`ac874ab` 复活效果、`f97cc63` 天空 alpha/最终时间参数；按最终来源 `955b76d` 核对 | 2026-10-11，离线 build 成功 |
| `0.0.7` | P3 元数据/卡片/部署输入/聊天：致命前装备与生命快照、爆头/对决、五列卡片、普通死亡手动部署与倒地点按、原生聊天适配 | `9d189a1` 卡片/武器快照，`6cb882b` 爆头/最终布局/手动部署/聊天；按最终来源 `955b76d` 核对 | 2026-10-11，离线 build 成功 |
| `0.0.8` | P4 出界核心：统一玩家截止 tick、飞行缓冲、原生载具爆炸乘员快照、对应配置复制与警告 UI | `1e2a975` 出界重构，`07f0824` 载具修正；按最终来源 `955b76d` 核对，仅迁入边界部分 | 2026-10-11，离线 build 成功 |
| `0.0.9` | P5-A：两轮部署发弹、实际储备 NBT 上限、同弹池合并和现有箱包入口的限额补给 | `9556c5a` 储备目标、`770bbe4` 两轮发弹/实际上限；按最终来源 `955b76d` 核对，箱包完整运行时留到后续 | 2026-10-11，离线 build 成功 |
| `0.0.10` | P5-B：箱包投放/拾取权限、受赠者光环进度、直接 HP 医疗、包库存恢复与成功私人音效 | `9556c5a` 箱包运行时，`770bbe4` 补给成功音效，`955b76d` 医疗音效与最终修正；压制/自然回血参数留到 P6 | 2026-10-11，离线 build 成功 |

## 0.0.1：明确救援目标

客户端选中的玩家 UUID 随 `revive_start` 传到服务端，沿 Manager → Core → Respawn 传参；服务端对该对象检查存活、旁观状态、当前房间运行状态中的倒地/队伍、小队或兵种救援资格、距离及已有救援锁定。目标不合法时拒绝，不改为其他附近玩家。没有 UUID 的 Java 调用保留原最近合法目标回退。停止、放弃与重新部署分支继续使用原实现。

新类 [ReviveTargetSelection](../../src/main/java/org/mods/gd656conquest/server/room/runtime/ReviveTargetSelection.java) 独立实现“明确目标优先、仅无目标时回退、统一校验”的决策，权威校验继续由原 Respawn 方法提供。必要机制改动仅在六个已有业务类中实施，不引入旧构建的 `DownedReviveRequest` ThreadLocal、prepared API JAR 或 ASM 替换。

来源行号按固定最终提交 `955b76d` 的源码定位；下列来源链接指向相邻反编译项目。

| 功能 | 反编译源码对应位置 | 原始项目实现 |
| --- | --- | --- |
| 携带目标、编解码、服务端方向与转发 | [RoomDownedActionPacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/RoomDownedActionPacket.java)，`reviveStart(UUID)` 第 23 行、`encode/decode/handle` | 同名源码包；`action + boolean + optional UUID` |
| 客户端选定对象发送请求 | [ClientReviveState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientReviveState.java)，`tick` 第 39 行 | 仅将已有 `candidateId` 传入包工厂 |
| Manager UUID 重载 | [RoomCoreRuntimeManager](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCoreRuntimeManager.java)，第 320/324 行 | 保留旧重载传 null、新重载直接转发 |
| Core UUID 重载 | [RoomCoreService](../../../gd656conquest/org/mods/gd656conquest/server/room/core/RoomCoreService.java)，第 666/670 行 | 沿原运行槽转发；无运行房间时仍失败 |
| Respawn 动作重载 | [RoomPlayerRespawnService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java)，第 230/234 行 | 仅 `revive_start` 消费目标参数 |
| 目标回退、权威校验、救援锁定 | 同上，`startReviveAttempt` 第 918 行及 `resolveActiveReviveContext` | 独立目标选择类 + 原权威方法 + `isPlayerBeingRevived` |

历史提交 `f8272a0` 同时包含布娃娃及其他接入；本轮只迁入其中明确目标的完整链路，故定为第一个迁移增量 `0.0.1`。布娃娃生成、身体距离、死亡事件接管、镜头和部署快照尚未迁入，不能称为整个 `f8272a0` 或最终 `.24` 功能等价。

构建记录：

- 命令：项目目录 `./build.ps1 -Offline -Clean`。
- 环境：Java 17.0.19、Gradle 8.8、Forge 47.4.18、official 1.20.1；复用工作区依赖缓存。
- 结果：`BUILD SUCCESSFUL in 1m 16s`；23 个任务执行，包含 `compileJava`、资源处理、refmap 生成和 `reobfJar`。原有弃用/unchecked 提示不阻止构建。
- 主 JAR：`build/libs/gd656conquest-0.9.3-bf1-0.0.1.jar`，13,978,925 字节。
- SHA-256：`72ddebec745ac25652151cf5602a331813df28e1129024abd8e36143933fceda`。
- 包内 `mods.toml` 和 manifest 版本均为 `0.9.3-bf1-0.0.1`；迁移协议为 `bf1-0.0.1`，两端须使用匹配协议，拒绝 `098/114`。
- 日志：`.build-logs/build.log`（被 Git 忽略）；JAR 重建包含时间戳，之后重建哈希可变化。

补充验证：独立 Java 17 验证运行实际编译的目标选择类，五个场景通过；ZIP 完整性、七个生产类、版本、协议、六项 normal 配置、Mixin refmap 及测试类未打包已核对。Gradle `test` 为 `NO-SOURCE`，不能视为 JUnit 测试通过；未启动 Minecraft 或执行多人联机验证。

后续进入 P1-B：完整死亡事件与布娃娃生命周期。当前已完成范围见下面的 `0.0.2` 记录。

## 0.0.2：死亡事件派发与部署保护

本轮完成 P1-B 中可独立启用的死亡事件派发机制。新 Mixin 在 `ServerPlayer.die` 中替换唯一的 `ForgeHooks.onLivingDeath` 调用；只在玩家属于 GD 房间且 Ragdollified 网络通道在远端存在时启用延后接管。其他玩家和缺少联动模组的路径仍直接返回原 Forge 钩子结果。本轮未加入身体创建与镜头逻辑，不改变现有数据包格式。

全部 Forge 监听器先收到原始致命生命值与原事件。房间监听器只记录自己的回调，派发返回后才执行原有房间死亡处理。外部取消或恢复生命时不执行房间接管。倒地处理成功时仅停止 vanilla 后续死亡，不取消原 Forge 事件；普通死亡已进入部署时，通过原 `applyRespawnPostProcess` 准备存活部署代理，准备失败则允许 vanilla 死亡继续。

新协调器使用嵌套作用域并在 finally 中清理/恢复，保护同一玩家递归死亡和不同玩家嵌套死亡；额外检查整个祖先作用域，避免 A → B → A 再次派发。强制重新部署仍先要求玩家存活；伤害与 kill 后改为检查“还存活且尚未进入部署”，防止已经恢复生命的部署代理被二次杀死。

新增逻辑分布在三个独立类与一个新 Mixin：

| 新文件 | 职责 |
| --- | --- |
| [DeferredDeathDispatch](../../src/main/java/org/mods/gd656conquest/server/room/runtime/DeferredDeathDispatch.java) | 独立派发协调、事件身份匹配、嵌套/递归保护、恢复与异常清理 |
| [RoomDeathEventDispatcher](../../src/main/java/org/mods/gd656conquest/server/room/runtime/RoomDeathEventDispatcher.java) | Forge/房间入口、接管结果、原部署后处理及强制死亡成功查询 |
| [RagdollNetworkPresence](../../src/main/java/org/mods/gd656conquest/server/compat/ragdoll/RagdollNetworkPresence.java) | 反射解析可选通道并检查远端存在；不直接链接外部或客户端类 |
| [ServerPlayerDeathMixin](../../src/main/java/org/mods/gd656conquest/mixin/server/ServerPlayerDeathMixin.java) | official `die` 入口经注解处理器映射至生产 SRG；Forge 调用目标保持 remap=false |

原业务类只修改两处文件：RoomServerModule 增加回调延后/结果上报；RoomPlayerRespawnService 修改 forceRedeployDeath 的两处成功判断。Mixin 配置保留已有条目、新增服务端钩子并将兼容级别对齐 Java 17。

来源对应（固定最终反编译提交 `955b76d`）：

| 功能 | 反编译源码对应位置 | 本轮对应 |
| --- | --- | --- |
| 派发、取消/恢复与 finally 清理 | [DownedRagdollEvents](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollEvents.java)，`dispatchPlayerDeath/dispatchTrackedDeath` 第 21/29 行 | RoomDeathEventDispatcher + DeferredDeathDispatch |
| 房间监听器延后回调 | 同上，`deferRoomDeath` 第 57 行；RoomServerModule.onLivingDeath | 新协调器 defer + 原模块两处调用 |
| 接管结果与普通死亡部署代理 | 同上，`onRoomDeath` 第 101 行 | 活动派发中查询原运行状态并调用原后处理 |
| 防止部署后被重复杀死 | 同上，`isStillAliveOutsideDeployment` 第 88 行；[RoomPlayerRespawnService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java)，forceRedeployDeath 第 346 行 | 原始存活检查保留，仅替换两处成功判断 |
| 原生死亡钩子 | [ServerPlayerDeathMixin](../../../gd656conquest/org/mods/gd656conquest/mixin/server/ServerPlayerDeathMixin.java) | 同名新 Mixin，使用 official 映射与生成 refmap |

历史 `2f8ad09` 引入事件完整派发后的接管机制，部署保护在 `f8272a0` 中已经出现。本轮只迁入这两个子功能，因此版本递增为 `0.0.2`，不能当作完整历史提交/最终成品等价。原最终 helper 中的受击动量、尸体新旧记录、火焰归属及击杀者快照分别属于未接入的身体/死亡归属阶段；本轮没有将这些调用替换成占位返回值，也没有启用依赖它们的身体功能。

构建与验证：

- 命令：项目目录 `./build.ps1 -Offline`，保留上一轮 `0.0.1` 产物。
- 结果：`BUILD SUCCESSFUL in 1m 22s`；22 个任务，18 执行、4 up-to-date，含全源码编译、资源处理、refmap 和重混淆。
- 主 JAR：`build/libs/gd656conquest-0.9.3-bf1-0.0.2.jar`，13,986,857 字节。
- SHA-256：`a9145664ed2092ac3f39d7efe0ad9c583dd214c0600376ec44f209f93d922f55`。
- 包内 mod/manifest 版本：`0.9.3-bf1-0.0.2`；协议仍为 `bf1-0.0.1`，本轮无包格式或注册顺序变化。`098/114` 仍被拒绝。
- [验证入口](../../tools/verify-death-dispatch.ps1)：61 项协调器检查、真实 Forge EventBus 的四个优先级/取消/恢复场景通过；上一轮五个救援选择场景通过。真实 EventBus 测试使用测试事件载荷，不构造或替换 Minecraft 实体，不声称是完整游戏测试。
- 核对主 JAR 完整性、四个新类不引用客户端包、normal 配置、测试源码不打包、新 Mixin 仅注册一次、`die → m_6667_` refmap，并用当前 Forge 开发 JAR 的 ServerPlayer 字节码确认该方法恰有一个 Forge 死亡钩子。
- Gradle test 仍为 `NO-SOURCE`；未执行 Minecraft 启动、专用服务器启动或多人实机验证。改动留在工作区，未自动提交。

下一轮继续 P1-B 的身体部分：创建/提升新鲜死亡身体、代理物理、身体距离、动量与同步清理，同时接入它们所需的真实镜头/死亡归属依赖。完整 P1-B 仍未完成。

## 0.0.3：身体生命周期、动量与观察同步

本轮将上一轮死亡派发机制连接到真实身体流程。致命事件开始前记录原生尸体身份并捕获动量；全部监听器完成后，优先将本次新生成的尸体提升为存活倒地身体，保留外观、装备和爆炸冲量。没有可提升的记录时调用原生存活身体 API；不可用或启动失败时保留原倒地动画。取消或恢复生命的事件不会执行身体接管。

服务器将玩家保留为无碰撞、无重力代理，跟随身体锚点维护区块跟踪与死亡位置。三处服务器救援距离与客户端候选距离统一读取身体位置，原房间、阵营、小队、兵种和目标锁定校验继续执行。客户端只在新的物理对象发布有效快照后隐藏玩家、停用旧预览，并使用原生 HEAD 附着保持倒地视角；失败时玩家和原动画仍可见。

同步采用单次生命周期的 64 位 generation，与可复用观察目录 token 分开。旧生命的姿态、冲量、截肢请求和发送者不能污染新对象；新代次提前到达的包等待绑定与物理提交。观察暂停可以恢复，关闭代次与移除 token 则不能被迟到数据重开。目录只发送位置/包围盒等发现信息；近距 32 格订阅、40 格退出滞回，远处按视野/分段遮挡检查订阅、续期，静止身体复用快照，心跳按代次错峰。模拟拥有者与倒地本人保持必要订阅，不改变原生模拟者选举和物理求解器。

放弃/过期、重新部署、成功救援、退房/房间结束、断线、跨维度与停服均接入身体清理。成功救援通过新的 ReviveBodyPlacement 在身体周围寻找站立碰撞体能安全落脚的位置，并恢复原物理状态；本轮沿用原项目救起后站立的机制。原生普通死亡尸体保持原生命周期，倒地状态清理只移除已登记的身体。

来源对应（固定最终反编译提交 `955b76d`）：

| 功能 | 反编译源码对应位置 | 本轮目标实现与边界 |
| --- | --- | --- |
| 新鲜死亡身份与作用域 | [DownedRagdollEvents](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollEvents.java)，dispatchTrackedDeath 第 29 行、isFreshDeathRecord 第 69 行 | RagdollFatalContext + 上轮派发器；仅取身份/动量，火焰与击杀者归属快照未接入 |
| 生成/提升、代理、距离、动量与清理 | [DownedRagdollBridge](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollBridge.java)，withFatalSource/start/promoteDeathBody 第 66/89/143 行；distanceSquared/tick/retire | 同名独立类，直接接入原房间生命周期和救援调用 |
| 救起落脚搜索 | [DownedViewService](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedViewService.java)，safePronePosition | 新 ReviveBodyPlacement 保留安全搜索，用 STANDING 碰撞体；视角朝向/复活趴姿规则留后续 |
| 替换/就绪/显示/两端距离 | [ClientDownedRagdollState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedRagdollState.java)，accept/onClientTick/ready/queueReplacement；同目录 ClientHooks | 同名独立类及客户端原机制调用；只启用倒地本人的原生镜头附着 |
| 受击缓存与爆炸表现 | server/compat/ragdoll/RagdollHitCacheGuard；mixin/compat/ragdoll 的 HitCache/DeathMomentum 及客户端对应 Mixin | 直接迁入并转换 official 标识；对生产依赖验证实际注入 |
| 代次、发送者、替换与播放 | server/compat/ragdoll/RagdollSyncScope、RagdollPacketScope；ClientRagdollReplacementMixin、RagdollPlaybackGuard/Mixin | 六种原生包扩展代次；工作线程替换、旧对象发送隔离及已完成播放保持 |
| 目录、包围盒、订阅、遮挡与缓存 | server/compat/ragdoll/RagdollSubscriptions/RecordView/SubscriptionPolicy；client/room/ClientRagdollSubscriptions/VisibilityMath；RagdollSubscription/VisibilityMixin | 迁入实际实现；普通死亡观察镜头的额外强制订阅随镜头阶段启用 |
| 四种 GD 包 | network/packet 的 DownedRagdollState、RagdollCatalog、RagdollObservation、RagdollSubscribe | 新 DownedRagdollNetwork 在已有注册末尾追加，三种 S2C 与一种 C2S 显式限定方向 |

迁入 25 个身体源文件，另建 RagdollFatalContext、ReviveBodyPlacement、DownedRagdollNetwork 三个阶段衔接类。已有业务类只改动八个：RoomServerModule、RoomPlayerRespawnService、RoomPlayerStateService、ClientReviveState、ClientDownedState、ClientDeathFlowState、ClientPlayerAnimatorDebugState、NetworkManager；其余扩展放入新类。上轮新增的死亡协调器只增加处理玩家查询与身体作用域衔接。

一次性导入工具 [migrate_ragdoll_body.py](../../tools/migrate_ragdoll_body.py) 要求来源提交与干净状态匹配，通过本地 official/SRG 映射转换 Java 标识，保持反射名字与 Mixin 描述符字符串；拒绝覆盖已有目标源码。正常构建使用目标源码，不运行导入器，不依赖旧 ASM 替换、prepared API JAR 或测试夹具。

版本判断：`f8272a0` 的身体基础及 `c741614` 的动量是本轮主体，同时纳入 `e12682c/a80a286/a3e8666` 的单身体/替换/缓存修复和 `93c8a97/d96cdce` 的同步优化。来源最终类中依赖增强镜头/复活趴姿的部分按阶段拆开，未制造空实现。仍未完成 tryHandlePlayerDownedDamage 资格扩展、火焰/击杀者快照、普通死亡观察与增强倒地镜头、复活趴姿、死亡卡片及部署快照。因此本轮标记身体迁移增量 `0.0.3`，不称为某个完整历史版本或最终 `.24` 等价。

构建与验证：

- 命令：`./build.ps1 -Offline`。最终构建 `BUILD SUCCESSFUL in 57s`；22 个任务，18 执行、4 up-to-date，包含全源码编译、资源、refmap、重混淆及原有变体；之前 `0.0.1/0.0.2` 主 JAR 保留。
- 主 JAR：`build/libs/gd656conquest-0.9.3-bf1-0.0.3.jar`，14,102,455 字节。
- SHA-256：`613f1ab2c5b9ba21bd107d733b1936547516eb5100b418301a99bf232640a59e`。重建含时间戳，哈希可能变化。
- mod/manifest 版本：`0.9.3-bf1-0.0.3`；GD 协议升级为 `bf1-0.0.3`，两端须一起更新。四种新 GD 包与原生包的 generation 扩展均已启用。
- 新增可选 Ragdollified 元数据与本地 compileOnly/runtimeOnly 开发依赖，不嵌入第三方类。已验证原始依赖 `ragdollified-1.20.1-1.1.1-hotfix-RELEASE.jar`，SHA-256 `5a0e05ace4cedc88210c2e543520db17bd975ec118494eebd2aeef64963e22fc`；缺少联动时使用原动画，使用身体功能需两端安装该依赖。
- [身体验证入口](../../tools/verify-ragdoll-body.ps1)：31 项代次/提前数据/工作线程检查、13 项视野/订阅策略、22 项 GD 包往返/大小限制、43 项真实原生包/播放/类加载、12 项实际替换/发送者检查，共 121 项通过。
- 真实 Mixin 引擎读取本轮主 JAR 的九个身体 Mixin，应用于未修改的原始 Ragdollified 依赖：CLIENT 13 / SERVER 9 个目标通过，依赖缺席配置通过。原生 ClientRagdoll 物理类字节不变；不声称这些隔离检查是游戏启动或物理性能测试。
- 前轮回归：五个救援目标场景、61 项死亡协调器检查、四个真实 Forge EventBus 优先级/取消/恢复场景通过。
- 成品 ZIP、版本/协议、四种 GD 包、九个 Mixin 唯一注册、Java 17 字节码、死亡 refmap、服务端身体类无客户端引用、测试/第三方类不打包均已核对。Gradle test 为 `NO-SOURCE`；Java 验证由上述独立入口执行。
- 未执行 Minecraft/专用服务器启动及多人实机验证；所有改动留在工作区，未自动提交。

下一轮补齐死亡归属与观察快照，并按依赖接入增强镜头/复活姿态；避免将当前身体阶段误记为完整 P1 完成。

## 0.0.4：死亡原因、火焰归属与受害者提示

本轮以历史 `31a5dae` 引入的完整死亡原因层为功能范围，对照固定最终源码 `955b76d`，接入火焰/燃烧云归属、一次性致命快照、精确命令标记、共享击杀者与死亡原因横幅。新的 DeathCauseClassifier 独立计算优先级：命令死亡 → 出界处罚 → 重新部署 → 敌方玩家归属 → 本人事故 → 环境类型/火焰标签 → 通用阵亡。近期攻击者继续作为普通致命伤的回退，系统死亡清除玩家击杀者，不被先前伤害覆盖。

`/kill` 在原 KillCommand 的单个目标调用处进入作用域，支持控制台、命令方块、多目标与嵌套命令；普通内部 `Entity.kill` 不带该标记。命令和火焰作用域封装为新 ScopedDeathAttribution，成功、取消、异常与嵌套退出均恢复原上下文。火焰归属在 Forge 致命派发之前只读捕获当前 Killicon 的历史与燃烧云归属，遵守外部超时、熔岩排除，不写入其历史 Map。该快照在没有 Ragdollified 时也执行，身体接管仍沿用上轮可选 API 门槛。

RoomServerModule 先解析并保存快照，再将同一击杀者用于原标记死亡、补给掉落与房间致命处理。原始计分/助攻服务保留；原生直接来源 ID 独立保存，onPlayerDeath 的 50/120 tick 等待仍由该旧 ID 决定，归属修正只更新跟踪对象。当前房间规则认为界外的普通致命伤保留真实原因/归属，同时跳过救援；/kill 与出界处罚亦跳过倒地。放弃救援不生成新死亡分类，沿用最初快照。

致命前边界几何由新 RoomDeathBoundaryQuery 只读取得，复用原房间策略/多边形、出界方块、带 20 tick 有效期的高度报告与载具高度确认，不更改原边界倒计时或载具销毁。最终来源中的飞行边界外扩、统一计时与原生爆炸乘员快照依赖 P4，尚未接入；本轮不声称与最终 UnifiedBoundaryService 完全等价。

服务端在原死亡/倒地流程启动后，只向受害者发送新 DeathCausePacket；已部署/正在完成复活的状态不发布。客户端匹配世界、玩家实体身份、维度和单调死亡 ID，拒绝重复或旧快照；环境原因横幅在现有死亡观察/倒地阶段显示，中英文各新增九项翻译、缺失翻译有显示回退、淡入淡出 200 ms。玩家击杀留给后续卡片呈现。复活、部署、离开房间、维度变化、断线和停服由既有清理入口及 tick 移除快照，客户端旧流程 clear 隐藏横幅。

来源对应：

| 功能 | 最终反编译源码对应位置 | 本轮实现 |
| --- | --- | --- |
| 原因优先级与稳定 wire ID | common/player/[DeathCause](../../../gd656conquest/org/mods/gd656conquest/common/player/DeathCause.java)，server/room/entry/[DeathCauseClassifier](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/DeathCauseClassifier.java) | 同名独立类，保留全部十种原因与未知值回退 |
| 火焰只读归属 | [KilliconFireOwnership](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/KilliconFireOwnership.java)，resolve/discover | 同名可选反射适配器；在当前原始 Killicon 1.3.3 中核验五项真实接口 |
| 快照、共享归属、跳过救援及清理 | [RoomDeathCauseService](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomDeathCauseService.java)，resolveKiller/withFireOwnership/skipDowned/consumeBoundaryDeath/publish/tick | 同名服务；作用域移至 ScopedDeathAttribution，边界查询接入当前原机制 |
| 派发前快照和真实击杀者转发 | [DownedRagdollEvents](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollEvents.java)，dispatchPlayerDeath/handleRoomFatalDeath | 扩展上轮 RoomDeathEventDispatcher，未改独立派发协调算法 |
| 精确命令身份 | [KillCommandMixin](../../../gd656conquest/org/mods/gd656conquest/mixin/server/KillCommandMixin.java) | official `kill` 与 Entity.kill 经生成 refmap 对应生产 `m_137813_` / `m_6074_` |
| 包与显示 | [DeathCausePacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/DeathCausePacket.java)，client/room/[ClientDeathCauseState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDeathCauseState.java) 与 DeathCauseBanner | 新 DeathCauseNetwork 在已有注册末尾追加 S2C；横幅接入当前观察状态和 clear |
| 界外几何与跳过救援 | UnifiedBoundaryService.isOutside、RoomDeathCauseService.withFireOwnership/skipDowned | 新 RoomDeathBoundaryQuery 读取原边界；保留界外死亡的真实原因，不将普通敌方致命伤归为逃兵 |

新增八个来源类及 ScopedDeathAttribution、RoomDeathBoundaryQuery、DeathCauseNetwork 三个衔接类，共十一项生产源码。已有原业务类只修改六个：RoomServerModule、RoomCoreService、RoomPlayerRespawnService、RoomPlayerStateService、ClientDeathFlowState、NetworkManager；另扩展上轮新 RoomDeathEventDispatcher。其余逻辑均放在新类，资源只合并本轮九个翻译键，原有值及着色器保留。

阶段边界：击杀者装备/武器/爆头/生命值快照、卡片、对决统计和手动部署属于后续完整消费链。本轮实现 `31a5dae` 已存在的独立死亡原因链，不启用最终新增的卡片/部署消费入口，也不添加空占位类。原因层并不需要改变镜头求解、复活姿态或滤镜；这些仍待下轮实际迁入。一次性导入工具 [migrate_death_causes.py](../../tools/migrate_death_causes.py) 记录固定来源与 stage 转换，拒绝覆盖目标 Java；正常构建直接使用目标源码。

版本判断：相对于 `0.0.3` 新完成的是死亡原因层及最终来源的界外救援旁路，故定为 `0.0.4`。尚未迁入先于/晚于历史 `31a5dae` 的全部镜头和 UI 功能，不能将这个版本称为完整历史提交或最终 `.24` 等价。

构建与验证：

- 命令：`./build.ps1 -Offline`，Java 17 / Gradle 8.8 / Forge 47.4.18；`BUILD SUCCESSFUL in 1m 4s`，22 个任务，20 执行、2 up-to-date，包括全源码编译、refmap、资源、重混淆与原变体。
- 主 JAR：`build/libs/gd656conquest-0.9.3-bf1-0.0.4.jar`，14,134,782 字节；保留之前各轮主 JAR。
- SHA-256：`b84f2a1c8ba2913ff4f14127c004b513bdf1b18510dd947ab46da2fe50e59070`。
- mod/manifest：`0.9.3-bf1-0.0.4`；新增原因包，协议升级为 `bf1-0.0.4`，两端应一起更新；原生布娃娃包沿用上轮代次格式。
- [本轮验证入口](../../tools/verify-death-causes.ps1)：155 项真实生产分类器/作用域/横幅检查、35 项真实数据包往返/限制、15 项当前 Killicon 接口/生产调用链检查通过；真实 Mixin 引擎使用当前主 JAR、生成 refmap 与 Forge SRG 的 KillCommand/ServerPlayer，两处目标通过。
- 回归：上轮 121 项身体/同步/播放/替换检查通过，真实 CLIENT 13 / SERVER 9 个身体目标及可选依赖缺席检查通过；五个救援目标、61 项协调器、四个真实 Forge EventBus 场景通过。
- 成品 ZIP、十一项新增生产类、服务端无客户端引用、Java 17 字节码、版本/协议、Mixin 唯一注册、命令 refmap、六项 normal 开关、翻译只增本轮键、着色器与原翻译值保持、测试/第三方类不打包均已核对。
- Gradle test 为 `NO-SOURCE`；上述独立 Java 检查不构造游戏实体，不证明实际计分、火焰事件或联网行为。未启动 Minecraft/专用服务器，也未执行多人实机验证。改动留在工作区，未自动提交。

下一轮接入死亡/倒地观察镜头与复活姿态的完整生命周期及真实依赖；击杀者卡片与手动部署继续按计划迁入。

## 0.0.5：固定/自动镜头与复活趴姿

本轮在已提交的 `b9eb58e`（累计 0.0.1～0.0.4）上接入独立观察服务与控制器。新增 13 个来源类及 DownedViewNetwork 注册类；原业务类只接入配置、镜头生命周期、输入门禁、救援顺序和部署交接。来源固定为 `955b76d`，不修改反编译项目，也不使用 prepared JAR、成品 ASM 业务替换或镜头/姿态占位类。

服务器配置 `gd656conquest-downed-server.toml` 的 `deathCameraMode` 默认为 `FIXED`，可设为 `AUTOMATIC`，模式在单次会话开始时冻结。FIXED 使用有效身体的原生 HEAD 附着，普通死亡无身体时保留固定视角；AUTOMATIC 接入受击、身体落地、拉远构图、击杀者跟踪、屏幕边缘跟随与碰撞裁剪。击杀者移除、死亡或重新进入部署后停止追踪，不跟随复用的实体 ID。

普通不可救援死亡观察原生尸体，不创建救援状态、不更改原部署倒计时、不删除原生尸体；原竞技玩法立即复活保持原调用分支。服务端将正在观察的原生尸体加入强制订阅。客户端核对身体身份、UUID、发布快照、新鲜程度与位置，缺少可用身体时由实际镜头控制器提供回退。

两个倒地入口均先记录倒地前朝向，再生成身体及应用旧倒地状态。成功救援依次执行 PREPARE_REVIVE 捕获视角 → 停止身体并按真实 SWIMMING 尺寸查找碰撞安全位置 → 清理旧救援/动画/死亡状态 → 广播清理 → 完成趴姿与 REVIVED 交接。归位耗时 0.28 秒，终点为原生插值后的趴姿眼睛位置，朝向恢复倒地前 yaw；缺少可选布娃娃时仍完成趴姿。替换并移除上一轮仅供站立归位的 ReviveBodyPlacement。

趴姿以玩家实体身份管理，支持独立服务器与客户端实例。成功救援前已按住跳跃不会立即站起；新按跳跃或 TACZ 爬行键/控制器解除趴姿，客户端只发送自己的 session，服务端核对玩家身份与当前会话。只在本次同步解除作用域内绕过 TACZ 非枪械/爬行冷却限制，原生下一 tick 不再恢复趴姿；普通 TACZ 操作仍执行原门槛。旧动画清理不能覆盖仍有效的复活趴姿。

镜头包使用递增 session/sequence、实体 ID 和维度拒绝旧会话/错维度数据；服务器 heartbeat 与客户端 lease 处理持续状态。离开房间、房间结束、重新部署、断线、维度切换和停服接入原归一化及清理路径。普通死亡打开部署时，先将最后显示的姿态传给真实 adoptCameraPose，再解除观察镜头，保留原部署移动流程。

| 功能 | 固定反编译来源 | 目标实现 |
| --- | --- | --- |
| 服务器模式与注册 | server/compat/ragdoll/DownedCameraConfig；Gd656conquest 构造器 | 同名新配置类，原模组构造器新增注册 |
| 会话、死亡观察、救援与清理 | server/compat/ragdoll/DownedViewService 的 start/startDeath/prepareRevive/completeRevive/releaseProne/tick/end | 同名新服务；RoomPlayerRespawnService 两个倒地入口、普通死亡、救援及两个退出入口；RoomServerModule、RoomPlayerStateService 清理 |
| 原生尸体观察与订阅 | ClientDownedRagdollState 的 deathSnapshot/Camera 系列；RagdollSubscriptions.mandatory | 恢复 0.0.3 阶段有意延后的真实观察部分和强制订阅 |
| 客户端镜头与输入 | client/room/ClientDownedCamera、DownedCameraMath；client.DownedCameraAccessor | 同名新类；Accessor 使用 official 名称，经当前注解处理器生成生产 refmap |
| 趴姿与 TACZ | common/player/RevivedPronePose；三个 RevivedProne*Mixin | 同名新类与可选 @Pseudo Mixin；DownedRagdollClientHooks 保留有效趴姿 |
| 服务端／客户端消息 | DownedViewPacket、RevivedProneReleasePacket | 同名新包；DownedViewNetwork 追加 S2C/C2S 注册，前序 ID 保持顺序 |
| 部署交接 | ClientDeathFlowState.adoptCameraPose、三个部署/出生过渡入口；ClientRoomLockState | 新增真实方法及必要调用，旧镜头让出位置和旋转所有权 |
| 归位时间线 | client/room/ReviveTransition（ac874ab，最终 f97cc63 参数） | 同名纯数学类；本轮只消费镜头归位，保留后续滤镜参数和读取接口 |

版本判断：本轮完成镜头/复活姿态这一可构建增量，定为 `0.0.5`。对应历史 `86e6bb6`、`461721a`、`ac874ab` 的相关子功能，并采用最终来源的数学与身份/输入修正；未迁入这些提交的全部滤镜/UI 功能，不能称为完整历史版本或最终 `.24` 等价。

阶段边界：世界去色、击杀者高亮、复活白光、曝光/bloom 与渲染资源管理尚未迁入，旧黑幕/受伤闪烁/去色继续走当前实现。只移除成功救援时的旧失明调用，部署和出生的失明逻辑保留。ReviveTransition 的 1.40 秒 pulse 是后续真实滤镜的时间线接口，目前没有 GPU 消费，不声称本轮已显示白光。ClientDownedCamera 中仅移除尚未接入的卡片隐藏消费入口，不建立卡片空类；既有死亡原因提示随新镜头状态显示并在准备救援/结束时隐藏。

构建与验证（2026-10-11）：

- 命令：`./build.ps1 -Offline`；Java 17.0.19、Gradle 8.8、Forge 47.4.18、official 1.20.1，复用工作区离线缓存。
- 结果：`BUILD SUCCESSFUL in 41s`；22 个任务，18 执行、4 up-to-date，包含源码编译、资源处理、当前 refmap 生成及 reobfJar。25 个弃用告警，不影响构建。
- 主 JAR：`build/libs/gd656conquest-0.9.3-bf1-0.0.5.jar`，14,188,505 字节；保留先前各轮产物。
- SHA-256：`de3a1726213ead8891bf06727245cf4b717bbf6c94d331daacd59706f1b85a04`。
- mod/manifest：`0.9.3-bf1-0.0.5`；追加镜头和趴姿解除包，协议 `bf1-0.0.5`，两端应一起更新。
- [本轮验证入口](../../tools/verify-downed-view.ps1)：224 项实际生产镜头构图/运动/0.28 秒归位检查，含 4:3/16:9/21:9 视野、短角插值、碰撞回调、跟随加速度、30/120 FPS 位置与速度一致性；97 项实际数据包往返与非法字段拒绝。
- 24 项当前主 JAR 的生产字节码顺序检查，覆盖倒地前快照、PREPARE→身体清理→旧状态清理→REVIVED、部署姿态捕获以及退房/断线/维度清理。这是静态调用审计，不冒充实际多人游戏行为。
- 使用当前主 JAR、生成 refmap、实际 Forge SRG 和原始 TACZ 1.1.8 JAR 应用真实 Mixin：CLIENT 5 个目标（Camera/MouseHandler/两种 Crawl/CrawlKey）、SERVER 1 个目标；TACZ 缺席场景通过。Camera 的三个 accessor 均成功生成。
- 当前 0.0.5 上回归上一轮：121 项身体/同步/播放/替换检查和 CLIENT 13 / SERVER 9 个原生 Mixin 目标、可选布娃娃缺席；155/35/15 项死亡原因规则/包/接口检查及两个 Forge 钩子；5 个救援选择、61 项死亡协调器和 4 个真实 EventBus 场景，全部通过。
- Gradle test 仍为 NO-SOURCE；未启动 Minecraft、专用服务器或多人联机。本轮没有运行旧世界夹具中的完整 DownedViewTest，也不声称已动态验证真实实体中的趴姿解除/碰撞搜寻/控制器全流程。

下一轮可继续 P2 的死亡/复活世界滤镜与渲染资源链，之后接入击杀者卡片和部署输入。本轮改动未自动提交。

## 0.0.6：死亡/复活世界滤镜与渲染资源链

本轮在已提交的 `1c912ab`（0.0.5）上迁入六个独立客户端类/Mixin 和十项专用 shader 资源。来源固定为最终反编译提交 `955b76d`，不改来源仓库；正常构建直接编译真实源码。原业务 Java 仅修改 ClientDeathFlowState 与 ClientDeathCauseState：禁用被替代的黑幕与旧全局去色槽、死亡期间隐藏受伤闪烁、提供受死亡阶段限制的击杀者 ID、让原因横幅共用实际滤镜状态。服务器、身体、救援、镜头、网络与 HUD 其他业务的既有 class 字节经成品对照保持一致。

死亡效果使用独立 PostChain，强度随 0.32 秒受击和 1.10 秒镜头展开进入，最终去色 96%，对比度 1.03。普通退出连续恢复 0.20 秒；成功救援使用已有镜头拥有的同一时间线，0.08 秒恢复颜色。世界渲染和既有后处理完成后，先应用死亡滤镜，再合成轮廓，再应用复活曝光/bloom，最后交还主 framebuffer，由原流程继续绘制 HUD/GUI；原因提示保留原 HUD 后置渲染。

击杀者轮廓仅在受害者本地呈红色，不修改实体 glowing 标记、队伍、计分或服务器状态；镜头会话沿用原玩家实体身份与追踪有效性，死亡/离开/进入复活流程后停止追踪。无镜头会话时，只有原死亡观察阶段提供 fallback ID，玩家本人、移除、死亡或错世界实体均排除。卡片 alpha 读取接口使用同一去色时间线，但本轮尚未启用卡片 UI。

复活效果保持最终参数：1.40 秒、峰值保持 0.10 秒、曝光 +3.1 EV、bloom 增益 1.40；0.28 秒镜头归位后继续视觉尾段，期间不再锁镜头或手部。五段图依次提取亮部、横向半尺寸模糊、纵向半尺寸模糊、曝光/bloom 合成、零曝光/零增益复制回主 framebuffer。半尺寸输出按实际尺寸设置投影，奇偶窗口尺寸均验证；最终复制使用 one/zero 覆盖混合，保留透明天空像素的 RGB 和 alpha，不恢复旧 native blit 黑天空问题。

资源在死亡阶段预热，主 framebuffer 身份或尺寸变化时重建/缩放；缺少 uniform 或 shader 加载失败时清理、记录错误并等待资源重载重试。世界更换、玩家为空、实际资源重载和 GameRenderer.close 均释放两条私有 chain。来源中的 GameRenderer `m_6213_` 并不存在于当前实际类，旧复合 Inject 实际只命中 close；本轮修正为 `reloadShaders(ResourceProvider)` 的 render-thread 入口（生成 refmap → `m_172767_`）及独立 close 注入，并对两个入口分别断言，不保留无效的重载钩子。

| 功能 | 固定反编译来源 | 原始项目实现 |
| --- | --- | --- |
| 连续去色/救援恢复 | client/room/DeathFilterTransition，beginReviveRecovery/recoverRevive/advance | 同名纯数学新类，使用已迁入 DownedCameraMath/ReviveTransition |
| 世界层处理/本地轮廓 | client/room/ClientDeathFilter，beginFrame/isKiller/compositeOutlines/finishWorld | 同名新类；使用上一轮真实镜头、玩家身份和生命状态 |
| 复活五段效果/尺寸/投影 | client/room/ClientReviveEffect，prepare/render/releaseResources | 同名新类，消费上一轮实际 pulse 时间线与最终参数 |
| 世界/HUD 边界、释放 | mixin/client/DeathWorldRenderMixin | official 名称 + 当前生成 refmap；资源重载修正为当前实际 reloadShaders |
| 本地红色成员与颜色 | mixin/client/DeathKillerOutlineMixin | 同名新 Mixin；保留 vanilla 其他实体发光/颜色结果 |
| Uniform/pass 列表 | mixin/client/DeathPostChainAccessor | official passes accessor + 当前生成 refmap |
| 专用渲染图与 GLSL | assets/gd656conquest/shaders/post/{death_world,revive_world}.json；program/{death_world,revive_world,revive_bright,revive_blur}.{json,fsh} | 十项资源原字节迁入 src/main/resources，既有资源保留 |
| 替代旧视觉入口 | ClientDeathFlowState.currentDarknessAlpha/currentHurtFlashAlpha/applyDesaturation/deathFilterKillerId；ClientDeathCauseState.onRender | 两个原类必要方法接入；既有生命/救援/部署时序不变 |

版本判断：完成独立死亡/复活世界渲染增量，定为 `0.0.6`。对应 `b650004`、`ac874ab`、`f97cc63` 的相关子功能及最终参数；没有因此迁入卡片/聊天、边界、补给和压制，不能称为任一完整历史提交或最终 `.24` 等价。[一次性导入工具](../../tools/migrate_world_filters.py) 保留固定来源、official 转换和实际重载修正，拒绝覆盖目标 Java/资源。

构建与验证（2026-10-11）：

- 命令：`./build.ps1 -Offline`；Java 17.0.19、Gradle 8.8、Forge 47.4.18、official 1.20.1，复用工作区离线缓存。修正重载入口后重新构建最终主 JAR。
- 最终结果：`BUILD SUCCESSFUL in 43s`；22 个任务，16 执行、6 up-to-date，包含源码编译、refmap 和 reobfJar；27 个弃用提示，原未命中重载方法的描述符告警已消除。
- 主 JAR：`build/libs/gd656conquest-0.9.3-bf1-0.0.6.jar`，14,204,686 字节，保留前轮产物。
- SHA-256：`120cfbdfa35430a72d601b89c0f65ea98b70e16f8d50c42bb007abfc98a8ed7c`。
- mod/manifest 为 `0.9.3-bf1-0.0.6`；本轮没有消息注册/格式变化，协议保持 `bf1-0.0.5`，不将纯客户端显示增量作为网络不兼容升级。
- [验证入口](../../tools/verify-world-filters.ps1)：当前主 JAR 与本工程 Forge SRG/依赖导出，不使用旧成品路径、世界夹具或复制生产方法；1908 项死亡过渡、2003 项复活时间线/低帧率/中断连续性检查通过。
- 真实 Mixin 引擎在当前 Forge SRG 上应用 CLIENT GameRenderer/LevelRenderer/PostChain 三个目标及生成 refmap，检查世界→既有后处理→死亡处理→GUI 深度清理顺序、轮廓延后、accessor、reloadShaders 和 close 两个资源释放入口；SERVER 三个目标字节保持一致。
- NVIDIA GeForce RTX 5060 Laptop GPU 的隐藏 OpenGL 上下文直接读取本轮 JAR：死亡 GLSL 编译/链接及 331 项像素检查；复活五段图 109075 项检查，包含 bloom 横纵扩散、暗部轮廓、透明/半透明天空 RGBA、旧 native blit 负面对照和奇偶尺寸重建，全部通过。像素数相关断言不等于 109075 个独立玩法场景。
- 2484 项当前与 0.0.5 成品范围检查：保留前轮条目，除两个有意修改的客户端原类外，全部原生产 class 字节一致；私有 chain 不使用全局 shader 槽，不改变实体发光/游戏模式。测试和 GPU 临时文件只位于 tools/build 验证目录，不打包。
- 当前 0.0.6 上镜头回归：224/97/24 项数学/包/调用顺序、CLIENT 5 / SERVER 1 个实际目标及 TACZ 缺席通过；死亡原因回归：155/35/15 项及两个真实 Forge 钩子通过。
- Gradle test 仍为 NO-SOURCE；没有启动 Minecraft、专用服务器或联机。实际 GPU shader/图已验证，游戏内资源重载执行、第三方 shader/瞄具、GUI 缩放与多人完整流程仍需实机验收。

下一轮可接入 P3 的击杀者元数据/卡片及部署输入消费链。本轮改动未自动提交。


## 0.0.7：击杀者快照、信息卡、部署输入与聊天

本轮在目标 HEAD `1c912ab` 上继续工作，完整保留上一轮尚未提交的 0.0.6；以保留的 0.0.6 主 JAR 为本轮对照。来源仍固定为最终反编译提交 `955b76d8adc264703eb49141bdb99b06e84cba46`，来源仓库未修改。本轮新增 22 个对应来源类/Mixin、独立追加注册类 KillerCardNetwork 及 headshot.png，共 23 个 Java 新类；原业务 Java 仅改 NetworkManager、RoomDeathEventDispatcher、RoomDeathCauseService、RoomPlayerRespawnService、ClientDeathCauseState、ClientDownedState、ClientDownedCamera、DownedRagdollClientHooks 八个类。

击杀信息在火焰归属作用域内、完整 Forge 死亡派发前冻结。近战绑定同一次攻击与复制的物品，弹射物绑定发射实体/所有者与发射时装备，TACZ 使用实际弹丸的 GunId/GunDisplayId；无法确认时显示“无法确认”，不猜测后来手持武器。生命/等级/名称属于该次快照；爆头只接受同受害者、同弹丸、同 damage tick 的报告。元数据观察器不取消事件、不修改伤害或计分；可选反射/API 扩展失败隔离。对决账本只在已接受的敌方玩家致命事件计一次，按房间与 activeMatchInstanceId 隔离，下一场清零。

倒地期间底部全宽背景保留头像、击杀者、武器、操作、救援五列；异步原生皮肤与默认皮肤回退，长名字截断/武器换行，列宽与 GUI 尺寸适配。alpha 共用上一轮死亡滤镜，HUD 在世界处理之后绘制。非玩家来源的三列击杀信息留空；已确认不可救援死亡按来源保留空击杀列和“不可被救援”。旧击杀动作栏、悬浮死亡倒计时和被救援提示改为清理对应 source，由卡片承担；救援者的原操作提示和救援进度继续使用现有机制。

不可救援死亡（含竞技场分支）替换原自动打开部署的机制：立即启动尸体观察和服务器 deadline，以 Long.MAX_VALUE 标记保留原死亡流程所有权。deadline 归零只更新提示，继续等待玩家新按键；也可提前按键打开原部署界面，只携带剩余等待。复用真实 enterDeployHoldingState/openDeployView，打开成功后移除等待记录、禁用自动部署与缺失选择回退，打开失败保留 deadline 并允许显式重试。主动重新部署的双倍等待在建立 deadline 时消费一次；不会再附加旧 50/120 tick 的自动观察延迟。这里只改变已确认死亡入口，其他原部署/出生流程保留。

倒地快照的部署剩余值直接调用原 resolveDeployDelayTicksFromDowned，死亡剩余值独立。开启 enableRespawnCountdownWhileDowned 时，服务端零值才允许新按空格立即走原 surrender→部署；之前继续原长按放弃。一直按住跨越归零、聊天中按键、救援锁解除后仍按住均不能自动提交；服务端 surrender 分支仍拒绝正在被救援者。普通死亡请求带 deathId，服务端核对成员、倒地/部署/收尾状态、维度及等待标记，重复/过期请求不能重开。

聊天适配按来源在整个房间内启用，退出房间恢复原布局；移动原生 ChatComponent、EditBox 和建议锚点，而不复制聊天历史/签名消息/滚动机制。渲染、悬停、链接与排队点击共用布局坐标；房间状态、宽度或聊天缩放改变时从原组件重排。四个客户端 Mixin 使用 official 名称与本工程当前生成的 refmap，不保留旧成品的 remap=false SRG 注入。

| 功能 | 固定反编译源码对应位置 | 原始项目实现 |
| --- | --- | --- |
| 致命前快照/装备/生命/发布 | [KillerIntelService](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/KillerIntelService.java)：withFatalSnapshot 第 86 行、snapshot 第 93 行、capture 第 113 行、publish 第 127 行 | 同名新类；RoomDeathEventDispatcher 嵌套真实快照，RoomDeathCauseService 接入 capture/publish/forget/clear/tick |
| 武器与精确爆头 | server/room/entry/KillWeaponResolver.resolve/extract/fromStack；FatalHitMetadata.onHitEvent/remember/headshot | 两个新观察/解析类，不改 TACZ/Killicon 源码；直接读取最终字段 |
| 对决与只读载荷 | server/room/entry/MatchDuelStats.record；common/player/KillerCardInfo | 同名纯 Java 类；场次与 deathId 去重 |
| 普通死亡手动门槛 | [ManualDeathDeployService](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/ManualDeathDeployService.java)：begin 第 20 行、valid 第 30 行、request 第 52 行 | 同名新类，保留原 countdown map 所有权与现有延迟/双倍处罚 |
| 两个真实移植方法 | [RoomPlayerRespawnService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java)：onPlayerDeath 第 151 行、openManualDeathDeploy 第 172 行 | 原源码中实际实现，直接调用现有服务；不用编译占位或 ASM transplant |
| 倒地快照/输入 | DownedDeployService.publish；DownedDeployModel；ClientDownedDeployState；ClientDownedState.requestReadyDeployment 第 311 行；downedDeployDelay 第 2283 行 | 三个新类 + 原倒地 tick/输入/发送的必要调用；原救援权威检查保留 |
| 五列卡片/生命周期 | ClientKillerCardState，KillerCardModel/Layout；ClientManualDeathDeployState，ManualDeathDeployModel | 同名新类，原 render 委托、PREPARE/END/清理隐藏；使用已迁入 cardOpacity |
| 原生聊天适配 | ClientRoomChatState、RoomChatLayout；KillerCardChatAccessor、RoomChatMixin/ScreenMixin/SuggestionsMixin | 六个新类/Mixin；四项 client 注册与当前 refmap |
| 两个 S2C 快照 | KillerCardPacket、DownedDeployPacket；来源 DownedRagdollNetwork.register | 同名新包，独立 KillerCardNetwork 在所有已存在 ID 之后追加注册 |
| 专用爆头图标 | assets/gd656conquest/textures/gui/killer_card/headshot.png | 原字节加入 src/main/resources，旧 assets/data 保留 |

版本判断：完成独立 P3 剩余元数据/UI/输入消费链，定为 `0.0.7`。对应来源 `9d189a1`、`6cb882b` 的相关功能及最终修正；边界、载具处罚、补给/治疗、压制尚未迁入，不能称为最终 `.24` 完整等价。[一次性导入工具](../../tools/migrate_killer_card.py) 固定来源、转换 official 并拒绝覆盖新增文件。八个原类的调用/机制改动写在真实源码中。

构建与验证（2026-10-11）：

- 命令：`./build.ps1 -Offline`；Java 17.0.19、Gradle 8.8、Forge 47.4.18、official 1.20.1，复用工作区离线缓存。
- 结果：`BUILD SUCCESSFUL in 42s`；22 个任务，18 执行、4 up-to-date；源码、资源、当前 refmap 和 reobfJar 均完成，32 个弃用提示，无 Mixin 未命中/描述符告警。
- 主 JAR：`build/libs/gd656conquest-0.9.3-bf1-0.0.7.jar`，14,277,941 字节，保留前轮产物。
- SHA-256：`04ef63e7d6a4603caa6c15985f9406509bd1cea6c674b946581b5ac4d6afd7e5`。
- mod/manifest：`0.9.3-bf1-0.0.7`；追加两个明确 PLAY_TO_CLIENT 包，前序 ID 顺序保留；协议 `bf1-0.0.7`，客户端与服务端须一起更新。默认六项 bypass 均为 false。
- [本轮验证入口](../../tools/verify-killer-card.ps1)：3433 项实际卡片原因/生命周期/对决/尺寸与聊天坐标/点按模型检查、18 项普通死亡等待/重试/聊天/输入模型、91 项真实数据包往返及非法字段拒绝，全部通过。多数布局断言来自尺寸组合，不是 3433 个独立游戏场景。
- 9 项实际元数据扩展失败隔离及真实 Forge EventBus 注册/无关事件/反射失败检查；不提供伪造的 Minecraft 实体/世界接口。原始 TACZ 1.1.8 的 EntityHurtByGunEvent 实际 getHurtEntity/getBullet/isHeadShot API 已核对。
- 实际 Mixin 引擎读取当前主 JAR/refmap 和 Forge SRG：CLIENT ChatComponent/ChatScreen/CommandSuggestions 三个目标成功，四个 Mixin 的渲染/宽度/行数/鼠标/输入/建议入口命中；SERVER 三个目标字节保持原样。
- 2843 项当前/0.0.6 成品范围和静态调用审计通过：八个有意改动的原类之外，原生产 class 字节保留；其既有内嵌类只变动 source debug 行，剥离调试信息的实际指令/字段/注解一致。既有 assets/data 原字节保留。快照先于监听器、原因包先于卡片、手动门槛/双倍处罚/部署开启、倒地快照/输入与退出清理均有静态生产证据；不冒充完整游戏动态验收。
- 前序镜头回归 224/97/24 项、CLIENT 5 / SERVER 1 个实际目标与 TACZ 缺席通过；死亡原因 155/35/15 项与两个实际 Forge 钩子通过；救援选择 5 场景、死亡协调器 61 项及真实 EventBus 4 场景通过。原死亡原因调用断言已更新为本轮有意替换的 ManualDeathDeployService.begin。
- 当前产物世界滤镜回归通过：1908/2003 项时间线，CLIENT 3 目标与 SERVER 隔离；实际 GPU 331/109075 项像素/五段图/透明天空/缩放检查；2477 项成品范围。范围工具显式登记本轮业务接入，由独立本轮工具核对；原视觉算法和资源字节保持一致。
- Gradle test 为 NO-SOURCE；未启动 Minecraft、专用服务器或多人联机。真正的玩家互杀/装备清理前快照、爆头事件时序、GUI 缩放与皮肤/聊天操作、手动部署全流程仍待实机验收。

下一轮可迁入 P4 的统一出界、载具处罚和配置复制链。本轮与前轮改动均留在工作区，未自动提交。

## 0.0.8：统一出界、原生载具摧毁与边界警告

从用户提交的 `42004f9`（0.0.6/0.0.7）继续迁移，固定来源仍为干净的 `955b76d8adc264703eb49141bdb99b06e84cba46`。
本轮按实际完成的 P4 出界链路定为 **0.0.8**；对应 `1e2a975` 的出界重构与 `07f0824` 的载具边界修正，以及最终源码的乘员/归因处理，不表示两个历史提交的全部功能均已迁入。

| 功能 | 反编译源码对应位置 | 本轮实现/适配 |
| --- | --- | --- |
| 单一截止 tick、状态切换只收紧 | `server/room/runtime/BoundaryCountdown.java`、`UnifiedBoundaryService.process` | 新类实现；原 `RoomRuleRuntimeService` 每 5 tick 调用，复用原状态表的清理周期；关闭旧飞行独立超时入口 |
| 飞行缓冲及统一致命几何 | `FlightBoundaryGeometry.outside`、`UnifiedBoundaryService.isOutside/aboveHeight` | 欧氏距离、凹多边形/多区域，默认 100 格；方块与高度报告 TTL 20 tick 继续接入；`RoomDeathBoundaryQuery` 转发统一几何，现有致命前快照/真实原因/禁止倒地机制继续工作 |
| 原生爆炸乘员处理 | `server/compat/vehicle/BoundaryVehicleDestruction.java`、两个 `BoundaryDestructionMixin` | YWZJ 零生命值后触发原生零伤害 hurt；Superb Warfare 等原生 tick/destroy；爆炸前递归快照实际乘员并全部标记，爆炸后只补处理仍存活且未部署者；使用真实 `RoomDeathEventDispatcher` 保护，服务端/房间重建清理请求 |
| 配置加载、保存和复制 | `RoomCommonConfig`、`BoundaryConfigNumbers`、`RoomDefinitionRepository`、分组编辑/模板/`RoomCommands` | 添加两个 nullable 扩展字段；缺失飞行时限继承旧载具时限、缺失缓冲 100，显式 0 不当作缺失；保留旧 49 参数构造器；四个实际复制点带值；描述、数字编辑和开局校验接入 |
| 本地警告及世界滤镜 | `ClientBoundaryState`、`BoundaryPresentation`、`BoundaryHudEvents`、`BoundaryWorldRenderMixin`、TACZ `BoundaryCrosshairMixin` | 剩余 tick/乘坐类型复用旧包槽位；暖灰滤镜 0.10 秒过渡，中文标题/倒计时、1.905333334 秒语音冷却；仅步兵准星取消，其他 HUD 与乘坐瞄具保留 |
| 两项兵种中文显示 | `server/loadout/domain/PlayerClassType.java` | ASSAULT 显示医护兵、ENGINEER 显示突击兵；内部 key/数字、输入别名及机制保持最终来源 |

新增业务集中在 **12 个类/Mixin**；已有 **16 个 Java 文件**仅做出界机制替换、共享配置传递、死亡/资源生命周期与协议语义接入。新增 3 个着色器资源和 1 个原始 OGG；声音表仅追加 leaving_area，旧条目保持一致。未新增包注册 ID；由于旧边界动作改为权威剩余 tick 更新，严格协议改为 **`bf1-0.0.8`**，两端一起更新。

来源适配：边界资源释放也改为实际 `GameRenderer.reloadShaders(ResourceProvider)` 和 `close` 两个独立 `require = 1` 接入，避免来源 `m_6213_` 不存在但被 close 掩盖；映射由当前 APT/refmap 生成。P6 压制未迁入，`SuppressionOpacity` 本轮明确设为 0，保留来源边界 GLSL，不添加假压制服务。配置保留来源行为：显式 0 能正确加载并关闭对应运行计时；房间开局完整性校验仍要求出界时限为正，不把 0 解释成自动继承。

构建：`./build.ps1 -Offline`，Java 17、ForgeGradle 6、本工程离线缓存，最终 **BUILD SUCCESSFUL**（1m 9s）。

- 主产物：[gd656conquest-0.9.3-bf1-0.0.8.jar](../../build/libs/gd656conquest-0.9.3-bf1-0.0.8.jar)
- 大小：**14,320,495 字节**。
- SHA-256：`c48b2b2c91e56379fe07352af6a369e18914c89cf5aacf7d5f2e378b9f9e5adb`。

验证：`tools/verify-boundary.ps1` 读取本轮主 JAR、当前 refmap 和真实依赖；与保留的 0.0.7 主 JAR 比较。计时/几何/真实 Gson、编辑、校验、仓库重载、模板/分组复制、包与语音/淡入淡出共 **222316** 项检查（含逐 tick 扫描）；成品原生请求/乘员时序 **13** 项使用服务端/载具夹具；真实 Forge HUD **360** 项；实际 GameRenderer/TACZ Mixin、资源重载/close、SERVER 隔离、TACZ 及两类载具缺席检查通过。边界实际 GPU **190** 项；死亡/复活 GPU **331/109075** 项；前后成品方法/资源/四处构造参数/统一路径审计 **13888** 项通过。镜头回归 **224/97/24** 项、真实 CLIENT 5 / SERVER 1 目标及 TACZ 缺席通过；死亡原因 **155/35/15** 项和两个真实 Forge 目标通过；卡片/部署模型 **3433/18**、包 **91**、真实元数据扩展/EventBus **9**，死亡/复活时间线 **1908/2003** 项通过。

成品审计：ZIP 完整、包内版本正确、Java 17 major 61、六项普通 bypass 标志 false；既有 refmap 与声音条目保留，新资源逐字节匹配固定来源；不含测试类或第三方模组类；反编译来源工作区未改变。未启动 Minecraft/多人联机，不把上述检查等同于实机完整玩法验收。

剩余：P4 六个共享扩展字段中的两个已全链路接入；敌方补给开关、压制配置、治疗伤害冷却和被动回血量留到 P5/P6。后续补给、压制及实机验收继续迁移；本轮不自动提交。


## 0.0.9：两轮部署发弹与实际储备上限

从已提交的 `1fe56bb`（0.0.8）继续，来源固定为干净的
`955b76d8adc264703eb49141bdb99b06e84cba46`。本轮完成可独立运行的 P5-A，定为 **0.0.9**。
`9556c5a` 引入补给目标与储备事务，`770bbe4` 新增 SupportAmmoGrant 并接入两轮发弹、部署结束记录与同弹池上限；
本轮迁入这些子功能，不代表两次历史提交或最终 `.24` 的医疗、箱包、压制和声音已全部等价。

| 功能 | 反编译源码对应位置 | 本轮实现/适配 |
| --- | --- | --- |
| 两轮初始发弹 | [SupportAmmoGrant](../../../gd656conquest/org/mods/gd656conquest/server/support/SupportAmmoGrant.java)，distribute 第 23 行、grant 第 40 行 | 同名新类转换 official；先按枪包组分格，再填同一批格子的实际容量；返回实际写入量，不向其他格子溢出 |
| 部署持久上限/重新部署 | 同上，cap/readCap 第 88/93 行、finishLoadout 第 99 行；来源 RoomPlayerLoadoutService.applyLoadout 第 213 行 | 实际数量/弹池写入枪械 NBT；后续配备覆盖弹药格子时按最终背包重新分配上限；旧枪缺记录回退配置，显式 0 保留，重新部署覆盖旧记录 |
| 同池合并与储备事务 | [SupportAmmo](../../../gd656conquest/org/mods/gd656conquest/server/support/SupportAmmo.java)，targets 第 15 行、Target.reserve/add 第 85/109 行 | 同名新类迁入目标、同池上限求和、TACZ 实体弹药/SBW 虚拟储备和配备/投掷物目标；只补储备，保留枪械已装填弹药及其他 NBT |
| 原发弹/部署链路 | 来源 RoomLoadoutGrantService.grantConfiguredWeaponAmmoByExtraData 第 150 行及 RoomPlayerLoadoutService.applyLoadout | 原源码改为委托 grant；配备/可选房间物品发放完成后调用 finishLoadout，再同步背包 |
| 分阶段接入原箱包入口 | 来源 SupportAmmo.targets/Target.add，以及后续 SupportRuntime 的储备调用；原 Core/RoomSupportBox 接口 | 新 SupportAmmoRefill 复用真实房间、阵营、运行、倒地/部署状态；箱补所有允许目标、包优先当前手持枪且保留旧 maxRounds；原类两处入口委托，移除整套配装补给和旧包超过上限的追加补弹 |
| 小容量空格补弹 | 原 RoomLoadoutGrantService.addTaczAmmoToPreferredSlots | 必要机制修正：空格数量同时取枪包组大小和真实 ItemStack 堆叠上限，防止组大小 64、实际上限 16 时创建超量堆叠 |

新增三个业务类；只修改两个已有业务 Java 文件。SupportAmmo 不导入依赖后续 SupportContext/SupportRules/SupportRuntime 的 `bag(ServerPlayer)`；
通过独立、实际可运行的 SupportAmmoRefill 接入现有入口，没有添加占位服务。原箱的权限检查继续由原 RoomSupportBoxService 提供。
[一次性导入工具](../../tools/migrate_ammo_reserves.py) 固定来源、转换 official、拒绝覆盖目标 Java；上述原类适配直接在源码中实施。

构建（2026-10-11）：`./build.ps1 -Offline`；Java 17.0.19、Gradle 8.8、ForgeGradle 6.0.54、MC 1.20.1 / Forge 47.4.18。
小容量补弹回归暴露旧入口问题后修正并重建，最终 **BUILD SUCCESSFUL in 57s**，22 个任务（16 执行、6 up-to-date），含编译、refmap 和 reobfJar。

- 主产物：[gd656conquest-0.9.3-bf1-0.0.9.jar](../../build/libs/gd656conquest-0.9.3-bf1-0.0.9.jar)，**14,336,957 字节**。
- SHA-256：`9b0482d772f185aa2690b9d763e692411edaa5b2e6bf0e5de5bed4661cc07f0c`。
- mods.toml/manifest：`0.9.3-bf1-0.0.9`；数据包、注册、协议保持 `bf1-0.0.8`，六项普通 bypass 为 false。

[验证入口](../../tools/verify-ammo-reserves.ps1) 读取本轮主 JAR、真实 Forge SRG 和本工程依赖导出：

- **473** 项实际 distribute/NBT 及成品调用检查：有限/扩容/小容量格子、重复/无效位置、实际写入失败、巨量请求、NBT 序列化、0/旧记录回退/重新部署覆盖。
- **33** 项实际成品 grant/finishLoadout/targets/refill 与原 addTaczAmmoToPreferredSlots 方法的夹具检查：执行产物方法字节码，适配背包、房间及枪械元数据，使用真实 Minecraft NBT；覆盖最终格子覆盖、同弹池合并、实际储备补充、配置编辑不提高旧上限、无效玩家/房间/阵营、配备补充、容量/持枪/禁用目标、0 上限及 SBW 储备/弹匣分离。
- **4131** 项 0.0.8 → 0.0.9 成品范围和静态调用检查：两个原类仅预期方法变动，部署方法除新增一次 finishLoadout 外原指令保持一致；相关内嵌类除 debug/表项顺序外行为保留；其他生产 class、资源、Mixin/refmap、网络和协议字节一致；不含测试/第三方类，Java 17 major 61。

Gradle test 为 NO-SOURCE。夹具隔离引擎/原生枪械元数据，不等于实际 TACZ/SBW 枪械、Minecraft 启动或多人验收。
来源仓库仍干净；本轮未自动提交。

剩余 P5：新箱包光环/频率、包两弹匣规则、所有者/敌方权限、Q/整组/容器丢弃限制、死亡包寿命、医疗与只发给接受者的成功音效及客户端通道保护。
旧包 maxRounds 与公共补给声音暂保留；四个共享字段和压制/自然回血按 P5/P6 继续迁入。


## 0.0.10：箱包运行时、医疗与成功私人音效

从用户提交的 `0b7383f`（0.0.9）继续，来源固定为干净的
`955b76d8adc264703eb49141bdb99b06e84cba46`。本轮完成 P5-B 独立箱包运行时，定为 **0.0.10**；
对应 `9556c5a` 的箱包/规则、`770bbe4` 的成功弹药音效以及 `955b76d` 的医疗音效最终修正。
不将压制或全局/房间自然回血参数视为已迁入，不表示整个历史提交或最终 `.24` 等价。

| 功能 | 固定反编译源码对应位置 | 本轮接入 |
| --- | --- | --- |
| 归属/跨房间/阵营/敌方开关 | [SupportContext](../../../gd656conquest/org/mods/gd656conquest/server/support/SupportContext.java)，of/available/stamp/allowed，stamp 第 27 行、allowed 第 34 行 | 同名新类与真实 Manager 状态；普通补给必须有房间和所有者，常驻箱公共、仍限当前房间；死亡奖励继续使用原击杀者拾取权 |
| 投包/拾取/寿命 | [SupportRuntime](../../../gd656conquest/org/mods/gd656conquest/server/support/SupportRuntime.java)，useBag 第 151 行、pickup 第 191 行、toss 第 230 行、markDeathReward 第 249 行 | 两种原物品的 use/interact 委托；两个事件入口提前接管并停止旧路径；共享投放/拾取冷却，创造自己投包同样等待 10 tick；同种普通地面包最多三个，独立 UUID 防合并、600 tick 寿命 |
| Q 和背包/容器丢弃限制 | SupportDropGuard、mixin/server/SupportDropMixin | 新类与 official/refmap Mixin；两处原生 drop 调用重定向，单个 Q 使用 ThreadLocal finally 作用域；阻止 Ctrl+Q 和容器 THROW/外部点击丢包，退款失败进入真实待退还队列 |
| 两包初始库存/独立恢复 | SupportRuntime.prepareLoadoutStack 第 246 行、recharge 第 342 行 | 原 placeLoadoutItem 接入初始 2 包；原 RoomItemReplenishService 改为仅补包，每 120 tick 恢复一个至 2；其他普通消耗品交给弹药箱 |
| 弹药箱/包储备事务 | SupportRuntime.boxes 第 269 行、debitAmmo；SupportAmmo.bag 第 51 行；SupportRules.ammoBurst/Progress | 原两种 aura 入口委托，三个格内取最近合法箱；每秒最多一把普通枪轮转；包一次只补一把且优先手持，补量 max(两弹匣,4)；配备/投掷物按受赠者目标累计进度，离开暂停、满额清零、失败保留；沿用 0.0.9 实际部署上限和原禁补/英雄/枪械大师规则 |
| 直接 HP 医疗/容量 | SupportRuntime.heal 第 137 行、tick/boxes/pickup | 包立即及每秒 2.5 HP、真实受伤终止；箱每 10 tick 1 HP，按实际 HP 扣普通箱 100 池；普通弹药箱 20 次成功事务，常驻无限；原箱初始化携带房间/归属，关闭旧交互、药水与医疗提示路径 |
| 成功私人声音与客户端通道 | SupportRuntime.ammoSupplied 第 328 行、medicalSupplied；[ClientAmmoSupplySound](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientAmmoSupplySound.java) filter 第 27 行；AmmoSupplySoundAccessor | 成功后通过真实 ServerPlayer.playNotifySound 向接受者发送一次原声音；服务端两音效分别合并同 tick；客户端分别读取真实通道，活动/待分配时合并、不打断/排队，通道结束后允许下次播放；退出/音频重载清理 |
| 医疗冷却和配置复制 | RoomCommonConfig；描述注册与数字编辑；仓库/分组/模板/RoomCommands | 增加 nullable allowEnemySupportResupply（缺失 true）与 medicalBoxDamageCooldownSeconds（缺失/无效 3，0–60 秒），保留旧 49/51 参数构造器；四处实际复制构造传入全部已迁入四字段 |
| 自然回血接入 | 来源旧构建 support.passiveRegen/regen.privateSound 接入；实际 PassiveRegenService | 有效医疗箱/包治疗时暂停，reset 同步清理补给状态；起始音效改为私人发送。本轮另检查 heal 前后实际 HP，外部取消回血时不播放成功音效；原回血量/等待/间隔保留 |

新逻辑分布在 **七个新类/Mixin**（四个 server/support 类、客户端声音类及两个 Mixin），**16 个已有 Java 文件**只接入机制替换、配置/生命周期和成功声音。
正常源码构建，无 ASM 成品补丁。[导入工具](../../tools/migrate_support.py) 固定来源，转换 official，拒绝覆盖新类；
[调用接入工具](../../tools/migrate_support_hooks.py) 绑定提交的 0.0.9，拒绝重复激活。
客户端声音 accessor 改用 official `instanceToChannel`，Q/容器入口、shadow/drop 描述符由 APT 生成 refmap，保留全部既有映射。

分阶段边界：SupportRuntime/SupportRules 暂不导入 Suppression 状态、网络、近失弹和治疗阻断方法；
医疗箱目前按实际受伤冷却暂停，自然回血按有效箱包治疗暂停。P6 接入真实压制时再组合公共阻断，未添加假服务或占位返回。
现有 `SupportAmmoRefill` 兼容 API 保留，旧 maxRounds 调用仍受部署上限且成功声音改为私人；正常包/箱已经使用新运行时。

构建与验证（2026-10-11）：

- 命令：`./build.ps1 -Offline`；Java 17.0.19、Gradle 8.8、ForgeGradle 6.0.54、MC 1.20.1 / Forge 47.4.18、official 映射与离线缓存。
- 最终 **BUILD SUCCESSFUL in 50s**；22 个任务，16 执行、6 up-to-date；源码、APT/refmap 与 reobfJar 完成。Gradle test 为 NO-SOURCE。
- 主产物：[gd656conquest-0.9.3-bf1-0.0.10.jar](../../build/libs/gd656conquest-0.9.3-bf1-0.0.10.jar)，**14,357,730 字节**。
- SHA-256：`2cd602ed0cdeab30ad33021860fae39aaa444a3ab9717206111e0c853c63b153`。
- mods.toml/manifest：`0.9.3-bf1-0.0.10`；消息注册/格式及协议保持 `bf1-0.0.8`，六项普通 bypass 为 false。
- [验证入口](../../tools/verify-support.ps1)：**100** 项实际 burst/进度/伤害时限与 Gson/编辑/仓库重载/模板/分组；**45** 项成品方法字节码的权限/拾取/治疗/光环/寿命/库存/Q 与自然回血成功音效夹具；**69** 项真实 Forge PlaySoundEvent/播放通道与实际 Minecraft 私人声音 API 检查。
- 实际 Mixin 引擎在当前 Forge SRG 上应用 ServerGamePacketListenerImpl 与 SoundEngine：CLIENT/SERVER 各 **3** 项，`require=2` 的 Q 重定向及容器 Inject 成功，SoundEngine accessor 命中，服务器不应用客户端 accessor。
- **4177** 项 0.0.9 → 0.0.10 成品范围/调用/四处扩展构造检查：预期方法以外保留原指令、字段和注解；只规范化调试信息、外部 inner 元数据及 javac 重编号的 lambda，lambda 实际指令继续比较；其他生产类、网络/协议和既有资源原字节保留。refmap 全部旧条目保持，仅追加两个本轮目标。
- 本轮 JAR 上回归 0.0.9：发弹/NBT/接入 **473** 项，实际储备事务夹具 **33** 项通过。ZIP 完整、Java 17 major 61、版本正确；无测试/运输/背包适配器和第三方类进入业务产物。来源工作区保持干净。

夹具保留实际产物规则方法，但隔离游戏引擎、背包/房间、枪械元数据、音效发送和奖励运输；不等于实际实体/原生枪械/多人游戏验收。
未启动 Minecraft 或专用服务器/联机；实机需检查投包、死亡奖励权、存档加载、GUI 丢弃、长音频与第三方声音/治疗事件交互。
下一轮进入 P6 的真实火力压制、世界暗角与全局/房间自然回血参数和剩余两配置复制链。本轮未自动提交。
