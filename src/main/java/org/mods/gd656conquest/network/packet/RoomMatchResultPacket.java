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
    private final boolean active;
    private final MatchResultType resultType;
    private final int totalTicks;
    private final int blackStartTicks;
    private final int restoreTicks;

    public RoomMatchResultPacket(boolean active, MatchResultType resultType, int totalTicks, int blackStartTicks, int restoreTicks) {
        this.active = active;
        this.resultType = Objects.requireNonNull(resultType, "resultType");
        this.totalTicks = totalTicks;
        this.blackStartTicks = blackStartTicks;
        this.restoreTicks = restoreTicks;
    }

    public static void encode(RoomMatchResultPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.active);
        buffer.writeInt(message.resultType.networkId());
        buffer.writeInt(message.totalTicks);
        buffer.writeInt(message.blackStartTicks);
        buffer.writeInt(message.restoreTicks);
    }

    public static RoomMatchResultPacket decode(FriendlyByteBuf buffer) {
        return new RoomMatchResultPacket(
            buffer.readBoolean(),
            MatchResultType.fromNetworkId(buffer.readInt()),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt()
        );
    }

    public static void handle(RoomMatchResultPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (message.active) {
                ClientMusicTriggerService.onMatchResult(message.resultType);
                ClientMatchResultOverlayState.start(message.resultType, message.totalTicks, message.blackStartTicks, message.restoreTicks);
            } else {
                ClientMatchResultOverlayState.clear();
            }
        }));
        context.setPacketHandled(true);
    }
}
