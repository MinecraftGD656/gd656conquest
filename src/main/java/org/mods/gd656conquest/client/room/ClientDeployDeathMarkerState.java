package org.mods.gd656conquest.client.room;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientDeployDeathMarkerState {
    private static final long DISPLAY_DURATION_MS = 10_000L;
    private static final long FADE_DURATION_MS = 2_000L;

    private static final Map<UUID, MarkerState> ACTIVE_MARKERS = new LinkedHashMap<>();

    private ClientDeployDeathMarkerState() {
    }

    public static void record(UUID playerId, double x, double y, double z, boolean squadMate) {
        if (playerId == null) {
            return;
        }
        ACTIVE_MARKERS.put(playerId, new MarkerState(playerId, x, y, z, squadMate, System.currentTimeMillis()));
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        Iterator<MarkerState> iterator = ACTIVE_MARKERS.values().iterator();
        while (iterator.hasNext()) {
            MarkerState marker = iterator.next();
            if (marker == null || marker.expireAtMs() <= now) {
                iterator.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE_MARKERS.clear();
    }

    public static List<MarkerSnapshot> snapshotForRender() {
        long now = System.currentTimeMillis();
        List<MarkerSnapshot> snapshots = new ArrayList<>();
        Iterator<MarkerState> iterator = ACTIVE_MARKERS.values().iterator();
        while (iterator.hasNext()) {
            MarkerState marker = iterator.next();
            if (marker == null || marker.expireAtMs() <= now) {
                iterator.remove();
                continue;
            }
            snapshots.add(marker.snapshot(now));
        }
        return snapshots;
    }

    private record MarkerState(UUID playerId, double x, double y, double z, boolean squadMate, long createdAtMs) {
        private long expireAtMs() {
            return createdAtMs + DISPLAY_DURATION_MS;
        }

        private MarkerSnapshot snapshot(long now) {
            long remainingMs = Math.max(0L, expireAtMs() - now);
            float alpha = remainingMs >= FADE_DURATION_MS ? 1.0F : Math.max(0.0F, remainingMs / (float) FADE_DURATION_MS);
            return new MarkerSnapshot(playerId, x, y, z, squadMate, alpha);
        }
    }

    public record MarkerSnapshot(UUID playerId, double x, double y, double z, boolean squadMate, float alpha) {
    }
}
