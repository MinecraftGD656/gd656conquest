package org.mods.gd656conquest.server.room.runtime.audio;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.network.NetworkManager;
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
        double radiusSq = horizontalRadius * horizontalRadius;
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || player.level() == null || !dimension.equals(player.serverLevel().dimension())) {
                continue;
            }
            double dx = player.getX() - origin.x;
            double dz = player.getZ() - origin.z;
            double distanceSq = dx * dx + dz * dz;
            if (distanceSq >= radiusSq) {
                continue;
            }
            double distance = Math.sqrt(distanceSq);
            float volume = (float) (maxVolume * Math.max(0.0D, 1.0D - (distance / horizontalRadius)));
            if (volume <= 0.0F) {
                continue;
            }
            NetworkManager.sendToPlayer(player, ClientShortSoundPacket.oneShot(soundId, volume, pitch));
        }
    }
}
