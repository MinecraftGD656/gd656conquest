package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomTroopBalanceReference;

import java.util.UUID;

public final class RoomAttackTroopBalanceSupport {
    private static final int ATTACK_TROOP_REFERENCE_PLAYERS = 12;
    private RoomAttackTroopBalanceSupport() {
    }

    public static AttackTroopBalanceSnapshot resolve(
        RunningRuntimeState runningState,
        RoomTroopBalanceReference reference,
        int configuredTroopCap
    ) {
        return resolve(runningState, reference, configuredTroopCap, 0.5D);
    }

    public static AttackTroopBalanceSnapshot resolve(
        RunningRuntimeState runningState,
        RoomTroopBalanceReference reference,
        int configuredTroopCap,
        double stageRestoreRatio
    ) {
        int referencePlayerCount = resolveReferencePlayerCount(runningState, reference);
        int troopCap = resolveCeilScaledValue(configuredTroopCap, referencePlayerCount, ATTACK_TROOP_REFERENCE_PLAYERS);
        int stageReward = resolveStageRestoreReward(troopCap, stageRestoreRatio);
        return new AttackTroopBalanceSnapshot(referencePlayerCount, troopCap, stageReward);
    }

    private static int resolveReferencePlayerCount(RunningRuntimeState runningState, RoomTroopBalanceReference reference) {
        if (reference == RoomTroopBalanceReference.DEFENDER) {
            return countPlayersInTeam(runningState, RoomTeamId.CAMP_B);
        }
        return countPlayersInTeam(runningState, RoomTeamId.CAMP_A);
    }

    private static int countPlayersInTeam(RunningRuntimeState runningState, RoomTeamId teamId) {
        if (runningState == null || teamId == null) {
            return 0;
        }
        int count = 0;
        for (UUID playerId : runningState.getRoomPlayers()) {
            if (runningState.getRoomTeams().get(playerId) == teamId) {
                count++;
            }
        }
        return count;
    }

    private static int resolveCeilScaledValue(int baseValue, int playerCount, int divisor) {
        if (baseValue <= 0 || playerCount <= 0 || divisor <= 0) {
            return 0;
        }
        return (int) Math.ceil(baseValue * playerCount / (double) divisor);
    }

    private static int resolveStageRestoreReward(int troopCap, double stageRestoreRatio) {
        if (troopCap <= 0 || !Double.isFinite(stageRestoreRatio) || stageRestoreRatio <= 0.0D) {
            return 0;
        }
        return (int) Math.ceil(troopCap * stageRestoreRatio);
    }

    public record AttackTroopBalanceSnapshot(int referencePlayerCount, int troopCap, int stageReward) {
    }
}
