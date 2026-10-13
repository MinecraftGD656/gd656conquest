package org.mods.gd656conquest.common.audio;

/** One uninterrupted opening per battalion or captured sector, across retreat, countdown and combat. */
public final class OperationsOpeningMusicState {
    private record Entry(OperationsMusicSnapshot snapshot, long offsetTicks) {}
    private final java.util.ArrayDeque<Entry> pending = new java.util.ArrayDeque<>();
    private String observedKey = "";
    private Entry current;
    private boolean finished;
    public boolean accept(OperationsMusicSnapshot snapshot, long localTick) {
        if (current != null && (!current.snapshot().roomId().equals(snapshot.roomId())
            || current.snapshot().matchId() != snapshot.matchId() || current.snapshot().battalion() != snapshot.battalion())) reset();
        if (!observedKey.equals(snapshot.openingKey())) {
            boolean first = current == null;
            observedKey = snapshot.openingKey();
            var entry = new Entry(snapshot, first ? Math.max(0, snapshot.serverTick() - snapshot.roundStartTick()) : 0);
            if (first || (finished && pending.isEmpty())) { activate(entry, localTick); return true; }
            pending.addLast(entry);
        }
        if (finished && !pending.isEmpty()) { activate(pending.removeFirst(), localTick); return true; }
        return false;
    }
    private void activate(Entry entry, long tick) { current = entry; finished = false; }
    public OperationsMusicSnapshot snapshot() { return current.snapshot(); }
    /** Fresh openings begin at zero even when resource decoding is slow; a late join keeps its initial offset. */
    public long initialOffsetTicks() { return current.offsetTicks(); }
    public void finish() { finished = true; }
    public boolean finished() { return finished; }
    public boolean allowsFinale(OperationsMusicSnapshot snapshot) {
        return finished && pending.isEmpty() && snapshot.mode() == OperationsMusicSnapshot.Mode.COMBAT;
    }
    public void reset() { observedKey = ""; current = null; pending.clear(); finished = false; }
}
