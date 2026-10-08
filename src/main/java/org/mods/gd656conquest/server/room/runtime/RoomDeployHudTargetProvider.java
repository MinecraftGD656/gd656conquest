package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

import java.util.List;

public interface RoomDeployHudTargetProvider {
    /**
     * Prepares room-wide data once before a batch of player HUD packets.
     * Providers must treat the prepared data as valid for this batch only.
     */
    default void prepareRoomSnapshot(MinecraftServer server, RoomDefinition room, RunningRuntimeState state) {
    }

    void collectElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context);
}
