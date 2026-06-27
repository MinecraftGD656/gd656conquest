package org.mods.gd656conquest.server.facility.range;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;

public final class RangeInventorySnapshotCodec {
    private RangeInventorySnapshotCodec() {
    }

    public static String snapshot(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        CompoundTag snapshot = new CompoundTag();
        snapshot.put("Inventory", player.getInventory().save(new ListTag()));
        snapshot.putInt("Selected", player.getInventory().selected);
        return snapshot.toString();
    }

    public static void restore(ServerPlayer player, String snapshot) {
        if (player == null || snapshot == null || snapshot.isBlank()) {
            return;
        }
        try {
            CompoundTag compoundTag = TagParser.parseTag(snapshot);
            ListTag inventoryList = compoundTag.getList("Inventory", Tag.TAG_COMPOUND);
            player.getInventory().load(inventoryList);
            if (compoundTag.contains("Selected", Tag.TAG_INT)) {
                player.getInventory().selected = compoundTag.getInt("Selected");
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        } catch (Exception ignored) {
        }
    }
}
