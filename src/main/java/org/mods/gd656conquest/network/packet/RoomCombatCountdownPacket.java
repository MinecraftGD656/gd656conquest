package org.mods.gd656conquest.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.common.match.CombatCountdownState.Kind;

/** A zero-duration update clears only the countdown from the named source. */
public record RoomCombatCountdownPacket(Kind kind, int remainingTicks, String text) {
    public static RoomCombatCountdownPacket clear(Kind kind) {
        return new RoomCombatCountdownPacket(kind, 0, "");
    }

    public static void encode(RoomCombatCountdownPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.kind());
        buffer.writeInt(packet.remainingTicks());
        buffer.writeUtf(packet.text(), 256);
    }

    public static RoomCombatCountdownPacket decode(FriendlyByteBuf buffer) {
        return new RoomCombatCountdownPacket(buffer.readEnum(Kind.class), buffer.readInt(), buffer.readUtf(256));
    }

    public static void handle(RoomCombatCountdownPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> org.mods.gd656conquest.client.room.ClientCombatCountdownState.accept(packet)));
        context.setPacketHandled(true);
    }
}
