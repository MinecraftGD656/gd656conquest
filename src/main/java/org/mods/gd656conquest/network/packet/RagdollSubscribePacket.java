package org.mods.gd656conquest.network.packet;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSubscriptions;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSubscriptionPolicy;

public record RagdollSubscribePacket(List<Entry> entries) {
    public record Entry(int id,long token,boolean subscribe,boolean refresh) {
        public Entry(int id,long token,boolean subscribe){this(id,token,subscribe,false);}
    }
    public RagdollSubscribePacket {entries=List.copyOf(entries);if(entries.size()>RagdollSubscriptionPolicy.MAX_REQUEST_ENTRIES)throw new IllegalArgumentException("Subscription batch too large");}
    public static void encode(RagdollSubscribePacket p,FriendlyByteBuf b) {b.writeVarInt(p.entries.size());for(Entry e:p.entries){b.writeVarInt(e.id);b.writeLong(e.token);b.writeBoolean(e.subscribe);b.writeBoolean(e.refresh);}}
    public static RagdollSubscribePacket decode(FriendlyByteBuf b) {
        int count=b.readVarInt();if(count<0||count>RagdollSubscriptionPolicy.MAX_REQUEST_ENTRIES)throw new IllegalArgumentException("Invalid subscription count");
        List<Entry> entries=new ArrayList<>(count);for(int i=0;i<count;i++)entries.add(new Entry(b.readVarInt(),b.readLong(),b.readBoolean(),b.readBoolean()));return new RagdollSubscribePacket(entries);
    }
    public static void handle(RagdollSubscribePacket p,Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context c=s.get();c.enqueueWork(()->RagdollSubscriptions.request(c.getSender(),p));c.setPacketHandled(true);
    }
}
