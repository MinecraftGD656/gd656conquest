package org.mods.gd656conquest.network.packet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import org.mods.gd656conquest.client.audio.ClientAudioManager;

import java.util.function.Supplier;

public final class ClientPositionalSoundPacket {
    private final String soundId;
    private final float volume;
    private final float pitch;
    private final double x;
    private final double y;
    private final double z;

    public ClientPositionalSoundPacket(String soundId, float volume, float pitch, double x, double y, double z) {
        this.soundId = soundId == null ? "" : soundId;
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static ClientPositionalSoundPacket oneShot(String soundId, float volume, float pitch, double x, double y, double z) {
        return new ClientPositionalSoundPacket(soundId, volume, pitch, x, y, z);
    }

    public static void encode(ClientPositionalSoundPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.soundId);
        buffer.writeFloat(packet.volume);
        buffer.writeFloat(packet.pitch);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
    }

    public static ClientPositionalSoundPacket decode(FriendlyByteBuf buffer) {
        return new ClientPositionalSoundPacket(
            buffer.readUtf(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble()
        );
    }

    public static void handle(ClientPositionalSoundPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            String cleanSoundId = packet.soundId;
            if (!cleanSoundId.contains(":")) {
                cleanSoundId = "gd656conquest:" + cleanSoundId;
            }
            ClientAudioManager.getInstance().enqueuePlaySoundAt(cleanSoundId, packet.volume, packet.pitch, packet.x, packet.y, packet.z);
        }));
        context.setPacketHandled(true);
    }
}
