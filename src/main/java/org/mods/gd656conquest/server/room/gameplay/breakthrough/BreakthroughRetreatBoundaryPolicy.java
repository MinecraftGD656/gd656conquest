package org.mods.gd656conquest.server.room.gameplay.breakthrough;

import java.util.ArrayList;
import java.util.List;
import org.mods.gd656conquest.server.room.model.RoomBreakthroughConfig;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.runtime.RoomBoundaryRuleEvaluator;

/** A completed retreat excludes the captured sector even while others are still withdrawing. */
public final class BreakthroughRetreatBoundaryPolicy {
    private BreakthroughRetreatBoundaryPolicy() {}
    public static List<List<RoomPosition>> destinations(RoomBreakthroughConfig config, int retreatStage, int maxStage) {
        if (config == null) return List.of();
        List<List<RoomPosition>> result = new ArrayList<>();
        add(result, config.campBBaseBoundary());
        for (int stage = retreatStage + 1; stage <= maxStage; stage++) add(result, config.stageBoundary(stage));
        return List.copyOf(result);
    }
    public static boolean reachedDestination(double x, double z, List<List<RoomPosition>> destinations) {
        // The generic boundary evaluator allows unconfigured maps; arrival must be affirmative.
        return RoomBoundaryRuleEvaluator.hasConfiguredBoundary(destinations)
            && RoomBoundaryRuleEvaluator.isInsideAnyBoundary(x, z, destinations);
    }
    private static void add(List<List<RoomPosition>> result, List<RoomPosition> boundary) {
        if (boundary != null && boundary.size() >= 3) result.add(boundary);
    }
}
