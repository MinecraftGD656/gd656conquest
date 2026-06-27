package org.mods.gd656conquest.server.room.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.common.log.ClientMessageType;
import org.mods.gd656conquest.server.shared.log.ServerLogger;

import java.util.Collection;
import java.util.UUID;

public final class RoomValidationNoticeService {
    private RoomValidationNoticeService() {
    }

    public static void announceMissingConfig(
            MinecraftServer server,
            String roomId,
            Collection<UUID> roomPlayers,
            String message
    ) {
        if (server != null && roomPlayers != null) {
            for (UUID uuid : roomPlayers) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    ServerLogger.sendToClient(player, ClientMessageType.INFO, message);
                }
            }
        }
        ServerLogger.failure("房间 %s 配置缺失: %s", roomId, message);
    }
}

