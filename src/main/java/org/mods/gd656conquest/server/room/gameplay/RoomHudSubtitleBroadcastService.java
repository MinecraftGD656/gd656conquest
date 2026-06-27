package org.mods.gd656conquest.server.room.gameplay;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomHudSubtitlePacket;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RoomHudSubtitleBroadcastService {
    public void broadcastPerspectiveOnce(
        RunningRuntimeState runningState,
        String key,
        RoomTeamId alliedTeam,
        String alliedText,
        String enemyText
    ) {
        if (runningState == null || alliedTeam == null) {
            return;
        }
        RoomTeamId enemyTeam = alliedTeam == RoomTeamId.CAMP_A ? RoomTeamId.CAMP_B : RoomTeamId.CAMP_A;
        for (UUID playerId : List.copyOf(runningState.getRoomPlayers())) {
            ServerPlayer player = resolvePlayer(playerId);
            if (player == null) {
                continue;
            }
            RoomTeamId playerTeam = runningState.getRoomTeams().get(playerId);
            String text = playerTeam == alliedTeam ? alliedText : playerTeam == enemyTeam ? enemyText : "";
            if (text == null || text.isBlank()) {
                continue;
            }
            NetworkManager.sendToPlayer(player, RoomHudSubtitlePacket.showOnce(key, text));
        }
    }

    private ServerPlayer resolvePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerId);
    }
}
