package org.mods.gd656conquest.common.audio;

public record OperationsVoiceContext(String roomId, long matchId, int battalion, int sector,
    boolean running, boolean retreatActive, boolean attacker, boolean pendingDefender) {
    public boolean sameStage(OperationsVoiceContext other) {
        return other != null && roomId.equals(other.roomId) && matchId == other.matchId
            && battalion == other.battalion && sector == other.sector;
    }
    public boolean allows(OperationsVoiceCue cue) {
        if (!running || cue == null) return false;
        return switch (cue) {
            case SECTOR_TAKEN -> attacker;
            case SECTOR_FALLEN -> !attacker && !pendingDefender;
            case CLEANUP_INITIATE_ATTACKERS, CLEANUP_FEW_ATTACKERS -> retreatActive && attacker;
            case CLEANUP_INITIATE_DEFENDERS, CLEANUP_FEW_DEFENDERS -> retreatActive && !attacker && pendingDefender;
            default -> true;
        };
    }
}
