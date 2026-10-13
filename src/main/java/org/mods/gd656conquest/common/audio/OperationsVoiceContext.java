package org.mods.gd656conquest.common.audio;

public record OperationsVoiceContext(String roomId, long matchId, int battalion, int sector,
    boolean running, boolean retreatActive, boolean attacker, boolean pendingDefender,
    boolean combat, int troops, int troopCap) {
    public OperationsVoiceContext(String roomId, long matchId, int battalion, int sector,
        boolean running, boolean retreatActive, boolean attacker, boolean pendingDefender) {
        this(roomId, matchId, battalion, sector, running, retreatActive, attacker, pendingDefender, !retreatActive, 200, 200);
    }
    public boolean sameStage(OperationsVoiceContext other) {
        return other != null && roomId.equals(other.roomId) && matchId == other.matchId
            && battalion == other.battalion && sector == other.sector;
    }
    public boolean allows(OperationsVoiceCue cue) {
        if (!running || cue == null) return false;
        if (cue.troopRank() > 0) {
            if (!allowsStarted(cue) || troops <= 0) return false;
            return switch (cue.troopRank()) {
                case 1 -> troopCap > 0 && 2L * troops <= troopCap;
                case 2 -> troops <= 50 && troops > 20;
                default -> troops <= 20;
            };
        }
        return switch (cue) {
            case SECTOR_TAKEN -> attacker;
            case SECTOR_FALLEN -> !attacker && !pendingDefender;
            case CLEANUP_INITIATE_ATTACKERS, CLEANUP_FEW_ATTACKERS -> retreatActive && attacker;
            case CLEANUP_INITIATE_DEFENDERS, CLEANUP_FEW_DEFENDERS -> retreatActive && !attacker && pendingDefender;
            case RETREAT_SUCCESS -> !attacker && !pendingDefender;
            case CLEANUP_ENDED -> attacker && !retreatActive && !combat;
            case WINNING -> attacker && combat;
            case LOSING -> !attacker && combat;
            default -> true;
        };
    }
    /** A valid warning already being spoken finishes normally; only its stage and role can invalidate it. */
    public boolean allowsStarted(OperationsVoiceCue cue) {
        if (cue != null && cue.troopRank() > 0) return running && combat && attacker == cue.attackerVariant();
        return allows(cue);
    }
}
