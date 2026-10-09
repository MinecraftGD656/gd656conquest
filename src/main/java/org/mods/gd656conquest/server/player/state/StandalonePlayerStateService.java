package org.mods.gd656conquest.server.player.state;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.RoomLobbyTransferService;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;

public final class StandalonePlayerStateService {
    private StandalonePlayerStateService() {
    }

    public static void enforceStandaloneWorldPolicy(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        if (RoomCoreRuntimeManager.of(player.server).findPlayerRoomId(player.getUUID()).isPresent()) {
            return;
        }
        var config = ConquestDataManager.of(player.server).serverConfig();
        boolean protectedStandalone = shouldCancelStandaloneDamage(player);
        if (HubDimensionService.isHubDimension(player.level())) {
            RangeRuntimeManager rangeManager = RangeRuntimeManager.of(player.server);
            if (rangeManager.isInRange(player.getUUID()) && player.getY() < -64.0D) {
                rangeManager.returnPlayerToJoinSpawn(player);
                return;
            }
            if (!player.hasPermissions(2) && player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                player.setGameMode(GameType.ADVENTURE);
            }
            if (player.getY() < -64.0D) {
                new RoomLobbyTransferService(player.server).transferToLobby(player);
                return;
            }
            player.setInvulnerable(protectedStandalone);
            return;
        }
        player.setInvulnerable(false);
        ResourceLocation dimensionId = player.level().dimension().location();
        if (dimensionId == null) {
            return;
        }
        if (HubDimensionService.isConquestDimension(player.level())) {
            if (!config.isConquestDimensionRestricted()) {
                return;
            }
            new RoomLobbyTransferService(player.server).transferToLobby(player);
            ServerMessageSender.system(player, "房间", "当前维度仅允许通过房间流程进入，你已被传回大厅", ChatFormatting.YELLOW);
            return;
        }
        new RoomLobbyTransferService(player.server).transferToLobby(player);
    }

    public static boolean shouldCancelStandaloneDamage(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        if (RoomCoreRuntimeManager.of(player.server).findPlayerRoomId(player.getUUID()).isPresent()) {
            return false;
        }
        return HubDimensionService.isHubDimension(player.level())
            || RangeRuntimeManager.of(player.server).isInRange(player.getUUID());
    }
}
