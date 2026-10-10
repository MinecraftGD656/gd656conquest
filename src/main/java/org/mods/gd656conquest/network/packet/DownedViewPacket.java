package org.mods.gd656conquest.network.packet;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.room.ClientDownedCamera;
import org.mods.gd656conquest.common.player.DownedCameraMode;

/** Only sent to the downed player; killer tracking does not extend team markers. */
public record DownedViewPacket(Action action, long session, int sequence, int entityId, String dimension,
                               DownedCameraMode mode, float yaw, Vec3 position,
                               UUID killerId, Vec3 killerPosition, boolean tracking) {
    public enum Action { START, UPDATE, PREPARE_REVIVE, REVIVED, END, PRONE_RELEASED, START_DEATH }
    public DownedViewPacket {
        if (action == null || mode == null || session <= 0 || sequence < 0 || entityId < 0
            || dimension == null || dimension.length() > 256 || !Float.isFinite(yaw)
            || !finite(position) || !finite(killerPosition)) throw new IllegalArgumentException("Invalid downed view");
    }
    private static boolean finite(Vec3 p) {
        return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }
    public static void encode(DownedViewPacket p, FriendlyByteBuf b) {
        b.writeByte(p.action.ordinal()); b.writeLong(p.session); b.writeVarInt(p.sequence); b.writeVarInt(p.entityId);
        b.writeUtf(p.dimension); b.writeByte(p.mode.ordinal()); b.writeFloat(p.yaw);
        writePosition(b, p.position); b.writeBoolean(p.killerId != null);
        if (p.killerId != null) b.writeUUID(p.killerId);
        writePosition(b, p.killerPosition); b.writeBoolean(p.tracking);
    }
    public static DownedViewPacket decode(FriendlyByteBuf b) {
        int action = b.readUnsignedByte(); long session = b.readLong(); int sequence = b.readVarInt(), entity = b.readVarInt();
        String dimension = b.readUtf(256); int mode = b.readUnsignedByte(); float yaw = b.readFloat();
        Vec3 position = readPosition(b); UUID killer = b.readBoolean() ? b.readUUID() : null;
        Vec3 target = readPosition(b); boolean tracking = b.readBoolean();
        if (action >= Action.values().length || mode >= DownedCameraMode.values().length)
            throw new IllegalArgumentException("Unknown downed view action/mode");
        return new DownedViewPacket(Action.values()[action], session, sequence, entity, dimension,
            DownedCameraMode.values()[mode], yaw, position, killer, target, tracking);
    }
    private static void writePosition(FriendlyByteBuf b, Vec3 p) {
        b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
    }
    private static Vec3 readPosition(FriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }
    public static void handle(DownedViewPacket p, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT)
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDownedCamera.accept(p)));
        context.setPacketHandled(true);
    }
}
