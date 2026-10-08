package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.packet.RoomHudSyncPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayHudState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.objective.RoomObjectiveDeployHudTargetProvider;

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
    public void prepareRoomSnapshot(RoomDefinition room, RunningRuntimeState state) {
        runningHudPayloadBuilder.prepareRoomSnapshot(room, state);
        deployHudPayloadBuilder.prepareRoomSnapshot(room, state);
    }

    @Override
    public RoomHudSyncPacket buildPacket(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        if (room == null || state == null || player == null) {
            return null;
        }
        RoomLifecycleState lifecycleState = room.lifecycleState();
        boolean maintenance = lifecycleState == RoomLifecycleState.MAINTENANCE;
        String lifecycleText = maintenance
            ? "维护中"
            : lifecycleState == RoomLifecycleState.ENDING ? "结算中" : "进行中";
        RoomGameplayPresentationProvider presentationProvider = RoomGameplayPresentationProvider.resolve(state);
        UUID playerId = player.getUUID();
        RoomTeamId selfTeam = state.getRoomTeams().get(playerId);
        boolean inDeployFlow = state.isInDeployFlow(playerId);
        RoomGameplayHudState hudState = presentationProvider == null
            ? RoomGameplayHudState.defaults()
            : Objects.requireNonNullElseGet(
                presentationProvider.buildHudState(room, state, player, inDeployFlow, server.getTickCount()),
                RoomGameplayHudState::defaults
            );
        String captureIcons = inDeployFlow
            ? deployHudPayloadBuilder.build(room, state, playerId, selfTeam)
            : runningHudPayloadBuilder.buildPersistent(room, state, playerId, selfTeam);
        boolean allowTeamSwitch = room.commonConfig().allowPlayerCampSwitch();
        if (org.mods.gd656conquest.server.room.model.RoomGameplayId.GUNMASTER.equals(room.gameplayId()) && room.gunmasterConfig().isSoloGrouping()) {
            allowTeamSwitch = false;
        }
        
        return new RoomHudSyncPacket(
            room.roomId(),
            lifecycleText,
            maintenance ? "MAINTENANCE" : hudState.audioState(),
            toHudTeamName(selfTeam),
            resolveHudClassKey(player),
            hudState.campAScore(),
            hudState.campBScore(),
            hudState.elapsedSeconds(),
            hudState.mode(),
            hudState.campAGoal(),
            hudState.campBGoal(),
            hudState.remainSeconds(),
            room.commonConfig().playerMaxY(),
            room.commonConfig().aircraftPassengerMaxY(),
            allowTeamSwitch,
            hudState.topStatus(),
            captureIcons,
            hudState.eventText(),
            hudState.focusPayload(),
            hudState.extraPayload()
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
        RunningRuntimeState state = RoomCoreRuntimeManager.of(player.server)
            .findRunningState(RoomCoreRuntimeManager.of(player.server).findPlayerRoomId(player.getUUID()).orElse(""))
            .orElse(null);
        PlayerClassType classType = CustomLoadoutService.resolveCurrentClass(
            ConquestDataManager.of(player.server).playerData().getPlayerData(player.getUUID()),
            state,
            player.getUUID()
        );
        return classType == null ? PlayerClassType.defaultType().getKey() : classType.getKey();
    }
}
