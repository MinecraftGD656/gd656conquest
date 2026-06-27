package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.model.RoomGameplayId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RoomGameplayEntryManager {
    private final Map<RoomGameplayId, IRoomGameplayEntry> entriesById = new LinkedHashMap<>();

    public RoomGameplayEntryManager(Collection<? extends IRoomGameplayEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        for (IRoomGameplayEntry entry : entries) {
            register(entry);
        }
    }

    public void register(IRoomGameplayEntry entry) {
        IRoomGameplayEntry resolved = Objects.requireNonNull(entry, "entry");
        RoomGameplayId gameplayId = Objects.requireNonNull(resolved.gameplayId(), "gameplayId");
        IRoomGameplayEntry previous = entriesById.putIfAbsent(gameplayId, resolved);
        if (previous != null) {
            throw new IllegalStateException("重复注册玩法入口: " + gameplayId);
        }
    }

    public Optional<IRoomGameplayEntry> find(RoomGameplayId gameplayId) {
        if (gameplayId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entriesById.get(gameplayId));
    }
}