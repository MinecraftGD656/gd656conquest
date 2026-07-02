package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.facility.parkour.ParkourRuntimeManager;

import java.util.function.Supplier;

public final class ServerboundParkourFinishPacket {
    private final long elapsedMillis;
    private final int runNonce;

    public ServerboundParkourFinishPacket(long elapsedMillis, int runNonce) {
        this.elapsedMillis = elapsedMillis;
        this.runNonce = runNonce;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public int runNonce() {
        return runNonce;
    }

    public static void encode(ServerboundParkourFinishPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.elapsedMillis);
        buffer.writeInt(packet.runNonce);
    }

    public static ServerboundParkourFinishPacket decode(FriendlyByteBuf buffer) {
        return new ServerboundParkourFinishPacket(buffer.readLong(), buffer.readInt());
    }

    public static void handle(ServerboundParkourFinishPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.server == null) {
                return;
            }
            ParkourRuntimeManager.of(sender.server).handleFinishSubmission(sender, message.elapsedMillis(), message.runNonce());
        });
        context.setPacketHandled(true);
    }
}
