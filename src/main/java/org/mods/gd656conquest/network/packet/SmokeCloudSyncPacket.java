package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.smoke.SmokeCloudStore;
import org.mods.gd656conquest.common.smoke.SmokeCloudInstance;

import java.util.UUID;
import java.util.function.Supplier;

public final class SmokeCloudSyncPacket {
    private final boolean remove;
    private final SmokeCloudInstance instance;
    private final UUID id;
    private final String dimensionId;

    private SmokeCloudSyncPacket(boolean remove, SmokeCloudInstance instance, UUID id, String dimensionId) {
        this.remove = remove;
        this.instance = instance;
        this.id = id;
        this.dimensionId = dimensionId;
    }

    public static SmokeCloudSyncPacket create(SmokeCloudInstance instance) {
        return new SmokeCloudSyncPacket(false, instance, instance.id(), instance.dimensionId());
    }

    public static SmokeCloudSyncPacket remove(UUID id, String dimensionId) {
        return new SmokeCloudSyncPacket(true, null, id, dimensionId);
    }

    public static void encode(SmokeCloudSyncPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.remove);
        buffer.writeUUID(message.id);
        buffer.writeUtf(message.dimensionId);
        if (message.remove) {
            return;
        }
        SmokeCloudInstance instance = message.instance;
        buffer.writeDouble(instance.x());
        buffer.writeDouble(instance.y());
        buffer.writeDouble(instance.z());
        buffer.writeDouble(instance.radiusXZ());
        buffer.writeDouble(instance.radiusY());
        buffer.writeLong(instance.seed());
        buffer.writeLong(instance.startTick());
        buffer.writeLong(instance.endTick());
    }

    public static SmokeCloudSyncPacket decode(FriendlyByteBuf buffer) {
        boolean remove = buffer.readBoolean();
        UUID id = buffer.readUUID();
        String dimensionId = buffer.readUtf();
        if (remove) {
            return remove(id, dimensionId);
        }
        return create(new SmokeCloudInstance(
            id,
            dimensionId,
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readLong()
        ));
    }

    public static void handle(SmokeCloudSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (message.remove) {
                SmokeCloudStore.remove(message.id, message.dimensionId);
            } else {
                SmokeCloudStore.put(message.instance);
            }
        }));
        context.setPacketHandled(true);
    }
}
