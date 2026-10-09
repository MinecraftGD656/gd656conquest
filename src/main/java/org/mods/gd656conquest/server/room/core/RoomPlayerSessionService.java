package org.mods.gd656conquest.server.room.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RoomPlayerSessionService {
    private final Map<UUID, String> roomIdByPlayer = new HashMap<>();

    public void bindPlayerToRoom(UUID playerId, String roomId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(roomId, "roomId");
        String normalized = roomId.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("房间ID不能为空");
        }
        roomIdByPlayer.put(playerId, normalized);
    }

    public Optional<String> findRoomId(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(roomIdByPlayer.get(playerId));
    }

    public boolean isPlayerInRoom(UUID playerId, String roomId) {
        return findRoomId(playerId).map(current -> current.equalsIgnoreCase(roomId)).orElse(false);
    }

    public void removePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        roomIdByPlayer.remove(playerId);
    }
}