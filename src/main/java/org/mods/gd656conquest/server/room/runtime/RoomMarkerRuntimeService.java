package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ClientShortSoundPacket;
import org.mods.gd656conquest.network.packet.RoomMarkerSyncPacket;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.compat.frontlinevoice.FrontlineVoiceBridge;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;

public final class RoomMarkerRuntimeService {
    private static final String MARKER_SPOT_FAIL_SOUND_ID = "gd656conquest:sfx.marker_spot_fail";
    private static final String MARKER_SPOT_SUCCESS_SOUND_ID = "gd656conquest:sfx.marker_spot_success";
    private static final int MARKER_LIFETIME_TICKS = 30 * 20;
    private static final int OBSERVE_GROUND_LIFETIME_TICKS = 2 * 20;
    private static final int DIRECT_SPOT_LIFETIME_TICKS = 5 * 20;
    private static final int AUTO_SPOT_LIFETIME_TICKS = 2 * 20;
    // GD656Mods: 救援提醒标记/actionbar 持续 5 秒
    private static final int RESCUE_REQUEST_LIFETIME_TICKS = 5 * 20;
    private static final int RESCUE_ACTIONBAR_TICKS = 5 * 20;
    private static final int MARKER_SYNC_INTERVAL_TICKS = 5;
    private static final double MAX_MARKER_DISTANCE_SQR = 256.0D * 256.0D;
    private static final String ACTIONBAR_SOURCE_MARKER = "room_marker";
    private static final long SPOTTING_BONUS_WINDOW_MS = 60000L;
    private static final int NEED_AMMO_MARKER_LIFETIME_TICKS = MARKER_SYNC_INTERVAL_TICKS * 3;
    private static final int SUPPORT_DROP_MARKER_LIFETIME_TICKS = MARKER_SYNC_INTERVAL_TICKS * 3;
    private static final double SUPPORT_DROP_MARKER_RADIUS = 30.0D;
    private static final String NEED_AMMO_ALLY_KIND = "NEED_AMMO_ALLY";
    private static final String NEED_AMMO_SQUAD_KIND = "NEED_AMMO_SQUAD";
    private static final String AMMO_BAG_KIND = "AMMO_BAG";
    private static final String MEDICAL_BAG_KIND = "MEDICAL_BAG";

    private final MinecraftServer server;
    private final Map<UUID, Deque<Long>> spottingBonusTimesByPlayer = new java.util.LinkedHashMap<>();

    public RoomMarkerRuntimeService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void tickRoom(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null) {
            return;
        }
        boolean changed = pruneExpiredMarkers(state, gameTick);
        if (changed || gameTick % MARKER_SYNC_INTERVAL_TICKS == 0L) {
            syncRunningRoom(room, state, gameTick);
        }
    }

    public boolean placeMarker(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RunningRuntimeState.MarkerKind kind,
        BlockPos blockPos,
        UUID targetPlayerId,
        long gameTick
    ) {
        if (!isPlayerAllowed(room, state, player) || blockPos == null) {
            return false;
        }
        RunningRuntimeState.MarkerKind resolvedKind = kind == null ? RunningRuntimeState.MarkerKind.NORMAL : kind;
        if (resolvedKind == RunningRuntimeState.MarkerKind.AUTO_ENEMY && !isAutoEnemyMarkerAllowed(player)) {
            return false;
        }
        UUID ownerId = player.getUUID();
        UUID resolvedTargetPlayerId = null;
        BlockPos normalizedPos = blockPos.immutable();
        ServerPlayer rescueTarget = null;
        if (resolvedKind == RunningRuntimeState.MarkerKind.DIRECT_ENEMY || resolvedKind == RunningRuntimeState.MarkerKind.AUTO_ENEMY) {
            ServerPlayer targetPlayer = resolveDirectEnemyTarget(state, player, targetPlayerId);
            if (targetPlayer == null) {
                return false;
            }
            normalizedPos = targetPlayer.blockPosition().immutable();
            resolvedTargetPlayerId = targetPlayer.getUUID();
        } else if (isRescueRequestKind(resolvedKind)) {
            // GD656Mods: 救援提醒 —— 标记键指向倒地友军/小队队友图标时, 标记生成在倒地玩家处
            // GD656Mods: 倒地/倒地待确认的玩家自身无法发起救援提醒(静默阻止, 无任何反馈)
            if (state.isPlayerDowned(player.getUUID()) || state.isPlayerPendingDowned(player.getUUID())) {
                return false;
            }
            rescueTarget = resolveDownedFriendlyTarget(state, player, targetPlayerId);
            if (rescueTarget == null) {
                return false;
            }
            normalizedPos = rescueTarget.blockPosition().immutable();
            resolvedTargetPlayerId = rescueTarget.getUUID();
        } else if (!isMarkerDistanceValid(player, normalizedPos)) {
            return false;
        }
        RunningRuntimeState.TacticalMarker nextMarker = new RunningRuntimeState.TacticalMarker(
            normalizedPos,
            resolvedKind,
            resolvedTargetPlayerId,
            gameTick,
            gameTick + (isRescueRequestKind(resolvedKind)
                ? RESCUE_REQUEST_LIFETIME_TICKS
                : resolveLifetimeTicks(resolvedKind)),
            resolvedKind == RunningRuntimeState.MarkerKind.DIRECT_ENEMY ? gameTick + DIRECT_SPOT_LIFETIME_TICKS : -1L
        );
        if (resolvedKind == RunningRuntimeState.MarkerKind.DIRECT_ENEMY && isPlayerInDroneView(player)) {
            // GD656Mods: 无人机视角 AOE 索敌 —— 同一玩家可同时标记多个敌人, 按被标记敌人分别存储, 互不覆盖
            state.getDroneSpotMarkersByOwner()
                .computeIfAbsent(ownerId, ignored -> new java.util.LinkedHashMap<>())
                .put(resolvedTargetPlayerId, nextMarker);
        } else {
            Map<UUID, RunningRuntimeState.TacticalMarker> markerMap = resolvedKind == RunningRuntimeState.MarkerKind.AUTO_ENEMY
                ? state.getAutoTacticalMarkersByOwner()
                : state.getTacticalMarkersByOwner();
            markerMap.put(ownerId, nextMarker);
        }
        if (resolvedKind != RunningRuntimeState.MarkerKind.AUTO_ENEMY && !isRescueRequestKind(resolvedKind)) {
            showMarkerSuccessActionbar(player, normalizedPos, resolvedKind, resolvedTargetPlayerId);
        }
        if (resolvedKind == RunningRuntimeState.MarkerKind.DIRECT_ENEMY) {
            playMarkerFeedbackSound(player, MARKER_SPOT_SUCCESS_SOUND_ID);
            tryAwardSpottingBonus(player);
            FrontlineVoiceBridge.playCue(player, resolveSpottedEnemyCue(targetPlayerId));
            // 广播索敌事件, 供外部模组(如 GD656Killicon 标示助攻)监听
            ServerPlayer spottedTarget = resolveDirectEnemyTarget(state, player, targetPlayerId);
            if (spottedTarget != null) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new org.mods.gd656conquest.server.room.runtime.event.ConquestMarkerSpotPlacedEvent(player, spottedTarget));
            }
        } else if (isRescueRequestKind(resolvedKind)) {
            // GD656Mods: 救援提醒 actionbar: 标记者 + 倒地玩家
            showRescueRequestActionbars(player, rescueTarget, normalizedPos);
            playMarkerFeedbackSound(player, MARKER_SPOT_SUCCESS_SOUND_ID);
        } else if (resolvedKind != RunningRuntimeState.MarkerKind.AUTO_ENEMY) {
            playMarkerFeedbackSound(player, MARKER_SPOT_FAIL_SOUND_ID);
        }
        syncRunningRoom(room, state, gameTick);
        return true;
    }

    private static boolean isRescueRequestKind(RunningRuntimeState.MarkerKind kind) {
        return kind == RunningRuntimeState.MarkerKind.RESCUE_ALLY
            || kind == RunningRuntimeState.MarkerKind.RESCUE_SQUAD;
    }

    /** GD656Mods: 校验标记目标为"同阵营且处于倒地/倒地待确认"的玩家 */
    private ServerPlayer resolveDownedFriendlyTarget(RunningRuntimeState state, ServerPlayer marker, UUID targetPlayerId) {
        if (state == null || marker == null || targetPlayerId == null) {
            return null;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(targetPlayerId);
        if (target == null || !target.isAlive() || target.isSpectator()) {
            return null;
        }
        if (!state.isPlayerDowned(targetPlayerId) && !state.isPlayerPendingDowned(targetPlayerId)) {
            return null;
        }
        RoomTeamId markerTeam = state.getRoomTeams().get(marker.getUUID());
        RoomTeamId targetTeam = state.getRoomTeams().get(targetPlayerId);
        if (markerTeam == null || markerTeam != targetTeam) {
            return null;
        }
        return target;
    }

    /** GD656Mods: 救援提醒 actionbar —— 标记者:"已提醒救援 X"; 倒地玩家:"X x米 即将前来救援" */
    private void showRescueRequestActionbars(ServerPlayer marker, ServerPlayer rescueTarget, BlockPos markerPos) {
        if (marker == null || rescueTarget == null) {
            return;
        }
        String targetName = rescueTarget.getScoreboardName();
        ServerActionBarService.showBuilder(marker, ACTIONBAR_SOURCE_MARKER, ServerActionBarService.builder()
            .text("已提醒救援 ", net.minecraft.ChatFormatting.AQUA)
            .text(targetName, net.minecraft.ChatFormatting.GOLD), RESCUE_ACTIONBAR_TICKS);
        double distance = marker.position().distanceTo(rescueTarget.position());
        ServerActionBarService.showBuilder(rescueTarget, ACTIONBAR_SOURCE_MARKER, ServerActionBarService.builder()
            .text(marker.getScoreboardName() + " ", net.minecraft.ChatFormatting.GOLD)
            .text(Math.round(distance) + "米 ", net.minecraft.ChatFormatting.DARK_GRAY)
            .text("即将前来救援", net.minecraft.ChatFormatting.GRAY), RESCUE_ACTIONBAR_TICKS);
    }

    public boolean cancelMarker(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick) {
        if (!isPlayerAllowed(room, state, player)) {
            return false;
        }
        RunningRuntimeState.TacticalMarker removed = state.getTacticalMarkersByOwner().remove(player.getUUID());
        state.getDroneSpotMarkersByOwner().remove(player.getUUID());
        if (removed == null) {
            syncRunningRoom(room, state, gameTick);
            return true;
        }
        syncRunningRoom(room, state, gameTick);
        return true;
    }

    public void clearPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        NetworkManager.sendToPlayer(player, RoomMarkerSyncPacket.clear());
    }

    public void syncRunningRoom(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null) {
            return;
        }
        for (UUID playerId : List.copyOf(state.getRoomPlayers())) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(playerId);
            if (viewer == null) {
                continue;
            }
            RoomTeamId viewerTeam = state.getRoomTeams().get(playerId);
            if (viewerTeam != RoomTeamId.CAMP_A && viewerTeam != RoomTeamId.CAMP_B) {
                NetworkManager.sendToPlayer(viewer, RoomMarkerSyncPacket.clear());
                continue;
            }
            List<RoomMarkerSyncPacket.MarkerEntry> visibleMarkers = buildVisibleMarkers(state, viewerTeam, gameTick);
            appendNeedAmmoMarkers(visibleMarkers, state, viewer, viewerTeam);
            appendOwnedSupportDropMarkers(visibleMarkers, viewer);
            NetworkManager.sendToPlayer(viewer, new RoomMarkerSyncPacket(visibleMarkers));
        }
    }

    public boolean putGameplayTrackedMarker(
        RunningRuntimeState state,
        String markerKey,
        RoomTeamId visibleTeam,
        UUID targetPlayerId,
        String ownerName,
        long gameTick,
        long expireTick
    ) {
        if (state == null || markerKey == null || markerKey.isBlank() || visibleTeam == null || targetPlayerId == null) {
            return false;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(targetPlayerId);
        if (target == null || !target.isAlive()) {
            return false;
        }
        RunningRuntimeState.TeamScopedTacticalMarker nextMarker = new RunningRuntimeState.TeamScopedTacticalMarker(
            UUID.nameUUIDFromBytes(markerKey.getBytes(StandardCharsets.UTF_8)),
            ownerName,
            visibleTeam,
            new RunningRuntimeState.TacticalMarker(
                target.blockPosition().immutable(),
                RunningRuntimeState.MarkerKind.AUTO_ENEMY,
                targetPlayerId,
                gameTick,
                Math.max(gameTick, expireTick),
                -1L
            )
        );
        RunningRuntimeState.TeamScopedTacticalMarker previous = state.getGameplayTacticalMarkersByKey().put(markerKey, nextMarker);
        return !Objects.equals(previous, nextMarker);
    }

    public boolean removeGameplayMarker(RunningRuntimeState state, String markerKey) {
        if (state == null || markerKey == null || markerKey.isBlank()) {
            return false;
        }
        return state.getGameplayTacticalMarkersByKey().remove(markerKey) != null;
    }

    public boolean clearGameplayMarkersByPrefix(RunningRuntimeState state, String markerKeyPrefix) {
        if (state == null || markerKeyPrefix == null || markerKeyPrefix.isBlank()) {
            return false;
        }
        boolean changed = false;
        for (String markerKey : List.copyOf(state.getGameplayTacticalMarkersByKey().keySet())) {
            if (!markerKey.startsWith(markerKeyPrefix)) {
                continue;
            }
            state.getGameplayTacticalMarkersByKey().remove(markerKey);
            changed = true;
        }
        return changed;
    }

    public void handlePlayerDeath(RoomDefinition room, RunningRuntimeState state, ServerPlayer victim, ServerPlayer killer, long gameTick) {
        if (room == null || state == null || victim == null) {
            return;
        }
        UUID victimId = victim.getUUID();
        RoomTeamId killerTeam = killer == null ? null : state.getRoomTeams().get(killer.getUUID());
        boolean changed = false;
        for (Map.Entry<UUID, RunningRuntimeState.TacticalMarker> entry : List.copyOf(state.getTacticalMarkersByOwner().entrySet())) {
            UUID ownerId = entry.getKey();
            RunningRuntimeState.TacticalMarker marker = entry.getValue();
            if (ownerId == null || marker == null) {
                continue;
            }
            if (rewardAndRemoveDirectSpotMarkerForVictim(state, ownerId, marker, victim, killer, killerTeam, gameTick)) {
                state.getTacticalMarkersByOwner().remove(ownerId);
                changed = true;
            }
        }
        for (Map.Entry<UUID, Map<UUID, RunningRuntimeState.TacticalMarker>> ownerEntry : List.copyOf(state.getDroneSpotMarkersByOwner().entrySet())) {
            UUID ownerId = ownerEntry.getKey();
            Map<UUID, RunningRuntimeState.TacticalMarker> ownedMarkers = ownerEntry.getValue();
            if (ownerId == null || ownedMarkers == null) {
                continue;
            }
            for (Map.Entry<UUID, RunningRuntimeState.TacticalMarker> targetEntry : List.copyOf(ownedMarkers.entrySet())) {
                if (rewardAndRemoveDirectSpotMarkerForVictim(state, ownerId, targetEntry.getValue(), victim, killer, killerTeam, gameTick)) {
                    ownedMarkers.remove(targetEntry.getKey());
                    changed = true;
                }
            }
            if (ownedMarkers.isEmpty()) {
                state.getDroneSpotMarkersByOwner().remove(ownerId);
            }
        }
        for (Map.Entry<UUID, RunningRuntimeState.TacticalMarker> entry : List.copyOf(state.getAutoTacticalMarkersByOwner().entrySet())) {
            RunningRuntimeState.TacticalMarker marker = entry.getValue();
            if (marker == null || marker.kind() != RunningRuntimeState.MarkerKind.AUTO_ENEMY) {
                continue;
            }
            if (!victimId.equals(marker.targetPlayerId())) {
                continue;
            }
            state.getAutoTacticalMarkersByOwner().remove(entry.getKey());
            changed = true;
        }
        for (Map.Entry<String, RunningRuntimeState.TeamScopedTacticalMarker> entry : List.copyOf(state.getGameplayTacticalMarkersByKey().entrySet())) {
            RunningRuntimeState.TeamScopedTacticalMarker marker = entry.getValue();
            if (marker == null || marker.marker() == null) {
                continue;
            }
            if (!victimId.equals(marker.marker().targetPlayerId())) {
                continue;
            }
            state.getGameplayTacticalMarkersByKey().remove(entry.getKey());
            changed = true;
        }
        if (changed) {
            syncRunningRoom(room, state, gameTick);
        }
    }

    /**
     * GD656Mods: 被标记敌人死亡结算 —— 标记仍处于直接索敌有效期内时, 给标记者结算击杀/助攻奖励。
     * 单标记与无人机多标记共用, 返回 true 表示该标记应被移除。
     */
    private boolean rewardAndRemoveDirectSpotMarkerForVictim(
        RunningRuntimeState state,
        UUID ownerId,
        RunningRuntimeState.TacticalMarker marker,
        ServerPlayer victim,
        ServerPlayer killer,
        RoomTeamId killerTeam,
        long gameTick
    ) {
        if (state == null || ownerId == null || marker == null || victim == null) {
            return false;
        }
        if (marker.kind() != RunningRuntimeState.MarkerKind.DIRECT_ENEMY) {
            return false;
        }
        if (!victim.getUUID().equals(marker.targetPlayerId()) || marker.directSpotExpireTick() < gameTick) {
            return false;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner != null && killer != null) {
            if (killer.getUUID().equals(ownerId)) {
                KilliconScoreboardBridge.tryAwardConquestBonus(killer, "SPOTTING_KILL", 1.0F, "", victim.getId(), victim.getScoreboardName());
            } else {
                RoomTeamId ownerTeam = state.getRoomTeams().get(ownerId);
                if (ownerTeam != null && ownerTeam == killerTeam) {
                    KilliconScoreboardBridge.tryAwardConquestBonus(owner, "SPOTTING_TEAM_ASSIST", 1.0F, "", victim.getId(), victim.getScoreboardName());
                }
            }
        }
        return true;
    }

    private boolean pruneExpiredMarkers(RunningRuntimeState state, long gameTick) {
        boolean changed = false;
        changed |= pruneExpiredMarkersFromMap(state.getTacticalMarkersByOwner(), gameTick);
        changed |= pruneExpiredMarkersFromMap(state.getAutoTacticalMarkersByOwner(), gameTick);
        changed |= pruneExpiredDroneMarkers(state, gameTick);
        changed |= pruneExpiredGameplayMarkers(state.getGameplayTacticalMarkersByKey(), gameTick);
        return changed;
    }

    private List<RoomMarkerSyncPacket.MarkerEntry> buildVisibleMarkers(RunningRuntimeState state, RoomTeamId viewerTeam, long gameTick) {
        List<RoomMarkerSyncPacket.MarkerEntry> entries = new ArrayList<>();
        appendVisibleMarkers(entries, state, state.getTacticalMarkersByOwner(), viewerTeam, gameTick);
        appendVisibleMarkers(entries, state, state.getAutoTacticalMarkersByOwner(), viewerTeam, gameTick);
        appendDroneSpotMarkers(entries, state, state.getDroneSpotMarkersByOwner(), viewerTeam, gameTick);
        appendGameplayVisibleMarkers(entries, state.getGameplayTacticalMarkersByKey(), viewerTeam, gameTick);
        return entries;
    }

    private void appendNeedAmmoMarkers(
        List<RoomMarkerSyncPacket.MarkerEntry> entries,
        RunningRuntimeState state,
        ServerPlayer viewer,
        RoomTeamId viewerTeam
    ) {
        if (entries == null || state == null || viewer == null || viewerTeam == null || !canViewerSeeNeedAmmoMarkers(state, viewer, viewerTeam)) {
            return;
        }
        String viewerSquadLabel = normalizeSquadLabel(state.getSquadLabelsByPlayer().get(viewer.getUUID()));
        for (UUID targetPlayerId : List.copyOf(state.getRoomPlayers())) {
            if (targetPlayerId == null || targetPlayerId.equals(viewer.getUUID())) {
                continue;
            }
            if (state.getRoomTeams().get(targetPlayerId) != viewerTeam) {
                continue;
            }
            if (state.isInRespawnFlow(targetPlayerId) || state.isPlayerDowned(targetPlayerId) || state.isPlayerBeingRevived(targetPlayerId)) {
                continue;
            }
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetPlayerId);
            if (targetPlayer == null || !targetPlayer.isAlive() || !playerNeedsAmmoMarker(targetPlayer)) {
                continue;
            }
            boolean sameSquad = isSameSquad(viewerSquadLabel, state.getSquadLabelsByPlayer().get(targetPlayerId));
            BlockPos displayPos = targetPlayer.blockPosition();
            entries.add(new RoomMarkerSyncPacket.MarkerEntry(
                targetPlayerId,
                targetPlayer.getScoreboardName(),
                sameSquad ? NEED_AMMO_SQUAD_KIND : NEED_AMMO_ALLY_KIND,
                displayPos.getX(),
                displayPos.getY(),
                displayPos.getZ(),
                targetPlayerId,
                0,
                NEED_AMMO_MARKER_LIFETIME_TICKS
            ));
        }
    }

    private void appendOwnedSupportDropMarkers(List<RoomMarkerSyncPacket.MarkerEntry> entries, ServerPlayer viewer) {
        if (entries == null || viewer == null || viewer.level() == null) {
            return;
        }
        AABB searchBox = viewer.getBoundingBox().inflate(SUPPORT_DROP_MARKER_RADIUS, SUPPORT_DROP_MARKER_RADIUS * 0.5D, SUPPORT_DROP_MARKER_RADIUS);
        for (ItemEntity itemEntity : viewer.level().getEntitiesOfClass(ItemEntity.class, searchBox)) {
            if (itemEntity == null || !itemEntity.isAlive() || itemEntity.distanceToSqr(viewer) > SUPPORT_DROP_MARKER_RADIUS * SUPPORT_DROP_MARKER_RADIUS) {
                continue;
            }
            if (!RoomDeathSupportDropService.isDeathSupportDrop(itemEntity)) {
                continue;
            }
            if (!viewer.getUUID().equals(RoomDeathSupportDropService.getPickupOwner(itemEntity))) {
                continue;
            }
            ItemStack stack = itemEntity.getItem();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String markerKind = null;
            String ownerName = null;
            if (stack.getItem() == ConquestItems.AMMO_BAG.get()) {
                markerKind = AMMO_BAG_KIND;
                ownerName = "弹药包";
            } else if (stack.getItem() == ConquestItems.MEDIC_BAG.get()) {
                markerKind = MEDICAL_BAG_KIND;
                ownerName = "医疗包";
            }
            if (markerKind == null) {
                continue;
            }
            BlockPos displayPos = itemEntity.blockPosition();
            entries.add(new RoomMarkerSyncPacket.MarkerEntry(
                itemEntity.getUUID(),
                ownerName,
                markerKind,
                displayPos.getX(),
                displayPos.getY(),
                displayPos.getZ(),
                null,
                0,
                SUPPORT_DROP_MARKER_LIFETIME_TICKS
            ));
        }
    }

    private String resolveOwnerName(UUID ownerId) {
        ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (owner == null || owner.getGameProfile() == null || owner.getGameProfile().getName() == null || owner.getGameProfile().getName().isBlank()) {
            return "未知";
        }
        return owner.getGameProfile().getName();
    }

    private boolean isPlayerAllowed(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        if (room == null || state == null || player == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        if (!state.getRoomPlayers().contains(playerId)) {
            return false;
        }
        if (state.getResultSequenceEndTick() > 0L) {
            return false;
        }
        if (state.isInRespawnFlow(playerId) && !state.isPlayerDowned(playerId)) {
            return false;
        }
        RoomTeamId teamId = state.getRoomTeams().get(playerId);
        return teamId == RoomTeamId.CAMP_A || teamId == RoomTeamId.CAMP_B;
    }

    private boolean isAutoEnemyMarkerAllowed(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        RunningRuntimeState state = RoomCoreRuntimeManager.of(server)
            .findRunningState(RoomCoreRuntimeManager.of(server).findPlayerRoomId(player.getUUID()).orElse(""))
            .orElse(null);
        PlayerClassType classType = CustomLoadoutService.resolveCurrentClass(
            ConquestDataManager.of(server).playerData().getPlayerData(player.getUUID()),
            state,
            player.getUUID()
        );
        return classType == PlayerClassType.RECON;
    }

    private boolean canViewerSeeNeedAmmoMarkers(RunningRuntimeState state, ServerPlayer viewer, RoomTeamId viewerTeam) {
        if (state == null || viewer == null || viewerTeam == null) {
            return false;
        }
        if (!viewer.isAlive() || state.getRoomTeams().get(viewer.getUUID()) != viewerTeam || state.isInRespawnFlow(viewer.getUUID())) {
            return false;
        }
        PlayerClassType classType = CustomLoadoutService.resolveCurrentClass(
            ConquestDataManager.of(server).playerData().getPlayerData(viewer.getUUID()),
            state,
            viewer.getUUID()
        );
        return classType == PlayerClassType.SUPPORT && carriesAmmoSupplyItem(viewer);
    }

    private boolean carriesAmmoSupplyItem(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (isAmmoSupplyItem(stack)) {
                return true;
            }
        }
        return isAmmoSupplyItem(player.getOffhandItem());
    }

    private boolean isAmmoSupplyItem(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && (stack.getItem() == ConquestItems.AMMO_BAG.get() || stack.getItem() == ConquestItems.AMMO_BOX.get());
    }

    private boolean playerNeedsAmmoMarker(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return isTrackedTaczWeaponReserveEmpty(player, 0) || isTrackedTaczWeaponReserveEmpty(player, 1);
    }

    private boolean isTrackedTaczWeaponReserveEmpty(ServerPlayer player, int slotIndex) {
        if (player == null || slotIndex < 0 || slotIndex >= player.getInventory().items.size()) {
            return false;
        }
        ItemStack weaponStack = player.getInventory().items.get(slotIndex);
        if (weaponStack == null || weaponStack.isEmpty()) {
            return false;
        }
        try {
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            Method getIGunOrNull = iGunClass.getMethod("getIGunOrNull", ItemStack.class);
            Object iGun = getIGunOrNull.invoke(null, weaponStack);
            if (iGun == null) {
                return false;
            }
            int dummyAmmo = 0;
            try {
                Method useDummyAmmo = iGun.getClass().getMethod("useDummyAmmo", ItemStack.class);
                if (Boolean.TRUE.equals(useDummyAmmo.invoke(iGun, weaponStack))) {
                    Method getDummyAmmoAmount = iGun.getClass().getMethod("getDummyAmmoAmount", ItemStack.class);
                    Object dummyAmmoObject = getDummyAmmoAmount.invoke(iGun, weaponStack);
                    if (dummyAmmoObject instanceof Integer dummyAmmoAmount) {
                        dummyAmmo = Math.max(0, dummyAmmoAmount);
                    }
                }
            } catch (Exception ignored) {
            }
            int reserveAmmo = countTaczReserveAmmo(player, iGun, weaponStack);
            return dummyAmmo + reserveAmmo <= 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int countTaczReserveAmmo(ServerPlayer player, Object iGun, ItemStack weaponStack) {
        if (player == null || iGun == null || weaponStack == null || weaponStack.isEmpty()) {
            return 0;
        }
        try {
            Method getGunId = iGun.getClass().getMethod("getGunId", ItemStack.class);
            Object gunIdObject = getGunId.invoke(iGun, weaponStack);
            if (!(gunIdObject instanceof net.minecraft.resources.ResourceLocation gunId)) {
                return 0;
            }
            Class<?> timelessApiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            Method getCommonGunIndex = timelessApiClass.getMethod("getCommonGunIndex", net.minecraft.resources.ResourceLocation.class);
            Object gunIndexOptionalObject = getCommonGunIndex.invoke(null, gunId);
            if (!(gunIndexOptionalObject instanceof java.util.Optional<?> gunIndexOptional) || gunIndexOptional.isEmpty()) {
                return 0;
            }
            Object gunIndex = gunIndexOptional.get();
            Method getGunData = gunIndex.getClass().getMethod("getGunData");
            Object gunData = getGunData.invoke(gunIndex);
            if (gunData == null) {
                return 0;
            }
            Method getAmmoId = gunData.getClass().getMethod("getAmmoId");
            Object ammoIdObject = getAmmoId.invoke(gunData);
            if (!(ammoIdObject instanceof net.minecraft.resources.ResourceLocation ammoId)) {
                return 0;
            }
            Class<?> builderClass = Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder");
            Method create = builderClass.getMethod("create");
            Method setId = builderClass.getMethod("setId", net.minecraft.resources.ResourceLocation.class);
            Method setCount = builderClass.getMethod("setCount", int.class);
            Method build = builderClass.getMethod("build");
            Object builder = create.invoke(null);
            builder = setId.invoke(builder, ammoId);
            builder = setCount.invoke(builder, 1);
            Object ammoStackObject = build.invoke(builder);
            if (!(ammoStackObject instanceof ItemStack sampleAmmo)) {
                return 0;
            }
            int total = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack != null && !stack.isEmpty() && ItemStack.isSameItemSameTags(stack, sampleAmmo)) {
                    total += stack.getCount();
                }
            }
            return total;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean isSameSquad(String viewerSquadLabel, String targetSquadLabel) {
        String normalizedTarget = normalizeSquadLabel(targetSquadLabel);
        return !viewerSquadLabel.isBlank() && viewerSquadLabel.equals(normalizedTarget);
    }

    private String normalizeSquadLabel(String squadLabel) {
        return squadLabel == null ? "" : squadLabel.trim();
    }

    private ServerPlayer resolveDirectEnemyTarget(RunningRuntimeState state, ServerPlayer owner, UUID targetPlayerId) {
        if (state == null || owner == null || targetPlayerId == null) {
            return null;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(targetPlayerId);
        if (target == null || !target.isAlive() || !state.getRoomPlayers().contains(targetPlayerId)) {
            return null;
        }
        RoomTeamId ownerTeam = state.getRoomTeams().get(owner.getUUID());
        RoomTeamId targetTeam = state.getRoomTeams().get(targetPlayerId);
        if (ownerTeam == null || targetTeam == null || ownerTeam == targetTeam) {
            return null;
        }
        if (!isMarkerDistanceValid(owner, target.blockPosition())) {
            return null;
        }
        return hasClearSightToTarget(owner, target) ? target : null;
    }

    /** GD656Mods: 玩家是否处于 SBW 无人机视角(手持 MonitorItem 且正在使用) */
    private boolean isPlayerInDroneView(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return isMonitorUsing(player.getMainHandItem()) || isMonitorUsing(player.getOffhandItem());
    }

    private boolean isMonitorUsing(net.minecraft.world.item.ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && stack.getItem().getClass().getName().equals("com.atsuishio.superbwarfare.item.misc.MonitorItem")
            && stack.getOrCreateTag().getBoolean("Using");
    }

    private net.minecraft.world.phys.Vec3 getPlayerOrDroneEyePosition(ServerPlayer player) {
        net.minecraft.world.entity.Entity drone = resolveLinkedDrone(player);
        if (drone != null) {
            return drone.position().add(0, drone.getBbHeight() * 0.5D, 0);
        }
        return player.getEyePosition();
    }

    private net.minecraft.world.phys.Vec3 getPlayerOrDronePosition(ServerPlayer player) {
        net.minecraft.world.entity.Entity drone = resolveLinkedDrone(player);
        if (drone != null) {
            return drone.position();
        }
        return player.position();
    }

    private net.minecraft.world.entity.Entity resolveLinkedDrone(ServerPlayer player) {
        if (!isPlayerInDroneView(player)) {
            return null;
        }
        net.minecraft.world.item.ItemStack stack = isMonitorUsing(player.getMainHandItem()) ? player.getMainHandItem() : player.getOffhandItem();
        String droneId = stack.getOrCreateTag().getString("LinkedDrone");
        if (droneId == null || droneId.isEmpty() || droneId.equals("none")) {
            return null;
        }
        try {
            java.util.UUID uuid = java.util.UUID.fromString(droneId);
            return player.level().getServer().getLevel(player.level().dimension()).getEntity(uuid);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isMarkerDistanceValid(ServerPlayer player, BlockPos blockPos) {
        if (player == null || blockPos == null) {
            return false;
        }
        return getPlayerOrDronePosition(player).distanceToSqr(Vec3.atCenterOf(blockPos)) <= MAX_MARKER_DISTANCE_SQR;
    }

    private boolean hasClearSightToTarget(ServerPlayer owner, ServerPlayer target) {
        if (owner == null || target == null) {
            return false;
        }
        Vec3 start = getPlayerOrDroneEyePosition(owner);
        for (Vec3 samplePoint : resolveSightSamplePoints(target)) {
            HitResult hit = owner.level().clip(new ClipContext(
                start,
                samplePoint,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                owner
            ));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return true;
            }
            if (start.distanceToSqr(hit.getLocation()) + 0.01D >= start.distanceToSqr(samplePoint)) {
                return true;
            }
        }
        return false;
    }

    private List<Vec3> resolveSightSamplePoints(ServerPlayer target) {
        Vec3 position = target.position();
        double height = target.getBbHeight();
        return List.of(
            position.add(0.0D, height * 0.25D, 0.0D),
            position.add(0.0D, height * 0.58D, 0.0D),
            position.add(0.0D, height * 0.90D, 0.0D)
        );
    }

    private BlockPos resolveDisplayPosition(RunningRuntimeState.TacticalMarker marker) {
        if (marker == null || marker.blockPos() == null) {
            return BlockPos.ZERO;
        }
        if ((marker.kind() != RunningRuntimeState.MarkerKind.DIRECT_ENEMY && marker.kind() != RunningRuntimeState.MarkerKind.AUTO_ENEMY) || marker.targetPlayerId() == null) {
            return marker.blockPos();
        }
        ServerPlayer target = server.getPlayerList().getPlayer(marker.targetPlayerId());
        if (target == null || !target.isAlive()) {
            return marker.blockPos();
        }
        return target.blockPosition().immutable();
    }

    private void tryAwardSpottingBonus(ServerPlayer player) {
        if (player == null || !consumeSpottingBonusWindow(player.getUUID(), System.currentTimeMillis())) {
            return;
        }
        KilliconScoreboardBridge.tryAwardConquestBonus(player, "SPOTTING", 1.0F, "");
    }

    private void showMarkerSuccessActionbar(ServerPlayer player, BlockPos markerPos, RunningRuntimeState.MarkerKind kind, UUID targetPlayerId) {
        if (player == null) {
            return;
        }
        if (kind == RunningRuntimeState.MarkerKind.DIRECT_ENEMY && targetPlayerId != null) {
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetPlayerId);
            String targetName = targetPlayer == null ? "未知敌人" : targetPlayer.getScoreboardName();
            ServerActionBarService.showText(player, ACTIONBAR_SOURCE_MARKER, "§7成功标记敌人 §c" + targetName, 35);
            return;
        }
        if (markerPos == null) {
            return;
        }
        String coords = markerPos.getX() + " " + markerPos.getY() + " " + markerPos.getZ();
        ServerActionBarService.showText(player, ACTIONBAR_SOURCE_MARKER, "§7成功标记坐标 §b" + coords, 35);
    }

    private void playMarkerFeedbackSound(ServerPlayer player, String soundId) {
        if (player == null || soundId == null || soundId.isBlank()) {
            return;
        }
        NetworkManager.sendToPlayer(player, ClientShortSoundPacket.oneShot(soundId, 1.0F, 1.0F));
    }

    private boolean consumeSpottingBonusWindow(UUID playerId, long nowMs) {
        if (playerId == null) {
            return false;
        }
        Deque<Long> times = spottingBonusTimesByPlayer.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        while (!times.isEmpty() && nowMs - times.peekFirst() > SPOTTING_BONUS_WINDOW_MS) {
            times.pollFirst();
        }
        if (times.size() >= 2) {
            return false;
        }
        times.addLast(nowMs);
        return true;
    }

    private String resolveSpottedEnemyCue(UUID targetPlayerId) {
        if (targetPlayerId == null) {
            return RoomThirdPersonAudioCueRegistry.PLAYER_SPOTTED_ENEMY;
        }
        ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetPlayerId);
        RunningRuntimeState state = targetPlayer == null
            ? null
            : RoomCoreRuntimeManager.of(server)
                .findRunningState(RoomCoreRuntimeManager.of(server).findPlayerRoomId(targetPlayer.getUUID()).orElse(""))
                .orElse(null);
        PlayerClassType classType = CustomLoadoutService.resolveCurrentClass(
            targetPlayer == null
                ? null
                : ConquestDataManager.of(server).playerData().getPlayerData(targetPlayer.getUUID()),
            state,
            targetPlayer == null ? null : targetPlayer.getUUID()
        );
        if (classType == PlayerClassType.ENGINEER) {
            return RoomThirdPersonAudioCueRegistry.PLAYER_SPOTTED_ENGINEER;
        }
        if (classType == PlayerClassType.RECON) {
            return RoomThirdPersonAudioCueRegistry.PLAYER_SPOTTED_RECON;
        }
        if (classType == PlayerClassType.SUPPORT) {
            return RoomThirdPersonAudioCueRegistry.PLAYER_SPOTTED_SUPPORT;
        }
        return RoomThirdPersonAudioCueRegistry.PLAYER_SPOTTED_ENEMY;
    }

    private int resolveLifetimeTicks(RunningRuntimeState.MarkerKind kind) {
        if (kind == RunningRuntimeState.MarkerKind.OBSERVE_GROUND) {
            return OBSERVE_GROUND_LIFETIME_TICKS;
        }
        if (kind == RunningRuntimeState.MarkerKind.DIRECT_ENEMY) {
            return DIRECT_SPOT_LIFETIME_TICKS;
        }
        if (kind == RunningRuntimeState.MarkerKind.AUTO_ENEMY) {
            return AUTO_SPOT_LIFETIME_TICKS;
        }
        return MARKER_LIFETIME_TICKS;
    }

    private boolean pruneExpiredMarkersFromMap(Map<UUID, RunningRuntimeState.TacticalMarker> markerMap, long gameTick) {
        boolean changed = false;
        for (Map.Entry<UUID, RunningRuntimeState.TacticalMarker> entry : List.copyOf(markerMap.entrySet())) {
            RunningRuntimeState.TacticalMarker marker = entry.getValue();
            if (marker == null || marker.expireTick() <= gameTick) {
                markerMap.remove(entry.getKey());
                changed = true;
                continue;
            }
            if ((marker.kind() == RunningRuntimeState.MarkerKind.DIRECT_ENEMY || marker.kind() == RunningRuntimeState.MarkerKind.AUTO_ENEMY)
                && marker.targetPlayerId() != null) {
                ServerPlayer target = server.getPlayerList().getPlayer(marker.targetPlayerId());
                if (target == null || !target.isAlive()) {
                    markerMap.remove(entry.getKey());
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** GD656Mods: 清理无人机视角 AOE 索敌标记 —— 过期或目标死亡/离线的标记逐条移除 */
    private boolean pruneExpiredDroneMarkers(RunningRuntimeState state, long gameTick) {
        boolean changed = false;
        for (Map.Entry<UUID, Map<UUID, RunningRuntimeState.TacticalMarker>> ownerEntry : List.copyOf(state.getDroneSpotMarkersByOwner().entrySet())) {
            UUID ownerId = ownerEntry.getKey();
            Map<UUID, RunningRuntimeState.TacticalMarker> ownedMarkers = ownerEntry.getValue();
            if (ownedMarkers == null) {
                state.getDroneSpotMarkersByOwner().remove(ownerId);
                changed = true;
                continue;
            }
            for (Map.Entry<UUID, RunningRuntimeState.TacticalMarker> targetEntry : List.copyOf(ownedMarkers.entrySet())) {
                RunningRuntimeState.TacticalMarker marker = targetEntry.getValue();
                boolean expired = marker == null || marker.expireTick() <= gameTick;
                if (!expired && marker.kind() == RunningRuntimeState.MarkerKind.DIRECT_ENEMY && marker.targetPlayerId() != null) {
                    ServerPlayer target = server.getPlayerList().getPlayer(marker.targetPlayerId());
                    expired = target == null || !target.isAlive();
                }
                if (expired) {
                    ownedMarkers.remove(targetEntry.getKey());
                    changed = true;
                }
            }
            if (ownedMarkers.isEmpty()) {
                state.getDroneSpotMarkersByOwner().remove(ownerId);
            }
        }
        return changed;
    }

    private boolean pruneExpiredGameplayMarkers(Map<String, RunningRuntimeState.TeamScopedTacticalMarker> markerMap, long gameTick) {
        boolean changed = false;
        for (Map.Entry<String, RunningRuntimeState.TeamScopedTacticalMarker> entry : List.copyOf(markerMap.entrySet())) {
            RunningRuntimeState.TeamScopedTacticalMarker marker = entry.getValue();
            if (marker == null || marker.marker() == null || marker.marker().expireTick() <= gameTick) {
                markerMap.remove(entry.getKey());
                changed = true;
                continue;
            }
            UUID targetPlayerId = marker.marker().targetPlayerId();
            if (targetPlayerId == null) {
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(targetPlayerId);
            if (target == null || !target.isAlive()) {
                markerMap.remove(entry.getKey());
                changed = true;
            }
        }
        return changed;
    }

    private void appendVisibleMarkers(
        List<RoomMarkerSyncPacket.MarkerEntry> entries,
        RunningRuntimeState state,
        Map<UUID, RunningRuntimeState.TacticalMarker> markerMap,
        RoomTeamId viewerTeam,
        long gameTick
    ) {
        for (Map.Entry<UUID, RunningRuntimeState.TacticalMarker> entry : markerMap.entrySet()) {
            appendMarkerEntry(entries, state, entry.getKey(), entry.getValue(), viewerTeam, gameTick);
        }
    }

    /** GD656Mods: 无人机视角 AOE 索敌标记同步 —— 同一玩家的多个敌人标记全部下发 */
    private void appendDroneSpotMarkers(
        List<RoomMarkerSyncPacket.MarkerEntry> entries,
        RunningRuntimeState state,
        Map<UUID, Map<UUID, RunningRuntimeState.TacticalMarker>> droneMarkers,
        RoomTeamId viewerTeam,
        long gameTick
    ) {
        for (Map.Entry<UUID, Map<UUID, RunningRuntimeState.TacticalMarker>> ownerEntry : droneMarkers.entrySet()) {
            Map<UUID, RunningRuntimeState.TacticalMarker> ownedMarkers = ownerEntry.getValue();
            if (ownedMarkers == null) {
                continue;
            }
            for (RunningRuntimeState.TacticalMarker marker : ownedMarkers.values()) {
                appendMarkerEntry(entries, state, ownerEntry.getKey(), marker, viewerTeam, gameTick);
            }
        }
    }

    private void appendMarkerEntry(
        List<RoomMarkerSyncPacket.MarkerEntry> entries,
        RunningRuntimeState state,
        UUID ownerId,
        RunningRuntimeState.TacticalMarker marker,
        RoomTeamId viewerTeam,
        long gameTick
    ) {
        if (ownerId == null || marker == null) {
            return;
        }
        RoomTeamId ownerTeam = state.getRoomTeams().get(ownerId);
        if (ownerTeam != viewerTeam) {
            return;
        }
        int remainingTicks = (int) Math.max(0L, marker.expireTick() - gameTick);
        if (remainingTicks <= 0) {
            return;
        }
        int ageTicks = (int) Math.max(0L, gameTick - marker.placedTick());
        String ownerName = resolveOwnerName(ownerId);
        BlockPos displayPos = resolveDisplayPosition(marker);
        entries.add(new RoomMarkerSyncPacket.MarkerEntry(
            ownerId,
            ownerName,
            marker.kind().name(),
            displayPos.getX(),
            displayPos.getY(),
            displayPos.getZ(),
            marker.targetPlayerId(),
            ageTicks,
            remainingTicks
        ));
    }

    private void appendGameplayVisibleMarkers(
        List<RoomMarkerSyncPacket.MarkerEntry> entries,
        Map<String, RunningRuntimeState.TeamScopedTacticalMarker> markerMap,
        RoomTeamId viewerTeam,
        long gameTick
    ) {
        for (RunningRuntimeState.TeamScopedTacticalMarker scopedMarker : markerMap.values()) {
            if (scopedMarker == null || scopedMarker.marker() == null || scopedMarker.visibleTeam() != viewerTeam) {
                continue;
            }
            RunningRuntimeState.TacticalMarker marker = scopedMarker.marker();
            int remainingTicks = (int) Math.max(0L, marker.expireTick() - gameTick);
            if (remainingTicks <= 0) {
                continue;
            }
            int ageTicks = (int) Math.max(0L, gameTick - marker.placedTick());
            BlockPos displayPos = resolveDisplayPosition(marker);
            entries.add(new RoomMarkerSyncPacket.MarkerEntry(
                scopedMarker.markerId(),
                scopedMarker.ownerName(),
                marker.kind().name(),
                displayPos.getX(),
                displayPos.getY(),
                displayPos.getZ(),
                marker.targetPlayerId(),
                ageTicks,
                remainingTicks
            ));
        }
    }
}
