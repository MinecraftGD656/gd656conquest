package org.mods.gd656conquest.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.room.ClientDownedDeployState;

public record DownedDeployPacket(long deathId, int entityId, String dimension, boolean enabled,
                                int deploymentTicks, int deathTicks, boolean confirmedDeath) {
    public DownedDeployPacket(long id, int entity, String dimension, boolean enabled, int deploymentTicks, int deathTicks) {
        this(id, entity, dimension, enabled, deploymentTicks, deathTicks, false);
    }
    public DownedDeployPacket {
        if (deathId <= 0 || entityId < 0 || dimension == null || dimension.length() > 256 || deploymentTicks < 0 || deathTicks < 0)
            throw new IllegalArgumentException("Invalid downed deployment snapshot");
    }
    public static void encode(DownedDeployPacket p, FriendlyByteBuf b) {
        b.writeLong(p.deathId()); b.writeVarInt(p.entityId()); b.writeUtf(p.dimension());
        b.writeBoolean(p.enabled()); b.writeVarInt(p.deploymentTicks()); b.writeVarInt(p.deathTicks()); b.writeBoolean(p.confirmedDeath());
    }
    public static DownedDeployPacket decode(FriendlyByteBuf b) {
        return new DownedDeployPacket(b.readLong(), b.readVarInt(), b.readUtf(256), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readBoolean());
    }
    public static void handle(DownedDeployPacket p, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT)
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (p.confirmedDeath()) org.mods.gd656conquest.client.room.ClientManualDeathDeployState.accept(p);
                else ClientDownedDeployState.accept(p);
            }));
        context.setPacketHandled(true);
    }
}
