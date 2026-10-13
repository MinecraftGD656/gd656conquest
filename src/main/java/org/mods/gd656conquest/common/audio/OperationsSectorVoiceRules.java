package org.mods.gd656conquest.common.audio;

public final class OperationsSectorVoiceRules {
    private OperationsSectorVoiceRules() {}
    public static OperationsVoiceCue captured(int sector, int total, boolean attacker, boolean pendingDefender, boolean retreat) {
        if (sector >= total) return null;
        if (attacker) return OperationsVoiceCue.SECTOR_TAKEN;
        return retreat && pendingDefender ? OperationsVoiceCue.CLEANUP_INITIATE_DEFENDERS : OperationsVoiceCue.SECTOR_FALLEN;
    }
}
