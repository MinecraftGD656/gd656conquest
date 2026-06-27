package org.mods.gd656conquest.server.room.gameplay.conquest;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayEntry;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;

import java.util.Objects;

public final class ConquestGameplayEntry implements IRoomGameplayEntry {
    private final ConquestCaptureRuntimeService captureRuntimeService;

    public ConquestGameplayEntry(MinecraftServer server) {
        this.captureRuntimeService = new ConquestCaptureRuntimeService(Objects.requireNonNull(server, "server"));
    }

    @Override
    public RoomGameplayId gameplayId() {
        return RoomGameplayId.CONQUEST;
    }

    @Override
    public IRoomGameplayRuntime createRuntime(RoomDefinition room, RunningRuntimeState state) {
        return new ConquestGameplayRuntime(state, captureRuntimeService);
    }
}
