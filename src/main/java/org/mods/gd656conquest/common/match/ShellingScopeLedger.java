package org.mods.gd656conquest.common.match;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** An old generation stays harmless even after its room resumes combat. */
public final class ShellingScopeLedger {
    public record Scope(String roomId, long generation) {}
    private record State(long generation, boolean harmless) {}
    private final Map<String, State> rooms = new HashMap<>();
    // Persisted entities from a previous server instance must never regain damage.
    private final long initialGeneration = UUID.randomUUID().getMostSignificantBits();

    public Scope scope(String room) {
        var state = rooms.computeIfAbsent(room, ignored -> new State(initialGeneration, false));
        return new Scope(room, state.generation());
    }

    public void begin(String room) {
        var current = scope(room);
        rooms.put(room, new State(current.generation(), true));
    }

    public void terminate(String room) {
        var current = scope(room);
        rooms.put(room, new State(current.generation() + 1, false));
    }

    public boolean expired(Scope scope) {
        var state = rooms.get(scope.roomId());
        return state == null || state.generation() != scope.generation();
    }

    public boolean harmless(Scope scope) {
        var state = rooms.get(scope.roomId());
        return expired(scope) || state.harmless();
    }
}
