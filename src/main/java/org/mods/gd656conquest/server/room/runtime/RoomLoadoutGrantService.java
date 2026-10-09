package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.Instruments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.compat.curios.CuriosCompatBridge;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareCompatBridge;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.attachment.WeaponAttachmentDataModel;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
import org.mods.gd656conquest.server.data.equipmentset.EquipmentSetDataModel;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.loadout.TaczAttachmentCompatService;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.application.WeaponAttachmentService;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RoomLoadoutGrantService {
    private static final int GOAT_HORN_HOTBAR_SLOT = 7;
    private static final int SUPPORT_HOTBAR_SLOT = 8;
    private static final String RANDOM_ATTACHMENT_NONE_SELECTION_ID = "__none__";
    private static final int RANDOM_ATTACHMENT_WITH_ITEM_WEIGHT = 3;
    private static final int RANDOM_ATTACHMENT_NONE_WEIGHT = 1;
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

    public RoomLoadoutGrantService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public PreparedWeaponStack prepareConfiguredWeaponStack(
        CustomLoadoutSlot slot,
        EquipmentDataModel.EquipmentEntry entry,
        ItemStack equipmentStack,
        PlayerDataModel playerData,
        WeaponAttachmentDataModel attachmentData,
        int playerLevel,
        RunningRuntimeState randomizedAttachmentState,
        ServerPlayer player,
        boolean randomizeAttachments,
        boolean refreshRandomSelection,
        boolean applyStoredAttachments
    ) {
        if (equipmentStack == null || equipmentStack.isEmpty()) {
            return new PreparedWeaponStack(ItemStack.EMPTY, false);
        }
        ItemStack preparedStack = equipmentStack.copy();
        TaczAttachmentCompatService attachmentCompatService = TaczAttachmentCompatService.getInstance();
        if (!WeaponAttachmentService.supportsSlot(slot)
            || entry == null
            || attachmentData == null
            || attachmentCompatService == null
            || !attachmentCompatService.isAvailable()) {
            return new PreparedWeaponStack(preparedStack, false);
        }
        String loadoutEntryId = CustomLoadoutService.normalizeEquipmentId(entry.getId());
        if (loadoutEntryId.isBlank() || !attachmentCompatService.isTaczGun(preparedStack)) {
            return new PreparedWeaponStack(preparedStack, false);
        }
        if (randomizeAttachments) {
            applyRandomizedWeaponAttachments(
                randomizedAttachmentState,
                player,
                preparedStack,
                loadoutEntryId,
                attachmentData,
                attachmentCompatService,
                refreshRandomSelection
            );
            return new PreparedWeaponStack(preparedStack, false);
        }
        if (!applyStoredAttachments || playerData == null) {
            return new PreparedWeaponStack(preparedStack, false);
        }
        boolean playerDataChanged = WeaponAttachmentService.sanitizeSelectionForWeapon(
            playerData,
            loadoutEntryId,
            preparedStack,
            attachmentData,
            playerLevel,
            attachmentCompatService
        );
        applyStoredWeaponAttachments(preparedStack, playerData, loadoutEntryId, attachmentData, attachmentCompatService);
        return new PreparedWeaponStack(preparedStack, playerDataChanged);
    }

    public void applyArmorLoadout(ServerPlayer player, RoomDefinition room, EquipmentDataModel equipmentData, PlayerClassType classType, RoomTeamId teamId) {
        if (player == null || equipmentData == null || classType == null || teamId == null) {
            return;
        }
        if (applyRoomEquipmentSet(player, room, teamId)) {
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

    public void applyOptionalRoomItems(ServerPlayer player, RoomDefinition room) {
        if (player == null || room == null) {
            return;
        }
        applyOptionalRoomGoatHorn(player, room);
        applyOptionalRoomSupportItem(player);
        applyOptionalParachute(player, room);
    }

    public void grantConfiguredWeaponAmmoByExtraData(ServerPlayer player, ItemStack gunStack, int extraData, int[] preferredSlots) {
        if (grantTaczAmmoByExtraData(player, gunStack, extraData, preferredSlots)) {
            return;
        }
        if (gunStack != null && !gunStack.isEmpty() && extraData > 0) {
            SuperbWarfareCompatBridge.applyReserveAmmo(gunStack, extraData);
        }
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

    public int addTaczAmmoToPreferredSlots(ServerPlayer player, TaczAmmoMeta ammoMeta, ItemStack sampleAmmoStack, int amount, int[] preferredSlots) {
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

    public TaczAmmoMeta resolveTaczAmmoMeta(ItemStack gunStack) {
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

    public ItemStack createTaczAmmoStack(ResourceLocation ammoId, int count) {
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

    public ItemStack deserializeEquipmentItem(String itemSnbt) {
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

    public boolean removeTotems(ServerPlayer player) {
        boolean removed = false;
        if (player == null) {
            return false;
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.is(Items.TOTEM_OF_UNDYING)) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            removed = true;
        }
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (stack != null && !stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                player.getInventory().items.set(slot, ItemStack.EMPTY);
                removed = true;
            }
        }
        return removed;
    }

    public void moveConfiguredTotemToOffhand(ServerPlayer player) {
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

    public void clearCurioBack(ServerPlayer player) {
        if (player != null && CuriosCompatBridge.isLoaded()) {
            CuriosCompatBridge.setCurio(player, "back", 0, ItemStack.EMPTY);
        }
    }

    public void forceSelectHotbarSlot(ServerPlayer player, int slot) {
        if (player == null || slot < 0) {
            return;
        }
        player.getInventory().selected = slot;
        if (player.connection != null) {
            player.connection.send(new ClientboundSetCarriedItemPacket(slot));
        }
    }

    public int[] buildSlotRange(int startInclusive, int endInclusive) {
        if (endInclusive < startInclusive) {
            return new int[0];
        }
        int[] result = new int[endInclusive - startInclusive + 1];
        for (int i = 0; i < result.length; i++) {
            result[i] = startInclusive + i;
        }
        return result;
    }

    private boolean applyRoomEquipmentSet(ServerPlayer player, RoomDefinition room, RoomTeamId teamId) {
        if (player == null || room == null || teamId == null) {
            return false;
        }
        String equipmentSetId = switch (teamId) {
            case CAMP_A -> room.commonConfig().campAEquipmentSetId();
            case CAMP_B -> room.commonConfig().campBEquipmentSetId();
        };
        if (equipmentSetId == null || equipmentSetId.isBlank()) {
            return false;
        }
        EquipmentSetDataModel data = ConquestDataManager.of(server).equipmentSets().get();
        EquipmentSetDataModel.EquipmentSetEntry entry = data.getEntriesById().get(equipmentSetId);
        if (entry == null) {
            return false;
        }
        player.setItemSlot(EquipmentSlot.HEAD, deserializeEquipmentItem(entry.getHeadItemSnbt()));
        player.setItemSlot(EquipmentSlot.CHEST, deserializeEquipmentItem(entry.getChestItemSnbt()));
        player.setItemSlot(EquipmentSlot.LEGS, deserializeEquipmentItem(entry.getLegsItemSnbt()));
        player.setItemSlot(EquipmentSlot.FEET, deserializeEquipmentItem(entry.getFeetItemSnbt()));
        return true;
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

    private void applyRandomizedWeaponAttachments(
        RunningRuntimeState state,
        ServerPlayer player,
        ItemStack gunStack,
        String loadoutEntryId,
        WeaponAttachmentDataModel attachmentData,
        TaczAttachmentCompatService attachmentCompatService,
        boolean refreshRandomSelection
    ) {
        if (state == null
            || player == null
            || gunStack == null
            || gunStack.isEmpty()
            || attachmentData == null
            || attachmentCompatService == null) {
            return;
        }
        String normalizedLoadoutEntryId = CustomLoadoutService.normalizeEquipmentId(loadoutEntryId);
        if (normalizedLoadoutEntryId.isBlank()) {
            return;
        }
        Map<String, List<WeaponAttachmentDataModel.WeaponAttachmentEntry>> candidatesByType =
            buildRandomAttachmentCandidatesByType(gunStack.copy(), attachmentData, attachmentCompatService);
        Map<String, Map<String, String>> byLoadoutEntry = state.getRandomizedAttachmentSelectionIdsByPlayerLoadoutEntry()
            .computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>());
        Map<String, String> storedSelection = refreshRandomSelection ? null : byLoadoutEntry.get(normalizedLoadoutEntryId);
        Map<String, String> resolvedSelection = new LinkedHashMap<>();
        for (String attachmentType : WeaponAttachmentService.ORDERED_ATTACHMENT_TYPES) {
            List<WeaponAttachmentDataModel.WeaponAttachmentEntry> candidates = candidatesByType.get(attachmentType);
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }
            attachmentCompatService.unloadAttachment(gunStack, attachmentType);
            RandomAttachmentChoice chosen = resolveRandomAttachmentChoice(
                player,
                gunStack,
                candidates,
                storedSelection == null ? "" : storedSelection.get(attachmentType),
                attachmentCompatService
            );
            if (chosen == null) {
                continue;
            }
            if (chosen.explicitNone()) {
                resolvedSelection.put(attachmentType, RANDOM_ATTACHMENT_NONE_SELECTION_ID);
                continue;
            }
            WeaponAttachmentDataModel.WeaponAttachmentEntry chosenEntry = chosen.entry();
            if (chosenEntry == null) {
                continue;
            }
            ItemStack attachmentStack = attachmentCompatService.deserializeStack(chosenEntry.getItemSnbt());
            if (attachmentStack.isEmpty() || !attachmentCompatService.installAttachment(gunStack, attachmentStack)) {
                continue;
            }
            resolvedSelection.put(attachmentType, chosen.selectionId());
        }
        if (resolvedSelection.isEmpty()) {
            byLoadoutEntry.remove(normalizedLoadoutEntryId);
            if (byLoadoutEntry.isEmpty()) {
                state.getRandomizedAttachmentSelectionIdsByPlayerLoadoutEntry().remove(player.getUUID());
            }
            return;
        }
        byLoadoutEntry.put(normalizedLoadoutEntryId, resolvedSelection);
    }

    private Map<String, List<WeaponAttachmentDataModel.WeaponAttachmentEntry>> buildRandomAttachmentCandidatesByType(
        ItemStack weaponStack,
        WeaponAttachmentDataModel attachmentData,
        TaczAttachmentCompatService attachmentCompatService
    ) {
        if (weaponStack == null || weaponStack.isEmpty() || attachmentData == null || attachmentCompatService == null) {
            return Map.of();
        }
        Map<String, List<WeaponAttachmentDataModel.WeaponAttachmentEntry>> candidatesByType = new LinkedHashMap<>();
        for (String attachmentType : WeaponAttachmentService.ORDERED_ATTACHMENT_TYPES) {
            if (attachmentCompatService.allowAttachmentType(weaponStack, attachmentType)) {
                candidatesByType.put(attachmentType, new ArrayList<>());
            }
        }
        if (candidatesByType.isEmpty()) {
            return Map.of();
        }
        for (WeaponAttachmentDataModel.WeaponAttachmentEntry entry : attachmentData.getAttachmentById().values()) {
            if (entry == null) {
                continue;
            }
            String attachmentType = WeaponAttachmentService.normalizeAttachmentType(entry.getAttachmentType());
            List<WeaponAttachmentDataModel.WeaponAttachmentEntry> candidates = candidatesByType.get(attachmentType);
            if (candidates == null) {
                continue;
            }
            ItemStack attachmentStack = attachmentCompatService.deserializeStack(entry.getItemSnbt());
            if (attachmentStack.isEmpty() || !attachmentCompatService.allowAttachment(weaponStack, attachmentStack)) {
                continue;
            }
            candidates.add(entry);
        }
        candidatesByType.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
        return candidatesByType.isEmpty() ? Map.of() : candidatesByType;
    }

    private RandomAttachmentChoice resolveRandomAttachmentChoice(
        ServerPlayer player,
        ItemStack currentGunStack,
        List<WeaponAttachmentDataModel.WeaponAttachmentEntry> candidates,
        String storedAttachmentId,
        TaczAttachmentCompatService attachmentCompatService
    ) {
        if (player == null || currentGunStack == null || currentGunStack.isEmpty() || candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (RANDOM_ATTACHMENT_NONE_SELECTION_ID.equalsIgnoreCase(storedAttachmentId == null ? "" : storedAttachmentId.trim())) {
            return RandomAttachmentChoice.none();
        }
        String normalizedStoredAttachmentId = WeaponAttachmentService.normalizeAttachmentId(storedAttachmentId);
        if (!normalizedStoredAttachmentId.isBlank()) {
            for (WeaponAttachmentDataModel.WeaponAttachmentEntry candidate : candidates) {
                if (candidate == null || !normalizedStoredAttachmentId.equalsIgnoreCase(WeaponAttachmentService.normalizeAttachmentId(candidate.getId()))) {
                    continue;
                }
                ItemStack attachmentStack = attachmentCompatService.deserializeStack(candidate.getItemSnbt());
                if (!attachmentStack.isEmpty() && attachmentCompatService.allowAttachment(currentGunStack, attachmentStack)) {
                    return RandomAttachmentChoice.withEntry(candidate);
                }
                break;
            }
        }
        List<WeaponAttachmentDataModel.WeaponAttachmentEntry> runtimeCandidates = new ArrayList<>();
        for (WeaponAttachmentDataModel.WeaponAttachmentEntry candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            ItemStack attachmentStack = attachmentCompatService.deserializeStack(candidate.getItemSnbt());
            if (attachmentStack.isEmpty() || !attachmentCompatService.allowAttachment(currentGunStack, attachmentStack)) {
                continue;
            }
            runtimeCandidates.add(candidate);
        }
        if (runtimeCandidates.isEmpty()) {
            return RandomAttachmentChoice.none();
        }
        int totalWeight = RANDOM_ATTACHMENT_WITH_ITEM_WEIGHT + RANDOM_ATTACHMENT_NONE_WEIGHT;
        if (player.getRandom().nextInt(totalWeight) < RANDOM_ATTACHMENT_NONE_WEIGHT) {
            return RandomAttachmentChoice.none();
        }
        return RandomAttachmentChoice.withEntry(runtimeCandidates.get(player.getRandom().nextInt(runtimeCandidates.size())));
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

    private void applyOptionalRoomGoatHorn(ServerPlayer player, RoomDefinition room) {
        if (player == null || player.server == null || room == null || !room.commonConfig().enableGoatHorn()) {
            return;
        }
        if (GOAT_HORN_HOTBAR_SLOT < 0 || GOAT_HORN_HOTBAR_SLOT >= player.getInventory().items.size()) {
            return;
        }
        ItemStack goatHorn = createRandomRoomGoatHorn(player);
        if (!goatHorn.isEmpty()) {
            player.getInventory().setItem(GOAT_HORN_HOTBAR_SLOT, goatHorn);
        }
    }

    private void applyOptionalParachute(ServerPlayer player, RoomDefinition room) {
        if (player == null || room == null || !room.commonConfig().enableParachute() || !CuriosCompatBridge.isLoaded()) {
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

    private void applyOptionalRoomSupportItem(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (SUPPORT_HOTBAR_SLOT < 0 || SUPPORT_HOTBAR_SLOT >= player.getInventory().items.size()) {
            return;
        }
        player.getInventory().setItem(SUPPORT_HOTBAR_SLOT, new ItemStack(ConquestItems.SUPPORT_ITEM.get()));
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

    public record PreparedWeaponStack(ItemStack stack, boolean playerDataChanged) {
    }

    public record TaczAmmoMeta(ResourceLocation ammoId, int stackSize) {
    }

    private record RandomAttachmentChoice(
        String selectionId,
        WeaponAttachmentDataModel.WeaponAttachmentEntry entry,
        boolean explicitNone
    ) {
        private static RandomAttachmentChoice withEntry(WeaponAttachmentDataModel.WeaponAttachmentEntry entry) {
            return new RandomAttachmentChoice(
                entry == null ? "" : WeaponAttachmentService.normalizeAttachmentId(entry.getId()),
                entry,
                false
            );
        }

        private static RandomAttachmentChoice none() {
            return new RandomAttachmentChoice(RANDOM_ATTACHMENT_NONE_SELECTION_ID, null, true);
        }
    }
}
