package org.mods.gd656conquest.client.loadout.state;

import java.util.Collections;
import java.util.List;

public final class ClientRoomSelectionState {
    private static volatile Snapshot cached;

    public static void apply(Snapshot snapshot) {
        cached = snapshot;
    }

    public static Snapshot snapshot() {
        return cached;
    }

    public record Entry(
        String roomId,
        String displayName,
        String mapName,
        String roomDescription,
        String gameplayId,
        String lifecycleState,
        int currentPlayers,
        int maxPlayers
    ) {}

    public record Snapshot(
        List<Entry> entries
    ) {
        public Snapshot {
            entries = entries == null ? Collections.emptyList() : List.copyOf(entries);
        }
    }
}
