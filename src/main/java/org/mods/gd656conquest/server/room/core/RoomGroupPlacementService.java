package org.mods.gd656conquest.server.room.core;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.room.matching.RoomMatchingCoordinator;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomMatchingConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomDeploySelectionService;
import org.mods.gd656conquest.server.room.runtime.RoomHudSyncService;
import org.mods.gd656conquest.server.room.runtime.RoomNametagVisibilityService;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerLaunchService;
import org.mods.gd656conquest.server.room.runtime.RoomSquadAssignmentService;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RoomGroupPlacementService {
    private final RoomRuntimeRepository<RoomRuntimeState> runtimeRepository;
    private final RoomPlayerSessionService playerSessionService;
    private final RoomMatchingCoordinator matchingCoordinator;
    private final RoomSquadAssignmentService squadAssignmentService;
    private final RoomDeploySelectionService deploySelectionService;
    private final RoomPlayerLaunchService playerLaunchService;
    private final RoomHudSyncService hudSyncService;
    private final RoomNametagVisibilityService nametagVisibilityService;
    private final PresentationSync presentationSync;

    public RoomGroupPlacementService(
        RoomRuntimeRepository<RoomRuntimeState> runtimeRepository,
        RoomPlayerSessionService playerSessionService,
        RoomMatchingCoordinator matchingCoordinator,
        RoomSquadAssignmentService squadAssignmentService,
        RoomDeploySelectionService deploySelectionService,
        RoomPlayerLaunchService playerLaunchService,
        RoomHudSyncService hudSyncService,
        RoomNametagVisibilityService nametagVisibilityService,
        PresentationSync presentationSync
    ) {
        this.runtimeRepository = Objects.requireNonNull(runtimeRepository, "runtimeRepository");
        this.playerSessionService = Objects.requireNonNull(playerSessionService, "playerSessionService");
        this.matchingCoordinator = Objects.requireNonNull(matchingCoordinator, "matchingCoordinator");
        this.squadAssignmentService = Objects.requireNonNull(squadAssignmentService, "squadAssignmentService");
        this.deploySelectionService = Objects.requireNonNull(deploySelectionService, "deploySelectionService");
        this.playerLaunchService = Objects.requireNonNull(playerLaunchService, "playerLaunchService");
        this.hudSyncService = Objects.requireNonNull(hudSyncService, "hudSyncService");
        this.nametagVisibilityService = Objects.requireNonNull(nametagVisibilityService, "nametagVisibilityService");
        this.presentationSync = Objects.requireNonNull(presentationSync, "presentationSync");
    }

    public Optional<RoomTeamId> findCurrentTeam(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return Optional.empty();
        }
        return runtimeRepository.find(roomId.get())
            .map(slot -> {
                RoomTeamId runningTeam = slot.runtime().getRunningState().getRoomTeams().get(playerId);
                if (runningTeam != null) {
                    return runningTeam;
                }
                return slot.runtime().getMatchingState().getQueueTeams().get(playerId);
            });
    }

    public Optional<String> findSquadLabel(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Optional<String> roomId = playerSessionService.findRoomId(playerId);
        if (roomId.isEmpty()) {
            return Optional.empty();
        }
        return runtimeRepository.find(roomId.get())
            .map(slot -> slot.runtime().getSquadLabelsByPlayer().get(playerId))
            .filter(label -> label != null && !label.isBlank());
    }

    public boolean canJoinInProgressRoom(UUID playerId, String roomId) {
        if (playerId == null || roomId == null || roomId.isBlank()) {
            return false;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return false;
        }
        RoomLifecycleState lifecycleState = slot.room().lifecycleState();
        if (lifecycleState != RoomLifecycleState.RUNNING && lifecycleState != RoomLifecycleState.ENDING) {
            return false;
        }
        if (slot.runtime().getRunningState().getRejoinTickets().containsKey(playerId)) {
            return true;
        }
        return chooseRunningJoinTeam(slot.room(), slot.runtime().getRunningState(), playerId) != null;
    }

    public RoomTeamId resolveRunningJoinTeam(RoomDefinition room, RunningRuntimeState state, UUID joiningPlayerId) {
        return chooseRunningJoinTeam(room, state, joiningPlayerId);
    }

    public Optional<RoomGroupPlacementPlan> planGroupJoinPlacement(String roomId, int groupSize) {
        if (roomId == null || roomId.isBlank() || groupSize <= 0) {
            return Optional.empty();
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return Optional.empty();
        }
        RoomDefinition room = slot.room();
        RoomLifecycleState lifecycleState = room.lifecycleState();
        if (lifecycleState == RoomLifecycleState.MAINTENANCE) {
            return Optional.of(new RoomGroupPlacementPlan(lifecycleState, null, ""));
        }
        if (lifecycleState == RoomLifecycleState.IDLE || lifecycleState == RoomLifecycleState.MATCHING) {
            RoomTeamId teamId = matchingCoordinator.chooseBestTeam(
                slot.runtime().getMatchingState(),
                room.commonConfig().matchingConfig(),
                groupSize
            );
            if (teamId == null) {
                return Optional.empty();
            }
            return Optional.of(new RoomGroupPlacementPlan(
                lifecycleState,
                teamId,
                squadAssignmentService.allocateMatchingSquadLabel(slot.runtime(), teamId)
            ));
        }
        if (lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING) {
            RoomTeamId teamId = chooseRunningJoinTeam(room, slot.runtime().getRunningState(), null, groupSize);
            if (teamId == null) {
                return Optional.empty();
            }
            return Optional.of(new RoomGroupPlacementPlan(
                lifecycleState,
                teamId,
                squadAssignmentService.allocateRunningSquadLabel(slot.runtime(), teamId)
            ));
        }
        return Optional.empty();
    }

    public Optional<RoomGroupPlacementPlan> resolvePlayerPlacement(String roomId, UUID playerId) {
        if (roomId == null || roomId.isBlank() || playerId == null) {
            return Optional.empty();
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return Optional.empty();
        }
        RoomLifecycleState lifecycleState = slot.room().lifecycleState();
        if (lifecycleState == RoomLifecycleState.MAINTENANCE) {
            return Optional.of(new RoomGroupPlacementPlan(lifecycleState, null, ""));
        }
        RoomTeamId teamId = findCurrentTeam(playerId).orElse(null);
        if (teamId == null) {
            return Optional.empty();
        }
        String squadLabel = findSquadLabel(playerId)
            .orElseGet(() -> reserveSquadLabel(roomId, teamId).orElse(""));
        return Optional.of(new RoomGroupPlacementPlan(lifecycleState, teamId, squadLabel));
    }

    public Optional<String> reserveSquadLabel(String roomId, RoomTeamId teamId) {
        if (roomId == null || roomId.isBlank() || teamId == null) {
            return Optional.empty();
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return Optional.empty();
        }
        RoomLifecycleState lifecycleState = slot.room().lifecycleState();
        if (lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING) {
            return Optional.of(squadAssignmentService.allocateRunningSquadLabel(slot.runtime(), teamId));
        }
        return Optional.of(squadAssignmentService.allocateMatchingSquadLabel(slot.runtime(), teamId));
    }

    public boolean canFitGroupInTeam(String roomId, RoomTeamId teamId, int requiredSlots) {
        if (roomId == null || roomId.isBlank() || teamId == null || requiredSlots < 0) {
            return false;
        }
        if (requiredSlots == 0) {
            return true;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return false;
        }
        RoomLifecycleState lifecycleState = slot.room().lifecycleState();
        RoomMatchingConfig config = slot.room().commonConfig().matchingConfig();
        if (lifecycleState == RoomLifecycleState.IDLE || lifecycleState == RoomLifecycleState.MATCHING) {
            return matchingCoordinator.chooseBestTeam(slot.runtime().getMatchingState(), config, requiredSlots) == teamId
                || config.maxPlayersForTeam(teamId) - countMatchingPlayersInTeam(slot.runtime().getMatchingState(), teamId) >= requiredSlots;
        }
        if (lifecycleState == RoomLifecycleState.RUNNING) {
            return countRunningOccupiedTeamSlots(slot.runtime().getRunningState(), null, teamId) + requiredSlots <= config.maxPlayersForTeam(teamId);
        }
        return lifecycleState == RoomLifecycleState.ENDING;
    }

    public boolean applyGroupPlacement(String roomId, UUID playerId, RoomTeamId targetTeam, String squadLabel, long gameTick) {
        if (roomId == null || roomId.isBlank() || playerId == null) {
            return false;
        }
        RoomRuntimeSlot<RoomRuntimeState> slot = runtimeRepository.find(roomId).orElse(null);
        if (slot == null) {
            return false;
        }
        RoomLifecycleState lifecycleState = slot.room().lifecycleState();
        if (lifecycleState == RoomLifecycleState.MATCHING || lifecycleState == RoomLifecycleState.IDLE) {
            MatchingRuntimeState matchingState = slot.runtime().getMatchingState();
            if (!matchingState.getQueuePlayers().contains(playerId)) {
                return false;
            }
            if (targetTeam != null) {
                matchingState.getQueueTeams().put(playerId, targetTeam);
            }
            matchingState.setLastPrimaryActionBarTick(-1L);
            matchingState.setLastSecondaryActionBarTick(-1L);
            squadAssignmentService.assignMatchingSquad(slot.runtime(), playerId, squadLabel);
            ServerPlayer player = playerLaunchService.findOnlinePlayer(playerId);
            if (player != null) {
                presentationSync.resync(player, gameTick);
            }
            hudSyncService.syncMatchingRoom(slot.room(), slot.runtime(), gameTick);
            return true;
        }
        if (lifecycleState != RoomLifecycleState.RUNNING && lifecycleState != RoomLifecycleState.ENDING) {
            return false;
        }
        RunningRuntimeState runningState = slot.runtime().getRunningState();
        if (!runningState.getRoomPlayers().contains(playerId)) {
            return false;
        }
        if (targetTeam != null) {
            runningState.getRoomTeams().put(playerId, targetTeam);
            if (lifecycleState == RoomLifecycleState.RUNNING) {
                deploySelectionService.replaceSelectionWithTeamFallback(runningState, playerId, targetTeam);
            }
        }
        squadAssignmentService.assignRunningSquad(slot.runtime(), playerId, squadLabel);
        nametagVisibilityService.syncRunningRoom(slot.room(), runningState);
        hudSyncService.syncRunningRoom(slot.room(), runningState);
        return true;
    }

    private RoomTeamId chooseRunningJoinTeam(RoomDefinition room, RunningRuntimeState state, UUID joiningPlayerId) {
        return chooseRunningJoinTeam(room, state, joiningPlayerId, 1);
    }

    private RoomTeamId chooseRunningJoinTeam(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID joiningPlayerId,
        int requiredSlots
    ) {
        if (room == null || state == null || requiredSlots <= 0) {
            return null;
        }
        RoomMatchingConfig config = room.commonConfig().matchingConfig();
        RoomTeamId bestTeam = null;
        int bestCount = Integer.MAX_VALUE;
        for (RoomTeamId teamId : config.enabledTeams()) {
            int occupied = countRunningOccupiedTeamSlots(state, joiningPlayerId, teamId);
            if (occupied + requiredSlots > config.maxPlayersForTeam(teamId)) {
                continue;
            }
            if (occupied < bestCount) {
                bestCount = occupied;
                bestTeam = teamId;
            }
        }
        return bestTeam;
    }

    private int countRunningOccupiedTeamSlots(RunningRuntimeState state, UUID joiningPlayerId, RoomTeamId teamId) {
        if (state == null || teamId == null) {
            return 0;
        }
        int count = 0;
        for (UUID memberId : state.getRoomPlayers()) {
            if (state.getRoomTeams().get(memberId) == teamId) {
                count++;
            }
        }
        for (Map.Entry<UUID, RunningRuntimeState.RejoinTicket> entry : state.getRejoinTickets().entrySet()) {
            UUID ticketPlayerId = entry.getKey();
            if (ticketPlayerId == null || ticketPlayerId.equals(joiningPlayerId) || state.getRoomPlayers().contains(ticketPlayerId)) {
                continue;
            }
            RunningRuntimeState.RejoinTicket ticket = entry.getValue();
            if (ticket != null && ticket.teamId() == teamId && ticket.matchInstanceId() == state.getActiveMatchInstanceId()) {
                count++;
            }
        }
        return count;
    }

    private int countMatchingPlayersInTeam(MatchingRuntimeState state, RoomTeamId teamId) {
        if (state == null || teamId == null) {
            return 0;
        }
        int count = 0;
        for (RoomTeamId assigned : state.getQueueTeams().values()) {
            if (assigned == teamId) {
                count++;
            }
        }
        return count;
    }

    @FunctionalInterface
    public interface PresentationSync {
        void resync(ServerPlayer player, long gameTick);
    }
}
