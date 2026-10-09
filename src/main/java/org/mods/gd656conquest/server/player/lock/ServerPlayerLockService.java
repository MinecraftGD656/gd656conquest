package org.mods.gd656conquest.server.player.lock;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.common.player.lock.PlayerLockSnapshot;
import org.mods.gd656conquest.common.player.lock.PlayerLockStrength;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.PlayerLockStatePacket;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class ServerPlayerLockService {
    private static final Map<MinecraftServer, ServerPlayerLockService> INSTANCES = new WeakHashMap<>();
    private static final int DEBUG_LOCK_DURATION_TICKS = 10 * 20;

    private final MinecraftServer server;
    private final Map<UUID, DebugOverride> debugOverrides = new LinkedHashMap<>();
    private final Map<UUID, PlayerLockSnapshot> lastSentSnapshots = new LinkedHashMap<>();

    private ServerPlayerLockService(MinecraftServer server) {
        this.server = server;
    }

    public static ServerPlayerLockService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, ServerPlayerLockService::new);
        }
    }

    public static void clearAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }

    public void onPlayerTick(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        long gameTick = player.server.getTickCount();
        clearExpiredDebugOverrides(gameTick);
        suppressMountedVehicleMotionIfLocked(player, gameTick);
        syncPlayer(player);
    }

    public void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        debugOverrides.remove(playerId);
        lastSentSnapshots.remove(playerId);
    }

    public void clearPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        debugOverrides.remove(playerId);
        PlayerLockSnapshot previous = lastSentSnapshots.remove(playerId);
        if (previous == null || previous.active()) {
            NetworkManager.sendToPlayer(player, new PlayerLockStatePacket(PlayerLockSnapshot.none()));
        }
    }

    public void applyDebugLock(ServerPlayer player, PlayerLockStrength strength, long currentTick) {
        if (player == null || strength == null) {
            return;
        }
        UUID playerId = player.getUUID();
        if (strength == PlayerLockStrength.NONE) {
            debugOverrides.remove(playerId);
        } else {
            debugOverrides.put(playerId, new DebugOverride(strength, currentTick + DEBUG_LOCK_DURATION_TICKS));
        }
        syncPlayer(player);
    }

    public boolean shouldBlockInventoryOperation(ServerPlayer player) {
        return resolve(player).lockInventory();
    }

    public boolean shouldIgnoreAdminInventoryBypass(ServerPlayer player) {
        PlayerLockSnapshot snapshot = resolve(player);
        return snapshot.lockInventory() && snapshot.reason().startsWith("debug_");
    }

    public boolean shouldBlockGameplayInteraction(ServerPlayer player) {
        PlayerLockSnapshot snapshot = resolve(player);
        return snapshot.lockGameplayInteraction() || snapshot.lockHotbar();
    }

    public PlayerLockSnapshot resolve(ServerPlayer player) {
        if (player == null || player.server == null) {
            return PlayerLockSnapshot.none();
        }
        long gameTick = player.server.getTickCount();
        DebugOverride debugOverride = resolveDebugOverride(player.getUUID(), gameTick);
        if (debugOverride != null) {
            return buildDebugSnapshot(debugOverride.strength());
        }
        RoomLockContext context = resolveRoomContext(player);
        if (context == null || context.room() == null) {
            return PlayerLockSnapshot.none();
        }
        RunningRuntimeState runningState = context.runningState();
        UUID playerId = player.getUUID();
        if (isMatchEndingActive(context.room(), runningState) && isPlayerInMatchEndViewPhase(runningState, playerId)) {
            return buildSnapshot(PlayerLockStrength.UI_CURSOR, "match_end", true, false, false, false, false, false, true);
        }
        if (runningState != null && runningState.isInitialMovementLockActive(playerId, gameTick)) {
            return buildSnapshot(PlayerLockStrength.MOVEMENT, "initial_movement_lock", false, false, false, false, false, false, false);
        }
        if (runningState != null && (runningState.isPlayerPendingDowned(playerId) || runningState.isPlayerDowned(playerId))) {
            return buildSnapshot(PlayerLockStrength.MOVEMENT, "downed", false, true, false, false, false, false, false);
        }
        if (runningState != null && runningState.isPlayerBeingRevived(playerId)) {
            return buildSnapshot(PlayerLockStrength.MOVEMENT, "being_revived", false, true, false, false, false, false, false);
        }
        if (runningState != null && runningState.getDeployReadyTicks().containsKey(playerId)) {
            if (context.room().isArenaGameplay()
                && !runningState.getDeployViewActivePlayers().contains(playerId)) {
                return buildSnapshot(PlayerLockStrength.MOVEMENT, "deathmatch_respawn_wait", true, true, false, false, false, false, false);
            }
            return buildSnapshot(PlayerLockStrength.UI_CURSOR, "deploy_ui", false, false, true, true, false, false, false);
        }
        if (runningState != null && runningState.getRespawnFinalizeTicks().containsKey(playerId)) {
            if (context.room().isArenaGameplay()) {
                return buildSnapshot(PlayerLockStrength.MOVEMENT, "deathmatch_respawn_finalize", true, true, false, false, false, false, false);
            }
            return buildSnapshot(PlayerLockStrength.HARD_LOCK, "respawn_finalize", false, false, false, false, false, false, false);
        }
        if (runningState != null && runningState.isInRespawnFlow(playerId)) {
            if (context.room().isArenaGameplay()) {
                return buildSnapshot(PlayerLockStrength.MOVEMENT, "deathmatch_respawn_flow", true, true, false, false, false, false, false);
            }
            return buildSnapshot(PlayerLockStrength.HARD_LOCK, "respawn_flow", true, true, false, false, false, false, false);
        }
        if (player.hasPermissions(2)) {
            return PlayerLockSnapshot.none();
        }
        if (context.room().lifecycleState() == RoomLifecycleState.MATCHING || context.isMatchingActive()) {
            return buildSnapshot(PlayerLockStrength.INVENTORY, "matching", false, false, false, false, false, false, false);
        }
        if (context.roomInventoryLockEnabled()) {
            return buildSnapshot(PlayerLockStrength.INVENTORY, "room_inventory_lock", false, false, false, false, false, false, false);
        }
        return PlayerLockSnapshot.none();
    }

    public void syncPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerLockSnapshot snapshot = resolve(player);
        UUID playerId = player.getUUID();
        PlayerLockSnapshot lastSnapshot = lastSentSnapshots.get(playerId);
        if (snapshot.equals(lastSnapshot)) {
            return;
        }
        lastSentSnapshots.put(playerId, snapshot);
        NetworkManager.sendToPlayer(player, new PlayerLockStatePacket(snapshot));
    }

    private void clearExpiredDebugOverrides(long currentTick) {
        debugOverrides.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expireTick() <= currentTick);
    }

    private DebugOverride resolveDebugOverride(UUID playerId, long currentTick) {
        if (playerId == null) {
            return null;
        }
        DebugOverride override = debugOverrides.get(playerId);
        if (override == null) {
            return null;
        }
        if (override.expireTick() <= currentTick) {
            debugOverrides.remove(playerId);
            return null;
        }
        return override;
    }

    private RoomLockContext resolveRoomContext(ServerPlayer player) {
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        UUID playerId = player.getUUID();
        Optional<String> roomId = roomManager.findPlayerRoomId(playerId);
        if (roomId.isPresent()) {
            RoomDefinition room = roomManager.findRoom(roomId.get()).orElse(null);
            if (room == null) {
                return null;
            }
            MatchingRuntimeState matchingState = roomManager.findMatchingState(roomId.get()).orElse(null);
            RunningRuntimeState runningState = roomManager.findRunningState(roomId.get()).orElse(null);
            boolean matchingActive = matchingState != null && matchingState.getQueuePlayers().contains(playerId);
            return new RoomLockContext(room, matchingState, runningState, matchingActive, room.commonConfig().disableInventoryChange());
        }
        if (player.level() == null || player.level().dimension().location() == null) {
            return null;
        }
        String dimensionId = player.level().dimension().location().toString();
        RoomDefinition room = ConquestDataManager.of(server).roomDefinitions().findByDimensionId(dimensionId).orElse(null);
        if (room == null) {
            return null;
        }
        return new RoomLockContext(room, null, null, false, room.commonConfig().disableInventoryChange());
    }

    private PlayerLockSnapshot buildDebugSnapshot(PlayerLockStrength strength) {
        return buildSnapshot(
            strength,
            "debug_" + strength.level(),
            false,
            false,
            false,
            false,
            false,
            false,
            false
        );
    }

    private boolean isMatchEndingActive(RoomDefinition room, RunningRuntimeState runningState) {
        return (room != null && room.lifecycleState() == RoomLifecycleState.ENDING)
            || (runningState != null && runningState.getResultSequenceEndTick() > 0L);
    }

    private boolean isPlayerInMatchEndViewPhase(RunningRuntimeState runningState, UUID playerId) {
        if (runningState == null || playerId == null) {
            return false;
        }
        if (!runningState.isResultViewApplied()) {
            return false;
        }
        return !runningState.getResultViewTransferPending().contains(playerId);
    }

    private PlayerLockSnapshot buildSnapshot(
        PlayerLockStrength strength,
        String reason,
        boolean hideHeldItems,
        boolean forceHudOnly,
        boolean forceVisibleHud,
        boolean deployUiActive,
        boolean forceFirstPerson,
        boolean forceCrouch,
        boolean endingState
    ) {
        int level = strength == null ? 0 : strength.level();
        boolean inventoryLocked = level >= PlayerLockStrength.INVENTORY.level();
        boolean interactionLocked = level >= PlayerLockStrength.ACTION.level();
        boolean movementLocked = level >= PlayerLockStrength.MOVEMENT.level();
        boolean lookLocked = level >= PlayerLockStrength.UI_CURSOR.level();
        boolean releaseMouse = strength == PlayerLockStrength.UI_CURSOR;
        return new PlayerLockSnapshot(
            strength,
            reason,
            inventoryLocked,
            interactionLocked,
            interactionLocked,
            movementLocked,
            lookLocked,
            releaseMouse,
            hideHeldItems,
            forceHudOnly,
            forceVisibleHud,
            forceFirstPerson,
            forceCrouch,
            deployUiActive,
            endingState
        );
    }

    private void suppressMountedVehicleMotionIfLocked(ServerPlayer player, long gameTick) {
        if (player == null || player.server == null) {
            return;
        }
        RoomLockContext context = resolveRoomContext(player);
        RunningRuntimeState runningState = context == null ? null : context.runningState();
        if (runningState == null || !runningState.isInitialMovementLockActive(player.getUUID(), gameTick)) {
            return;
        }
        Entity vehicle = player.getVehicle();
        if (!VehicleCompatFacade.isVehicleEntity(vehicle)) {
            return;
        }
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        vehicle.setDeltaMovement(0.0D, 0.0D, 0.0D);
        vehicle.hurtMarked = true;
    }

    private record DebugOverride(PlayerLockStrength strength, long expireTick) {
    }

    private record RoomLockContext(
        RoomDefinition room,
        MatchingRuntimeState matchingState,
        RunningRuntimeState runningState,
        boolean isMatchingActive,
        boolean roomInventoryLockEnabled
    ) {
    }
}
