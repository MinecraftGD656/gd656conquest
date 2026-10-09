package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RoomDeployHudPayloadBuilder {
    private final MinecraftServer server;
    private final List<RoomDeployHudTargetProvider> targetProviders;

    public RoomDeployHudPayloadBuilder(MinecraftServer server, List<RoomDeployHudTargetProvider> targetProviders) {
        this.server = Objects.requireNonNull(server, "server");
        this.targetProviders = List.copyOf(targetProviders);
    }

    public String build(RoomDefinition room, RunningRuntimeState state, UUID playerId, RoomTeamId selfTeam) {
        return build(room, state, playerId, selfTeam, true);
    }

    public String buildPersistent(RoomDefinition room, RunningRuntimeState state, UUID playerId, RoomTeamId selfTeam) {
        return build(room, state, playerId, selfTeam, false);
    }

    private String build(RoomDefinition room, RunningRuntimeState state, UUID playerId, RoomTeamId selfTeam, boolean requireDeployFlow) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        if (playerId == null || (requireDeployFlow && (selfTeam == null || !state.isInDeployFlow(playerId)))) {
            return "-";
        }

        List<RoomDeployHudElement> elements = new java.util.ArrayList<>();
        RoomDeployHudBuildContext context = new RoomDeployHudBuildContext(
            server,
            room,
            state,
            playerId,
            selfTeam,
            state.getGameplayRuntime()
        );
        for (RoomDeployHudTargetProvider targetProvider : targetProviders) {
            targetProvider.collectElements(elements, context);
        }
        return RoomDeployHudElementCodec.encodeAll(elements);
    }
}
