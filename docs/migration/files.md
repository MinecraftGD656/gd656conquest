# 文件迁移明细（自动生成证据）

入口与完成状态见 ../../MIGRATION.md。这里的 P 编号表示主要归属，不是完整依赖；SHARED 必须分批处理。

快照来源：反编译原版 → 最后提交的 Git 差异，加选择性编译列表、ASM 目标及 .24 成品类审计的并集。

Git 差异 177 项，完整文件清单 181 项。目标存在只表示已有同路径文件，不能据此认定功能已迁移。

A=新增；M=修改；UNCHANGED=Git 无差异但成品/接入需要迁移。编译/ASM/审计分别记录三种独立证据。

## P1

| 来源 | 目标 | Git | 编译/ASM/审计 | 目标已有 |
| --- | --- | --- | --- | --- |
| [gd656conquest/org/mods/gd656conquest/client/animation/ClientPlayerAnimatorDebugState.java](../../../gd656conquest/org/mods/gd656conquest/client/animation/ClientPlayerAnimatorDebugState.java) | `src/main/java/org/mods/gd656conquest/client/animation/ClientPlayerAnimatorDebugState.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientDownedRagdollState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedRagdollState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientDownedRagdollState.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientDownedState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientDownedState.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientRagdollSubscriptions.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientRagdollSubscriptions.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientRagdollSubscriptions.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientReviveState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientReviveState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientReviveState.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/client/room/DownedRagdollClientHooks.java](../../../gd656conquest/org/mods/gd656conquest/client/room/DownedRagdollClientHooks.java) | `src/main/java/org/mods/gd656conquest/client/room/DownedRagdollClientHooks.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/RagdollPlaybackGuard.java](../../../gd656conquest/org/mods/gd656conquest/client/room/RagdollPlaybackGuard.java) | `src/main/java/org/mods/gd656conquest/client/room/RagdollPlaybackGuard.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/RagdollVisibilityMath.java](../../../gd656conquest/org/mods/gd656conquest/client/room/RagdollVisibilityMath.java) | `src/main/java/org/mods/gd656conquest/client/room/RagdollVisibilityMath.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/compat/ragdoll/ClientRagdollHitCacheMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/compat/ragdoll/ClientRagdollHitCacheMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/compat/ragdoll/ClientRagdollHitCacheMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/compat/ragdoll/ClientRagdollReplacementMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/compat/ragdoll/ClientRagdollReplacementMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/compat/ragdoll/ClientRagdollReplacementMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/compat/ragdoll/RagdollPlaybackMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/compat/ragdoll/RagdollPlaybackMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/compat/ragdoll/RagdollPlaybackMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/compat/ragdoll/RagdollSpawnMomentumMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/compat/ragdoll/RagdollSpawnMomentumMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/compat/ragdoll/RagdollSpawnMomentumMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/compat/ragdoll/RagdollVisibilityMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/compat/ragdoll/RagdollVisibilityMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/compat/ragdoll/RagdollVisibilityMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/compat/ragdoll/RagdollDeathMomentumMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/compat/ragdoll/RagdollDeathMomentumMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/compat/ragdoll/RagdollDeathMomentumMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/compat/ragdoll/RagdollPacketScopeMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/compat/ragdoll/RagdollPacketScopeMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/compat/ragdoll/RagdollPacketScopeMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/compat/ragdoll/RagdollSubscriptionMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/compat/ragdoll/RagdollSubscriptionMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/compat/ragdoll/RagdollSubscriptionMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/compat/ragdoll/ServerRagdollHitCacheMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/compat/ragdoll/ServerRagdollHitCacheMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/compat/ragdoll/ServerRagdollHitCacheMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/server/ServerPlayerDeathMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/server/ServerPlayerDeathMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/server/ServerPlayerDeathMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/network/packet/DownedRagdollStatePacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/DownedRagdollStatePacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/DownedRagdollStatePacket.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/network/packet/RagdollCatalogPacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/RagdollCatalogPacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/RagdollCatalogPacket.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/network/packet/RagdollObservationPacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/RagdollObservationPacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/RagdollObservationPacket.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/network/packet/RagdollSubscribePacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/RagdollSubscribePacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/RagdollSubscribePacket.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollBridge.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollBridge.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollBridge.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollEvents.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollEvents.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollEvents.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollNetwork.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollNetwork.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/DownedRagdollNetwork.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollHitCacheGuard.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollHitCacheGuard.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/RagdollHitCacheGuard.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollPacketScope.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollPacketScope.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/RagdollPacketScope.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollRecordView.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollRecordView.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/RagdollRecordView.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollSubscriptionPolicy.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollSubscriptionPolicy.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/RagdollSubscriptionPolicy.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollSubscriptions.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollSubscriptions.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/RagdollSubscriptions.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollSyncScope.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/RagdollSyncScope.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/RagdollSyncScope.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/core/RoomCoreService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/core/RoomCoreService.java) | `src/main/java/org/mods/gd656conquest/server/room/core/RoomCoreService.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCoreRuntimeManager.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCoreRuntimeManager.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/RoomCoreRuntimeManager.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/RoomServerModule.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomServerModule.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/RoomServerModule.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java) | `src/main/java/org/mods/gd656conquest/server/room/runtime/RoomPlayerRespawnService.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerStateService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerStateService.java) | `src/main/java/org/mods/gd656conquest/server/room/runtime/RoomPlayerStateService.java` | M | —/是/是 | 是 |

## P2

| 来源 | 目标 | Git | 编译/ASM/审计 | 目标已有 |
| --- | --- | --- | --- | --- |
| [gd656conquest/assets/gd656conquest/shaders/post/death_world.json](../../../gd656conquest/assets/gd656conquest/shaders/post/death_world.json) | `src/main/resources/assets/gd656conquest/shaders/post/death_world.json` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/post/revive_world.json](../../../gd656conquest/assets/gd656conquest/shaders/post/revive_world.json) | `src/main/resources/assets/gd656conquest/shaders/post/revive_world.json` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/death_world.fsh](../../../gd656conquest/assets/gd656conquest/shaders/program/death_world.fsh) | `src/main/resources/assets/gd656conquest/shaders/program/death_world.fsh` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/death_world.json](../../../gd656conquest/assets/gd656conquest/shaders/program/death_world.json) | `src/main/resources/assets/gd656conquest/shaders/program/death_world.json` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/revive_blur.fsh](../../../gd656conquest/assets/gd656conquest/shaders/program/revive_blur.fsh) | `src/main/resources/assets/gd656conquest/shaders/program/revive_blur.fsh` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/revive_blur.json](../../../gd656conquest/assets/gd656conquest/shaders/program/revive_blur.json) | `src/main/resources/assets/gd656conquest/shaders/program/revive_blur.json` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/revive_bright.fsh](../../../gd656conquest/assets/gd656conquest/shaders/program/revive_bright.fsh) | `src/main/resources/assets/gd656conquest/shaders/program/revive_bright.fsh` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/revive_bright.json](../../../gd656conquest/assets/gd656conquest/shaders/program/revive_bright.json) | `src/main/resources/assets/gd656conquest/shaders/program/revive_bright.json` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/revive_world.fsh](../../../gd656conquest/assets/gd656conquest/shaders/program/revive_world.fsh) | `src/main/resources/assets/gd656conquest/shaders/program/revive_world.fsh` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/revive_world.json](../../../gd656conquest/assets/gd656conquest/shaders/program/revive_world.json) | `src/main/resources/assets/gd656conquest/shaders/program/revive_world.json` | A | —/—/— | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientDeathFilter.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDeathFilter.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientDeathFilter.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientDeathFlowState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDeathFlowState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientDeathFlowState.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientDownedCamera.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedCamera.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientDownedCamera.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientReviveEffect.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientReviveEffect.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientReviveEffect.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientRoomLockState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientRoomLockState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientRoomLockState.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/client/room/DeathFilterTransition.java](../../../gd656conquest/org/mods/gd656conquest/client/room/DeathFilterTransition.java) | `src/main/java/org/mods/gd656conquest/client/room/DeathFilterTransition.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/DownedCameraMath.java](../../../gd656conquest/org/mods/gd656conquest/client/room/DownedCameraMath.java) | `src/main/java/org/mods/gd656conquest/client/room/DownedCameraMath.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ReviveTransition.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ReviveTransition.java) | `src/main/java/org/mods/gd656conquest/client/room/ReviveTransition.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/common/player/DownedCameraMode.java](../../../gd656conquest/org/mods/gd656conquest/common/player/DownedCameraMode.java) | `src/main/java/org/mods/gd656conquest/common/player/DownedCameraMode.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/common/player/RevivedPronePose.java](../../../gd656conquest/org/mods/gd656conquest/common/player/RevivedPronePose.java) | `src/main/java/org/mods/gd656conquest/common/player/RevivedPronePose.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/DeathPostChainAccessor.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/DeathPostChainAccessor.java) | `src/main/java/org/mods/gd656conquest/mixin/client/DeathPostChainAccessor.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/DeathWorldRenderMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/DeathWorldRenderMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/DeathWorldRenderMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/DownedCameraAccessor.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/DownedCameraAccessor.java) | `src/main/java/org/mods/gd656conquest/mixin/client/DownedCameraAccessor.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/compat/tacz/RevivedProneInputMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/compat/tacz/RevivedProneInputMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/compat/tacz/RevivedProneInputMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/compat/tacz/RevivedProneReleaseMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/compat/tacz/RevivedProneReleaseMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/compat/tacz/RevivedProneReleaseMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/compat/tacz/RevivedProneCrawlMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/compat/tacz/RevivedProneCrawlMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/compat/tacz/RevivedProneCrawlMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/network/packet/DownedViewPacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/DownedViewPacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/DownedViewPacket.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/network/packet/RevivedProneReleasePacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/RevivedProneReleasePacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/RevivedProneReleasePacket.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedCameraConfig.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedCameraConfig.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/DownedCameraConfig.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedReviveRequest.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedReviveRequest.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/DownedReviveRequest.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedViewService.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/ragdoll/DownedViewService.java) | `src/main/java/org/mods/gd656conquest/server/compat/ragdoll/DownedViewService.java` | A | 是/—/是 | 否 |

## P3

| 来源 | 目标 | Git | 编译/ASM/审计 | 目标已有 |
| --- | --- | --- | --- | --- |
| [gd656conquest/assets/gd656conquest/textures/gui/killer_card/headshot.png](../../../gd656conquest/assets/gd656conquest/textures/gui/killer_card/headshot.png) | `src/main/resources/assets/gd656conquest/textures/gui/killer_card/headshot.png` | A | —/—/— | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientDeathCauseState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDeathCauseState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientDeathCauseState.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientDownedDeployState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientDownedDeployState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientDownedDeployState.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientKillerCardState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientKillerCardState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientKillerCardState.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientManualDeathDeployState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientManualDeathDeployState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientManualDeathDeployState.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientRoomChatState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientRoomChatState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientRoomChatState.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/DeathCauseBanner.java](../../../gd656conquest/org/mods/gd656conquest/client/room/DeathCauseBanner.java) | `src/main/java/org/mods/gd656conquest/client/room/DeathCauseBanner.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/KillerCardLayout.java](../../../gd656conquest/org/mods/gd656conquest/client/room/KillerCardLayout.java) | `src/main/java/org/mods/gd656conquest/client/room/KillerCardLayout.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/KillerCardModel.java](../../../gd656conquest/org/mods/gd656conquest/client/room/KillerCardModel.java) | `src/main/java/org/mods/gd656conquest/client/room/KillerCardModel.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/RoomChatLayout.java](../../../gd656conquest/org/mods/gd656conquest/client/room/RoomChatLayout.java) | `src/main/java/org/mods/gd656conquest/client/room/RoomChatLayout.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/common/player/DeathCause.java](../../../gd656conquest/org/mods/gd656conquest/common/player/DeathCause.java) | `src/main/java/org/mods/gd656conquest/common/player/DeathCause.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/common/player/DownedDeployModel.java](../../../gd656conquest/org/mods/gd656conquest/common/player/DownedDeployModel.java) | `src/main/java/org/mods/gd656conquest/common/player/DownedDeployModel.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/common/player/KillerCardInfo.java](../../../gd656conquest/org/mods/gd656conquest/common/player/KillerCardInfo.java) | `src/main/java/org/mods/gd656conquest/common/player/KillerCardInfo.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/common/player/ManualDeathDeployModel.java](../../../gd656conquest/org/mods/gd656conquest/common/player/ManualDeathDeployModel.java) | `src/main/java/org/mods/gd656conquest/common/player/ManualDeathDeployModel.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/DeathKillerOutlineMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/DeathKillerOutlineMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/DeathKillerOutlineMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/KillerCardChatAccessor.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/KillerCardChatAccessor.java) | `src/main/java/org/mods/gd656conquest/mixin/client/KillerCardChatAccessor.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/RoomChatMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/RoomChatMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/RoomChatMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/RoomChatScreenMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/RoomChatScreenMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/RoomChatScreenMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/RoomChatSuggestionsMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/RoomChatSuggestionsMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/RoomChatSuggestionsMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/server/KillCommandMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/server/KillCommandMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/server/KillCommandMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/network/packet/DeathCausePacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/DeathCausePacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/DeathCausePacket.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/network/packet/DownedDeployPacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/DownedDeployPacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/DownedDeployPacket.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/network/packet/KillerCardPacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/KillerCardPacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/KillerCardPacket.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/DeathCauseClassifier.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/DeathCauseClassifier.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/DeathCauseClassifier.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/DownedDeployService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/DownedDeployService.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/DownedDeployService.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/FatalHitMetadata.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/FatalHitMetadata.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/FatalHitMetadata.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/KillWeaponResolver.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/KillWeaponResolver.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/KillWeaponResolver.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/KillerIntelService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/KillerIntelService.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/KillerIntelService.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/KilliconFireOwnership.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/KilliconFireOwnership.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/KilliconFireOwnership.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/ManualDeathDeployService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/ManualDeathDeployService.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/ManualDeathDeployService.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/MatchDuelStats.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/MatchDuelStats.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/MatchDuelStats.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/RoomDeathCauseService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomDeathCauseService.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/RoomDeathCauseService.java` | A | 是/—/是 | 否 |

## P4

| 来源 | 目标 | Git | 编译/ASM/审计 | 目标已有 |
| --- | --- | --- | --- | --- |
| [gd656conquest/assets/gd656conquest/shaders/post/boundary_world.json](../../../gd656conquest/assets/gd656conquest/shaders/post/boundary_world.json) | `src/main/resources/assets/gd656conquest/shaders/post/boundary_world.json` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/boundary_world.fsh](../../../gd656conquest/assets/gd656conquest/shaders/program/boundary_world.fsh) | `src/main/resources/assets/gd656conquest/shaders/program/boundary_world.fsh` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/boundary_world.json](../../../gd656conquest/assets/gd656conquest/shaders/program/boundary_world.json) | `src/main/resources/assets/gd656conquest/shaders/program/boundary_world.json` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/sounds/voice/broadcast/vo_mp_mother_inform_leaving_area.ogg](../../../gd656conquest/assets/gd656conquest/sounds/voice/broadcast/vo_mp_mother_inform_leaving_area.ogg) | `src/main/resources/assets/gd656conquest/sounds/voice/broadcast/vo_mp_mother_inform_leaving_area.ogg` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/sounds/voice/broadcast/vo_mp_mother_inform_leaving_area.wav](../../../gd656conquest/assets/gd656conquest/sounds/voice/broadcast/vo_mp_mother_inform_leaving_area.wav) | `src/main/resources/assets/gd656conquest/sounds/voice/broadcast/vo_mp_mother_inform_leaving_area.wav` | A | —/—/— | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/BoundaryHudEvents.java](../../../gd656conquest/org/mods/gd656conquest/client/room/BoundaryHudEvents.java) | `src/main/java/org/mods/gd656conquest/client/room/BoundaryHudEvents.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/BoundaryPresentation.java](../../../gd656conquest/org/mods/gd656conquest/client/room/BoundaryPresentation.java) | `src/main/java/org/mods/gd656conquest/client/room/BoundaryPresentation.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientBoundaryState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientBoundaryState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientBoundaryState.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/BoundaryWorldRenderMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/BoundaryWorldRenderMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/BoundaryWorldRenderMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/compat/tacz/BoundaryCrosshairMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/compat/tacz/BoundaryCrosshairMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/compat/tacz/BoundaryCrosshairMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/compat/superbwarfare/BoundaryDestructionMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/compat/superbwarfare/BoundaryDestructionMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/compat/superbwarfare/BoundaryDestructionMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/compat/ywzjvehicle/BoundaryDestructionMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/compat/ywzjvehicle/BoundaryDestructionMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/compat/ywzjvehicle/BoundaryDestructionMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/compat/vehicle/BoundaryVehicleDestruction.java](../../../gd656conquest/org/mods/gd656conquest/server/compat/vehicle/BoundaryVehicleDestruction.java) | `src/main/java/org/mods/gd656conquest/server/compat/vehicle/BoundaryVehicleDestruction.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/data/roomdefinition/RoomDefinitionRepository.java](../../../gd656conquest/org/mods/gd656conquest/server/data/roomdefinition/RoomDefinitionRepository.java) | `src/main/java/org/mods/gd656conquest/server/data/roomdefinition/RoomDefinitionRepository.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/loadout/domain/PlayerClassType.java](../../../gd656conquest/org/mods/gd656conquest/server/loadout/domain/PlayerClassType.java) | `src/main/java/org/mods/gd656conquest/server/loadout/domain/PlayerClassType.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/config/BoundaryConfigNumbers.java](../../../gd656conquest/org/mods/gd656conquest/server/room/config/BoundaryConfigNumbers.java) | `src/main/java/org/mods/gd656conquest/server/room/config/BoundaryConfigNumbers.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/config/RoomDefinitionGroupConfigEditor.java](../../../gd656conquest/org/mods/gd656conquest/server/room/config/RoomDefinitionGroupConfigEditor.java) | `src/main/java/org/mods/gd656conquest/server/room/config/RoomDefinitionGroupConfigEditor.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/config/RoomDefinitionScalarConfigEditor.java](../../../gd656conquest/org/mods/gd656conquest/server/room/config/RoomDefinitionScalarConfigEditor.java) | `src/main/java/org/mods/gd656conquest/server/room/config/RoomDefinitionScalarConfigEditor.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/RoomAdminConfigTemplateService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomAdminConfigTemplateService.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/RoomAdminConfigTemplateService.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCommands.java](../../../gd656conquest/org/mods/gd656conquest/server/room/entry/RoomCommands.java) | `src/main/java/org/mods/gd656conquest/server/room/entry/RoomCommands.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/runtime/BoundaryCountdown.java](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/BoundaryCountdown.java) | `src/main/java/org/mods/gd656conquest/server/room/runtime/BoundaryCountdown.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/runtime/FlightBoundaryGeometry.java](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/FlightBoundaryGeometry.java) | `src/main/java/org/mods/gd656conquest/server/room/runtime/FlightBoundaryGeometry.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomRuleRuntimeService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomRuleRuntimeService.java) | `src/main/java/org/mods/gd656conquest/server/room/runtime/RoomRuleRuntimeService.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/runtime/UnifiedBoundaryService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/UnifiedBoundaryService.java) | `src/main/java/org/mods/gd656conquest/server/room/runtime/UnifiedBoundaryService.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/vehicle/VehicleBoundaryPenaltyService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/vehicle/VehicleBoundaryPenaltyService.java) | `src/main/java/org/mods/gd656conquest/server/room/vehicle/VehicleBoundaryPenaltyService.java` | M | —/是/是 | 是 |

## P5

| 来源 | 目标 | Git | 编译/ASM/审计 | 目标已有 |
| --- | --- | --- | --- | --- |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientAmmoSupplySound.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientAmmoSupplySound.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientAmmoSupplySound.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/common/item/AmmoBagItem.java](../../../gd656conquest/org/mods/gd656conquest/common/item/AmmoBagItem.java) | `src/main/java/org/mods/gd656conquest/common/item/AmmoBagItem.java` | UNCHANGED | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/common/item/MedicalBagItem.java](../../../gd656conquest/org/mods/gd656conquest/common/item/MedicalBagItem.java) | `src/main/java/org/mods/gd656conquest/common/item/MedicalBagItem.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/AmmoSupplySoundAccessor.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/AmmoSupplySoundAccessor.java) | `src/main/java/org/mods/gd656conquest/mixin/client/AmmoSupplySoundAccessor.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/server/SupportDropMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/server/SupportDropMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/server/SupportDropMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/player/state/PlayerItemInteractionService.java](../../../gd656conquest/org/mods/gd656conquest/server/player/state/PlayerItemInteractionService.java) | `src/main/java/org/mods/gd656conquest/server/player/state/PlayerItemInteractionService.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomDeathSupportDropService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomDeathSupportDropService.java) | `src/main/java/org/mods/gd656conquest/server/room/runtime/RoomDeathSupportDropService.java` | UNCHANGED | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomItemReplenishService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomItemReplenishService.java) | `src/main/java/org/mods/gd656conquest/server/room/runtime/RoomItemReplenishService.java` | UNCHANGED | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomLoadoutGrantService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomLoadoutGrantService.java) | `src/main/java/org/mods/gd656conquest/server/room/runtime/RoomLoadoutGrantService.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerLoadoutService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomPlayerLoadoutService.java) | `src/main/java/org/mods/gd656conquest/server/room/runtime/RoomPlayerLoadoutService.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomSupportBoxService.java](../../../gd656conquest/org/mods/gd656conquest/server/room/runtime/RoomSupportBoxService.java) | `src/main/java/org/mods/gd656conquest/server/room/runtime/RoomSupportBoxService.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/support/SupportAmmo.java](../../../gd656conquest/org/mods/gd656conquest/server/support/SupportAmmo.java) | `src/main/java/org/mods/gd656conquest/server/support/SupportAmmo.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/support/SupportAmmoGrant.java](../../../gd656conquest/org/mods/gd656conquest/server/support/SupportAmmoGrant.java) | `src/main/java/org/mods/gd656conquest/server/support/SupportAmmoGrant.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/support/SupportContext.java](../../../gd656conquest/org/mods/gd656conquest/server/support/SupportContext.java) | `src/main/java/org/mods/gd656conquest/server/support/SupportContext.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/support/SupportDropGuard.java](../../../gd656conquest/org/mods/gd656conquest/server/support/SupportDropGuard.java) | `src/main/java/org/mods/gd656conquest/server/support/SupportDropGuard.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/support/SupportRuntime.java](../../../gd656conquest/org/mods/gd656conquest/server/support/SupportRuntime.java) | `src/main/java/org/mods/gd656conquest/server/support/SupportRuntime.java` | A | 是/—/是 | 否 |

## P6

| 来源 | 目标 | Git | 编译/ASM/审计 | 目标已有 |
| --- | --- | --- | --- | --- |
| [gd656conquest/assets/gd656conquest/shaders/post/suppression_world.json](../../../gd656conquest/assets/gd656conquest/shaders/post/suppression_world.json) | `src/main/resources/assets/gd656conquest/shaders/post/suppression_world.json` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/suppression_world.fsh](../../../gd656conquest/assets/gd656conquest/shaders/program/suppression_world.fsh) | `src/main/resources/assets/gd656conquest/shaders/program/suppression_world.fsh` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/shaders/program/suppression_world.json](../../../gd656conquest/assets/gd656conquest/shaders/program/suppression_world.json) | `src/main/resources/assets/gd656conquest/shaders/program/suppression_world.json` | A | —/—/— | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/ClientSuppressionState.java](../../../gd656conquest/org/mods/gd656conquest/client/room/ClientSuppressionState.java) | `src/main/java/org/mods/gd656conquest/client/room/ClientSuppressionState.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/client/room/SuppressionHudEvents.java](../../../gd656conquest/org/mods/gd656conquest/client/room/SuppressionHudEvents.java) | `src/main/java/org/mods/gd656conquest/client/room/SuppressionHudEvents.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/client/SuppressionWorldMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/client/SuppressionWorldMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/client/SuppressionWorldMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/compat/tacz/KineticBulletSupportAccessor.java](../../../gd656conquest/org/mods/gd656conquest/mixin/compat/tacz/KineticBulletSupportAccessor.java) | `src/main/java/org/mods/gd656conquest/mixin/compat/tacz/KineticBulletSupportAccessor.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/compat/tacz/SupportBulletMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/compat/tacz/SupportBulletMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/compat/tacz/SupportBulletMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/compat/tacz/SupportShotMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/compat/tacz/SupportShotMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/compat/tacz/SupportShotMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/compat/tacz/SupportSuppressionMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/compat/tacz/SupportSuppressionMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/compat/tacz/SupportSuppressionMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/mixin/server/SuppressedFoodRegenMixin.java](../../../gd656conquest/org/mods/gd656conquest/mixin/server/SuppressedFoodRegenMixin.java) | `src/main/java/org/mods/gd656conquest/mixin/server/SuppressedFoodRegenMixin.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/network/packet/SuppressionPacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/SuppressionPacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/SuppressionPacket.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/core/command/ConquestServerCommands.java](../../../gd656conquest/org/mods/gd656conquest/server/core/command/ConquestServerCommands.java) | `src/main/java/org/mods/gd656conquest/server/core/command/ConquestServerCommands.java` | UNCHANGED | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/core/command/NaturalRegenCommands.java](../../../gd656conquest/org/mods/gd656conquest/server/core/command/NaturalRegenCommands.java) | `src/main/java/org/mods/gd656conquest/server/core/command/NaturalRegenCommands.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/data/serverconfig/ServerConfigDataModel.java](../../../gd656conquest/org/mods/gd656conquest/server/data/serverconfig/ServerConfigDataModel.java) | `src/main/java/org/mods/gd656conquest/server/data/serverconfig/ServerConfigDataModel.java` | M | 是/—/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/data/serverconfig/ServerConfigStore.java](../../../gd656conquest/org/mods/gd656conquest/server/data/serverconfig/ServerConfigStore.java) | `src/main/java/org/mods/gd656conquest/server/data/serverconfig/ServerConfigStore.java` | M | 是/—/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/player/regen/NaturalRegenService.java](../../../gd656conquest/org/mods/gd656conquest/server/player/regen/NaturalRegenService.java) | `src/main/java/org/mods/gd656conquest/server/player/regen/NaturalRegenService.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/player/regen/PassiveRegenService.java](../../../gd656conquest/org/mods/gd656conquest/server/player/regen/PassiveRegenService.java) | `src/main/java/org/mods/gd656conquest/server/player/regen/PassiveRegenService.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/model/RoomNaturalRegenConfig.java](../../../gd656conquest/org/mods/gd656conquest/server/room/model/RoomNaturalRegenConfig.java) | `src/main/java/org/mods/gd656conquest/server/room/model/RoomNaturalRegenConfig.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/room/model/RoomSuppressionConfig.java](../../../gd656conquest/org/mods/gd656conquest/server/room/model/RoomSuppressionConfig.java) | `src/main/java/org/mods/gd656conquest/server/room/model/RoomSuppressionConfig.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/support/SupportRules.java](../../../gd656conquest/org/mods/gd656conquest/server/support/SupportRules.java) | `src/main/java/org/mods/gd656conquest/server/support/SupportRules.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/support/SuppressionNetwork.java](../../../gd656conquest/org/mods/gd656conquest/server/support/SuppressionNetwork.java) | `src/main/java/org/mods/gd656conquest/server/support/SuppressionNetwork.java` | A | 是/—/是 | 否 |
| [gd656conquest/org/mods/gd656conquest/server/support/TaczSuppression.java](../../../gd656conquest/org/mods/gd656conquest/server/support/TaczSuppression.java) | `src/main/java/org/mods/gd656conquest/server/support/TaczSuppression.java` | A | 是/—/是 | 否 |

## SHARED

| 来源 | 目标 | Git | 编译/ASM/审计 | 目标已有 |
| --- | --- | --- | --- | --- |
| [gd656conquest/BOUNDARY.md](../../../gd656conquest/BOUNDARY.md) | `docs/legacy/BOUNDARY.md` | A | —/—/— | 否 |
| [gd656conquest/DEATH_CAUSES.md](../../../gd656conquest/DEATH_CAUSES.md) | `docs/legacy/DEATH_CAUSES.md` | A | —/—/— | 否 |
| [gd656conquest/DEATH_FILTER.md](../../../gd656conquest/DEATH_FILTER.md) | `docs/legacy/DEATH_FILTER.md` | A | —/—/— | 否 |
| [gd656conquest/KILLER_CARD.md](../../../gd656conquest/KILLER_CARD.md) | `docs/legacy/KILLER_CARD.md` | A | —/—/— | 否 |
| [gd656conquest/META-INF/mods.toml](../../../gd656conquest/META-INF/mods.toml) | `src/main/resources/META-INF/mods.toml` | M | —/—/— | 是 |
| [gd656conquest/RAGDOLL_INTEGRATION.md](../../../gd656conquest/RAGDOLL_INTEGRATION.md) | `docs/legacy/RAGDOLL_INTEGRATION.md` | A | —/—/— | 否 |
| [gd656conquest/SUPPORT_SUPPRESSION.md](../../../gd656conquest/SUPPORT_SUPPRESSION.md) | `docs/legacy/SUPPORT_SUPPRESSION.md` | A | —/—/— | 否 |
| [gd656conquest/assets/gd656conquest/lang/en_us.json](../../../gd656conquest/assets/gd656conquest/lang/en_us.json) | `src/main/resources/assets/gd656conquest/lang/en_us.json` | M | —/—/— | 是 |
| [gd656conquest/assets/gd656conquest/lang/zh_cn.json](../../../gd656conquest/assets/gd656conquest/lang/zh_cn.json) | `src/main/resources/assets/gd656conquest/lang/zh_cn.json` | M | —/—/— | 是 |
| [gd656conquest/assets/gd656conquest/sounds.json](../../../gd656conquest/assets/gd656conquest/sounds.json) | `src/main/resources/assets/gd656conquest/sounds.json` | M | —/—/— | 是 |
| [gd656conquest/gd656conquest.mixins.json](../../../gd656conquest/gd656conquest.mixins.json) | `src/main/resources/gd656conquest.mixins.json` | M | —/—/— | 是 |
| [gd656conquest/org/mods/gd656conquest/Gd656conquest.java](../../../gd656conquest/org/mods/gd656conquest/Gd656conquest.java) | `src/main/java/org/mods/gd656conquest/Gd656conquest.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/network/NetworkManager.java](../../../gd656conquest/org/mods/gd656conquest/network/NetworkManager.java) | `src/main/java/org/mods/gd656conquest/network/NetworkManager.java` | M | —/是/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/network/packet/RoomDeathFlowPacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/RoomDeathFlowPacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/RoomDeathFlowPacket.java` | M | 是/—/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/network/packet/RoomDownedActionPacket.java](../../../gd656conquest/org/mods/gd656conquest/network/packet/RoomDownedActionPacket.java) | `src/main/java/org/mods/gd656conquest/network/packet/RoomDownedActionPacket.java` | M | 是/—/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/data/roomdefinition/RoomConfigValidator.java](../../../gd656conquest/org/mods/gd656conquest/server/data/roomdefinition/RoomConfigValidator.java) | `src/main/java/org/mods/gd656conquest/server/data/roomdefinition/RoomConfigValidator.java` | M | 是/—/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/config/RoomConfigDescriptorRegistry.java](../../../gd656conquest/org/mods/gd656conquest/server/room/config/RoomConfigDescriptorRegistry.java) | `src/main/java/org/mods/gd656conquest/server/room/config/RoomConfigDescriptorRegistry.java` | M | 是/—/是 | 是 |
| [gd656conquest/org/mods/gd656conquest/server/room/model/RoomCommonConfig.java](../../../gd656conquest/org/mods/gd656conquest/server/room/model/RoomCommonConfig.java) | `src/main/java/org/mods/gd656conquest/server/room/model/RoomCommonConfig.java` | M | 是/—/是 | 是 |

## 成品资源核对

字节不同需要复核构建来源；不能只把 Git 改动当作已发行行为。Java 源码与 class 不进行字节等价比较。

| 资源 | 成品包含 | 成品等于当前源码文件 | 相对原版成品改变 |
| --- | --- | --- | --- |
| `gd656conquest/BOUNDARY.md` | 是 | 是 | 是 |
| `gd656conquest/DEATH_CAUSES.md` | 是 | 是 | 是 |
| `gd656conquest/DEATH_FILTER.md` | 是 | 是 | 是 |
| `gd656conquest/KILLER_CARD.md` | 是 | 是 | 是 |
| `gd656conquest/META-INF/mods.toml` | 是 | 是 | 是 |
| `gd656conquest/RAGDOLL_INTEGRATION.md` | 是 | 是 | 是 |
| `gd656conquest/SUPPORT_SUPPRESSION.md` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/lang/en_us.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/lang/zh_cn.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/post/boundary_world.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/post/death_world.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/post/revive_world.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/post/suppression_world.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/boundary_world.fsh` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/boundary_world.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/death_world.fsh` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/death_world.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/revive_blur.fsh` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/revive_blur.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/revive_bright.fsh` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/revive_bright.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/revive_world.fsh` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/revive_world.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/suppression_world.fsh` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/shaders/program/suppression_world.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/sounds.json` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/sounds/voice/broadcast/vo_mp_mother_inform_leaving_area.ogg` | 是 | 是 | 是 |
| `gd656conquest/assets/gd656conquest/sounds/voice/broadcast/vo_mp_mother_inform_leaving_area.wav` | 否 | 否 | 否 |
| `gd656conquest/assets/gd656conquest/textures/gui/killer_card/headshot.png` | 是 | 是 | 是 |
| `gd656conquest/gd656conquest.mixins.json` | 是 | 是 | 是 |
