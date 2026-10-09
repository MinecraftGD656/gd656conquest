package org.mods.gd656conquest.server.room.runtime.audio;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ClientPositionalSoundPacket;
import org.mods.gd656conquest.network.packet.ClientShortSoundPacket;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;

import java.util.Objects;
import java.util.UUID;

public final class RoomDistanceAudioService {
    private final MinecraftServer server;

    public RoomDistanceAudioService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void playHorizontalRadiusSound(
        RunningRuntimeState state,
        ResourceKey<Level> dimension,
        Vec3 origin,
        String soundId,
        double horizontalRadius,
        float maxVolume,
        float pitch
    ) {
        if (state == null || dimension == null || origin == null || soundId == null || soundId.isBlank() || horizontalRadius <= 0.0D || maxVolume <= 0.0F) {
            return;
        }
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            float volume = resolveHorizontalVolume(player, dimension, origin, horizontalRadius, maxVolume);
            if (volume <= 0.0F) {
                continue;
            }
            NetworkManager.sendToPlayer(player, ClientShortSoundPacket.oneShot(soundId, volume, pitch));
        }
    }

    public void playPositionalRadiusSound(
        Iterable<ServerPlayer> players,
        ResourceKey<Level> dimension,
        Vec3 origin,
        String soundId,
        double radius,
        float maxVolume,
        float pitch
    ) {
        if (players == null || dimension == null || origin == null || soundId == null || soundId.isBlank() || radius <= 0.0D || maxVolume <= 0.0F) {
            return;
        }
        for (ServerPlayer player : players) {
            float volume = resolveSphericalVolume(player, dimension, origin, radius, maxVolume);
            if (volume <= 0.0F) {
                continue;
            }
            NetworkManager.sendToPlayer(player, ClientPositionalSoundPacket.oneShot(soundId, volume, pitch, origin.x, origin.y, origin.z));
        }
    }

    public float resolveHorizontalVolume(
        ServerPlayer player,
        ResourceKey<Level> dimension,
        Vec3 origin,
        double horizontalRadius,
        float maxVolume
    ) {
        if (player == null || dimension == null || origin == null || horizontalRadius <= 0.0D || maxVolume <= 0.0F) {
            return 0.0F;
        }
        if (player.level() == null || !dimension.equals(player.serverLevel().dimension())) {
            return 0.0F;
        }
        double dx = player.getX() - origin.x;
        double dz = player.getZ() - origin.z;
        double distanceSq = dx * dx + dz * dz;
        double radiusSq = horizontalRadius * horizontalRadius;
        if (distanceSq >= radiusSq) {
            return 0.0F;
        }
        double distance = Math.sqrt(distanceSq);
        return (float) (maxVolume * Math.max(0.0D, 1.0D - (distance / horizontalRadius)));
    }

    public float resolveSphericalVolume(
        ServerPlayer player,
        ResourceKey<Level> dimension,
        Vec3 origin,
        double radius,
        float maxVolume
    ) {
        if (player == null || dimension == null || origin == null || radius <= 0.0D || maxVolume <= 0.0F) {
            return 0.0F;
        }
        if (player.level() == null || !dimension.equals(player.serverLevel().dimension())) {
            return 0.0F;
        }
        double distanceSq = player.position().distanceToSqr(origin);
        double radiusSq = radius * radius;
        if (distanceSq >= radiusSq) {
            return 0.0F;
        }
        double distance = Math.sqrt(distanceSq);
        return (float) (maxVolume * Math.max(0.0D, 1.0D - (distance / radius)));
    }
}
