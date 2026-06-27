package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.hud.ClientHudDebugPreviewState;

import java.util.function.Supplier;

public final class HudDebugPreviewPacket {
    private final String mode;
    private final int durationMs;

    public HudDebugPreviewPacket(String mode, int durationMs) {
        this.mode = mode == null || mode.isBlank() ? "CONQUEST" : mode.trim().toUpperCase(java.util.Locale.ROOT);
        this.durationMs = Math.max(1000, durationMs);
    }

    public static void encode(HudDebugPreviewPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.mode);
        buffer.writeVarInt(message.durationMs);
    }

    public static HudDebugPreviewPacket decode(FriendlyByteBuf buffer) {
        return new HudDebugPreviewPacket(buffer.readUtf(), buffer.readVarInt());
    }

    public static void handle(HudDebugPreviewPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> ClientHudDebugPreviewState.startPreview(message.mode, message.durationMs)
        ));
        context.setPacketHandled(true);
    }
}
