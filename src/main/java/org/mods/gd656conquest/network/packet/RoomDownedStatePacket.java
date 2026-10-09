package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.config.ClientPacketBypassConfig;
import org.mods.gd656conquest.client.room.ClientDownedState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class RoomDownedStatePacket {
    private static final int ACTION_START = 0;
    private static final int ACTION_UPDATE = 1;
    private static final int ACTION_CLEAR = 2;
    private static final int ACTION_DEBUG = 3;

    private final int action;
    private final double deathX;
    private final double deathY;
    private final double deathZ;
    private final int killerEntityId;
    private final int remainTicks;
    private final List<RescuerEntry> rescuers;

    public RoomDownedStatePacket(int action, double deathX, double deathY, double deathZ, int killerEntityId, int remainTicks, List<RescuerEntry> rescuers) {
        this.action = action;
        this.deathX = deathX;
        this.deathY = deathY;
        this.deathZ = deathZ;
        this.killerEntityId = killerEntityId;
        this.remainTicks = Math.max(0, remainTicks);
        this.rescuers = rescuers == null ? List.of() : List.copyOf(rescuers);
    }

    public static RoomDownedStatePacket start(double deathX, double deathY, double deathZ, int killerEntityId, int remainTicks, List<RescuerEntry> rescuers) {
        return new RoomDownedStatePacket(ACTION_START, deathX, deathY, deathZ, killerEntityId, remainTicks, rescuers);
    }

    public static RoomDownedStatePacket update(int remainTicks, List<RescuerEntry> rescuers) {
        return new RoomDownedStatePacket(ACTION_UPDATE, 0.0D, 0.0D, 0.0D, -1, remainTicks, rescuers);
    }

    public static RoomDownedStatePacket clear() {
        return new RoomDownedStatePacket(ACTION_CLEAR, 0.0D, 0.0D, 0.0D, -1, 0, List.of());
    }

    public static RoomDownedStatePacket debugPreview(int durationMs) {
        return new RoomDownedStatePacket(ACTION_DEBUG, 0.0D, 0.0D, 0.0D, -1, Math.max(20, Math.round(durationMs / 50.0F)), List.of());
    }

    public static void encode(RoomDownedStatePacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.action);
        buffer.writeDouble(message.deathX);
        buffer.writeDouble(message.deathY);
        buffer.writeDouble(message.deathZ);
        buffer.writeVarInt(message.killerEntityId);
        buffer.writeVarInt(message.remainTicks);
        buffer.writeVarInt(message.rescuers.size());
        for (RescuerEntry rescuer : message.rescuers) {
            buffer.writeUtf(rescuer.name);
            buffer.writeVarInt(rescuer.distanceMeters);
            buffer.writeBoolean(rescuer.squadMate);
        }
    }

    public static RoomDownedStatePacket decode(FriendlyByteBuf buffer) {
        int action = buffer.readVarInt();
        double deathX = buffer.readDouble();
        double deathY = buffer.readDouble();
        double deathZ = buffer.readDouble();
        int killerEntityId = buffer.readVarInt();
        int remainTicks = buffer.readVarInt();
        int count = Math.max(0, buffer.readVarInt());
        List<RescuerEntry> rescuers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            rescuers.add(new RescuerEntry(
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readBoolean()
            ));
        }
        return new RoomDownedStatePacket(action, deathX, deathY, deathZ, killerEntityId, remainTicks, rescuers);
    }

    public static void handle(RoomDownedStatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (ClientPacketBypassConfig.bypassDownedStatePackets()) {
                ClientDownedState.clear();
                return;
            }
            if (message.action == ACTION_CLEAR) {
                ClientDownedState.clear();
                return;
            }
            if (message.action == ACTION_DEBUG) {
                ClientDownedState.startDebugPreview(message.remainTicks * 50);
                return;
            }
            List<ClientDownedState.RescuerEntry> rescuers = message.rescuers.stream()
                .map(entry -> new ClientDownedState.RescuerEntry(entry.name, entry.distanceMeters, entry.squadMate))
                .toList();
            ClientDownedState.startOrUpdate(
                message.action == ACTION_START,
                message.deathX,
                message.deathY,
                message.deathZ,
                message.killerEntityId,
                message.remainTicks,
                rescuers
            );
        }));
        context.setPacketHandled(true);
    }

    public record RescuerEntry(String name, int distanceMeters, boolean squadMate) {
        public RescuerEntry {
            name = name == null ? "" : name;
            distanceMeters = Math.max(1, distanceMeters);
        }
    }
}
