package org.mods.gd656conquest.server.facility.support;

import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;

import java.util.UUID;

public final class SupportScoreService {
    private SupportScoreService() {
    }

    public static int getSpentScore(RunningRuntimeState runningState, UUID playerId) {
        if (runningState == null || playerId == null) {
            return 0;
        }
        return Math.max(0, runningState.getSupportSpentScoreByPlayer().getOrDefault(playerId, 0));
    }

    public static int getCurrentScore(RunningRuntimeState runningState, UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        KilliconScoreboardBridge.TempSnapshot snapshot = KilliconScoreboardBridge.getPlayerSnapshot(runningState, playerId);
        return Math.max(0, snapshot.score());
    }

    public static int getRemainingScore(RunningRuntimeState runningState, UUID playerId) {
        int currentScore = getCurrentScore(runningState, playerId);
        int spentScore = getSpentScore(runningState, playerId);
        return Math.max(0, currentScore - spentScore);
    }

    public static boolean canConsumeScore(RunningRuntimeState runningState, UUID playerId, int cost) {
        if (cost < 0 || playerId == null || runningState == null || !runningState.getRoomPlayers().contains(playerId)) {
            return false;
        }
        return getRemainingScore(runningState, playerId) >= cost;
    }

    public static ConsumptionResult tryConsumeScore(RunningRuntimeState runningState, UUID playerId, int cost) {
        if (cost < 0) {
            return new ConsumptionResult(false, getSpentScore(runningState, playerId), getRemainingScore(runningState, playerId), cost);
        }
        if (cost == 0) {
            return new ConsumptionResult(true, getSpentScore(runningState, playerId), getRemainingScore(runningState, playerId), 0);
        }
        if (!canConsumeScore(runningState, playerId, cost)) {
            return new ConsumptionResult(false, getSpentScore(runningState, playerId), getRemainingScore(runningState, playerId), cost);
        }
        int updatedSpent = Math.max(0, getSpentScore(runningState, playerId) + cost);
        runningState.getSupportSpentScoreByPlayer().put(playerId, updatedSpent);
        return new ConsumptionResult(true, updatedSpent, getRemainingScore(runningState, playerId), cost);
    }

    public static int setSpentScore(RunningRuntimeState runningState, UUID playerId, int spentScore) {
        if (runningState == null || playerId == null) {
            return 0;
        }
        int normalized = Math.max(0, spentScore);
        if (normalized <= 0) {
            runningState.getSupportSpentScoreByPlayer().remove(playerId);
            return 0;
        }
        runningState.getSupportSpentScoreByPlayer().put(playerId, normalized);
        return normalized;
    }

    public static void clearPlayer(RunningRuntimeState runningState, UUID playerId) {
        if (runningState == null || playerId == null) {
            return;
        }
        runningState.getSupportSpentScoreByPlayer().remove(playerId);
    }

    public record ConsumptionResult(
        boolean success,
        int spentScore,
        int remainingScore,
        int requestedCost
    ) {
    }
}
