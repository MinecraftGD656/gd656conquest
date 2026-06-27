package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.common.player.lock.PlayerLockSnapshot;
import org.mods.gd656conquest.common.player.lock.PlayerLockStrength;

import java.util.function.Supplier;

public final class PlayerLockStatePacket {
    private final PlayerLockSnapshot snapshot;

    public PlayerLockStatePacket(PlayerLockSnapshot snapshot) {
        this.snapshot = snapshot == null ? PlayerLockSnapshot.none() : snapshot;
    }

    public PlayerLockSnapshot snapshot() {
        return snapshot;
    }

    public static void encode(PlayerLockStatePacket message, FriendlyByteBuf buffer) {
        PlayerLockSnapshot snapshot = message == null ? PlayerLockSnapshot.none() : message.snapshot();
        buffer.writeInt(snapshot.strength().level());
        buffer.writeUtf(snapshot.reason());
        buffer.writeBoolean(snapshot.lockInventory());
        buffer.writeBoolean(snapshot.lockHotbar());
        buffer.writeBoolean(snapshot.lockGameplayInteraction());
        buffer.writeBoolean(snapshot.lockMovement());
        buffer.writeBoolean(snapshot.lockLook());
        buffer.writeBoolean(snapshot.releaseMouse());
        buffer.writeBoolean(snapshot.hideHeldItems());
        buffer.writeBoolean(snapshot.forceHudOnly());
        buffer.writeBoolean(snapshot.forceVisibleHud());
        buffer.writeBoolean(snapshot.forceFirstPerson());
        buffer.writeBoolean(snapshot.forceCrouch());
        buffer.writeBoolean(snapshot.deployUiActive());
        buffer.writeBoolean(snapshot.endingState());
    }

    public static PlayerLockStatePacket decode(FriendlyByteBuf buffer) {
        return new PlayerLockStatePacket(new PlayerLockSnapshot(
            PlayerLockStrength.fromLevel(buffer.readInt()),
            buffer.readUtf(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean()
        ));
    }

    public static void handle(PlayerLockStatePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ClientRoomLockState.update(message == null ? PlayerLockSnapshot.none() : message.snapshot())
        ));
        context.setPacketHandled(true);
    }
}
