package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.network.clientbridge.ClientPacketBridgeHolder;

import java.util.function.Supplier;

public final class SupportUiOpenPacket {
    public static void encode(SupportUiOpenPacket packet, FriendlyByteBuf buffer) {
    }

    public static SupportUiOpenPacket decode(FriendlyByteBuf buffer) {
        return new SupportUiOpenPacket();
    }

    public static void handle(SupportUiOpenPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketBridgeHolder.get().openSupportUi()));
        context.setPacketHandled(true);
    }
}
