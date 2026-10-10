package org.mods.gd656conquest.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.common.player.KillerCardInfo;
import org.mods.gd656conquest.client.room.ClientKillerCardState;

/** Victim-only snapshot, correlated with DeathCausePacket by death ID, dimension and victim entity ID. */
public record KillerCardPacket(long deathId, int entityId, String dimension, KillerCardInfo info) {
    public KillerCardPacket {
        if (deathId <= 0 || entityId < 0 || dimension == null || dimension.length() > 256 || info == null)
            throw new IllegalArgumentException("Invalid killer card packet");
    }
    public static void encode(KillerCardPacket p, FriendlyByteBuf b) {
        b.writeLong(p.deathId()); b.writeVarInt(p.entityId()); b.writeUtf(p.dimension());
        KillerCardInfo i = p.info();
        b.writeUUID(i.killerId()); b.writeUtf(i.name()); b.writeVarInt(i.level());
        b.writeFloat(i.health()); b.writeFloat(i.maxHealth()); b.writeVarInt(i.playerKills()); b.writeVarInt(i.enemyKills());
        b.writeUtf(i.weaponName()); b.writeUtf(i.weaponId()); b.writeUtf(i.weaponDisplayId());
        b.writeBoolean(i.headshot());
    }
    public static KillerCardPacket decode(FriendlyByteBuf b) {
        long id = b.readLong(); int entity = b.readVarInt(); String dimension = b.readUtf(256);
        KillerCardInfo info = new KillerCardInfo(b.readUUID(), b.readUtf(128), b.readVarInt(),
            b.readFloat(), b.readFloat(), b.readVarInt(), b.readVarInt(), b.readUtf(256), b.readUtf(256), b.readUtf(256), b.readBoolean());
        return new KillerCardPacket(id, entity, dimension, info);
    }
    public static void handle(KillerCardPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT)
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientKillerCardState.accept(packet)));
        context.setPacketHandled(true);
    }
}
