package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Reports whether the quick-action item actually did something, so the lobby can close itself. */
public final class QuickAccessResultPacket {
    private final boolean success;

    public QuickAccessResultPacket(boolean success) {
        this.success = success;
    }

    public boolean success() {
        return this.success;
    }

    public static void encode(QuickAccessResultPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.success);
    }

    public static QuickAccessResultPacket decode(FriendlyByteBuf buffer) {
        return new QuickAccessResultPacket(buffer.readBoolean());
    }

    public static void handle(QuickAccessResultPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> org.mods.gd656conquest.client.loadout.ConquestLobbyProvider.onQuickJoinResult(message.success)));
        context.setPacketHandled(true);
    }
}
