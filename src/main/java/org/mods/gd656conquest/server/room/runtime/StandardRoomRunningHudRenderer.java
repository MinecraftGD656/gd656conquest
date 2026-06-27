package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.packet.RoomHudSyncPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.IRoomGameplayRuntime;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayHudState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveDeployHudTargetProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class StandardRoomRunningHudRenderer implements RoomRunningHudRenderer {
    private final MinecraftServer server;
    private final RoomDeployHudPayloadBuilder runningHudPayloadBuilder;
    private final RoomDeployHudPayloadBuilder deployHudPayloadBuilder;

    public StandardRoomRunningHudRenderer(MinecraftServer server, RoomSquadBeaconService squadBeaconService) {
        this.server = Objects.requireNonNull(server, "server");
        Objects.requireNonNull(squadBeaconService, "squadBeaconService");
        this.runningHudPayloadBuilder = new RoomDeployHudPayloadBuilder(
            server,
            java.util.List.of(
                new RoomBoundaryDeployHudTargetProvider(),
                new RoomObjectiveDeployHudTargetProvider(),
                new RoomBeaconDeployHudTargetProvider(squadBeaconService)
            )
        );
        this.deployHudPayloadBuilder = new RoomDeployHudPayloadBuilder(
            server,
            java.util.List.of(
                new RoomBoundaryDeployHudTargetProvider(),
                new RoomObjectiveDeployHudTargetProvider(),
                new RoomBeaconDeployHudTargetProvider(squadBeaconService),
                new RoomTeamDeployHudTargetProvider(),
                new RoomTeammateDeployHudTargetProvider(),
                new RoomVehicleDeployHudTargetProvider()
            )
        );
    }

    @Override
    public boolean supports(RoomDefinition room) {
        return room != null;
    }

    @Override
    public RoomHudSyncPacket buildPacket(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        return buildPacket(beginRoomBatch(room, state), player);
    }

    public RunningRoomBatch beginRoomBatch(RoomDefinition room, RunningRuntimeState state) {
        if (room == null || state == null) {
            return null;
        }
        RoomLifecycleState lifecycleState = room.lifecycleState();
        boolean maintenance = lifecycleState == RoomLifecycleState.MAINTENANCE;
        return new RunningRoomBatch(
            room,
            state,
            RoomGameplayPresentationProvider.resolve(state),
            maintenance,
            maintenance ? "维护中" : lifecycleState == RoomLifecycleState.ENDING ? "结算中" : "进行中",
            server.getTickCount()
        );
    }

    public RoomHudSyncPacket buildPacket(RunningRoomBatch batch, ServerPlayer player) {
        if (batch == null) {
            return null;
        }
        RoomDefinition room = batch.room();
        RunningRuntimeState state = batch.state();
        if (room == null || state == null || player == null) {
            return null;
        }
        UUID playerId = player.getUUID();
        RoomTeamId selfTeam = state.getRoomTeams().get(playerId);
        boolean inDeployFlow = state.isInDeployFlow(playerId);
        RoomGameplayHudState hudState = batch.presentationProvider() == null
            ? RoomGameplayHudState.defaults()
            : Objects.requireNonNullElseGet(
                batch.presentationProvider().buildHudState(room, state, player, inDeployFlow, batch.serverTick()),
                RoomGameplayHudState::defaults
            );
        String captureIcons = inDeployFlow
            ? deployHudPayloadBuilder.build(room, state, playerId, selfTeam)
            : resolvePersistentCaptureIcons(batch, playerId, selfTeam);
        return new RoomHudSyncPacket(
            room.roomId(),
            batch.lifecycleText(),
            batch.maintenance() ? "MAINTENANCE" : hudState.audioState(),
            toHudTeamName(selfTeam),
            resolveHudClassKey(player),
            hudState.campAScore(),
            hudState.campBScore(),
            hudState.elapsedSeconds(),
            hudState.mode(),
            hudState.campAGoal(),
            hudState.campBGoal(),
            hudState.remainSeconds(),
            hudState.topStatus(),
            captureIcons,
            hudState.eventText(),
            hudState.focusPayload()
        );
    }

    @Override
    public String buildMatchingCaptureIcons(RoomDefinition room, RoomRuntimeState state, ServerPlayer player) {
        if (room == null || state == null || player == null) {
            return "-";
        }
        RoomTeamId selfTeam = state.getMatchingState().getQueueTeams().get(player.getUUID());
        if (selfTeam == null) {
            return "-";
        }
        RunningRuntimeState matchingSnapshot = new RunningRuntimeState();
        for (UUID queuedPlayerId : state.getMatchingState().getQueuePlayers()) {
            matchingSnapshot.addRoomPlayer(queuedPlayerId, state.getMatchingState().getQueueTeams().get(queuedPlayerId));
        }
        return runningHudPayloadBuilder.buildPersistent(room, matchingSnapshot, player.getUUID(), selfTeam);
    }

    private String toHudTeamName(RoomTeamId teamId) {
        if (teamId == RoomTeamId.CAMP_A) {
            return "CampA";
        }
        if (teamId == RoomTeamId.CAMP_B) {
            return "CampB";
        }
        return "-";
    }

    private String resolveHudClassKey(ServerPlayer player) {
        if (player == null || player.server == null) {
            return PlayerClassType.defaultType().getKey();
        }
        PlayerClassType classType = CustomLoadoutService.resolveCurrentClass(
            ConquestDataManager.of(player.server).playerData().getPlayerData(player.getUUID())
        );
        return classType == null ? PlayerClassType.defaultType().getKey() : classType.getKey();
    }

    private String resolvePersistentCaptureIcons(RunningRoomBatch batch, UUID playerId, RoomTeamId selfTeam) {
        String squadLabel = normalizeSquadLabel(batch.state().getSquadLabelsByPlayer().get(playerId));
        PersistentPayloadCacheKey cacheKey = new PersistentPayloadCacheKey(selfTeam, squadLabel);
        return batch.persistentCaptureIconsByGroup().computeIfAbsent(
            cacheKey,
            ignored -> runningHudPayloadBuilder.buildPersistent(batch.room(), batch.state(), playerId, selfTeam)
        );
    }

    private static String normalizeSquadLabel(String squadLabel) {
        return squadLabel == null ? "" : squadLabel.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public record RunningRoomBatch(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomGameplayPresentationProvider presentationProvider,
        boolean maintenance,
        String lifecycleText,
        int serverTick,
        Map<PersistentPayloadCacheKey, String> persistentCaptureIconsByGroup
    ) {
        private RunningRoomBatch(
            RoomDefinition room,
            RunningRuntimeState state,
            RoomGameplayPresentationProvider presentationProvider,
            boolean maintenance,
            String lifecycleText,
            int serverTick
        ) {
            this(room, state, presentationProvider, maintenance, lifecycleText, serverTick, new HashMap<>());
        }
    }

    private record PersistentPayloadCacheKey(RoomTeamId selfTeam, String squadLabel) {
    }
}
