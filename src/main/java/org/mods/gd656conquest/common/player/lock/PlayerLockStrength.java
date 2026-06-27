package org.mods.gd656conquest.common.player.lock;

public enum PlayerLockStrength {
    NONE(0),
    INVENTORY(1),
    ACTION(2),
    MOVEMENT(3),
    UI_CURSOR(4),
    HARD_LOCK(5),
    RESERVED(6);

    private final int level;

    PlayerLockStrength(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public static PlayerLockStrength fromLevel(int level) {
        int clamped = Math.max(0, Math.min(RESERVED.level, level));
        for (PlayerLockStrength value : values()) {
            if (value.level == clamped) {
                return value;
            }
        }
        return NONE;
    }
}
