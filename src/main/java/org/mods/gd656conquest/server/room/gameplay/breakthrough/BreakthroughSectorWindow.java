package org.mods.gd656conquest.server.room.gameplay.breakthrough;

import java.util.ArrayList;
import java.util.List;
import org.mods.gd656conquest.server.room.model.*;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryRuleEvaluator;

/** Bases are the neighbours before the first and after the last combat sector. */
public final class BreakthroughSectorWindow {
    private BreakthroughSectorWindow() {}

    public static List<List<RoomPosition>> boundaries(RoomBreakthroughConfig config, int stage, int maxStage, RoomTeamId team) {
        if (config == null || stage < 1 || stage > maxStage || team == null) return List.of();
        List<List<RoomPosition>> result = new ArrayList<>();
        add(result, config.stageBoundary(stage));
        if (team == RoomTeamId.CAMP_A) {
            add(result, stage == 1 ? config.campABaseBoundary() : config.stageBoundary(stage - 1));
        } else if (team == RoomTeamId.CAMP_B) {
            add(result, stage == maxStage ? config.campBBaseBoundary() : config.stageBoundary(stage + 1));
        }
        return List.copyOf(result);
    }

    public static boolean contains(double x, double z, List<List<RoomPosition>> boundaries) {
        // Missing polygons do not authorize any extra space.
        return RoomBoundaryRuleEvaluator.hasConfiguredBoundary(boundaries)
            && RoomBoundaryRuleEvaluator.isInsideAnyBoundary(x, z, boundaries);
    }

    public static List<List<RoomPosition>> retreatBoundaries(RoomBreakthroughConfig config, int retreatStage, int maxStage, boolean pending) {
        List<List<RoomPosition>> result = new ArrayList<>(boundaries(config, retreatStage + 1, maxStage, RoomTeamId.CAMP_B));
        if (pending && config != null) add(result, config.stageBoundary(retreatStage));
        return List.copyOf(result);
    }

    private static void add(List<List<RoomPosition>> result, List<RoomPosition> polygon) {
        if (polygon != null && polygon.size() >= 3) result.add(polygon);
    }
}
