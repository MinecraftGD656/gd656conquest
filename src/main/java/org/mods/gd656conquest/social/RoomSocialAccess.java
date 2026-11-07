package org.mods.gd656conquest.social;

import java.util.Optional;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;

/** Internal server bridge used by the squad service; it is never exposed in a network payload. */
public interface RoomSocialAccess {
    Optional<RoomRuntimeState> socialRuntime(String roomId);
}
