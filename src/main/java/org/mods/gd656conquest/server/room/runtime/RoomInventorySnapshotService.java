package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.shared.inventory.PlayerInventorySanitizer;

import java.util.Objects;
import java.util.UUID;

public final class RoomInventorySnapshotService {
    private static final String INVENTORY_SNAPSHOT_TAG = "gd656conquest_room_inventory_snapshot";

    public void backupAndClear(ServerPlayer player, RoomRuntimeState runtimeState) {
        Objects.requireNonNull(runtimeState, "runtimeState");
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        if (!runtimeState.getInventorySnapshots().containsKey(playerId)) {
            CompoundTag snapshot = new CompoundTag();
            snapshot.put("Inventory", player.getInventory().save(new ListTag()));
            snapshot.putInt("Selected", player.getInventory().selected);
            runtimeState.getInventorySnapshots().put(playerId, snapshot.toString());
        }
        String snapshot = runtimeState.getInventorySnapshots().get(playerId);
        if (snapshot != null && !snapshot.isBlank()) {
            player.getPersistentData().putString(INVENTORY_SNAPSHOT_TAG, snapshot);
        }
        clearPlayerInventoryState(player);
    }

    public void restore(ServerPlayer player, RoomRuntimeState runtimeState) {
        Objects.requireNonNull(runtimeState, "runtimeState");
        if (player == null) {
            return;
        }
        String snapshot = runtimeState.getInventorySnapshots().remove(player.getUUID());
        if (snapshot == null || snapshot.isBlank()) {
            snapshot = pullPersistentInventorySnapshot(player);
        } else {
            player.getPersistentData().remove(INVENTORY_SNAPSHOT_TAG);
        }
        if (snapshot == null || snapshot.isBlank()) {
            return;
        }
        restoreInventoryFromSnapshot(player, snapshot);
    }

    public void tryRestoreFromPersistent(ServerPlayer player) {
        if (player == null) {
            return;
        }
        String snapshot = pullPersistentInventorySnapshot(player);
        if (snapshot == null || snapshot.isBlank()) {
            return;
        }
        restoreInventoryFromSnapshot(player, snapshot);
    }

    private String pullPersistentInventorySnapshot(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(INVENTORY_SNAPSHOT_TAG, Tag.TAG_STRING)) {
            return null;
        }
        String snapshot = persistent.getString(INVENTORY_SNAPSHOT_TAG);
        persistent.remove(INVENTORY_SNAPSHOT_TAG);
        return snapshot;
    }

    private void restoreInventoryFromSnapshot(ServerPlayer player, String snapshot) {
        try {
            CompoundTag compoundTag = TagParser.parseTag(snapshot);
            ListTag inventoryList = compoundTag.getList("Inventory", Tag.TAG_COMPOUND);
            clearPlayerInventoryState(player);
            player.getInventory().load(inventoryList);
            if (compoundTag.contains("Selected", Tag.TAG_INT)) {
                player.getInventory().selected = compoundTag.getInt("Selected");
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        } catch (Exception exception) {
            ServerLogger.failure("鎭㈠鐜╁鑳屽寘澶辫触: %s", player.getGameProfile().getName());
        }
    }

    private void clearPlayerInventoryState(ServerPlayer player) {
        PlayerInventorySanitizer.clearAllSlots(player);
    }
}
