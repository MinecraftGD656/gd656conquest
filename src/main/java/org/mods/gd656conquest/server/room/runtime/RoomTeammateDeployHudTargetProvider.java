package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RoomTeammateDeployHudTargetProvider implements RoomDeployHudTargetProvider {
    private static final int MAX_TEAMMATE_ROWS = 12;
    private RunningRuntimeState preparedState;
    private String preparedRoomId = "";
    private Map<SquadKey, List<TeammateSnapshot>> preparedTeammates = Map.of();

    @Override
    public void prepareRoomSnapshot(MinecraftServer server, RoomDefinition room, RunningRuntimeState state) {
        if (server == null || room == null || state == null) {
            clearPreparedSnapshot();
            return;
        }
        long gameTick = server.getTickCount();
        Map<SquadKey, List<TeammateSnapshot>> teammates = new HashMap<>();
        for (UUID teammateId : state.getRoomPlayers()) {
            RoomTeamId teamId = state.getRoomTeams().get(teammateId);
            String squadLabel = state.getSquadLabelsByPlayer().get(teammateId);
            if (teamId == null || squadLabel == null || squadLabel.isBlank()) {
                continue;
            }
            ServerPlayer teammate = server.getPlayerList().getPlayer(teammateId);
            if (!isDeployableTeammate(state, teammateId, teammate)) {
                continue;
            }
            teammates.computeIfAbsent(new SquadKey(teamId, normalizeSquadLabel(squadLabel)), ignored -> new ArrayList<>())
                .add(new TeammateSnapshot(
                    teammateId,
                    new RoomPosition(Mth.floor(teammate.getX()), Mth.floor(teammate.getY()), Mth.floor(teammate.getZ())),
                    teammate.getYRot(),
                    teammate.getGameProfile().getName(),
                    state.isPlayerInCombat(teammateId, gameTick),
                    isCreativeMode(teammate)
                ));
        }
        preparedState = state;
        preparedRoomId = room.roomId();
        preparedTeammates = teammates;
    }

    @Override
    public void collectElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        if (context.room() != null 
            && org.mods.gd656conquest.server.room.model.RoomGameplayId.GUNMASTER.equals(context.room().gameplayId()) 
            && context.room().gunmasterConfig() != null 
            && context.room().gunmasterConfig().isSoloGrouping()) {
            return;
        }
        RoomTeamId selfTeam = context.selfTeam();
        UUID selfId = context.playerId();
        if (selfTeam == null || selfId == null) {
            return;
        }

        List<TeammateSnapshot> teammates = resolvePreparedTeammates(context, selfTeam, selfId);
        int appended = 0;
        for (TeammateSnapshot teammate : teammates) {
            if (teammate.playerId().equals(selfId)) {
                continue;
            }
            elements.add(RoomDeployHudElement.teammate(
                teammate.playerId(),
                teammate.position(),
                selfTeam,
                teammate.yaw(),
                teammate.name(),
                teammate.inCombat(),
                teammate.creative()
            ));
            appended++;
            if (appended >= MAX_TEAMMATE_ROWS) {
                return;
            }
        }
    }

    private List<TeammateSnapshot> resolvePreparedTeammates(
        RoomDeployHudBuildContext context,
        RoomTeamId selfTeam,
        UUID selfId
    ) {
        if (context.state() != preparedState || !context.room().roomId().equals(preparedRoomId)) {
            prepareRoomSnapshot(context.server(), context.room(), context.state());
        }
        String squadLabel = context.state().getSquadLabelsByPlayer().get(selfId);
        if (squadLabel == null || squadLabel.isBlank()) {
            return List.of();
        }
        return preparedTeammates.getOrDefault(new SquadKey(selfTeam, normalizeSquadLabel(squadLabel)), List.of());
    }

    private static boolean isDeployableTeammate(
        RunningRuntimeState state,
        UUID teammateId,
        ServerPlayer teammate
    ) {
        return teammateId != null
            && teammate != null
            && teammate.isAlive()
            && !teammate.isSpectator()
            && !VehicleCompatFacade.isVehicleEntity(teammate.getVehicle())
            && !state.isInRespawnFlow(teammateId)
            && !state.isPlayerDowned(teammateId)
            && !state.isPlayerPendingDowned(teammateId);
    }

    /** GD656Mods: 创造模式玩家不能作为部署落点 —— 但仍要下发图标, 供客户端显示红色与提示。 */
    private static boolean isCreativeMode(ServerPlayer player) {
        return player != null && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    private void clearPreparedSnapshot() {
        preparedState = null;
        preparedRoomId = "";
        preparedTeammates = Map.of();
    }

    private static String normalizeSquadLabel(String squadLabel) {
        return squadLabel.trim().toLowerCase(Locale.ROOT);
    }

    private record SquadKey(RoomTeamId teamId, String squadLabel) {
    }

    private record TeammateSnapshot(
        UUID playerId,
        RoomPosition position,
        float yaw,
        String name,
        boolean inCombat,
        boolean creative
    ) {
    }
}
