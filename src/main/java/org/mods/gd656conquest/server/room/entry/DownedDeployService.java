package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.DownedDeployPacket;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerRespawnService;

/** Shares the original service's calculation, including gameplay mode overrides and existing penalties. */
public final class DownedDeployService {
    private DownedDeployService() {}
    public static void publish(RoomPlayerRespawnService service, ServerPlayer player, RunningRuntimeState state, int deathTicks) {
        if (player == null || state == null || !state.isPlayerDowned(player.getUUID())) return;
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(player.server);
        var room = manager.findPlayerRoomId(player.getUUID()).flatMap(manager::findRoom).orElse(null);
        long id = RoomDeathCauseService.deathId(player);
        if (room == null || id <= 0) return;
        int ticks = service.downedDeployDelay(room, state, player.getUUID(), player.server.getTickCount());
        if (state.getDoubledRespawnPending().contains(player.getUUID())) ticks *= 2;
        NetworkManager.sendToPlayer(player, new DownedDeployPacket(id, player.getId(),
            player.level().dimension().location().toString(), room.commonConfig().enableRespawnCountdownWhileDowned(),
            Math.max(0, ticks), Math.max(0, deathTicks)));
    }
}
