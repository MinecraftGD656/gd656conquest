package org.mods.gd656conquest.network.packet;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.room.ClientDownedRagdollState;

/** Presentation ownership, separate from the physical poses streamed by Ragdollified. */
public final class DownedRagdollStatePacket {
    public static final int MAX_SPAWN_BYTES = 262144;
    private final UUID playerId;
    private final int entityId;
    private final long generation;
    private final String dimension;
    private final boolean active;
    private final Vec3 anchor;
    private final byte[] spawn;
    private final UUID streamOwner;

    public DownedRagdollStatePacket(UUID playerId, int entityId, long generation,
                                   String dimension, boolean active, Vec3 anchor) {
        this(playerId, entityId, generation, dimension, active, anchor, new byte[0], null);
    }

    public DownedRagdollStatePacket(UUID playerId, int entityId, long generation,
                                   String dimension, boolean active, Vec3 anchor, byte[] spawn, UUID streamOwner) {
        if (spawn == null || spawn.length > MAX_SPAWN_BYTES) throw new IllegalArgumentException("Invalid ragdoll spawn payload");
        this.playerId = playerId;
        this.entityId = entityId;
        this.generation = generation;
        this.dimension = dimension;
        this.active = active;
        this.anchor = anchor;
        this.spawn = spawn.clone();
        this.streamOwner = streamOwner;
    }

    public static void encode(DownedRagdollStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerId);
        buffer.writeVarInt(packet.entityId);
        buffer.writeLong(packet.generation);
        buffer.writeUtf(packet.dimension);
        buffer.writeBoolean(packet.active);
        buffer.writeDouble(packet.anchor.x);
        buffer.writeDouble(packet.anchor.y);
        buffer.writeDouble(packet.anchor.z);
        buffer.writeByteArray(packet.spawn);
        buffer.writeBoolean(packet.streamOwner != null);
        if (packet.streamOwner != null) buffer.writeUUID(packet.streamOwner);
    }

    public static DownedRagdollStatePacket decode(FriendlyByteBuf buffer) {
        UUID playerId = buffer.readUUID();
        int entityId = buffer.readVarInt();
        long generation = buffer.readLong();
        String dimension = buffer.readUtf();
        boolean active = buffer.readBoolean();
        Vec3 anchor = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        byte[] spawn = buffer.readByteArray(MAX_SPAWN_BYTES);
        UUID owner = buffer.readBoolean() ? buffer.readUUID() : null;
        return new DownedRagdollStatePacket(playerId, entityId, generation, dimension, active, anchor, spawn, owner);
    }

    public static void handle(DownedRagdollStatePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientDownedRagdollState.accept(packet.playerId, packet.entityId, packet.generation,
                    packet.dimension, packet.active, packet.anchor, packet.spawn, packet.streamOwner)));
        }
        context.setPacketHandled(true);
    }
}
