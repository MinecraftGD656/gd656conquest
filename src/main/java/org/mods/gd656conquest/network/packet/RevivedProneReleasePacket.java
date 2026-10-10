package org.mods.gd656conquest.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.compat.ragdoll.DownedViewService;

/** No target/position supplied by the client; only the sender's current revive can be released. */
public record RevivedProneReleasePacket(long session) {
    public static void encode(RevivedProneReleasePacket p, FriendlyByteBuf b) { b.writeLong(p.session); }
    public static RevivedProneReleasePacket decode(FriendlyByteBuf b) {
        long id = b.readLong();
        if (id <= 0) throw new IllegalArgumentException("Invalid revive session");
        return new RevivedProneReleasePacket(id);
    }
    public static void handle(RevivedProneReleasePacket p, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection() == NetworkDirection.PLAY_TO_SERVER)
            context.enqueueWork(() -> DownedViewService.releaseProne(context.getSender(), p.session));
        context.setPacketHandled(true);
    }
}
