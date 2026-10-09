package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.config.ClientPacketBypassConfig;
import org.mods.gd656conquest.client.support.ClientSupportUiState;
import org.mods.gd656conquest.client.support.SupportScreen;
import org.mods.gd656conquest.network.clientbridge.ClientPacketBridgeHolder;

import net.minecraft.client.Minecraft;
import java.util.function.Supplier;

public final class SupportUiOpenPacket {
    public static void encode(SupportUiOpenPacket packet, FriendlyByteBuf buffer) {
    }

    public static SupportUiOpenPacket decode(FriendlyByteBuf buffer) {
        return new SupportUiOpenPacket();
    }

    public static void handle(SupportUiOpenPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (ClientPacketBypassConfig.bypassSupportUiPackets()) {
                ClientSupportUiState.clear();
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null && minecraft.screen instanceof SupportScreen supportScreen) {
                    supportScreen.onClose();
                }
                return;
            }
            ClientPacketBridgeHolder.get().openSupportUi();
        }));
        context.setPacketHandled(true);
    }
}
