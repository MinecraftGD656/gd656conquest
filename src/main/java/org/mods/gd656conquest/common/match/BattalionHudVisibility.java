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

    private static final java.util.Set<String> BATTLE_OVERLAYS = java.util.Set.of(
        "minecraft:hotbar", "minecraft:crosshair", "minecraft:boss_event_progress",
        "minecraft:player_health", "minecraft:armor_level", "minecraft:food_level", "minecraft:air_level",
        "minecraft:mount_health", "minecraft:jump_bar", "minecraft:experience_bar", "minecraft:item_name",
        "minecraft:potion_icons", "minecraft:scoreboard", "minecraft:title_text", "minecraft:record_overlay",
        "simplehealthbar:health_bar", "tacz:tac_gun_hud_overlay", "tacz:tac_heat_bar",
        "tacz:tac_kill_amount_overlay", "tacz:tac_interact_key_overlay", "tacz:ammo_count",
        "superbwarfare:superbwarfare_kill_message"
    );

    public static boolean hidesBattleOverlay(String id) {
        return BATTLE_OVERLAYS.contains(id == null ? "" : id);
    }
}
