package org.mods.gd656conquest.common.audio;

/** Room-wide music phase; the local outcome is supplied separately for each listener. */
public record OperationsMusicSnapshot(String roomId, long matchId, long serverTick, Mode mode,
    int battalion, int sector, int roundVariant, long roundStartTick, long resultSequence,
    int finaleLevel, boolean strongFinale, boolean terminal, boolean victory, int resultElapsedTicks, int advanceVariant) {
    public OperationsMusicSnapshot(String roomId, long matchId, long serverTick, Mode mode,
        int battalion, int sector, int roundVariant, long roundStartTick, long resultSequence,
        int finaleLevel, boolean strongFinale, boolean terminal, boolean victory, int resultElapsedTicks) {
        this(roomId, matchId, serverTick, mode, battalion, sector, roundVariant, roundStartTick, resultSequence,
            finaleLevel, strongFinale, terminal, victory, resultElapsedTicks, 0);
    }
    public enum Mode { PREPARATION, COMBAT, ROUND_START, ADVANCE, RESULT }
    public String stageKey() { return roomId + "|" + matchId + "|" + battalion + "|" + sector; }
    public String resultKey() { return roomId + "|" + matchId + "|" + resultSequence; }
    public boolean advanceOpening() { return advanceVariant > 0; }
    public String openingKey() { return stageKey() + "|" + roundStartTick + "|" + roundVariant + "|" + advanceVariant; }
}
