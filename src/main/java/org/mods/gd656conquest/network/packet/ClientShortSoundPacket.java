package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.audio.ClientAudioManager;

import java.util.function.Supplier;

public final class ClientShortSoundPacket {
    private final String soundId;
    private final float volume;
    private final float pitch;

    public ClientShortSoundPacket(String soundId, float volume, float pitch) {
        this.soundId = soundId == null ? "" : soundId;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static ClientShortSoundPacket oneShot(String soundId, float volume, float pitch) {
        return new ClientShortSoundPacket(soundId, volume, pitch);
    }

    public static void encode(ClientShortSoundPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.soundId);
        buffer.writeFloat(packet.volume);
        buffer.writeFloat(packet.pitch);
    }

    public static ClientShortSoundPacket decode(FriendlyByteBuf buffer) {
        return new ClientShortSoundPacket(
            buffer.readUtf(),
            buffer.readFloat(),
            buffer.readFloat()
        );
    }

    public static void handle(ClientShortSoundPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            String cleanSoundId = packet.soundId;
            if (!cleanSoundId.contains(":")) {
                cleanSoundId = "gd656conquest:" + cleanSoundId;
            }
            ClientAudioManager.getInstance().enqueuePlaySound(cleanSoundId, packet.volume, packet.pitch);
        }));
        context.setPacketHandled(true);
    }
}
