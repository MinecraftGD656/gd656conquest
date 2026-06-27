package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public interface RoomDimensionReloadBridge {
    boolean unloadRoomDimension(ResourceKey<Level> levelKey);

    boolean reloadRoomDimension(ResourceKey<Level> levelKey);
}
