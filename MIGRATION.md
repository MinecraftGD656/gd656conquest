# gd656conquest 原始源码迁移清单

目标：以本工程的原始 Java 源码与 ForgeGradle 构建为基础，保留最终反编译修改版的功能、配置兼容和客户端表现，将 GD 自身的 ASM 构建接入改成可维护的源码调用。

本清单建立于 2026-10-11。已完成 P1-A、P1-B 死亡派发及身体生命周期/同步、P3 死亡原因与火焰归属，以及 P2 镜头、复活姿态和死亡/复活世界滤镜；P3 卡片、元数据、部署输入和聊天布局也已迁入，P4 统一出界、载具原生摧毁和对应配置/UI 已迁入，P5-A 两轮发弹、部署实际储备上限及现有补给入口的限额补充已迁入，最新版本 `0.0.9` 已成功构建；原始项目能够运行已由用户确认。当前工作区在 `v0.9.3-bf1`，本次没有切换分支。后续迁移规则和每轮来源/产物见 [迁移版本记录](docs/migration/versions.md)。

## 固定对照与范围

| 对照 | 固定值 | 用途 |
| --- | --- | --- |
| 反编译原版提交 | `acb7636877d2d515c2a616d6703295120bec8e33` | 分离用户修改与反编译原始内容 |
| 反编译最终提交 | `955b76d8adc264703eb49141bdb99b06e84cba46` | 功能迁移来源；盘点时工作区干净 |
| 原始源码基线提交 | `4cf4ef4d09f64e5505ad0004ea1af99566ef5808` | 已修复构建依赖的原始源码；盘点前工作区干净 |
| 原版二进制 SHA-256 | `0bdfaee77d7281c550f0d107ca0763fb750b8e66ced4d4aafeba3ca022ca9ba2` | 原 ASM 构建绑定的输入 |
| 最终 `.24` JAR SHA-256 | `47bc5ed18e6071e93c3d3570a21bc18e36ab09a2c69b9f6828c429b8cff9c20e` | 行为与资源对照；不能仅凭文件名判断最新 |
| 网络协议 | 原始源码基线 `098`；最终成品 `114` | 迁移时核对数据包格式、注册顺序和两端行为 |

盘点结果：Git 差异 **177 项（139 新增、38 修改、无删除）**，其中 Java **147 项**；旧构建选择性编译 **121 个业务源码**；成品审计及实际二进制比较均为 **221 个修改/新增 class**，对应 **151 个顶层源码文件**。完整文件迁移清单 **181 项**，包括 Git 无差异但实际被改写的四个原类：

- `common/item/AmmoBagItem.java`
- `server/core/command/ConquestServerCommands.java`
- `server/room/runtime/RoomDeathSupportDropService.java`
- `server/room/runtime/RoomItemReplenishService.java`

上述路径均位于 `org/mods/gd656conquest/`。221 个 class 包含内部类，不能当作 221 个业务源码文件。本清单也不能只由 `.24` 当次修改说明或旧导出的 patch 推导。

## 明细入口与维护规则

| 文件 | 内容 |
| --- | --- |
| [文件明细](docs/migration/files.md) | 181 项来源、目标路径、主要批次、源码编译/ASM/成品审计证据及资源核对 |
| [ASM 接入清单](docs/migration/asm-hooks.md) | 30 个相关原类、方法与接入方式；编译前临时接口；80 处静态 hit 调用；109 个成品审计键 |
| [构建和验证输入](docs/migration/inputs.md) | 66 个外部构建/验证输入的内容哈希、原版/最终 JAR 与目标构建配置哈希 |
| [机器可读快照](docs/migration/inventory.json) | 上述记录、每个来源文件 SHA-256 及成品资源对照 |
| [重建证据脚本](tools/migration_inventory.py) | 标准库 Python 脚本；只重建 docs/migration 下的四个证据文件 |
| [第一批依赖分析](docs/migration/p1-dependencies.md) | 六个核心类的调用链、45 个方法入口、13 个缺失接口、运行注入点和六文件首个实施范围 |
| [P1 静态依赖证据](docs/migration/p1-dependency-evidence.md) | 87 个整类依赖候选、逐方法签名/目标入口对照与来源链；并非编译器最小依赖集合 |
| [迁移版本记录](docs/migration/versions.md) | 每轮版本、反编译功能对应、构建结果和产物哈希；未迁移基线为 0.0.0 |

从本项目目录运行 `python tools/migration_inventory.py` 可重建证据。脚本依赖旁边的旧源码、build-project、verification 和 dist；这份证据目前不是独立构建包。脚本固定最终提交并拒绝脏的来源工作区，不覆盖本文件中的人工状态。迁移批次完成后在本文件记录提交、验证结果和剩余问题。

P 编号表示主要验收顺序；SHARED 是跨批次文件。死亡服务直接依赖镜头、死亡原因、部署和边界模块，实际移植时要同时带入必要的真实依赖。不能根据文件主要批次删除调用、写空占位方法或假定每一批都能独立编译。

按用户指定，本轮及后续每轮以成功构建出主 JAR 为完成条件，格式固定为 `gd656conquest-0.9.3-bf1-x.x.x.jar`。状态记为 `待迁移 → 已迁入源码 → JAR 构建成功（本轮完成）`；自动验证及实机情况单独记录，不作为额外完成门槛，也不把构建成功表述为完整玩法等价。版本按迁入功能与反编译源码对应判断，新增独立逻辑优先放在新类，必要机制修改才改原类。共享文件按功能逐项签收。

## P0：构建、映射和协议基础

依赖：无。产出：可靠的原版对照环境和后续迁移入口。

- [x] 固定来源提交、原版/最终 JAR 哈希；盘点完整文件与成品 class 对应关系。
- [x] 盘点源码以外的构建接入、测试和资源打包行为。
- [x] 迁移分支 HEAD `e66bf3e` 为基线 `4cf4ef4` 的文档后继，原版基线保留可回退。
- [x] 原始项目能够运行（用户确认；本轮接受该结论，不重复执行构建或启动）。
- [x] 首轮迁移已构建主 JAR 并记录哈希；后续每轮继续记录。客户端/专用服务器与多人流程的实机验证另行记录。
- [ ] 固定 Java 17、MC 1.20.1、Forge 与外部模组版本；核对旧 SRG 编译依赖 Forge 47.4.10 与本工程配置 47.4.18 的差异。
- [ ] 将新增源码中的 SRG `m_...` / `f_...` 名称按真实映射转换为本工程 official 名称；Mixin 的目标、描述符和 remap 属性一起检查。
- [ ] 在 ForgeGradle 中加入需要的 ragdollified 编译/开发运行依赖，保留生产元数据中可选依赖语义；检查外部模组缺席时的类加载。
- [ ] 合并 Mixin 配置并由注解处理器生成 refmap；保留原工程已有 Mixin，兼容级别与 Java 17 对齐。禁止直接复用旧反编译 refmap。
- [ ] 整理 NetworkManager 与 DownedRagdollNetwork：保留原包注册顺序；核对十个扩展包的 ID、方向、编码、处理线程与退出清理。

十个扩展包按旧成品顺序为：DownedRagdollState、RagdollCatalog、RagdollObservation、RagdollSubscribe、DownedView、RevivedProneRelease、DeathCause、KillerCard、DownedDeploy、Suppression。另有已有的 RoomDownedActionPacket（显式目标 UUID）与 RoomDeathFlowPacket（死亡/出界状态）格式变化。不能仅注册新包而漏掉这两个旧包。

验收：全源码编译/重混淆；专用服务器不加载客户端类；联网两端注册一致；不兼容协议明确拒绝。中间迁移构建若尚未等价，不作为 `.24/114` 完整兼容版发行；最终是否沿用 114 以包格式和行为核对结果为准。

## P1：致命事件、倒地、布娃娃与救援

依赖：P0；同时处理必要的 P2/P3/P4 接口。主要目标：RoomCoreService、RoomCoreRuntimeManager、RoomServerModule、RoomPlayerRespawnService、RoomPlayerStateService、ClientReviveState、ClientDownedState、ClientPlayerAnimatorDebugState，以及 server/compat/ragdoll 与对应 Mixin/数据包。

- [x] 显式目标 `handleDownedAction` 重载已写入 Manager/Core/Respawn 原始源码并打通网络调用链（P1-A / 0.0.1）。
- [ ] 将 `tryHandlePlayerDownedDamage` 写入真实源码；倒地资格 API 按后续依赖一并迁移。
- [x] 迁移完整 Forge 事件派发后再接管房间死亡的机制，保持原事件及 DamageSource；取消/恢复、递归/嵌套及部署代理处理已迁入（0.0.2）。
- [x] 接入致命受击/爆炸动量与新鲜尸体身份快照，先完成监听器再提升本次死亡身体（0.0.3）。
- [x] 接入火焰归属与致命前几何快照、一次性死亡原因及共享击杀者归属；保留原计分/助攻服务，系统死亡不继承近期攻击者（0.0.4）。
- [x] 接入击杀者装备、生命值、武器与爆头的预派发快照，火焰作用域内先冻结再完整派发（0.0.7）。
- [x] 迁移 DownedRagdollBridge / ClientState / ClientHooks，以 RagdollFatalContext 衔接已有死亡派发器：生成/提升单个存活倒地身体、维持玩家代理、完成死亡或救起后的清理（0.0.3）。
- [x] 迁移 startReviveAttempt 明确 UUID：复用原权威校验和持续救援逻辑，新增目标锁定检查；直接传参，不使用救援 ThreadLocal 桥（P1-A / 0.0.1）。身体位置规则仍见下一项。
- [x] 服务端三处救援距离与客户端候选距离统一按身体位置处理；保留原权威资格校验，身体就绪时停用旧预览，失败保留原动画（0.0.3）。
- [x] 迁移 forceRedeployDeath 两处致死操作后的成功判断；保留起始存活检查，防止部署代理恢复生命后被二次杀死（0.0.2）。
- [x] 保留死亡爆炸动量；迁入过期受击缓存保护、工作线程替换和发送者隔离、已完成播放保持及静止姿态复用，沿用原生模拟者选举（0.0.3；实机仍待验收）。
- [x] 迁移目录/观察/订阅范围同步与性能规则，包括 32/40 格近距滞回、远处视野订阅、续期与暂停、跨维度拒绝、代次与 token 清理（0.0.3；跨房间游戏内表现待验收）。普通死亡镜头的强制订阅已在 0.0.5 接入。
- [x] 对死亡、救援、重新部署、退出房间、断线、切换维度和停服接入身体清理；退房/房间结束同时通过原 normalizePlayerModelState 与 tick 状态核验收口（0.0.3）。

自动验证参考：verify-ragdoll-integration.ps1、verify-death-eventbus.ps1、verify-forced-deployment.ps1、verify-momentum-cache.ps1、verify-ragdoll-mixins.ps1；RealReplacementPipelineTest、RealScopedPacketTest、RagdollReadinessTest、RagdollSubscriptionTest、ClientVisibilitySubscriptionTest、RagdollOptimizationTest。具体旧脚本并非都通过 verify-mod.ps1 自动执行。

实机验收：至少两名玩家，普通枪伤/爆炸倒地、多个救援目标、持续救援被打断、成功救援、放弃救援、不可救援死亡、重新部署、观察者进入/离开视距、断线重连。每次只能生成预期的一个身体，计分/阵亡只发生一次。

## P2：死亡镜头、复活姿态和世界滤镜

依赖：P1 的状态/生命周期。主要目标：DownedViewService / CameraConfig、ClientDownedCamera、DownedCameraMath、ClientDeathFlowState、ClientRoomLockState、RevivedPronePose 与 TACZ 联动、DeathFilter/ReviveEffect/Transition 与后处理资源。

- [x] 注册镜头服务端配置；迁移固定/自动镜头模式、普通死亡镜头和输入锁定；两个进入/退出倒地路径接入镜头生命周期（0.0.5；实机视觉待验收）。
- [x] 救援完成时先准备归位与停止身体，再完成姿态广播和交接；保留倒地前朝向、救起后趴下及趴姿解除逻辑（0.0.5；新按跳跃/爬行解除、TACZ 专用释放作用域）。
- [x] 将 adoptCameraPose 写成真实方法；部署交接先捕获当前显示姿态再清理，核对调用顺序（0.0.5）。deathFilterKillerId 的阶段限制与本地轮廓消费链已在 0.0.6 接入。
- [x] 关闭被新实现替代的旧死亡黑幕、死亡期间受伤闪烁和全局去色路径（0.0.6）；0.0.5 已只移除成功救援时的旧失明调用，部署/出生失明逻辑保持原状。
- [x] 迁移世界滤镜与 HUD 的渲染顺序、击杀者本地轮廓、天空 alpha、资源重载及 GPU 资源释放（0.0.6；实际 GLSL/GPU 和当前 Forge Mixin 验证通过，游戏内重载/第三方兼容待验收）。
- [x] 保留最终参数：复活白光 1.40 秒、峰值保持 0.10 秒、曝光 +3.1 EV、bloom 1.40、镜头归位 0.28 秒、成功救援时死亡去色退出 0.08 秒（0.0.6；普通部署退出仍为来源的 0.20 秒）。

自动验证参考：DownedViewTest、ReviveTransitionTest、DeathFilterTransitionTest、RealDeathFilterMixinTest、RealRevivedProneReleaseTest、DeathFilterGpuTest、ReviveGpuTest；verify-death-filter.ps1、verify-prone-release.ps1。

实机验收：不同 GUI 缩放、自动/固定模式、出生/部署/救起三种过渡、天空及 HUD 颜色、第三方准星/瞄具、资源重载和退出。镜头不能留在身体或部署镜头上，滤镜尾段不能继续锁输入。

## P3：死亡归属、击杀者卡片与部署输入

依赖：P1/P2。主要目标：DeathCauseClassifier / RoomDeathCauseService / KilliconFireOwnership、FatalHitMetadata / KillWeaponResolver / KillerIntelService / MatchDuelStats、ManualDeathDeploy/DownedDeploy、KillerCard 与 RoomChat、对应数据包。

- [x] 迁移一次性死亡快照与原因优先级：精确 /kill → 出界处罚 → 主动重新部署 → 敌方玩家 → 本人事故 → 环境死亡 → 通用阵亡；沿用有效近期攻击归属（0.0.4）。
- [x] 区分系统强制死亡与普通 genericKill；/kill、出界、重新部署不能被先前攻击者覆盖；放弃救援继续沿用已接受的死亡原因（0.0.4）。
- [x] 迁移火焰/燃烧云归属的致命前只读快照、超时/熔岩排除和敌我判定；保留原计分/助攻服务（0.0.4；未执行实机归属验收）。
- [x] 迁移武器/爆头元数据和对决统计：致命前快照、精确命中关联、房间/场次对决账本及只读可选扩展（0.0.7）。
- [x] 迁移普通不可救援死亡的手动部署门槛与四个被抽取方法中的 onPlayerDeath / openManualDeathDeploy：真实源码调用原部署流程，归零后继续等待新按键，提前进入只携带剩余等待（0.0.7）。
- [x] 同步倒地部署快照、单次部署请求与长按放弃逻辑；仅服务端零值解锁点按，持续按住不能误触发，救援锁定沿用原权威校验（0.0.7）。
- [x] 迁移非玩家来源的原因横幅、200 ms 淡入淡出、过期快照拒绝、中英文文本与退出隐藏（0.0.4）。
- [x] 迁移底部全宽五列卡片与右侧操作/救援布局；关闭被替代的动作栏击杀字幕、悬浮死亡倒计时及被救援提示，救援者自身的原操作提示保留（0.0.7）。
- [x] 迁移房间内含死亡/部署阶段的聊天定位、输入框、原生滚动/点击坐标及建议列表；退出流程隐藏卡片/横幅并清理元数据（0.0.7）。

自动验证参考：DeathCauseTest、KillerIntelTest、KillerCardBehaviorTest、ManualDeathDeployTest、RealKillerCardMixinTest、RoomUiScopeTest；verify-death-causes.ps1、verify-killer-card.ps1。

实机验收：玩家击杀/环境死亡/事故/管理员杀死/主动部署、普通命中与爆头、长武器名、不同 GUI 缩放、聊天滚动/点击链接/建议列表、救援锁定、长按与新按空格两种边界输入。

## P4：统一出界、载具摧毁与配置复制

依赖：P1/P3；共同修改 RoomCommonConfig、配置描述/验证、编辑、模板与重载。

- [x] 迁移 BoundaryCountdown / FlightBoundaryGeometry / UnifiedBoundaryService；每 5 tick 判定，使用 UUID 到期 tick，状态切换只收紧连续出界的剩余时间。
- [x] 保留步兵/地面/飞行时限、飞行水平缓冲默认 100 格、凹多边形/多区域、高度及方块规则；显式 0 与旧字段缺失区别处理。
- [x] 关闭旧飞行载具独立超时/警告入口；玩家超时死亡后通过载具原生生命值/摧毁接口处理，不直接 discard/remove。
- [x] 迁移原生爆炸开始时的乘员快照；全员逃兵、不可救援、提前下车不纳入、爆炸后未完成阵亡者补处理且不重复记账。
- [x] 保留界外普通致命伤的真实死亡原因与归属，同时跳过救援；区分出界处决与界外遭敌方击杀。
- [ ] 公共配置所有拷贝点传递六个扩展字段；成品 ASM 审计四处，核对仓库/分组编辑/模板/命令中的实际构造调用；重载、保存、武器修改均不丢字段。
- [x] 迁移边界 UI、暖灰世界滤镜、0.10 秒过渡、步兵准星隐藏、1.905 秒语音冷却；标题“回到战斗区域”保持正确编码，HUD 与载具瞄具保留。
- [x] 只修改兵种中文显示名：ASSAULT 医护兵、ENGINEER 突击兵；内部 ID、别名、配装和技能保留。

本轮 `0.0.8` 完成上述出界核心行为。共享配置的六个字段中已迁入 `flightVehicleOutsideBoundaryDeathSeconds`、`flightBoundaryPaddingBlocks`，四处复制链均携带这两个值；其余敌方补给开关、压制配置、治疗伤害冷却与被动回血量按 P5/P6 继续接入，因此六字段总项保持待完成。与最终来源一致，显式 0 的加载/运行语义保留，但开局完整性校验仍要求出界时限为正。

自动验证参考：BoundaryTest、BoundaryDeathsTest、RealBoundaryMixinTest、RealBoundaryVehicleMixinTest、BoundaryGpuTest；verify-boundary.ps1。

实机验收：步行/上下车/换座、多个乘员、超高/边界方块、YWZJ 与 Superb Warfare 爆炸和残骸、提前下车、计时禁用、旧配置读写、模板复制及重载。

## P5：箱包补给、两轮发弹与成功音效

依赖：P0/P1 与房间/阵营/部署配置。主要目标：server/support（共享规则除外）、AmmoBag/MedicalBag、PlayerItemInteractionService、RoomSupportBox/DeathSupportDrop/ItemReplenish/LoadoutGrant/PlayerLoadoutService、ClientAmmoSupplySound 与声音通道访问器。

- [ ] 接入包使用/对实体交互/丢弃/拾取分支；新路径成功后停止旧逻辑；Q 键、整组丢弃、容器/背包丢弃限制同步迁入。
- [ ] 保留房间、阵营、所有者、敌方补给权限、库存与生命周期标记；创造模式自己投出的包同样有 0.5 秒拾取保护。
- [ ] 接入死亡奖励包标记及 30 秒寿命；初始配备包库存 2；库存恢复按独立规则处理。
- [ ] 接入新医疗/弹药箱光环和元数据；关闭旧箱交互、医疗箱提示及箱包生命恢复药水效果；核对医疗/弹药容量常量 100/20。
- [x] 迁移两轮初始发弹：先按枪包组数量分格，再填同批格子的剩余容量；不溢出到其他格子；部署结束保存实际发放量到枪械 NBT，同类弹池合并。
- [x] 补给量受本次部署实际发放上限约束；弹药、配备、投掷物分别核对；旧枪重新部署后刷新记录。
- [ ] 医疗包首次和后续回血共用实际成功音效入口；医疗箱、医疗包、自然回血起始及补给声音只发给接受者，未成功时不播放。
- [ ] 客户端按真实通道分别保护回血/补弹音效；同 tick 合并、播放中不叠加/打断/排队，声音结束后允许下次成功事务播放。

本轮 `0.0.9` 完成 P5-A：独立 SupportAmmoGrant/SupportAmmo/SupportAmmoRefill 类实现两轮发弹、实际量 NBT、最终配备覆盖修正、同类弹池合并和储备补充。原 RoomLoadoutGrantService/RoomPlayerLoadoutService 只接入发弹/部署结束、替换整套配装补给及旧包越限逻辑；旧空格补弹增加实际堆叠容量检查。原箱入口按配置补充弹药、配备和投掷物，原包入口保留 maxRounds 限额且不补慢速配备；没有迁入新光环的频率、包的两弹匣规则、Q/所有者/敌方权限及私人声音。共享配置尚未增加 P5/P6 的四个字段。上述两项完成的是发弹与储备规则，不代表整个箱包运行时已等价。

本轮 P5-A 自动验证：AmmoGrantTest、AmmoReserveFixtureTest、AmmoReserveScopeTest；tools/verify-ammo-reserves.ps1。后续完整 P5 验证再迁入 SupportRulesTest、AmmoSupplySoundTest、RealSupportMixinTest。

实机验收：自己/队友/敌人跨房间拾取、创造投包、背包满格/扩容/部分发弹、同类弹池、重新部署、死亡掉包、医疗箱/包连续回血、长音频和旁观玩家听不到私人声音。

## P6：火力压制与房间自然回血

依赖：P1/P5；P4/P5/P6 共用配置扩展与复制流程。

- [ ] 迁移 TACZ 弹道截断、头部距离、普通/穿透弹丸、同次射击分组与强度上限；强度按实际规则累计、衰减、同步并清理。
- [ ] 保留退出被压制状态后等待完整 2 秒的回复冷却；重新压制后从下次退出重新计时；医疗箱与自然回血走公共阻断检查。
- [ ] 迁移 ClientSuppressionState 与世界软暗角；不恢复已删除的“被压制”文字 HUD。
- [ ] 迁移房间“全局/房间”来源、旧“禁用”向“房间＋两个开关关闭”的配置转换；房间外读取全局配置。
- [ ] 保留当前缺省：房间来源、被动回血开启、饱食度回血关闭、受伤等待 6 秒、间隔 10 tick、单次 0.5 HP；医疗箱独立受伤冷却 3 秒。
- [ ] 明确保存的旧配置值保留；缺失/无效值回退同步到 ServerConfigDataModel/Store、RoomNaturalRegenConfig、配置验证与复制。
- [ ] PassiveRegenService 接入开关/等待/间隔/单次量与私人声音；饱食度回血 Mixin 按玩家房间选择开关并受压制规则限制。
- [ ] ConquestServerCommands 接入新全局配置键、读写、补全和全量输出；处理未知键向旧路径回退。

自动验证参考：SupportRulesTest、RealSupportMixinTest、AmmoSupplySoundTest，包含配置来源/旧禁用/默认值/压制退出重计时验证；verify-support.ps1。

实机验收：近失弹、连续/散射/穿透射击、压制进入/退出/再进入、医疗与两种自然回血暂停/恢复、全局/房间切换、原版饱食度开关、旧配置加载和缺依赖启动。

## P7：资源、构建与验证收口

依赖：P0-P6；资源可随对应批次提前迁移。

- [ ] assets/data/META-INF 等迁入 src/main/resources，保留原工程资源与 Gradle 属性模板；mods.toml 合并可选 ragdollified 依赖和版本字段，不能直接覆盖成固定反编译文本。
- [ ] sounds.json、出界 OGG、死亡/复活/出界/压制 GLSL 与程序/pass JSON、卡片 headshot.png、两种语言文件逐项核对成品。
- [ ] `vo_mp_mother_inform_leaving_area.wav` 在来源目录有新增记录，但固定 `.24` JAR 不包含它；列为参考音源。实际播放迁移 OGG，不能把 WAV 记录当作已打包功能。
- [ ] 通过 ForgeGradle 打包、refmap 生成、重混淆，最终业务构建不再依赖原版 JAR 方法替换；保留 normal/nores/resources 等产物需要的原工程任务。
- [ ] 将手工维护测试、构建/安装说明和必要验证夹具纳入目标仓库；生成类、缓存、SRG classpath 文本和临时 prepared JAR 不作为业务源码迁入。
- [ ] 使用新产物重建验证入口，解除旧脚本中的绝对 JDK 路径、固定 `.24` 路径、SRG 接口及旧源码目录绑定。
- [ ] 调整 VerifyRagdollJar、RealPacketTest 与 Scope 系列：保留链接/类加载、协议往返、客户端隔离和行为约束；不要求全源码产物保留相同 class 字节、固定类数量或旧 ASM 接入次数。
- [ ] 最终两端联网验收、外部可选模组存在/缺席检查、配置升级/重载、GPU/资源重载、多人完整对局；记录产物哈希和剩余问题后再发行。

## 验证迁移方式

| 类型 | 现有输入 | 源码迁移后处理 |
| --- | --- | --- |
| 业务规则与计时 | SupportRulesTest、AmmoGrantTest、BoundaryTest、DeathCauseTest、KillerIntelTest、ManualDeathDeployTest、ReviveTransitionTest 等 | 保留断言意图；转换映射与真实生产调用，避免只测复制出来的方法 |
| 网络与 JVM 成员链接 | RealPacketTest、VerifyRagdollJar | 改为新 Gradle 产物和开发依赖；包方向/ID/字段/往返/服务端隔离继续验证 |
| 真实死亡事件 | RealDeathEventBusTest、DeathDispatchTest | 保留完整派发、取消事件和接管时序；增加专用服务器实际流程验收 |
| 外部 Mixin 转换 | RealRagdollMixinTest、RealBoundary*、RealSupportMixinTest、RealDeathFilterMixinTest、RealKillerCardMixinTest、RealRevivedProneReleaseTest | 保留外部接入验证；更新 official/SRG 输入与 refmap；含客户端、服务器和依赖缺席场景 |
| GPU 与渲染 | DeathFilterGpuTest、ReviveGpuTest、BoundaryGpuTest | 使用新产物着色器/pass JSON；保留像素断言及实机视觉验收 |
| Scope/旧版本字节保护 | *ScopeTest、多个历史版本 JAR 对照 | 逐项改成范围/行为或关键资源约束；全源码重编译后不能用字节相同作为通用通过条件 |
| 脚本生成测试/模拟接口 | verify-forced-deployment.ps1 的 ForcedDeploymentTest；各脚本的 Stub/fixture | 测试源码可能嵌在 PowerShell 中；不能只复制 tests/*.java。模拟接口只进入测试，不能打包进模组 |

verify-mod.ps1 当前主要串联成品检查、RealPacketTest、边界和补给验证；其他 suite 必须明确纳入迁移验收，不假定一个脚本覆盖全部功能。42 个独立 Java 文件中包含 IsolationMixinService / IsolationProperties 两个辅助文件，不是 42 套独立业务测试。本次未执行上述游戏或 Java 验证。

## 第一批执行边界与记录

P0 原始项目运行基线已获用户确认；P1-A、P1-B 死亡派发与身体同步、P2 镜头/姿态和世界滤镜，以及 P3 死亡归属、击杀者快照/卡片、手动部署和聊天布局已完成。0.0.6/0.0.7 已由用户提交为 `42004f9`。P4 出界核心已由用户提交为 `1fe56bb`。本轮迁入 P5-A：三个新类、两个原业务 Java 文件的必要机制接入，`0.0.9` 主 JAR 已成功构建，协议保持 `bf1-0.0.8`。四个补给/治疗/压制共享字段及后续业务留到 P5/P6；多人实机与第三方渲染兼容仍待验收。

每轮围绕可构建的具体行为迁入；成功构建后记录版本与来源对应。本轮修改留在工作区，未自动提交。

| 批次 | 状态 | 提交 | 自动验证 | 实机验收/剩余问题 |
| --- | --- | --- | --- | --- |
| 清单与证据 | 已建立；P1 迁移前依赖分析已补齐 | 原清单 e66bf3e；分析 b9eb58e | 181 项文件盘点；迁移前 45 个方法/13 个接口核对 | 保留迁移前快照，当前状态见版本记录 |
| P0 | 首轮构建/命名/协议已接入 | b9eb58e | clean build、refmap/重混淆、包内版本与协议核对 | 后续扩展协议/依赖继续迁移 |
| P1-A | 本轮完成，版本 0.0.1 | b9eb58e | 主 JAR 构建成功；5 个目标选择场景通过；包内容核对通过 | 未执行联机验证；完整布娃娃/镜头/死亡快照仍待迁移 |
| P1-B 死亡派发/部署保护 | 本轮完成，版本 0.0.2 | b9eb58e | 主 JAR 构建成功；61 项协调器检查；4 个真实 EventBus 场景；Mixin/refmap 核对 | 未执行联机；身体、动量、镜头与快照尚未接入 |
| P1-B 身体生命周期/同步 | 本轮完成，版本 0.0.3 | b9eb58e | 主 JAR 构建成功；121 项身体/包/播放/替换检查；真实 CLIENT 13 / SERVER 9 个 Mixin 目标；可选依赖缺席检查 | 未执行联机；源文件映射与阶段边界见版本记录 |
| P1-B 剩余接口、P1-C 至 P1-E | 部分完成 | 工作区未提交 | 死亡归属子功能见 P3 本轮记录 | 镜头/趴姿/部署快照已接入；其他扩展按后续批次与完整实机验收推进 |
| P2 镜头/复活姿态 | 已完成，版本 0.0.5 | 1c912ab | 主 JAR 构建成功；224 项构图/运动/归位、97 项包、24 项调用顺序检查；CLIENT 5 / SERVER 1 个真实 Mixin 目标及 TACZ 缺席检查 | 未执行实机；控制器完整游戏流程待验收；滤镜见下一行 |
| P2 死亡/复活世界滤镜 | 已完成，版本 0.0.6 | 42004f9 | 主 JAR 构建成功；1908/2003 项死亡/复活时间线、2484 项成品范围；3 个真实 CLIENT Mixin 目标及 SERVER 隔离；331/109075 项实际 GPU 像素检查 | 未执行 Minecraft/联机；游戏内资源重载、第三方着色器/瞄具兼容待验收 |
| P3 死亡原因/火焰归属 | 本轮完成，版本 0.0.4 | b9eb58e | 主 JAR 构建成功；155 项规则/作用域/横幅、35 项包检查、15 项接口/调用链；2 个真实 Forge Mixin/refmap 目标通过 | 未执行实机；卡片/武器/爆头/对决与手动部署见下一行 |
| P3 元数据/卡片/手动部署/聊天 | 已完成，版本 0.0.7 | 42004f9 | 主 JAR 成功；3433/18 项卡片布局/输入模型、91 项包、9 项真实 EventBus/扩展、2843 项成品范围/调用链；3 个真实 CLIENT 目标及 SERVER 隔离；前序镜头/归属/派发/GPU 回归通过 | 未启动 Minecraft/联机；实际爆头/互杀/聊天操作/GUI 缩放需实机验收 |
| P4 出界核心 | 已完成，版本 0.0.8；共享字段部分完成 | 1fe56bb | 主 JAR 成功；真实配置/包、13 项载具夹具、360 项 HUD、真实客户端/服务器及依赖缺席 Mixin、190 项边界 GPU；13888 项范围/调用链；前序镜头/归属/卡片/滤镜回归通过 | 未启动 Minecraft/联机；四个补给/治疗/压制字段按 P5/P6 接入 |
| P5-A 发弹/储备上限 | 本轮完成，版本 0.0.9 | 工作区未提交 | 主 JAR 成功；473 项分配/NBT/接入、33 项实际成品方法夹具、4131 项成品范围/调用链检查通过 | 未启动 Minecraft；新箱包光环、权限/投包/死亡掉包、医疗/私人音效留到后续 P5 |
| P5 其余运行时 | 待迁移 | — | 未执行 | 依赖完整补给与 P6 治疗/压制规则 |
| P6 | 待迁移 | — | 未执行 | — |
| P7 | 待迁移 | — | 未执行 | — |
