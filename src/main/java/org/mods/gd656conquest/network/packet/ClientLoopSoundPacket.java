package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.audio.ClientAudioManager;

import java.util.function.Supplier;

public final class ClientLoopSoundPacket {
    private final Action action;
    private final String soundId;
    private final float volume;
    private final int fadeTicks;

    public ClientLoopSoundPacket(Action action, String soundId, float volume, int fadeTicks) {
        this.action = action == null ? Action.STOP : action;
        this.soundId = soundId == null ? "" : soundId;
        this.volume = volume;
        this.fadeTicks = Math.max(0, fadeTicks);
    }

    public static ClientLoopSoundPacket play(String soundId, float volume, int fadeInTicks) {
        return new ClientLoopSoundPacket(Action.PLAY, soundId, volume, fadeInTicks);
    }

    public static ClientLoopSoundPacket stop(String soundId, int fadeOutTicks) {
        return new ClientLoopSoundPacket(Action.STOP, soundId, 0.0F, fadeOutTicks);
    }

    public static void encode(ClientLoopSoundPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUtf(packet.soundId);
        buffer.writeFloat(packet.volume);
        buffer.writeVarInt(packet.fadeTicks);
    }

    public static ClientLoopSoundPacket decode(FriendlyByteBuf buffer) {
        return new ClientLoopSoundPacket(
            buffer.readEnum(Action.class),
            buffer.readUtf(),
            buffer.readFloat(),
            buffer.readVarInt()
        );
    }

    public static void handle(ClientLoopSoundPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            String cleanSoundId = packet.soundId;
            if (!cleanSoundId.contains(":")) {
                cleanSoundId = "gd656conquest:" + cleanSoundId;
            }
            if (packet.action == Action.PLAY) {
                org.mods.gd656conquest.client.audio.ClientAudioManager.getInstance().enqueuePlayLoopSound(cleanSoundId, packet.volume, packet.fadeTicks);
                org.mods.gd656conquest.server.shared.log.ServerLogger.info("[ClientAudio] Received play loop sound packet: " + cleanSoundId);
            } else {
                org.mods.gd656conquest.client.audio.ClientAudioManager.getInstance().enqueueStopLoopSound(cleanSoundId, packet.fadeTicks);
                org.mods.gd656conquest.server.shared.log.ServerLogger.info("[ClientAudio] Received stop loop sound packet: " + cleanSoundId);
            }
        }));
        context.setPacketHandled(true);
    }

    public enum Action {
        PLAY,
        STOP
    }
}
