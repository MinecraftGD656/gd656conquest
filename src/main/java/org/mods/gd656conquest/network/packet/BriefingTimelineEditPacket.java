package org.mods.gd656conquest.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record BriefingTimelineEditPacket(String roomId, boolean save, String revision, String json) {
    public static void encode(BriefingTimelineEditPacket p,FriendlyByteBuf b) { b.writeUtf(p.roomId,128);b.writeBoolean(p.save);b.writeUtf(p.revision,128);b.writeUtf(p.json,262144); }
    public static BriefingTimelineEditPacket decode(FriendlyByteBuf b) { return new BriefingTimelineEditPacket(b.readUtf(128),b.readBoolean(),b.readUtf(128),b.readUtf(262144)); }
    public static void handle(BriefingTimelineEditPacket p,Supplier<NetworkEvent.Context> s) {
        var c=s.get();c.enqueueWork(() -> org.mods.gd656conquest.server.room.gameplay.breakthrough.BriefingTimelineEditorService.handle(c.getSender(),p));c.setPacketHandled(true);
    }
}
