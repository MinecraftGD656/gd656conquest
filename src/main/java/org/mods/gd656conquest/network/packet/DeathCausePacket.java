package org.mods.gd656conquest.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.common.player.DeathCause;
import org.mods.gd656conquest.client.room.ClientDeathCauseState;

/** Victim-only presentation metadata; contains no raw damage type for the UI to reinterpret. */
public record DeathCausePacket(long deathId, int entityId, String dimension, DeathCause cause) {
    public DeathCausePacket {
        if (deathId <= 0 || entityId < 0 || dimension == null || dimension.length() > 256)
            throw new IllegalArgumentException("Invalid death cause packet");
        if (cause == null) cause = DeathCause.KILLED_IN_ACTION;
    }
    public static void encode(DeathCausePacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.deathId()); buffer.writeVarInt(packet.entityId());
        buffer.writeUtf(packet.dimension()); buffer.writeByte(packet.cause().wireId());
    }
    public static DeathCausePacket decode(FriendlyByteBuf buffer) {
        return new DeathCausePacket(buffer.readLong(), buffer.readVarInt(), buffer.readUtf(256),
            DeathCause.fromWireId(buffer.readUnsignedByte()));
    }
    public static void handle(DeathCausePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT)
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDeathCauseState.accept(packet)));
        context.setPacketHandled(true);
    }
}
