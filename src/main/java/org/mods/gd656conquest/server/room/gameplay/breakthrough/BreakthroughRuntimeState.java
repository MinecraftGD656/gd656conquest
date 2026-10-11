package org.mods.gd656conquest.server.room.gameplay.breakthrough;

import org.mods.gd656conquest.server.room.model.RoomBreakthroughConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveSupport;
import org.mods.gd656conquest.common.match.OperationsTroopRules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BreakthroughRuntimeState {
    public enum Phase {
        COMBAT,
        RETREAT,
        ADVANCE_COUNTDOWN
    }

    public enum DesperateMode {
        NONE,
        ZERO_TROOP_CONTEST
    }

    public enum CombatOutcome {
        IN_PROGRESS, CAPTURED, ZERO_TROOP_CONTEST, EXHAUSTED, TIMED_OUT
    }

    private final Map<String, Double> progressByCode = new LinkedHashMap<>();
    private final Map<String, RoomTeamId> ownerByCode = new LinkedHashMap<>();
    private final Map<String, RoomTeamId> drivingTeamByCode = new LinkedHashMap<>();
    private final Map<String, Integer> campACountByCode = new LinkedHashMap<>();
    private final Map<String, Integer> campBCountByCode = new LinkedHashMap<>();
    private final Map<String, Integer> stageIndexByCode = new LinkedHashMap<>();
    private final Map<RoomTeamId, String> lastTeamEventTextByTeam = new LinkedHashMap<>();
    private final Map<RoomTeamId, Long> lastTeamEventExpireTickByTeam = new LinkedHashMap<>();
    private final Map<UUID, Integer> advanceCountdownLastShownSecondsByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Integer> retreatCountdownLastShownSecondsByPlayer = new LinkedHashMap<>();
    private int currentStageIndex;
    private int maxStageCount;
    private int attackTroops;
    private int attackTroopCap;
    private long stageStartTick;
    private long stageDeadlineTick;
    private Phase phase = Phase.COMBAT;
    private int retreatStageIndex;
    private long retreatStartTick = -1L;
    private long retreatDeadlineTick = -1L;
    private long advanceCountdownStartTick = -1L;
    private long advanceCountdownEndTick = -1L;
    private long lastStageRewardTick = -1L;
    private int lastStageRewardAmount;
    private int lastAdvancedStageIndex;
    private DesperateMode desperateMode = DesperateMode.NONE;

    public void initialize(RoomBreakthroughConfig config, long gameTick) {
        progressByCode.clear();
        ownerByCode.clear();
        drivingTeamByCode.clear();
        campACountByCode.clear();
        campBCountByCode.clear();
        stageIndexByCode.clear();
        lastTeamEventTextByTeam.clear();
        lastTeamEventExpireTickByTeam.clear();
        advanceCountdownLastShownSecondsByPlayer.clear();
        retreatCountdownLastShownSecondsByPlayer.clear();
        currentStageIndex = config == null || config.stageCount() <= 0 ? 0 : 1;
        maxStageCount = config == null ? 0 : config.stageCount();
        attackTroops = 0;
        attackTroopCap = 0;
        stageStartTick = gameTick;
        stageDeadlineTick = config == null || config.stageDurationMinutes() <= 0 ? Long.MAX_VALUE
            : gameTick + (long) config.stageDurationMinutes() * 60L * 20L;
        phase = Phase.COMBAT;
        retreatStageIndex = 0;
        retreatStartTick = -1L;
        retreatDeadlineTick = -1L;
        advanceCountdownStartTick = -1L;
        advanceCountdownEndTick = -1L;
        lastStageRewardTick = -1L;
        lastStageRewardAmount = 0;
        lastAdvancedStageIndex = 0;
        desperateMode = DesperateMode.NONE;
        synchronizeWithConfig(config);
    }

    public void synchronizeWithConfig(RoomBreakthroughConfig config) {
        progressByCode.clear();
        ownerByCode.clear();
        drivingTeamByCode.clear();
        campACountByCode.clear();
        campBCountByCode.clear();
        stageIndexByCode.clear();
        if (config == null || currentStageIndex <= 0) {
            return;
        }
        List<RoomCapturePointDefinition> activePoints = config.allActivePoints();
        for (RoomCapturePointDefinition point : activePoints) {
            String code = RoomObjectiveSupport.normalizeCode(point.code());
            int pointStageIndex = Math.max(1, point.stageIndex());
            boolean capturedByAttackers = pointStageIndex < currentStageIndex;
            stageIndexByCode.put(code, pointStageIndex);
            progressByCode.put(code, capturedByAttackers ? 100.0D : 0.0D);
            ownerByCode.put(code, capturedByAttackers ? RoomTeamId.CAMP_A : RoomTeamId.CAMP_B);
            drivingTeamByCode.put(code, null);
            campACountByCode.put(code, 0);
            campBCountByCode.put(code, 0);
        }
    }

    public Map<String, Double> getProgressByCode() {
        return progressByCode;
    }

    public Map<String, RoomTeamId> getOwnerByCode() {
        return ownerByCode;
    }

    public Map<String, RoomTeamId> getDrivingTeamByCode() {
        return drivingTeamByCode;
    }

    public Map<String, Integer> getCampACountByCode() {
        return campACountByCode;
    }

    public Map<String, Integer> getCampBCountByCode() {
        return campBCountByCode;
    }

    public Map<String, Integer> getStageIndexByCode() {
        return stageIndexByCode;
    }

    public int getCurrentStageIndex() {
        return currentStageIndex;
    }

    public int getMaxStageCount() {
        return maxStageCount;
    }

    public int getAttackTroops() {
        return attackTroops;
    }

    public int getAttackTroopCap() {
        return attackTroopCap;
    }

    public long getStageStartTick() {
        return stageStartTick;
    }

    public long getStageDeadlineTick() {
        return stageDeadlineTick;
    }

    public Phase getPhase() {
        return phase;
    }

    public int getRetreatStageIndex() {
        return retreatStageIndex;
    }

    public long getRetreatDeadlineTick() {
        return retreatDeadlineTick;
    }

    public long getAdvanceCountdownEndTick() {
        return advanceCountdownEndTick;
    }

    public Map<UUID, Integer> getAdvanceCountdownLastShownSecondsByPlayer() {
        return advanceCountdownLastShownSecondsByPlayer;
    }

    public Map<UUID, Integer> getRetreatCountdownLastShownSecondsByPlayer() {
        return retreatCountdownLastShownSecondsByPlayer;
    }

    public long getLastStageRewardTick() {
        return lastStageRewardTick;
    }

    public int getLastStageRewardAmount() {
        return lastStageRewardAmount;
    }

    public int getLastAdvancedStageIndex() {
        return lastAdvancedStageIndex;
    }

    public DesperateMode getDesperateMode() {
        return desperateMode;
    }

    public void consumeAttackTroop(int amount) {
        attackTroops = Math.max(0, attackTroops - Math.max(0, amount));
    }

    public void initializeAttackTroops(int cap) {
        attackTroopCap = Math.max(0, cap);
        attackTroops = attackTroopCap;
    }

    public void setAttackTroopCap(int cap) {
        attackTroopCap = Math.max(0, cap);
    }

    public int rewardSectorCapture(int cap, int fixedReward) {
        setAttackTroopCap(cap);
        int previous = attackTroops;
        attackTroops = OperationsTroopRules.afterSectorCapture(attackTroops, attackTroopCap, fixedReward);
        return attackTroops - previous;
    }

    public int addAttackTroops(int amount) {
        int previous = attackTroops;
        attackTroops = OperationsTroopRules.add(attackTroops, attackTroopCap, amount);
        return attackTroops - previous;
    }

    public void startRetreat(long gameTick, int retreatSeconds) {
        phase = Phase.RETREAT;
        retreatStageIndex = currentStageIndex;
        retreatStartTick = gameTick;
        retreatDeadlineTick = gameTick + Math.max(0L, (long) retreatSeconds * 20L);
        advanceCountdownStartTick = -1L;
        advanceCountdownEndTick = -1L;
        advanceCountdownLastShownSecondsByPlayer.clear();
        retreatCountdownLastShownSecondsByPlayer.clear();
    }

    public void startAdvanceCountdown(long gameTick, int countdownSeconds) {
        phase = Phase.ADVANCE_COUNTDOWN;
        advanceCountdownStartTick = gameTick;
        advanceCountdownEndTick = gameTick + Math.max(0L, (long) countdownSeconds * 20L);
        advanceCountdownLastShownSecondsByPlayer.clear();
        retreatCountdownLastShownSecondsByPlayer.clear();
    }

    public void advanceToNextStage(RoomBreakthroughConfig config, long gameTick) {
        currentStageIndex = Math.min(maxStageCount, currentStageIndex + 1);
        stageStartTick = gameTick;
        stageDeadlineTick = config == null || config.stageDurationMinutes() <= 0 ? Long.MAX_VALUE
            : gameTick + (long) config.stageDurationMinutes() * 60L * 20L;
        phase = Phase.COMBAT;
        retreatStageIndex = 0;
        retreatStartTick = -1L;
        retreatDeadlineTick = -1L;
        advanceCountdownStartTick = -1L;
        advanceCountdownEndTick = -1L;
        advanceCountdownLastShownSecondsByPlayer.clear();
        retreatCountdownLastShownSecondsByPlayer.clear();
        desperateMode = DesperateMode.NONE;
        synchronizeWithConfig(config);
    }

    public void restartCurrentStage(RoomBreakthroughConfig config, long combatStartTick) {
        phase = Phase.COMBAT;
        stageStartTick = combatStartTick;
        stageDeadlineTick = config == null || config.stageDurationMinutes() <= 0 ? Long.MAX_VALUE
            : combatStartTick + (long) config.stageDurationMinutes() * 60 * 20;
        retreatStageIndex = 0;
        retreatStartTick = retreatDeadlineTick = advanceCountdownStartTick = advanceCountdownEndTick = -1;
        clearDesperateMode();
        advanceCountdownLastShownSecondsByPlayer.clear();
        retreatCountdownLastShownSecondsByPlayer.clear();
        lastStageRewardTick = -1;
        lastStageRewardAmount = 0;
        synchronizeWithConfig(config);
    }

    public void markStageReward(long gameTick, int amount, int advancedStageIndex) {
        lastStageRewardTick = gameTick;
        lastStageRewardAmount = Math.max(0, amount);
        lastAdvancedStageIndex = Math.max(0, advancedStageIndex);
    }

    public void pushTeamEvent(RoomTeamId teamId, String text, long expireTick) {
        if (teamId == null || text == null || text.isBlank()) {
            return;
        }
        lastTeamEventTextByTeam.put(teamId, text);
        lastTeamEventExpireTickByTeam.put(teamId, expireTick);
    }

    public String resolveTeamEvent(RoomTeamId teamId, long gameTick) {
        if (teamId == null) {
            return "-";
        }
        long expireTick = lastTeamEventExpireTickByTeam.getOrDefault(teamId, -1L);
        if (expireTick < 0L || gameTick > expireTick) {
            return "-";
        }
        return lastTeamEventTextByTeam.getOrDefault(teamId, "-");
    }

    public boolean areAllActiveObjectivesFullyCapturedByAttackers(RoomBreakthroughConfig config) {
        if (config == null || currentStageIndex <= 0) {
            return false;
        }
        List<RoomCapturePointDefinition> activePoints = config.stageActivePoints(currentStageIndex);
        if (activePoints.isEmpty()) {
            return false;
        }
        for (RoomCapturePointDefinition point : activePoints) {
            String code = RoomObjectiveSupport.normalizeCode(point.code());
            if (ownerByCode.get(code) != RoomTeamId.CAMP_A) {
                return false;
            }
            if (progressByCode.getOrDefault(code, 0.0D) < 99.999D) {
                return false;
            }
        }
        return true;
    }

    public void startZeroTroopContest() {
        desperateMode = DesperateMode.ZERO_TROOP_CONTEST;
    }

    public void clearDesperateMode() {
        desperateMode = DesperateMode.NONE;
    }

    public boolean isDesperateModeActive() {
        return desperateMode != DesperateMode.NONE;
    }

    /** Captured attacker points may remain. Every other point must still be contested or partially captured. */
    public boolean canContinueZeroTroopContest(RoomBreakthroughConfig config) {
        if (config == null || currentStageIndex <= 0) return false;
        List<RoomCapturePointDefinition> activePoints = config.stageActivePoints(currentStageIndex);
        if (activePoints.isEmpty()) return false;
        for (RoomCapturePointDefinition point : activePoints) {
            String code = RoomObjectiveSupport.normalizeCode(point.code());
            boolean contested = campACountByCode.getOrDefault(code, 0) > 0
                && campBCountByCode.getOrDefault(code, 0) > 0;
            if (isFullyControlledBy(code, RoomTeamId.CAMP_B) && !contested) return false;
        }
        return true;
    }

    public CombatOutcome resolveCombatOutcome(RoomBreakthroughConfig config, long gameTick) {
        if (areAllActiveObjectivesFullyCapturedByAttackers(config)) return CombatOutcome.CAPTURED;
        if (attackTroops <= 0) return canContinueZeroTroopContest(config)
            ? CombatOutcome.ZERO_TROOP_CONTEST : CombatOutcome.EXHAUSTED;
        return stageDeadlineTick != Long.MAX_VALUE && gameTick >= stageDeadlineTick
            ? CombatOutcome.TIMED_OUT : CombatOutcome.IN_PROGRESS;
    }

    public boolean isLastStageFinalFightPressure() {
        String code = resolveSingleRemainingObjectiveCode();
        if (code == null || currentStageIndex != maxStageCount) {
            return false;
        }
        return ownerByCode.get(code) != RoomTeamId.CAMP_A
            && progressByCode.getOrDefault(code, 0.0D) > 25.0D;
    }

    public boolean shouldPlayLastObjectiveLoop() {
        String code = resolveSingleRemainingObjectiveCode();
        if (code == null) {
            return false;
        }
        RoomTeamId owner = ownerByCode.get(code);
        return currentStageIndex == maxStageCount ? owner != RoomTeamId.CAMP_A && owner == null : owner == null;
    }

    private String resolveSingleRemainingObjectiveCode() {
        String remainingCode = null;
        for (Map.Entry<String, RoomTeamId> entry : ownerByCode.entrySet()) {
            if (stageIndexByCode.getOrDefault(entry.getKey(), 0) != currentStageIndex) {
                continue;
            }
            if (entry.getValue() == RoomTeamId.CAMP_A) {
                continue;
            }
            if (remainingCode != null) {
                return null;
            }
            remainingCode = entry.getKey();
        }
        return remainingCode;
    }

    public boolean isFullyControlledBy(String code, RoomTeamId teamId) {
        double progress = progressByCode.getOrDefault(code, 0.0D);
        if (teamId == RoomTeamId.CAMP_A) {
            return progress >= 99.999D;
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return progress <= 0.001D;
        }
        return false;
    }
}
