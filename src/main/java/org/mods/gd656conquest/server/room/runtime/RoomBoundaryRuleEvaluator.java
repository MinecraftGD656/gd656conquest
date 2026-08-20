package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayBoundaryPolicyProvider;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

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

    /**
     * 统一判定: 玩家是否处于允许边界(战斗区域)之外。
     * 玩法运行时实现了 {@link RoomGameplayBoundaryPolicyProvider} 时用其判定, 否则(据点玩法等)
     * 回退到房间 conquestConfig 的阵营/共享边界。无边界配置或无法判定时返回 false(视为在区域内)。
     * 与"请返回战斗区域"惩罚判定保持一致。
     */
    public static boolean isOutsideAllowedBoundary(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId, ServerPlayer player) {
        if (room == null || state == null || teamId == null || player == null) {
            return false;
        }
        if (teamId != RoomTeamId.CAMP_A && teamId != RoomTeamId.CAMP_B) {
            return false;
        }
        RoomGameplayBoundaryPolicyProvider policy = RoomGameplayBoundaryPolicyProvider.resolve(state);
        if (policy != null) {
            return policy.isOutsideAllowedBoundary(room, state, player, teamId);
        }
        var conquestConfig = room.conquestConfig();
        if (conquestConfig == null) {
            return false;
        }
        return isOutsideAllBoundaries(player, conquestConfig.allowedBoundariesForTeam(teamId));
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
