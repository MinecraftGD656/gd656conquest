package org.mods.gd656conquest.server.support;


/** Deterministic rules shared by the server and standalone verification. All times are ticks. */
public final class SupportRules {
    private SupportRules() {}
    public static boolean damageBlocksHealing(long tick, long lastDamage, double seconds) {
        return lastDamage >= 0 && tick < lastDamage + (long)Math.ceil(seconds * 20);
    }
    public static int ammoBurst(int magazine, boolean bag) {
        long amount = Math.max((long)Math.max(1, magazine) * (bag ? 2 : 1), bag ? 4 : 2);
        return (int)Math.min(Integer.MAX_VALUE, amount);
    }
    /** Progress belongs to the recipient and equipment, never the supplying box. */
    public static final class Progress {
        private long elapsed, previous = -1;
        private boolean wasEligible;
        public boolean ready(long tick, boolean missing, boolean eligible, long interval) {
            if (!missing) { elapsed=0; previous=tick; wasEligible=eligible; return false; }
            if (previous >= 0 && wasEligible && eligible) elapsed += Math.max(0,tick-previous);
            previous=tick; wasEligible=eligible;
            return eligible && elapsed >= interval;
        }
        public void supplied() { elapsed=0; }
    }
}
