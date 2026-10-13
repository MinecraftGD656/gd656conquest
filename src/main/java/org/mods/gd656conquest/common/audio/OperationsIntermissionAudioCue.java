package org.mods.gd656conquest.common.audio;

import org.mods.gd656conquest.common.match.BattalionSequence.Phase;

/** Tracks authoritative phase changes independently of the authored briefing timeline. */
public final class OperationsIntermissionAudioCue {
    public enum Announcement { NONE, NEXT_ASSAULT, LAST_ASSAULT }

    public record Update(boolean accepted, boolean startBackground, boolean stopBackground, Announcement announcement) {
        private static final Update NONE = new Update(false, false, false, Announcement.NONE);
    }

    private String roomId;
    private long matchId;
    private long sequenceId;
    private Phase phase;
    private boolean backgroundActive;

    public Update accept(String room, long match, long sequence, Phase nextPhase, int elapsedTicks,
                         int battalion, int total, boolean attackersWon) {
        boolean sameMatch = room.equals(roomId) && match == matchId;
        boolean sameSequence = sameMatch && sequence == sequenceId;
        if (sameMatch && (sequence < sequenceId || (sameSequence && phase != null
            && nextPhase.presentationOrder() <= phase.presentationOrder()))) return Update.NONE;

        boolean nextBackground = !attackersWon && switch (nextPhase) {
            // At preparation arrival, the server has already incremented the battalion number.
            case PREPARATION -> battalion > 1 && battalion <= total;
            case RESULT, TRAVEL_TO_BRIEFING, BRIEFING, BRIEFING_FADE_OUT, NEXT_ASSAULT, TRAVEL_TO_DEPLOY -> battalion < total;
            default -> false;
        };
        Announcement announcement = Announcement.NONE;
        if (nextBackground && nextPhase == Phase.NEXT_ASSAULT && (sameSequence || elapsedTicks == 0)) {
            announcement = total - battalion == 1 ? Announcement.LAST_ASSAULT : Announcement.NEXT_ASSAULT;
        }
        Update update = new Update(true, nextBackground && !backgroundActive, !nextBackground && backgroundActive, announcement);
        roomId = room;
        matchId = match;
        sequenceId = sequence;
        phase = nextPhase;
        backgroundActive = nextBackground;
        return update;
    }

    public boolean isBackgroundActive() { return backgroundActive; }

    public boolean isForRoom(String room) { return room.equals(roomId); }

    public void reset() {
        roomId = null;
        matchId = sequenceId = 0;
        phase = null;
        backgroundActive = false;
    }
}
