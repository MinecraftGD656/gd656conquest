package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.core.command.ScreenshotServerHandler;

import java.util.function.Supplier;

public class ServerboundScreenshotStatusPacket {
    private final String sessionToken;
    private final boolean success;
    private final String message;

    public ServerboundScreenshotStatusPacket(String sessionToken, boolean success, String message) {
        this.sessionToken = sessionToken == null ? "" : sessionToken;
        this.success = success;
        this.message = message == null ? "" : message;
    }

    public static void encode(ServerboundScreenshotStatusPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.sessionToken);
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.message);
    }

    public static ServerboundScreenshotStatusPacket decode(FriendlyByteBuf buffer) {
        return new ServerboundScreenshotStatusPacket(
            buffer.readUtf(),
            buffer.readBoolean(),
            buffer.readUtf()
        );
    }

    public static void handle(ServerboundScreenshotStatusPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> ScreenshotServerHandler.handleStatus(sender, packet.sessionToken, packet.success, packet.message));
        }
        context.setPacketHandled(true);
    }
}
