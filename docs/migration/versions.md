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
