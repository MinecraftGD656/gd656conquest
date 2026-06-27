package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.core.command.ScreenshotServerHandler;

import java.util.function.Supplier;

public class ServerboundScreenshotChunkPacket {
    private final String sessionToken;
    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] data;

    public ServerboundScreenshotChunkPacket(String sessionToken, int chunkIndex, int totalChunks, byte[] data) {
        this.sessionToken = sessionToken;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.data = data;
    }

    public static void encode(ServerboundScreenshotChunkPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.sessionToken);
        buffer.writeInt(packet.chunkIndex);
        buffer.writeInt(packet.totalChunks);
        buffer.writeByteArray(packet.data);
    }

    public static ServerboundScreenshotChunkPacket decode(FriendlyByteBuf buffer) {
        return new ServerboundScreenshotChunkPacket(
            buffer.readUtf(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readByteArray()
        );
    }

    public static void handle(ServerboundScreenshotChunkPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> {
                ScreenshotServerHandler.handleChunk(sender, packet.sessionToken, packet.chunkIndex, packet.totalChunks, packet.data);
            });
        }
        context.setPacketHandled(true);
    }
}
