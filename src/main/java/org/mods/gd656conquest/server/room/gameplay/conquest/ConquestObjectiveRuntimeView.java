package org.mods.gd656conquest.server.room.gameplay.conquest;

import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeView;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;

import java.util.Objects;

public final class ConquestObjectiveRuntimeView implements RoomObjectiveRuntimeView {
    private final ConquestCaptureRuntimeState captureState;

    public ConquestObjectiveRuntimeView(ConquestCaptureRuntimeState captureState) {
        this.captureState = Objects.requireNonNull(captureState, "captureState");
    }

    @Override
    public RoomTeamId ownerOf(String objectiveCode) {
        return captureState.getOwnerByCode().get(RoomObjectiveSupport.normalizeCode(objectiveCode));
    }

    @Override
    public RoomTeamId drivingTeamOf(String objectiveCode) {
        return captureState.getDrivingTeamByCode().get(RoomObjectiveSupport.normalizeCode(objectiveCode));
    }

    @Override
    public double progressOf(String objectiveCode) {
        return captureState.getProgressByCode().getOrDefault(RoomObjectiveSupport.normalizeCode(objectiveCode), 50.0D);
    }

    @Override
    public int teamCount(String objectiveCode, RoomTeamId teamId) {
        String normalizedCode = RoomObjectiveSupport.normalizeCode(objectiveCode);
        if (teamId == RoomTeamId.CAMP_A) {
            return captureState.getCampACountByCode().getOrDefault(normalizedCode, 0);
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return captureState.getCampBCountByCode().getOrDefault(normalizedCode, 0);
        }
        return 0;
    }

    @Override
    public boolean isFullyControlledBy(String objectiveCode, RoomTeamId teamId) {
        return captureState.isFullyControlledBy(RoomObjectiveSupport.normalizeCode(objectiveCode), teamId);
    }
}
