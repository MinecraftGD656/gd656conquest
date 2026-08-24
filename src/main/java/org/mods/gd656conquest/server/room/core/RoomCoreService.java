package org.mods.gd656conquest.server.room.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.mods.gd656conquest.common.entity.PlayerAnimatorDebugDimensionsState;
import org.mods.gd656conquest.common.log.ClientMessageType;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.PlayerAnimatorDebugPacket;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.network.packet.RoomDownedStatePacket;
import org.mods.gd656conquest.network.packet.RoomMatchLoadPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.player.state.HubDimensionService;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.facility.support.HeroKitSupportService;
import org.mods.gd656conquest.server.facility.support.SupportScoreService;
import org.mods.gd656conquest.server.player.identity.PlayerIdentityService;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.shared.inventory.PlayerInventorySanitizer;
import org.mods.gd656conquest.server.room.entry.RoomAdminLifecycleService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayEntry;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayCommonUseHandler;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayObjectiveSnapshotProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomMatchEndRequest;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayEntryManager;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomTeamPresentationSupport;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.room.matching.RoomMatchingCoordinator;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomHudSyncService;
import org.mods.gd656conquest.server.room.runtime.RoomItemReplenishService;
import org.mods.gd656conquest.server.room.runtime.RoomInventorySnapshotService;
import org.mods.gd656conquest.server.room.runtime.RoomKilliconService;
import org.mods.gd656conquest.server.room.runtime.RoomLobbyTransferService;
import org.mods.gd656conquest.server.room.runtime.RoomMarkerRuntimeService;
import org.mods.gd656conquest.server.room.runtime.RoomNametagVisibilityService;
import org.mods.gd656conquest.server.room.runtime.RoomMatchingActionBarService;
import org.mods.gd656conquest.server.room.runtime.RoomAudioRuntimeService;
import org.mods.gd656conquest.server.room.runtime.RoomDeathSupportDropService;
import org.mods.gd656conquest.server.room.runtime.RoomDeploySelectionService;
import org.mods.gd656conquest.server.room.runtime.RoomGunmasterLoadoutService;
import org.mods.gd656conquest.server.room.runtime.RoomGroundSensorService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerLaunchService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerLoadoutService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerRespawnService;
import org.mods.gd656conquest.server.room.runtime.RoomShellingRuntimeService;
import org.mods.gd656conquest.server.room.runtime.RoomResultRuntimeService;
import org.mods.gd656conquest.server.room.runtime.RoomMatchLoadService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerStateService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerTransferQueueService;
import org.mods.gd656conquest.server.room.runtime.RoomRuleRuntimeService;
import org.mods.gd656conquest.server.room.runtime.RoomSupportBoxService;
import org.mods.gd656conquest.server.room.runtime.RoomSquadBeaconService;
import org.mods.gd656conquest.server.room.runtime.RoomSquadAssignmentService;
import org.mods.gd656conquest.server.room.runtime.RoomTeamSwitchService;
import org.mods.gd656conquest.server.room.vehicle.RoomVehicleRuntimeService;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.compat.tacz.TaczServerPoseCompat;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RoomCoreService {
    private static final long RUNNING_HUD_SYNC_INTERVAL_TICKS = 2L;
    private static final long RUNNING_NAMETAG_SYNC_INTERVAL_TICKS = 10L;
    private final MinecraftServer server;
    private final RoomRuntimeRepository<RoomRuntimeState> runtimeRepository;
    private final RoomPlayerSessionService playerSessionService;
    private final RoomMatchingCoordinator matchingCoordinator;
    private final RoomGameplayEntryManager gameplayEntryManager;
    private final RoomPlayerLaunchService playerLaunchService;
    private final RoomPlayerStateService playerStateService;
    private final RoomPlayerTransferQueueService playerTransferQueueService;
    private final RoomDeploySelectionService deploySelectionService;
    private final RoomPlayerRespawnService playerRespawnService;
    private final RoomPlayerLoadoutService playerLoadoutService;
    private final RoomGunmasterLoadoutService gunmasterLoadoutService;
    private final RoomItemReplenishService itemReplenishService;
    private final RoomDeathSupportDropService deathSupportDropService;
    private final RoomSupportBoxService supportBoxService;
    private final RoomGroundSensorService groundSensorService;
    private final RoomMarkerRuntimeService markerRuntimeService;
    private final RoomHudSyncService hudSyncService;
    private final RoomInventorySnapshotService inventorySnapshotService;
    private final RoomPlayerRejoinService playerRejoinService;
    private final RoomSquadAssignmentService squadAssignmentService;
    private final RoomSquadBeaconService squadBeaconService;
    private final RoomNametagVisibilityService nametagVisibilityService;
    private final RoomTeamSwitchService teamSwitchService;
    private final RoomKilliconService killiconService;
    private final RoomAdminDebugOverlayService adminDebugOverlayService;
    private final RoomLobbyTransferService lobbyTransferService;
    private final RoomAudioRuntimeService audioRuntimeService;
    private final RoomVehicleRuntimeService vehicleRuntimeService;
    private final RoomRuleRuntimeService ruleRuntimeService;
    private final RoomResultRuntimeService resultRuntimeService;
    private final RoomShellingRuntimeService shellingRuntimeService;
    private final RoomGroupPlacementService groupPlacementService;
    private final RoomMatchLoadService matchLoadService;

    public RoomCoreService(RoomCoreComponents components) {
        RoomCoreComponents resolved = Objects.requireNonNull(components, "components");
        this.server = resolved.server();
        this.runtimeRepository = resolved.runtimeRepository();
        this.playerSessionService = resolved.playerSessionService();
        this.matchingCoordinator = resolved.matchingCoordinator();
        this.gameplayEntryManager = resolved.gameplayEntryManager();
        this.playerLaunchService = resolved.playerLaunchService();
        this.playerStateService = resolved.playerStateService();
        this.playerTransferQueueService = resolved.playerTransferQueueService();
        this.deploySelectionService = resolved.deploySelectionService();
        this.playerRespawnService = resolved.playerRespawnService();
        this.playerLoadoutService = resolved.playerLoadoutService();
        this.gunmasterLoadoutService = resolved.gunmasterLoadoutService();
        this.itemReplenishService = resolved.itemReplenishService();
        this.deathSupportDropService = resolved.deathSupportDropService();
        this.supportBoxService = resolved.supportBoxService();
        this.markerRuntimeService = resolved.markerRuntimeService();
        this.groundSensorService = new RoomGroundSensorService(server, runtimeRepository, playerSessionService, this.markerRuntimeService);
        this.hudSyncService = resolved.hudSyncService();
        this.inventorySnapshotService = resolved.inventorySnapshotService();
        this.playerRejoinService = resolved.playerRejoinService();
        this.squadAssignmentService = resolved.squadAssignmentService();
        this.squadBeaconService = resolved.squadBeaconService();
        this.nametagVisibilityService = resolved.nametagVisibilityService();
        this.teamSwitchService = resolved.teamSwitchService();
        this.killiconService = resolved.killiconService();
        this.adminDebugOverlayService = resolved.adminDebugOverlayService();
        this.lobbyTransferService = resolved.lobbyTransferService();
        this.audioRuntimeService = resolved.audioRuntimeService();
        this.vehicleRuntimeService = resolved.vehicleRuntimeService();
        this.ruleRuntimeService = resolved.ruleRuntimeService();
        this.resultRuntimeService = resolved.resultRuntimeService();
        this.shellingRuntimeService = new RoomShellingRuntimeService(server);
        this.groupPlacementService = new RoomGroupPlacementService(
            runtimeRepository,
            playerSessionService,
            matchingCoordinator,
            squadAssignmentService,
            deploySelectionService,
            playerLaunchService,
            hudSyncService,
            nametagVisibilityService,
            this::resyncPlayerPresentation
        );
        this.matchLoadService = new RoomMatchLoadService(
            server,
            playerLaunchService,
            playerStateService,
            playerRespawnService,
            resultRuntimeService
        );
    }

    public RoomVehicleRuntimeService vehicleRuntimeService() {
        return vehicleRuntimeService;
    }

    public RoomMarkerRuntimeService markerRuntimeService() {
        return markerRuntimeService;
    }

    public RoomDeploySelectionService deploySelectionService() {
        return deploySelectionService;
    }

    public RoomShellingRuntimeService shellingRuntimeService() {
        return shellingRuntimeService;
    }

    public void registerRoom(RoomDefinition room) {
        runtimeRepository.save(room, RoomRuntimeState::new);
    }

    public RoomDefinition hotUpdateRoomDefinition(RoomDefinition definition, long gameTick) {
        Objects.requireNonNull(definition, "definition");
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = runtimeRepository.find(definition.roomId());
        if (slotOptional.isEmpty()) {
            registerRoom(definition);
            return definition;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = slotOptional.get();
        RoomLifecycleState currentLifecycle = slot.room().lifecycleState();
        RoomDefinition applied = definition.withLifecycleState(currentLifecycle);
        runtimeRepository.save(applied, RoomRuntimeState::new);
        if (currentLifecycle == RoomLifecycleState.RUNNING || currentLifecycle == RoomLifecycleState.ENDING) {
            hudSyncService.syncRunningRoom(applied, slot.runtime().getRunningState());
        } else if (currentLifecycle == RoomLifecycleState.MATCHING) {
            hudSyncService.syncMatchingRoom(applied, slot.runtime(), gameTick);
        } else if (currentLifecycle == RoomLifecycleState.MAINTENANCE) {
            for (UUID playerId : slot.runtime().getRunningState().getRoomPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    resyncPlayerPresentation(player, gameTick);
                }
            }
        }
        return applied;
    }

    public void registerRooms(Collection<RoomDefinition> rooms) {
        Objects.requireNonNull(rooms, "rooms");
        for (RoomDefinition room : rooms) {
            registerRoom(room);
        }
    }

    public List<RoomDefinition> listRooms() {
        return runtimeRepository.list().stream()
            .map(RoomRuntimeSlot::room)
            .toList();
    }

    public void shutdown() {
        for (RoomRuntimeSlot<RoomRuntimeState> slot : runtimeRepository.list()) {
            if (slot == null) {
                continue;
            }
            shellingRuntimeService.abortAllShellingRequests(
                slot.room(),
                slot.runtime().getRunningState(),
                RoomShellingRuntimeService.AbortReason.ROOM_UNAVAILABLE
            );
        }
    }

    public Optional<RoomDefinition> findRoom(String roomId) {
        return runtimeRepository.find(roomId).map(RoomRuntimeSlot::room);
    }

    public Optional<RunningRuntimeState> findRunningState(String roomId) {
        return runtimeRepository.find(roomId).map(slot -> slot.runtime().getRunningState());
    }

    public RoomDefinition updateLifecycleState(String roomId, RoomLifecycleState nextState) {
        Objects.requireNonNull(nextState, "nextState");
        RoomRuntimeSlot<RoomRuntimeState> slot = requireRoomSlot(roomId);
        RoomDefinition updated = slot.room().withLifecycleState(nextState);
        runtimeRepository.save(updated, RoomRuntimeState::new);
        return updated;
    }

    public int evacuateRoom(String roomId, long gameTick) {
        RoomRuntimeSlot<RoomRuntimeState> slot = requireRoomSlot(roomId);
        LinkedHashSet<UUID> occupants = new LinkedHashSet<>();
        occupants.addAll(slot.runtime().getMatchingState().getQueuePlayers());
        occupants.addAll(slot.runtime().getRunningState().getRoomPlayers());
        int evacuated = 0;
        for (UUID playerId : occupants) {
            removePlayerFromSlot(playerId, slot, gameTick, true);
            playerSessionService.removePlayer(playerId);
            evacuated++;
        }
        return evacuated;
    }

    public boolean joinMaintenanceRoom(String roomId, ServerPlayer player) {
        if (player == null) {
            return false;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = requireRoomSlot(roomId);
        RoomDefinition effectiveRoom = refreshRoomDefinitionFromRepository(slot);
        if (effectiveRoom.lifecycleState() != RoomLifecycleState.MAINTENANCE) {
            return false;
        }
        UUID playerId = player.getUUID();
        if (!preparePlayerForRoomJoin(player, roomId)) {
            return false;
        }
        // Maintenance room entry is a plain admin world edit flow, but dimension-change listeners
        // still run during teleport. Bind the player first so standalone-world enforcement does not
        // mistakenly treat this as an illegal conquest-dimension intrusion and kick them to hub.
        playerRejoinService.clearPlayerTickets(playerId);
        bindPlayerToRoom(playerId, roomId);
        slot.runtime().getRunningState().addRoomPlayer(playerId, null);
        inventorySnapshotService.backupAndClear(player, slot.runtime());
        if (!playerLaunchService.teleportToQueueSpawn(player, effectiveRoom)) {
            PlayerInventorySanitizer.clearArmorSlots(player);
            inventorySnapshotService.restore(player, slot.runtime());
            slot.runtime().getRunningState().removeRoomPlayer(playerId);
            playerSessionService.removePlayer(playerId);
            notifyJoinTeleportFailure(player, effectiveRoom);
            return false;
        }
        playerStateService.applyMatchingState(effectiveRoom, player);
        RoomMatchingActionBarService.clear(player);
        resyncPlayerPresentation(player, server.getTickCount());
        return true;
    }

    public void bindPlayerToRoom(UUID playerId, String roomId) {
        requireRoomSlot(roomId);
        playerSessionService.bindPlayerToRoom(playerId, roomId);
    }

    public Optional<String> findPlayerRoomId(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Optional<String> boundRoomId = playerSessionService.findRoomId(playerId);
        if (boundRoomId.isPresent()) {
            Optional<RoomRuntimeSlot<RoomRuntimeState>> boundSlot = runtimeRepository.find(boundRoomId.get());
            if (boundSlot.isPresent() && containsPlayer(boundSlot.get(), playerId)) {
                return boundRoomId;
            }
            playerSessionService.removePlayer(playerId);
        }
        for (RoomRuntimeSlot<RoomRuntimeState> slot : runtimeRepository.list()) {
            if (!containsPlayer(slot, playerId)) {
                continue;
            }
            playerSessionService.bindPlayerToRoom(playerId, slot.room().roomId());
            return Optional.of(slot.room().roomId());
        }
        return Optional.empty();
    }

    public boolean isPlayerInMatchLoad(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return matchLoadService.isLoading(playerId);
    }

    public Optional<RoomTeamId> findRoomTeam(String roomId, UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return runtimeRepository.find(roomId)
            .map(slot -> slot.runtime().getRunningState().getRoomTeams().get(playerId));
    }

    public void resyncPlayerPresentation(ServerPlayer player, long gameTick) {
        if (player == null || player.server == null) {
            return;
        }
        Optional<String> roomId = findPlayerRoomId(player.getUUID());
        if (roomId.isEmpty()) {
            return;
        }
        runtimeRepository.find(roomId.get()).ifPresent(slot -> {
            RoomDefinition effectiveRoom = slot.room().lifecycleState() == RoomLifecycleState.MAINTENANCE
                ? refreshRoomDefinitionFromRepository(slot)
                : slot.room();
            RoomLifecycleState lifecycleState = effectiveRoom.lifecycleState();
            if (lifecycleState == RoomLifecycleState.MATCHING) {
                hudSyncService.syncMatchingRoom(effectiveRoom, slot.runtime(), gameTick);
                return;
            }
            if (lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING) {
                nametagVisibilityService.forceResyncRunningRoom(effectiveRoom, slot.runtime().getRunningState());
                hudSyncService.syncRunningRoom(effectiveRoom, slot.runtime().getRunningState());
                return;
            }
            if (lifecycleState == RoomLifecycleState.MAINTENANCE) {
                if (!shouldShowMaintenanceDebugHud(player, effectiveRoom)
                    || !hudSyncService.syncRunningPlayer(effectiveRoom, slot.runtime().getRunningState(), player)) {
                    hudSyncService.clearPlayer(player);
                }
            }
        });
    }

    private RoomDefinition refreshRoomDefinitionFromRepository(RoomRuntimeSlot<RoomRuntimeState> slot) {
        if (slot == null) {
            return null;
        }
        RoomDefinition current = slot.room();
        if (current == null) {
            return null;
        }
        RoomDefinition latest = ConquestDataManager.of(server)
            .roomDefinitions()
            .findDefinition(current.roomId())
            .orElse(current);
        RoomDefinition applied = latest.withLifecycleState(current.lifecycleState());
        runtimeRepository.save(applied, RoomRuntimeState::new);
        return applied;
    }

    private boolean shouldShowMaintenanceDebugHud(ServerPlayer player, RoomDefinition room) {
        return player != null
            && room != null
            && room.lifecycleState() == RoomLifecycleState.MAINTENANCE
            && room.hasConfiguredObjectivePoints()
            && player.hasPermissions(2)
            && RoomCoreRuntimeManager.of(server).isPlayerDebugOverlayEnabled(player.getUUID());
    }

    public Optional<RoomTeamId> findCurrentTeam(UUID playerId) {
        return groupPlacementService.findCurrentTeam(playerId);
    }

    public Optional<String> findSquadLabel(UUID playerId) {
        return groupPlacementService.findSquadLabel(playerId);
    }

    public String getPlayerTeamDisplayName(UUID playerId) {
        if (playerId == null) {
            return "-";
        }
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return "-";
        }
        RoomTeamId teamId = findCurrentTeam(playerId).orElse(null);
        return runtimeRepository.find(roomId.get())
            .map(slot -> resolveTeamDisplayName(slot.room(), slot.runtime().getRunningState(), teamId))
            .orElse(resolveTeamDisplayName(null, null, teamId));
    }

    public int countRoomPlayers(String roomId) {
        return runtimeRepository.find(roomId)
            .map(slot -> slot.runtime().getRunningState().getRoomPlayers().size())
            .orElse(0);
    }

    public Optional<String> findRejoinableRoomId(UUID playerId) {
        return playerRejoinService.findRejoinTarget(playerId).map(target -> target.room().roomId());
    }

    public boolean canJoinInProgressRoom(UUID playerId, String roomId) {
        return groupPlacementService.canJoinInProgressRoom(playerId, roomId);
    }

    public Optional<RoomGroupPlacementPlan> planGroupJoinPlacement(String roomId, int groupSize) {
        return groupPlacementService.planGroupJoinPlacement(roomId, groupSize);
    }

    public Optional<RoomGroupPlacementPlan> resolvePlayerPlacement(String roomId, UUID playerId) {
        return groupPlacementService.resolvePlayerPlacement(roomId, playerId);
    }

    public Optional<String> reserveSquadLabel(String roomId, RoomTeamId teamId) {
        return groupPlacementService.reserveSquadLabel(roomId, teamId);
    }

    public boolean canFitGroupInTeam(String roomId, RoomTeamId teamId, int requiredSlots) {
        return groupPlacementService.canFitGroupInTeam(roomId, teamId, requiredSlots);
    }

    public boolean applyGroupPlacement(String roomId, UUID playerId, RoomTeamId targetTeam, String squadLabel, long gameTick) {
        return groupPlacementService.applyGroupPlacement(roomId, playerId, targetTeam, squadLabel, gameTick);
    }

    public String queueTeamJoinChange(String roomId, UUID targetPlayerId, RoomTeamId targetTeam, String operatorName, long gameTick) {
        if (targetPlayerId == null) {
            return "未找到目标玩家";
        }
        if (targetTeam != RoomTeamId.CAMP_A && targetTeam != RoomTeamId.CAMP_B) {
            return "目标队伍不合法";
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = runtimeRepository.find(roomId);
        if (slotOptional.isEmpty()) {
            return "房间不存在: " + roomId;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = slotOptional.get();
        RoomDefinition room = slot.room();
        if (room.lifecycleState() == RoomLifecycleState.MATCHING) {
            MatchingRuntimeState matchingState = slot.runtime().getMatchingState();
            if (!matchingState.getQueuePlayers().contains(targetPlayerId)) {
                return "目标玩家不在该房间匹配队列内";
            }
            RoomTeamId current = matchingState.getQueueTeams().get(targetPlayerId);
            if (current != RoomTeamId.CAMP_A && current != RoomTeamId.CAMP_B) {
                return "目标玩家当前不在可切换队伍";
            }
            if (current == targetTeam) {
                return "目标玩家已经在该队伍";
            }
            matchingState.getQueueTeams().put(targetPlayerId, targetTeam);
            matchingState.setLastPrimaryActionBarTick(-1L);
            matchingState.setLastSecondaryActionBarTick(-1L);
            squadAssignmentService.assignMatchingPlayer(slot.runtime(), targetPlayerId, targetTeam);
            hudSyncService.syncMatchingRoom(room, slot.runtime(), gameTick);
            ServerPlayer targetPlayer = playerLaunchService.findOnlinePlayer(targetPlayerId);
            if (targetPlayer != null) {
                resyncPlayerPresentation(targetPlayer, gameTick);
                ServerLogger.sendToClient(targetPlayer, ClientMessageType.INFO,
                    "管理员已将你切换到 %s，匹配阶段立即生效",
                    resolveTeamDisplayName(room, null, targetTeam));
            }
            return "已将 " + resolvePlayerName(targetPlayer, targetPlayerId) + " 切换至 "
                + resolveTeamDisplayName(room, null, targetTeam) + "，匹配阶段立即生效";
        }
        if (room.lifecycleState() != RoomLifecycleState.RUNNING) {
            return "仅可在匹配阶段或对局进行中切换队伍";
        }
        RunningRuntimeState state = slot.runtime().getRunningState();
        if (!state.getRoomPlayers().contains(targetPlayerId)) {
            return "目标玩家不在该房间内";
        }
        RoomTeamId current = state.getRoomTeams().get(targetPlayerId);
        if (current != RoomTeamId.CAMP_A && current != RoomTeamId.CAMP_B) {
            return "目标玩家当前不在可切换队伍";
        }
        if (current == targetTeam) {
            return "目标玩家已经在该队伍";
        }
        state.getAutoTeamSwitchOfferTargetTeams().remove(targetPlayerId);
        state.getAutoTeamSwitchOfferExpireTicks().remove(targetPlayerId);

        ServerPlayer targetPlayer = playerLaunchService.findOnlinePlayer(targetPlayerId);
        String operator = operatorName == null || operatorName.isBlank() ? "系统" : operatorName;
        if (state.isInRespawnFlow(targetPlayerId)) {
            RoomTeamSwitchService.TeamSwitchResult result = teamSwitchService.applyRequestedTeam(
                room,
                slot.runtime(),
                targetPlayerId,
                targetTeam
            );
            if (!result.valid()) {
                return "切换队伍失败";
            }
            if (targetPlayer != null) {
                ServerLogger.sendToClient(targetPlayer, ClientMessageType.SUCCESS,
                    "管理员已将你切换到 %s，本次部署将按新队伍生效",
                    resolveTeamDisplayName(room, state, targetTeam));
            }
            return "已将 " + resolvePlayerName(targetPlayer, targetPlayerId) + " 切换至 "
                + resolveTeamDisplayName(room, state, targetTeam) + "，本次部署立即生效";
        }

        state.getPendingTeamChanges().put(targetPlayerId, targetTeam);
        if (targetPlayer != null) {
            ServerLogger.sendToClient(targetPlayer, ClientMessageType.INFO,
                "管理员已将你标记为切换到 %s，队伍更改将在下次重生生效",
                resolveTeamDisplayName(room, state, targetTeam));
        }
        return "已将 " + resolvePlayerName(targetPlayer, targetPlayerId) + " 标记为下次重生切换至 "
            + resolveTeamDisplayName(room, state, targetTeam);
    }

    public String switchPlayerToOppositeTeam(String roomId, UUID targetPlayerId, String operatorName, long gameTick) {
        if (targetPlayerId == null) {
            return "未找到目标玩家";
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = runtimeRepository.find(roomId);
        if (slotOptional.isEmpty()) {
            return "房间不存在: " + roomId;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = slotOptional.get();
        if (slot.room().lifecycleState() == RoomLifecycleState.MATCHING) {
            MatchingRuntimeState matchingState = slot.runtime().getMatchingState();
            if (!matchingState.getQueuePlayers().contains(targetPlayerId)) {
                return "目标玩家不在该房间匹配队列内";
            }
            RoomTeamId current = matchingState.getQueueTeams().get(targetPlayerId);
            RoomTeamId targetTeam = teamSwitchService.resolveOppositePlayableTeam(current);
            if (targetTeam == null) {
                return "目标玩家当前不在可切换队伍";
            }
            return queueTeamJoinChange(roomId, targetPlayerId, targetTeam, operatorName, gameTick);
        }
        RunningRuntimeState state = slot.runtime().getRunningState();
        if (!state.getRoomPlayers().contains(targetPlayerId)) {
            return "目标玩家不在该房间内";
        }
        RoomTeamId current = state.getRoomTeams().get(targetPlayerId);
        RoomTeamId targetTeam = teamSwitchService.resolveOppositePlayableTeam(current);
        if (targetTeam == null) {
            return "目标玩家当前不在可切换队伍";
        }
        return queueTeamJoinChange(roomId, targetPlayerId, targetTeam, operatorName, gameTick);
    }

    public String requestActivePlayerCampSwitch(String roomId, UUID playerId, String operatorName, boolean operatorAdmin, long gameTick) {
        if (playerId == null) {
            return "未找到目标玩家";
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = runtimeRepository.find(roomId);
        if (slotOptional.isEmpty()) {
            return "房间不存在: " + roomId;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = slotOptional.get();
        if (slot.room().lifecycleState() != RoomLifecycleState.RUNNING) {
            return "当前不在进行中的对局";
        }
        RunningRuntimeState state = slot.runtime().getRunningState();
        if (!state.getRoomPlayers().contains(playerId)) {
            return "你不在该房间内";
        }
        RoomTeamSwitchService.TeamSwitchEligibility eligibility = teamSwitchService.evaluateSelfSwitchEligibility(
            slot.room(),
            state,
            playerId,
            operatorAdmin
        );
        if (!eligibility.valid()) {
            return eligibility.deniedMessage();
        }
        if (!eligibility.allowed()) {
            return eligibility.deniedMessage();
        }
        return switchPlayerToOppositeTeam(roomId, playerId, operatorName, gameTick);
    }

    public String acceptAutoTeamSwitch(String roomId, UUID playerId, long gameTick) {
        if (playerId == null) {
            return "无法确认换队";
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = runtimeRepository.find(roomId);
        if (slotOptional.isEmpty()) {
            return "房间不存在: " + roomId;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = slotOptional.get();
        if (slot.room().lifecycleState() != RoomLifecycleState.RUNNING) {
            return "当前不在进行中的对局";
        }
        RunningRuntimeState state = slot.runtime().getRunningState();
        if (!state.getRoomPlayers().contains(playerId)) {
            return "你不在该房间内";
        }
        RoomTeamId targetTeam = state.getAutoTeamSwitchOfferTargetTeams().get(playerId);
        Long expireTick = state.getAutoTeamSwitchOfferExpireTicks().get(playerId);
        if (targetTeam == null || expireTick == null || gameTick > expireTick) {
            state.getAutoTeamSwitchOfferTargetTeams().remove(playerId);
            state.getAutoTeamSwitchOfferExpireTicks().remove(playerId);
            return "当前没有可确认的换队邀请";
        }
        state.getAutoTeamSwitchOfferTargetTeams().remove(playerId);
        state.getAutoTeamSwitchOfferExpireTicks().remove(playerId);
        return queueTeamJoinChange(roomId, playerId, targetTeam, "自动平衡", gameTick);
    }

    public void removePlayerFromRoom(UUID playerId) {
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return;
        }
        // GD656Mods: 玩家离开房间时直接实现对局内暂存的配装修改; 玩家对象不可用时兜底丢弃
        ServerPlayer leavingPlayer = server.getPlayerList().getPlayer(playerId);
        if (leavingPlayer != null) {
            org.mods.gd656conquest.server.loadout.LoadoutUiService.applyPendingLoadoutEdits(leavingPlayer);
        } else {
            org.mods.gd656conquest.server.loadout.LoadoutUiService.discardPendingLoadoutEdits(playerId);
        }
        long gameTick = server.getTickCount();
        matchLoadService.cancel(playerId, gameTick);
        runtimeRepository.find(roomId.get())
            .ifPresent(slot -> removePlayerFromSlot(playerId, slot, gameTick, true));
        playerSessionService.removePlayer(playerId);
    }

    public String sendPlayerToHub(ServerPlayer player, long gameTick) {
        if (player == null) {
            return "未找到玩家";
        }
        UUID playerId = player.getUUID();
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isPresent()) {
            runtimeRepository.find(roomId.get())
                .ifPresent(slot -> removePlayerFromSlot(playerId, slot, gameTick, true));
            playerSessionService.removePlayer(playerId);
            return "已离开当前对局并返回主城";
        }
        scheduleLobbyTransfer(player, gameTick);
        return "已返回主城";
    }

    public String requestRedeploy(ServerPlayer player) {
        if (player == null) {
            return "当前不在任何房间";
        }
        Optional<String> roomId = playerSessionService.findRoomId(player.getUUID());
        if (roomId.isEmpty()) {
            return "当前不在任何房间";
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = runtimeRepository.find(roomId.get());
        if (slotOptional.isEmpty()) {
            playerSessionService.removePlayer(player.getUUID());
            return "当前不在任何房间";
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = slotOptional.get();
        return playerRespawnService.requestRedeploy(slot.room(), slot.runtime().getRunningState(), player);
    }

    public String handleForceDeployCommand(ServerPlayer player, long gameTick) {
        if (player == null) {
            return "当前不在任何房间";
        }
        return findRunningSlotByPlayer(player.getUUID())
            .map(slot -> playerRespawnService.handleForceDeployCommand(
                slot.room(),
                slot.runtime().getRunningState(),
                player,
                gameTick
            ))
            .orElse("当前不在任何房间");
    }

    public boolean handleDownedAction(ServerPlayer player, String action, long gameTick) {
        if (player == null) {
            return false;
        }
        return findRunningSlotByPlayer(player.getUUID())
            .map(slot -> playerRespawnService.handleDownedAction(
                slot.room(),
                slot.runtime().getRunningState(),
                player,
                action,
                gameTick
            ))
            .orElse(false);
    }

    public boolean handlePlayerDeath(UUID playerId, RoomPosition deathPosition, int killerEntityId, long gameTick) {
        ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
        if (player != null) {
            handlePlayerDeathEvent(player, deathPosition, killerEntityId, gameTick);
            return findRunningSlotByPlayer(playerId).isPresent();
        }
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return false;
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slot = runtimeRepository.find(roomId.get());
        if (slot.isEmpty()) {
            playerSessionService.removePlayer(playerId);
            return false;
        }
        RoomRuntimeSlot<RoomRuntimeState> runningSlot = slot.get();
        RunningRuntimeState runningState = runningSlot.runtime().getRunningState();
        if ((runningSlot.room().lifecycleState() != RoomLifecycleState.RUNNING
            && runningSlot.room().lifecycleState() != RoomLifecycleState.ENDING)
            || !runningState.getRoomPlayers().contains(playerId)) {
            return false;
        }
        RoomTeamId teamId = runningState.getRoomTeams().get(playerId);
        runningState.getGameplayRuntime().ifPresent(runtime ->
            runtime.onPlayerInvalidated(runningSlot.room(), playerId, teamId, RoomPlayerInvalidationReason.DEATH, killerEntityId, gameTick)
        );
        playerRespawnService.onPlayerDeath(runningSlot.room(), runningState, playerId, deathPosition, killerEntityId, gameTick);
        applyPendingTeamChangeAfterDeath(runningSlot.room(), runningState, playerId);
        return true;
    }

    public boolean handlePlayerDeathEvent(ServerPlayer player, RoomPosition deathPosition, int killerEntityId, long gameTick) {
        return handlePlayerFatalState(player, deathPosition, killerEntityId, gameTick);
    }

    public void handlePlayerKillSupportDrop(ServerPlayer victim, ServerPlayer killer) {
        deathSupportDropService.handlePlayerKillSupportDrop(victim, killer);
    }

    public boolean isDeathSupportDropPickupBlocked(ServerPlayer player, ItemEntity itemEntity) {
        return RoomDeathSupportDropService.isPickupBlocked(player, itemEntity);
    }

    private boolean handlePlayerFatalState(
        ServerPlayer player,
        RoomPosition deathPosition,
        int killerEntityId,
        long gameTick
    ) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return false;
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slot = runtimeRepository.find(roomId.get());
        if (slot.isEmpty()) {
            playerSessionService.removePlayer(playerId);
            return false;
        }
        RoomRuntimeSlot<RoomRuntimeState> runningSlot = slot.get();
        RunningRuntimeState runningState = runningSlot.runtime().getRunningState();
        if ((runningSlot.room().lifecycleState() != RoomLifecycleState.RUNNING
            && runningSlot.room().lifecycleState() != RoomLifecycleState.ENDING)
            || !runningState.getRoomPlayers().contains(playerId)) {
            return false;
        }
        boolean bypassDownedForRedeploy = runningState.getDoubledRespawnPending().contains(playerId);
        boolean bypassDownedForBoundary = runningState.getBoundaryDeathPendingPlayers().remove(playerId);
        boolean bypassDowned = bypassDownedForRedeploy || bypassDownedForBoundary;
        shellingRuntimeService.abortShellingRequest(
            runningSlot.room(),
            runningState,
            player,
            playerId,
            RoomShellingRuntimeService.AbortReason.PLAYER_DEATH
        );
        // Clear TACZ crawl before any downed/death handling so preview dimensions and pose start from a clean state.
        TaczServerPoseCompat.clearCrawlState(player);
        playerRespawnService.emitNearbyTeammatesManDown(
            runningState,
            playerId,
            deathPosition,
            killerEntityId,
            !bypassDowned && runningSlot.room().commonConfig().enablePlayerDowned()
        );
        boolean downedHandled = !bypassDowned && playerRespawnService.tryEnterDownedState(
            runningSlot.room(),
            runningState,
            player,
            deathPosition,
            killerEntityId,
            gameTick
        );
        if (!downedHandled) {
            RoomTeamId teamId = runningState.getRoomTeams().get(playerId);
            runningState.getGameplayRuntime().ifPresent(runtime ->
                runtime.onPlayerInvalidated(runningSlot.room(), playerId, teamId, RoomPlayerInvalidationReason.DEATH, killerEntityId, gameTick)
            );
            playerRespawnService.onPlayerDeath(runningSlot.room(), runningState, playerId, deathPosition, killerEntityId, gameTick);
        }
        applyPendingTeamChangeAfterDeath(runningSlot.room(), runningState, playerId);
        return downedHandled;
    }

    public boolean handlePlayerLogout(UUID playerId, long gameTick) {
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return false;
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slot = runtimeRepository.find(roomId.get());
        if (slot.isEmpty()) {
            playerSessionService.removePlayer(playerId);
            return false;
        }
        if (slot.get().runtime().getMatchingState().getQueuePlayers().contains(playerId)) {
            removePlayerFromSlot(playerId, slot.get(), gameTick, false);
            playerSessionService.removePlayer(playerId);
            return true;
        }
        if (slot.get().room().lifecycleState() == RoomLifecycleState.MAINTENANCE
            && slot.get().runtime().getRunningState().getRoomPlayers().contains(playerId)) {
            removeMaintenancePlayerFromSlot(playerId, slot.get());
            playerSessionService.removePlayer(playerId);
            return true;
        }
        return handleRunningPlayerInvalidation(playerId, gameTick, RoomPlayerInvalidationReason.LOGOUT);
    }

    public boolean handleDeploySelection(ServerPlayer player, String action, String kind, String targetId, long gameTick) {
        if (player == null) {
            return false;
        }
        return findRunningSlotByPlayer(player.getUUID())
            .map(slot -> playerRespawnService.handleDeploySelection(
                slot.room(),
                slot.runtime().getRunningState(),
                player,
                action,
                kind,
                targetId,
                gameTick
            ))
            .orElse(false);
    }

    public boolean handleMarkerAction(ServerPlayer player, String action, String kind, BlockPos blockPos, UUID targetPlayerId, long gameTick) {
        if (player == null || action == null || action.isBlank()) {
            return false;
        }
        return findRunningSlotByPlayer(player.getUUID())
            .filter(slot -> slot.room().lifecycleState() == RoomLifecycleState.RUNNING)
            .map(slot -> {
                RunningRuntimeState runningState = slot.runtime().getRunningState();
                if (!runningState.getRoomPlayers().contains(player.getUUID())) {
                    return false;
                }
                if ("cancel".equalsIgnoreCase(action)) {
                    return markerRuntimeService.cancelMarker(slot.room(), runningState, player, gameTick);
                }
                if ("place".equalsIgnoreCase(action)) {
                    RunningRuntimeState.MarkerKind markerKind;
                    try {
                        markerKind = RunningRuntimeState.MarkerKind.valueOf(kind == null ? "NORMAL" : kind.toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        markerKind = RunningRuntimeState.MarkerKind.NORMAL;
                    }
                    return markerRuntimeService.placeMarker(slot.room(), runningState, player, markerKind, blockPos, targetPlayerId, gameTick);
                }
                return false;
            })
            .orElse(false);
    }

    public boolean handleBoundaryHeightReport(ServerPlayer player, boolean abovePlayerMaxY, boolean aboveAircraftPassengerMaxY, long gameTick) {
        if (player == null) {
            return false;
        }
        return findRunningSlotByPlayer(player.getUUID())
            .map(slot -> {
                RunningRuntimeState runningState = slot.runtime().getRunningState();
                UUID playerId = player.getUUID();
                if (!runningState.getRoomPlayers().contains(playerId) || !player.isAlive() || runningState.isInRespawnFlow(playerId)) {
                    runningState.getBoundaryHeightReportTicks().remove(playerId);
                    runningState.getBoundaryPlayerMaxYReportedPlayers().remove(playerId);
                    runningState.getBoundaryAircraftMaxYReportedPlayers().remove(playerId);
                    return false;
                }
                runningState.getBoundaryHeightReportTicks().put(playerId, gameTick);
                if (abovePlayerMaxY) {
                    runningState.getBoundaryPlayerMaxYReportedPlayers().add(playerId);
                } else {
                    runningState.getBoundaryPlayerMaxYReportedPlayers().remove(playerId);
                }
                if (aboveAircraftPassengerMaxY) {
                    runningState.getBoundaryAircraftMaxYReportedPlayers().add(playerId);
                } else {
                    runningState.getBoundaryAircraftMaxYReportedPlayers().remove(playerId);
                }
                return true;
            })
            .orElse(false);
    }

    public void handleMarkedPlayerDeath(ServerPlayer victim, ServerPlayer killer, long gameTick) {
        if (victim == null) {
            return;
        }
        findRunningSlotByPlayer(victim.getUUID())
            .filter(slot -> slot.room().lifecycleState() == RoomLifecycleState.RUNNING)
            .ifPresent(slot -> markerRuntimeService.handlePlayerDeath(slot.room(), slot.runtime().getRunningState(), victim, killer, gameTick));
    }

    public boolean shouldCancelDamage(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        if (HubDimensionService.isHubDimension(player.level())
            && ConquestDataManager.of(player.server).serverConfig().isOverworldInvulnerableEnabled()) {
            return true;
        }
        UUID playerId = player.getUUID();
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return false;
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slot = runtimeRepository.find(roomId.get());
        if (slot.isEmpty()) {
            playerSessionService.removePlayer(playerId);
            return false;
        }
        if (slot.get().runtime().getMatchingState().getQueuePlayers().contains(playerId)) {
            return true;
        }
        RunningRuntimeState state = slot.get().runtime().getRunningState();
        if ((slot.get().room().lifecycleState() != RoomLifecycleState.RUNNING
            && slot.get().room().lifecycleState() != RoomLifecycleState.ENDING)
            || !state.getRoomPlayers().contains(playerId)) {
            return false;
        }
        return player.isInvulnerable()
            || state.getResultSequenceEndTick() > 0
            || state.isInRespawnFlow(playerId);
    }

    public boolean isPlayerDowned(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return false;
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slot = runtimeRepository.find(roomId.get());
        if (slot.isEmpty() || (slot.get().room().lifecycleState() != RoomLifecycleState.RUNNING && slot.get().room().lifecycleState() != RoomLifecycleState.ENDING)) {
            return false;
        }
        RunningRuntimeState state = slot.get().runtime().getRunningState();
        return state.getRoomPlayers().contains(playerId) && (state.isPlayerPendingDowned(playerId) || state.isPlayerDowned(playerId));
    }

    public boolean shouldBlockPassiveRegen(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return false;
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slot = runtimeRepository.find(roomId.get());
        if (slot.isEmpty()) {
            playerSessionService.removePlayer(playerId);
            return false;
        }
        if (slot.get().room().lifecycleState() != RoomLifecycleState.RUNNING
            && slot.get().room().lifecycleState() != RoomLifecycleState.ENDING) {
            return false;
        }
        RunningRuntimeState state = slot.get().runtime().getRunningState();
        return state.getRoomPlayers().contains(playerId)
            && (state.isPlayerPendingDowned(playerId) || state.isPlayerDowned(playerId));
    }

    public boolean tryUseSquadBeaconItem(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        return squadBeaconService.tryUseSquadBeaconItem(player, hand);
    }

    public boolean markPlayerDealtDamage(UUID attackerId, UUID targetId, long gameTick) {
        if (attackerId == null || targetId == null) {
            return false;
        }
        return findRunningSlotByPlayer(attackerId)
            .filter(slot -> slot.room().lifecycleState() == RoomLifecycleState.RUNNING)
            .filter(slot -> slot.runtime().getRunningState().getRoomPlayers().contains(targetId))
            .map(slot -> deploySelectionService.markPlayerDealtDamage(
                slot.runtime().getRunningState(),
                attackerId,
                targetId,
                gameTick
            ))
            .orElse(false);
    }

    public boolean shouldCancelDamageToSquadBeacon(ServerPlayer attacker, net.minecraft.world.entity.Entity beacon) {
        if (!(beacon instanceof org.mods.gd656conquest.common.entity.SquadDeployBeaconEntity squadBeacon)) {
            return false;
        }
        return squadBeaconService.shouldCancelDamageToSquadBeacon(attacker, squadBeacon);
    }

    public boolean shouldCancelDamageToGroundSensor(ServerPlayer attacker, net.minecraft.world.entity.Entity sensor) {
        if (!(sensor instanceof org.mods.gd656conquest.common.entity.GroundSensorEntity groundSensor)) {
            return false;
        }
        return groundSensorService.shouldCancelDamageToGroundSensor(attacker, groundSensor);
    }

    public boolean shouldCancelDamageToVehicle(ServerPlayer attacker, Entity vehicle) {
        if (attacker == null || vehicle == null || attacker.server == null || attacker.server != server) {
            return false;
        }
        String roomId = vehicleRuntimeService.resolveManagedRoomId(vehicle).orElse("");
        if (roomId.isBlank()) {
            return false;
        }
        Optional<String> attackerRoomId = findPlayerRoomId(attacker.getUUID());
        if (attackerRoomId.isEmpty() || !roomId.equalsIgnoreCase(attackerRoomId.get())) {
            return false;
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = runtimeRepository.find(roomId);
        if (slotOptional.isEmpty()) {
            return false;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = slotOptional.get();
        if (slot.room().lifecycleState() != RoomLifecycleState.RUNNING
            && slot.room().lifecycleState() != RoomLifecycleState.ENDING) {
            return false;
        }
        RunningRuntimeState state = slot.runtime().getRunningState();
        UUID attackerId = attacker.getUUID();
        if (!state.getRoomPlayers().contains(attackerId)) {
            return false;
        }
        RoomTeamId attackerTeam = state.getRoomTeams().get(attackerId);
        return vehicleRuntimeService.shouldBlockFriendlyVehicleDamage(vehicle, state, attackerTeam);
    }

    public boolean isSameCampPlayer(ServerPlayer left, ServerPlayer right) {
        if (left == null || right == null) {
            return false;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = findRunningSlotByPlayer(left.getUUID()).orElse(null);
        if (slot == null || slot.room().lifecycleState() != RoomLifecycleState.RUNNING) {
            return false;
        }
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        if (!runningState.getRoomPlayers().contains(left.getUUID()) || !runningState.getRoomPlayers().contains(right.getUUID())) {
            return false;
        }
        Optional<String> rightRoomId = findPlayerRoomId(right.getUUID());
        if (rightRoomId.isEmpty() || !slot.room().roomId().equalsIgnoreCase(rightRoomId.get())) {
            return false;
        }
        RoomTeamId leftTeam = runningState.getRoomTeams().get(left.getUUID());
        RoomTeamId rightTeam = runningState.getRoomTeams().get(right.getUUID());
        return (leftTeam == RoomTeamId.CAMP_A || leftTeam == RoomTeamId.CAMP_B) && leftTeam == rightTeam;
    }

    public RoomPlayerLoadoutService.AmmoSupplyResult trySupplyAmmoByBag(ServerPlayer player, boolean supportGroup) {
        if (gunmasterLoadoutService.isGunmasterPlayer(player)) {
            return RoomPlayerLoadoutService.AmmoSupplyResult.EMPTY;
        }
        return playerLoadoutService.trySupplyAmmoByBag(player, supportGroup);
    }

    public boolean refreshPlayerLoadout(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return findRunningSlotByPlayer(player.getUUID())
            .filter(slot -> slot.runtime().getRunningState().getRoomPlayers().contains(player.getUUID()))
            .map(slot -> {
                HeroKitSupportService.endHeroKitState(server, slot.runtime().getRunningState(), player.getUUID(), server.getTickCount());
                if (gunmasterLoadoutService.isGunmasterRoom(slot.room())) {
                    gunmasterLoadoutService.applySpawnLoadout(slot.room(), slot.runtime().getRunningState(), player);
                } else {
                    playerLoadoutService.applySpawnLoadout(slot.runtime().getRunningState(), player);
                }
                slot.runtime().getRunningState().getHeroKitLoadoutResetPendingPlayers().remove(player.getUUID());
                return true;
            })
            .orElse(false);
    }

    public boolean tryUseAmmoBoxItem(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        return supportBoxService.tryUseAmmoBoxItem(player, hand);
    }

    public boolean tryUseMedicalBoxItem(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        return supportBoxService.tryUseMedicalBoxItem(player, hand);
    }

    public boolean tryUseGroundSensorItem(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        return groundSensorService.tryUseGroundSensorItem(player, hand);
    }

    public boolean tryUseNearbyAmmoBox(ServerPlayer player, long gameTick) {
        return supportBoxService.tryUseNearbyAmmoBox(player, gameTick);
    }

    public boolean handleCommonUseStart(ServerPlayer player, long gameTick) {
        if (player == null) {
            return false;
        }
        return findRunningSlotByPlayer(player.getUUID())
            .filter(slot -> slot.room().lifecycleState() == RoomLifecycleState.RUNNING)
            .map(slot -> slot.runtime().getRunningState().getGameplayRuntime()
                .filter(RoomGameplayCommonUseHandler.class::isInstance)
                .map(RoomGameplayCommonUseHandler.class::cast)
                .map(handler -> handler.handleCommonUseStart(slot.room(), player, gameTick))
                .orElse(false))
            .orElse(false);
    }

    public void handleCommonUseStop(ServerPlayer player, long gameTick) {
        if (player == null) {
            return;
        }
        findRunningSlotByPlayer(player.getUUID())
            .filter(slot -> slot.room().lifecycleState() == RoomLifecycleState.RUNNING)
            .flatMap(slot -> slot.runtime().getRunningState().getGameplayRuntime()
                .filter(RoomGameplayCommonUseHandler.class::isInstance)
                .map(RoomGameplayCommonUseHandler.class::cast)
                .map(handler -> java.util.Map.entry(slot.room(), handler)))
            .ifPresent(entry -> entry.getValue().handleCommonUseStop(entry.getKey(), player, gameTick));
    }

    public boolean emitPlayerThirdPersonAudioCue(UUID playerId, String cueId) {
        if (playerId == null || cueId == null || cueId.isBlank()) {
            return false;
        }
        return findRunningSlotByPlayer(playerId)
            .map(slot -> {
                ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
                if (player == null) {
                    return false;
                }
                audioRuntimeService.emitPlayerThirdPersonCue(slot.room(), slot.runtime().getRunningState(), player, cueId);
                return true;
            })
            .orElse(false);
    }

    public Optional<RoomJoinResult> joinMatching(UUID playerId, String roomId, long gameTick) {
        RoomRuntimeSlot<RoomRuntimeState> slot = requireRoomSlot(roomId);
        ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
        if (!preparePlayerForRoomJoin(player, roomId)) {
            return Optional.empty();
        }
        RoomLifecycleState lifecycleState = slot.room().lifecycleState();
        if (lifecycleState == RoomLifecycleState.RUNNING) {
            Optional<RoomJoinResult> rejoin = tryRejoinRunningMatch(slot, player, gameTick);
            return rejoin.isPresent() ? rejoin : tryMidJoinRunningMatch(slot, player, gameTick);
        }
        if (lifecycleState == RoomLifecycleState.ENDING) {
            return Optional.empty();
        }
        if (lifecycleState == RoomLifecycleState.DISABLED
            || lifecycleState == RoomLifecycleState.MAINTENANCE
            || lifecycleState == RoomLifecycleState.SAVING
            || lifecycleState == RoomLifecycleState.CLEANING) {
            return Optional.empty();
        }
        if (lifecycleState == RoomLifecycleState.IDLE) {
            runtimeRepository.save(slot.room().withLifecycleState(RoomLifecycleState.MATCHING), RoomRuntimeState::new);
            slot = requireRoomSlot(roomId);
        }
        MatchingRuntimeState state = slot.runtime().getMatchingState();
        if (!matchingCoordinator.joinQueue(state, playerId, gameTick, slot.room().commonConfig().matchingConfig())) {
            return Optional.empty();
        }
        if (player != null) {
            inventorySnapshotService.backupAndClear(player, slot.runtime());
            if (RoomMatchLoadService.isEnabled(slot.room())) {
                // Intercept: use match-loading phase instead of direct queue teleport
                playerRejoinService.clearPlayerTickets(playerId);
                bindPlayerToRoom(playerId, roomId);
                squadAssignmentService.assignMatchingPlayer(slot.runtime(), playerId, state.getQueueTeams().get(playerId));
                matchLoadService.beginMatchLoad(
                    player, slot.room(), slot.runtime().getRunningState(), gameTick,
                    RoomMatchLoadService.EntryKind.MATCHING, state.getQueueTeams().get(playerId));
                return Optional.of(new RoomJoinResult(RoomJoinResult.Kind.MATCHING, roomId, state.getQueueTeams().get(playerId)));
            }
            if (!playerLaunchService.teleportToQueueSpawn(player, slot.room())) {
                PlayerInventorySanitizer.clearArmorSlots(player);
                inventorySnapshotService.restore(player, slot.runtime());
                matchingCoordinator.leaveQueue(state, playerId);
                notifyJoinTeleportFailure(player, slot.room());
                return Optional.empty();
            }
        }
        playerRejoinService.clearPlayerTickets(playerId);
        bindPlayerToRoom(playerId, roomId);
        squadAssignmentService.assignMatchingPlayer(slot.runtime(), playerId, state.getQueueTeams().get(playerId));
        if (player != null) {
            playerStateService.applyMatchingState(slot.room(), player);
            RoomMatchingActionBarService.showQueueStatus(player, slot.room().commonConfig().matchingConfig(), state);
            if (matchingCoordinator.canStart(state, slot.room().commonConfig().matchingConfig()) && state.getQueueDeadlineTick() > gameTick) {
                RoomMatchingActionBarService.showCountdownStatus(
                    player,
                    (int) Math.ceil((state.getQueueDeadlineTick() - gameTick) / 20.0D)
                );
            } else {
                RoomMatchingActionBarService.showWaitingStatus(player);
            }
            hudSyncService.syncMatchingRoom(slot.room(), slot.runtime(), gameTick);
        }
        return Optional.of(new RoomJoinResult(RoomJoinResult.Kind.MATCHING, roomId, state.getQueueTeams().get(playerId)));
    }

    public void leaveMatching(UUID playerId) {
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return;
        }
        runtimeRepository.find(roomId.get())
            .ifPresent(slot -> {
                ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
                matchingCoordinator.leaveQueue(slot.runtime().getMatchingState(), playerId);
                KilliconScoreboardBridge.clearPlayerMatchSnapshot(slot.runtime().getRunningState(), playerId);
                squadAssignmentService.removePlayer(slot.runtime(), playerId);
                if (player != null) {
                    PlayerInventorySanitizer.clearArmorSlots(player);
                    inventorySnapshotService.restore(player, slot.runtime());
                }
                RoomMatchingActionBarService.clear(player);
                hudSyncService.clearPlayer(player);
                if (slot.runtime().getMatchingState().getQueuePlayers().isEmpty()
                    && slot.room().lifecycleState() == RoomLifecycleState.MATCHING) {
                    runtimeRepository.save(slot.room().withLifecycleState(RoomLifecycleState.IDLE), RoomRuntimeState::new);
                }
            });
        playerSessionService.removePlayer(playerId);
    }

    public Optional<MatchingRuntimeState> findMatchingState(String roomId) {
        return runtimeRepository.find(roomId).map(slot -> slot.runtime().getMatchingState());
    }

    public void tickMatching(String roomId, long gameTick) {
        RoomRuntimeSlot<RoomRuntimeState> slot = requireRoomSlot(roomId);
        if (slot.room().lifecycleState() != RoomLifecycleState.MATCHING) {
            return;
        }

        MatchingRuntimeState state = slot.runtime().getMatchingState();
        if (!matchingCoordinator.canStart(state, slot.room().commonConfig().matchingConfig())) {
            playerStateService.maintainMatchingState(slot.room(), state);
            state.resetCountdown();
            hudSyncService.syncMatchingRoom(slot.room(), slot.runtime(), gameTick);
            syncMatchingActionBars(slot.room(), state, gameTick, false, 0);
            return;
        }
        playerStateService.maintainMatchingState(slot.room(), state);

        if (state.getQueueDeadlineTick() < 0) {
            int waitSeconds = matchingCoordinator.isFull(state, slot.room().commonConfig().matchingConfig())
                ? slot.room().commonConfig().matchingConfig().waitAfterMaxSeconds()
                : slot.room().commonConfig().matchingConfig().waitAfterMinSeconds();
            long deadlineTick = gameTick + (long) waitSeconds * 20L;
            state.setQueueStartTick(gameTick);
            state.setQueueDeadlineTick(deadlineTick);
        }

        if (matchingCoordinator.isFull(state, slot.room().commonConfig().matchingConfig())) {
            long fastDeadline = gameTick + (long) slot.room().commonConfig().matchingConfig().waitAfterMaxSeconds() * 20L;
            if (state.getQueueDeadlineTick() > fastDeadline) {
                state.setQueueDeadlineTick(fastDeadline);
            }
        }

        long remainTicks = state.getQueueDeadlineTick() - gameTick;
        if (remainTicks > 0) {
            int remainSeconds = (int) Math.ceil(remainTicks / 20.0D);
            if (remainSeconds <= 10 || remainSeconds % 5 == 0) {
                state.setLastPrimaryActionBarTick(-1L);
                state.setLastSecondaryActionBarTick(-1L);
            }
            hudSyncService.syncMatchingRoom(slot.room(), slot.runtime(), gameTick);
            syncMatchingActionBars(slot.room(), state, gameTick, true, remainSeconds);
        }

        if (gameTick >= state.getQueueDeadlineTick()) {
            startGameplay(roomId, gameTick);
        }
    }

    public Optional<IRoomGameplayRuntime> startGameplay(String roomId, long gameTick) {
        RoomRuntimeSlot<RoomRuntimeState> slot = requireRoomSlot(roomId);
        Optional<IRoomGameplayEntry> entry = gameplayEntryManager.find(slot.room().gameplayId());
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        slot.runtime().getRunningState().beginNewMatchInstance(gameTick);
        IRoomGameplayRuntime runtime = entry.get().createRuntime(slot.room(), slot.runtime().getRunningState());
        for (UUID playerId : slot.runtime().getMatchingState().getQueuePlayers()) {
            ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
            RoomMatchingActionBarService.clear(player);
            KilliconScoreboardBridge.resetPlayerMatchSnapshot(slot.runtime().getRunningState(), playerId);
            slot.runtime().getRunningState().addRoomPlayer(playerId, slot.runtime().getMatchingState().getQueueTeams().get(playerId));
            String squadLabel = slot.runtime().getSquadLabelsByPlayer().get(playerId);
            if (squadLabel != null && !squadLabel.isBlank()) {
                slot.runtime().getRunningState().getSquadLabelsByPlayer().put(playerId, squadLabel);
            }
        }
        runtime.onEnterMainPhase(slot.room(), gameTick);
        playerRespawnService.ensureInitialDeployTimelineInitialized(slot.room(), slot.runtime().getRunningState(), gameTick);
        supportBoxService.spawnPermanentSupportBoxes(slot.room());
        slot.runtime().getRunningState().setGameplayRuntime(runtime);
        for (UUID playerId : slot.runtime().getMatchingState().getQueuePlayers()) {
            ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
            if (player != null && !matchLoadService.isLoading(playerId)) {
                playerRespawnService.beginInitialDeploy(slot.room(), slot.runtime().getRunningState(), player, gameTick);
            }
        }
        slot.runtime().getMatchingState().clearQueue();
        runtimeRepository.save(slot.room().withLifecycleState(RoomLifecycleState.RUNNING), RoomRuntimeState::new);
        nametagVisibilityService.syncRunningRoom(slot.room(), slot.runtime().getRunningState());
        hudSyncService.syncRunningRoom(slot.room(), slot.runtime().getRunningState());
        return Optional.of(runtime);
    }

    public void tickGameplay(String roomId, long gameTick) {
        runtimeRepository.find(roomId).ifPresent(slot -> {
            if (slot.room().lifecycleState() == RoomLifecycleState.ENDING) {
                syncRunningNametagsIfDue(slot.room(), slot.runtime().getRunningState(), gameTick);
                slot.runtime().getRunningState().getGameplayRuntime()
                    .filter(RoomGameplayPresentationProvider.class::isInstance)
                    .map(RoomGameplayPresentationProvider.class::cast)
                    .ifPresent(runtime -> runtime.syncEndingHudSubtitles(slot.room(), gameTick));
                syncRunningHudIfDue(slot.room(), slot.runtime().getRunningState(), gameTick);
                shellingRuntimeService.tickRoom(slot.room(), slot.runtime().getRunningState(), gameTick);
                if (resultRuntimeService.tickEnding(slot.room(), slot.runtime().getRunningState(), gameTick)) {
                    finishEnding(slot, gameTick);
                }
                return;
            }
            if (slot.room().lifecycleState() != RoomLifecycleState.RUNNING) {
                return;
            }
            syncRunningNametagsIfDue(slot.room(), slot.runtime().getRunningState(), gameTick);
            detectZeroCampDraw(slot, gameTick);
            if (ConquestDataManager.of(server).serverConfig().isExperimentalPlayerBalanceEnabled()) {
                tickExperimentalBalance(slot, gameTick);
            }
            if (slot.room().lifecycleState() == RoomLifecycleState.ENDING) {
                syncRunningHudIfDue(slot.room(), slot.runtime().getRunningState(), gameTick);
                return;
            }
            playerStateService.maintainRunningState(slot.room(), slot.runtime().getRunningState(), slot.runtime().getRunningState().getRoomPlayers());
            playerRespawnService.tick(slot.room(), slot.runtime().getRunningState(), gameTick);
            itemReplenishService.tick(slot.room(), slot.runtime().getRunningState(), gameTick);
            ruleRuntimeService.tick(slot.room(), slot.runtime().getRunningState(), gameTick);
            groundSensorService.tickRoom(slot.room(), slot.runtime().getRunningState(), gameTick);
            squadBeaconService.tickRoom(slot.room(), slot.runtime().getRunningState(), gameTick);
            shellingRuntimeService.tickRoom(slot.room(), slot.runtime().getRunningState(), gameTick);
            markerRuntimeService.tickRoom(slot.room(), slot.runtime().getRunningState(), gameTick);
            vehicleRuntimeService.tickRoom(slot.room(), slot.runtime().getRunningState(), gameTick);
            slot.runtime().getRunningState().getGameplayRuntime()
                .ifPresent(runtime -> {
                    runtime.tick(slot.room(), gameTick);
                    runtime.pollMatchEndRequest().ifPresent(request -> beginMatchEnd(slot, request, gameTick));
                });
            syncRunningHudIfDue(slot.room(), slot.runtime().getRunningState(), gameTick);
        });
    }

    public void tickPlayerStates(long gameTick) {
        supportBoxService.tick(gameTick);
        groundSensorService.tickGlobal(gameTick);
        squadBeaconService.tickGlobal(gameTick);
        playerTransferQueueService.tick(gameTick);
        playerStateService.tick(gameTick);
        matchLoadService.tick(gameTick);
    }

    public void tickRooms(long gameTick) {
        for (RoomRuntimeSlot<RoomRuntimeState> slot : runtimeRepository.list()) {
            RoomLifecycleState lifecycleState = slot.room().lifecycleState();
            if (lifecycleState == RoomLifecycleState.MATCHING) {
                tickMatching(slot.room().roomId(), gameTick);
                continue;
            }
            if (lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING) {
                tickGameplay(slot.room().roomId(), gameTick);
            }
        }
    }

    public boolean prepareRespawnAnchor(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return findRunningSlotByPlayer(player.getUUID())
            .map(slot -> playerRespawnService.prepareRespawnAnchor(slot.room(), slot.runtime().getRunningState(), player))
            .orElse(false);
    }

    public boolean applyRespawnPostProcess(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return findRunningSlotByPlayer(player.getUUID())
            .map(slot -> playerRespawnService.applyRespawnPostProcess(slot.room(), slot.runtime().getRunningState(), player))
            .orElse(false);
    }

    public void tryRestoreInventorySnapshot(ServerPlayer player) {
        if (player == null) {
            return;
        }
        inventorySnapshotService.tryRestoreFromPersistent(player);
    }

    public KilliconScoreboardBridge.Payload buildKilliconScoreboardPayload(UUID requester) {
        if (requester == null) {
            return null;
        }
        Optional<String> roomId = findPlayerRoomId(requester);
        if (roomId.isEmpty()) {
            return null;
        }
        return runtimeRepository.find(roomId.get())
            .map(slot -> killiconService.buildPayload(slot.room(), slot.runtime(), requester))
            .orElse(null);
    }

    public Map<String, Object> buildKilliconObjectiveSnapshot(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return Map.of();
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = runtimeRepository.find(roomId);
        if (slotOptional.isEmpty()) {
            return Map.of();
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = slotOptional.get();
        RoomDefinition room = slot.room();
        if (room.lifecycleState() != RoomLifecycleState.RUNNING) {
            return Map.of();
        }
        RoomGameplayObjectiveSnapshotProvider objectiveSnapshotProvider =
            RoomGameplayObjectiveSnapshotProvider.resolve(slot.runtime().getRunningState());
        if (objectiveSnapshotProvider == null) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roomId", room.roomId());
        snapshot.put("roomPlayers", new LinkedHashSet<>(slot.runtime().getRunningState().getRoomPlayers()));
        snapshot.put("playerTeams", buildKilliconPlayerTeamsSnapshot(slot.runtime().getRunningState()));
        snapshot.put("activeCodesByPlayer", buildKilliconActiveCodesSnapshot(room, slot.runtime().getRunningState(), objectiveSnapshotProvider));
        snapshot.put("progressByCode", new LinkedHashMap<>(objectiveSnapshotProvider.objectiveProgressSnapshot()));
        snapshot.put("ownerByCode", buildKilliconTeamMapSnapshot(objectiveSnapshotProvider.objectiveOwnerSnapshot()));
        snapshot.put("drivingByCode", buildKilliconTeamMapSnapshot(objectiveSnapshotProvider.objectiveDrivingSnapshot()));
        snapshot.put("captureDisplayNames", buildKilliconCaptureDisplayNames(room));
        return snapshot;
    }

    public Map<String, Object> buildKilliconConquestSnapshot(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return Map.of();
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = runtimeRepository.find(roomId);
        if (slotOptional.isEmpty()) {
            return Map.of();
        }
        RoomDefinition room = slotOptional.get().room();
        if (room == null || RoomGameplayId.RUSH.equals(room.gameplayId())) {
            return Map.of();
        }
        return buildKilliconObjectiveSnapshot(roomId);
    }

    public boolean recordKilliconMatchDelta(UUID playerId, float scoreDelta, int killDelta, int deathDelta, int assistDelta, int reviveDelta) {
        if (playerId == null) {
            return false;
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = findRunningSlotByPlayer(playerId);
        if (slotOptional.isEmpty()) {
            return false;
        }
        RunningRuntimeState runningState = slotOptional.get().runtime().getRunningState();
        RunningRuntimeState.KilliconMatchStats previous = runningState.getKilliconMatchStatsByPlayer()
            .getOrDefault(playerId, new RunningRuntimeState.KilliconMatchStats(0.0F, 0, 0, 0, 0));
        runningState.getKilliconMatchStatsByPlayer().put(
            playerId,
            new RunningRuntimeState.KilliconMatchStats(
                Math.max(0.0F, previous.score() + scoreDelta),
                Math.max(0, previous.kill() + killDelta),
                Math.max(0, previous.death() + deathDelta),
                Math.max(0, previous.assist() + assistDelta),
                Math.max(0, previous.revive() + reviveDelta)
            )
        );
        return true;
    }

    /**
     * 记录一次局内 honor 触发(由 killicon 侧经 KilliconScoreboardBridge.recordMatchHonor 通知)。仅统计 RUNNING/ENDING 对局内玩家。
     * @return 0 = 未记录(非对局/参数无效); 1 = 已记录, 非本局最高; 2 = 已记录, 且该玩家当前为该 honor 本局最高(并列也算)。
     */
    public int recordKilliconMatchHonor(UUID playerId, String honorId) {
        if (playerId == null || honorId == null || honorId.isBlank()) {
            return 0;
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = findRunningSlotByPlayer(playerId);
        if (slotOptional.isEmpty()) {
            return 0;
        }
        RunningRuntimeState runningState = slotOptional.get().runtime().getRunningState();
        Map<UUID, Integer> perPlayer = runningState.getKilliconMatchHonorByHonor()
            .computeIfAbsent(honorId, k -> new java.util.HashMap<>());
        int newCount = perPlayer.merge(playerId, 1, Integer::sum);
        int matchBestCount = 0;
        for (Integer count : perPlayer.values()) {
            if (count != null && count > matchBestCount) {
                matchBestCount = count;
            }
        }
        return newCount >= matchBestCount ? 2 : 1;
    }

    /** 构建局内 honor 最佳查询结果: 请求者所在对局内, 每 honor 取本局获得次数最多的玩家与次数。非对局返回空列表。 */
    public List<KilliconScoreboardBridge.MatchHonorBest> buildKilliconMatchHonorBest(UUID requester) {
        if (requester == null) {
            return List.of();
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = findRunningSlotByPlayer(requester);
        if (slotOptional.isEmpty()) {
            return List.of();
        }
        RunningRuntimeState runningState = slotOptional.get().runtime().getRunningState();
        Map<String, Map<UUID, Integer>> honorByHonor = runningState.getKilliconMatchHonorByHonor();
        if (honorByHonor.isEmpty()) {
            return List.of();
        }
        List<KilliconScoreboardBridge.MatchHonorBest> result = new java.util.ArrayList<>(honorByHonor.size());
        for (Map.Entry<String, Map<UUID, Integer>> honorEntry : honorByHonor.entrySet()) {
            String honorId = honorEntry.getKey();
            UUID bestPlayer = null;
            int bestCount = 0;
            for (Map.Entry<UUID, Integer> playerEntry : honorEntry.getValue().entrySet()) {
                int count = playerEntry.getValue() == null ? 0 : playerEntry.getValue();
                if (count > bestCount) {
                    bestCount = count;
                    bestPlayer = playerEntry.getKey();
                }
            }
            if (bestPlayer != null && bestCount > 0) {
                result.add(new KilliconScoreboardBridge.MatchHonorBest(honorId, bestPlayer, bestCount));
            }
        }
        return result;
    }

    public int getPlayerSupportSpentScore(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return findRunningSlotByPlayer(playerId)
            .map(slot -> SupportScoreService.getSpentScore(slot.runtime().getRunningState(), playerId))
            .orElse(0);
    }

    public int getPlayerRemainingSupportScore(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return findRunningSlotByPlayer(playerId)
            .map(slot -> SupportScoreService.getRemainingScore(slot.runtime().getRunningState(), playerId))
            .orElse(0);
    }

    public SupportScoreService.ConsumptionResult tryConsumePlayerSupportScore(UUID playerId, int cost) {
        if (playerId == null) {
            return new SupportScoreService.ConsumptionResult(false, 0, 0, Math.max(0, cost));
        }
        return findRunningSlotByPlayer(playerId)
            .map(slot -> SupportScoreService.tryConsumeScore(slot.runtime().getRunningState(), playerId, cost))
            .orElse(new SupportScoreService.ConsumptionResult(false, 0, 0, Math.max(0, cost)));
    }

    public int refundPlayerSupportScore(UUID playerId, int refundAmount) {
        if (playerId == null || refundAmount <= 0) {
            return getPlayerSupportSpentScore(playerId);
        }
        return findRunningSlotByPlayer(playerId)
            .map(slot -> {
                RunningRuntimeState runningState = slot.runtime().getRunningState();
                int currentSpent = SupportScoreService.getSpentScore(runningState, playerId);
                return SupportScoreService.setSpentScore(runningState, playerId, currentSpent - refundAmount);
            })
            .orElse(0);
    }

    public void clearPlayerSupportScore(UUID playerId) {
        if (playerId == null) {
            return;
        }
        findRunningSlotByPlayer(playerId)
            .ifPresent(slot -> SupportScoreService.clearPlayer(slot.runtime().getRunningState(), playerId));
    }

    public RoomShellingRuntimeService.StartRequestResult startShellingRequest(
        ServerPlayer player,
        String shellingId,
        int reservedCost,
        String originalSlot9ItemSnbt,
        long gameTick
    ) {
        if (player == null) {
            return RoomShellingRuntimeService.StartRequestResult.failure(
                RoomShellingRuntimeService.StartFailureReason.INVALID_CONTEXT,
                null,
                null
            );
        }
        return findRunningSlotByPlayer(player.getUUID())
            .map(slot -> shellingRuntimeService.startShellingRequest(
                slot.room(),
                slot.runtime().getRunningState(),
                player,
                shellingId,
                reservedCost,
                originalSlot9ItemSnbt,
                gameTick
            ))
            .orElse(RoomShellingRuntimeService.StartRequestResult.failure(
                RoomShellingRuntimeService.StartFailureReason.INVALID_CONTEXT,
                null,
                null
            ));
    }

    public boolean requestDeathmatchDeployView(ServerPlayer player, long gameTick) {
        if (player == null) {
            return false;
        }
        return playerSessionService.findRoomId(player.getUUID())
            .flatMap(runtimeRepository::find)
            .filter(slot -> slot.room().lifecycleState() == RoomLifecycleState.RUNNING)
            .map(slot -> playerRespawnService.requestDeathmatchDeployView(
                slot.room(),
                slot.runtime().getRunningState(),
                player,
                gameTick
            ))
            .orElse(false);
    }

    public boolean updateShellingTarget(ServerPlayer player, BlockPos targetBlockPos) {
        if (player == null || targetBlockPos == null) {
            return false;
        }
        return findRunningSlotByPlayer(player.getUUID())
            .map(slot -> shellingRuntimeService.updateShellingTarget(
                slot.room(),
                slot.runtime().getRunningState(),
                player.getUUID(),
                targetBlockPos
            ))
            .orElse(false);
    }

    public Optional<String> buildAdminDebugOverlayPayload(ServerPlayer player) {
        return adminDebugOverlayService.buildPayloadForPlayer(player);
    }

    private Map<UUID, String> buildKilliconPlayerTeamsSnapshot(RunningRuntimeState runningState) {
        Map<UUID, String> snapshot = new LinkedHashMap<>();
        if (runningState == null) {
            return snapshot;
        }
        for (Map.Entry<UUID, RoomTeamId> entry : runningState.getRoomTeams().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                snapshot.put(entry.getKey(), entry.getValue().name());
            }
        }
        return snapshot;
    }

    private Map<UUID, java.util.Set<String>> buildKilliconActiveCodesSnapshot(
        RoomDefinition room,
        RunningRuntimeState runningState,
        RoomGameplayObjectiveSnapshotProvider objectiveSnapshotProvider
    ) {
        Map<UUID, java.util.Set<String>> snapshot = new LinkedHashMap<>();
        if (room == null || runningState == null || objectiveSnapshotProvider == null) {
            return snapshot;
        }
        for (UUID playerId : runningState.getRoomPlayers()) {
            ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
            if (player == null || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            String activeCode = objectiveSnapshotProvider.activeObjectiveCode(room, player);
            if (activeCode == null || activeCode.isBlank()) {
                continue;
            }
            snapshot.put(playerId, java.util.Set.of(activeCode));
        }
        return snapshot;
    }

    private Map<String, String> buildKilliconTeamMapSnapshot(Map<String, RoomTeamId> source) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        if (source == null) {
            return snapshot;
        }
        for (Map.Entry<String, RoomTeamId> entry : source.entrySet()) {
            String code = entry.getKey();
            if (code == null || code.isBlank()) {
                continue;
            }
            RoomTeamId teamId = entry.getValue();
            snapshot.put(code, teamId == null ? "" : teamId.name());
        }
        return snapshot;
    }

    private Map<String, String> buildKilliconCaptureDisplayNames(RoomDefinition room) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        if (room == null) {
            return snapshot;
        }
        for (RoomCapturePointDefinition point : room.configuredObjectivePoints()) {
            snapshot.put(point.code(), point.displayName());
        }
        return snapshot;
    }

    private boolean handleRunningPlayerInvalidation(UUID playerId, long gameTick, RoomPlayerInvalidationReason reason) {
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return false;
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slot = runtimeRepository.find(roomId.get());
        if (slot.isEmpty()) {
            playerSessionService.removePlayer(playerId);
            return false;
        }
        RunningRuntimeState runningState = slot.get().runtime().getRunningState();
        if (slot.get().room().lifecycleState() != RoomLifecycleState.RUNNING
            && slot.get().room().lifecycleState() != RoomLifecycleState.ENDING
            || !runningState.getRoomPlayers().contains(playerId)) {
            return false;
        }
        notifyGameplayPlayerInvalidated(slot.get(), playerId, gameTick, reason);
        removeRunningPlayerFromSlot(playerId, slot.get(), gameTick, false);
        playerSessionService.removePlayer(playerId);
        return true;
    }

    private void notifyGameplayPlayerInvalidated(
        RoomRuntimeSlot<RoomRuntimeState> slot,
        UUID playerId,
        long gameTick,
        RoomPlayerInvalidationReason reason
    ) {
        if (slot == null || playerId == null || reason == null) {
            return;
        }
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        RoomTeamId teamId = runningState.getRoomTeams().get(playerId);
        RoomPlayerInvalidationReason effectiveReason = reason;
        int killerEntityId = -1;
        if (reason == RoomPlayerInvalidationReason.LOGOUT
            && (runningState.isPlayerPendingDowned(playerId) || runningState.isPlayerDowned(playerId))) {
            effectiveReason = RoomPlayerInvalidationReason.DEATH;
            killerEntityId = runningState.getDeathKillerEntityIdOrDefault(playerId);
        }
        final RoomPlayerInvalidationReason finalEffectiveReason = effectiveReason;
        final int finalKillerEntityId = killerEntityId;
        runningState.getGameplayRuntime().ifPresent(runtime ->
            runtime.onPlayerInvalidated(slot.room(), playerId, teamId, finalEffectiveReason, finalKillerEntityId, gameTick)
        );
    }

    private void removePlayerFromSlot(UUID playerId, RoomRuntimeSlot<RoomRuntimeState> slot, long gameTick, boolean shouldTransferLobby) {
        MatchingRuntimeState matchingState = slot.runtime().getMatchingState();
        if (matchingState.getQueuePlayers().contains(playerId)) {
            ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
            matchingCoordinator.leaveQueue(matchingState, playerId);
            KilliconScoreboardBridge.clearPlayerMatchSnapshot(slot.runtime().getRunningState(), playerId);
            squadAssignmentService.removePlayer(slot.runtime(), playerId);
            if (player != null) {
                PlayerInventorySanitizer.clearArmorSlots(player);
                inventorySnapshotService.restore(player, slot.runtime());
                clearTransientRoomClientState(player);
            }
            nametagVisibilityService.clearPlayer(player);
            ruleRuntimeService.clearPlayer(playerId);
            resultRuntimeService.clearPlayer(player);
            markerRuntimeService.clearPlayer(player);
            hudSyncService.clearPlayer(player);
            RoomMatchingActionBarService.clear(player);
            playerStateService.clearPlayerState(playerId);
            if (shouldTransferLobby && player != null) {
                scheduleLobbyTransfer(player, gameTick);
            }
            if (matchingState.getQueuePlayers().isEmpty()
                && slot.room().lifecycleState() == RoomLifecycleState.MATCHING) {
                runtimeRepository.save(slot.room().withLifecycleState(RoomLifecycleState.IDLE), RoomRuntimeState::new);
            }
            return;
        }

        RunningRuntimeState runningState = slot.runtime().getRunningState();
        if (!runningState.getRoomPlayers().contains(playerId)) {
            return;
        }

        notifyGameplayPlayerInvalidated(slot, playerId, gameTick, RoomPlayerInvalidationReason.LOGOUT);
        removeRunningPlayerFromSlot(playerId, slot, gameTick, shouldTransferLobby);
    }

    private void removeRunningPlayerFromSlot(UUID playerId, RoomRuntimeSlot<RoomRuntimeState> slot, long gameTick, boolean shouldTransferLobby) {
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        playerRejoinService.markRejoinable(slot.room(), runningState, playerId, gameTick);
        ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
        HeroKitSupportService.endHeroKitState(server, runningState, playerId, gameTick);
        shellingRuntimeService.abortShellingRequest(
            slot.room(),
            runningState,
            player,
            playerId,
            RoomShellingRuntimeService.AbortReason.PLAYER_LEFT_ROOM
        );
        if (player != null) {
            PlayerInventorySanitizer.clearArmorSlots(player);
            inventorySnapshotService.restore(player, slot.runtime());
            clearTransientRoomClientState(player);
        }
        if (shouldTransferLobby) {
            SupportScoreService.clearPlayer(runningState, playerId);
        }
        nametagVisibilityService.clearPlayer(player);
        ruleRuntimeService.clearPlayer(playerId);
        resultRuntimeService.clearPlayer(player);
        markerRuntimeService.clearPlayer(player);
        hudSyncService.clearPlayer(player);
        runningState.clearRespawnFlow(playerId);
        runningState.removeRoomPlayer(playerId);
        squadAssignmentService.removePlayer(slot.runtime(), playerId);
        playerStateService.clearPlayerState(playerId);
        playerTransferQueueService.cancelPendingTransfers(playerId);
        if (shouldTransferLobby && player != null) {
            scheduleLobbyTransfer(player, gameTick);
        }
        if (slot.room().lifecycleState() == RoomLifecycleState.RUNNING && !runningState.getRoomPlayers().isEmpty()) {
            detectZeroCampDraw(slot, gameTick);
            if (slot.room().lifecycleState() == RoomLifecycleState.ENDING) {
                return;
            }
        }
        if (runningState.getRoomPlayers().isEmpty()
            && (slot.room().lifecycleState() == RoomLifecycleState.RUNNING || slot.room().lifecycleState() == RoomLifecycleState.ENDING)) {
            finishEnding(slot, gameTick);
            return;
        }
        if (runningState.getRoomPlayers().isEmpty()) {
            cleanupVacatedRuntime(slot, gameTick);
        }
    }

    private void removeMaintenancePlayerFromSlot(UUID playerId, RoomRuntimeSlot<RoomRuntimeState> slot) {
        if (playerId == null || slot == null) {
            return;
        }
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        if (!runningState.getRoomPlayers().contains(playerId)) {
            return;
        }
        ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
        HeroKitSupportService.endHeroKitState(server, runningState, playerId, server.getTickCount());
        shellingRuntimeService.abortShellingRequest(
            slot.room(),
            runningState,
            player,
            playerId,
            RoomShellingRuntimeService.AbortReason.PLAYER_LEFT_ROOM
        );
        if (player != null) {
            PlayerInventorySanitizer.clearArmorSlots(player);
            inventorySnapshotService.restore(player, slot.runtime());
            clearTransientRoomClientState(player);
        }
        SupportScoreService.clearPlayer(runningState, playerId);
        nametagVisibilityService.clearPlayer(player);
        ruleRuntimeService.clearPlayer(playerId);
        resultRuntimeService.clearPlayer(player);
        markerRuntimeService.clearPlayer(player);
        hudSyncService.clearPlayer(player);
        RoomMatchingActionBarService.clear(player);
        playerStateService.clearPlayerState(playerId);
        playerTransferQueueService.cancelPendingTransfers(playerId);
        runningState.clearRespawnFlow(playerId);
        runningState.removeRoomPlayer(playerId);
        squadAssignmentService.removePlayer(slot.runtime(), playerId);
    }

    private RoomRuntimeSlot<RoomRuntimeState> requireRoomSlot(String roomId) {
        return runtimeRepository.find(roomId)
            .orElseThrow(() -> new IllegalArgumentException("房间不存在: " + roomId));
    }

    private Optional<RoomRuntimeSlot<RoomRuntimeState>> findRunningSlotByPlayer(UUID playerId) {
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return Optional.empty();
        }
        return runtimeRepository.find(roomId.get())
            .filter(slot -> slot.room().lifecycleState() == RoomLifecycleState.RUNNING || slot.room().lifecycleState() == RoomLifecycleState.ENDING);
    }

    private boolean containsPlayer(RoomRuntimeSlot<RoomRuntimeState> slot, UUID playerId) {
        if (slot == null || playerId == null) {
            return false;
        }
        return slot.runtime().getMatchingState().getQueuePlayers().contains(playerId)
            || slot.runtime().getRunningState().getRoomPlayers().contains(playerId);
    }

    private void beginMatchEnd(RoomRuntimeSlot<RoomRuntimeState> slot, org.mods.gd656conquest.server.room.gameplay.RoomMatchEndRequest request, long gameTick) {
        if (slot == null || request == null || slot.room().lifecycleState() != RoomLifecycleState.RUNNING) {
            return;
        }
        if (!resultRuntimeService.beginMatchEnd(
            slot.room(),
            slot.runtime().getRunningState(),
            request.winnerTeam(),
            request.winnerPlayerId(),
            request.reason(),
            gameTick
        )) {
            return;
        }
        runtimeRepository.save(slot.room().withLifecycleState(RoomLifecycleState.ENDING), RoomRuntimeState::new);
    }

    private void finishEnding(RoomRuntimeSlot<RoomRuntimeState> slot, long gameTick) {
        String roomId = slot.room().roomId();
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        shellingRuntimeService.abortAllShellingRequests(
            slot.room(),
            runningState,
            RoomShellingRuntimeService.AbortReason.ROOM_UNAVAILABLE
        );
        supportBoxService.clearPermanentSupportBoxes(slot.room().roomId());
        java.util.List<UUID> players = java.util.List.copyOf(runningState.getRoomPlayers());
        for (UUID playerId : players) {
            ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
            if (player != null) {
                // GD656Mods: 对局结束时实现对局内暂存的配装修改
                org.mods.gd656conquest.server.loadout.LoadoutUiService.applyPendingLoadoutEdits(player);
                PlayerInventorySanitizer.clearArmorSlots(player);
                inventorySnapshotService.restore(player, slot.runtime());
                clearTransientRoomClientState(player);
            }
            nametagVisibilityService.clearPlayer(player);
            ruleRuntimeService.clearPlayer(playerId);
            resultRuntimeService.clearPlayer(player);
            markerRuntimeService.clearPlayer(player);
            hudSyncService.clearPlayer(player);
            RoomMatchingActionBarService.clear(player);
            playerStateService.clearPlayerState(playerId);
            runningState.clearRespawnFlow(playerId);
            runningState.removeRoomPlayer(playerId);
            squadAssignmentService.removePlayer(slot.runtime(), playerId);
            playerTransferQueueService.cancelPendingTransfers(playerId);
            if (player != null) {
                scheduleLobbyTransfer(player, gameTick);
            }
            playerSessionService.removePlayer(playerId);
        }
        runningState.getGameplayRuntime().ifPresent(runtime -> runtime.onLeaveMainPhase(slot.room(), gameTick));
        runningState.clearGameplayRuntime();
        // 对局结束: 清空 Killicon 对局级荣誉状态(爆破专家等, 玩家离开再回来累计仍在直到对局结束)
        org.mods.gd656conquest.server.integration.KilliconHonorBridge.triggerRoundEnd();
        runningState.clearRoomPlayers();
        runningState.clearResultState();
        nametagVisibilityService.clearRoom(slot.room());
        vehicleRuntimeService.clearRoom(roomId);
        RoomAdminLifecycleService.OperationResult result = new RoomAdminLifecycleService(server).cleanRoom(roomId, gameTick);
        if (!result.success()) {
            ServerLogger.failure("对局结束后的房间热重置失败: %s | %s", roomId, result.message());
        } else {
            ServerLogger.info("对局结束后的房间热重置完成: %s -> IDLE", roomId);
        }
    }

    private void cleanupVacatedRuntime(RoomRuntimeSlot<RoomRuntimeState> slot, long gameTick) {
        if (slot == null) {
            return;
        }
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        shellingRuntimeService.abortAllShellingRequests(
            slot.room(),
            runningState,
            RoomShellingRuntimeService.AbortReason.ROOM_UNAVAILABLE
        );
        supportBoxService.clearPermanentSupportBoxes(slot.room().roomId());
        runningState.getGameplayRuntime().ifPresent(runtime -> runtime.onLeaveMainPhase(slot.room(), gameTick));
        runningState.clearGameplayRuntime();
        runningState.clearRoomPlayers();
        runningState.clearResultState();
        nametagVisibilityService.clearRoom(slot.room());
        vehicleRuntimeService.clearRoom(slot.room().roomId());
    }

    private void clearTransientRoomClientState(ServerPlayer player) {
        if (player == null) {
            return;
        }
        playerStateService.normalizePlayerModelState(player);
        TaczServerPoseCompat.clearCrawlState(player);
        PlayerAnimatorDebugDimensionsState.clearPlayer(player);
        NetworkManager.sendToPlayer(player, RoomDownedStatePacket.clear());
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        NetworkManager.sendToPlayer(player, new RoomMatchLoadPacket(false, 0, "", "", "", "", "", "", ""));
        NetworkManager.sendToPlayer(player, PlayerAnimatorDebugPacket.stop(player.getUUID()));
    }

    private void detectZeroCampDraw(RoomRuntimeSlot<RoomRuntimeState> slot, long gameTick) {
        if (slot == null || slot.room().lifecycleState() != RoomLifecycleState.RUNNING) {
            return;
        }
        RunningRuntimeState state = slot.runtime().getRunningState();
        if (state == null || state.getResultSequenceEndTick() > 0 || state.getRoomPlayers().isEmpty()) {
            return;
        }
        boolean hasEnabledCampA = slot.room().commonConfig().matchingConfig().enabledTeams().contains(RoomTeamId.CAMP_A);
        boolean hasEnabledCampB = slot.room().commonConfig().matchingConfig().enabledTeams().contains(RoomTeamId.CAMP_B);
        if (!hasEnabledCampA || !hasEnabledCampB) {
            return;
        }
        int campACount = 0;
        int campBCount = 0;
        for (UUID playerId : state.getRoomPlayers()) {
            RoomTeamId teamId = state.getRoomTeams().get(playerId);
            if (teamId == RoomTeamId.CAMP_A) {
                campACount++;
            } else if (teamId == RoomTeamId.CAMP_B) {
                campBCount++;
            }
        }
        if (campACount > 0 && campBCount > 0) {
            return;
        }
        beginMatchEnd(slot, new RoomMatchEndRequest((RoomTeamId) null, "运行中某阵营玩家数归零，判定平局"), gameTick);
    }

    private void syncRunningHudIfDue(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null) {
            return;
        }
        if (state.getLastHudSyncTick() >= 0L && gameTick - state.getLastHudSyncTick() < RUNNING_HUD_SYNC_INTERVAL_TICKS) {
            return;
        }
        state.setLastHudSyncTick(gameTick);
        hudSyncService.syncRunningRoom(room, state);
    }

    private void syncRunningNametagsIfDue(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null) {
            return;
        }
        if (state.getLastNametagSyncTick() >= 0L && gameTick - state.getLastNametagSyncTick() < RUNNING_NAMETAG_SYNC_INTERVAL_TICKS) {
            return;
        }
        state.setLastNametagSyncTick(gameTick);
        nametagVisibilityService.syncRunningRoom(room, state);
    }

    private void syncMatchingActionBars(RoomDefinition room, MatchingRuntimeState state, long gameTick, boolean countdownReady, int remainSeconds) {
        if (room == null || state == null) {
            return;
        }
        long primaryInterval = 100L;
        long secondaryInterval = countdownReady && remainSeconds <= 10 ? 20L : 100L;
        boolean refreshPrimary = state.getLastPrimaryActionBarTick() < 0 || gameTick - state.getLastPrimaryActionBarTick() >= primaryInterval;
        boolean refreshSecondary = state.getLastSecondaryActionBarTick() < 0 || gameTick - state.getLastSecondaryActionBarTick() >= secondaryInterval;
        if (!refreshPrimary && !refreshSecondary) {
            return;
        }
        if (refreshPrimary) {
            state.setLastPrimaryActionBarTick(gameTick);
        }
        if (refreshSecondary) {
            state.setLastSecondaryActionBarTick(gameTick);
        }
        for (UUID playerId : state.getQueuePlayers()) {
            ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
            if (player == null) {
                continue;
            }
            if (refreshPrimary) {
                RoomMatchingActionBarService.showQueueStatus(player, room.commonConfig().matchingConfig(), state);
            }
            if (refreshSecondary) {
                if (countdownReady && remainSeconds > 0) {
                    RoomMatchingActionBarService.showCountdownStatus(player, remainSeconds);
                } else {
                    RoomMatchingActionBarService.showWaitingStatus(player);
                }
            }
        }
    }

    private void scheduleLobbyTransfer(ServerPlayer player, long gameTick) {
        if (player == null) {
            return;
        }
        playerTransferQueueService.enqueueLobbyTransfer(player, gameTick);
    }

    private boolean preparePlayerForRoomJoin(ServerPlayer player, String targetRoomId) {
        if (player == null || player.server == null) {
            return false;
        }
        UUID playerId = player.getUUID();
        Optional<String> boundRoomId = playerSessionService.findRoomId(playerId);
        if (boundRoomId.isPresent()) {
            if (runtimeRepository.find(boundRoomId.get()).isPresent()) {
                return false;
            }
            playerSessionService.removePlayer(playerId);
        }
        playerTransferQueueService.cancelPendingTransfers(playerId);
        RangeRuntimeManager rangeRuntimeManager = RangeRuntimeManager.of(player.server);
        if (rangeRuntimeManager.isInRange(playerId)) {
            rangeRuntimeManager.leaveRangeForTransfer(player);
        }
        return playerSessionService.findRoomId(playerId)
            .map(roomId -> roomId.equalsIgnoreCase(targetRoomId))
            .orElse(true);
    }

    private void notifyJoinTeleportFailure(ServerPlayer player, RoomDefinition room) {
        if (player == null || room == null) {
            return;
        }
        String dimensionId = room.commonConfig().dimensionId();
        String target = dimensionId == null || dimensionId.isBlank() ? room.roomId() : dimensionId;
        ServerLogger.sendToClient(player, ClientMessageType.FAILURE,
            "加入房间失败：目标维度不可用或传送失败，已停止进入流程（%s）",
            target);
    }

    private Optional<RoomJoinResult> tryRejoinRunningMatch(
        RoomRuntimeSlot<RoomRuntimeState> slot,
        ServerPlayer player,
        long gameTick
    ) {
        if (slot == null || player == null) {
            return Optional.empty();
        }
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        UUID playerId = player.getUUID();
        RunningRuntimeState.RejoinTicket ticket = playerRejoinService.resolveValidTicket(slot.room(), runningState, playerId).orElse(null);
        if (ticket == null) {
            return Optional.empty();
        }
        RoomTeamId targetTeam = ticket.teamId();
        if (ConquestDataManager.of(player.server).serverConfig().isExperimentalPlayerBalanceEnabled()) {
            int countA = countRoomPlayersByTeam(runningState, RoomTeamId.CAMP_A);
            int countB = countRoomPlayersByTeam(runningState, RoomTeamId.CAMP_B);
            if (targetTeam == RoomTeamId.CAMP_A && countA > countB) {
                targetTeam = RoomTeamId.CAMP_B;
            } else if (targetTeam == RoomTeamId.CAMP_B && countB > countA) {
                targetTeam = RoomTeamId.CAMP_A;
            }
        }
        inventorySnapshotService.backupAndClear(player, slot.runtime());
        playerRejoinService.clearPlayerTickets(playerId);
        bindPlayerToRoom(playerId, slot.room().roomId());
        KilliconScoreboardBridge.ensurePlayerMatchSnapshot(runningState, playerId);
        runningState.addRoomPlayer(playerId, targetTeam);
        if (ticket.squadLabel() != null && !ticket.squadLabel().isBlank() && targetTeam == ticket.teamId()) {
            slot.runtime().getSquadLabelsByPlayer().put(playerId, ticket.squadLabel());
            runningState.getSquadLabelsByPlayer().put(playerId, ticket.squadLabel());
        }
        RoomMatchingActionBarService.clear(player);
        if (RoomMatchLoadService.isEnabled(slot.room())) {
            // Intercept: use match-loading phase before deploy
            matchLoadService.beginMatchLoad(
                player, slot.room(), runningState, gameTick,
                RoomMatchLoadService.EntryKind.REJOIN, targetTeam);
            return Optional.of(new RoomJoinResult(RoomJoinResult.Kind.REJOIN_RUNNING, slot.room().roomId(), targetTeam));
        }
        if (slot.room().lifecycleState() == RoomLifecycleState.ENDING) {
            resultRuntimeService.syncRejoinedPlayer(slot.room(), runningState, player, gameTick);
            nametagVisibilityService.syncRunningRoom(slot.room(), runningState);
            hudSyncService.syncRunningRoom(slot.room(), runningState);
            return Optional.of(new RoomJoinResult(RoomJoinResult.Kind.REJOIN_ENDING, slot.room().roomId(), ticket.teamId()));
        }
        resultRuntimeService.clearPlayer(player);
        playerRespawnService.beginReconnectDeploy(slot.room(), runningState, player, gameTick);
        nametagVisibilityService.syncRunningRoom(slot.room(), runningState);
        hudSyncService.syncRunningRoom(slot.room(), runningState);
        return Optional.of(new RoomJoinResult(RoomJoinResult.Kind.REJOIN_RUNNING, slot.room().roomId(), targetTeam));
    }

    private int countRoomPlayersByTeam(RunningRuntimeState runningState, RoomTeamId team) {
        int count = 0;
        for (UUID memberId : runningState.getRoomPlayers()) {
            if (runningState.getRoomTeams().get(memberId) == team) {
                count++;
            }
        }
        return count;
    }

    private void tickExperimentalBalance(RoomRuntimeSlot<RoomRuntimeState> slot, long gameTick) {
        if (slot.room().lifecycleState() != RoomLifecycleState.RUNNING) {
            return;
        }
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        int countA = countRoomPlayersByTeam(runningState, RoomTeamId.CAMP_A);
        int countB = countRoomPlayersByTeam(runningState, RoomTeamId.CAMP_B);
        if (Math.abs(countA - countB) <= 1) {
            runningState.setTopScorerBalanceTarget(null);
            runningState.setTopScorerBalanceSinceTick(-1L);
            return;
        }
        RoomTeamId largerTeam = countA > countB ? RoomTeamId.CAMP_A : RoomTeamId.CAMP_B;
        UUID topScorerId = null;
        float topScore = Float.NEGATIVE_INFINITY;
        for (UUID playerId : runningState.getRoomPlayers()) {
            if (runningState.getRoomTeams().get(playerId) != largerTeam) {
                continue;
            }
            if (runningState.isInRespawnFlow(playerId)) {
                continue;
            }
            float score = org.mods.gd656conquest.server.integration.KilliconScoreboardBridge
                .getPlayerSnapshot(runningState, playerId).score();
            if (score > topScore) {
                topScore = score;
                topScorerId = playerId;
            }
        }
        if (topScorerId == null) {
            runningState.setTopScorerBalanceTarget(null);
            runningState.setTopScorerBalanceSinceTick(-1L);
            return;
        }
        // 该玩家已有挂起的换队（之前已触发过自动平衡），等待换队完成再评估
        if (runningState.getPendingTeamChanges().containsKey(topScorerId)) {
            return;
        }
        if (!topScorerId.equals(runningState.getTopScorerBalanceTarget())) {
            runningState.setTopScorerBalanceTarget(topScorerId);
            runningState.setTopScorerBalanceSinceTick(gameTick);
            return;
        }
        long elapsed = gameTick - runningState.getTopScorerBalanceSinceTick();
        if (elapsed >= 600L) {
            switchPlayerToOppositeTeam(slot.room().roomId(), topScorerId, "<实验性自动平衡>", gameTick);
        }
    }

    private Optional<RoomJoinResult> tryMidJoinRunningMatch(
        RoomRuntimeSlot<RoomRuntimeState> slot,
        ServerPlayer player,
        long gameTick
    ) {
        if (slot == null || player == null || slot.room().lifecycleState() != RoomLifecycleState.RUNNING) {
            return Optional.empty();
        }
        UUID playerId = player.getUUID();
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        RoomTeamId teamId = groupPlacementService.resolveRunningJoinTeam(slot.room(), runningState, playerId);
        if (teamId == null) {
            return Optional.empty();
        }
        inventorySnapshotService.backupAndClear(player, slot.runtime());
        playerRejoinService.clearPlayerTickets(playerId);
        bindPlayerToRoom(playerId, slot.room().roomId());
        KilliconScoreboardBridge.resetPlayerMatchSnapshot(runningState, playerId);
        runningState.addRoomPlayer(playerId, teamId);
        squadAssignmentService.assignRunningPlayer(slot.runtime(), playerId, teamId);
        RoomMatchingActionBarService.clear(player);
        resultRuntimeService.clearPlayer(player);
        if (RoomMatchLoadService.isEnabled(slot.room())) {
            // Intercept: use match-loading phase before deploy
            matchLoadService.beginMatchLoad(
                player, slot.room(), runningState, gameTick,
                RoomMatchLoadService.EntryKind.MIDJOIN, teamId);
            return Optional.of(new RoomJoinResult(RoomJoinResult.Kind.MIDJOIN_RUNNING, slot.room().roomId(), teamId));
        }
        playerRespawnService.beginMidJoinDeploy(slot.room(), runningState, player, gameTick);
        nametagVisibilityService.syncRunningRoom(slot.room(), runningState);
        hudSyncService.syncRunningRoom(slot.room(), runningState);
        return Optional.of(new RoomJoinResult(RoomJoinResult.Kind.MIDJOIN_RUNNING, slot.room().roomId(), teamId));
    }

    private Optional<RoomJoinResult> tryMidJoinEndingMatch(
        RoomRuntimeSlot<RoomRuntimeState> slot,
        ServerPlayer player,
        long gameTick
    ) {
        if (slot == null || player == null || slot.room().lifecycleState() != RoomLifecycleState.ENDING) {
            return Optional.empty();
        }
        UUID playerId = player.getUUID();
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        RoomTeamId teamId = groupPlacementService.resolveRunningJoinTeam(slot.room(), runningState, playerId);
        if (teamId == null) {
            return Optional.empty();
        }
        inventorySnapshotService.backupAndClear(player, slot.runtime());
        playerRejoinService.clearPlayerTickets(playerId);
        bindPlayerToRoom(playerId, slot.room().roomId());
        KilliconScoreboardBridge.resetPlayerMatchSnapshot(runningState, playerId);
        runningState.addRoomPlayer(playerId, teamId);
        squadAssignmentService.assignRunningPlayer(slot.runtime(), playerId, teamId);
        RoomMatchingActionBarService.clear(player);
        resultRuntimeService.syncLateJoinedPlayer(slot.room(), runningState, player, gameTick);
        nametagVisibilityService.syncRunningRoom(slot.room(), runningState);
        hudSyncService.syncRunningRoom(slot.room(), runningState);
        return Optional.of(new RoomJoinResult(RoomJoinResult.Kind.MIDJOIN_ENDING, slot.room().roomId(), teamId));
    }

    private void applyPendingTeamChangeAfterDeath(RoomDefinition room, RunningRuntimeState state, UUID playerId) {
        if (room == null || state == null || playerId == null) {
            return;
        }
        Optional<RoomRuntimeSlot<RoomRuntimeState>> slotOptional = runtimeRepository.find(room.roomId());
        if (slotOptional.isEmpty()) {
            return;
        }
        RoomTeamSwitchService.TeamSwitchResult result = teamSwitchService.applyPendingTeamChangeAfterDeath(
            room,
            slotOptional.get().runtime(),
            playerId
        );
        if (!result.changed()) {
            return;
        }
        ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
        if (player != null) {
            ServerLogger.sendToClient(player, ClientMessageType.SUCCESS,
                "你的队伍已切换为 %s，当前重生将按新队伍生效",
                resolveTeamDisplayName(room, state, result.currentTeam()));
        }
    }

    private String resolvePlayerName(ServerPlayer player, UUID playerId) {
        if (player != null) {
            return player.getGameProfile().getName();
        }
        if (playerId == null) {
            return "目标玩家";
        }
        return PlayerIdentityService.of(server).resolvePlayerName(playerId).orElse(playerId.toString());
    }


    private String resolveTeamDisplayName(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId) {
        return RoomTeamPresentationSupport.resolveTeamDisplayName(room, state, teamId);
    }
}
