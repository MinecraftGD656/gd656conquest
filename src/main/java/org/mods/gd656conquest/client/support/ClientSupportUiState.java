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
        List<HeroKitEntryState> heroKits = new ArrayList<>();
        for (SupportUiSnapshotPacket.HeroKitPayload payload : packet.heroKits()) {
            if (payload == null) {
                continue;
            }
            heroKits.add(new HeroKitEntryState(
                payload.id() == null ? "" : payload.id(),
                payload.displayName() == null ? "" : payload.displayName(),
                payload.type() == null ? "" : payload.type(),
                Math.max(0, payload.cost()),
                payload.unlocked()
            ));
        }
        heroKits.sort(Comparator
            .comparingInt(HeroKitEntryState::cost)
            .thenComparing(HeroKitEntryState::id, String.CASE_INSENSITIVE_ORDER));
        snapshot = new Snapshot(
            packet.roomId() == null ? "" : packet.roomId(),
            packet.roomDisplayName() == null ? "" : packet.roomDisplayName(),
            packet.teamDisplayName() == null ? "" : packet.teamDisplayName(),
            Math.max(0, packet.currentScore()),
            Math.max(0, packet.spentScore()),
            Math.max(0, packet.remainingScore()),
            packet.statusText() == null ? "" : packet.statusText(),
            List.copyOf(heroKits)
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
        List<HeroKitEntryState> heroKits
    ) {
    }

    public record HeroKitEntryState(
        String id,
        String displayName,
        String type,
        int cost,
        boolean unlocked
    ) {
    }
}
