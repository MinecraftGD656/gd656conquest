package org.mods.gd656conquest.common.match;

/** Shared arithmetic for server troop accounting. */
public final class OperationsTroopRules {
    public static final int SECTOR_CAPTURE_FLOOR = 50;

    private OperationsTroopRules() {}

    public static int cap(double troopsPerPlayer, int playerCount, int maximum) {
        if (!Double.isFinite(troopsPerPlayer) || troopsPerPlayer <= 0 || playerCount <= 0 || maximum <= 0) return 0;
        return (int) Math.min(maximum, Math.ceil(troopsPerPlayer * playerCount));
    }

    /** A reduced cap limits new reinforcements; it never destroys existing troops. */
    public static int add(int current, int cap, int amount) {
        current = Math.max(0, current);
        if (amount <= 0 || current >= cap) return current;
        return current + Math.min(amount, cap - current);
    }

    public static int afterSectorCapture(int current, int cap, int fixedReward) {
        int withFloor = add(current, cap, Math.max(0, SECTOR_CAPTURE_FLOOR - current));
        return add(withFloor, cap, fixedReward);
    }
}
