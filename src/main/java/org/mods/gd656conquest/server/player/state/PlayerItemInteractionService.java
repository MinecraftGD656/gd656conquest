package org.mods.gd656conquest.server.player.state;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerLoadoutService;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;

public final class PlayerItemInteractionService {
    private static final int PICKUP_USE_MEDICAL_DURATION_TICKS = 3 * 20;
    private static final int PICKUP_USE_MEDICAL_AMPLIFIER = 2;
    private static final int PICKUP_USE_AMMO_AMOUNT = 40;

    private PlayerItemInteractionService() {
    }

    public static void handlePlayerItemToss(ItemTossEvent event) {
        if (event == null || !(event.getPlayer() instanceof ServerPlayer player) || event.getEntity() == null) {
            return;
        }
        if (event.getEntity().level().isClientSide) {
            return;
        }
        ItemStack dropped = event.getEntity().getItem();
        if (dropped.isEmpty()) {
            return;
        }

        if (RoomCoreRuntimeManager.of(player.server).isPlayerDowned(player.getUUID())) {
            event.setCanceled(true);
            restoreNonDroppableItemToInventory(player, dropped.copy());
            if (!event.getEntity().isRemoved()) {
                event.getEntity().discard();
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return;
        }

        if (!isNonDroppableInteractItem(dropped.getItem())) {
            return;
        }
        event.setCanceled(true);
        restoreNonDroppableItemToInventory(player, dropped.copy());
        if (!event.getEntity().isRemoved()) {
            event.getEntity().discard();
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static void handleEntityItemPickup(EntityItemPickupEvent event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player) || event.getItem() == null) {
            return;
        }
        ItemEntity itemEntity = event.getItem();
        if (itemEntity.level().isClientSide) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return;
        }
        if (RoomCoreRuntimeManager.of(player.server).isDeathSupportDropPickupBlocked(player, itemEntity)) {
            event.setCanceled(true);
            return;
        }
        Item item = stack.getItem();
        if (item == ConquestItems.AMMO_BAG.get()) {
            handleAmmoBagPickup(event, player, itemEntity, stack);
            return;
        }
        if (item == ConquestItems.MEDIC_BAG.get()) {
            handleMedicalBagPickup(event, player, itemEntity, stack);
        }
    }

    private static void handleAmmoBagPickup(EntityItemPickupEvent event, ServerPlayer player, ItemEntity itemEntity, ItemStack stack) {
        if (!isCoreRoomPlayer(player)) {
            return;
        }
        RoomPlayerLoadoutService.AmmoSupplyResult result =
            RoomCoreRuntimeManager.of(player.server).trySupplyAmmoByBag(player, PICKUP_USE_AMMO_AMOUNT);
        if (result.totalAdded() <= 0) {
            return;
        }
        stack.shrink(1);
        ServerMessageSender.actionBar(player, "房间", Component.literal("拾取并直接使用了弹药包").withStyle(ChatFormatting.GREEN));
        cleanupPickedItemEntity(itemEntity);
        event.setCanceled(true);
    }

    private static void handleMedicalBagPickup(EntityItemPickupEvent event, ServerPlayer player, ItemEntity itemEntity, ItemStack stack) {
        stack.shrink(1);
        player.addEffect(new MobEffectInstance(
            MobEffects.REGENERATION,
            PICKUP_USE_MEDICAL_DURATION_TICKS,
            PICKUP_USE_MEDICAL_AMPLIFIER,
            false,
            true,
            true
        ));
        ServerMessageSender.actionBar(player, "房间", Component.literal("拾取并直接使用了医疗包").withStyle(ChatFormatting.GREEN));
        cleanupPickedItemEntity(itemEntity);
        event.setCanceled(true);
    }

    private static boolean isCoreRoomPlayer(ServerPlayer player) {
        return player != null
            && player.server != null
            && RoomCoreRuntimeManager.of(player.server).findPlayerRoomId(player.getUUID()).isPresent();
    }

    private static boolean isNonDroppableInteractItem(Item item) {
        if (item == null) {
            return false;
        }
        return QuickAccessItemService.isUtilityItem(item)
            || item == ConquestItems.AMMO_BOX.get()
            || item == ConquestItems.MED_BOX.get()
            || item == ConquestItems.RADIO_BEACON.get();
    }

    private static void cleanupPickedItemEntity(ItemEntity itemEntity) {
        if (itemEntity == null) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty() || stack.getCount() <= 0) {
            itemEntity.discard();
        }
    }

    private static void restoreNonDroppableItemToInventory(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack restore = stack.copy();
        if (player.getInventory().add(restore)) {
            return;
        }
        int selected = player.getInventory().selected;
        if (selected >= 0 && selected < player.getInventory().items.size()) {
            ItemStack selectedStack = player.getInventory().getItem(selected);
            if (selectedStack.isEmpty()) {
                player.getInventory().setItem(selected, restore.copy());
                return;
            }
            if (ItemStack.isSameItemSameTags(selectedStack, restore)) {
                int maxStack = selectedStack.getMaxStackSize();
                int room = maxStack - selectedStack.getCount();
                if (room > 0) {
                    int toMove = Math.min(room, restore.getCount());
                    selectedStack.grow(toMove);
                    restore.shrink(toMove);
                    if (restore.isEmpty()) {
                        return;
                    }
                }
            }
        }
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, restore.copy());
                return;
            }
        }
    }
}
