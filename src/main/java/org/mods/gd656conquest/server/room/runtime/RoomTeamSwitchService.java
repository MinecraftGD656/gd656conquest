package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.Objects;
import java.util.UUID;

public final class RoomTeamSwitchService {
    private final RoomDeploySelectionService deploySelectionService;
    private final RoomSquadAssignmentService squadAssignmentService;
    private final RoomNametagVisibilityService nametagVisibilityService;
    private final RoomHudSyncService hudSyncService;

    public RoomTeamSwitchService(
        RoomDeploySelectionService deploySelectionService,
        RoomSquadAssignmentService squadAssignmentService,
        RoomNametagVisibilityService nametagVisibilityService,
        RoomHudSyncService hudSyncService
    ) {
        this.deploySelectionService = Objects.requireNonNull(deploySelectionService, "deploySelectionService");
        this.squadAssignmentService = Objects.requireNonNull(squadAssignmentService, "squadAssignmentService");
        this.nametagVisibilityService = Objects.requireNonNull(nametagVisibilityService, "nametagVisibilityService");
        this.hudSyncService = Objects.requireNonNull(hudSyncService, "hudSyncService");
    }

    public RoomTeamId resolveOppositePlayableTeam(RoomTeamId currentTeam) {
        if (currentTeam == RoomTeamId.CAMP_A) {
            return RoomTeamId.CAMP_B;
        }
        if (currentTeam == RoomTeamId.CAMP_B) {
            return RoomTeamId.CAMP_A;
        }
        return null;
    }

    public TeamSwitchResult applyRequestedTeam(
        RoomDefinition room,
        RoomRuntimeState runtimeState,
        UUID playerId,
        RoomTeamId targetTeam
    ) {
        if (room == null || runtimeState == null || playerId == null) {
            return TeamSwitchResult.invalid();
        }
        RunningRuntimeState state = runtimeState.getRunningState();
        RoomTeamId currentTeam = state.getRoomTeams().get(playerId);
        if (currentTeam != RoomTeamId.CAMP_A && currentTeam != RoomTeamId.CAMP_B) {
            return TeamSwitchResult.invalid();
        }
        if (targetTeam != RoomTeamId.CAMP_A && targetTeam != RoomTeamId.CAMP_B) {
            return TeamSwitchResult.invalid();
        }
        if (currentTeam == targetTeam) {
            clearSwitchRequests(state, playerId);
            return TeamSwitchResult.noop(currentTeam);
        }
        clearSwitchRequests(state, playerId);
        state.getRoomTeams().put(playerId, targetTeam);
        clearTeamSensitiveRuntimeState(state, playerId);
        deploySelectionService.replaceSelectionWithTeamFallback(state, playerId, targetTeam);
        runtimeState.getSquadLabelsByPlayer().remove(playerId);
        state.getSquadLabelsByPlayer().remove(playerId);
        squadAssignmentService.assignRunningPlayer(runtimeState, playerId, targetTeam);
        nametagVisibilityService.syncRunningRoom(room, state);
        hudSyncService.syncRunningRoom(room, state);
        return TeamSwitchResult.changed(currentTeam, targetTeam);
    }

    public TeamSwitchResult applyPendingTeamChangeAfterDeath(
        RoomDefinition room,
        RoomRuntimeState runtimeState,
        UUID playerId
    ) {
        if (runtimeState == null || playerId == null) {
            return TeamSwitchResult.invalid();
        }
        RoomTeamId pendingTeam = runtimeState.getRunningState().getPendingTeamChanges().get(playerId);
        if (pendingTeam != RoomTeamId.CAMP_A && pendingTeam != RoomTeamId.CAMP_B) {
            return TeamSwitchResult.invalid();
        }
        return applyRequestedTeam(room, runtimeState, playerId, pendingTeam);
    }

    private void clearSwitchRequests(RunningRuntimeState state, UUID playerId) {
        state.getPendingTeamChanges().remove(playerId);
        state.getAutoTeamSwitchOfferTargetTeams().remove(playerId);
        state.getAutoTeamSwitchOfferExpireTicks().remove(playerId);
    }

    private void clearTeamSensitiveRuntimeState(RunningRuntimeState state, UUID playerId) {
        state.getTeammateDeployKillWindowUntilTicks().remove(playerId);
        state.getBeaconOwnerByDeployer().remove(playerId);
        state.getBeaconOwnerKillWindowUntilTicksByDeployer().remove(playerId);
        state.getBeaconOwnerByDeployer().entrySet().removeIf(entry -> playerId.equals(entry.getValue()));
        state.getOutsideBoundarySinceTicks().remove(playerId);
        state.getBoundaryPenaltyLastShownSeconds().remove(playerId);
        state.getBoundaryPenaltyVisualApplied().remove(playerId);
        state.getCombatUntilTicks().remove(playerId);
    }

    public record TeamSwitchResult(boolean changed, boolean valid, RoomTeamId previousTeam, RoomTeamId currentTeam) {
        public static TeamSwitchResult changed(RoomTeamId previousTeam, RoomTeamId currentTeam) {
            return new TeamSwitchResult(true, true, previousTeam, currentTeam);
        }

        public static TeamSwitchResult noop(RoomTeamId team) {
            return new TeamSwitchResult(false, true, team, team);
        }

        public static TeamSwitchResult invalid() {
            return new TeamSwitchResult(false, false, null, null);
        }
    }
}
