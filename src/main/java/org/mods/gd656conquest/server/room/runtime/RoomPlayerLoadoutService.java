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
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareCompatBridge;
import org.mods.gd656conquest.server.compat.curios.CuriosCompatBridge;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.attachment.WeaponAttachmentDataModel;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataStore;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RoomPlayerLoadoutService {
    private static final int PRIMARY_WEAPON_HOTBAR_SLOT = 0;
    private static final int SECONDARY_WEAPON_HOTBAR_SLOT = 1;
    private static final int MELEE_HOTBAR_SLOT = 2;
    private static final int GADGET_1_HOTBAR_SLOT = 3;
    private static final int GADGET_2_HOTBAR_SLOT = 4;
    private static final int THROWABLE_HOTBAR_SLOT = 5;
    private static final int GOAT_HORN_HOTBAR_SLOT = 8;
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

    public RoomPlayerLoadoutService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void applySpawnLoadout(RunningRuntimeState state, ServerPlayer player) {
        if (state == null || player == null || player.server == null) {
            return;
        }
        applyLoadout(resolvePlayerRoom(player), state, state.getRoomTeams().get(player.getUUID()), player, true, true, true);
        initializeConsumableReplenishBaseline(state, player, server.getTickCount());
    }

    public void applyStandaloneLoadout(ServerPlayer player, boolean grantTaczAmmo) {
        if (player == null || player.server == null) {
            return;
        }
        applyLoadout(null, null, null, player, grantTaczAmmo, false, false);
    }

    public boolean tryRefillLoadoutByAmmoBox(RoomTeamId teamId, ServerPlayer player) {
        if (teamId == null || player == null || player.server == null) {
            return false;
        }
        Map<Integer, ItemStack> blockedSnapshots = snapshotAmmoBoxBlockedSlots(player);
        RoomRuntimeContext roomContext = resolveRoomRuntimeContext(player);
        applyLoadout(roomContext.room(), roomContext.state(), teamId, player, true, true, false);
        restoreBlockedSlots(player, blockedSnapshots);
        return true;
    }

    private void applyLoadout(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId teamId,
        ServerPlayer player,
        boolean grantTaczAmmo,
        boolean grantArmor,
        boolean refreshRandomSelection
    ) {
        if (player == null || player.server == null) {
            return;
        }
        ConquestDataManager dataManager = ConquestDataManager.of(server);
        EquipmentDataStore equipmentDataHandler = dataManager.equipmentData();
        PlayerDataStore playerDataHandler = dataManager.playerData();
        EquipmentDataModel equipmentData = equipmentDataHandler.getData();
        if (equipmentData == null) {
            return;
        }
        RoomRuntimeContext roomContext = room == null || state == null ? resolveRoomRuntimeContext(player) : new RoomRuntimeContext(room, state);
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
            playerLevel,
            refreshRandomSelection
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
            applyArmorLoadout(player, equipmentData, classType, teamId);
        }
        moveConfiguredTotemToOffhand(player);
        applyOptionalRoomGoatHorn(player);
        applyOptionalParachute(roomContext, player);
        if (selection.playerDataChanged()) {
            playerDataHandler.savePlayerData(player.getUUID(), playerData);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private Map<Integer, ItemStack> snapshotAmmoBoxBlockedSlots(ServerPlayer player) {
        if (player == null || player.server == null) {
            return Map.of();
        }
        ConquestDataManager dataManager = ConquestDataManager.of(server);
        EquipmentDataModel equipmentData = dataManager.equipmentData().getData();
        if (equipmentData == null) {
            return Map.of();
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
            playerLevel,
            false
        );
        Map<Integer, ItemStack> snapshots = new HashMap<>();
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
        }
        return snapshots;
    }

    private void restoreBlockedSlots(ServerPlayer player, Map<Integer, ItemStack> snapshots) {
        if (player == null || snapshots == null || snapshots.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, ItemStack> entry : snapshots.entrySet()) {
            Integer slot = entry.getKey();
            if (slot == null || slot < 0 || slot >= player.getInventory().items.size()) {
                continue;
            }
            ItemStack restore = entry.getValue();
            player.getInventory().setItem(slot, restore == null ? ItemStack.EMPTY : restore.copy());
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private void applyArmorLoadout(ServerPlayer player, EquipmentDataModel equipmentData, PlayerClassType classType, RoomTeamId teamId) {
        if (player == null || equipmentData == null || classType == null || teamId == null) {
            return;
        }
        EquipmentDataModel.ArmorEntry entry = CustomLoadoutService.resolveArmorByCampClass(equipmentData, teamId, classType);
        if (entry == null) {
            return;
        }
        player.setItemSlot(EquipmentSlot.HEAD, deserializeEquipmentItem(entry.getHelmetSnbt()));
        player.setItemSlot(EquipmentSlot.CHEST, deserializeEquipmentItem(entry.getChestplateSnbt()));
        player.setItemSlot(EquipmentSlot.LEGS, deserializeEquipmentItem(entry.getLeggingsSnbt()));
        player.setItemSlot(EquipmentSlot.FEET, deserializeEquipmentItem(entry.getBootsSnbt()));
    }

    private ItemStack deserializeEquipmentItem(String itemSnbt) {
        if (itemSnbt == null || itemSnbt.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            CompoundTag tag = TagParser.parseTag(itemSnbt);
            return ItemStack.of(tag);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private PreparedLoadoutStack prepareConfiguredLoadoutStack(
        CustomLoadoutSlot slot,
        EquipmentDataModel.EquipmentEntry entry,
        ItemStack equipmentStack,
        PlayerDataModel playerData,
        WeaponAttachmentDataModel attachmentData,
        int playerLevel,
        TaczAttachmentCompatService attachmentCompatService,
        boolean applyStoredAttachments
    ) {
        if (equipmentStack == null || equipmentStack.isEmpty()) {
            return new PreparedLoadoutStack(ItemStack.EMPTY, false);
        }
        ItemStack preparedStack = equipmentStack.copy();
        if (!applyStoredAttachments) {
            return new PreparedLoadoutStack(preparedStack, false);
        }
        if (!WeaponAttachmentService.supportsSlot(slot)
            || entry == null
            || playerData == null
            || attachmentData == null
            || attachmentCompatService == null
            || !attachmentCompatService.isAvailable()) {
            return new PreparedLoadoutStack(preparedStack, false);
        }
        String loadoutEntryId = CustomLoadoutService.normalizeEquipmentId(entry.getId());
        if (loadoutEntryId.isBlank()) {
            return new PreparedLoadoutStack(preparedStack, false);
        }
        boolean playerDataChanged = WeaponAttachmentService.sanitizeSelectionForWeapon(
            playerData,
            loadoutEntryId,
            preparedStack,
            attachmentData,
            playerLevel,
            attachmentCompatService
        );
        if (!attachmentCompatService.isTaczGun(preparedStack)) {
            return new PreparedLoadoutStack(preparedStack, playerDataChanged);
        }
        applyStoredWeaponAttachments(preparedStack, playerData, loadoutEntryId, attachmentData, attachmentCompatService);
        return new PreparedLoadoutStack(preparedStack, playerDataChanged);
    }

    private void applyStoredWeaponAttachments(
        ItemStack gunStack,
        PlayerDataModel playerData,
        String loadoutEntryId,
        WeaponAttachmentDataModel attachmentData,
        TaczAttachmentCompatService attachmentCompatService
    ) {
        if (gunStack == null
            || gunStack.isEmpty()
            || playerData == null
            || attachmentData == null
            || attachmentCompatService == null
            || loadoutEntryId == null
            || loadoutEntryId.isBlank()) {
            return;
        }
        for (String attachmentType : WeaponAttachmentService.ORDERED_ATTACHMENT_TYPES) {
            if (!WeaponAttachmentService.hasStoredSelection(playerData, loadoutEntryId, attachmentType)
                || !attachmentCompatService.allowAttachmentType(gunStack, attachmentType)) {
                continue;
            }
            attachmentCompatService.unloadAttachment(gunStack, attachmentType);
            String attachmentId = WeaponAttachmentService.getSelectedAttachmentId(playerData, loadoutEntryId, attachmentType);
            if (attachmentId.isBlank()) {
                continue;
            }
            WeaponAttachmentDataModel.WeaponAttachmentEntry attachmentEntry = attachmentData.getAttachmentById().get(attachmentId);
            if (attachmentEntry == null) {
                continue;
            }
            ItemStack attachmentStack = attachmentCompatService.deserializeStack(attachmentEntry.getItemSnbt());
            if (attachmentStack.isEmpty()) {
                continue;
            }
            attachmentCompatService.installAttachment(gunStack, attachmentStack);
        }
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
            playerLevel,
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
        if (player == null || maxRounds <= 0) {
            return AmmoSupplyResult.EMPTY;
        }
        LoadoutAmmoContext primary = resolveLoadoutAmmoContext(player, CustomLoadoutSlot.PRIMARY_WEAPON, PRIMARY_WEAPON_HOTBAR_SLOT, PRIMARY_AMMO_ROW_START, PRIMARY_AMMO_ROW_END);
        LoadoutAmmoContext secondary = resolveLoadoutAmmoContext(player, CustomLoadoutSlot.SECONDARY_WEAPON, SECONDARY_WEAPON_HOTBAR_SLOT, SECONDARY_AMMO_ROW_START, SECONDARY_AMMO_ROW_END);
        int remaining = maxRounds;
        int primaryAdded = 0;
        int secondaryAdded = 0;
        int superbWarfareAdded = 0;

        if (primary != null && primary.targetAmmo() > 0) {
            int currentPrimary = countAmmoInInventory(player, primary.ammoMeta());
            int missingPrimary = Math.max(0, primary.targetAmmo() - currentPrimary);
            int firstPass = Math.min(remaining, missingPrimary);
            if (firstPass > 0) {
                int added = addTaczAmmoToPreferredSlots(player, primary.ammoMeta(), null, firstPass, primary.preferredSlots());
                primaryAdded += added;
                remaining -= added;
            }
        }

        if (remaining > 0 && secondary != null && secondary.targetAmmo() > 0) {
            int currentSecondary = countAmmoInInventory(player, secondary.ammoMeta());
            int missingSecondary = Math.max(0, secondary.targetAmmo() - currentSecondary);
            int secondPass = Math.min(remaining, missingSecondary);
            if (secondPass > 0) {
                int added = addTaczAmmoToPreferredSlots(player, secondary.ammoMeta(), null, secondPass, secondary.preferredSlots());
                secondaryAdded += added;
                remaining -= added;
            }
        }

        if (remaining > 0 && primary != null) {
            int added = addTaczAmmoToPreferredSlots(player, primary.ammoMeta(), null, remaining, primary.preferredSlots());
            primaryAdded += added;
            remaining -= added;
        }

        if (remaining > 0) {
            Map<CustomLoadoutSlot, ResolvedLoadoutItem> resolvedItems = resolveConfiguredLoadoutItems(player);
            for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
                if (remaining <= 0) {
                    break;
                }
                int added = addSuperbWarfareAmmoToConfiguredSlot(player, resolvedItems.get(slot), remaining);
                if (added <= 0) {
                    continue;
                }
                superbWarfareAdded += added;
                remaining -= added;
            }
        }

        int totalAdded = primaryAdded + secondaryAdded + superbWarfareAdded;
        if (totalAdded <= 0) {
            return AmmoSupplyResult.EMPTY;
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.serverLevel().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvent.createVariableRangeEvent(AMMO_BAG_PICKUP_SOUND_ID),
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
        return new AmmoSupplyResult(totalAdded);
    }

    private void grantConfiguredWeaponAmmoByExtraData(ServerPlayer player, ItemStack gunStack, int extraData, int[] preferredSlots) {
        if (grantTaczAmmoByExtraData(player, gunStack, extraData, preferredSlots)) {
            return;
        }
        grantSuperbWarfareAmmoByExtraData(gunStack, extraData);
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
        if (player == null || ammoMeta == null || amount <= 0 || preferredSlots == null || preferredSlots.length == 0) {
            return 0;
        }
        ItemStack sampleAmmo = sampleAmmoStack == null ? createTaczAmmoStack(ammoMeta.ammoId(), 1) : sampleAmmoStack;
        if (sampleAmmo.isEmpty()) {
            return 0;
        }
        int remaining = amount;
        int added = 0;
        for (int slot : preferredSlots) {
            if (remaining <= 0) {
                break;
            }
            if (slot < 0 || slot >= player.getInventory().items.size()) {
                continue;
            }
            ItemStack existing = player.getInventory().getItem(slot);
            if (!existing.isEmpty()) {
                if (!ItemStack.isSameItemSameTags(existing, sampleAmmo)) {
                    continue;
                }
                int max = Math.max(1, existing.getMaxStackSize());
                int canAdd = Math.max(0, max - existing.getCount());
                if (canAdd <= 0) {
                    continue;
                }
                int delta = Math.min(canAdd, remaining);
                existing.grow(delta);
                player.getInventory().setItem(slot, existing);
                remaining -= delta;
                added += delta;
                continue;
            }
            int count = Math.min(ammoMeta.stackSize(), remaining);
            ItemStack ammoStack = createTaczAmmoStack(ammoMeta.ammoId(), count);
            if (ammoStack.isEmpty()) {
                continue;
            }
            player.getInventory().setItem(slot, ammoStack);
            remaining -= count;
            added += count;
        }
        return added;
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
        ActiveLoadoutSelection selection = resolveActiveLoadoutSelection(
            resolveRoomRuntimeContext(player),
            equipmentData,
            playerData,
            player,
            playerLevel,
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
        int playerLevel,
        boolean refreshRandomSelection
    ) {
        if (equipmentData == null || player == null) {
            return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), false, false);
        }
        if (isRandomizedLoadoutEnabled(roomContext)) {
            return resolveRandomizedLoadoutSelection(roomContext.state(), equipmentData, player, refreshRandomSelection);
        }
        PlayerClassType classType = CustomLoadoutService.resolveCurrentClass(playerData);
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
        return new ActiveLoadoutSelection(classType, entriesBySlot, false, playerDataChanged);
    }

    private ActiveLoadoutSelection resolveRandomizedLoadoutSelection(
        RunningRuntimeState state,
        EquipmentDataModel equipmentData,
        ServerPlayer player,
        boolean refreshRandomSelection
    ) {
        if (state == null || equipmentData == null || player == null) {
            return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), true, false);
        }
        UUID playerId = player.getUUID();
        if (!refreshRandomSelection) {
            ActiveLoadoutSelection existing = resolveStoredRandomizedLoadoutSelection(state, equipmentData, playerId);
            if (!existing.entriesBySlot().isEmpty()) {
                return existing;
            }
        }
        List<PlayerClassType> fullCoverageClasses = new ArrayList<>();
        List<PlayerClassType> partialCoverageClasses = new ArrayList<>();
        for (PlayerClassType classType : PlayerClassType.values()) {
            int coveredSlots = 0;
            for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
                if (!CustomLoadoutService.listEntriesByClassAndSlot(equipmentData, classType, slot).isEmpty()) {
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
            return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), true, false);
        }
        PlayerClassType chosenClass = candidates.get(player.getRandom().nextInt(candidates.size()));
        Map<CustomLoadoutSlot, EquipmentDataModel.EquipmentEntry> entriesBySlot = new EnumMap<>(CustomLoadoutSlot.class);
        Map<String, String> storedSelectionIds = new LinkedHashMap<>();
        for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
            List<EquipmentDataModel.EquipmentEntry> candidatesBySlot = CustomLoadoutService.listEntriesByClassAndSlot(equipmentData, chosenClass, slot);
            if (candidatesBySlot.isEmpty()) {
                continue;
            }
            EquipmentDataModel.EquipmentEntry selectedEntry = candidatesBySlot.get(player.getRandom().nextInt(candidatesBySlot.size()));
            if (selectedEntry == null) {
                continue;
            }
            entriesBySlot.put(slot, selectedEntry);
            storedSelectionIds.put(slot.getKey(), CustomLoadoutService.normalizeEquipmentId(selectedEntry.getId()));
        }
        state.getRandomizedLoadoutClassKeysByPlayer().put(playerId, chosenClass.getKey());
        state.getRandomizedLoadoutSelectionIdsByPlayerSlot().put(playerId, storedSelectionIds);
        return new ActiveLoadoutSelection(chosenClass, entriesBySlot, true, false);
    }

    private ActiveLoadoutSelection resolveStoredRandomizedLoadoutSelection(
        RunningRuntimeState state,
        EquipmentDataModel equipmentData,
        UUID playerId
    ) {
        if (state == null || equipmentData == null || playerId == null) {
            return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), true, false);
        }
        PlayerClassType classType = PlayerClassType.fromInput(state.getRandomizedLoadoutClassKeysByPlayer().get(playerId));
        Map<String, String> storedIds = state.getRandomizedLoadoutSelectionIdsByPlayerSlot().get(playerId);
        if (classType == null || storedIds == null || storedIds.isEmpty()) {
            return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), true, false);
        }
        Map<CustomLoadoutSlot, EquipmentDataModel.EquipmentEntry> entriesBySlot = new EnumMap<>(CustomLoadoutSlot.class);
        for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
            String selectedId = CustomLoadoutService.normalizeEquipmentId(storedIds.get(slot.getKey()));
            if (selectedId.isBlank()) {
                continue;
            }
            EquipmentDataModel.EquipmentEntry entry = equipmentData.getEquipmentById().get(selectedId);
            if (entry == null
                || entry.getSlot() != slot.getIndex()
                || !CustomLoadoutService.isEntryAvailableForClass(entry, classType)) {
                return new ActiveLoadoutSelection(PlayerClassType.defaultType(), Map.of(), true, false);
            }
            entriesBySlot.put(slot, entry);
        }
        return new ActiveLoadoutSelection(classType, entriesBySlot, true, false);
    }

    private boolean isRandomizedLoadoutEnabled(RoomRuntimeContext roomContext) {
        return roomContext != null
            && roomContext.room() != null
            && roomContext.state() != null
            && roomContext.room().commonConfig().enableRandomLoadout();
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
        try {
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            Method getIGunOrNull = iGunClass.getMethod("getIGunOrNull", ItemStack.class);
            Object iGun = getIGunOrNull.invoke(null, gunStack);
            if (iGun == null) {
                return null;
            }
            Method getGunId = iGunClass.getMethod("getGunId", ItemStack.class);
            Object gunIdObject = getGunId.invoke(iGun, gunStack);
            if (!(gunIdObject instanceof ResourceLocation gunId)) {
                return null;
            }

            Class<?> timelessApiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            Method getCommonGunIndex = timelessApiClass.getMethod("getCommonGunIndex", ResourceLocation.class);
            Object gunIndexOptionalObject = getCommonGunIndex.invoke(null, gunId);
            if (!(gunIndexOptionalObject instanceof Optional<?> gunIndexOptional) || gunIndexOptional.isEmpty()) {
                return null;
            }
            Object gunIndex = gunIndexOptional.get();
            Method getGunData = gunIndex.getClass().getMethod("getGunData");
            Object gunData = getGunData.invoke(gunIndex);
            if (gunData == null) {
                return null;
            }
            Method getAmmoId = gunData.getClass().getMethod("getAmmoId");
            Object ammoIdObject = getAmmoId.invoke(gunData);
            if (!(ammoIdObject instanceof ResourceLocation ammoId)) {
                return null;
            }

            int stackSize = 64;
            Method getCommonAmmoIndex = timelessApiClass.getMethod("getCommonAmmoIndex", ResourceLocation.class);
            Object ammoIndexOptionalObject = getCommonAmmoIndex.invoke(null, ammoId);
            if (ammoIndexOptionalObject instanceof Optional<?> ammoIndexOptional && ammoIndexOptional.isPresent()) {
                Object ammoIndex = ammoIndexOptional.get();
                Method getStackSize = ammoIndex.getClass().getMethod("getStackSize");
                Object stackSizeObject = getStackSize.invoke(ammoIndex);
                if (stackSizeObject instanceof Integer ammoStackSize && ammoStackSize > 0) {
                    stackSize = ammoStackSize;
                }
            }
            return new TaczAmmoMeta(ammoId, stackSize);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack createTaczAmmoStack(ResourceLocation ammoId, int count) {
        if (ammoId == null || count <= 0) {
            return ItemStack.EMPTY;
        }
        try {
            Class<?> builderClass = Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder");
            Method create = builderClass.getMethod("create");
            Object builder = create.invoke(null);
            Method setId = builderClass.getMethod("setId", ResourceLocation.class);
            Method setCount = builderClass.getMethod("setCount", int.class);
            Method build = builderClass.getMethod("build");
            builder = setId.invoke(builder, ammoId);
            builder = setCount.invoke(builder, count);
            Object ammoStackObject = build.invoke(builder);
            if (ammoStackObject instanceof ItemStack ammoStack) {
                return ammoStack;
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    private ItemStack placeLoadoutItem(ServerPlayer player, CustomLoadoutSlot slot, ItemStack stack) {
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

    private void moveConfiguredTotemToOffhand(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.is(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (stack == null || stack.isEmpty() || !stack.is(Items.TOTEM_OF_UNDYING)) {
                continue;
            }
            player.setItemSlot(EquipmentSlot.OFFHAND, stack.copyWithCount(1));
            if (stack.getCount() <= 1) {
                player.getInventory().items.set(slot, ItemStack.EMPTY);
            } else {
                stack.shrink(1);
            }
            return;
        }
    }

    private void applyOptionalRoomGoatHorn(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        RoomDefinition room = resolvePlayerRoom(player);
        if (room == null || !room.commonConfig().enableGoatHorn()) {
            return;
        }
        if (GOAT_HORN_HOTBAR_SLOT < 0 || GOAT_HORN_HOTBAR_SLOT >= player.getInventory().items.size()) {
            return;
        }
        ItemStack goatHorn = createRandomRoomGoatHorn(player);
        if (goatHorn.isEmpty()) {
            return;
        }
        player.getInventory().setItem(GOAT_HORN_HOTBAR_SLOT, goatHorn);
    }

    private void applyOptionalParachute(RoomRuntimeContext roomContext, ServerPlayer player) {
        if (player == null || roomContext == null || roomContext.room() == null) {
            return;
        }
        if (!roomContext.room().commonConfig().enableParachute()) {
            return;
        }
        if (!CuriosCompatBridge.isLoaded()) {
            return;
        }
        ResourceLocation parachuteId = ResourceLocation.tryParse("superbwarfare:parachute");
        if (parachuteId == null) {
            return;
        }
        net.minecraft.world.item.Item parachuteItem = ForgeRegistries.ITEMS.getValue(parachuteId);
        if (parachuteItem == null || parachuteItem == Items.AIR) {
            return;
        }
        CuriosCompatBridge.setCurio(player, "back", 0, new ItemStack(parachuteItem));
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
        boolean playerDataChanged
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
}
