package org.mods.gd656conquest.client.parkour;

import org.mods.gd656conquest.network.packet.ParkourLeaderboardSnapshotPacket;

import java.util.List;

public final class ClientParkourLeaderboardState {
    private static Snapshot snapshot;
    private static boolean requestOpenPanel;
    private static long snapshotUpdatedAtMs;

    private ClientParkourLeaderboardState() {
    }

    public static synchronized void apply(ParkourLeaderboardSnapshotPacket packet) {
        if (packet == null) {
            return;
        }
        List<EntryState> entries = packet.entries().stream()
            .map(entry -> new EntryState(entry.rank(), entry.playerName(), entry.timeMillis(), entry.self()))
            .toList();
        snapshot = new Snapshot(
            packet.quickJoinAvailable(),
            packet.inParkour(),
            Math.max(0, packet.currentRank()),
            Math.max(0L, packet.bestTimeMillis()),
            Math.max(0L, packet.lastTimeMillis()),
            packet.recordHolderName() == null ? "" : packet.recordHolderName(),
            Math.max(0L, packet.recordHolderTimeMillis()),
            entries
        );
        snapshotUpdatedAtMs = System.currentTimeMillis();
        if (packet.openPanel()) {
            requestOpenPanel = true;
        }
    }

    public static synchronized Snapshot snapshot() {
        return snapshot;
    }

    public static synchronized long snapshotAgeMs() {
        if (snapshot == null || snapshotUpdatedAtMs <= 0L) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, System.currentTimeMillis() - snapshotUpdatedAtMs);
    }

    public static synchronized boolean consumeOpenPanelRequest() {
        boolean current = requestOpenPanel;
        requestOpenPanel = false;
        return current;
    }

    public static synchronized void clear() {
        snapshot = null;
        requestOpenPanel = false;
        snapshotUpdatedAtMs = 0L;
    }

    public record Snapshot(
        boolean quickJoinAvailable,
        boolean inParkour,
        int currentRank,
        long bestTimeMillis,
        long lastTimeMillis,
        String recordHolderName,
        long recordHolderTimeMillis,
        List<EntryState> entries
    ) {
    }

    public record EntryState(int rank, String playerName, long timeMillis, boolean self) {
    }
}
