# P1 静态依赖证据

人工结论和实际实施范围见 [第一批依赖分析](p1-dependencies.md)。本文件由 tools/migration_p1_dependencies.py 生成。

保守词法扫描 imports/全限定名/同包及通配导入类名；只沿 151 个盘点过的迁移 Java 类追踪；非编译器调用图，不解析反射字符串、Mixin 配置和全部字段接收者类型。

六个起点得到 87 个整类依赖候选。这不是必须一次迁移的文件数量，也不是经过编译证明的最小依赖集合。

## 六个起点的直接候选

| 起点 | 迁移类依赖候选 |
| --- | --- |
| `RoomCoreService` | NetworkManager, RoomDeathFlowPacket, RoomCoreRuntimeManager, RoomDeathCauseService, RoomDeathSupportDropService, RoomItemReplenishService, RoomPlayerLoadoutService, RoomPlayerRespawnService, RoomPlayerStateService, RoomRuleRuntimeService, RoomSupportBoxService, UnifiedBoundaryService |
| `RoomCoreRuntimeManager` | NetworkManager, RoomCoreService, RoomPlayerLoadoutService |
| `RoomServerModule` | DownedRagdollBridge, DownedRagdollEvents, DownedViewService, RoomCoreRuntimeManager, RoomDeathCauseService |
| `RoomPlayerRespawnService` | NetworkManager, RoomDeathFlowPacket, DownedRagdollBridge, DownedRagdollEvents, DownedViewService, PlayerClassType, DownedDeployService, ManualDeathDeployService, RoomCoreRuntimeManager, RoomDeathCauseService, RoomPlayerLoadoutService, RoomPlayerStateService |
| `RoomPlayerStateService` | DownedRagdollBridge, DownedViewService |
| `ClientReviveState` | ClientPlayerAnimatorDebugState, ClientDownedRagdollState, ClientDownedState, NetworkManager, RoomDownedActionPacket |

## 逐方法入口核对

参数数量用于定位重载，不代表完整 Java 类型检查。direct_class_tokens 只记录方法体中出现的类名；字段接收者调用由人工表补充。反编译方法体不完整时使用下一声明定位边界，并明确标记。

| 来源及行 | 签名 | 原始项目入口 | 方法体出现的迁移类 | 方法边界 |
| --- | --- | --- | --- | --- |
| [RoomCoreService:666](../../../gd656conquest/org/mods/gd656conquest/server/room/core/RoomCoreService.java) | `public boolean handleDownedAction(ServerPlayer player, String action, long gameTick)` | 同名同参数数量入口已有；须合并方法体 |  | balanced |
| [RoomCoreService:670](../../../gd656conquest/org/mods/gd656conquest/server/room/core/RoomCoreService.java) | `public boolean handleDownedAction(ServerPlayer player, String action, UUID requestedTargetId, long gameTick)` | 该参数数量入口缺失；新增方法/重载 |  | balanced |
| [RoomCoreService:716](../../../gd656conquest/org/mods/gd656conquest/server/room/core/RoomCoreService.java) | `public boolean tryHandlePlayerDownedDamage(ServerPlayer player, RoomPosition deathPosition, int killerEntityId, long gameTick)` | 该参数数量入口缺失；新增方法/重载 | UnifiedBoundaryService | balanced |
| [RoomCoreService:730](../../../gd656conquest/org/mods/gd656conquest/server/room/core/RoomCoreService.java) | `public boolean handlePlayerDeathEvent(ServerPlayer player, RoomPosition deathPosition, int killerEntityId, long gameTick)` | 同名同参数数量入口已有；须合并方法体 |  | balanced |
| [RoomCoreService:747](../../../gd656conquest/org/mods/gd656conquest/server/room/core/RoomCoreService.java) | `private boolean handlePlayerFatalState(ServerPlayer player, RoomPosition deathPosition, int killerEntityId, long gameTick)` | 同名同参数数量入口已有；须合并方法体 | RoomDeathCauseService | balanced |
| [RoomCoreRuntimeManager:320](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCoreRuntimeManager.java) | `public boolean handleDownedAction(ServerPlayer player, String action, long gameTick)` | 同名同参数数量入口已有；须合并方法体 |  | balanced |
| [RoomCoreRuntimeManager:324](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCoreRuntimeManager.java) | `public boolean handleDownedAction(ServerPlayer player, String action, UUID requestedTargetId, long gameTick)` | 该参数数量入口缺失；新增方法/重载 |  | balanced |
| [RoomCoreRuntimeManager:338](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCoreRuntimeManager.java) | `public boolean tryHandlePlayerDownedDamage(ServerPlayer player, RoomPosition deathPosition, int killerEntityId, long gameTick)` | 该参数数量入口缺失；新增方法/重载 |  | balanced |
| [RoomCoreRuntimeManager:342](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCoreRuntimeManager.java) | `public boolean handlePlayerDeathEvent(ServerPlayer player, RoomPosition deathPosition, int killerEntityId, long gameTick)` | 同名同参数数量入口已有；须合并方法体 |  | balanced |
| [RoomServerModule:27](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomServerModule.java) | `public void onServerTick(MinecraftServer server, long gameTick)` | 同名同参数数量入口已有；须合并方法体 | DownedRagdollBridge, DownedViewService, RoomCoreRuntimeManager, RoomDeathCauseService | balanced |
| [RoomServerModule:36](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomServerModule.java) | `public void onServerStopped(MinecraftServer server)` | 同名同参数数量入口已有；须合并方法体 | DownedRagdollBridge, DownedViewService, RoomCoreRuntimeManager, RoomDeathCauseService | balanced |
| [RoomServerModule:46](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomServerModule.java) | `public void onPlayerLogout(ServerPlayer player)` | 同名同参数数量入口已有；须合并方法体 | DownedRagdollBridge, DownedViewService, RoomCoreRuntimeManager, RoomDeathCauseService | balanced |
| [RoomServerModule:57](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomServerModule.java) | `public void onPlayerChangedDimension(ServerPlayer player)` | 同名同参数数量入口已有；须合并方法体 | DownedRagdollBridge, DownedViewService, RoomDeathCauseService | balanced |
| [RoomServerModule:83](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomServerModule.java) | `public void onLivingDeath(LivingDeathEvent event)` | 同名同参数数量入口已有；须合并方法体 | DownedRagdollEvents, RoomCoreRuntimeManager, RoomDeathCauseService | balanced |
| [RoomPlayerRespawnService:151](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `public void onPlayerDeath(RoomDefinition room, RunningRuntimeState state, UUID playerId, RoomPosition deathPosition, int killerEntityId, long gameTick)` | 同名同参数数量入口已有；须合并方法体 | NetworkManager, RoomDeathFlowPacket, DownedViewService, ManualDeathDeployService | balanced |
| [RoomPlayerRespawnService:172](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `public void openManualDeathDeploy(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick, int delay)` | 该参数数量入口缺失；新增方法/重载 |  | balanced |
| [RoomPlayerRespawnService:197](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `public boolean tryEnterDownedState(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, RoomPosition deathPosition, int killerEntityId, long gameTick)` | 同名同参数数量入口已有；须合并方法体 | NetworkManager, RoomDeathFlowPacket, DownedRagdollBridge, DownedViewService, RoomDeathCauseService | balanced |
| [RoomPlayerRespawnService:230](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `public boolean handleDownedAction(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, String action, long gameTick)` | 同名同参数数量入口已有；须合并方法体 |  | balanced |
| [RoomPlayerRespawnService:234](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `public boolean handleDownedAction(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, String action, UUID requestedTargetId, long gameTick)` | 该参数数量入口缺失；新增方法/重载 | ManualDeathDeployService | balanced |
| [RoomPlayerRespawnService:346](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `private boolean forceRedeployDeath(ServerPlayer player)` | 同名同参数数量入口已有；须合并方法体 | DownedRagdollEvents | balanced |
| [RoomPlayerRespawnService:592](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `public void tick(RoomDefinition room, RunningRuntimeState state, long gameTick)` | 同名同参数数量入口已有；须合并方法体 | ManualDeathDeployService | balanced |
| [RoomPlayerRespawnService:918](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `private boolean startReviveAttempt(RoomDefinition room, RunningRuntimeState state, ServerPlayer reviver, UUID requestedTargetId, long gameTick)` | 该参数数量入口缺失；新增方法/重载 | PlayerClassType | balanced |
| [RoomPlayerRespawnService:965](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `private ActiveReviveContext resolveActiveReviveContext(RunningRuntimeState state, ServerPlayer reviver, ServerPlayer target)` | 同名同参数数量入口已有；须合并方法体 | DownedRagdollBridge, PlayerClassType | balanced |
| [RoomPlayerRespawnService:989](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `private void activateDownedState(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick)` | 同名同参数数量入口已有；须合并方法体 | DownedRagdollBridge, DownedViewService | balanced |
| [RoomPlayerRespawnService:1106](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `private void finishDownedState(RoomDefinition room, RunningRuntimeState state, UUID playerId, long gameTick)` | 同名同参数数量入口已有；须合并方法体 | NetworkManager, DownedRagdollBridge, DownedViewService | balanced |
| [RoomPlayerRespawnService:1132](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `private void skipDownedStateForRedeploy(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick)` | 同名同参数数量入口已有；须合并方法体 | NetworkManager, RoomDeathFlowPacket, DownedRagdollBridge, DownedViewService | balanced |
| [RoomPlayerRespawnService:1477](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `private void sendDownedStart(ServerPlayer player, RunningRuntimeState state, RoomPosition deathPosition, int killerEntityId, long remainTicks)` | 同名同参数数量入口已有；须合并方法体 | NetworkManager, DownedDeployService | balanced |
| [RoomPlayerRespawnService:1495](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `private void sendDownedUpdate(ServerPlayer player, RunningRuntimeState state, int remainTicks)` | 同名同参数数量入口已有；须合并方法体 | NetworkManager, DownedDeployService | balanced |
| [RoomPlayerRespawnService:1529](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `private List<RoomDownedStatePacket.RescuerEntry> buildNearbyRescuerEntries(RunningRuntimeState state, ServerPlayer downedPlayer)` | 同名同参数数量入口已有；须合并方法体 |  | next-declaration-fallback |
| [RoomPlayerRespawnService:1621](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `private DownedRescueHint resolveNearestDownedRescueHint(RunningRuntimeState state, ServerPlayer viewer)` | 同名同参数数量入口已有；须合并方法体 | DownedRagdollBridge, PlayerClassType | balanced |
| [RoomPlayerRespawnService:1707](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `private void completeRevive(RoomDefinition room, RunningRuntimeState state, ServerPlayer reviver, ServerPlayer target, long gameTick)` | 同名同参数数量入口已有；须合并方法体 | NetworkManager, RoomDeathFlowPacket, DownedRagdollBridge, DownedViewService, PlayerClassType | balanced |
| [RoomPlayerRespawnService:2283](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `public int downedDeployDelay(RoomDefinition room, RunningRuntimeState state, UUID playerId, long gameTick)` | 该参数数量入口缺失；新增方法/重载 |  | balanced |
| [RoomPlayerStateService:145](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerStateService.java) | `public void normalizePlayerModelState(ServerPlayer player)` | 同名同参数数量入口已有；须合并方法体 | DownedRagdollBridge, DownedViewService | balanced |
| [RoomPlayerStateService:196](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerStateService.java) | `public void applyDownedState(RoomDefinition room, ServerPlayer player)` | 同名同参数数量入口已有；须合并方法体 | DownedRagdollBridge | balanced |
| [ClientReviveState:24](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientReviveState.java) | `public static void tick()` | 同名同参数数量入口已有；须合并方法体 | ClientDownedState, NetworkManager, RoomDownedActionPacket | balanced |
| [ClientReviveState:77](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientReviveState.java) | `private static RescueCandidate resolveCandidateTarget(Minecraft minecraft, LocalPlayer self)` | 同名同参数数量入口已有；须合并方法体 | ClientPlayerAnimatorDebugState, ClientDownedRagdollState | balanced |
| [RoomDownedActionPacket:22](../../../gd656conquest/org/mods/gd656conquest/network/packet/RoomDownedActionPacket.java) | `public static RoomDownedActionPacket reviveStart()` | 同名同参数数量入口已有；须合并方法体 |  | balanced |
| [RoomDownedActionPacket:23](../../../gd656conquest/org/mods/gd656conquest/network/packet/RoomDownedActionPacket.java) | `public static RoomDownedActionPacket reviveStart(UUID targetId)` | 该参数数量入口缺失；新增方法/重载 |  | balanced |
| [RoomDownedActionPacket:27](../../../gd656conquest/org/mods/gd656conquest/network/packet/RoomDownedActionPacket.java) | `public static void encode(RoomDownedActionPacket message, FriendlyByteBuf buffer)` | 同名同参数数量入口已有；须合并方法体 |  | balanced |
| [RoomDownedActionPacket:32](../../../gd656conquest/org/mods/gd656conquest/network/packet/RoomDownedActionPacket.java) | `public static RoomDownedActionPacket decode(FriendlyByteBuf buffer)` | 同名同参数数量入口已有；须合并方法体 |  | balanced |
| [RoomDownedActionPacket:36](../../../gd656conquest/org/mods/gd656conquest/network/packet/RoomDownedActionPacket.java) | `public static void handle(RoomDownedActionPacket message, Supplier<NetworkEvent.Context> supplier)` | 同名同参数数量入口已有；须合并方法体 | RoomCoreRuntimeManager | balanced |
| [ClientDownedState:309](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedState.java) | `public static int killerCardRemainingTicks()` | 该参数数量入口缺失；新增方法/重载 |  | balanced |
| [ClientDownedState:311](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedState.java) | `public static void requestReadyDeployment()` | 该参数数量入口缺失；新增方法/重载 | NetworkManager, RoomDownedActionPacket | balanced |
| [ClientDeathFlowState:749](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDeathFlowState.java) | `public static void adoptCameraPose(double x,double y,double z,float yaw,float pitch)` | 该参数数量入口缺失；新增方法/重载 |  | balanced |
| [ClientDeathFlowState:911](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDeathFlowState.java) | `public static int deathFilterKillerId()` | 该参数数量入口缺失；新增方法/重载 |  | balanced |

## 整类候选与一条最短来源链

本表用于识别整类复制带来的范围扩张，不能据此认定同批必须迁入。

| 来源 | 主要批次 | 目标已有 | 候选依赖链 |
| --- | --- | --- | --- |
| [ClientPlayerAnimatorDebugState](../../../gd656conquest/org/mods/gd656conquest/client/animation/ClientPlayerAnimatorDebugState.java) | P1 | 是 | ClientReviveState → ClientPlayerAnimatorDebugState |
| [BoundaryPresentation](../../../gd656conquest/org/mods/gd656conquest/client/room/BoundaryPresentation.java) | P4 | 否 | RoomCoreService → RoomDeathFlowPacket → ClientBoundaryState → BoundaryPresentation |
| [ClientBoundaryState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientBoundaryState.java) | P4 | 否 | RoomCoreService → RoomDeathFlowPacket → ClientBoundaryState |
| [ClientDeathCauseState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDeathCauseState.java) | P3 | 否 | RoomCoreService → RoomDeathFlowPacket → ClientDeathFlowState → ClientDeathCauseState |
| [ClientDeathFilter](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDeathFilter.java) | P2 | 否 | RoomCoreService → RoomDeathFlowPacket → ClientBoundaryState → ClientDeathFilter |
| [ClientDeathFlowState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDeathFlowState.java) | P2 | 是 | RoomCoreService → RoomDeathFlowPacket → ClientDeathFlowState |
| [ClientDownedCamera](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedCamera.java) | P2 | 否 | ClientReviveState → ClientDownedRagdollState → ClientDownedCamera |
| [ClientDownedDeployState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedDeployState.java) | P3 | 否 | ClientReviveState → ClientDownedState → ClientDownedDeployState |
| [ClientDownedRagdollState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedRagdollState.java) | P1 | 否 | ClientReviveState → ClientDownedRagdollState |
| [ClientDownedState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedState.java) | P1 | 是 | ClientReviveState → ClientDownedState |
| [ClientKillerCardState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientKillerCardState.java) | P3 | 否 | ClientReviveState → ClientDownedState → ClientKillerCardState |
| [ClientManualDeathDeployState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientManualDeathDeployState.java) | P3 | 否 | RoomPlayerRespawnService → DownedDeployService → DownedDeployPacket → ClientManualDeathDeployState |
| [ClientRagdollSubscriptions](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientRagdollSubscriptions.java) | P1 | 否 | RoomCoreService → NetworkManager → DownedRagdollNetwork → RagdollCatalogPacket → ClientRagdollSubscriptions |
| [ClientReviveEffect](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientReviveEffect.java) | P2 | 否 | RoomCoreService → RoomDeathFlowPacket → ClientBoundaryState → ClientDeathFilter → ClientReviveEffect |
| [ClientReviveState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientReviveState.java) | P1 | 是 | ClientReviveState |
| [ClientRoomLockState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientRoomLockState.java) | P2 | 是 | ClientReviveState → ClientDownedState → ClientRoomLockState |
| [ClientSuppressionState](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientSuppressionState.java) | P6 | 否 | RoomCoreService → RoomDeathFlowPacket → ClientBoundaryState → ClientSuppressionState |
| [DeathCauseBanner](../../../gd656conquest/org/mods/gd656conquest/client/room/DeathCauseBanner.java) | P3 | 否 | RoomCoreService → RoomDeathFlowPacket → ClientDeathFlowState → ClientDeathCauseState → DeathCauseBanner |
| [DeathFilterTransition](../../../gd656conquest/org/mods/gd656conquest/client/room/DeathFilterTransition.java) | P2 | 否 | RoomCoreService → RoomDeathFlowPacket → ClientBoundaryState → ClientDeathFilter → DeathFilterTransition |
| [DownedCameraMath](../../../gd656conquest/org/mods/gd656conquest/client/room/DownedCameraMath.java) | P2 | 否 | RoomCoreService → RoomDeathFlowPacket → ClientBoundaryState → ClientDeathFilter → DeathFilterTransition → DownedCameraMath |
| [DownedRagdollClientHooks](../../../gd656conquest/org/mods/gd656conquest/client/room/DownedRagdollClientHooks.java) | P1 | 否 | ClientReviveState → ClientPlayerAnimatorDebugState → DownedRagdollClientHooks |
| [KillerCardLayout](../../../gd656conquest/org/mods/gd656conquest/client/room/KillerCardLayout.java) | P3 | 否 | ClientReviveState → ClientDownedState → ClientKillerCardState → KillerCardLayout |
| [KillerCardModel](../../../gd656conquest/org/mods/gd656conquest/client/room/KillerCardModel.java) | P3 | 否 | ClientReviveState → ClientDownedState → ClientKillerCardState → KillerCardModel |
| [RagdollVisibilityMath](../../../gd656conquest/org/mods/gd656conquest/client/room/RagdollVisibilityMath.java) | P1 | 否 | RoomCoreService → NetworkManager → DownedRagdollNetwork → RagdollCatalogPacket → ClientRagdollSubscriptions → RagdollVisibilityMath |
| [ReviveTransition](../../../gd656conquest/org/mods/gd656conquest/client/room/ReviveTransition.java) | P2 | 否 | ClientReviveState → ClientDownedRagdollState → ClientDownedCamera → ReviveTransition |
| [DeathCause](../../../gd656conquest/org/mods/gd656conquest/common/player/DeathCause.java) | P3 | 否 | RoomCoreService → RoomDeathCauseService → DeathCause |
| [DownedCameraMode](../../../gd656conquest/org/mods/gd656conquest/common/player/DownedCameraMode.java) | P2 | 否 | RoomServerModule → DownedViewService → DownedCameraMode |
| [DownedDeployModel](../../../gd656conquest/org/mods/gd656conquest/common/player/DownedDeployModel.java) | P3 | 否 | ClientReviveState → ClientDownedState → ClientDownedDeployState → DownedDeployModel |
| [KillerCardInfo](../../../gd656conquest/org/mods/gd656conquest/common/player/KillerCardInfo.java) | P3 | 否 | RoomCoreService → RoomDeathCauseService → KillerIntelService → KillerCardInfo |
| [ManualDeathDeployModel](../../../gd656conquest/org/mods/gd656conquest/common/player/ManualDeathDeployModel.java) | P3 | 否 | RoomPlayerRespawnService → ManualDeathDeployService → ManualDeathDeployModel |
| [RevivedPronePose](../../../gd656conquest/org/mods/gd656conquest/common/player/RevivedPronePose.java) | P2 | 否 | RoomServerModule → DownedViewService → RevivedPronePose |
| [DeathPostChainAccessor](../../../gd656conquest/org/mods/gd656conquest/mixin/client/DeathPostChainAccessor.java) | P2 | 否 | RoomCoreService → RoomDeathFlowPacket → ClientBoundaryState → DeathPostChainAccessor |
| [DownedCameraAccessor](../../../gd656conquest/org/mods/gd656conquest/mixin/client/DownedCameraAccessor.java) | P2 | 否 | ClientReviveState → ClientDownedRagdollState → ClientDownedCamera → DownedCameraAccessor |
| [NetworkManager](../../../gd656conquest/org/mods/gd656conquest/network/NetworkManager.java) | SHARED | 是 | RoomCoreService → NetworkManager |
| [DeathCausePacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/DeathCausePacket.java) | P3 | 否 | RoomCoreService → RoomDeathCauseService → DeathCausePacket |
| [DownedDeployPacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/DownedDeployPacket.java) | P3 | 否 | RoomPlayerRespawnService → DownedDeployService → DownedDeployPacket |
| [DownedRagdollStatePacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/DownedRagdollStatePacket.java) | P1 | 否 | RoomCoreService → NetworkManager → DownedRagdollStatePacket |
| [DownedViewPacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/DownedViewPacket.java) | P2 | 否 | RoomServerModule → DownedViewService → DownedViewPacket |
| [KillerCardPacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/KillerCardPacket.java) | P3 | 否 | RoomCoreService → NetworkManager → DownedRagdollNetwork → KillerCardPacket |
| [RagdollCatalogPacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/RagdollCatalogPacket.java) | P1 | 否 | RoomCoreService → NetworkManager → DownedRagdollNetwork → RagdollCatalogPacket |
| [RagdollObservationPacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/RagdollObservationPacket.java) | P1 | 否 | RoomCoreService → NetworkManager → DownedRagdollNetwork → RagdollObservationPacket |
| [RagdollSubscribePacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/RagdollSubscribePacket.java) | P1 | 否 | RoomCoreService → NetworkManager → DownedRagdollNetwork → RagdollSubscribePacket |
| [RevivedProneReleasePacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/RevivedProneReleasePacket.java) | P2 | 否 | RoomCoreService → NetworkManager → DownedRagdollNetwork → RevivedProneReleasePacket |
| [RoomDeathFlowPacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/RoomDeathFlowPacket.java) | SHARED | 是 | RoomCoreService → RoomDeathFlowPacket |
| [RoomDownedActionPacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/RoomDownedActionPacket.java) | SHARED | 是 | ClientReviveState → RoomDownedActionPacket |
| [SuppressionPacket](../../../gd656conquest/org/mods/gd656conquest/network/packet/SuppressionPacket.java) | P6 | 否 | RoomCoreService → NetworkManager → DownedRagdollNetwork → SuppressionPacket |
| [DownedCameraConfig](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedCameraConfig.java) | P2 | 否 | RoomServerModule → DownedViewService → DownedCameraConfig |
| [DownedRagdollBridge](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollBridge.java) | P1 | 否 | RoomServerModule → DownedRagdollBridge |
| [DownedRagdollEvents](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollEvents.java) | P1 | 否 | RoomServerModule → DownedRagdollEvents |
| [DownedRagdollNetwork](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollNetwork.java) | P1 | 否 | RoomCoreService → NetworkManager → DownedRagdollNetwork |
| [DownedViewService](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedViewService.java) | P2 | 否 | RoomServerModule → DownedViewService |
| [RagdollHitCacheGuard](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollHitCacheGuard.java) | P1 | 否 | RoomServerModule → DownedRagdollBridge → RagdollHitCacheGuard |
| [RagdollPacketScope](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollPacketScope.java) | P1 | 否 | RoomServerModule → DownedRagdollBridge → RagdollSyncScope → RagdollPacketScope |
| [RagdollRecordView](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollRecordView.java) | P1 | 否 | RoomServerModule → DownedRagdollBridge → RagdollSubscriptions → RagdollRecordView |
| [RagdollSubscriptionPolicy](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollSubscriptionPolicy.java) | P1 | 否 | RoomServerModule → DownedRagdollBridge → RagdollSubscriptionPolicy |
| [RagdollSubscriptions](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollSubscriptions.java) | P1 | 否 | RoomServerModule → DownedRagdollBridge → RagdollSubscriptions |
| [RagdollSyncScope](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollSyncScope.java) | P1 | 否 | RoomServerModule → DownedRagdollBridge → RagdollSyncScope |
| [BoundaryVehicleDestruction](../../../gd656conquest/org/mods/gd656conquest/server/compat/vehicle/BoundaryVehicleDestruction.java) | P4 | 否 | RoomCoreService → RoomDeathCauseService → BoundaryVehicleDestruction |
| [PlayerClassType](../../../gd656conquest/org/mods/gd656conquest/server/loadout/domain/PlayerClassType.java) | P4 | 是 | RoomPlayerRespawnService → PlayerClassType |
| [RoomCoreService](../../../gd656conquest/org/mods/gd656conquest/server/room/core/RoomCoreService.java) | P1 | 是 | RoomCoreService |
| [DeathCauseClassifier](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/DeathCauseClassifier.java) | P3 | 否 | RoomCoreService → RoomDeathCauseService → DeathCauseClassifier |
| [DownedDeployService](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/DownedDeployService.java) | P3 | 否 | RoomPlayerRespawnService → DownedDeployService |
| [FatalHitMetadata](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/FatalHitMetadata.java) | P3 | 否 | RoomCoreService → RoomDeathCauseService → KillerIntelService → FatalHitMetadata |
| [KillWeaponResolver](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/KillWeaponResolver.java) | P3 | 否 | RoomCoreService → RoomDeathCauseService → KillerIntelService → KillWeaponResolver |
| [KillerIntelService](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/KillerIntelService.java) | P3 | 否 | RoomCoreService → RoomDeathCauseService → KillerIntelService |
| [KilliconFireOwnership](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/KilliconFireOwnership.java) | P3 | 否 | RoomCoreService → RoomDeathCauseService → KilliconFireOwnership |
| [ManualDeathDeployService](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/ManualDeathDeployService.java) | P3 | 否 | RoomPlayerRespawnService → ManualDeathDeployService |
| [MatchDuelStats](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/MatchDuelStats.java) | P3 | 否 | RoomCoreService → RoomDeathCauseService → KillerIntelService → MatchDuelStats |
| [RoomCoreRuntimeManager](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCoreRuntimeManager.java) | P1 | 是 | RoomCoreRuntimeManager |
| [RoomDeathCauseService](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomDeathCauseService.java) | P3 | 否 | RoomCoreService → RoomDeathCauseService |
| [RoomServerModule](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomServerModule.java) | P1 | 是 | RoomServerModule |
| [RoomCommonConfig](../../../gd656conquest/org/mods/gd656conquest/server/room/model/RoomCommonConfig.java) | SHARED | 是 | RoomCoreService → UnifiedBoundaryService → RoomCommonConfig |
| [RoomNaturalRegenConfig](../../../gd656conquest/org/mods/gd656conquest/server/room/model/RoomNaturalRegenConfig.java) | P6 | 否 | RoomCoreService → UnifiedBoundaryService → RoomCommonConfig → RoomNaturalRegenConfig |
| [RoomSuppressionConfig](../../../gd656conquest/org/mods/gd656conquest/server/room/model/RoomSuppressionConfig.java) | P6 | 否 | RoomCoreService → UnifiedBoundaryService → RoomCommonConfig → RoomSuppressionConfig |
| [BoundaryCountdown](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/BoundaryCountdown.java) | P4 | 否 | RoomCoreService → UnifiedBoundaryService → BoundaryCountdown |
| [FlightBoundaryGeometry](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/FlightBoundaryGeometry.java) | P4 | 否 | RoomCoreService → UnifiedBoundaryService → FlightBoundaryGeometry |
| [RoomDeathSupportDropService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomDeathSupportDropService.java) | P5 | 是 | RoomCoreService → RoomDeathSupportDropService |
| [RoomItemReplenishService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomItemReplenishService.java) | P5 | 是 | RoomCoreService → RoomItemReplenishService |
| [RoomLoadoutGrantService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomLoadoutGrantService.java) | P5 | 是 | RoomCoreService → RoomPlayerLoadoutService → RoomLoadoutGrantService |
| [RoomPlayerLoadoutService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerLoadoutService.java) | P5 | 是 | RoomCoreService → RoomPlayerLoadoutService |
| [RoomPlayerRespawnService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | P1 | 是 | RoomPlayerRespawnService |
| [RoomPlayerStateService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerStateService.java) | P1 | 是 | RoomPlayerStateService |
| [RoomRuleRuntimeService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomRuleRuntimeService.java) | P4 | 是 | RoomCoreService → RoomRuleRuntimeService |
| [RoomSupportBoxService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomSupportBoxService.java) | P5 | 是 | RoomCoreService → RoomSupportBoxService |
| [UnifiedBoundaryService](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/UnifiedBoundaryService.java) | P4 | 否 | RoomCoreService → UnifiedBoundaryService |
| [VehicleBoundaryPenaltyService](../../../gd656conquest/org/mods/gd656conquest/server/room/vehicle/VehicleBoundaryPenaltyService.java) | P4 | 是 | RoomCoreService → RoomRuleRuntimeService → VehicleBoundaryPenaltyService |
| [SupportAmmoGrant](../../../gd656conquest/org/mods/gd656conquest/server/support/SupportAmmoGrant.java) | P5 | 否 | RoomCoreService → RoomPlayerLoadoutService → SupportAmmoGrant |
