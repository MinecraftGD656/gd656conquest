package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ClientLoopSoundPacket;
import org.mods.gd656conquest.network.packet.ClientShortSoundPacket;
import org.mods.gd656conquest.server.compat.frontlinevoice.FrontlineVoiceBridge;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;

import java.util.Objects;
import java.util.UUID;

public final class RoomAudioRuntimeService {
    private final MinecraftServer server;

    public RoomAudioRuntimeService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void playOneShotShortSoundToRoomPlayers(RunningRuntimeState state, String soundId, float volume, float pitch) {
        if (state == null || soundId == null || soundId.isBlank()) {
            return;
        }
        ClientShortSoundPacket packet = ClientShortSoundPacket.oneShot(soundId, Math.max(0.0F, volume), pitch);
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                NetworkManager.sendToPlayer(player, packet);
            }
        }
    }

    public void playLoopSoundToPlayer(ServerPlayer player, String soundId, float volume, int fadeInTicks) {
        if (player == null || soundId == null || soundId.isBlank()) {
            return;
        }
        NetworkManager.sendToPlayer(player, ClientLoopSoundPacket.play(soundId, Math.max(0.0F, volume), Math.max(0, fadeInTicks)));
    }

    public void stopLoopSoundToPlayer(ServerPlayer player, String soundId, int fadeOutTicks) {
        if (player == null || soundId == null || soundId.isBlank()) {
            return;
        }
        NetworkManager.sendToPlayer(player, ClientLoopSoundPacket.stop(soundId, Math.max(0, fadeOutTicks)));
    }

    public void emitPlayerThirdPersonCue(RoomDefinition room, RunningRuntimeState state, ServerPlayer sourcePlayer, String cueId) {
        if (room == null || state == null || sourcePlayer == null || cueId == null || cueId.isBlank()) {
            return;
        }
        if (!state.getRoomPlayers().contains(sourcePlayer.getUUID())) {
            return;
        }
        RoomTeamId teamId = state.getRoomTeams().get(sourcePlayer.getUUID());
        emitResolvedThirdPersonCue(room, teamId, sourcePlayer, cueId);
    }

    private void emitResolvedThirdPersonCue(RoomDefinition room, RoomTeamId teamId, ServerPlayer sourcePlayer, String cueId) {
        String normalizedCueId = cueId.trim().toLowerCase(java.util.Locale.ROOT);
        if (RoomThirdPersonAudioCueRegistry.PLAYER_REVIVE_ASSAULT_START.equals(normalizedCueId)) {
            FrontlineVoiceBridge.playThirdPersonSound(sourcePlayer, "gd656conquest:sfx.revive_defibrillator_start", 1.0F, 1.0F, 24.0D);
            return;
        }
        if (RoomThirdPersonAudioCueRegistry.PLAYER_REVIVE_SQUAD_START.equals(normalizedCueId)) {
            FrontlineVoiceBridge.playThirdPersonSound(sourcePlayer, "gd656conquest:sfx.revive_syringe_start", 1.0F, 1.0F, 24.0D);
            return;
        }
        String frontlineVoiceCueId = resolveFrontlineVoiceCueId(normalizedCueId);
        if (frontlineVoiceCueId != null) {
            FrontlineVoiceBridge.playCue(sourcePlayer, frontlineVoiceCueId);
        }
    }

    private static String resolveFrontlineVoiceCueId(String cueId) {
        if (cueId == null || cueId.isBlank()) {
            return null;
        }
        return switch (cueId) {
            case RoomThirdPersonAudioCueRegistry.PLAYER_DEATH -> "DEATH";
            case RoomThirdPersonAudioCueRegistry.PLAYER_BURNING -> "BURNING";
            case RoomThirdPersonAudioCueRegistry.PLAYER_DOWNED_HELP -> "DOWNED_HELP";
            case RoomThirdPersonAudioCueRegistry.PLAYER_TEAMMATE_DOWNED -> "TEAMMATE_DOWNED";
            case RoomThirdPersonAudioCueRegistry.PLAYER_REQUEST_AMMO -> "REQUEST_AMMO";
            case RoomThirdPersonAudioCueRegistry.PLAYER_HEAVY_HURT -> "HEAVY_HURT";
            case RoomThirdPersonAudioCueRegistry.PLAYER_THROW_MEDKIT -> "THROW_MEDKIT";
            case RoomThirdPersonAudioCueRegistry.PLAYER_THROW_AMMO -> "THROW_AMMO";
            case RoomThirdPersonAudioCueRegistry.PLAYER_SPOTTED_ENEMY -> "SPOTTED_ENEMY";
            case RoomThirdPersonAudioCueRegistry.PLAYER_REVIVED -> "REVIVED";
            case RoomThirdPersonAudioCueRegistry.PLAYER_GOGOGO -> "GOGOGO";
            case RoomThirdPersonAudioCueRegistry.PLAYER_NICE_SHOT -> "NICE_SHOT";
            case RoomThirdPersonAudioCueRegistry.PLAYER_KILL_ENEMY -> "KILL_ENEMY";
            case RoomThirdPersonAudioCueRegistry.PLAYER_SPOTTED_ENGINEER -> "SPOTTED_ENGINEER";
            case RoomThirdPersonAudioCueRegistry.PLAYER_SPOTTED_RECON -> "SPOTTED_RECON";
            case RoomThirdPersonAudioCueRegistry.PLAYER_SPOTTED_SUPPORT -> "SPOTTED_SUPPORT";
            case RoomThirdPersonAudioCueRegistry.PLAYER_REVIVING -> "REVIVING";
            case RoomThirdPersonAudioCueRegistry.PLAYER_THANKS -> "THANKS";
            default -> cueId;
        };
    }
}
