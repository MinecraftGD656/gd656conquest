package org.mods.gd656conquest.common.audio;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Loss-triggered troop warnings, once per battalion sector. Reinforcements alone do not rearm them. */
public final class OperationsTroopVoiceCue {
    public enum Threshold {
        HALFWAY(OperationsVoiceCue.HALFWAY_ATTACKERS, OperationsVoiceCue.HALFWAY_DEFENDERS),
        FIFTY(OperationsVoiceCue.FIFTY_ATTACKERS, OperationsVoiceCue.FIFTY_DEFENDERS),
        TWENTY(OperationsVoiceCue.TWENTY_ATTACKERS, OperationsVoiceCue.TWENTY_DEFENDERS);

        private final OperationsVoiceCue attackers;
        private final OperationsVoiceCue defenders;
        Threshold(OperationsVoiceCue attackers, OperationsVoiceCue defenders) {
            this.attackers = attackers;
            this.defenders = defenders;
        }
        public OperationsVoiceCue forTeam(boolean attacker) { return attacker ? attackers : defenders; }
    }

    private final EnumSet<Threshold> announced = EnumSet.noneOf(Threshold.class);

    public List<Threshold> onLoss(int before, int after, int cap) {
        if (after < 0 || after >= before) return List.of();
        var result = new ArrayList<Threshold>();
        for (var threshold : Threshold.values()) {
            boolean crossed = switch (threshold) {
                case HALFWAY -> cap > 0 && 2L * after <= cap;
                case FIFTY -> after <= 50;
                case TWENTY -> after <= 20;
            };
            if (crossed && announced.add(threshold)) result.add(threshold);
        }
        return List.copyOf(result);
    }

    public void reset() { announced.clear(); }
}
