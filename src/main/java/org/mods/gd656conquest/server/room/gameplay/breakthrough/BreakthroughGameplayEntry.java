package org.mods.gd656conquest.server.room.gameplay.breakthrough;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayEntry;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.runtime.RoomMarkerRuntimeService;

import java.util.Objects;

public final class BreakthroughGameplayEntry implements IRoomGameplayEntry {
    private final MinecraftServer server;
    private final BreakthroughCaptureRuntimeService captureRuntimeService;
    private final BreakthroughStageTransitionTitleService titleService;
    private final RoomMarkerRuntimeService markerRuntimeService;

    public BreakthroughGameplayEntry(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.captureRuntimeService = new BreakthroughCaptureRuntimeService(this.server);
        this.titleService = new BreakthroughStageTransitionTitleService(this.server);
        this.markerRuntimeService = new RoomMarkerRuntimeService(this.server);
    }

    @Override
    public RoomGameplayId gameplayId() {
        return RoomGameplayId.BREAKTHROUGH;
    }

    @Override
    public IRoomGameplayRuntime createRuntime(RoomDefinition room, RunningRuntimeState state) {
        return new BreakthroughGameplayRuntime(server, state, captureRuntimeService, titleService, markerRuntimeService);
    }
}
