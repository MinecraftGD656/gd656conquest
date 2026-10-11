package org.mods.gd656conquest.server.room.gameplay;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

/** Fatal-event queries share the same retreat identity as boundary and marker handling. */
public interface RoomGameplayRetreatProvider {
    boolean isRetreatingDefender(RoomDefinition room, ServerPlayer player);
}
