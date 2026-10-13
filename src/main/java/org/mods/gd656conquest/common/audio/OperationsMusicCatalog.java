package org.mods.gd656conquest.common.audio;

import java.util.function.IntPredicate;

public final class OperationsMusicCatalog {
    private static final String PREFIX = "gd656conquest:music.operation.";
    public static final String WIN = PREFIX + "eor_win", LOSE = PREFIX + "eor_lose", OVERLAY = PREFIX + "pre_eor_overlay";
    private OperationsMusicCatalog() {}
    public static String round(int n) { return PREFIX + "round_start" + n; }
    public static String advance(int n) { return PREFIX + "attackers_advance" + n; }
    public static String finale(int n) { return PREFIX + "pre_eor_0" + n; }
    public static int numbered(int requested, int slots, IntPredicate playable) {
        for (int n = Math.min(Math.max(1, requested), slots); n >= 1; n--) if (playable.test(n)) return n;
        return 0;
    }
}
