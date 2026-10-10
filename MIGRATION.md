# gd656conquest 原始源码迁移清单

目标：以本工程的原始 Java 源码与 ForgeGradle 构建为基础，保留最终反编译修改版的功能、配置兼容和客户端表现，将 GD 自身的 ASM 构建接入改成可维护的源码调用。

本清单建立于 2026-10-11。仅已完成盘点和证据核对；所有业务迁移、游戏启动及多人验收仍待执行。当前工作区在 `v0.9.3-bf1`，本次没有切换分支。

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

从本项目目录运行 `python tools/migration_inventory.py` 可重建证据。脚本依赖旁边的旧源码、build-project、verification 和 dist；这份证据目前不是独立构建包。脚本固定最终提交并拒绝脏的来源工作区，不覆盖本文件中的人工状态。迁移批次完成后在本文件记录提交、验证结果和剩余问题。

P 编号表示主要验收顺序；SHARED 是跨批次文件。死亡服务直接依赖镜头、死亡原因、部署和边界模块，实际移植时要同时带入必要的真实依赖。不能根据文件主要批次删除调用、写空占位方法或假定每一批都能独立编译。

每个功能使用四个状态：`待迁移 → 已迁入源码 → 自动验证通过 → 实机验收通过`。编译通过只证明构建，不等于玩法等价。共享文件按功能逐项签收。

## P0：构建、映射和协议基础

依赖：无。产出：可靠的原版对照环境和后续迁移入口。

- [x] 固定来源提交、原版/最终 JAR 哈希；盘点完整文件与成品 class 对应关系。
- [x] 盘点源码以外的构建接入、测试和资源打包行为。
- [ ] 在迁移分支确认 HEAD 与本基线的关系，保留原版基线可回退。
- [ ] 执行 `./build.ps1 -Offline -Clean`，记录新构建日志与产物哈希；检查旧原版 JAR 与源码产物的实际行为差异。
- [ ] 客户端与专用服务器分别启动；使用房间配置副本验证加载、阵营加入、部署、原始倒地/救援和退出流程。
- [ ] 固定 Java 17、MC 1.20.1、Forge 与外部模组版本；核对旧 SRG 编译依赖 Forge 47.4.10 与本工程配置 47.4.18 的差异。
- [ ] 将新增源码中的 SRG `m_...` / `f_...` 名称按真实映射转换为本工程 official 名称；Mixin 的目标、描述符和 remap 属性一起检查。
- [ ] 在 ForgeGradle 中加入需要的 ragdollified 编译/开发运行依赖，保留生产元数据中可选依赖语义；检查外部模组缺席时的类加载。
- [ ] 合并 Mixin 配置并由注解处理器生成 refmap；保留原工程已有 Mixin，兼容级别与 Java 17 对齐。禁止直接复用旧反编译 refmap。
- [ ] 整理 NetworkManager 与 DownedRagdollNetwork：保留原包注册顺序；核对十个扩展包的 ID、方向、编码、处理线程与退出清理。

十个扩展包按旧成品顺序为：DownedRagdollState、RagdollCatalog、RagdollObservation、RagdollSubscribe、DownedView、RevivedProneRelease、DeathCause、KillerCard、DownedDeploy、Suppression。另有已有的 RoomDownedActionPacket（显式目标 UUID）与 RoomDeathFlowPacket（死亡/出界状态）格式变化。不能仅注册新包而漏掉这两个旧包。

验收：全源码编译/重混淆；专用服务器不加载客户端类；联网两端注册一致；不兼容协议明确拒绝。中间迁移构建若尚未等价，不作为 `.24/114` 完整兼容版发行；最终是否沿用 114 以包格式和行为核对结果为准。

## P1：致命事件、倒地、布娃娃与救援

依赖：P0；同时处理必要的 P2/P3/P4 接口。主要目标：RoomCoreService、RoomCoreRuntimeManager、RoomServerModule、RoomPlayerRespawnService、RoomPlayerStateService、ClientReviveState、ClientDownedState、ClientPlayerAnimatorDebugState，以及 server/compat/ragdoll 与对应 Mixin/数据包。

- [ ] 将 `tryHandlePlayerDownedDamage` 和显式目标 `handleDownedAction` 重载写入原始源码；新增方法不能停留在 prepared API JAR 中。
- [ ] 迁移完整死亡事件派发/延后接管流程，传递真实 DamageSource，保留其他监听器、Killicon 归属和计分时序；禁止重复触发事件/死亡记账。
- [ ] 迁移 DownedRagdollBridge / Events / ClientState / ClientHooks：生成单个存活倒地身体、维持玩家代理、完成死亡或救起后的清理。
- [ ] 迁移 startReviveAttempt：请求明确 UUID；检查目标有效性、队伍、房间、距离和持续救援条件；源码调用链显式传参优先，线程局部上下文是否保留单独审阅。
- [ ] 服务端三处救援距离与客户端候选距离统一按身体位置处理；候选目标资格与旧预览姿态处理按成品行为核对。
- [ ] 迁移 forceRedeployDeath 两处致死操作后的成功判断；保留起始存活检查，防止部署代理恢复生命后被二次杀死。
- [ ] 保留死亡爆炸动量；处理过期受击缓存、替换身体、播放状态、静止冻结和观察者选举；防止重复身体与重复播放。
- [ ] 迁移目录/观察/订阅范围同步与性能规则；检查刚加入观察、远处身体、跨房间及无 ragdollified 的路径。
- [ ] 对死亡、救援、重新部署、退出房间、断线、切换维度和停服逐一接入状态清理。

自动验证参考：verify-ragdoll-integration.ps1、verify-death-eventbus.ps1、verify-forced-deployment.ps1、verify-momentum-cache.ps1、verify-ragdoll-mixins.ps1；RealReplacementPipelineTest、RealScopedPacketTest、RagdollReadinessTest、RagdollSubscriptionTest、ClientVisibilitySubscriptionTest、RagdollOptimizationTest。具体旧脚本并非都通过 verify-mod.ps1 自动执行。

实机验收：至少两名玩家，普通枪伤/爆炸倒地、多个救援目标、持续救援被打断、成功救援、放弃救援、不可救援死亡、重新部署、观察者进入/离开视距、断线重连。每次只能生成预期的一个身体，计分/阵亡只发生一次。

## P2：死亡镜头、复活姿态和世界滤镜

依赖：P1 的状态/生命周期。主要目标：DownedViewService / CameraConfig、ClientDownedCamera、DownedCameraMath、ClientDeathFlowState、ClientRoomLockState、RevivedPronePose 与 TACZ 联动、DeathFilter/ReviveEffect/Transition 与后处理资源。

- [ ] 注册镜头服务端配置；迁移固定/自动镜头模式、普通死亡镜头和输入锁定；两个进入/退出倒地路径接入镜头生命周期。
- [ ] 救援完成时先准备归位与停止身体，再完成姿态广播和交接；保留倒地前朝向、救起后趴下及趴姿解除逻辑。
- [ ] 将 deathFilterKillerId / adoptCameraPose 等临时接口写成真实方法；部署交接先捕获当前头部姿态再清理，核对调用顺序。
- [ ] 关闭被新实现替代的旧死亡黑幕、受伤闪烁和去色路径；只移除成功救援时的旧失明调用，部署/出生失明逻辑按现状保留。
- [ ] 迁移世界滤镜与 HUD 的渲染顺序、击杀者轮廓、天空 alpha、资源重载及 GPU 资源释放。
- [ ] 保留最终参数：复活白光 1.40 秒、峰值保持 0.10 秒、曝光 +3.1 EV、bloom 1.40、镜头归位 0.28 秒、死亡去色退出 0.08 秒。

自动验证参考：DownedViewTest、ReviveTransitionTest、DeathFilterTransitionTest、RealDeathFilterMixinTest、RealRevivedProneReleaseTest、DeathFilterGpuTest、ReviveGpuTest；verify-death-filter.ps1、verify-prone-release.ps1。

实机验收：不同 GUI 缩放、自动/固定模式、出生/部署/救起三种过渡、天空及 HUD 颜色、第三方准星/瞄具、资源重载和退出。镜头不能留在身体或部署镜头上，滤镜尾段不能继续锁输入。

## P3：死亡归属、击杀者卡片与部署输入

依赖：P1/P2。主要目标：DeathCauseClassifier / RoomDeathCauseService / KilliconFireOwnership、FatalHitMetadata / KillWeaponResolver / KillerIntelService / MatchDuelStats、ManualDeathDeploy/DownedDeploy、KillerCard 与 RoomChat、对应数据包。

- [ ] 迁移一次性死亡快照与原因优先级：精确 /kill → 出界处罚 → 主动重新部署 → 敌方玩家 → 本人事故 → 环境死亡 → 通用阵亡；沿用有效近期攻击归属。
- [ ] 区分系统强制死亡与普通 genericKill；/kill、出界、重新部署不能被先前攻击者覆盖，倒地时放弃救援不能误归类为重新部署。
- [ ] 迁移火焰/燃烧云归属读取时序、敌我判定、武器/爆头元数据、对决统计；保留原计分/助攻机制。
- [ ] 迁移普通不可救援死亡的手动部署门槛与四个被抽取方法中的 onPlayerDeath / openManualDeathDeploy。
- [ ] 同步倒地部署快照、单次部署请求与长按放弃逻辑；倒计时归零时持续按住不能误触发部署；救援锁定期间不能绕过操作限制。
- [ ] 迁移底部全宽五列卡片、非玩家来源的右侧操作/救援布局、原因横幅；关闭旧动作栏击杀字幕、悬浮倒计时和救援提示。
- [ ] 迁移死亡/部署阶段聊天定位、输入框、滚动和建议列表；退出流程隐藏卡片/横幅并清理元数据。

自动验证参考：DeathCauseTest、KillerIntelTest、KillerCardBehaviorTest、ManualDeathDeployTest、RealKillerCardMixinTest、RoomUiScopeTest；verify-death-causes.ps1、verify-killer-card.ps1。

实机验收：玩家击杀/环境死亡/事故/管理员杀死/主动部署、普通命中与爆头、长武器名、不同 GUI 缩放、聊天滚动/点击链接/建议列表、救援锁定、长按与新按空格两种边界输入。

## P4：统一出界、载具摧毁与配置复制

依赖：P1/P3；共同修改 RoomCommonConfig、配置描述/验证、编辑、模板与重载。

- [ ] 迁移 BoundaryCountdown / FlightBoundaryGeometry / UnifiedBoundaryService；每 5 tick 判定，使用 UUID 到期 tick，状态切换只收紧连续出界的剩余时间。
- [ ] 保留步兵/地面/飞行时限、飞行水平缓冲默认 100 格、凹多边形/多区域、高度及方块规则；显式 0 与旧字段缺失区别处理。
- [ ] 关闭旧飞行载具独立超时/警告入口；玩家超时死亡后通过载具原生生命值/摧毁接口处理，不直接 discard/remove。
- [ ] 迁移原生爆炸开始时的乘员快照；全员逃兵、不可救援、提前下车不纳入、爆炸后未完成阵亡者补处理且不重复记账。
- [ ] 保留界外普通致命伤的真实死亡原因与归属，同时跳过救援；区分出界处决与界外遭敌方击杀。
- [ ] 公共配置所有拷贝点传递六个扩展字段；成品 ASM 审计四处，核对仓库/分组编辑/模板/命令中的实际构造调用；重载、保存、武器修改均不丢字段。
- [ ] 迁移边界 UI、暖灰世界滤镜、0.10 秒过渡、步兵准星隐藏、1.905 秒语音冷却；标题“回到战斗区域”保持正确编码，HUD 与载具瞄具保留。
- [ ] 只修改兵种中文显示名：ASSAULT 医护兵、ENGINEER 突击兵；内部 ID、别名、配装和技能保留。

自动验证参考：BoundaryTest、BoundaryDeathsTest、RealBoundaryMixinTest、RealBoundaryVehicleMixinTest、BoundaryGpuTest；verify-boundary.ps1。

实机验收：步行/上下车/换座、多个乘员、超高/边界方块、YWZJ 与 Superb Warfare 爆炸和残骸、提前下车、计时禁用、旧配置读写、模板复制及重载。

## P5：箱包补给、两轮发弹与成功音效

依赖：P0/P1 与房间/阵营/部署配置。主要目标：server/support（共享规则除外）、AmmoBag/MedicalBag、PlayerItemInteractionService、RoomSupportBox/DeathSupportDrop/ItemReplenish/LoadoutGrant/PlayerLoadoutService、ClientAmmoSupplySound 与声音通道访问器。

- [ ] 接入包使用/对实体交互/丢弃/拾取分支；新路径成功后停止旧逻辑；Q 键、整组丢弃、容器/背包丢弃限制同步迁入。
- [ ] 保留房间、阵营、所有者、敌方补给权限、库存与生命周期标记；创造模式自己投出的包同样有 0.5 秒拾取保护。
- [ ] 接入死亡奖励包标记及 30 秒寿命；初始配备包库存 2；库存恢复按独立规则处理。
- [ ] 接入新医疗/弹药箱光环和元数据；关闭旧箱交互、医疗箱提示及箱包生命恢复药水效果；核对医疗/弹药容量常量 100/20。
- [ ] 迁移两轮初始发弹：先按枪包组数量分格，再填同批格子的剩余容量；不溢出到其他格子；部署结束保存实际发放量到枪械 NBT，同类弹池合并。
- [ ] 补给量受本次部署实际发放上限约束；弹药、配备、投掷物分别核对；旧枪重新部署后刷新记录。
- [ ] 医疗包首次和后续回血共用实际成功音效入口；医疗箱、医疗包、自然回血起始及补给声音只发给接受者，未成功时不播放。
- [ ] 客户端按真实通道分别保护回血/补弹音效；同 tick 合并、播放中不叠加/打断/排队，声音结束后允许下次成功事务播放。

自动验证参考：SupportRulesTest、AmmoGrantTest、AmmoSupplySoundTest、RealSupportMixinTest；verify-support.ps1。

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

下一批先完成 P0 基线验证和 P1 依赖分析：逐方法对照 RoomCoreService、RoomCoreRuntimeManager、RoomServerModule、RoomPlayerRespawnService、RoomPlayerStateService、ClientReviveState；列出它们直接依赖的镜头/死亡原因/部署/边界接口，然后迁入真实依赖与入口。不能只搬新类，漏掉旧类方法和 prepared 阶段新增接口。

建议每次提交围绕一个可验收行为（入口与依赖、死亡派发、身体生命周期、救援/部署、清理、网络等）。本轮建立清单不改动业务代码，也没有执行源码迁移。

| 批次 | 状态 | 提交 | 自动验证 | 实机验收/剩余问题 |
| --- | --- | --- | --- | --- |
| 清单与证据 | 已建立 | 待提交 | 181 项路径/成品审计覆盖/资源/输入哈希核对 | 业务迁移未开始 |
| P0 | 待验证 | — | 未执行 | 基线启动与联网待执行 |
| P1 | 待迁移 | — | 未执行 | — |
| P2 | 待迁移 | — | 未执行 | — |
| P3 | 待迁移 | — | 未执行 | — |
| P4 | 待迁移 | — | 未执行 | — |
| P5 | 待迁移 | — | 未执行 | — |
| P6 | 待迁移 | — | 未执行 | — |
| P7 | 待迁移 | — | 未执行 | — |
