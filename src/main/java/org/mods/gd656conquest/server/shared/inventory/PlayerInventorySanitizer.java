package org.mods.gd656conquest.server.shared.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class PlayerInventorySanitizer {
    private PlayerInventorySanitizer() {
    }

    public static void clearAllSlots(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.getInventory().clearContent();
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        flush(player);
    }

    public static void flush(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }
}
