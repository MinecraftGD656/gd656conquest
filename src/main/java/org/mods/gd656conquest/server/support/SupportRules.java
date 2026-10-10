package org.mods.gd656conquest.server.support;

import org.mods.gd656conquest.server.room.model.RoomSuppressionConfig;

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
    public static double nearGain(double damage, double distance, RoomSuppressionConfig config) {
        if (!Double.isFinite(damage) || !Double.isFinite(distance) || damage <= 0 || distance < 0
            || distance > config.radiusBlocks()) return 0;
        return damage * config.damageScale() * (1 - (2.0 / 3) * distance / config.radiusBlocks());
    }
    public static final class Suppression {
        private double strength;
        private long lastNear = Long.MIN_VALUE, evaluated, recoveryUntil;
        private boolean suppressed;
        public double value(long tick, RoomSuppressionConfig config) {
            if (lastNear != Long.MIN_VALUE) {
                long from = Math.max(evaluated, lastNear + (long)Math.ceil(config.decayDelaySeconds()*20));
                if (tick > from) strength = Math.max(0, strength - (tick-from)*config.decayPerSecond()/20);
            }
            evaluated = tick;
            boolean active = strength >= config.threshold();
            // Start the full recovery delay once, when suppression actually ends.
            if (suppressed && !active) recoveryUntil = tick + (long)Math.ceil(config.recoverySeconds()*20);
            suppressed = active;
            return strength;
        }
        public void add(double gain, long tick, RoomSuppressionConfig config) {
            value(tick, config);
            if (!Double.isFinite(gain) || gain <= 0) return;
            strength = Math.min(100, strength + gain); lastNear = evaluated = tick;
            suppressed = strength >= config.threshold();
        }
        public boolean active(long tick, RoomSuppressionConfig config) { return value(tick,config) >= config.threshold(); }
        public boolean blocksHealing(long tick, RoomSuppressionConfig config) {
            return active(tick,config) || tick < recoveryUntil;
        }
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
