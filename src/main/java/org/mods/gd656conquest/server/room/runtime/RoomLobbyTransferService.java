package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.mods.gd656conquest.server.player.state.PlayerSessionStateService;
import org.mods.gd656conquest.server.player.state.HubDimensionService;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessInventoryPolicyService;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class RoomLobbyTransferService {
    private static final int LOBBY_RANDOM_RADIUS = 2;

    private final MinecraftServer server;

    public RoomLobbyTransferService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void transferToLobby(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ServerLevel hubLevel = HubDimensionService.resolveHubLevel(server);
        if (hubLevel == null) {
            return;
        }
        BlockPos spawn = resolveLobbySpawn();
        player.teleportTo(hubLevel, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, player.getYRot(), player.getXRot());
        player.setRespawnPosition(hubLevel.dimension(), spawn, player.getYRot(), true, false);
        applyLobbyGameMode(player);
        player.setInvulnerable(ConquestDataManager.of(server).serverConfig().isOverworldInvulnerableEnabled());
        PlayerSessionStateService.maintainLobbyHealth(player);
        QuickAccessInventoryPolicyService.sanitizeLobbyInventory(player);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    private BlockPos resolveLobbySpawn() {
        var base = ConquestDataManager.of(server).serverConfig().getOverworldSpawnFallbackShared();
        int randomX = base.x() + ThreadLocalRandom.current().nextInt(-LOBBY_RANDOM_RADIUS, LOBBY_RANDOM_RADIUS + 1);
        int randomZ = base.z() + ThreadLocalRandom.current().nextInt(-LOBBY_RANDOM_RADIUS, LOBBY_RANDOM_RADIUS + 1);
        return new BlockPos(randomX, (int) Math.round(base.y()), randomZ);
    }

    private void applyLobbyGameMode(ServerPlayer player) {
        if (player == null || player.hasPermissions(2)) {
            return;
        }
        if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            player.setGameMode(GameType.ADVENTURE);
        }
    }
}
