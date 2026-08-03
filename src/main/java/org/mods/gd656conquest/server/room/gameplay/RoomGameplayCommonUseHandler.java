package org.mods.gd656conquest.server.room.gameplay;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

public interface RoomGameplayCommonUseHandler {
    default boolean handleCommonUseStart(RoomDefinition room, ServerPlayer player, long gameTick) {
        return false;
    }

    default void handleCommonUseStop(RoomDefinition room, ServerPlayer player, long gameTick) {
    }
}
