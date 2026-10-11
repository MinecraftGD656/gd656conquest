package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.audio.ClientMusicTriggerService;
import org.mods.gd656conquest.client.hud.ClientMatchResultOverlayState;
import org.mods.gd656conquest.common.match.MatchResultType;

import java.util.Objects;
import java.util.function.Supplier;

public final class RoomMatchResultPacket {
    private final boolean operations;
    private final boolean active;
    private final MatchResultType resultType;
    private final int totalTicks;
    private final int blackStartTicks;
    private final int restoreTicks;
    private final String subtitle;

    public RoomMatchResultPacket(boolean active, MatchResultType resultType, int totalTicks, int blackStartTicks, int restoreTicks, String subtitle) {
        this(active, resultType, totalTicks, blackStartTicks, restoreTicks, subtitle, false);
    }

    public RoomMatchResultPacket(boolean active, MatchResultType resultType, int totalTicks, int blackStartTicks, int restoreTicks, String subtitle, boolean operations) {
        this.operations = operations;
        this.active = active;
        this.resultType = Objects.requireNonNull(resultType, "resultType");
        this.totalTicks = totalTicks;
        this.blackStartTicks = blackStartTicks;
        this.restoreTicks = restoreTicks;
        this.subtitle = subtitle == null ? "" : subtitle;
    }

    public boolean operations() { return operations; }
    public int totalTicks() { return totalTicks; }
    public MatchResultType resultType() { return resultType; }

    public static void encode(RoomMatchResultPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.active);
        buffer.writeBoolean(message.operations);
        buffer.writeInt(message.resultType.networkId());
        buffer.writeInt(message.totalTicks);
        buffer.writeInt(message.blackStartTicks);
        buffer.writeInt(message.restoreTicks);
        buffer.writeUtf(message.subtitle);
    }

    public static RoomMatchResultPacket decode(FriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean(), operations = buffer.readBoolean();
        return new RoomMatchResultPacket(
            active,
            MatchResultType.fromNetworkId(buffer.readInt()),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readUtf(), operations
        );
    }

    public static void handle(RoomMatchResultPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (message.active) {
                ClientMusicTriggerService.onMatchResult(message.resultType);
                if (message.operations) ClientMatchResultOverlayState.startOperations(message.resultType, message.totalTicks);
                else ClientMatchResultOverlayState.start(message.resultType, message.totalTicks, message.blackStartTicks, message.restoreTicks, message.subtitle);
            } else {
                ClientMatchResultOverlayState.clear();
            }
        }));
        context.setPacketHandled(true);
    }
}
