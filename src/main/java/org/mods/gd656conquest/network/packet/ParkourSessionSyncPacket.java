package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.parkour.ClientParkourRuntimeState;

import java.util.function.Supplier;

public final class ParkourSessionSyncPacket {
    private final boolean inParkour;
    private final boolean challengeActive;
    private final int runNonce;
    private final int startX;
    private final double startY;
    private final int startZ;
    private final int endX;
    private final double endY;
    private final int endZ;

    public ParkourSessionSyncPacket(
        boolean inParkour,
        boolean challengeActive,
        int runNonce,
        int startX,
        double startY,
        int startZ,
        int endX,
        double endY,
        int endZ
    ) {
        this.inParkour = inParkour;
        this.challengeActive = challengeActive;
        this.runNonce = runNonce;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
    }

    public boolean inParkour() {
        return inParkour;
    }

    public boolean challengeActive() {
        return challengeActive;
    }

    public int runNonce() {
        return runNonce;
    }

    public int startX() {
        return startX;
    }

    public double startY() {
        return startY;
    }

    public int startZ() {
        return startZ;
    }

    public int endX() {
        return endX;
    }

    public double endY() {
        return endY;
    }

    public int endZ() {
        return endZ;
    }

    public static void encode(ParkourSessionSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.inParkour);
        buffer.writeBoolean(packet.challengeActive);
        buffer.writeInt(packet.runNonce);
        buffer.writeInt(packet.startX);
        buffer.writeDouble(packet.startY);
        buffer.writeInt(packet.startZ);
        buffer.writeInt(packet.endX);
        buffer.writeDouble(packet.endY);
        buffer.writeInt(packet.endZ);
    }

    public static ParkourSessionSyncPacket decode(FriendlyByteBuf buffer) {
        return new ParkourSessionSyncPacket(
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readDouble(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readDouble(),
            buffer.readInt()
        );
    }

    public static void handle(ParkourSessionSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientParkourRuntimeState.apply(message)));
        context.setPacketHandled(true);
    }
}
