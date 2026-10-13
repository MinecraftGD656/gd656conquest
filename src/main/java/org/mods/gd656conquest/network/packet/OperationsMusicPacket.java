package org.mods.gd656conquest.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.common.audio.OperationsMusicSnapshot;

public record OperationsMusicPacket(OperationsMusicSnapshot snapshot) {
    public static void encode(OperationsMusicPacket packet, FriendlyByteBuf b) {
        var s = packet.snapshot;
        b.writeUtf(s.roomId(), 128); b.writeLong(s.matchId()); b.writeLong(s.serverTick()); b.writeEnum(s.mode());
        b.writeInt(s.battalion()); b.writeInt(s.sector()); b.writeInt(s.roundVariant()); b.writeLong(s.roundStartTick());
        b.writeLong(s.resultSequence()); b.writeInt(s.finaleLevel()); b.writeBoolean(s.strongFinale());
        b.writeBoolean(s.terminal()); b.writeBoolean(s.victory()); b.writeInt(s.resultElapsedTicks());
        b.writeInt(s.advanceVariant());
    }
    public static OperationsMusicPacket decode(FriendlyByteBuf b) {
        return new OperationsMusicPacket(new OperationsMusicSnapshot(b.readUtf(128), b.readLong(), b.readLong(),
            b.readEnum(OperationsMusicSnapshot.Mode.class), b.readInt(), b.readInt(), b.readInt(), b.readLong(),
            b.readLong(), b.readInt(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readInt(), b.readInt()));
    }
    public static void handle(OperationsMusicPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> org.mods.gd656conquest.client.audio.ClientOperationsMusicDirector.accept(packet.snapshot)));
        context.setPacketHandled(true);
    }
}
