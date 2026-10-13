package org.mods.gd656conquest.common.audio;

/** Local operation announcements, independent of national soldier voice packs. */
public enum OperationsVoiceCue {
    HALFWAY_ATTACKERS("kills_halfway_attackers"),
    HALFWAY_DEFENDERS("kills_halfway_defenders"),
    FIFTY_ATTACKERS("kills_togo50_attackers"),
    FIFTY_DEFENDERS("kills_togo50_defenders"),
    TWENTY_ATTACKERS("kills_togo20_attackers"),
    TWENTY_DEFENDERS("kills_togo20_defenders"),
    RETREAT_SUCCESS("cleanup_retreatsuccess_defenders"),
    CLEANUP_ENDED("cleanup_ended_attackers"),
    WINNING("winning"),
    LOSING("losing"),
    SECTOR_TAKEN("sector_taken"),
    SECTOR_FALLEN("sector_fallen"),
    CLEANUP_INITIATE_ATTACKERS("cleanup_initiate_attackers"),
    CLEANUP_INITIATE_DEFENDERS("cleanup_initiate_defenders"),
    CLEANUP_FEW_ATTACKERS("cleanup_togofew_attackers"),
    CLEANUP_FEW_DEFENDERS("cleanup_togofew_defenders");

    private final String key;
    OperationsVoiceCue(String key) { this.key = key; }
    public String soundId() { return "gd656conquest:voice.operation." + key; }
    public String resourceName() { return "gd656conquest:voice/operation/vo_mp_mother_breakthrough_" + key; }
    public static boolean contains(String soundId) {
        return fromId(soundId) != null;
    }
    public static OperationsVoiceCue fromId(String soundId) {
        for (var cue : values()) if (cue.soundId().equals(soundId)) return cue;
        return null;
    }
    public boolean reminder() { return this == CLEANUP_FEW_ATTACKERS || this == CLEANUP_FEW_DEFENDERS; }
    public boolean initiate() { return this == CLEANUP_INITIATE_ATTACKERS || this == CLEANUP_INITIATE_DEFENDERS; }
    public int troopRank() {
        return switch (this) {
            case HALFWAY_ATTACKERS, HALFWAY_DEFENDERS -> 1;
            case FIFTY_ATTACKERS, FIFTY_DEFENDERS -> 2;
            case TWENTY_ATTACKERS, TWENTY_DEFENDERS -> 3;
            default -> 0;
        };
    }
    public boolean attackerVariant() {
        return this == HALFWAY_ATTACKERS || this == FIFTY_ATTACKERS || this == TWENTY_ATTACKERS;
    }
    public int queueLifetimeTicks() {
        if (reminder()) return 60;
        if (troopRank() > 0 || this == RETREAT_SUCCESS || this == CLEANUP_ENDED) return 100;
        return this == WINNING || this == LOSING ? 200 : 0;
    }
}
