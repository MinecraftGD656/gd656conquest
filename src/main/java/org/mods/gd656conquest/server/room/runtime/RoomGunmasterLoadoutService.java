package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.attachment.WeaponAttachmentDataModel;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataStore;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RoomGunmasterLoadoutService {
    private static final int PRIMARY_WEAPON_HOTBAR_SLOT = 0;
    private static final int LOADOUT_HOTBAR_END_SLOT = 5;
    private static final int PRIMARY_AMMO_ROW_START = 18;
    private static final int PRIMARY_AMMO_ROW_END = 26;
    private final MinecraftServer server;
    private final RoomLoadoutGrantService grantService;

    public RoomGunmasterLoadoutService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.grantService = new RoomLoadoutGrantService(server);
    }

    public boolean isGunmasterRoom(RoomDefinition room) {
        return room != null && RoomGameplayId.GUNMASTER.equals(room.gameplayId());
    }

    public boolean isGunmasterPlayer(ServerPlayer player) {
        return isGunmasterRoom(resolveRoomRuntimeContext(player).room());
    }

    public void applySpawnLoadout(RunningRuntimeState state, ServerPlayer player) {
        RoomRuntimeContext context = resolveRoomRuntimeContext(player);
        applySpawnLoadout(context.room(), state == null ? context.state() : state, player, false);
    }

    public void applySpawnLoadout(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        RoomRuntimeContext context = resolveRoomRuntimeContext(player);
        applySpawnLoadout(room == null ? context.room() : room, state == null ? context.state() : state, player, false);
    }

    public void applyStageSwitchLoadout(RunningRuntimeState state, ServerPlayer player) {
        RoomRuntimeContext context = resolveRoomRuntimeContext(player);
        applySpawnLoadout(context.room(), state == null ? context.state() : state, player, true);
    }

    public void applyStageSwitchLoadout(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        RoomRuntimeContext context = resolveRoomRuntimeContext(player);
        applySpawnLoadout(room == null ? context.room() : room, state == null ? context.state() : state, player, true);
    }

    private void applySpawnLoadout(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, boolean forceSelectPrimary) {
        if (!isGunmasterRoom(room) || state == null || player == null || player.server == null) {
            return;
        }
        ConquestDataManager dataManager = ConquestDataManager.of(server);
        EquipmentDataStore equipmentDataStore = dataManager.equipmentData();
        EquipmentDataModel equipmentData = equipmentDataStore.getData();
        if (equipmentData == null) {
            return;
        }
        WeaponAttachmentDataModel attachmentData = dataManager.weaponAttachments().getData();
        int playerLevel = PlayerLevelService.getEffectiveLevel(player);
        EquipmentDataModel.EquipmentEntry weaponEntry = resolveCurrentWeaponEntry(state, player.getUUID(), equipmentData);
        RoomTeamId teamId = state.getRoomTeams().get(player.getUUID());

        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        clearGunmasterManagedSlots(player);

        if (weaponEntry != null) {
            ItemStack weaponStack = deserializeEquipmentItem(weaponEntry.getItemSnbt());
            PreparedGunmasterWeapon prepared = preparePrimaryWeaponStack(weaponEntry, weaponStack, attachmentData, playerLevel, state, player, room);
            if (!prepared.stack().isEmpty()) {
                player.getInventory().setItem(PRIMARY_WEAPON_HOTBAR_SLOT, prepared.stack());
            }
        }

        grantInfiniteAmmoItems(player);

        if (teamId != null) {
            applyArmorLoadout(player, room, equipmentData, PlayerClassType.defaultType(), teamId);
        }
        if (room.commonConfig().disableUndyingTotem()) {
            removeTotems(player);
        }
        grantService.applyOptionalRoomItems(player, room);
        if (forceSelectPrimary || !player.getInventory().getItem(PRIMARY_WEAPON_HOTBAR_SLOT).isEmpty()) {
            forceSelectHotbarSlot(player, PRIMARY_WEAPON_HOTBAR_SLOT);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private EquipmentDataModel.EquipmentEntry resolveCurrentWeaponEntry(RunningRuntimeState state, UUID playerId, EquipmentDataModel equipmentData) {
        if (state == null || playerId == null || equipmentData == null) {
            return null;
        }
        String weaponId = resolveCurrentWeaponId(state, playerId);
        if (weaponId.isBlank()) {
            return null;
        }
        EquipmentDataModel.EquipmentEntry direct = RoomGunmasterWeaponPoolRules.resolveEntry(equipmentData, weaponId);
        if (direct != null) {
            return direct;
        }
        String normalized = CustomLoadoutService.normalizeEquipmentId(weaponId);
        for (EquipmentDataModel.EquipmentEntry entry : equipmentData.getEquipmentById().values()) {
            if (entry == null) {
                continue;
            }
            if (normalized.equalsIgnoreCase(CustomLoadoutService.normalizeEquipmentId(entry.getId()))) {
                return entry;
            }
        }
        return null;
    }

    private String resolveCurrentWeaponId(RunningRuntimeState state, UUID playerId) {
        List<String> sequence = state == null ? List.of() : state.getGunmasterMatchWeaponSequenceIds();
        if (playerId == null || sequence.isEmpty()) {
            return "";
        }
        int stageIndex = Math.max(0, state.getGunmasterStageIndexByPlayer().getOrDefault(playerId, 0));
        if (stageIndex >= sequence.size()) {
            stageIndex = sequence.size() - 1;
        }
        return stageIndex < 0 ? "" : CustomLoadoutService.normalizeEquipmentId(sequence.get(stageIndex));
    }

    private PreparedGunmasterWeapon preparePrimaryWeaponStack(
        EquipmentDataModel.EquipmentEntry entry,
        ItemStack equipmentStack,
        WeaponAttachmentDataModel attachmentData,
        int playerLevel,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomDefinition room
    ) {
        RoomLoadoutGrantService.PreparedWeaponStack prepared = grantService.prepareConfiguredWeaponStack(
            CustomLoadoutSlot.PRIMARY_WEAPON,
            entry,
            equipmentStack,
            null,
            attachmentData,
            playerLevel,
            state,
            player,
            room != null && room.commonConfig().enableRandomAttachments(),
            false,
            false
        );
        return new PreparedGunmasterWeapon(prepared.stack(), prepared.playerDataChanged());
    }

    private void grantConfiguredWeaponAmmoByExtraData(ServerPlayer player, ItemStack gunStack, int extraData, int[] preferredSlots) {
        grantService.grantConfiguredWeaponAmmoByExtraData(player, gunStack, extraData, preferredSlots);
    }

    private void applyArmorLoadout(ServerPlayer player, RoomDefinition room, EquipmentDataModel equipmentData, PlayerClassType classType, RoomTeamId teamId) {
        grantService.applyArmorLoadout(player, room, equipmentData, classType, teamId);
    }

    private void clearGunmasterManagedSlots(ServerPlayer player) {
        if (player == null) {
            return;
        }
        for (int slot = PRIMARY_WEAPON_HOTBAR_SLOT; slot <= LOADOUT_HOTBAR_END_SLOT; slot++) {
            if (slot < 0 || slot >= player.getInventory().items.size()) {
                continue;
            }
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    private void grantInfiniteAmmoItems(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            net.minecraft.world.item.Item taczBoxItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.tryParse("tacz:ammo_box"));
            if (taczBoxItem != null) {
                ItemStack taczBox = taczBoxItem.getDefaultInstance();
                if (taczBox.getItem() instanceof com.tacz.guns.api.item.IAmmoBox iAmmoBox) {
                    iAmmoBox.setCreative(taczBox, true);
                }
                player.getInventory().setItem(9, taczBox);
            }
        } catch (Exception ignored) {
        }

        try {
            net.minecraft.world.item.Item sbwBoxItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.tryParse("superbwarfare:creative_ammo_box"));
            if (sbwBoxItem != null) {
                player.getInventory().setItem(10, sbwBoxItem.getDefaultInstance());
            }
        } catch (Exception ignored) {
        }

        try {
            player.getInventory().setItem(11, new ItemStack(net.minecraft.world.item.Items.ARROW));
        } catch (Exception ignored) {
        }
    }

    private void forceSelectHotbarSlot(ServerPlayer player, int slot) {
        grantService.forceSelectHotbarSlot(player, slot);
    }

    private int[] buildSlotRange(int startInclusive, int endInclusive) {
        int[] result = new int[Math.max(0, endInclusive - startInclusive + 1)];
        for (int i = 0; i < result.length; i++) {
            result[i] = startInclusive + i;
        }
        return result;
    }

    private ItemStack deserializeEquipmentItem(String itemSnbt) {
        return grantService.deserializeEquipmentItem(itemSnbt);
    }

    private boolean removeTotems(ServerPlayer player) {
        return grantService.removeTotems(player);
    }

    private RoomRuntimeContext resolveRoomRuntimeContext(ServerPlayer player) {
        if (player == null || player.server == null) {
            return RoomRuntimeContext.EMPTY;
        }
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(player.server);
        String roomId = manager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (roomId == null || roomId.isBlank()) {
            return RoomRuntimeContext.EMPTY;
        }
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        RunningRuntimeState state = manager.findRunningState(roomId).orElse(null);
        return new RoomRuntimeContext(room, state);
    }

    private record PreparedGunmasterWeapon(ItemStack stack, boolean ignored) {
    }

    private record RoomRuntimeContext(RoomDefinition room, RunningRuntimeState state) {
        private static final RoomRuntimeContext EMPTY = new RoomRuntimeContext(null, null);
    }
}
