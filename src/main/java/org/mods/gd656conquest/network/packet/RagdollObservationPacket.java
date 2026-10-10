package org.mods.gd656conquest.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.room.ClientRagdollSubscriptions;

/** A pause is reversible; a removal permanently retires this catalog token. */
public record RagdollObservationPacket(int id,long token,long generation,boolean active,boolean removed) {
    public static void encode(RagdollObservationPacket p,FriendlyByteBuf b){b.writeVarInt(p.id);b.writeLong(p.token);b.writeLong(p.generation);b.writeBoolean(p.active);b.writeBoolean(p.removed);}
    public static RagdollObservationPacket decode(FriendlyByteBuf b){return new RagdollObservationPacket(b.readVarInt(),b.readLong(),b.readLong(),b.readBoolean(),b.readBoolean());}
    public static void handle(RagdollObservationPacket p,Supplier<NetworkEvent.Context> s){NetworkEvent.Context c=s.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientRagdollSubscriptions.observation(p)));c.setPacketHandled(true);}
}
