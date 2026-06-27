package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomPosition;

import java.util.List;
import java.util.UUID;

public final class RoomTeammateDeployHudTargetProvider implements RoomDeployHudTargetProvider {
    private static final int MAX_TEAMMATE_ROWS = 12;

    @Override
    public void collectElements(List<RoomDeployHudElement> elements, RoomDeployHudBuildContext context) {
        RoomTeamId selfTeam = context.selfTeam();
        UUID selfId = context.playerId();
        if (selfTeam == null || selfId == null) {
            return;
        }

        long gameTick = context.server().getTickCount();
        int appended = 0;
        for (UUID teammateId : context.state().getRoomPlayers()) {
            if (teammateId.equals(selfId)) {
                continue;
            }
            if (context.state().getRoomTeams().get(teammateId) != selfTeam) {
                continue;
            }
            if (!isSameSquad(context.state(), selfId, teammateId)) {
                continue;
            }
            ServerPlayer teammate = context.server().getPlayerList().getPlayer(teammateId);
            if (!isDeployableTeammate(context, teammateId, teammate, gameTick)) {
                continue;
            }
            boolean teammateCombat = context.state().isPlayerInCombat(teammateId, gameTick);
            elements.add(RoomDeployHudElement.teammate(
                teammateId,
                new RoomPosition(Mth.floor(teammate.getX()), Mth.floor(teammate.getY()), Mth.floor(teammate.getZ())),
                selfTeam,
                teammate.getYRot(),
                teammate.getGameProfile().getName(),
                teammateCombat
            ));
            appended++;
            if (appended >= MAX_TEAMMATE_ROWS) {
                return;
            }
        }
    }

    private boolean isDeployableTeammate(
        RoomDeployHudBuildContext context,
        UUID teammateId,
        ServerPlayer teammate,
        long gameTick
    ) {
        return teammateId != null
            && teammate != null
            && teammate.isAlive()
            && !teammate.isSpectator()
            && !VehicleCompatFacade.isVehicleEntity(teammate.getVehicle())
            && !context.state().isInRespawnFlow(teammateId)
            && !context.state().isPlayerDowned(teammateId)
            && !context.state().isPlayerPendingDowned(teammateId);
    }

    private boolean isSameSquad(org.mods.gd656conquest.server.room.core.RunningRuntimeState state, UUID selfId, UUID teammateId) {
        if (state == null || selfId == null || teammateId == null) {
            return false;
        }
        String own = state.getSquadLabelsByPlayer().get(selfId);
        String other = state.getSquadLabelsByPlayer().get(teammateId);
        return own != null && !own.isBlank() && own.equalsIgnoreCase(other);
    }
}
