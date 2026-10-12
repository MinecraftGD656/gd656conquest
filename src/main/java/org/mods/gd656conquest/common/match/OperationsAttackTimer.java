package org.mods.gd656conquest.common.match;

import java.util.Locale;

/** Unlimited attacks hide the clock; a timed attack keeps its clock during zero-troop contest. */
public final class OperationsAttackTimer {
    public static final int HIDDEN = -2;
    public static final int CONTEST = -1;

    private OperationsAttackTimer() {}

    public static boolean isLimited(long deadlineTick) { return deadlineTick != Long.MAX_VALUE; }

    public static int combatRemainingSeconds(long deadlineTick, long gameTick, boolean zeroTroopContest) {
        if (!isLimited(deadlineTick)) return HIDDEN;
        if (zeroTroopContest) return CONTEST;
        if (deadlineTick <= gameTick) return 0;
        return (int) Math.min(Integer.MAX_VALUE, (deadlineTick - gameTick + 19L) / 20L);
    }

    public static String text(int remainingSeconds) {
        if (remainingSeconds == HIDDEN) return "";
        if (remainingSeconds < 0) return "--:--";
        return String.format(Locale.ROOT, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
    }
}
