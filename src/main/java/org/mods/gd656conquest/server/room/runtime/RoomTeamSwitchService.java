package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomTeamPresentationSupport;
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

    public TeamSwitchEligibility evaluateSelfSwitchEligibility(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        boolean operatorAdmin
    ) {
        if (state == null || playerId == null) {
            return TeamSwitchEligibility.invalid("当前不在可切换队伍");
        }
        RoomTeamId currentTeam = state.getRoomTeams().get(playerId);
        RoomTeamId targetTeam = resolveOppositePlayableTeam(currentTeam);
        if (targetTeam == null) {
            return TeamSwitchEligibility.invalid("当前不在可切换队伍");
        }
        int currentCount = countPlayersInTeam(state, currentTeam);
        int targetCount = countPlayersInTeam(state, targetTeam);
        String targetDisplayName = RoomTeamPresentationSupport.resolveTeamDisplayName(room, state, targetTeam);
        if (operatorAdmin) {
            return TeamSwitchEligibility.allowed(currentTeam, targetTeam, currentCount, targetCount, targetDisplayName);
        }
        if (room != null && !room.commonConfig().allowPlayerCampSwitch()) {
            return TeamSwitchEligibility.denied(
                currentTeam,
                targetTeam,
                currentCount,
                targetCount,
                targetDisplayName,
                "当前房间已关闭玩家主动切换阵营"
            );
        }
        if (room != null && org.mods.gd656conquest.server.room.model.RoomGameplayId.GUNMASTER.equals(room.gameplayId()) && room.gunmasterConfig().isSoloGrouping()) {
            return TeamSwitchEligibility.denied(
                currentTeam,
                targetTeam,
                currentCount,
                targetCount,
                targetDisplayName,
                "枪神个人战模式不支持切换阵营"
            );
        }
        boolean allowed = targetCount < currentCount && currentCount - targetCount > 1;
        if (!allowed) {
            return TeamSwitchEligibility.denied(
                currentTeam,
                targetTeam,
                currentCount,
                targetCount,
                targetDisplayName,
                "当前仅允许切换到人数更少且差距至少为 2 的敌方阵营"
            );
        }
        return TeamSwitchEligibility.allowed(currentTeam, targetTeam, currentCount, targetCount, targetDisplayName);
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
        state.clearTeamSensitiveStateForTeamChange(playerId);
    }

    private int countPlayersInTeam(RunningRuntimeState state, RoomTeamId teamId) {
        if (state == null || teamId == null) {
            return 0;
        }
        int count = 0;
        for (UUID playerId : state.getRoomPlayers()) {
            if (state.getRoomTeams().get(playerId) == teamId) {
                count++;
            }
        }
        return count;
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

    public record TeamSwitchEligibility(
        boolean valid,
        boolean allowed,
        RoomTeamId currentTeam,
        RoomTeamId targetTeam,
        int currentTeamCount,
        int targetTeamCount,
        String targetDisplayName,
        String deniedMessage
    ) {
        public static TeamSwitchEligibility invalid(String deniedMessage) {
            return new TeamSwitchEligibility(false, false, null, null, 0, 0, "", deniedMessage);
        }

        public static TeamSwitchEligibility denied(
            RoomTeamId currentTeam,
            RoomTeamId targetTeam,
            int currentTeamCount,
            int targetTeamCount,
            String targetDisplayName,
            String deniedMessage
        ) {
            return new TeamSwitchEligibility(
                true,
                false,
                currentTeam,
                targetTeam,
                currentTeamCount,
                targetTeamCount,
                targetDisplayName == null ? "" : targetDisplayName,
                deniedMessage
            );
        }

        public static TeamSwitchEligibility allowed(
            RoomTeamId currentTeam,
            RoomTeamId targetTeam,
            int currentTeamCount,
            int targetTeamCount,
            String targetDisplayName
        ) {
            return new TeamSwitchEligibility(
                true,
                true,
                currentTeam,
                targetTeam,
                currentTeamCount,
                targetTeamCount,
                targetDisplayName == null ? "" : targetDisplayName,
                ""
            );
        }
    }
}
