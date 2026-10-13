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
    LOSING("losing");

    private final String key;
    OperationsVoiceCue(String key) { this.key = key; }
    public String soundId() { return "gd656conquest:voice.operation." + key; }
    public String resourceName() { return "gd656conquest:voice/operation/vo_mp_mother_breakthrough_" + key; }
    public static boolean contains(String soundId) {
        for (var cue : values()) if (cue.soundId().equals(soundId)) return true;
        return false;
    }
}
