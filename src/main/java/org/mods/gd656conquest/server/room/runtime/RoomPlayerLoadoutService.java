package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.Instruments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareCompatBridge;
import org.mods.gd656conquest.server.compat.curios.CuriosCompatBridge;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.attachment.WeaponAttachmentDataModel;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataStore;
import org.mods.gd656conquest.server.data.equipmentset.EquipmentSetDataModel;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataStore;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.loadout.TaczAttachmentCompatService;
import org.mods.gd656conquest.server.loadout.application.WeaponAttachmentService;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RoomPlayerLoadoutService {
    private static final String RANDOM_ATTACHMENT_NONE_SELECTION_ID = "__none__";
    private static final int RANDOM_ATTACHMENT_WITH_ITEM_WEIGHT = 3;
    private static final int RANDOM_ATTACHMENT_NONE_WEIGHT = 1;
    private static final int PRIMARY_WEAPON_HOTBAR_SLOT = 0;
    private static final int SECONDARY_WEAPON_HOTBAR_SLOT = 1;
    private static final int MELEE_HOTBAR_SLOT = 2;
    private static final int GADGET_1_HOTBAR_SLOT = 3;
    private static final int GADGET_2_HOTBAR_SLOT = 4;
    private static final int THROWABLE_HOTBAR_SLOT = 5;
    private static final int GOAT_HORN_HOTBAR_SLOT = 7;
    private static final int SUPPORT_HOTBAR_SLOT = 8;
    private static final int PRIMARY_AMMO_ROW_START = 18;
    private static final int PRIMARY_AMMO_ROW_END = 26;
    private static final int SECONDARY_AMMO_ROW_START = 29;
    private static final int SECONDARY_AMMO_ROW_END = 35;
    private static final int OTHER_GUN_AMMO_ROW_START = 31;
    private static final int OTHER_GUN_AMMO_ROW_END = 35;
    private static final ResourceLocation AMMO_BAG_PICKUP_SOUND_ID = ResourceLocation.fromNamespaceAndPath("gd656conquest", "sfx.ammo_bag_pickup");
    private static final List<ResourceKey<Instrument>> ROOM_GOAT_HORN_VARIANTS = List.of(
        Instruments.PONDER_GOAT_HORN,
        Instruments.SING_GOAT_HORN,
        Instruments.SEEK_GOAT_HORN,
        Instruments.FEEL_GOAT_HORN,
        Instruments.ADMIRE_GOAT_HORN,
        Instruments.CALL_GOAT_HORN,
        Instruments.YEARN_GOAT_HORN,
        Instruments.DREAM_GOAT_HORN
    );

    private final MinecraftServer server;
    private final RoomLoadoutGrantService grantService;

    public RoomPlayerLoadoutService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.grantService = new RoomLoadoutGrantService(server);
    }

    public void applySpawnLoadout(RunningRuntimeState state, ServerPlayer player) {
        if (state == null || player == null || player.server == null) {
            return;
        }
        RoomDefinition room = resolvePlayerRoom(player);
        applyLoadout(room, state, state.getRoomTeams().get(player.getUUID()), player, true, true, true, true);
        initializeConsumableReplenishBaseline(state, player, server.getTickCount());
    }

    public void applyStandaloneLoadout(ServerPlayer player, boolean grantTaczAmmo) {
        if (player == null || player.server == null) {
            return;
        }
        applyLoadout(null, null, null, player, grantTaczAmmo, false, false, true);
    }

    public boolean tryRefillLoadoutByAmmoBox(RoomTeamId teamId, ServerPlayer player) {
        return org.mods.gd656conquest.server.support.SupportAmmoRefill.box(teamId, player);
    }

    private void applyLoadout(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId teamId,
        ServerPlayer player,
        boolean grantTaczAmmo,
        boolean grantArmor,
        boolean refreshRandomSelection,
        boolean moveTotemToOffhand
    ) {
        if (player == null || player.server == null) {
            return;
        }
        RoomRuntimeContext roomContext = room == null || state == null ? resolveRoomRuntimeContext(player) : new RoomRuntimeContext(room, state);
        ConquestDataManager dataManager = ConquestDataManager.of(server);
        EquipmentDataStore equipmentDataHandler = dataManager.equipmentData();
        PlayerDataStore playerDataHandler = dataManager.playerData();
        EquipmentDataModel equipmentData = equipmentDataHandler.getData();
        if (equipmentData == null) {
            return;
        }
        int playerLevel = PlayerLevelService.getEffectiveLevel(player);
        if (CustomLoadoutService.sanitizePlayerSelection(player, equipmentData, null, false)) {
            equipmentData = equipmentDataHandler.getData();
        }
        PlayerDataModel playerData = playerDataHandler.getPlayerData(player.getUUID());
        WeaponAttachmentDataModel attachmentData = dataManager.weaponAttachments().getData();
        TaczAttachmentCompatService attachmentCompatService = TaczAttachmentCompatService.getInstance();
        ActiveLoadoutSelection selection = resolveActiveLoadoutSelection(
            roomContext,
            equipmentData,
            playerData,
            player,
            teamId,
            playerLevel,
            refreshRandomSelection,
            true
        );
        PlayerClassType classType = selection.classType();

        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        player.getInventory().clearContent();
        for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
            EquipmentDataModel.EquipmentEntry selectedEntry = selection.entriesBySlot().get(slot);
            if (selectedEntry == null) {
                continue;
            }
            ItemStack equipmentStack = deserializeEquipmentItem(selectedEntry.getItemSnbt());
            if (equipmentStack.isEmpty()) {
                continue;
            }
            PreparedLoadoutStack preparedStack = prepareConfiguredLoadoutStack(
                slot,
                selectedEntry,
                equipmentStack,
                playerData,
                attachmentData,
                playerLevel,
                attachmentCompatService,
                roomContext,
                player,
                refreshRandomSelection,
                !selection.randomized()
            );
            ItemStack placedStack = placeLoadoutItem(player, slot, preparedStack.stack());
            if (grantTaczAmmo && slot == CustomLoadoutSlot.PRIMARY_WEAPON) {
                grantConfiguredWeaponAmmoByExtraData(player, placedStack, selectedEntry.getExtraData(), buildSlotRange(PRIMARY_AMMO_ROW_START, PRIMARY_AMMO_ROW_END));
            } else if (grantTaczAmmo && slot == CustomLoadoutSlot.SECONDARY_WEAPON) {
                grantConfiguredWeaponAmmoByExtraData(player, placedStack, selectedEntry.getExtraData(), buildSlotRange(SECONDARY_AMMO_ROW_START, SECONDARY_AMMO_ROW_END));
            } else if (grantTaczAmmo) {
                grantConfiguredWeaponAmmoByExtraData(player, placedStack, selectedEntry.getExtraData(), resolveOtherGunAmmoSlots(slot));
            }
        }
        if (grantArmor && teamId != null) {
            applyArmorLoadout(player, roomContext.room(), equipmentData, classType, teamId);
        }
        if (roomContext.room() != null && roomContext.room().commonConfig().disableUndyingTotem()) {
            if (removeTotems(player)) {
                org.mods.gd656conquest.server.shared.log.ServerLogger.sendToClient(
                    player,
                    org.mods.gd656conquest.common.log.ClientMessageType.WARNING,
                    "当前房间已禁用不死图腾"
                );
            }
        } else if (moveTotemToOffhand) {
            moveConfiguredTotemToOffhand(player);
        }
        grantService.applyOptionalRoomItems(player, roomContext.room());
        if (selection.playerDataChanged()) {
            playerDataHandler.savePlayerData(player.getUUID(), playerData);
        }
        org.mods.gd656conquest.server.support.SupportAmmoGrant.finishLoadout(player);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private AmmoBoxBlockedState snapshotAmmoBoxBlockedState(ServerPlayer player) {
        if (player == null || player.server == null) {
            return AmmoBoxBlockedState.empty();
        }
        ConquestDataManager dataManager = ConquestDataManager.of(server);
        EquipmentDataModel equipmentData = dataManager.equipmentData().getData();
        if (equipmentData == null) {
            return AmmoBoxBlockedState.empty();
        }
        if (CustomLoadoutService.sanitizePlayerSelection(player, equipmentData, null, false)) {
            equipmentData = dataManager.equipmentData().getData();
        }
        PlayerDataModel playerData = dataManager.playerData().getPlayerData(player.getUUID());
        int playerLevel = PlayerLevelService.getEffectiveLevel(player);
        RoomRuntimeContext roomContext = resolveRoomRuntimeContext(player);
        ActiveLoadoutSelection selection = resolveActiveLoadoutSelection(
            roomContext,
            equipmentData,
            playerData,
            player,
            resolveRoomTeamId(roomContext, player),
            playerLevel,
            false,
            false
        );
        Map<Integer, ItemStack> snapshots = new HashMap<>();
        boolean blockTotemOffhandAutoMove = false;
        for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
            EquipmentDataModel.EquipmentEntry selectedEntry = selection.entriesBySlot().get(slot);
            if (selectedEntry == null || selectedEntry.isAmmoBoxRefillEnabled()) {
                continue;
            }
            int hotbarSlot = getHotbarSlot(slot);
            if (hotbarSlot < 0 || hotbarSlot >= player.getInventory().items.size()) {
                continue;
            }
            snapshots.put(hotbarSlot, player.getInventory().getItem(hotbarSlot).copy());
            ItemStack configuredStack = deserializeEquipmentItem(selectedEntry.getItemSnbt());
            if (!configuredStack.isEmpty() && configuredStack.is(Items.TOTEM_OF_UNDYING)) {
                blockTotemOffhandAutoMove = true;
            }
        }
        ItemStack offhandSnapshot = blockTotemOffhandAutoMove ? player.getOffhandItem().copy() : ItemStack.EMPTY;
        return new AmmoBoxBlockedState(Map.copyOf(snapshots), offhandSnapshot, blockTotemOffhandAutoMove);
    }

    private void restoreBlockedState(ServerPlayer player, AmmoBoxBlockedState blockedState) {
        if (player == null || blockedState == null) {
            return;
        }
        for (Map.Entry<Integer, ItemStack> entry : blockedState.slotSnapshots().entrySet()) {
            Integer slot = entry.getKey();
            if (slot == null || slot < 0 || slot >= player.getInventory().items.size()) {
                continue;
            }
            ItemStack restore = entry.getValue();
            player.getInventory().setItem(slot, restore == null ? ItemStack.EMPTY : restore.copy());
        }
        if (blockedState.blockTotemOffhandAutoMove()) {
            ItemStack offhandSnapshot = blockedState.offhandSnapshot();
            player.setItemSlot(EquipmentSlot.OFFHAND, offhandSnapshot == null ? ItemStack.EMPTY : offhandSnapshot.copy());
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private void applyArmorLoadout(ServerPlayer player, RoomDefinition room, EquipmentDataModel equipmentData, PlayerClassType classType, RoomTeamId teamId) {
        grantService.applyArmorLoadout(player, room, equipmentData, classType, teamId);
    }

    private ItemStack deserializeEquipmentItem(String itemSnbt) {
        return grantService.deserializeEquipmentItem(itemSnbt);
    }

    private PreparedLoadoutStack prepareConfiguredLoadoutStack(
        CustomLoadoutSlot slot,
        EquipmentDataModel.EquipmentEntry entry,
        ItemStack equipmentStack,
        PlayerDataModel playerData,
        WeaponAttachmentDataModel attachmentData,
        int playerLevel,
        TaczAttachmentCompatService attachmentCompatService,
        RoomRuntimeContext roomContext,
        ServerPlayer player,
        boolean refreshRandomSelection,
        boolean applyStoredAttachments
    ) {
        RoomLoadoutGrantService.PreparedWeaponStack prepared = grantService.prepareConfiguredWeaponStack(
            slot,
            entry,
            equipmentStack,
            playerData,
            attachmentData,
            playerLevel,
            roomContext == null ? null : roomContext.state(),
            player,
            isRandomizedAttachmentEnabled(roomContext),
            refreshRandomSelection,
            applyStoredAttachments
        );
        return new PreparedLoadoutStack(prepared.stack(), prepared.playerDataChanged());
    }

    public Map<CustomLoadoutSlot, ResolvedLoadoutItem> resolveConfiguredLoadoutItems(ServerPlayer player) {
        if (player == null || player.server == null) {
            return Map.of();
        }
        ConquestDataManager dataManager = ConquestDataManager.of(server);
        EquipmentDataStore equipmentDataHandler = dataManager.equipmentData();
        PlayerDataStore playerDataHandler = dataManager.playerData();
        EquipmentDataModel equipmentData = equipmentDataHandler.getData();
        if (equipmentData == null) {
            return Map.of();
        }
        if (CustomLoadoutService.sanitizePlayerSelection(player, equipmentData, null, false)) {
            equipmentData = equipmentDataHandler.getData();
        }
        PlayerDataModel playerData = playerDataHandler.getPlayerData(player.getUUID());
        int playerLevel = PlayerLevelService.getEffectiveLevel(player);
        RoomRuntimeContext roomContext = resolveRoomRuntimeContext(player);
        ActiveLoadoutSelection selection = resolveActiveLoadoutSelection(
            roomContext,
            equipmentData,
            playerData,
            player,
            roomContext == null || roomContext.state() == null ? null : roomContext.state().getRoomTeams().get(player.getUUID()),
            playerLevel,
            false,
            false
        );
        Map<CustomLoadoutSlot, ResolvedLoadoutItem> resolved = new EnumMap<>(CustomLoadoutSlot.class);
        for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
            EquipmentDataModel.EquipmentEntry entry = selection.entriesBySlot().get(slot);
            if (entry == null) {
                continue;
            }
            resolved.put(slot, new ResolvedLoadoutItem(
                slot,
                getHotbarSlot(slot),
                entry,
                deserializeEquipmentItem(entry.getItemSnbt())
            ));
        }
        if (selection.playerDataChanged()) {
            playerDataHandler.savePlayerData(player.getUUID(), playerData);
        }
        return resolved;
    }

    public boolean isTaczGunItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && resolveTaczAmmoMeta(stack) != null;
    }

    public boolean isSuperbWarfareGunItem(ItemStack stack) {
        return SuperbWarfareCompatBridge.isGunItem(stack);
    }

    public boolean isAmmoManagedGunItem(ItemStack stack) {
        return isTaczGunItem(stack) || isSuperbWarfareGunItem(stack);
    }

    public void initializeConsumableReplenishBaseline(RunningRuntimeState state, ServerPlayer player, long gameTick) {
        if (state == null || player == null) {
            return;
        }
        Map<Integer, Long> ticksBySlot = state.getLoadoutItemLastReplenishTickByPlayerSlot()
            .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        ticksBySlot.clear();
        if (gameTick <= 0L) {
            return;
        }
        for (ResolvedLoadoutItem item : resolveConfiguredLoadoutItems(player).values()) {
            if (item == null || item.hotbarSlot() < 0 || item.entry() == null) {
                continue;
            }
            if (item.entry().getReplenishSeconds() <= 0 || item.configuredStack().isEmpty() || isAmmoManagedGunItem(item.configuredStack())) {
                continue;
            }
            if (item.configuredStack().is(Items.TOTEM_OF_UNDYING)) {
                continue;
            }
            ticksBySlot.put(item.slot().getIndex(), gameTick);
        }
    }

    public AmmoSupplyResult trySupplyAmmoByBag(ServerPlayer player, int maxRounds) {
        int added = org.mods.gd656conquest.server.support.SupportAmmoRefill.bag(player, maxRounds);
        if (added <= 0) return AmmoSupplyResult.EMPTY;
        org.mods.gd656conquest.server.support.SupportRuntime.ammoSupplied(player);
        return new AmmoSupplyResult(added);
    }

    private void grantConfiguredWeaponAmmoByExtraData(ServerPlayer player, ItemStack gunStack, int extraData, int[] preferredSlots) {
        grantService.grantConfiguredWeaponAmmoByExtraData(player, gunStack, extraData, preferredSlots);
    }

    private boolean grantTaczAmmoByExtraData(ServerPlayer player, ItemStack gunStack, int extraData, int[] preferredSlots) {
        if (player == null || gunStack == null || gunStack.isEmpty() || extraData <= 0 || preferredSlots == null || preferredSlots.length == 0) {
            return false;
        }
        TaczAmmoMeta ammoMeta = resolveTaczAmmoMeta(gunStack);
        if (ammoMeta == null || ammoMeta.ammoId() == null || ammoMeta.stackSize() <= 0) {
            return false;
        }
        ItemStack sampleAmmo = createTaczAmmoStack(ammoMeta.ammoId(), 1);
        if (sampleAmmo.isEmpty()) {
            return false;
        }
        addTaczAmmoToPreferredSlots(player, ammoMeta, sampleAmmo, extraData, preferredSlots);
        return true;
    }

    private void grantSuperbWarfareAmmoByExtraData(ItemStack gunStack, int extraData) {
        if (gunStack == null || gunStack.isEmpty() || extraData <= 0) {
            return;
        }
        SuperbWarfareCompatBridge.applyReserveAmmo(gunStack, extraData);
    }

    private int addSuperbWarfareAmmoToConfiguredSlot(ServerPlayer player, ResolvedLoadoutItem item, int amount) {
        if (player == null || item == null || amount <= 0) {
            return 0;
        }
        if (item.entry() == null || item.entry().getExtraData() <= 0) {
            return 0;
        }
        if (item.hotbarSlot() < 0 || item.hotbarSlot() >= player.getInventory().items.size()) {
            return 0;
        }
        ItemStack gunStack = player.getInventory().getItem(item.hotbarSlot());
        if (!SuperbWarfareCompatBridge.isGunItem(gunStack)) {
            return 0;
        }
        int currentReserveAmmo = SuperbWarfareCompatBridge.getReserveAmmo(gunStack);
        int targetReserveAmmo = Math.max(0, item.entry().getExtraData());
        int missing = Math.max(0, targetReserveAmmo - currentReserveAmmo);
        if (missing <= 0) {
            return 0;
        }
        int added = Math.min(amount, missing);
        return SuperbWarfareCompatBridge.applyReserveAmmo(gunStack, currentReserveAmmo + added) ? added : 0;
    }

    private int addTaczAmmoToPreferredSlots(ServerPlayer player, TaczAmmoMeta ammoMeta, ItemStack sampleAmmoStack, int amount, int[] preferredSlots) {
        return grantService.addTaczAmmoToPreferredSlots(
            player,
            ammoMeta == null ? null : new RoomLoadoutGrantService.TaczAmmoMeta(ammoMeta.ammoId(), ammoMeta.stackSize()),
            sampleAmmoStack,
            amount,
            preferredSlots
        );
    }

    private int countAmmoInInventory(ServerPlayer player, TaczAmmoMeta ammoMeta) {
        if (player == null || ammoMeta == null || ammoMeta.ammoId() == null) {
            return 0;
        }
        ItemStack sampleAmmo = createTaczAmmoStack(ammoMeta.ammoId(), 1);
        if (sampleAmmo.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack != null && !stack.isEmpty() && ItemStack.isSameItemSameTags(stack, sampleAmmo)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private LoadoutAmmoContext resolveLoadoutAmmoContext(
            ServerPlayer player,
            CustomLoadoutSlot slot,
            int hotbarSlot,
            int preferredStart,
            int preferredEnd
    ) {
        if (player == null || slot == null) {
            return null;
        }
        ConquestDataManager dataManager = ConquestDataManager.of(server);
        EquipmentDataStore equipmentDataHandler = dataManager.equipmentData();
        PlayerDataStore playerDataHandler = dataManager.playerData();
        EquipmentDataModel equipmentData = equipmentDataHandler.getData();
        if (equipmentData == null) {
            return null;
        }
        if (CustomLoadoutService.sanitizePlayerSelection(player, equipmentData, null, false)) {
            equipmentData = equipmentDataHandler.getData();
        }
        PlayerDataModel playerData = playerDataHandler.getPlayerData(player.getUUID());
        int playerLevel = PlayerLevelService.getEffectiveLevel(player);
        RoomRuntimeContext roomContext = resolveRoomRuntimeContext(player);
        ActiveLoadoutSelection selection = resolveActiveLoadoutSelection(
            roomContext,
            equipmentData,
            playerData,
            player,
            resolveRoomTeamId(roomContext, player),
            playerLevel,
            false,
            false
        );
        EquipmentDataModel.EquipmentEntry selectedEntry = selection.entriesBySlot().get(slot);

        int targetAmmo = selectedEntry == null ? 0 : Math.max(0, selectedEntry.getExtraData());
        ItemStack hotbarStack = (hotbarSlot >= 0 && hotbarSlot < player.getInventory().items.size())
                ? player.getInventory().getItem(hotbarSlot)
                : ItemStack.EMPTY;
        ItemStack fallbackLoadoutGun = selectedEntry == null ? ItemStack.EMPTY : deserializeEquipmentItem(selectedEntry.getItemSnbt());
        ItemStack sourceGun = !hotbarStack.isEmpty() ? hotbarStack : fallbackLoadoutGun;
        if (sourceGun.isEmpty()) {
            return null;
        }
        TaczAmmoMeta ammoMeta = resolveTaczAmmoMeta(sourceGun);
        if (ammoMeta == null || ammoMeta.ammoId() == null || ammoMeta.stackSize() <= 0) {
            return null;
        }
        return new LoadoutAmmoContext(slot, targetAmmo, ammoMeta, buildSlotRange(preferredStart, preferredEnd));
    }

    private EquipmentDataModel.EquipmentEntry resolveEffectiveLoadoutEntry(
            EquipmentDataModel equipmentData,
            PlayerDataModel playerData,
            PlayerClassType classType,
            CustomLoadoutSlot slot,
            int playerLevel
    ) {
        if (equipmentData == null || playerData == null || classType == null || slot == null) {
            return null;
        }
        String selectedId = CustomLoadoutService.getSelectedIdForClassSlot(playerData, classType, slot);
        EquipmentDataModel.EquipmentEntry selectedEntry = equipmentData.getEquipmentById().get(selectedId);
        boolean valid = selectedEntry != null
                && selectedEntry.getSlot() == slot.getIndex()
                && CustomLoadoutService.isEntryUnlocked(selectedEntry, playerLevel)
                && CustomLoadoutService.isEntryAvailableForClass(selectedEntry, classType);
        if (valid) {
            return selectedEntry;
        }
        String fallbackId = CustomLoadoutService.resolveFallbackIdForClassSlot(equipmentData, classType, slot, playerLevel);
        if (fallbackId == null || fallbackId.isBlank()) {
            return null;
        }
        EquipmentDataModel.EquipmentEntry fallbackEntry = equipmentData.getEquipmentById().get(fallbackId);
        if (fallbackEntry == null || fallbackEntry.getSlot() != slot.getIndex()) {
            return null;
        }
        return fallbackEntry;
    }

    private ActiveLoadoutSelection resolveActiveLoadoutSelection(
        RoomRuntimeContext roomContext,
        EquipmentDataModel equipmentData,
        PlayerDataModel playerData,
        ServerPlayer player,
        RoomTeamId teamId,
        int playerLevel,
        boolean refreshRandomSelection,
        boolean notifyPlayer
    ) {
        if (equipmentData == null || player == null) {
            return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), false, false, false);
        }
        if (isRandomizedLoadoutEnabled(roomContext)) {
            return resolveRandomizedLoadoutSelection(roomContext, equipmentData, player, teamId, refreshRandomSelection, notifyPlayer);
        }
        PlayerClassType selectedClassType = CustomLoadoutService.resolveCurrentClass(playerData);
        ActiveLoadoutSelection selection = resolveManualLoadoutSelectionForClass(
            equipmentData,
            playerData,
            selectedClassType,
            playerLevel,
            false
        );
        return applyRoomLoadoutRestrictions(roomContext, equipmentData, player, teamId, selection, notifyPlayer);
    }

    private ActiveLoadoutSelection resolveManualLoadoutSelectionForClass(
        EquipmentDataModel equipmentData,
        PlayerDataModel playerData,
        PlayerClassType classType,
        int playerLevel,
        boolean classForcedByRoom
    ) {
        if (equipmentData == null || classType == null) {
            return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), false, false, classForcedByRoom);
        }
        Map<CustomLoadoutSlot, EquipmentDataModel.EquipmentEntry> entriesBySlot = new EnumMap<>(CustomLoadoutSlot.class);
        boolean playerDataChanged = false;
        for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
            String selectedId = CustomLoadoutService.getSelectedIdForClassSlot(playerData, classType, slot);
            EquipmentDataModel.EquipmentEntry selectedEntry = equipmentData.getEquipmentById().get(selectedId);
            boolean valid = selectedEntry != null
                && selectedEntry.getSlot() == slot.getIndex()
                && CustomLoadoutService.isEntryUnlocked(selectedEntry, playerLevel)
                && CustomLoadoutService.isEntryAvailableForClass(selectedEntry, classType);
            if (!valid) {
                String fallbackId = CustomLoadoutService.resolveFallbackIdForClassSlot(equipmentData, classType, slot, playerLevel);
                if (fallbackId == null || fallbackId.isBlank()) {
                    if (!selectedId.isBlank()) {
                        CustomLoadoutService.setSelectedIdForClassSlot(playerData, classType, slot, "");
                        playerDataChanged = true;
                    }
                    continue;
                }
                selectedEntry = equipmentData.getEquipmentById().get(fallbackId);
                CustomLoadoutService.setSelectedIdForClassSlot(playerData, classType, slot, fallbackId);
                playerDataChanged = true;
                if (selectedEntry == null || selectedEntry.getSlot() != slot.getIndex()) {
                    continue;
                }
            }
            entriesBySlot.put(slot, selectedEntry);
        }
        return new ActiveLoadoutSelection(classType, entriesBySlot, false, playerDataChanged, classForcedByRoom);
    }

    private ActiveLoadoutSelection resolveRandomizedLoadoutSelection(
        RoomRuntimeContext roomContext,
        EquipmentDataModel equipmentData,
        ServerPlayer player,
        RoomTeamId teamId,
        boolean refreshRandomSelection,
        boolean notifyPlayer
    ) {
        RunningRuntimeState state = roomContext == null ? null : roomContext.state();
        if (state == null || equipmentData == null || player == null) {
            return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), true, false, false);
        }
        UUID playerId = player.getUUID();
        if (!refreshRandomSelection) {
            ActiveLoadoutSelection existing = resolveStoredRandomizedLoadoutSelection(state, equipmentData, playerId);
            if (!existing.entriesBySlot().isEmpty()) {
                return applyRoomLoadoutRestrictions(roomContext, equipmentData, player, teamId, existing, notifyPlayer);
            }
        }
        List<PlayerClassType> fullCoverageClasses = new ArrayList<>();
        List<PlayerClassType> partialCoverageClasses = new ArrayList<>();
        for (PlayerClassType classType : PlayerClassType.values()) {
            int coveredSlots = 0;
            for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
                if (!listRandomSelectableIds(roomContext, equipmentData, teamId, classType, slot).isEmpty()) {
                    coveredSlots++;
                }
            }
            if (coveredSlots >= CustomLoadoutSlot.values().length) {
                fullCoverageClasses.add(classType);
            } else if (coveredSlots > 0) {
                partialCoverageClasses.add(classType);
            }
        }
        List<PlayerClassType> candidates = !fullCoverageClasses.isEmpty() ? fullCoverageClasses : partialCoverageClasses;
        if (candidates.isEmpty()) {
            state.getRandomizedLoadoutClassKeysByPlayer().remove(playerId);
            state.getRandomizedLoadoutSelectionIdsByPlayerSlot().remove(playerId);
            return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), true, false, false);
        }
        PlayerClassType chosenClass = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Map<CustomLoadoutSlot, EquipmentDataModel.EquipmentEntry> entriesBySlot = new EnumMap<>(CustomLoadoutSlot.class);
        Map<String, String> storedSelectionIds = new LinkedHashMap<>();
        for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
            List<String> candidateIds = listRandomSelectableIds(roomContext, equipmentData, teamId, chosenClass, slot);
            if (candidateIds.isEmpty()) {
                continue;
            }
            String selectedId = candidateIds.get(ThreadLocalRandom.current().nextInt(candidateIds.size()));
            if (CustomLoadoutService.isReservedEmptyEquipmentId(selectedId)) {
                storedSelectionIds.put(slot.getKey(), CustomLoadoutService.EMPTY_EQUIPMENT_ID);
                continue;
            }
            EquipmentDataModel.EquipmentEntry selectedEntry = equipmentData.getEquipmentById().get(CustomLoadoutService.normalizeEquipmentId(selectedId));
            if (selectedEntry == null) {
                continue;
            }
            entriesBySlot.put(slot, selectedEntry);
            storedSelectionIds.put(slot.getKey(), CustomLoadoutService.normalizeEquipmentId(selectedEntry.getId()));
        }
        state.getRandomizedLoadoutClassKeysByPlayer().put(playerId, chosenClass.getKey());
        state.getRandomizedLoadoutSelectionIdsByPlayerSlot().put(playerId, storedSelectionIds);
        return applyRoomLoadoutRestrictions(roomContext, equipmentData, player, teamId, new ActiveLoadoutSelection(chosenClass, entriesBySlot, true, false, false), notifyPlayer);
    }

    private ActiveLoadoutSelection resolveStoredRandomizedLoadoutSelection(
        RunningRuntimeState state,
        EquipmentDataModel equipmentData,
        UUID playerId
    ) {
        if (state == null || equipmentData == null || playerId == null) {
            return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), true, false, false);
        }
        PlayerClassType classType = PlayerClassType.fromInput(state.getRandomizedLoadoutClassKeysByPlayer().get(playerId));
        Map<String, String> storedIds = state.getRandomizedLoadoutSelectionIdsByPlayerSlot().get(playerId);
        if (classType == null || storedIds == null || storedIds.isEmpty()) {
            return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), true, false, false);
        }
        Map<CustomLoadoutSlot, EquipmentDataModel.EquipmentEntry> entriesBySlot = new EnumMap<>(CustomLoadoutSlot.class);
        for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
            String selectedId = CustomLoadoutService.normalizeEquipmentId(storedIds.get(slot.getKey()));
            if (selectedId.isBlank()) {
                continue;
            }
            if (CustomLoadoutService.isReservedEmptyEquipmentId(selectedId)) {
                continue;
            }
            EquipmentDataModel.EquipmentEntry entry = equipmentData.getEquipmentById().get(selectedId);
            if (entry == null
                || entry.getSlot() != slot.getIndex()
                || !CustomLoadoutService.isEntryAvailableForClass(entry, classType)) {
                return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), true, false, false);
            }
            entriesBySlot.put(slot, entry);
        }
        return new ActiveLoadoutSelection(classType, entriesBySlot, true, false, false);
    }

    private boolean isRandomizedLoadoutEnabled(RoomRuntimeContext roomContext) {
        return roomContext != null
            && roomContext.room() != null
            && roomContext.state() != null
            && roomContext.room().commonConfig().enableRandomLoadout();
    }

    private ActiveLoadoutSelection applyRoomLoadoutRestrictions(
        RoomRuntimeContext roomContext,
        EquipmentDataModel equipmentData,
        ServerPlayer player,
        RoomTeamId teamId,
        ActiveLoadoutSelection selection,
        boolean notifyPlayer
    ) {
        if (selection == null || roomContext == null || roomContext.room() == null) {
            return selection;
        }

        PlayerClassType classType = selection.classType();
        Map<CustomLoadoutSlot, EquipmentDataModel.EquipmentEntry> entries = new EnumMap<>(CustomLoadoutSlot.class);
        entries.putAll(selection.entriesBySlot());
        for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
            if (!RoomFixedLoadoutRules.hasRestriction(roomContext.room(), teamId, slot)) {
                continue;
            }
            EquipmentDataModel.EquipmentEntry resolvedEntry = resolveRoomRestrictedEntry(
                roomContext,
                equipmentData,
                player,
                teamId,
                classType,
                slot,
                entries.get(slot),
                selection.randomized()
            );
            if (resolvedEntry == null) {
                entries.remove(slot);
            } else {
                entries.put(slot, resolvedEntry);
            }
        }

        return new ActiveLoadoutSelection(classType, entries, selection.randomized(), selection.playerDataChanged(), selection.classForcedByRoom());
    }

    private EquipmentDataModel.EquipmentEntry resolveRoomRestrictedEntry(
        RoomRuntimeContext roomContext,
        EquipmentDataModel equipmentData,
        ServerPlayer player,
        RoomTeamId teamId,
        PlayerClassType classType,
        CustomLoadoutSlot slot,
        EquipmentDataModel.EquipmentEntry currentEntry,
        boolean randomizedSelection
    ) {
        if (roomContext == null || roomContext.room() == null || equipmentData == null || teamId == null || slot == null) {
            return currentEntry;
        }
        RoomDefinition room = roomContext.room();
        if (!RoomFixedLoadoutRules.hasRestriction(room, teamId, slot)) {
            return currentEntry;
        }
        int playerLevel = player == null ? 0 : PlayerLevelService.getEffectiveLevel(player);
        if (!randomizedSelection) {
            String roomSelectedId = CustomLoadoutService.getSelectedIdForRoomSlot(
                roomContext.state(),
                player == null ? null : player.getUUID(),
                classType,
                slot
            );
            if (!roomSelectedId.isBlank()) {
                if (CustomLoadoutService.isReservedEmptyEquipmentId(roomSelectedId)) {
                    return RoomFixedLoadoutRules.allowsEmpty(room, teamId, slot)
                        ? null
                        : resolveRoomRestrictionFallback(roomContext, player, teamId, classType, slot, equipmentData, playerLevel, false);
                }
                EquipmentDataModel.EquipmentEntry roomSelectedEntry = equipmentData.getEquipmentById().get(roomSelectedId);
                if (roomSelectedEntry != null
                    && roomSelectedEntry.getSlot() == slot.getIndex()
                    && CustomLoadoutService.isEntryUnlocked(roomSelectedEntry, playerLevel)
                    && RoomFixedLoadoutRules.allowsEntryId(room, teamId, slot, roomSelectedId)) {
                    return roomSelectedEntry;
                }
                CustomLoadoutService.setSelectedIdForRoomSlot(roomContext.state(), player == null ? null : player.getUUID(), classType, slot, "");
            }
        }
        String currentId = currentEntry == null ? "" : CustomLoadoutService.normalizeEquipmentId(currentEntry.getId());
        if (!currentId.isBlank() && RoomFixedLoadoutRules.allowsEntryId(room, teamId, slot, currentId)) {
            return currentEntry;
        }
        return resolveRoomRestrictionFallback(roomContext, player, teamId, classType, slot, equipmentData, playerLevel, randomizedSelection);
    }

    private EquipmentDataModel.EquipmentEntry resolveRoomRestrictionFallback(
        RoomRuntimeContext roomContext,
        ServerPlayer player,
        RoomTeamId teamId,
        PlayerClassType classType,
        CustomLoadoutSlot slot,
        EquipmentDataModel equipmentData,
        int playerLevel,
        boolean ignoreUnlockLevel
    ) {
        RoomDefinition room = roomContext == null ? null : roomContext.room();
        EquipmentDataModel.EquipmentEntry fallbackEntry = RoomFixedLoadoutRules.resolveFallbackEntry(room, equipmentData, teamId, slot, playerLevel, ignoreUnlockLevel);
        if (fallbackEntry != null) {
            return fallbackEntry;
        }
        return RoomFixedLoadoutRules.allowsEmpty(room, teamId, slot) ? null : null;
    }

    private List<String> listRandomSelectableIds(
        RoomRuntimeContext roomContext,
        EquipmentDataModel equipmentData,
        RoomTeamId teamId,
        PlayerClassType classType,
        CustomLoadoutSlot slot
    ) {
        if (equipmentData == null || classType == null || slot == null) {
            return List.of();
        }
        if (roomContext != null && roomContext.room() != null && RoomFixedLoadoutRules.hasRestriction(roomContext.room(), teamId, slot)) {
            List<String> ids = new ArrayList<>();
            for (String allowedId : RoomFixedLoadoutRules.idsForTeamSlot(roomContext.room(), teamId, slot)) {
                if (CustomLoadoutService.isReservedEmptyEquipmentId(allowedId)) {
                    ids.add(CustomLoadoutService.EMPTY_EQUIPMENT_ID);
                    continue;
                }
                EquipmentDataModel.EquipmentEntry entry = equipmentData.getEquipmentById().get(CustomLoadoutService.normalizeEquipmentId(allowedId));
                if (entry != null && entry.getSlot() == slot.getIndex()) {
                    ids.add(CustomLoadoutService.normalizeEquipmentId(entry.getId()));
                }
            }
            return ids;
        }
        List<String> ids = new ArrayList<>();
        for (EquipmentDataModel.EquipmentEntry entry : CustomLoadoutService.listEntriesByClassAndSlot(equipmentData, classType, slot)) {
            if (entry == null) {
                continue;
            }
            String normalizedId = CustomLoadoutService.normalizeEquipmentId(entry.getId());
            if (!normalizedId.isBlank()) {
                ids.add(normalizedId);
            }
        }
        return ids;
    }

    private boolean isRandomizedAttachmentEnabled(RoomRuntimeContext roomContext) {
        return roomContext != null
            && roomContext.room() != null
            && roomContext.state() != null
            && roomContext.room().commonConfig().enableRandomAttachments();
    }

    private int[] buildSlotRange(int startInclusive, int endInclusive) {
        if (endInclusive < startInclusive) {
            return new int[0];
        }
        int[] result = new int[endInclusive - startInclusive + 1];
        for (int i = 0; i < result.length; i++) {
            result[i] = startInclusive + i;
        }
        return result;
    }

    private int[] resolveOtherGunAmmoSlots(CustomLoadoutSlot slot) {
        if (slot == null) {
            return new int[0];
        }
        int mappedSlot = OTHER_GUN_AMMO_ROW_START + Math.max(0, slot.getIndex() - 3);
        if (mappedSlot > OTHER_GUN_AMMO_ROW_END) {
            return new int[0];
        }
        return new int[]{mappedSlot};
    }

    private TaczAmmoMeta resolveTaczAmmoMeta(ItemStack gunStack) {
        RoomLoadoutGrantService.TaczAmmoMeta ammoMeta = grantService.resolveTaczAmmoMeta(gunStack);
        return ammoMeta == null ? null : new TaczAmmoMeta(ammoMeta.ammoId(), ammoMeta.stackSize());
    }

    private ItemStack createTaczAmmoStack(ResourceLocation ammoId, int count) {
        return grantService.createTaczAmmoStack(ammoId, count);
    }

    private ItemStack placeLoadoutItem(ServerPlayer player, CustomLoadoutSlot slot, ItemStack stack) {
        org.mods.gd656conquest.server.support.SupportRuntime.prepareLoadoutStack(stack);
        if (player == null || slot == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int targetSlot = getHotbarSlot(slot);
        if (targetSlot >= 0 && targetSlot < player.getInventory().items.size()) {
            player.getInventory().setItem(targetSlot, stack.copy());
            return player.getInventory().getItem(targetSlot);
        }
        player.getInventory().add(stack.copy());
        return stack;
    }

    private boolean removeTotems(ServerPlayer player) {
        return grantService.removeTotems(player);
    }

    private void moveConfiguredTotemToOffhand(ServerPlayer player) {
        grantService.moveConfiguredTotemToOffhand(player);
    }

    private RoomDefinition resolvePlayerRoom(ServerPlayer player) {
        return resolveRoomRuntimeContext(player).room();
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

    private RoomTeamId resolveRoomTeamId(RoomRuntimeContext roomContext, ServerPlayer player) {
        if (roomContext == null || roomContext.state() == null || player == null) {
            return null;
        }
        return roomContext.state().getRoomTeams().get(player.getUUID());
    }

    private ItemStack createRandomRoomGoatHorn(ServerPlayer player) {
        if (player == null || player.server == null || ROOM_GOAT_HORN_VARIANTS.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Registry<Instrument> instrumentRegistry = player.server.registryAccess().registryOrThrow(Registries.INSTRUMENT);
        ResourceKey<Instrument> selectedKey = ROOM_GOAT_HORN_VARIANTS.get(player.getRandom().nextInt(ROOM_GOAT_HORN_VARIANTS.size()));
        Holder.Reference<Instrument> selectedInstrument = instrumentRegistry.getHolder(selectedKey).orElse(null);
        if (selectedInstrument == null) {
            return new ItemStack(Items.GOAT_HORN);
        }
        return InstrumentItem.create(Items.GOAT_HORN, selectedInstrument);
    }

    private int getHotbarSlot(CustomLoadoutSlot slot) {
        if (slot == null) {
            return -1;
        }
        return switch (slot) {
            case PRIMARY_WEAPON -> PRIMARY_WEAPON_HOTBAR_SLOT;
            case SECONDARY_WEAPON -> SECONDARY_WEAPON_HOTBAR_SLOT;
            case MELEE -> MELEE_HOTBAR_SLOT;
            case GADGET_1 -> GADGET_1_HOTBAR_SLOT;
            case GADGET_2 -> GADGET_2_HOTBAR_SLOT;
            case THROWABLE -> THROWABLE_HOTBAR_SLOT;
            default -> -1;
        };
    }

    public record AmmoSupplyResult(int totalAdded) {
        public static final AmmoSupplyResult EMPTY = new AmmoSupplyResult(0);
    }

    private record LoadoutAmmoContext(
            CustomLoadoutSlot slot,
            int targetAmmo,
            TaczAmmoMeta ammoMeta,
            int[] preferredSlots
    ) {
    }

    private record TaczAmmoMeta(ResourceLocation ammoId, int stackSize) {
    }

    private record PreparedLoadoutStack(ItemStack stack, boolean playerDataChanged) {
    }

    private record ActiveLoadoutSelection(
        PlayerClassType classType,
        Map<CustomLoadoutSlot, EquipmentDataModel.EquipmentEntry> entriesBySlot,
        boolean randomized,
        boolean playerDataChanged,
        boolean classForcedByRoom
    ) {
    }

    private record RoomRuntimeContext(RoomDefinition room, RunningRuntimeState state) {
        private static final RoomRuntimeContext EMPTY = new RoomRuntimeContext(null, null);
    }

    public record ResolvedLoadoutItem(
        CustomLoadoutSlot slot,
        int hotbarSlot,
        EquipmentDataModel.EquipmentEntry entry,
        ItemStack configuredStack
    ) {
    }

    private record AmmoBoxBlockedState(
        Map<Integer, ItemStack> slotSnapshots,
        ItemStack offhandSnapshot,
        boolean blockTotemOffhandAutoMove
    ) {
        private static AmmoBoxBlockedState empty() {
            return new AmmoBoxBlockedState(Map.of(), ItemStack.EMPTY, false);
        }
    }
}
