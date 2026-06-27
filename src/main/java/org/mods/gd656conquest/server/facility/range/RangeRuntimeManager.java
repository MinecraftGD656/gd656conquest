package org.mods.gd656conquest.server.facility.range;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.serverconfig.ServerConfigStore;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService;
import org.mods.gd656conquest.server.player.state.HubDimensionService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerLoadoutService;
import org.mods.gd656conquest.server.shared.inventory.PlayerInventorySanitizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class RangeRuntimeManager {
    private static final String INVENTORY_SNAPSHOT_TAG = "gd656conquest_range_inventory_snapshot";
    private static final int RANGE_AMMO_BOX_SLOT = 18;
    private static final Map<MinecraftServer, RangeRuntimeManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final RoomPlayerLoadoutService playerLoadoutService;
    private final Map<UUID, RangePlayerSession> playerSessions = new LinkedHashMap<>();

    private RangeRuntimeManager(MinecraftServer server) {
        this.server = server;
        this.playerLoadoutService = new RoomPlayerLoadoutService(server);
    }

    public static RangeRuntimeManager of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, RangeRuntimeManager::new);
        }
    }

    public void initialize() {
    }

    public void shutdown() {
        for (UUID uuid : new ArrayList<>(playerSessions.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                leaveRangeInternal(player, false);
            }
        }
        playerSessions.clear();
    }

    public boolean isInRange(UUID uuid) {
        return uuid != null && playerSessions.containsKey(uuid);
    }

    public String joinRange(ServerPlayer player) {
        if (player == null) {
            return "加入靶场失败：玩家不存在";
        }
        UUID uuid = player.getUUID();
        if (RoomCoreRuntimeManager.of(server).findPlayerRoomId(uuid).isPresent()) {
            return "加入靶场失败：请先离开当前房间";
        }
        if (playerSessions.containsKey(uuid)) {
            refreshLoadoutIfInRange(player);
            teleportToJoinSpawn(player);
            return "你已在靶场，已刷新当前配装";
        }
        backupInventory(player);
        clearPlayerInventory(player);
        playerSessions.put(uuid, new RangePlayerSession(requireSnapshot(player)));
        playerLoadoutService.applyStandaloneLoadout(player, false);
        grantRangeAmmoBox(player);
        enforceRangeHotbarLayout(player);
        teleportToJoinSpawn(player);
        QuickAccessItemService.refreshHotbar(player);
        return "已加入靶场";
    }

    public String leaveRange(ServerPlayer player) {
        if (player == null) {
            return "离开靶场失败：玩家不存在";
        }
        UUID uuid = player.getUUID();
        if (!playerSessions.containsKey(uuid) && !hasPersistentSnapshot(player)) {
            return "当前不在靶场";
        }
        leaveRangeInternal(player, true);
        QuickAccessItemService.refreshHotbar(player);
        return "已离开靶场";
    }

    public void leaveRangeForTransfer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        leaveRangeInternal(player, false);
    }

    public boolean onPlayerRespawn(ServerPlayer player) {
        if (player == null || !playerSessions.containsKey(player.getUUID())) {
            return false;
        }
        playerLoadoutService.applyStandaloneLoadout(player, false);
        grantRangeAmmoBox(player);
        enforceRangeHotbarLayout(player);
        teleportToJoinSpawn(player);
        QuickAccessItemService.refreshHotbar(player);
        return true;
    }

    public void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        leaveRangeInternal(player, false);
    }

    public void onPlayerLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        playerSessions.remove(player.getUUID());
        if (!hasPersistentSnapshot(player)) {
            return;
        }
        tryRestoreFromPersistent(player);
        teleportToOverworldSpawn(player);
        QuickAccessItemService.refreshHotbar(player);
    }

    public void onPlayerChangedDimension(ServerPlayer player) {
        if (player == null || !playerSessions.containsKey(player.getUUID())) {
            return;
        }
        leaveRangeInternal(player, false);
    }

    public void refreshLoadoutIfInRange(ServerPlayer player) {
        if (player == null || !playerSessions.containsKey(player.getUUID())) {
            return;
        }
        playerLoadoutService.applyStandaloneLoadout(player, false);
        grantRangeAmmoBox(player);
        enforceRangeHotbarLayout(player);
        QuickAccessItemService.refreshHotbar(player);
    }

    private void leaveRangeInternal(ServerPlayer player, boolean teleportToLeaveSpawn) {
        UUID uuid = player.getUUID();
        RangePlayerSession session = playerSessions.remove(uuid);
        restoreInventory(player, session == null ? null : session.inventorySnapshot());
        if (teleportToLeaveSpawn) {
            teleportToLeaveSpawn(player);
        }
    }

    private void backupInventory(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!playerSessions.containsKey(uuid)) {
            String snapshot = RangeInventorySnapshotCodec.snapshot(player);
            if (!snapshot.isBlank()) {
                player.getPersistentData().putString(INVENTORY_SNAPSHOT_TAG, snapshot);
            }
        }
    }

    private String requireSnapshot(ServerPlayer player) {
        String snapshot = player.getPersistentData().getString(INVENTORY_SNAPSHOT_TAG);
        return snapshot == null ? "" : snapshot;
    }

    private void clearPlayerInventory(ServerPlayer player) {
        PlayerInventorySanitizer.clearAllSlots(player);
    }

    private void restoreInventory(ServerPlayer player, String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            snapshot = pullPersistentSnapshot(player);
        } else {
            player.getPersistentData().remove(INVENTORY_SNAPSHOT_TAG);
        }
        if (snapshot == null || snapshot.isBlank()) {
            return;
        }
        RangeInventorySnapshotCodec.restore(player, snapshot);
    }

    private void tryRestoreFromPersistent(ServerPlayer player) {
        String snapshot = pullPersistentSnapshot(player);
        if (snapshot == null || snapshot.isBlank()) {
            return;
        }
        RangeInventorySnapshotCodec.restore(player, snapshot);
    }

    private String pullPersistentSnapshot(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(INVENTORY_SNAPSHOT_TAG, Tag.TAG_STRING)) {
            return null;
        }
        String snapshot = persistent.getString(INVENTORY_SNAPSHOT_TAG);
        persistent.remove(INVENTORY_SNAPSHOT_TAG);
        return snapshot;
    }

    private boolean hasPersistentSnapshot(ServerPlayer player) {
        return player != null && player.getPersistentData().contains(INVENTORY_SNAPSHOT_TAG, Tag.TAG_STRING);
    }

    private void teleportToJoinSpawn(ServerPlayer player) {
        var position = serverConfig().getRangeJoinSpawnFallbackShared();
        player.teleportTo(HubDimensionService.resolveHubLevel(server), position.x() + 0.5D, position.y(), position.z() + 0.5D, player.getYRot(), player.getXRot());
    }

    private void teleportToLeaveSpawn(ServerPlayer player) {
        var position = serverConfig().getRangeLeaveSpawnFallbackShared();
        player.teleportTo(HubDimensionService.resolveHubLevel(server), position.x() + 0.5D, position.y(), position.z() + 0.5D, player.getYRot(), player.getXRot());
    }

    private void teleportToOverworldSpawn(ServerPlayer player) {
        var position = serverConfig().getOverworldSpawnFallbackShared();
        player.teleportTo(HubDimensionService.resolveHubLevel(server), position.x() + 0.5D, position.y(), position.z() + 0.5D, player.getYRot(), player.getXRot());
    }

    private ServerConfigStore serverConfig() {
        return ConquestDataManager.of(server).serverConfig();
    }

    private void grantRangeAmmoBox(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ItemStack ammoBox = createRangeAmmoBoxStack();
        if (ammoBox.isEmpty()) {
            return;
        }
        player.getInventory().setItem(RANGE_AMMO_BOX_SLOT, ammoBox);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private void enforceRangeHotbarLayout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        // Keep loadout-driven 1/2/3 slots intact, force 4/5/6/7 empty, set 8=loadout, 9=leave.
        forceClearHotbarSlot(player, 3);
        forceClearHotbarSlot(player, 4);
        forceClearHotbarSlot(player, 5);
        forceClearHotbarSlot(player, 6);
        setHotbarSlotItem(player, 7, ConquestItems.LOADOUT_GUIDE_ITEM.get());
        setHotbarSlotItem(player, 8, ConquestItems.LEAVE_ITEM.get());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private void forceClearHotbarSlot(ServerPlayer player, int slot) {
        if (player == null || slot < 0 || slot >= 9) {
            return;
        }
        if (player.getInventory().getItem(slot).isEmpty()) {
            return;
        }
        player.getInventory().setItem(slot, ItemStack.EMPTY);
    }

    private void setHotbarSlotItem(ServerPlayer player, int slot, Item item) {
        if (player == null || item == null || slot < 0 || slot >= 9) {
            return;
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (!current.isEmpty() && current.getItem() == item) {
            return;
        }
        player.getInventory().setItem(slot, new ItemStack(item));
    }

    private ItemStack createRangeAmmoBoxStack() {
        Item item = resolveItem("tacz:ammo_box");
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack ammoBox = new ItemStack(item, 1);
        CompoundTag tag = ammoBox.getOrCreateTag();
        tag.remove("Creative");
        tag.putBoolean("AllTypeCreative", true);
        return ammoBox;
    }

    private Item resolveItem(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }
}
