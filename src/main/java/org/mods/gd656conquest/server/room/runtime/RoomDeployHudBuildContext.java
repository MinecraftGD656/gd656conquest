package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.Optional;
import java.util.UUID;

public record RoomDeployHudBuildContext(
    MinecraftServer server,
    RoomDefinition room,
    RunningRuntimeState state,
    UUID playerId,
    RoomTeamId selfTeam,
    Optional<IRoomGameplayRuntime> gameplayRuntime
) {
}
