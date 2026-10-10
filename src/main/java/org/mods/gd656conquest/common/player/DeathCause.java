package org.mods.gd656conquest.common.player;

/** Presentation categories, independent of Minecraft/mod damage types. Wire IDs are stable. */
public enum DeathCause {
    PLAYER_KILL(0), ACCIDENT(1), FALL(2), DESERTION(3), BURNED(4), CRUSHED(5),
    DROWNED(6), REDEPLOY(7), KILLED_IN_ACTION(8), COMMAND_KILL(9);

    private final int wireId;
    DeathCause(int wireId) { this.wireId = wireId; }
    public int wireId() { return wireId; }
    public static DeathCause fromWireId(int id) {
        for (DeathCause cause : values()) if (cause.wireId == id) return cause;
        return KILLED_IN_ACTION;
    }
}
