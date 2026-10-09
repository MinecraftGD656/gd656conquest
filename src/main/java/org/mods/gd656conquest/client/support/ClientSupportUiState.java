package org.mods.gd656conquest.client.support;

import org.mods.gd656conquest.network.packet.SupportUiSnapshotPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ClientSupportUiState {
    private static Snapshot snapshot;
    private static long snapshotUpdatedAtMs;

    private ClientSupportUiState() {
    }

    public static synchronized void apply(SupportUiSnapshotPacket packet) {
        if (packet == null) {
            return;
        }
        List<SupportEntryState> entries = new ArrayList<>();
        for (SupportUiSnapshotPacket.SupportEntryPayload payload : packet.entries()) {
            if (payload == null) {
                continue;
            }
            entries.add(new SupportEntryState(
                payload.kind() == null ? "" : payload.kind(),
                payload.id() == null ? "" : payload.id(),
                payload.displayName() == null ? "" : payload.displayName(),
                payload.typeKey() == null ? "" : payload.typeKey(),
                payload.subtitleText() == null ? "" : payload.subtitleText(),
                payload.detailText() == null ? "" : payload.detailText(),
                payload.buttonText() == null ? "" : payload.buttonText(),
                Math.max(0, payload.cost()),
                payload.available()
            ));
        }
        entries.sort(Comparator
            .comparing(ClientSupportUiState.SupportEntryState::available, Comparator.reverseOrder())
            .thenComparingInt(SupportEntryState::cost)
            .thenComparing(SupportEntryState::kind, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(SupportEntryState::id, String.CASE_INSENSITIVE_ORDER));
        snapshot = new Snapshot(
            packet.roomId() == null ? "" : packet.roomId(),
            packet.roomDisplayName() == null ? "" : packet.roomDisplayName(),
            packet.teamDisplayName() == null ? "" : packet.teamDisplayName(),
            Math.max(0, packet.currentScore()),
            Math.max(0, packet.spentScore()),
            Math.max(0, packet.remainingScore()),
            packet.statusText() == null ? "" : packet.statusText(),
            List.copyOf(entries)
        );
        snapshotUpdatedAtMs = System.currentTimeMillis();
    }

    public static synchronized Snapshot snapshot() {
        return snapshot;
    }

    public static synchronized void clear() {
        snapshot = null;
        snapshotUpdatedAtMs = 0L;
    }

    public static synchronized long snapshotUpdatedAtMs() {
        return snapshotUpdatedAtMs;
    }

    public record Snapshot(
        String roomId,
        String roomDisplayName,
        String teamDisplayName,
        int currentScore,
        int spentScore,
        int remainingScore,
        String statusText,
        List<SupportEntryState> entries
    ) {
    }

    public record SupportEntryState(
        String kind,
        String id,
        String displayName,
        String typeKey,
        String subtitleText,
        String detailText,
        String buttonText,
        int cost,
        boolean available
    ) {
    }
}
