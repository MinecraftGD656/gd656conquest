package org.mods.gd656conquest.common.match;

/** Fixed troop warning bands, shared by the live HUD and its preview. */
public final class OperationsHudText {
    private OperationsHudText() {}

    public static String notice(int attackTroops, int battalion, int totalBattalions, boolean selfAttacker) {
        if (attackTroops >= 0 && attackTroops <= 50) {
            int band = attackTroops <= 20 ? 20 : 50;
            return "剩余" + band + "名" + (selfAttacker ? "士兵" : "攻击者");
        }
        return battalion > 0 && battalion == totalBattalions ? "最后的营" : "";
    }
}
