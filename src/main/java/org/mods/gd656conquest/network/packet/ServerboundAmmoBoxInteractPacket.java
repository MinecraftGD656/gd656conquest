package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.facility.parkour.ParkourRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

import java.util.function.Supplier;

public final class ServerboundAmmoBoxInteractPacket {
    private final Action action;

    public ServerboundAmmoBoxInteractPacket() {
        this(Action.START);
    }

    public ServerboundAmmoBoxInteractPacket(Action action) {
        this.action = action == null ? Action.START : action;
    }

    public Action action() {
        return action;
    }

    public static void encode(ServerboundAmmoBoxInteractPacket message, FriendlyByteBuf buffer) {
        buffer.writeEnum(message == null ? Action.START : message.action());
    }

    public static ServerboundAmmoBoxInteractPacket decode(FriendlyByteBuf buffer) {
        return new ServerboundAmmoBoxInteractPacket(buffer.readEnum(Action.class));
    }

    public static void handle(ServerboundAmmoBoxInteractPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.server == null) {
                return;
            }
            if (ParkourRuntimeManager.of(sender.server).tryHandleCommonUse(sender)) {
                return;
            }
            long gameTick = sender.server.getTickCount();
            if (message != null && message.action() == Action.STOP) {
                RoomCoreRuntimeManager.of(sender.server).handleCommonUseStop(sender, gameTick);
                return;
            }
            if (RoomCoreRuntimeManager.of(sender.server).handleCommonUseStart(sender, gameTick)) {
                return;
            }
            RoomCoreRuntimeManager.of(sender.server).tryUseNearbyAmmoBox(sender, gameTick);
        });
        context.setPacketHandled(true);
    }

    public enum Action {
        START,
        STOP
    }
}
