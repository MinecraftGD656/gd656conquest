package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.model.RoomPosition;

import java.util.List;

public final class RoomBoundaryRuleEvaluator {
    public record BoundaryCheckResult(boolean configuredBoundary, boolean insideBoundary) {
        public boolean isOutsideBoundary() {
            return configuredBoundary && !insideBoundary;
        }
    }

    private RoomBoundaryRuleEvaluator() {
    }

    public static boolean hasConfiguredBoundary(List<List<RoomPosition>> boundaries) {
        if (boundaries == null || boundaries.isEmpty()) {
            return false;
        }
        for (List<RoomPosition> boundary : boundaries) {
            if (boundary != null && boundary.size() >= 3) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInsideAnyBoundary(ServerPlayer player, List<List<RoomPosition>> boundaries) {
        return evaluateMembership(player, boundaries).insideBoundary();
    }

    public static boolean isInsideAnyBoundary(double x, double z, List<List<RoomPosition>> boundaries) {
        return evaluateMembership(x, z, boundaries).insideBoundary();
    }

    public static boolean isOutsideAllBoundaries(ServerPlayer player, List<List<RoomPosition>> boundaries) {
        return evaluateMembership(player, boundaries).isOutsideBoundary();
    }

    public static boolean isOutsideAllBoundaries(double x, double z, List<List<RoomPosition>> boundaries) {
        return evaluateMembership(x, z, boundaries).isOutsideBoundary();
    }

    public static BoundaryCheckResult evaluateMembership(ServerPlayer player, List<List<RoomPosition>> boundaries) {
        if (player == null) {
            return new BoundaryCheckResult(false, true);
        }
        return evaluateMembership(player.getX(), player.getZ(), boundaries);
    }

    public static BoundaryCheckResult evaluateMembership(double x, double z, List<List<RoomPosition>> boundaries) {
        boolean configuredBoundary = false;
        if (boundaries == null || boundaries.isEmpty()) {
            return new BoundaryCheckResult(false, true);
        }
        for (List<RoomPosition> boundary : boundaries) {
            if (boundary == null || boundary.size() < 3) {
                continue;
            }
            configuredBoundary = true;
            if (RoomBoundaryPolygonSupport.isPointInsideBoundary(x, z, boundary)) {
                return new BoundaryCheckResult(true, true);
            }
        }
        return new BoundaryCheckResult(configuredBoundary, !configuredBoundary);
    }
}
