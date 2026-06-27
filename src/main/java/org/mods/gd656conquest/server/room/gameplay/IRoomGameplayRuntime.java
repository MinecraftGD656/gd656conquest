package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.core.RoomPlayerInvalidationReason;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.Optional;
import java.util.UUID;

public interface IRoomGameplayRuntime {
    void onEnterMainPhase(RoomDefinition room, long gameTick);

    void tick(RoomDefinition room, long gameTick);

    default void onPlayerInvalidated(
        RoomDefinition room,
        UUID playerId,
        RoomTeamId teamId,
        RoomPlayerInvalidationReason reason,
        int killerEntityId,
        long gameTick
    ) {
    }

    default void onLeaveMainPhase(RoomDefinition room, long gameTick) {
    }

    default Optional<RoomMatchEndRequest> pollMatchEndRequest() {
        return Optional.empty();
    }
}
