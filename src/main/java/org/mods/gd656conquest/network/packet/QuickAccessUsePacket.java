package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.network.NetworkManager;

import java.util.function.Supplier;

/**
 * Asks the server to use the quick-action item, so the lobby button behaves exactly like a
 * right click on the hotbar item instead of duplicating the join logic on the client.
 */
public final class QuickAccessUsePacket {
    private static final int QUICK_JOIN_HOTBAR_SLOT = 0;

    public static void encode(QuickAccessUsePacket message, FriendlyByteBuf buffer) {
    }

    public static QuickAccessUsePacket decode(FriendlyByteBuf buffer) {
        return new QuickAccessUsePacket();
    }

    public static void handle(QuickAccessUsePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> NetworkManager.sendToPlayer(player, new QuickAccessResultPacket(useQuickJoin(player))));
        context.setPacketHandled(true);
    }

    private static boolean useQuickJoin(ServerPlayer player) {
        ItemStack stack = player.getInventory().getItem(QUICK_JOIN_HOTBAR_SLOT);
        if (stack.isEmpty() || stack.getItem() != ConquestItems.QUICK_JOIN_ITEM.get()) {
            return false;
        }
        return stack.use(player.level(), player, InteractionHand.MAIN_HAND).getResult().consumesAction();
    }
}
