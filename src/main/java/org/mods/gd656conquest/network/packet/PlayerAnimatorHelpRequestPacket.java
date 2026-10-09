package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;

import java.util.function.Supplier;

public final class PlayerAnimatorHelpRequestPacket {
    private static final int HELP_ANIMATION_DURATION_MS = 1_250;

    public static void encode(PlayerAnimatorHelpRequestPacket message, FriendlyByteBuf buffer) {
    }

    public static PlayerAnimatorHelpRequestPacket decode(FriendlyByteBuf buffer) {
        return new PlayerAnimatorHelpRequestPacket();
    }

    public static void handle(PlayerAnimatorHelpRequestPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || sender.server == null) {
                return;
            }
            RoomCoreRuntimeManager.of(sender.server).emitPlayerThirdPersonAudioCue(
                sender.getUUID(),
                RoomThirdPersonAudioCueRegistry.PLAYER_DOWNED_HELP
            );
            PlayerAnimatorHelpSyncPacket packet = new PlayerAnimatorHelpSyncPacket(sender.getUUID(), HELP_ANIMATION_DURATION_MS);
            for (ServerPlayer viewer : sender.server.getPlayerList().getPlayers()) {
                NetworkManager.sendToPlayer(viewer, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
