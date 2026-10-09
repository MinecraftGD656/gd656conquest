package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.packet.RoomHudSyncPacket;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

public interface RoomRunningHudRenderer {
    boolean supports(RoomDefinition room);

    RoomHudSyncPacket buildPacket(RoomDefinition room, RunningRuntimeState state, ServerPlayer player);

    default String buildMatchingCaptureIcons(RoomDefinition room, RoomRuntimeState state, ServerPlayer player) {
        return "-";
    }
}
