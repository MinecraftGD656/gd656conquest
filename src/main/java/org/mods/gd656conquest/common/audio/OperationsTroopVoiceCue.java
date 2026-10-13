package org.mods.gd656conquest.common.audio;

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
        Threshold mostUrgent = null;
        for (var threshold : Threshold.values()) {
            boolean crossed = switch (threshold) {
                case HALFWAY -> cap > 0 && 2L * before > cap && 2L * after <= cap;
                case FIFTY -> before > 50 && after <= 50;
                case TWENTY -> before > 20 && after <= 20;
            };
            if (crossed && announced.add(threshold)) mostUrgent = threshold;
        }
        return mostUrgent == null || after == 0 ? List.of() : List.of(mostUrgent);
    }

    public void reset() { announced.clear(); }
}
