package org.mods.gd656conquest.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;
public record BriefingTimelineSnapshotPacket(String roomId, boolean open, String revision, String json, String message) {
    public static void encode(BriefingTimelineSnapshotPacket p,FriendlyByteBuf b) { b.writeUtf(p.roomId,128);b.writeBoolean(p.open);b.writeUtf(p.revision,128);b.writeUtf(p.json,262144);b.writeUtf(p.message,1024); }
    public static BriefingTimelineSnapshotPacket decode(FriendlyByteBuf b) { return new BriefingTimelineSnapshotPacket(b.readUtf(128),b.readBoolean(),b.readUtf(128),b.readUtf(262144),b.readUtf(1024)); }
    public static void handle(BriefingTimelineSnapshotPacket p,Supplier<NetworkEvent.Context> s) {
        var c=s.get();c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> org.mods.gd656conquest.client.loadout.screen.BriefingTimelineScreen.receive(p)));c.setPacketHandled(true);
    }
}
