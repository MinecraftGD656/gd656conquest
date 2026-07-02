package org.mods.gd656conquest.server.facility.quickaccess;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.facility.parkour.ParkourRuntimeManager;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.player.state.HubDimensionService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.shared.inventory.PlayerInventorySanitizer;
import org.mods.gd656conquest.server.social.party.PlayerPartyManager;

import java.util.Optional;

public final class QuickAccessInventoryPolicyService {
    private static final int LOBBY_ENFORCE_INTERVAL_TICKS = 40;
    private static final int HOTBAR_QUICK_JOIN = 0;
    private static final int HOTBAR_ROOM_LIST = 1;
    private static final int HOTBAR_TEAM = 2;
    private static final int HOTBAR_MY_TITLE = 6;
    private static final int HOTBAR_LOADOUT = 7;
    private static final int HOTBAR_LEAVE_OR_RANGE = 8;
    private QuickAccessInventoryPolicyService() {
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (!shouldEnforceLobbyInventory(player) || player.tickCount % LOBBY_ENFORCE_INTERVAL_TICKS != 0) {
            return;
        }
        applyLobbyInventory(player);
    }

    public static void refreshForCurrentContext(ServerPlayer player) {
        if (player == null || player.server == null || player.hasPermissions(2)) {
            return;
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(player.server);
        RangeRuntimeManager rangeManager = RangeRuntimeManager.of(player.server);
        ParkourRuntimeManager parkourManager = ParkourRuntimeManager.of(player.server);
        Optional<String> roomId = roomManager.findPlayerRoomId(player.getUUID());
        if (roomId.isPresent()) {
            RoomDefinition room = roomManager.findRoom(roomId.get()).orElse(null);
            applyRoomInventory(player, room);
            return;
        }
        if (rangeManager.isInRange(player.getUUID())) {
            applyRangeInventory(player);
            return;
        }
        if (parkourManager.isInParkour(player.getUUID())) {
            applyParkourInventory(player);
            return;
        }
        if (HubDimensionService.isHubDimension(player.level())
        ) {
            applyLobbyInventory(player);
        }
    }

    public static void sanitizeLobbyInventory(ServerPlayer player) {
        if (!shouldEnforceLobbyInventory(player)) {
            return;
        }
        applyLobbyInventory(player);
    }

    public static boolean isQuickAccessItem(Item item) {
        return item != null
            && (matchesRegistryItem(item, ConquestItems.QUICK_JOIN_ITEM)
            || matchesRegistryItem(item, ConquestItems.ROOM_LIST_ITEM)
            || matchesRegistryItem(item, ConquestItems.PARTY_GUIDE_ITEM)
            || matchesRegistryItem(item, ConquestItems.MY_TITLE_ITEM)
            || matchesRegistryItem(item, ConquestItems.LOADOUT_GUIDE_ITEM)
            || matchesRegistryItem(item, ConquestItems.ENTER_RANGE_ITEM)
            || matchesRegistryItem(item, ConquestItems.LEAVE_ITEM)
            || matchesRegistryItem(item, ConquestItems.LEAVE_PARTY_ITEM));
    }

    private static boolean shouldEnforceLobbyInventory(ServerPlayer player) {
        if (player == null || player.server == null || player.hasPermissions(2)) {
            return false;
        }
        if (!HubDimensionService.isHubDimension(player.level())) {
            return false;
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(player.server);
        if (roomManager.findPlayerRoomId(player.getUUID()).isPresent()) {
            return false;
        }
        if (RangeRuntimeManager.of(player.server).isInRange(player.getUUID())) {
            return false;
        }
        return !ParkourRuntimeManager.of(player.server).isInParkour(player.getUUID());
    }

    private static void applyLobbyInventory(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        boolean changed = false;
        PlayerPartyManager partyManager = PlayerPartyManager.of(player.server);
        changed |= setHotbarSlotIfNeeded(player, HOTBAR_QUICK_JOIN, ConquestItems.QUICK_JOIN_ITEM.get());
        changed |= setHotbarSlotIfNeeded(player, HOTBAR_ROOM_LIST, ConquestItems.ROOM_LIST_ITEM.get());
        changed |= setHotbarSlotIfNeeded(player, HOTBAR_TEAM, partyManager.isInParty(player.getUUID())
            ? ConquestItems.LEAVE_PARTY_ITEM.get()
            : ConquestItems.PARTY_GUIDE_ITEM.get());
        changed |= clearHotbarSlot(player, 3);
        changed |= clearHotbarSlot(player, 4);
        changed |= clearHotbarSlot(player, 5);
        changed |= setHotbarSlotIfNeeded(player, HOTBAR_MY_TITLE, ConquestItems.MY_TITLE_ITEM.get());
        changed |= setHotbarSlotIfNeeded(player, HOTBAR_LOADOUT, ConquestItems.LOADOUT_GUIDE_ITEM.get());
        changed |= setHotbarSlotIfNeeded(player, HOTBAR_LEAVE_OR_RANGE, ConquestItems.ENTER_RANGE_ITEM.get());
        changed |= clearAllInventorySlotsOutsideHotbar(player);
        changed |= clearEquipmentSlotIfNeeded(player, EquipmentSlot.HEAD);
        changed |= clearEquipmentSlotIfNeeded(player, EquipmentSlot.CHEST);
        changed |= clearEquipmentSlotIfNeeded(player, EquipmentSlot.LEGS);
        changed |= clearEquipmentSlotIfNeeded(player, EquipmentSlot.FEET);
        changed |= clearEquipmentSlotIfNeeded(player, EquipmentSlot.OFFHAND);
        changed |= clearCarriedItemIfNeeded(player);
        int normalizedSelected = normalizeLobbySelectedSlot(player);
        if (player.getInventory().selected != normalizedSelected) {
            player.getInventory().selected = normalizedSelected;
            changed = true;
        }
        if (changed) {
            PlayerInventorySanitizer.flush(player);
        }
    }

    private static void applyRangeInventory(ServerPlayer player) {
        boolean changed = stripQuickAccessItemsOutsideHotbar(player);
        changed |= clearHotbarSlot(player, 3);
        changed |= clearHotbarSlot(player, 4);
        changed |= clearHotbarSlot(player, 5);
        changed |= clearHotbarSlot(player, 6);
        changed |= clearHotbarSlot(player, 6);
        changed |= setHotbarSlotIfNeeded(player, HOTBAR_LOADOUT, ConquestItems.LOADOUT_GUIDE_ITEM.get());
        changed |= setHotbarSlotIfNeeded(player, HOTBAR_LEAVE_OR_RANGE, ConquestItems.LEAVE_ITEM.get());
        if (changed) {
            PlayerInventorySanitizer.flush(player);
        }
    }

    private static void applyParkourInventory(ServerPlayer player) {
        boolean changed = stripQuickAccessItemsOutsideHotbar(player);
        for (int slot = 0; slot < 8; slot++) {
            changed |= clearHotbarSlot(player, slot);
        }
        changed |= setHotbarSlotIfNeeded(player, HOTBAR_LEAVE_OR_RANGE, ConquestItems.LEAVE_ITEM.get());
        if (player != null && player.getInventory().selected != HOTBAR_LEAVE_OR_RANGE) {
            player.getInventory().selected = HOTBAR_LEAVE_OR_RANGE;
            changed = true;
        }
        if (changed) {
            PlayerInventorySanitizer.flush(player);
        }
    }

    private static void applyRoomInventory(ServerPlayer player, RoomDefinition room) {
        boolean changed = stripQuickAccessItemsOutsideHotbar(player);
        RoomLifecycleState lifecycleState = room == null ? null : room.lifecycleState();
        if (lifecycleState == RoomLifecycleState.MATCHING) {
            changed |= setHotbarSlotIfNeeded(player, HOTBAR_QUICK_JOIN, ConquestItems.LOADOUT_GUIDE_ITEM.get());
            changed |= setHotbarSlotIfNeeded(player, HOTBAR_LEAVE_OR_RANGE, ConquestItems.LEAVE_ITEM.get());
            changed |= clearHotbarSlot(player, 1);
            changed |= clearHotbarSlot(player, 2);
            changed |= clearHotbarSlot(player, 3);
            changed |= clearHotbarSlot(player, 4);
            changed |= clearHotbarSlot(player, 5);
            changed |= clearHotbarSlot(player, 6);
            changed |= clearHotbarSlot(player, 7);
        }
        if (changed) {
            PlayerInventorySanitizer.flush(player);
        }
    }

    private static boolean stripQuickAccessItemsOutsideHotbar(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        boolean changed = false;
        int size = player.getInventory().getContainerSize();
        for (int slot = 9; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && isQuickAccessItem(stack.getItem())) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean setHotbarSlotIfNeeded(ServerPlayer player, int slot, Item item) {
        if (player == null || item == null || slot < 0 || slot >= 9) {
            return false;
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (!current.isEmpty() && current.getCount() == 1 && current.getItem() == item) {
            return false;
        }
        setHotbarSlot(player, slot, item);
        return true;
    }

    private static void setHotbarSlot(ServerPlayer player, int slot, Item item) {
        if (player == null || item == null || slot < 0 || slot >= 9) {
            return;
        }
        player.getInventory().setItem(slot, new ItemStack(item));
    }

    private static boolean matchesRegistryItem(Item item, net.minecraftforge.registries.RegistryObject<Item> registryObject) {
        return item != null && registryObject != null && registryObject.isPresent() && item == registryObject.get();
    }

    private static boolean clearHotbarSlot(ServerPlayer player, int slot) {
        if (player == null || slot < 0 || slot >= 9) {
            return false;
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (current.isEmpty()) {
            return false;
        }
        player.getInventory().setItem(slot, ItemStack.EMPTY);
        return true;
    }

    private static boolean clearAllInventorySlotsOutsideHotbar(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        boolean changed = false;
        int size = player.getInventory().getContainerSize();
        for (int slot = 9; slot < size; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty()) {
                continue;
            }
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            changed = true;
        }
        return changed;
    }

    private static boolean clearEquipmentSlotIfNeeded(ServerPlayer player, EquipmentSlot slot) {
        if (player == null || slot == null) {
            return false;
        }
        if (player.getItemBySlot(slot).isEmpty()) {
            return false;
        }
        player.setItemSlot(slot, ItemStack.EMPTY);
        return true;
    }

    private static boolean clearCarriedItemIfNeeded(ServerPlayer player) {
        if (player == null || player.containerMenu.getCarried().isEmpty()) {
            return false;
        }
        player.containerMenu.setCarried(ItemStack.EMPTY);
        return true;
    }

    private static int normalizeLobbySelectedSlot(ServerPlayer player) {
        if (player == null) {
            return HOTBAR_QUICK_JOIN;
        }
        int selected = player.getInventory().selected;
        if (selected >= 0 && selected < 9) {
            ItemStack selectedStack = player.getInventory().getItem(selected);
            if (!selectedStack.isEmpty() && isQuickAccessItem(selectedStack.getItem())) {
                return selected;
            }
        }
        return HOTBAR_QUICK_JOIN;
    }
}
