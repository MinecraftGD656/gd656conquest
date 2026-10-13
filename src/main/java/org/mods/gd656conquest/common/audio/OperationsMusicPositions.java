package org.mods.gd656conquest.common.audio;

import java.util.HashMap;
import java.util.Map;

/** Bookmarks belong to one battalion sector; a first visit uses the outgoing track's elapsed position. */
public final class OperationsMusicPositions {
    private final Map<String, Double> saved = new HashMap<>();
    public void save(String id, double seconds) {
        if (Double.isFinite(seconds)) saved.put(id, Math.max(0, seconds));
    }
    public double resume(String id, double outgoingPosition, double duration) {
        if (!Double.isFinite(duration) || duration <= 0) return 0;
        double position = saved.getOrDefault(id, Math.max(0, outgoingPosition));
        return Double.isFinite(position) ? position % duration : 0;
    }
    public void reset() { saved.clear(); }
}
