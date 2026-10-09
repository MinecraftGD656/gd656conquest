package org.mods.gd656conquest.server.room.gameplay.breakthrough;

import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveRuntimeView;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;

import java.util.Objects;

public final class BreakthroughObjectiveRuntimeView implements RoomObjectiveRuntimeView {
    private final BreakthroughRuntimeState state;

    public BreakthroughObjectiveRuntimeView(BreakthroughRuntimeState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public int currentStageIndex() {
        return state.getCurrentStageIndex();
    }

    public int maxStageCount() {
        return state.getMaxStageCount();
    }

    @Override
    public RoomTeamId ownerOf(String objectiveCode) {
        return state.getOwnerByCode().get(RoomObjectiveSupport.normalizeCode(objectiveCode));
    }

    @Override
    public RoomTeamId drivingTeamOf(String objectiveCode) {
        return state.getDrivingTeamByCode().get(RoomObjectiveSupport.normalizeCode(objectiveCode));
    }

    @Override
    public double progressOf(String objectiveCode) {
        return state.getProgressByCode().getOrDefault(RoomObjectiveSupport.normalizeCode(objectiveCode), 0.0D);
    }

    @Override
    public int teamCount(String objectiveCode, RoomTeamId teamId) {
        String normalizedCode = RoomObjectiveSupport.normalizeCode(objectiveCode);
        if (teamId == RoomTeamId.CAMP_A) {
            return state.getCampACountByCode().getOrDefault(normalizedCode, 0);
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return state.getCampBCountByCode().getOrDefault(normalizedCode, 0);
        }
        return 0;
    }

    @Override
    public boolean isFullyControlledBy(String objectiveCode, RoomTeamId teamId) {
        return state.isFullyControlledBy(RoomObjectiveSupport.normalizeCode(objectiveCode), teamId);
    }
}
