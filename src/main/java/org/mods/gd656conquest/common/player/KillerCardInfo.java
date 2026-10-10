package org.mods.gd656conquest.common.player;

import java.util.UUID;

/** Immutable metadata captured for one accepted fatal hit, never live entity references. */
public record KillerCardInfo(UUID killerId, String name, int level, float health, float maxHealth,
                             int playerKills, int enemyKills, String weaponName, String weaponId,
                             String weaponDisplayId, boolean headshot) {
    public KillerCardInfo(UUID killerId, String name, int level, float health, float maxHealth,
                          int playerKills, int enemyKills, String weaponName, String weaponId, String weaponDisplayId) {
        this(killerId, name, level, health, maxHealth, playerKills, enemyKills, weaponName, weaponId, weaponDisplayId, false);
    }
    public KillerCardInfo {
        if (killerId == null || !valid(name, 128) || !valid(weaponName, 256)
            || weaponId == null || weaponId.length() > 256 || weaponDisplayId == null || weaponDisplayId.length() > 256
            || level < 0 || level > 100 || !Float.isFinite(health) || !Float.isFinite(maxHealth)
            || health < 0 || maxHealth <= 0 || health > maxHealth || playerKills < 0 || enemyKills < 0)
            throw new IllegalArgumentException("Invalid killer card metadata");
    }
    private static boolean valid(String text, int max) { return text != null && !text.isBlank() && text.length() <= max; }
    public int healthPercent() { return Math.round(100 * health / maxHealth); }
}
