# ASM 与编译前临时接口迁移清单

按源码人工审阅归纳；本表覆盖 prepare、方法移植、patch、公共配置复制。audit 标记是补充证据，不能代替方法体审阅。

每个目标的完整源码路径为 org/mods/gd656conquest/<类>.java，迁入 src/main/java/。

| 批次 | 原类 | 方法/位置 | 迁移要求 |
| --- | --- | --- | --- |
| P1/P2 | [Gd656conquest](../../../gd656conquest/org/mods/gd656conquest/Gd656conquest.java) | `<init>` | 注册 DownedCameraConfig；迁入源码构造器的合法位置，不能照搬字节码前插位置 |
| P1 | [server/room/core/RoomCoreService](../../../gd656conquest/org/mods/gd656conquest/server/room/core/RoomCoreService.java) | `tryHandlePlayerDownedDamage; handlePlayerFatalState; handleDownedAction` | 新增倒地入口和显式 UUID 救援重载；迁移致命状态判定；消费出界死亡快照 |
| P1 | [server/room/entry/RoomCoreRuntimeManager](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCoreRuntimeManager.java) | `tryHandlePlayerDownedDamage; handleDownedAction` | 将 prepare 阶段新增的入口/重载写成真实 Java 方法；保留 coreService 空值分支 |
| P1/P2/P3 | [server/room/runtime/RoomPlayerRespawnService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `startReviveAttempt; onPlayerDeath; openManualDeathDeploy; downedDeployDelay; handleDownedAction; tryEnterDownedState; activateDownedState; finishDownedState; skipDownedStateForRedeploy; completeRevive; forceRedeployDeath; resolveActiveReviveContext; buildNearbyRescuerEntries; resolveNearestDownedRescueHint; sendDownedStart; sendDownedUpdate; showBeingRevived; showDownedCountdown; showKillerActionbar; tick` | 四个移植方法、显式救援目标、布娃娃/镜头生命周期、救援位置、普通死亡手动部署、快照、旧提示关闭；onPlayerDeath 最后被 transplant 覆盖，核对最终方法体 |
| P1/P2 | [server/room/runtime/RoomPlayerStateService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerStateService.java) | `normalizePlayerModelState; applyDownedState` | 清理镜头和布娃娃；维持倒地代理物理，核对两个 return 分支 |
| P1/P2/P3 | [server/room/entry/RoomServerModule](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomServerModule.java) | `onLivingDeath; onServerTick; onServerStopped; onPlayerLogout; onPlayerChangedDimension` | 完整死亡事件先派发，再接管房间流程；传递 DamageSource；统一击杀归属；tick 与退出/停服清理 |
| P0-P6 | [network/NetworkManager](../../../gd656conquest/org/mods/gd656conquest/network/NetworkManager.java) | `PROTOCOL_VERSION; init` | 成品协议 114；保留原数据包顺序并注册十个扩展包；packetId 递增十次；中间版本兼容性单独验证 |
| P1/P2 | [client/animation/ClientPlayerAnimatorDebugState](../../../gd656conquest/org/mods/gd656conquest/client/animation/ClientPlayerAnimatorDebugState.java) | `stopPreview; applyPreviewState` | 停止强制姿态；布娃娃就绪时不再套用旧预览身体姿态 |
| P1/P3 | [client/room/ClientDownedState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedState.java) | `killerCardRemainingTicks; requestReadyDeployment; tick; render; startOrUpdate; startDebugPreview; clear` | prepare 新增计时/单次部署接口；面板渲染、倒地快照、调试预览和长按门槛；clear 通知强制姿态清理 |
| P1 | [client/room/ClientReviveState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientReviveState.java) | `tick; resolveCandidateTarget` | 救援请求携带明确 UUID；目标资格和距离改为身体状态/位置；核对源码差异与 ClientHooks 最终处理 |
| P2/P3/P4 | [client/room/ClientDeathFlowState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDeathFlowState.java) | `currentDarknessAlpha; currentHurtFlashAlpha; applyDesaturation; setBoundaryPenaltyActive; clear; deathFilterKillerId; openDeploySequence; openDeployInstant; openRespawnWaitInstant; startRespawnTransition; startMatchEndTransition; adoptCameraPose; applyCameraOverride; hasActiveCameraOverride; shouldHideFirstPersonHands; enterDownedView` | 统一世界滤镜/死亡镜头所有权；保留部署交接顺序、原因横幅隐藏、边界激活与姿态清理；新增真实相机接口 |
| P2 | [client/room/ClientRoomLockState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientRoomLockState.java) | `shouldBlockLookInput` | 镜头拥有者优先阻断视角输入 |
| P5 | [common/item/AmmoBagItem](../../../gd656conquest/org/mods/gd656conquest/common/item/AmmoBagItem.java) | `use (m_7203_); interactLivingEntity (m_6880_)` | 原类没有 Git 差异，成品两个入口改为 SupportRuntime，必须纳入 |
| P5 | [common/item/MedicalBagItem](../../../gd656conquest/org/mods/gd656conquest/common/item/MedicalBagItem.java) | `use (m_7203_); interactLivingEntity (m_6880_); applyHealEffect` | 接入 SupportRuntime；关闭旧生命恢复效果 |
| P5 | [server/room/runtime/RoomPlayerLoadoutService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerLoadoutService.java) | `placeLoadoutItem; applyLoadout` | 初始包库存和部署结束实际弹药上限核对 |
| P5 | [server/room/runtime/RoomLoadoutGrantService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomLoadoutGrantService.java) | `grantConfiguredWeaponAmmoByExtraData` | 替换为两轮发弹；核对私有成员访问并改成合理源码接口 |
| P5 | [server/room/runtime/RoomDeathSupportDropService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomDeathSupportDropService.java) | `死亡补给实体生成处（原 addFreshEntity 调用）` | 原类没有 Git 差异；生成时标记死亡奖励包/寿命 |
| P5 | [server/player/state/PlayerItemInteractionService](../../../gd656conquest/org/mods/gd656conquest/server/player/state/PlayerItemInteractionService.java) | `handlePlayerItemToss; handleEntityItemPickup; handleMedicalBagPickup` | 新投放/拾取处理成功则停止旧路径；关闭旧医疗包效果 |
| P6 | [server/player/regen/PassiveRegenService](../../../gd656conquest/org/mods/gd656conquest/server/player/regen/PassiveRegenService.java) | `onPlayerTick; resetPlayer` | 按房间选配置、压制暂停、等待/间隔/回复量、玩家专属声音和清理 |
| P6 | [server/core/command/ConquestServerCommands](../../../gd656conquest/org/mods/gd656conquest/server/core/command/ConquestServerCommands.java) | `SERVER_CONFIG_IDS; normalizeServerConfigIdSafe; readConfigValue; suggestServerConfigValues; executeConfigGetSingle; executeConfigSet; executeConfigGetAll` | 原类没有 Git 差异；接入 NaturalRegenCommands 的新项、get/set/补全/全量显示 |
| P5 | [server/room/runtime/RoomItemReplenishService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomItemReplenishService.java) | `replenishConfiguredItem` | 原类没有 Git 差异；替换包库存恢复入口 |
| P5 | [server/room/runtime/RoomSupportBoxService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomSupportBoxService.java) | `processMedicalBoxAura; processAmmoBoxAura; tryUseNearbyAmmoBox; tryTriggerMedicalBoxForPlayer; setOwnerUuid 调用点; tryUse*; spawn*` | 新光环、权限/来源元数据、关闭旧交互；spawn 中容量常量改为医疗 100 / 弹药 20，该修改没有独立 hit 标记 |
| P4 | [server/loadout/domain/PlayerClassType](../../../gd656conquest/org/mods/gd656conquest/server/loadout/domain/PlayerClassType.java) | `枚举显示名初始化` | 只改两个中文显示名；内部键值、输入别名及兵种逻辑保留 |
| P4/P5/P6 | [server/room/config/RoomDefinitionScalarConfigEditor](../../../gd656conquest/org/mods/gd656conquest/server/room/config/RoomDefinitionScalarConfigEditor.java) | `parseNumberValue` | 接入 BoundaryConfigNumbers 范围解析，原解析分支仍保留 |
| P4 | [server/room/runtime/RoomRuleRuntimeService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomRuleRuntimeService.java) | `processBoundaryRule; clearBoundaryPenalty` | 统一玩家出界判定与清理；clear 方法没有独立 hit 标记 |
| P4 | [server/room/vehicle/VehicleBoundaryPenaltyService](../../../gd656conquest/org/mods/gd656conquest/server/room/vehicle/VehicleBoundaryPenaltyService.java) | `processAircraftBoundaryTimeouts; updateAircraftBoundaryState; handleBoundaryTimeout` | 关闭旧飞行载具计时；玩家当前载具走原生摧毁接口 |
| P4/P5/P6 | [server/data/roomdefinition/RoomDefinitionRepository](../../../gd656conquest/org/mods/gd656conquest/server/data/roomdefinition/RoomDefinitionRepository.java) | `RoomCommonConfig 构造复制调用` | 全局 preserveBoundaryConfig 扫描；核对从原配置拷贝的六个扩展字段 |
| P4/P5/P6 | [server/room/config/RoomDefinitionGroupConfigEditor](../../../gd656conquest/org/mods/gd656conquest/server/room/config/RoomDefinitionGroupConfigEditor.java) | `RoomCommonConfig 构造复制调用` | 全局 preserveBoundaryConfig 扫描；核对列表/分组编辑后字段保留 |
| P4/P5/P6 | [server/room/entry/RoomAdminConfigTemplateService](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomAdminConfigTemplateService.java) | `RoomCommonConfig 构造复制调用` | 全局 preserveBoundaryConfig 扫描；模板复制后字段保留 |
| P4/P5/P6 | [server/room/entry/RoomCommands](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCommands.java) | `RoomCommonConfig 构造复制调用` | 全局 preserveBoundaryConfig 扫描；武器配置等旧构造复制后字段保留 |

## 编译前接口和方法移植

- RoomCoreService、RoomCoreRuntimeManager、RoomPlayerRespawnService 的显式 UUID 动作重载：改为真实源码方法，沿调用链传参；评估是否仍需要 DownedReviveRequest 线程局部上下文。
- tryHandlePlayerDownedDamage、openManualDeathDeploy 的编译占位方法不能留空；downedDeployDelay 应调用原延迟计算。
- ClientDownedState 的 killerCardRemainingTicks / requestReadyDeployment 与 ClientDeathFlowState 的 deathFilterKillerId / adoptCameraPose 都要成为真实接口。
- extract-patch-methods.ps1 实际抽取四个方法：tryHandlePlayerDownedDamage、startReviveAttempt、onPlayerDeath、openManualDeathDeploy。末尾日志只提两个方法，不能据此遗漏后两者。
- Patch 声明壳、prepared API JAR、ASM 槽位/跳转、Forge 编译访问辅助不迁入运行模组；业务行为迁入源码后由 ForgeGradle 编译/重混淆。
- preserveBoundaryConfig 扫描全体原类，成品审计四处；保留 flightVehicleOutsideBoundaryDeathSeconds、flightBoundaryPaddingBlocks、allowEnemySupportResupply、suppressionConfig、medicalBoxDamageCooldownSeconds、naturalRegenConfig。核对构造复制的最终源码。
- 仍针对 Minecraft/TACZ/ragdollified/载具的 Mixin 单独保留并转换映射，迁移 GD 自身 ASM 接入不等于删除所有 Mixin。

## 静态 hit() 源码位置

自动抽取全部调用表达式；含工具通用动态键。循环内一次静态调用可能对应多个成品接入。

| BuildRagdollJar.java 行 | 表达式 |
| --- | --- |
| 43 | `String s){AUDIT.merge(s,1,Integer::sum` |
| 49 | `n.name+"."+methodName` |
| 96 | `n.name+".explicitTargetOverload"` |
| 122 | `"deploy.sharedCalculation"` |
| 138 | `"deploy.singlePressAction"` |
| 150 | `target.name+"."+methodName+".transplant"` |
| 183 | `"boundary.preserveConfig"` |
| 195 | `"support.bagItem"` |
| 198 | `"support.removeMedicalBagEffect"` |
| 205 | `"support.initialBagStock"` |
| 212 | `"support.deployedAmmoCeiling"` |
| 220 | `"support.twoPassInitialAmmo"` |
| 228 | `"support.deathReward"` |
| 236 | `"support.itemEvents"` |
| 238 | `"support.removePickupEffect"` |
| 254 | `"regen.amount"` |
| 270 | `"regen.privateSound"` |
| 273 | `"support.passiveRegen"` |
| 281 | `"regen.commandKeys"` |
| 291 | `"regen.commandHooks"` |
| 294 | `"regen.commandShowAll"` |
| 301 | `"support.recharge"` |
| 309 | `"support.boxAura"` |
| 315 | `"support.boxMetadata"` |
| 318 | `"support.boxThrowMetadata"` |
| 323 | `"support.removeMedicalBoxEffect"` |
| 324 | `"support.disableBoxInteraction"` |
| 336 | `"loadout.chineseDisplayNames"` |
| 353 | `"boundary.unifiedRule"` |
| 370 | `"boundary.removeAircraftRule"` |
| 375 | `"camera.registerServerConfig"` |
| 385 | `"boundary.fatalEligibility"` |
| 393 | `"deploy.moveRescueStatusToFooter"` |
| 396 | `"deploy.removeFloatingCountdown"` |
| 405 | `"deploy.publishSnapshot"` |
| 410 | `"killcard.removeOldSubtitle"` |
| 415 | `"cause.commandSkipsDowned"` |
| 419 | `"server.forcedRedeploySuccess"` |
| 428 | `"camera.startOrdinaryDeath"` |
| 431 | `"server.rescueDistance"` |
| 434 | `"camera.startSession"` |
| 435 | `"server.spawnLiveBody"` |
| 440 | `"server.finishBody"` |
| 440 | `"camera.endSession"` |
| 444 | `"server.reviveBody"` |
| 444 | `"camera.prepareRevive"` |
| 448 | `"camera.completeRevive"` |
| 457 | `"revive.removeSuccessBlind"` |
| 468 | `"deploy.manualDeathAction"` |
| 472 | `"deploy.manualDeathGate"` |
| 478 | `"server.proxyPhysics"` |
| 484 | `"cause.unifiedResolver"` |
| 490 | `"server.deferRoomDeath"` |
| 502 | `"server.tick"` |
| 502 | `"camera.tick"` |
| 503 | `"cause.tick"` |
| 511 | `"server.fatalExplosionSource"` |
| 513 | `"server.roomDeathTransition"` |
| 520 | `"network.protocol114"` |
| 524 | `"network.registerStatePacket"` |
| 537 | `"deploy.preserveHoldBeforeReady"` |
| 548 | `"killcard.replaceDownedRenderer"` |
| 554 | `"killcard.startDowned"` |
| 559 | `"client.clearDownedPose"` |
| 565 | `"client.explicitTarget"` |
| 568 | `"client.downedMarker"` |
| 571 | `"client.rescueDistance"` |
| 578 | `"filter.removeDeathDarkness"` |
| 582 | `"filter.suppressDeathFlash"` |
| 586 | `"filter.replaceDeathDesaturation"` |
| 594 | `"filter.legacyKillerId"` |
| 596 | `"camera.deathHandoff"` |
| 599 | `"camera.deathClear"` |
| 605 | `"cause.hideOnTransition"` |
| 612 | `"camera.adoptDeathPose"` |
| 620 | `"client.deployPoseCleanup"` |
| 625 | `"client.cameraOwnership"` |
| 627 | `"camera.hideHands"` |
| 633 | `"camera.blockLook"` |
| 697 | `"forge.publicConnectionAccess"` |

## 固定 .24 成品审计键

这些次数用于寻找旧实现，源码迁移后不要求保持相同字节码形状。

| 审计键 | 成品计数 |
| --- | --- |
| `boundary.fatalEligibility` | 1 |
| `boundary.preserveConfig` | 4 |
| `boundary.removeAircraftRule` | 1 |
| `boundary.unifiedRule` | 1 |
| `camera.adoptDeathPose` | 1 |
| `camera.blockLook` | 1 |
| `camera.completeRevive` | 1 |
| `camera.deathClear` | 2 |
| `camera.deathHandoff` | 3 |
| `camera.endSession` | 2 |
| `camera.hideHands` | 1 |
| `camera.prepareRevive` | 1 |
| `camera.registerServerConfig` | 1 |
| `camera.startOrdinaryDeath` | 1 |
| `camera.startSession` | 2 |
| `camera.tick` | 1 |
| `cause.commandSkipsDowned` | 1 |
| `cause.hideOnTransition` | 5 |
| `cause.tick` | 1 |
| `cause.unifiedResolver` | 1 |
| `client.cameraOwnership` | 1 |
| `client.clearDownedPose` | 1 |
| `client.deployPoseCleanup` | 4 |
| `client.downedMarker` | 1 |
| `client.explicitTarget` | 1 |
| `client.rescueDistance` | 1 |
| `deploy.manualDeathAction` | 1 |
| `deploy.manualDeathGate` | 1 |
| `deploy.moveRescueStatusToFooter` | 1 |
| `deploy.preserveHoldBeforeReady` | 1 |
| `deploy.publishSnapshot` | 2 |
| `deploy.removeFloatingCountdown` | 1 |
| `filter.legacyKillerId` | 1 |
| `filter.removeDeathDarkness` | 1 |
| `filter.replaceDeathDesaturation` | 1 |
| `filter.suppressDeathFlash` | 1 |
| `killcard.removeOldSubtitle` | 1 |
| `killcard.replaceDownedRenderer` | 1 |
| `killcard.startDowned` | 1 |
| `loadout.chineseDisplayNames` | 1 |
| `network.protocol114` | 3 |
| `network.registerStatePacket` | 1 |
| `org/mods/gd656conquest/Gd656conquest.<init>` | 1 |
| `org/mods/gd656conquest/client/animation/ClientPlayerAnimatorDebugState.applyPreviewState` | 1 |
| `org/mods/gd656conquest/client/animation/ClientPlayerAnimatorDebugState.stopPreview` | 1 |
| `org/mods/gd656conquest/client/room/ClientDeathFlowState.applyCameraOverride` | 1 |
| `org/mods/gd656conquest/client/room/ClientDeathFlowState.clear` | 2 |
| `org/mods/gd656conquest/client/room/ClientDeathFlowState.currentHurtFlashAlpha` | 1 |
| `org/mods/gd656conquest/client/room/ClientDeathFlowState.enterDownedView` | 1 |
| `org/mods/gd656conquest/client/room/ClientDeathFlowState.openDeployInstant` | 1 |
| `org/mods/gd656conquest/client/room/ClientDeathFlowState.openDeploySequence` | 1 |
| `org/mods/gd656conquest/client/room/ClientDeathFlowState.setBoundaryPenaltyActive` | 1 |
| `org/mods/gd656conquest/client/room/ClientDeathFlowState.startMatchEndTransition` | 1 |
| `org/mods/gd656conquest/client/room/ClientDeathFlowState.startRespawnTransition` | 1 |
| `org/mods/gd656conquest/client/room/ClientDownedState.tick` | 1 |
| `org/mods/gd656conquest/client/room/ClientRoomLockState.shouldBlockLookInput` | 1 |
| `org/mods/gd656conquest/server/core/command/ConquestServerCommands.executeConfigGetSingle` | 1 |
| `org/mods/gd656conquest/server/core/command/ConquestServerCommands.executeConfigSet` | 1 |
| `org/mods/gd656conquest/server/core/command/ConquestServerCommands.normalizeServerConfigIdSafe` | 1 |
| `org/mods/gd656conquest/server/core/command/ConquestServerCommands.readConfigValue` | 1 |
| `org/mods/gd656conquest/server/core/command/ConquestServerCommands.suggestServerConfigValues` | 1 |
| `org/mods/gd656conquest/server/player/regen/PassiveRegenService.onPlayerTick` | 1 |
| `org/mods/gd656conquest/server/player/regen/PassiveRegenService.resetPlayer` | 1 |
| `org/mods/gd656conquest/server/player/state/PlayerItemInteractionService.handleEntityItemPickup` | 1 |
| `org/mods/gd656conquest/server/player/state/PlayerItemInteractionService.handlePlayerItemToss` | 1 |
| `org/mods/gd656conquest/server/room/config/RoomDefinitionScalarConfigEditor.parseNumberValue` | 1 |
| `org/mods/gd656conquest/server/room/core/RoomCoreService.tryHandlePlayerDownedDamage.transplant` | 1 |
| `org/mods/gd656conquest/server/room/entry/RoomServerModule.onLivingDeath` | 1 |
| `org/mods/gd656conquest/server/room/entry/RoomServerModule.onPlayerChangedDimension` | 3 |
| `org/mods/gd656conquest/server/room/entry/RoomServerModule.onPlayerLogout` | 3 |
| `org/mods/gd656conquest/server/room/entry/RoomServerModule.onServerStopped` | 3 |
| `org/mods/gd656conquest/server/room/runtime/RoomPlayerLoadoutService.placeLoadoutItem` | 1 |
| `org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.onPlayerDeath.transplant` | 1 |
| `org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.openManualDeathDeploy.transplant` | 1 |
| `org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.startReviveAttempt.transplant` | 1 |
| `org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.tick` | 1 |
| `org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.tryEnterDownedState` | 1 |
| `org/mods/gd656conquest/server/room/runtime/RoomPlayerStateService.normalizePlayerModelState` | 2 |
| `regen.amount` | 1 |
| `regen.commandHooks` | 5 |
| `regen.commandKeys` | 1 |
| `regen.commandShowAll` | 1 |
| `regen.privateSound` | 1 |
| `revive.removeSuccessBlind` | 1 |
| `server.deferRoomDeath` | 1 |
| `server.fatalExplosionSource` | 1 |
| `server.finishBody` | 2 |
| `server.forcedRedeploySuccess` | 2 |
| `server.proxyPhysics` | 2 |
| `server.rescueDistance` | 3 |
| `server.reviveBody` | 1 |
| `server.roomDeathTransition` | 1 |
| `server.spawnLiveBody` | 2 |
| `server.tick` | 1 |
| `support.bagItem` | 4 |
| `support.boxAura` | 2 |
| `support.boxMetadata` | 4 |
| `support.boxThrowMetadata` | 2 |
| `support.deathReward` | 1 |
| `support.deployedAmmoCeiling` | 1 |
| `support.disableBoxInteraction` | 1 |
| `support.initialBagStock` | 1 |
| `support.itemEvents` | 2 |
| `support.passiveRegen` | 1 |
| `support.recharge` | 1 |
| `support.removeMedicalBagEffect` | 1 |
| `support.removeMedicalBoxEffect` | 1 |
| `support.removePickupEffect` | 1 |
| `support.twoPassInitialAmmo` | 1 |
