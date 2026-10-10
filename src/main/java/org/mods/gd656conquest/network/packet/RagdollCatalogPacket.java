package org.mods.gd656conquest.network.packet;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.room.ClientRagdollSubscriptions;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSubscriptionPolicy;

/** Discovery only: no equipment, part rotations or physics objects. */
public record RagdollCatalogPacket(String dimension, int serverTick, List<Entry> entries) {
    public record Entry(int id,long token,long generation,boolean removed,boolean moving,boolean mandatory,Vec3 anchor,AABB bounds) {}
    public RagdollCatalogPacket { entries=List.copyOf(entries); if(entries.size()>RagdollSubscriptionPolicy.MAX_DIRECTORY_ENTRIES) throw new IllegalArgumentException("Catalog too large"); }
    public static void encode(RagdollCatalogPacket p,FriendlyByteBuf b) {
        b.writeUtf(p.dimension); b.writeVarInt(p.serverTick); b.writeVarInt(p.entries.size());
        for(Entry e:p.entries) {
            b.writeVarInt(e.id); b.writeLong(e.token); b.writeLong(e.generation);
            b.writeBoolean(e.removed); b.writeBoolean(e.moving); b.writeBoolean(e.mandatory);
            b.writeDouble(e.anchor.x); b.writeDouble(e.anchor.y); b.writeDouble(e.anchor.z);
            b.writeFloat((float)(e.bounds.minX-e.anchor.x)); b.writeFloat((float)(e.bounds.minY-e.anchor.y)); b.writeFloat((float)(e.bounds.minZ-e.anchor.z));
            b.writeFloat((float)(e.bounds.maxX-e.anchor.x)); b.writeFloat((float)(e.bounds.maxY-e.anchor.y)); b.writeFloat((float)(e.bounds.maxZ-e.anchor.z));
        }
    }
    public static RagdollCatalogPacket decode(FriendlyByteBuf b) {
        String dimension=b.readUtf(); int tick=b.readVarInt(), count=b.readVarInt();
        if(count<0||count>RagdollSubscriptionPolicy.MAX_DIRECTORY_ENTRIES) throw new IllegalArgumentException("Invalid catalog size");
        List<Entry> entries=new ArrayList<>(count);
        for(int i=0;i<count;i++) {
            int id=b.readVarInt(); long token=b.readLong(),generation=b.readLong(); boolean removed=b.readBoolean(),moving=b.readBoolean(),mandatory=b.readBoolean();
            Vec3 p=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());
            AABB box=new AABB(p.x+b.readFloat(),p.y+b.readFloat(),p.z+b.readFloat(),p.x+b.readFloat(),p.y+b.readFloat(),p.z+b.readFloat());
            if(!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||!finite(box)) throw new IllegalArgumentException("Invalid body bounds");
            entries.add(new Entry(id,token,generation,removed,moving,mandatory,p,box));
        }
        return new RagdollCatalogPacket(dimension,tick,entries);
    }
    private static boolean finite(AABB b) {return Double.isFinite(b.minX)&&Double.isFinite(b.minY)&&Double.isFinite(b.minZ)&&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ);}
    public static void handle(RagdollCatalogPacket p,Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context c=s.get(); c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientRagdollSubscriptions.catalog(p))); c.setPacketHandled(true);
    }
}
