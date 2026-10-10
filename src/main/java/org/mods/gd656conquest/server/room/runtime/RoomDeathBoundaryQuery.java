package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.level.ServerPlayer;

/** Fatal-time geometry uses the same rules and aircraft padding as the live boundary countdown. */
public final class RoomDeathBoundaryQuery {
    private RoomDeathBoundaryQuery() {}
    public static boolean isOutside(ServerPlayer player) {
        return UnifiedBoundaryService.isOutside(player);
    }
}
