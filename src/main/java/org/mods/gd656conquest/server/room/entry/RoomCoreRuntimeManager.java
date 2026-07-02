package org.mods.gd656conquest.server.room.entry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.AdminDebugOverlayPacket;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;
import org.mods.gd656conquest.server.room.bootstrap.defaults.DefaultRoomCoreBootstrap;
import org.mods.gd656conquest.server.room.core.RoomJoinResult;
import org.mods.gd656conquest.server.room.core.RoomCoreService;
import org.mods.gd656conquest.server.room.core.RoomGroupPlacementPlan;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerLoadoutService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.LinkedHashSet;

public final class RoomCoreRuntimeManager {
    private static final Map<MinecraftServer, RoomCoreRuntimeManager> INSTANCES = new WeakHashMap<>();
    private static final String ROOM_SERVICE_NOT_INITIALIZED = "房间服务尚未初始化";

    private final MinecraftServer server;
    private final Set<UUID> debugOverlayEnabledPlayers = new LinkedHashSet<>();
    private RoomCoreService coreService;

    private RoomCoreRuntimeManager(MinecraftServer server) {
        this.server = server;
    }

    public static RoomCoreRuntimeManager of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, RoomCoreRuntimeManager::new);
        }
    }

    public void initialize() {
        ServerActionBarService.initialize(server);
        coreService = DefaultRoomCoreBootstrap.bootstrap(server);
        ServerLogger.info("房间服务已初始化，共装载 %d 个房间定义", coreService.listRooms().size());
    }

    public void runStartupCleanRoomPass(long gameTick) {
        if (coreService == null) {
            return;
        }
        List<RoomDefinition> roomsToClean = coreService.listRooms().stream()
            .filter(room -> room != null && room.lifecycleState() != RoomLifecycleState.MAINTENANCE)
            .toList();
        if (roomsToClean.isEmpty()) {
            ServerLogger.info("启动房间标准 cleanRoom 检查完成，本次无非维护中房间需要处理");
            return;
        }
        ServerLogger.info("启动房间标准 cleanRoom 开始，共 %d 个非维护中房间待处理", roomsToClean.size());
        RoomAdminLifecycleService lifecycleService = new RoomAdminLifecycleService(server);
        int successCount = 0;
        int failureCount = 0;
        for (RoomDefinition room : roomsToClean) {
            String roomId = room.roomId();
            RoomLifecycleState previousState = room.lifecycleState();
            ServerLogger.info("启动标准 cleanRoom: %s | 启动前状态=%s", roomId, previousState == null ? "-" : previousState.name());
            RoomAdminLifecycleService.OperationResult result = lifecycleService.cleanRoom(
                roomId,
                gameTick,
                resolveStartupReopenState(previousState)
            );
            if (result.success()) {
                successCount++;
                ServerLogger.info("启动标准 cleanRoom 完成: %s | %s", roomId, result.message());
                continue;
            }
            failureCount++;
            ServerLogger.failure("启动标准 cleanRoom 失败: %s | %s", roomId, result.message());
        }
        ServerLogger.info("启动房间标准 cleanRoom 结束，成功=%d，失败=%d", successCount, failureCount);
    }

    private RoomLifecycleState resolveStartupReopenState(RoomLifecycleState previousState) {
        if (previousState == RoomLifecycleState.DISABLED) {
            return RoomLifecycleState.DISABLED;
        }
        if (previousState == RoomLifecycleState.MAINTENANCE) {
            return RoomLifecycleState.MAINTENANCE;
        }
        return RoomLifecycleState.IDLE;
    }

    public RoomCoreService coreService() {
        if (coreService == null) {
            throw new IllegalStateException(ROOM_SERVICE_NOT_INITIALIZED);
        }
        return coreService;
    }

    public boolean isInitialized() {
        return coreService != null;
    }

    public List<RoomDefinition> listRooms() {
        if (coreService == null) {
            return List.of();
        }
        return coreService.listRooms();
    }

    public Optional<RoomDefinition> findRoom(String roomId) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.findRoom(roomId);
    }

    public Optional<RunningRuntimeState> findRunningState(String roomId) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.findRunningState(roomId);
    }

    public Optional<RoomDefinition> hotUpdateRoomDefinition(RoomDefinition definition, long gameTick) {
        if (coreService == null || definition == null) {
            return Optional.empty();
        }
        return Optional.of(coreService.hotUpdateRoomDefinition(definition, gameTick));
    }

    public Optional<MatchingRuntimeState> findMatchingState(String roomId) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.findMatchingState(roomId);
    }

    public Optional<RoomTeamId> findQueueTeam(String roomId, UUID playerId) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.findMatchingState(roomId)
            .map(MatchingRuntimeState::getQueueTeams)
            .map(teams -> teams.get(playerId));
    }

    public Optional<RoomTeamId> findRoomTeam(String roomId, UUID playerId) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.findRoomTeam(roomId, playerId);
    }

    public Optional<RoomTeamId> findCurrentTeam(UUID playerId) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.findCurrentTeam(playerId);
    }

    public Optional<String> findPlayerSquadLabel(UUID playerId) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.findSquadLabel(playerId);
    }

    public String getPlayerTeamDisplayName(UUID playerId) {
        if (coreService == null) {
            return "-";
        }
        return coreService.getPlayerTeamDisplayName(playerId);
    }

    public int countRoomPlayers(String roomId) {
        if (coreService == null) {
            return 0;
        }
        return coreService.countRoomPlayers(roomId);
    }

    public String queueTeamJoinChange(String roomId, UUID targetPlayerId, RoomTeamId targetTeam, String operatorName, long gameTick) {
        if (coreService == null) {
            return ROOM_SERVICE_NOT_INITIALIZED;
        }
        return coreService.queueTeamJoinChange(roomId, targetPlayerId, targetTeam, operatorName, gameTick);
    }

    public String switchPlayerToOppositeTeam(String roomId, UUID targetPlayerId, String operatorName, long gameTick) {
        if (coreService == null) {
            return ROOM_SERVICE_NOT_INITIALIZED;
        }
        return coreService.switchPlayerToOppositeTeam(roomId, targetPlayerId, operatorName, gameTick);
    }

    public String acceptAutoTeamSwitch(String roomId, UUID playerId, long gameTick) {
        if (coreService == null) {
            return ROOM_SERVICE_NOT_INITIALIZED;
        }
        return coreService.acceptAutoTeamSwitch(roomId, playerId, gameTick);
    }

    public Optional<String> findPlayerRoomId(UUID playerId) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.findPlayerRoomId(playerId);
    }

    public Optional<String> findRejoinableRoomId(UUID playerId) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.findRejoinableRoomId(playerId);
    }

    public boolean canJoinInProgressRoom(UUID playerId, String roomId) {
        if (coreService == null) {
            return false;
        }
        return coreService.canJoinInProgressRoom(playerId, roomId);
    }

    public Optional<RoomGroupPlacementPlan> planGroupJoinPlacement(String roomId, int groupSize) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.planGroupJoinPlacement(roomId, groupSize);
    }

    public Optional<RoomGroupPlacementPlan> resolvePlayerPlacement(String roomId, UUID playerId) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.resolvePlayerPlacement(roomId, playerId);
    }

    public Optional<String> reserveSquadLabel(String roomId, RoomTeamId teamId) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.reserveSquadLabel(roomId, teamId);
    }

    public boolean canFitGroupInTeam(String roomId, RoomTeamId teamId, int requiredSlots) {
        if (coreService == null) {
            return false;
        }
        return coreService.canFitGroupInTeam(roomId, teamId, requiredSlots);
    }

    public boolean applyGroupPlacement(String roomId, UUID playerId, RoomTeamId targetTeam, String squadLabel, long gameTick) {
        if (coreService == null) {
            return false;
        }
        return coreService.applyGroupPlacement(roomId, playerId, targetTeam, squadLabel, gameTick);
    }

    public void resyncPlayerPresentation(ServerPlayer player, long gameTick) {
        if (coreService == null) {
            return;
        }
        coreService.resyncPlayerPresentation(player, gameTick);
    }

    public Optional<RoomJoinResult> joinMatching(UUID playerId, String roomId, long gameTick) {
        if (coreService == null) {
            return Optional.empty();
        }
        return coreService.joinMatching(playerId, roomId, gameTick);
    }

    public void leaveMatching(UUID playerId) {
        if (coreService == null) {
            return;
        }
        coreService.leaveMatching(playerId);
    }

    public void removePlayerFromRoom(UUID playerId) {
        if (coreService == null) {
            return;
        }
        coreService.removePlayerFromRoom(playerId);
    }

    public String sendPlayerToHub(ServerPlayer player) {
        if (coreService == null) {
            return ROOM_SERVICE_NOT_INITIALIZED;
        }
        return coreService.sendPlayerToHub(player, server.getTickCount());
    }

    public String requestRedeploy(ServerPlayer player) {
        if (coreService == null) {
            return ROOM_SERVICE_NOT_INITIALIZED;
        }
        return coreService.requestRedeploy(player);
    }

    public boolean handleDownedAction(ServerPlayer player, String action, long gameTick) {
        if (coreService == null) {
            return false;
        }
        return coreService.handleDownedAction(player, action, gameTick);
    }

    public boolean handlePlayerDeath(UUID playerId, RoomPosition deathPosition, int killerEntityId, long gameTick) {
        if (coreService == null) {
            return false;
        }
        return coreService.handlePlayerDeath(playerId, deathPosition, killerEntityId, gameTick);
    }

    public boolean handlePlayerDeathEvent(ServerPlayer player, RoomPosition deathPosition, int killerEntityId, long gameTick) {
        if (coreService == null) {
            return false;
        }
        return coreService.handlePlayerDeathEvent(player, deathPosition, killerEntityId, gameTick);
    }

    public boolean handlePlayerLogout(UUID playerId, long gameTick) {
        if (coreService == null) {
            return false;
        }
        return coreService.handlePlayerLogout(playerId, gameTick);
    }

    public boolean startGameplay(String roomId, long gameTick) {
        if (coreService == null) {
            return false;
        }
        return coreService.startGameplay(roomId, gameTick).isPresent();
    }

    public int evacuateRoom(String roomId, long gameTick) {
        if (coreService == null) {
            return 0;
        }
        return coreService.evacuateRoom(roomId, gameTick);
    }

    public boolean joinMaintenanceRoom(String roomId, ServerPlayer player) {
        if (coreService == null) {
            return false;
        }
        return coreService.joinMaintenanceRoom(roomId, player);
    }

    public boolean prepareRespawnAnchor(net.minecraft.server.level.ServerPlayer player) {
        if (coreService == null) {
            return false;
        }
        return coreService.prepareRespawnAnchor(player);
    }

    public boolean applyRespawnPostProcess(net.minecraft.server.level.ServerPlayer player) {
        if (coreService == null) {
            return false;
        }
        return coreService.applyRespawnPostProcess(player);
    }

    public boolean handleDeploySelection(ServerPlayer player, String action, String kind, String targetId, long gameTick) {
        if (coreService == null) {
            return false;
        }
        return coreService.handleDeploySelection(player, action, kind, targetId, gameTick);
    }

    public boolean handleMarkerAction(ServerPlayer player, String action, String kind, BlockPos blockPos, UUID targetPlayerId, long gameTick) {
        if (coreService == null) {
            return false;
        }
        return coreService.handleMarkerAction(player, action, kind, blockPos, targetPlayerId, gameTick);
    }

    public void handleMarkedPlayerDeath(ServerPlayer victim, ServerPlayer killer, long gameTick) {
        if (coreService == null) {
            return;
        }
        coreService.handleMarkedPlayerDeath(victim, killer, gameTick);
    }

    public boolean shouldCancelDamage(ServerPlayer player) {
        if (coreService == null) {
            return false;
        }
        return coreService.shouldCancelDamage(player);
    }

    public boolean shouldBlockPassiveRegen(ServerPlayer player) {
        if (coreService == null) {
            return false;
        }
        return coreService.shouldBlockPassiveRegen(player);
    }

    public boolean tryUseSquadBeaconItem(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        if (coreService == null) {
            return false;
        }
        return coreService.tryUseSquadBeaconItem(player, hand);
    }

    public boolean markPlayerDealtDamage(UUID attackerId, UUID targetId, long gameTick) {
        if (coreService == null) {
            return false;
        }
        return coreService.markPlayerDealtDamage(attackerId, targetId, gameTick);
    }

    public boolean shouldCancelDamageToSquadBeacon(ServerPlayer attacker, net.minecraft.world.entity.Entity beacon) {
        if (coreService == null) {
            return false;
        }
        return coreService.shouldCancelDamageToSquadBeacon(attacker, beacon);
    }

    public boolean shouldCancelDamageToVehicle(ServerPlayer attacker, Entity vehicle) {
        if (coreService == null) {
            return false;
        }
        return coreService.shouldCancelDamageToVehicle(attacker, vehicle);
    }

    public boolean isSameCampPlayer(ServerPlayer left, ServerPlayer right) {
        if (coreService == null) {
            return false;
        }
        return coreService.isSameCampPlayer(left, right);
    }

    public RoomPlayerLoadoutService.AmmoSupplyResult trySupplyAmmoByBag(ServerPlayer player, int maxRounds) {
        if (coreService == null) {
            return RoomPlayerLoadoutService.AmmoSupplyResult.EMPTY;
        }
        return coreService.trySupplyAmmoByBag(player, maxRounds);
    }

    public boolean refreshPlayerLoadout(ServerPlayer player) {
        if (coreService == null) {
            return false;
        }
        return coreService.refreshPlayerLoadout(player);
    }

    public boolean tryUseAmmoBoxItem(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        if (coreService == null) {
            return false;
        }
        return coreService.tryUseAmmoBoxItem(player, hand);
    }

    public boolean tryUseMedicalBoxItem(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        if (coreService == null) {
            return false;
        }
        return coreService.tryUseMedicalBoxItem(player, hand);
    }

    public boolean tryUseNearbyAmmoBox(ServerPlayer player, long gameTick) {
        if (coreService == null) {
            return false;
        }
        return coreService.tryUseNearbyAmmoBox(player, gameTick);
    }

    public boolean emitPlayerThirdPersonAudioCue(UUID playerId, String cueId) {
        if (coreService == null) {
            return false;
        }
        return coreService.emitPlayerThirdPersonAudioCue(playerId, cueId);
    }

    public boolean handleNearbyHeadshotPraise(ServerPlayer killer) {
        if (coreService == null || killer == null) {
            return false;
        }
        return coreService.handleNearbyHeadshotPraise(killer);
    }

    public void tryRestoreInventorySnapshot(ServerPlayer player) {
        if (coreService == null || player == null) {
            return;
        }
        coreService.tryRestoreInventorySnapshot(player);
    }

    public KilliconScoreboardBridge.Payload buildKilliconScoreboardPayload(UUID requester) {
        if (coreService == null) {
            return null;
        }
        return coreService.buildKilliconScoreboardPayload(requester);
    }

    public Map<String, Object> buildKilliconConquestSnapshot(String roomId) {
        if (coreService == null) {
            return Map.of();
        }
        return coreService.buildKilliconConquestSnapshot(roomId);
    }

    public Map<String, Object> buildKilliconObjectiveSnapshot(String roomId) {
        if (coreService == null) {
            return Map.of();
        }
        return coreService.buildKilliconObjectiveSnapshot(roomId);
    }

    public boolean recordKilliconMatchDelta(UUID playerId, float scoreDelta, int killDelta, int deathDelta, int assistDelta, int reviveDelta) {
        if (coreService == null) {
            return false;
        }
        return coreService.recordKilliconMatchDelta(playerId, scoreDelta, killDelta, deathDelta, assistDelta, reviveDelta);
    }

    public void onServerTick(long gameTick) {
        if (coreService == null) {
            return;
        }
        ServerActionBarService.onServerTick();
        coreService.tickPlayerStates(gameTick);
        coreService.tickRooms(gameTick);
        syncAdminDebugOverlay(gameTick);
    }

    public void setPlayerDebugOverlayEnabled(UUID playerId, boolean enabled) {
        if (playerId == null) {
            return;
        }
        if (enabled) {
            debugOverlayEnabledPlayers.add(playerId);
        } else {
            debugOverlayEnabledPlayers.remove(playerId);
        }
    }

    public boolean isPlayerDebugOverlayEnabled(UUID playerId) {
        return playerId != null && debugOverlayEnabledPlayers.contains(playerId);
    }

    private void syncAdminDebugOverlay(long gameTick) {
        if (coreService == null || gameTick % 20L != 0L) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isPlayerDebugOverlayEnabled(player.getUUID())) {
                NetworkManager.sendToPlayer(player, new AdminDebugOverlayPacket("-"));
                continue;
            }
            String payload = coreService.buildAdminDebugOverlayPayload(player).orElse("-");
            NetworkManager.sendToPlayer(player, new AdminDebugOverlayPacket(payload));
        }
    }

    public void shutdown() {
        debugOverlayEnabledPlayers.clear();
        coreService = null;
        ServerActionBarService.shutdown();
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }
}
