package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomMarkerSyncPacket;
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

public final class RoomMarkerRuntimeService {
    private static final int MARKER_LIFETIME_TICKS = 30 * 20;
    private static final int DIRECT_SPOT_LIFETIME_TICKS = 5 * 20;
    private static final int AUTO_SPOT_LIFETIME_TICKS = 2 * 20;
    private static final int MARKER_SYNC_INTERVAL_TICKS = 5;
    private static final double MAX_MARKER_DISTANCE_SQR = 256.0D * 256.0D;
    private static final String ACTIONBAR_SOURCE_MARKER = "room_marker";
    private static final long SPOTTING_BONUS_WINDOW_MS = 60000L;

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
        if (resolvedKind == RunningRuntimeState.MarkerKind.DIRECT_ENEMY || resolvedKind == RunningRuntimeState.MarkerKind.AUTO_ENEMY) {
            ServerPlayer targetPlayer = resolveDirectEnemyTarget(state, player, targetPlayerId);
            if (targetPlayer == null) {
                return false;
            }
            normalizedPos = targetPlayer.blockPosition().immutable();
            resolvedTargetPlayerId = targetPlayer.getUUID();
        } else if (!isMarkerDistanceValid(player, normalizedPos)) {
            return false;
        }
        Map<UUID, RunningRuntimeState.TacticalMarker> markerMap = resolvedKind == RunningRuntimeState.MarkerKind.AUTO_ENEMY
            ? state.getAutoTacticalMarkersByOwner()
            : state.getTacticalMarkersByOwner();
        long expireTick = gameTick + resolveLifetimeTicks(resolvedKind);
        long directSpotExpireTick = resolvedKind == RunningRuntimeState.MarkerKind.DIRECT_ENEMY ? gameTick + DIRECT_SPOT_LIFETIME_TICKS : -1L;
        markerMap.put(
            ownerId,
            new RunningRuntimeState.TacticalMarker(
                normalizedPos,
                resolvedKind,
                resolvedTargetPlayerId,
                gameTick,
                expireTick,
                directSpotExpireTick
            )
        );
        if (resolvedKind != RunningRuntimeState.MarkerKind.AUTO_ENEMY) {
            showMarkerSuccessActionbar(player, normalizedPos, resolvedKind, resolvedTargetPlayerId);
        }
        if (resolvedKind == RunningRuntimeState.MarkerKind.DIRECT_ENEMY) {
            tryAwardSpottingBonus(player);
            FrontlineVoiceBridge.playCue(player, resolveSpottedEnemyCue(targetPlayerId));
        }
        syncRunningRoom(room, state, gameTick);
        return true;
    }

    public boolean cancelMarker(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick) {
        if (!isPlayerAllowed(room, state, player)) {
            return false;
        }
        RunningRuntimeState.TacticalMarker removed = state.getTacticalMarkersByOwner().remove(player.getUUID());
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
            if (ownerId == null || marker == null || marker.kind() != RunningRuntimeState.MarkerKind.DIRECT_ENEMY) {
                continue;
            }
            if (!victimId.equals(marker.targetPlayerId()) || marker.directSpotExpireTick() < gameTick) {
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner != null) {
                if (killer != null && killer.getUUID().equals(ownerId)) {
                    KilliconScoreboardBridge.tryAwardConquestBonus(killer, "SPOTTING_KILL", 1.0F, "", victim.getId(), victim.getScoreboardName());
                } else if (killer != null) {
                    RoomTeamId ownerTeam = state.getRoomTeams().get(ownerId);
                    if (ownerTeam != null && ownerTeam == killerTeam) {
                        KilliconScoreboardBridge.tryAwardConquestBonus(owner, "SPOTTING_TEAM_ASSIST", 1.0F, "", victim.getId(), victim.getScoreboardName());
                    }
                }
            }
            state.getTacticalMarkersByOwner().remove(ownerId);
            changed = true;
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

    private boolean pruneExpiredMarkers(RunningRuntimeState state, long gameTick) {
        boolean changed = false;
        changed |= pruneExpiredMarkersFromMap(state.getTacticalMarkersByOwner(), gameTick);
        changed |= pruneExpiredMarkersFromMap(state.getAutoTacticalMarkersByOwner(), gameTick);
        changed |= pruneExpiredGameplayMarkers(state.getGameplayTacticalMarkersByKey(), gameTick);
        return changed;
    }

    private List<RoomMarkerSyncPacket.MarkerEntry> buildVisibleMarkers(RunningRuntimeState state, RoomTeamId viewerTeam, long gameTick) {
        List<RoomMarkerSyncPacket.MarkerEntry> entries = new ArrayList<>();
        appendVisibleMarkers(entries, state, state.getTacticalMarkersByOwner(), viewerTeam, gameTick);
        appendVisibleMarkers(entries, state, state.getAutoTacticalMarkersByOwner(), viewerTeam, gameTick);
        appendGameplayVisibleMarkers(entries, state.getGameplayTacticalMarkersByKey(), viewerTeam, gameTick);
        return entries;
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

    private boolean isMarkerDistanceValid(ServerPlayer player, BlockPos blockPos) {
        if (player == null || blockPos == null) {
            return false;
        }
        return player.position().distanceToSqr(Vec3.atCenterOf(blockPos)) <= MAX_MARKER_DISTANCE_SQR;
    }

    private boolean hasClearSightToTarget(ServerPlayer owner, ServerPlayer target) {
        if (owner == null || target == null) {
            return false;
        }
        Vec3 start = owner.getEyePosition();
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
            UUID ownerId = entry.getKey();
            RunningRuntimeState.TacticalMarker marker = entry.getValue();
            if (ownerId == null || marker == null) {
                continue;
            }
            RoomTeamId ownerTeam = state.getRoomTeams().get(ownerId);
            if (ownerTeam != viewerTeam) {
                continue;
            }
            int remainingTicks = (int) Math.max(0L, marker.expireTick() - gameTick);
            if (remainingTicks <= 0) {
                continue;
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
