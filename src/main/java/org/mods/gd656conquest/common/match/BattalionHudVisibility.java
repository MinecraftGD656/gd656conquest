package org.mods.gd656conquest.common.match;

/** Preserve cinematic presentation and non-combat UI without changing the user's F1 setting. */
public final class BattalionHudVisibility {
    private BattalionHudVisibility() {}

    public static boolean hidesPlayerHud(BattalionSequence.Phase phase, boolean finalResult) {
        return switch (phase) {
            case COMBAT, PREPARATION -> false;
            case FINISHED -> finalResult;
            default -> true;
        };
    }

    public static boolean keepsOverlay(String id) {
        return "gd656conquest:gd656conquest_base_effects".equals(id)
            || "gd656conquest:gd656conquest_main_hud".equals(id)
            || "minecraft:chat_panel".equals(id)
            || "minecraft:player_list".equals(id);
    }
}
