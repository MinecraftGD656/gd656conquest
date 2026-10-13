package org.mods.gd656conquest.common.audio;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Authoritative hysteresis; timers describe objective control, independently of music transitions. */
public final class OperationsFinaleState {
    public record Objective(String id, boolean attackerOwned, boolean neutral, boolean defenderFull, boolean disturbed) {}
    private final Map<String, Long> defenderSince = new HashMap<>();
    private long allDefenderSince = -1;
    private boolean troopReason, objectiveReason;
    private int level;

    public int update(long tick, int troops, boolean lastBattalion, boolean lastSector,
                      boolean zeroContest, List<Objective> objectives) {
        var ids = new HashSet<String>();
        for (var objective : objectives) {
            ids.add(objective.id());
            if (objective.defenderFull()) defenderSince.putIfAbsent(objective.id(), tick);
            else defenderSince.remove(objective.id());
        }
        defenderSince.keySet().retainAll(ids);
        boolean allFull = !objectives.isEmpty() && objectives.stream().allMatch(Objective::defenderFull);
        if (!allFull) allDefenderSince = -1;
        else if (allDefenderSince < 0) allDefenderSince = tick;
        var remaining = objectives.stream().filter(o -> !o.attackerOwned()).toList();
        boolean singleNeutral = remaining.size() == 1 && remaining.get(0).neutral();
        boolean singleDisturbed = remaining.size() == 1 && remaining.get(0).disturbed();
        boolean anyNeutral = objectives.stream().anyMatch(Objective::neutral);
        if (!lastBattalion && !lastSector) {
            if (troops <= 20) troopReason = true;
            if (troops >= 30) troopReason = false;
            if (singleNeutral) objectiveReason = true;
            if (anyFullFor(tick, 200)) objectiveReason = false;
            level = zeroContest || troopReason || objectiveReason ? 1 : 0;
            return level;
        }
        level = Math.max(1, level);
        boolean high = zeroContest || (lastBattalion && troops <= 20) || (lastSector && singleDisturbed);
        boolean medium = (lastBattalion && troops <= 50) || (lastSector && anyNeutral);
        if (high) level = 3;
        else if (medium) level = Math.max(2, level);
        if (!zeroContest && level == 3 && (!lastBattalion || troops >= 30)
            && (!lastSector || anyFullFor(tick, 600))) level = 2;
        if (!zeroContest && level == 2 && (!lastBattalion || troops >= 60)
            && (!lastSector || (allDefenderSince >= 0 && tick - allDefenderSince >= 200))) level = 1;
        return level;
    }

    private boolean anyFullFor(long tick, long duration) {
        return defenderSince.values().stream().anyMatch(start -> tick - start >= duration);
    }
    public void reset() {
        defenderSince.clear(); allDefenderSince = -1;
        troopReason = objectiveReason = false; level = 0;
    }
}
