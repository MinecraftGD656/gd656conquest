package org.mods.gd656conquest.server.loadout;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiOpenPacket;
import org.mods.gd656conquest.network.packet.RoomEditorSnapshotPacket;
import org.mods.gd656conquest.network.packet.VehicleAdminEditorSnapshotPacket;
import org.mods.gd656conquest.common.block.ConquestBlocks;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.attachment.WeaponAttachmentDataModel;
import org.mods.gd656conquest.server.data.attachment.WeaponAttachmentDataStore;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataStore;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataStore;
import org.mods.gd656conquest.server.data.roomstatisticdata.RoomStatisticDataModel;
import org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult;
import org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator;
import org.mods.gd656conquest.server.data.vehicledata.VehicleConfigEntry;
import org.mods.gd656conquest.server.data.vehicledata.VehicleConfigRepository;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.application.WeaponAttachmentService;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
import org.mods.gd656conquest.server.room.core.RoomCoreService;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.title.PlayerTitleService;
import org.mods.gd656conquest.server.room.config.RoomConfigDescriptor;
import org.mods.gd656conquest.server.room.config.RoomConfigDescriptorRegistry;
import org.mods.gd656conquest.server.room.config.RoomConfigScope;
import org.mods.gd656conquest.server.room.config.RoomConfigValueResolver;
import org.mods.gd656conquest.server.room.config.RoomValidationMessageFormatter;
import org.mods.gd656conquest.server.room.config.RoomDefinitionGroupConfigEditor;
import org.mods.gd656conquest.server.room.config.RoomDefinitionScalarConfigEditor;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.room.model.RoomCaptureConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomSessionTransitionService;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;
import org.mods.gd656conquest.server.room.runtime.RoomFixedLoadoutRules;
import org.mods.gd656conquest.server.room.runtime.RoomGunmasterWeaponPoolRules;

import java.util.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "gd656conquest")
public final class LoadoutUiService {
    private static final Map<UUID, CachedSnapshot> SNAPSHOT_CACHE = new HashMap<>();

    private LoadoutUiService() {
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SNAPSHOT_CACHE.remove(player.getUUID());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        SNAPSHOT_CACHE.values().removeIf(snapshot -> snapshot.server() == event.getServer());
    }

    public static void sendSnapshot(ServerPlayer player, boolean openPanel) {
        sendSnapshot(player, openPanel, false, 0);
    }

    public static void sendSnapshot(ServerPlayer player, boolean openPanel, int targetTabIndex) {
        sendSnapshot(player, openPanel, false, targetTabIndex);
    }

    public static void sendSnapshot(ServerPlayer player, boolean openPanel, boolean forceSend) {
        sendSnapshot(player, openPanel, forceSend, 0);
    }

    public static void sendSnapshot(ServerPlayer player, boolean openPanel, boolean forceSend, int targetTabIndex) {
        if (player == null || player.server == null) {
            return;
        }
        LoadoutVehiclePageSnapshotBuilder.Snapshot vehicleSnapshot = LoadoutVehiclePageSnapshotBuilder.build(player);
        CachedSnapshot cached = forceSend || openPanel ? null : SNAPSHOT_CACHE.get(player.getUUID());
        if (cached != null && cached.server() == player.server) {
            NetworkManager.sendToPlayer(player, cached.toPacket(openPanel, targetTabIndex, vehicleSnapshot));
            return;
        }

        LoadoutUiOpenPacket snapshot = buildSnapshot(player, openPanel, targetTabIndex, vehicleSnapshot);
        SNAPSHOT_CACHE.put(player.getUUID(), new CachedSnapshot(player.server, snapshot));
        NetworkManager.sendToPlayer(player, snapshot);
    }

    public static void handleAction(ServerPlayer player, String action, String classKey, int slotIndex, String itemId) {
        handleAction(player, action, classKey, slotIndex, itemId, "", "");
    }

    public static void handleAction(
        ServerPlayer player,
        String action,
        String classKey,
        int slotIndex,
        String itemId,
        String contextId,
        String attachmentType
    ) {
        if (player == null || player.server == null) {
            return;
        }
        String normalizedAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        // GD656Mods: 对局进行中(非部署流程)修改配装 -> 暂存不实现, 于玩家死亡/对局结束时应用
        if (isLoadoutEditAction(normalizedAction) && isLoadoutEditBlockedInRunning(player)) {
            if (!validatePendingEditForRoom(player, normalizedAction)) {
                return;
            }
            queuePendingLoadoutEdit(player, normalizedAction, classKey, slotIndex, itemId, contextId, attachmentType);
            sendInfo(player, "您修改的配装将在下一次死亡时实现");
            return;
        }
        switch (normalizedAction) {
            case "select_class" -> {
                if (applyClassSelection(player, classKey)) {
                    sendSnapshot(player, false, true);
                }
            }
            case "select_slot" -> {
                if (applySlotSelection(player, classKey, slotIndex, itemId)) {
                    sendSnapshot(player, false, true);
                }
            }
            case "select_attachment" -> {
                if (applyAttachmentSelection(player, classKey, slotIndex, contextId, attachmentType, itemId)) {
                    sendSnapshot(player, false, true);
                }
            }
            case "leave_session" -> leaveSession(player);
            case "switch_camp" -> switchCamp(player);
            default -> {
            }
        }
    }

    public static void handleAdminEquipmentEdit(ServerPlayer player, String equipmentId, String fieldKey, String rawValue) {
        if (player == null || player.server == null) {
            return;
        }
        if (!player.hasPermissions(2)) {
            sendError(player, "你没有权限编辑物品数据");
            return;
        }
        String id = CustomLoadoutService.normalizeEquipmentId(equipmentId);
        String field = fieldKey == null ? "" : fieldKey.trim().toLowerCase(Locale.ROOT);
        String value = rawValue == null ? "" : rawValue.trim();
        if (id.isBlank() || field.isBlank()) {
            sendError(player, "编辑参数无效");
            return;
        }
        EquipmentDataStore equipmentDataHandler = ConquestDataManager.of(player.server).equipmentData();
        EquipmentDataModel data = equipmentDataHandler.getData();
        if (data == null) {
            sendError(player, "装备数据不存在");
            return;
        }
        if ("create".equals(field)) {
            if (!createEquipmentEntry(player, data, id)) {
                return;
            }
            equipmentDataHandler.saveData(data);
            sendSnapshot(player, false, true);
            sendInfo(player, "已创建装备: " + id);
            return;
        }
        EquipmentDataModel.EquipmentEntry entry = data.getEquipmentById().get(id);
        if (entry == null) {
            sendError(player, "装备不存在: " + id);
            return;
        }

        boolean changed = false;
        String changedFieldName = field;
        String changedValueSummary = value;
        switch (field) {
            case "id" -> {
                String newId = CustomLoadoutService.normalizeEquipmentId(value);
                if (!CustomLoadoutService.isValidEquipmentId(newId)) {
                    sendError(player, "ID 格式非法");
                    return;
                }
                if (newId.equals(id)) {
                    return;
                }
                if (data.getEquipmentById().containsKey(newId)) {
                    sendError(player, "新 ID 已存在: " + newId);
                    return;
                }
                data.getEquipmentById().remove(id);
                entry.setId(newId);
                data.getEquipmentById().put(newId, entry);
                replaceSelectionId(data, id, newId);
                changed = true;
                changedValueSummary = newId;
            }
            case "displayname", "display_name" -> {
                entry.setDisplayName(value);
                changed = true;
                changedFieldName = "displayName";
            }
            case "slot" -> {
                int slotValue;
                try {
                    slotValue = Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    sendError(player, "slot 必须是数字");
                    return;
                }
                CustomLoadoutSlot slot = CustomLoadoutSlot.fromIndex(slotValue);
                if (slot == null) {
                    sendError(player, "slot 仅支持 1~6");
                    return;
                }
                entry.setSlot(slot.getIndex());
                entry.setSlotName(slot.getKey());
                changed = true;
            }
            case "slotname", "slot_name" -> {
                entry.setSlotName(value);
                changed = true;
                changedFieldName = "slotName";
            }
            case "extradata", "extra_data" -> {
                try {
                    entry.setExtraData(Integer.parseInt(value));
                    changed = true;
                    changedFieldName = "extraData";
                } catch (NumberFormatException exception) {
                    sendError(player, "extraData 必须是数字");
                    return;
                }
            }
            case "unlocklevel", "unlock_level" -> {
                try {
                    int unlockLevel = Integer.parseInt(value);
                    if (unlockLevel < 0) {
                        sendError(player, "unlockLevel 不能小于 0");
                        return;
                    }
                    entry.setUnlockLevel(unlockLevel);
                    changed = true;
                    changedFieldName = "unlockLevel";
                } catch (NumberFormatException exception) {
                    sendError(player, "unlockLevel 必须是数字");
                    return;
                }
            }
            case "availableclasses", "available_classes" -> {
                try {
                    int availableClasses = Integer.parseInt(value);
                    if (PlayerClassType.parseAvailableDigits(availableClasses).isEmpty()) {
                        sendError(player, "availableClasses 至少包含 1~4 中的一个数字");
                        return;
                    }
                    entry.setAvailableClasses(availableClasses);
                    changed = true;
                    changedFieldName = "availableClasses";
                } catch (NumberFormatException exception) {
                    sendError(player, "availableClasses 必须是数字");
                    return;
                }
            }
            case "itemsnbt", "item_snbt" -> {
                if (value.isBlank()) {
                    entry.setItemSnbt("");
                    changed = true;
                    changedFieldName = "itemSnbt";
                    changedValueSummary = "<empty>";
                } else {
                    try {
                        CompoundTag tag = TagParser.parseTag(value);
                        ItemStack stack = ItemStack.of(tag);
                        if (stack.isEmpty()) {
                            sendError(player, "itemSnbt 无法解析成有效物品");
                            return;
                        }
                        entry.setItemSnbt(value);
                        changed = true;
                        changedFieldName = "itemSnbt";
                        changedValueSummary = stack.getDisplayName().getString();
                    } catch (Exception exception) {
                        sendError(player, "itemSnbt 语法错误");
                        return;
                    }
                }
            }
            case "replenishseconds", "replenish_seconds" -> {
                try {
                    int replenishSeconds = Integer.parseInt(value);
                    if (replenishSeconds < 0) {
                        sendError(player, "replenishSeconds 不能小于 0");
                        return;
                    }
                    entry.setReplenishSeconds(replenishSeconds);
                    changed = true;
                    changedFieldName = "replenishSeconds";
                    changedValueSummary = String.valueOf(replenishSeconds);
                } catch (NumberFormatException exception) {
                    sendError(player, "replenishSeconds 必须是数字");
                    return;
                }
            }
            case "ammoboxrefillenabled", "ammo_box_refill_enabled" -> {
                Boolean enabled = parseBoolean(value);
                if (enabled == null) {
                    sendError(player, "ammoBoxRefillEnabled 仅支持 true/false");
                    return;
                }
                entry.setAmmoBoxRefillEnabled(enabled);
                changed = true;
                changedFieldName = "ammoBoxRefillEnabled";
                changedValueSummary = String.valueOf(enabled);
            }
            case "delete" -> {
                data.getEquipmentById().remove(id);
                replaceSelectionId(data, id, "");
                changed = true;
                changedFieldName = "delete";
                changedValueSummary = "deleted";
            }
            case "set_default", "setdefault" -> {
                setDefaultEquipmentForSupportedClasses(data, entry);
                changed = true;
                changedFieldName = "default";
                changedValueSummary = "updated";
            }
            default -> {
                sendError(player, "不支持的字段: " + field);
                return;
            }
        }

        if (!changed) {
            return;
        }

        equipmentDataHandler.saveData(data);
        if ("id".equals(field)) {
            migrateAttachmentLoadoutEntryIdForAllPlayers(player.server, id, changedValueSummary);
        } else if ("delete".equals(field)) {
            migrateAttachmentLoadoutEntryIdForAllPlayers(player.server, id, "");
        }
        for (ServerPlayer onlinePlayer : player.server.getPlayerList().getPlayers()) {
            CustomLoadoutService.sanitizePlayerSelection(onlinePlayer, data, "delete".equals(field) ? id : null, false);
        }
        sendSnapshot(player, false, true);
        sendSystem(player, Component.literal(String.format(Locale.ROOT, "已更新 %s.%s -> %s", id, changedFieldName, changedValueSummary)).withStyle(ChatFormatting.AQUA));
    }

    public static void handleAdminAttachmentEdit(ServerPlayer player, String attachmentId, String fieldKey, String rawValue) {
        if (player == null || player.server == null) {
            return;
        }
        if (!player.hasPermissions(2)) {
            sendError(player, "你没有权限编辑配件数据");
            return;
        }
        String id = WeaponAttachmentService.normalizeAttachmentId(attachmentId);
        String field = fieldKey == null ? "" : fieldKey.trim().toLowerCase(Locale.ROOT);
        String value = rawValue == null ? "" : rawValue.trim();
        if (id.isBlank() || field.isBlank()) {
            sendError(player, "配件编辑参数无效");
            return;
        }
        WeaponAttachmentDataStore attachmentStore = ConquestDataManager.of(player.server).weaponAttachments();
        WeaponAttachmentDataModel data = attachmentStore.getData();
        if (data == null) {
            sendError(player, "配件数据不存在");
            return;
        }
        if ("create".equals(field)) {
            int unlockLevel = 0;
            if (!value.isBlank()) {
                try {
                    unlockLevel = Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    sendError(player, "配件解锁等级必须是数字");
                    return;
                }
                if (unlockLevel < 0) {
                    sendError(player, "配件解锁等级不能小于 0");
                    return;
                }
            }
            if (!createAttachmentEntry(player, data, id, unlockLevel)) {
                return;
            }
            attachmentStore.saveData(data);
            sendSnapshot(player, false, true);
            sendInfo(player, "已创建配件: " + id);
            return;
        }
        WeaponAttachmentDataModel.WeaponAttachmentEntry entry = data.getAttachmentById().get(id);
        if (entry == null) {
            sendError(player, "配件不存在: " + id);
            return;
        }

        boolean changed = false;
        String changedFieldName = field;
        String changedValueSummary = value;
        String previousId = id;
        String replacementId = null;
        switch (field) {
            case "id" -> {
                String newId = WeaponAttachmentService.normalizeAttachmentId(value);
                if (!WeaponAttachmentService.isValidAttachmentId(newId)) {
                    sendError(player, "配件 ID 格式非法");
                    return;
                }
                if (newId.equals(id)) {
                    return;
                }
                if (data.getAttachmentById().containsKey(newId)) {
                    sendError(player, "新配件 ID 已存在: " + newId);
                    return;
                }
                data.getAttachmentById().remove(id);
                entry.setId(newId);
                data.getAttachmentById().put(newId, entry);
                changed = true;
                changedValueSummary = newId;
                replacementId = newId;
            }
            case "displayname", "display_name" -> {
                entry.setDisplayName(value);
                changed = true;
                changedFieldName = "displayName";
            }
            case "unlocklevel", "unlock_level" -> {
                try {
                    int unlockLevel = Integer.parseInt(value);
                    if (unlockLevel < 0) {
                        sendError(player, "unlockLevel 不能小于 0");
                        return;
                    }
                    entry.setUnlockLevel(unlockLevel);
                    changed = true;
                    changedFieldName = "unlockLevel";
                    changedValueSummary = String.valueOf(unlockLevel);
                } catch (NumberFormatException exception) {
                    sendError(player, "unlockLevel 必须是数字");
                    return;
                }
            }
            case "itemsnbt", "item_snbt" -> {
                ItemStack stack = parseAttachmentStack(player, value);
                if (stack.isEmpty()) {
                    return;
                }
                TaczAttachmentCompatService compatService = TaczAttachmentCompatService.getInstance();
                String attachmentType = compatService.resolveAttachmentType(stack);
                if (attachmentType.isBlank()) {
                    sendError(player, "当前物品无法识别 TACZ 配件类型");
                    return;
                }
                entry.setItemSnbt(serializeStack(stack));
                entry.setAttachmentType(attachmentType);
                entry.setDisplayName(resolveRealItemName(player, stack, BuiltInRegistries.ITEM.getKey(stack.getItem())));
                changed = true;
                changedFieldName = "itemSnbt";
                changedValueSummary = entry.getDisplayName();
            }
            case "futurerequirement", "future_requirement" -> {
                entry.setFutureRequirement(value);
                changed = true;
                changedFieldName = "futureRequirement";
                changedValueSummary = value.isBlank() ? "<empty>" : value;
            }
            case "delete" -> {
                data.getAttachmentById().remove(id);
                changed = true;
                changedFieldName = "delete";
                changedValueSummary = "deleted";
                replacementId = "";
            }
            default -> {
                sendError(player, "不支持的配件字段: " + field);
                return;
            }
        }

        if (!changed) {
            return;
        }

        attachmentStore.saveData(data);
        migrateAttachmentSelectionValueForAllPlayers(player.server, previousId, replacementId);
        sendSnapshot(player, false, true);
        sendSystem(player, Component.literal(String.format(Locale.ROOT, "已更新配件 %s.%s -> %s", id, changedFieldName, changedValueSummary)).withStyle(ChatFormatting.AQUA));
    }

    public static int deleteAllAttachmentsByType(ServerPlayer player, String attachmentType) {
        if (player == null || player.server == null) {
            return 0;
        }
        if (!player.hasPermissions(2)) {
            sendError(player, "你没有权限编辑配件数据");
            return 0;
        }
        String normalizedType = WeaponAttachmentService.normalizeAttachmentType(attachmentType);
        if (normalizedType.isBlank()) {
            sendError(player, "配件类型无效");
            return 0;
        }
        WeaponAttachmentDataStore attachmentStore = ConquestDataManager.of(player.server).weaponAttachments();
        WeaponAttachmentDataModel data = attachmentStore.getData();
        if (data == null) {
            sendError(player, "配件数据不存在");
            return 0;
        }
        List<String> removedIds = new ArrayList<>();
        Iterator<Map.Entry<String, WeaponAttachmentDataModel.WeaponAttachmentEntry>> iterator = data.getAttachmentById().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, WeaponAttachmentDataModel.WeaponAttachmentEntry> entry = iterator.next();
            String entryType = WeaponAttachmentService.normalizeAttachmentType(entry.getValue().getAttachmentType());
            if (!normalizedType.equals(entryType)) {
                continue;
            }
            removedIds.add(entry.getKey());
            iterator.remove();
        }
        if (removedIds.isEmpty()) {
            sendInfo(player, "当前类型没有可删除的配件注册项: " + WeaponAttachmentService.getAttachmentTypeLabel(normalizedType));
            return 0;
        }
        attachmentStore.saveData(data);
        for (String removedId : removedIds) {
            migrateAttachmentSelectionValueForAllPlayers(player.server, removedId, "");
        }
        sendSnapshot(player, false, true);
        sendInfo(
            player,
            String.format(
                Locale.ROOT,
                "已删除 %s 类型的全部配件注册项，共 %d 项",
                WeaponAttachmentService.getAttachmentTypeLabel(normalizedType),
                removedIds.size()
            )
        );
        return removedIds.size();
    }

    public static void sendAdminRoomSnapshot(ServerPlayer player, boolean openPanel) {
        if (player == null || player.server == null) {
            return;
        }
        if (!player.hasPermissions(2)) {
            NetworkManager.sendToPlayer(player, new RoomEditorSnapshotPacket(false, openPanel, List.of()));
            return;
        }
        ConquestDataManager dataManager = ConquestDataManager.of(player.server);
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(player.server);
        List<RoomEditorSnapshotPacket.RoomPayload> rooms = new ArrayList<>();
        for (RoomDefinition room : dataManager.roomDefinitions().getAllDefinitions()) {
            if (room == null || room.roomId() == null || room.roomId().isBlank()) {
                continue;
            }
            RoomConfigValidationResult validation = RoomConfigValidator.validate(room);
            List<String> missingItems = validation == null ? List.of() : List.copyOf(validation.getMissingItems());
            List<String> missingConfigIds = validation == null ? List.of() : List.copyOf(validation.getMissingConfigIds());
            int roomPlayerCount = (int) player.server.getPlayerList().getPlayers().stream()
                .filter(serverPlayer -> serverPlayer != null)
                .map(serverPlayer -> coreManager.findPlayerRoomId(serverPlayer.getUUID()).orElse(""))
                .filter(room.roomId()::equalsIgnoreCase)
                .count();
            int roomMaxPlayers = room.commonConfig().matchingConfig().totalMaxPlayers();
            List<RoomEditorSnapshotPacket.ConfigEntryPayload> configEntries =
                buildRoomConfigEntries(dataManager, room, missingItems, missingConfigIds);
            RoomLifecycleState roomState = room.lifecycleState() == null ? RoomLifecycleState.DISABLED : room.lifecycleState();
            rooms.add(new RoomEditorSnapshotPacket.RoomPayload(
                room.roomId(),
                room.displayName() == null ? "" : room.displayName(),
                roomState.name(),
                resolveRoomStateDisplayName(roomState),
                roomPlayerCount,
                roomMaxPlayers,
                missingItems,
                missingConfigIds,
                configEntries
            ));
        }
        NetworkManager.sendToPlayer(player, new RoomEditorSnapshotPacket(true, openPanel, rooms));
    }

    public static void handleAdminRoomConfigEdit(ServerPlayer player, String roomId, String keyPath, String operation, String value) {
        if (player == null || player.server == null) {
            return;
        }
        if (!player.hasPermissions(2)) {
            sendError(player, "你没有权限编辑房间数据");
            return;
        }
        String normalizedRoomId = roomId == null ? "" : roomId.trim();
        String normalizedKey = keyPath == null ? "" : keyPath.trim();
        String normalizedOperation = operation == null || operation.isBlank() ? "set" : operation.trim().toLowerCase(Locale.ROOT);
        String rawValue = value == null ? "" : value.trim();
        if (normalizedRoomId.isBlank() || normalizedKey.isBlank()) {
            sendError(player, "房间编辑参数无效");
            return;
        }
        ConquestDataManager dataManager = ConquestDataManager.of(player.server);
        RoomDefinition room = dataManager.roomDefinitions().findDefinition(normalizedRoomId).orElse(null);
        if (room == null) {
            sendError(player, "未知房间: " + normalizedRoomId);
            return;
        }
        try {
            RoomDefinition updated = room;
            if ("room.displayName".equalsIgnoreCase(normalizedKey)) {
                updated = new RoomDefinition(
                    room.roomId(),
                    rawValue,
                    room.gameplayId(),
                    room.lifecycleState(),
                    room.commonConfig(),
                    room.gameplayConfig()
                );
            } else if ("room.state".equalsIgnoreCase(normalizedKey)) {
                RoomLifecycleState targetState = parseRoomState(rawValue);
                if (targetState == null) {
                    sendError(player, "无效房间状态: " + rawValue);
                    return;
                }
                updated = room.withLifecycleState(targetState);
            } else {
                RoomConfigDescriptor descriptor = resolveEditableRoomConfigDescriptor(normalizedKey);
                if (descriptor == null) {
                    sendError(player, "未找到配置项: " + normalizedKey);
                    return;
                }
                if (descriptor.supportsGroupOperations()) {
                    updated = switch (normalizedOperation) {
                        case "group_add" -> RoomDefinitionGroupConfigEditor.add(room, descriptor.configId(), rawValue, player);
                        case "group_delete" -> RoomDefinitionGroupConfigEditor.delete(room, descriptor.configId(), rawValue, player);
                        default -> applyGroupDescriptorUpdate(room, descriptor, rawValue, player);
                    };
                } else {
                    updated = RoomDefinitionScalarConfigEditor.apply(room, descriptor, rawValue, player);
                }
            }
            updated = dataManager.roomDefinitions().saveDefinition(updated);
            RoomCoreRuntimeManager.of(player.server).hotUpdateRoomDefinition(updated, player.server.getTickCount());
            room = updated;
        } catch (Exception exception) {
            sendError(player, "编辑失败: " + exception.getMessage());
            return;
        }
        sendAdminRoomSnapshot(player, false);
        sendInfo(player, "已更新 " + room.roomId() + "." + normalizedKey);
    }

    public static void sendAdminVehicleSnapshot(ServerPlayer player, boolean openPanel) {
        if (player == null || player.server == null) {
            return;
        }
        if (!player.hasPermissions(2)) {
            NetworkManager.sendToPlayer(player, new VehicleAdminEditorSnapshotPacket(false, openPanel, List.of()));
            return;
        }
        ConquestDataManager dataManager = ConquestDataManager.of(player.server);
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(player.server);
        RoomVehicleBackend backend = dataManager.serverConfig().getVehicleBackend();
        VehicleConfigRepository repository = dataManager.vehicleConfigs();
        List<VehicleAdminEditorSnapshotPacket.RoomPayload> rooms = new ArrayList<>();
        for (RoomDefinition room : dataManager.roomDefinitions().getAllDefinitions()) {
            if (room == null || room.roomId() == null || room.roomId().isBlank()) {
                continue;
            }
            int roomPlayerCount = (int) player.server.getPlayerList().getPlayers().stream()
                .filter(serverPlayer -> serverPlayer != null)
                .map(serverPlayer -> coreManager.findPlayerRoomId(serverPlayer.getUUID()).orElse(""))
                .filter(room.roomId()::equalsIgnoreCase)
                .count();
            List<VehicleConfigEntry> entries = repository.getEntries(room.roomId(), room.gameplayId().value(), backend).stream()
                .filter(entry -> entry != null)
                .sorted(Comparator.comparing(VehicleConfigEntry::getEntryId, String.CASE_INSENSITIVE_ORDER))
                .toList();
            List<VehicleAdminEditorSnapshotPacket.VehicleEntryPayload> payloads = new ArrayList<>();
            for (VehicleConfigEntry entry : entries) {
                payloads.add(new VehicleAdminEditorSnapshotPacket.VehicleEntryPayload(
                    safeVehicleField(entry.getEntryId()),
                    normalizeVehicleTeam(entry.getTeam()),
                    safeVehicleField(entry.getDisplayName()),
                    safeVehicleField(entry.getVehicleId()),
                    entry.isEnabled(),
                    buildVehicleFieldPayloads(entry)
                ));
            }
            RoomLifecycleState roomState = room.lifecycleState() == null ? RoomLifecycleState.DISABLED : room.lifecycleState();
            rooms.add(new VehicleAdminEditorSnapshotPacket.RoomPayload(
                room.roomId(),
                room.displayName() == null ? "" : room.displayName(),
                roomState.name(),
                resolveRoomStateDisplayName(roomState),
                roomPlayerCount,
                room.commonConfig().matchingConfig().totalMaxPlayers(),
                room.gameplayId().value(),
                backend.value(),
                room.commonConfig().campADisplayName(),
                room.commonConfig().campBDisplayName(),
                payloads
            ));
        }
        NetworkManager.sendToPlayer(player, new VehicleAdminEditorSnapshotPacket(true, openPanel, rooms));
    }

    public static void handleAdminVehicleConfigEdit(ServerPlayer player, String roomId, String entryId, String fieldKey, String value) {
        if (player == null || player.server == null) {
            return;
        }
        if (!player.hasPermissions(2)) {
            sendError(player, "你没有权限编辑房间载具数据");
            return;
        }
        String normalizedRoomId = roomId == null ? "" : roomId.trim();
        String normalizedEntryId = entryId == null ? "" : entryId.trim();
        String normalizedFieldKey = fieldKey == null ? "" : fieldKey.trim();
        String rawValue = value == null ? "" : value.trim();
        if (normalizedRoomId.isBlank() || normalizedEntryId.isBlank() || normalizedFieldKey.isBlank()) {
            sendError(player, "载具编辑参数无效");
            return;
        }
        ConquestDataManager dataManager = ConquestDataManager.of(player.server);
        RoomDefinition room = dataManager.roomDefinitions().findDefinition(normalizedRoomId).orElse(null);
        if (room == null) {
            sendError(player, "未知房间: " + normalizedRoomId);
            return;
        }
        RoomVehicleBackend backend = dataManager.serverConfig().getVehicleBackend();
        VehicleConfigRepository repository = dataManager.vehicleConfigs();
        List<VehicleConfigEntry> entries = new ArrayList<>(repository.getEntries(normalizedRoomId, room.gameplayId().value(), backend));
        VehicleConfigEntry target = entries.stream()
            .filter(entry -> entry != null && normalizedEntryId.equalsIgnoreCase(entry.getEntryId()))
            .findFirst()
            .orElse(null);
        if (target == null) {
            sendError(player, "未找到载具条目: " + normalizedEntryId);
            return;
        }
        try {
            applyVehicleFieldUpdate(entries, target, normalizedFieldKey, rawValue);
        } catch (IllegalArgumentException exception) {
            sendError(player, exception.getMessage());
            return;
        } catch (Exception exception) {
            sendError(player, "编辑失败: " + exception.getMessage());
            return;
        }
        entries.sort(Comparator.comparing(VehicleConfigEntry::getEntryId, String.CASE_INSENSITIVE_ORDER));
        repository.saveEntries(normalizedRoomId, room.gameplayId().value(), backend, entries);
        syncVehicleRuntimeIfNeeded(player, room, backend);
        sendAdminVehicleSnapshot(player, false);
        sendInfo(player, "已更新载具 " + target.getEntryId() + "." + normalizedFieldKey);
    }

    private static void syncVehicleRuntimeIfNeeded(ServerPlayer player, RoomDefinition room, RoomVehicleBackend backend) {
        if (player == null || player.server == null || room == null || backend == null) {
            return;
        }
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(player.server);
        if (!manager.isInitialized()) {
            return;
        }
        manager.coreService().vehicleRuntimeService().syncConfiguredEntries(room.roomId(), room.gameplayId().value(), backend);
    }

    private static void applyVehicleFieldUpdate(
        List<VehicleConfigEntry> allEntries,
        VehicleConfigEntry entry,
        String fieldKey,
        String rawValue
    ) {
        String normalizedField = fieldKey == null ? "" : fieldKey.trim().toLowerCase(Locale.ROOT);
        switch (normalizedField) {
            case "entryid" -> {
                String newEntryId = rawValue == null ? "" : rawValue.trim();
                if (newEntryId.isBlank()) {
                    throw new IllegalArgumentException("entryId 不能为空");
                }
                boolean duplicated = allEntries.stream().anyMatch(candidate ->
                    candidate != null
                        && candidate != entry
                        && newEntryId.equalsIgnoreCase(candidate.getEntryId())
                );
                if (duplicated) {
                    throw new IllegalArgumentException("载具条目 ID 已存在: " + newEntryId);
                }
                entry.setEntryId(newEntryId);
            }
            case "team" -> {
                String normalizedTeam = normalizeVehicleTeam(rawValue);
                if (normalizedTeam.isBlank()) {
                    throw new IllegalArgumentException("team 仅支持 campA 或 campB");
                }
                entry.setTeam(normalizedTeam);
            }
            case "vehicleid" -> entry.setVehicleId(rawValue);
            case "ywzjvehicleid" -> entry.setYwzjVehicleId(rawValue);
            case "displayname", "displayid" -> entry.setDisplayName(rawValue);
            case "requiredteamsize" -> entry.setRequiredTeamSize(parseVehicleInt(rawValue, "requiredTeamSize"));
            case "respawnseconds" -> entry.setRespawnSeconds(parseVehicleInt(rawValue, "respawnSeconds"));
            case "spawnx" -> entry.setSpawnX(parseVehicleDouble(rawValue, "spawnX"));
            case "spawny" -> entry.setSpawnY(parseVehicleDouble(rawValue, "spawnY"));
            case "spawnz" -> entry.setSpawnZ(parseVehicleDouble(rawValue, "spawnZ"));
            case "spawnyaw45" -> entry.setSpawnYaw45(normalizeYaw45(parseVehicleInt(rawValue, "spawnYaw45")));
            case "stageindex" -> entry.setStageIndex(rawValue == null || rawValue.trim().isBlank() ? null : parseVehicleInt(rawValue, "stageIndex"));
            case "teamwidedeployenabled" -> entry.setTeamWideDeployEnabled(parseVehicleBoolean(rawValue, "teamWideDeployEnabled"));
            case "enabled" -> entry.setEnabled(parseVehicleBoolean(rawValue, "enabled"));
            default -> throw new IllegalArgumentException("不支持的载具字段: " + fieldKey);
        }
    }

    private static List<VehicleAdminEditorSnapshotPacket.VehicleFieldPayload> buildVehicleFieldPayloads(VehicleConfigEntry entry) {
        if (entry == null) {
            return List.of();
        }
        List<VehicleAdminEditorSnapshotPacket.VehicleFieldPayload> fields = new ArrayList<>();
        fields.add(vehicleField("entryId", "entryId", entry.getEntryId(), !entry.getEntryId().isBlank(), true, "STRING", List.of(), false));
        fields.add(vehicleField("team", "team", normalizeVehicleTeam(entry.getTeam()), !entry.getTeam().isBlank(), true, "STRING", List.of("campA", "campB"), false));
        fields.add(vehicleField("vehicleId", "vehicleId", entry.getVehicleId(), !entry.getVehicleId().isBlank(), true, "STRING", List.of(), false));
        fields.add(vehicleField("ywzjVehicleId", "ywzjVehicleId", entry.getYwzjVehicleId(), !entry.getYwzjVehicleId().isBlank(), true, "STRING", List.of(), false));
        fields.add(vehicleField("displayName", "displayName", entry.getDisplayName(), !entry.getDisplayName().isBlank(), true, "STRING", List.of(), true));
        fields.add(vehicleField("requiredTeamSize", "requiredTeamSize", String.valueOf(entry.getRequiredTeamSize()), true, true, "NUMBER", List.of(), false));
        fields.add(vehicleField("respawnSeconds", "respawnSeconds", String.valueOf(entry.getRespawnSeconds()), true, true, "NUMBER", List.of(), false));
        fields.add(vehicleField("spawnX", "spawnX", formatVehicleDouble(entry.getSpawnX()), true, true, "NUMBER", List.of(), false));
        fields.add(vehicleField("spawnY", "spawnY", formatVehicleDouble(entry.getSpawnY()), true, true, "NUMBER", List.of(), false));
        fields.add(vehicleField("spawnZ", "spawnZ", formatVehicleDouble(entry.getSpawnZ()), true, true, "NUMBER", List.of(), false));
        fields.add(vehicleField("spawnYaw45", "spawnYaw45", String.valueOf(entry.getSpawnYaw45()), true, true, "NUMBER", List.of(), false));
        fields.add(vehicleField("stageIndex", "stageIndex", entry.getStageIndex() == null ? "" : String.valueOf(entry.getStageIndex()), entry.getStageIndex() != null, true, "OPTIONAL_NUMBER", List.of(), false));
        fields.add(vehicleField("teamWideDeployEnabled", "teamWideDeployEnabled", String.valueOf(entry.isTeamWideDeployEnabled()), true, true, "BOOLEAN", List.of("true", "false"), false));
        fields.add(vehicleField("enabled", "enabled", String.valueOf(entry.isEnabled()), true, true, "BOOLEAN", List.of("true", "false"), false));
        return fields;
    }

    private static VehicleAdminEditorSnapshotPacket.VehicleFieldPayload vehicleField(
        String fieldKey,
        String displayName,
        String value,
        boolean configured,
        boolean editable,
        String valueType,
        List<String> suggestionValues,
        boolean supportsQuotedValue
    ) {
        return new VehicleAdminEditorSnapshotPacket.VehicleFieldPayload(
            fieldKey,
            displayName,
            value == null ? "" : value,
            configured,
            editable,
            valueType,
            suggestionValues == null ? List.of() : suggestionValues,
            supportsQuotedValue
        );
    }

    private static String safeVehicleField(String value) {
        return value == null ? "" : value;
    }

    private static String formatVehicleDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int parseVehicleInt(String rawValue, String fieldName) {
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (Exception exception) {
            throw new IllegalArgumentException(fieldName + " 必须是整数");
        }
    }

    private static double parseVehicleDouble(String rawValue, String fieldName) {
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (Exception exception) {
            throw new IllegalArgumentException(fieldName + " 必须是数字");
        }
    }

    private static boolean parseVehicleBoolean(String rawValue, String fieldName) {
        Boolean parsed = parseBoolean(rawValue);
        if (parsed == null) {
            throw new IllegalArgumentException(fieldName + " 仅支持 true/false");
        }
        return parsed;
    }

    private static String normalizeVehicleTeam(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        if (normalized.equalsIgnoreCase("campA")) {
            return "campA";
        }
        if (normalized.equalsIgnoreCase("campB")) {
            return "campB";
        }
        return "";
    }

    private static int normalizeYaw45(int rawYaw45) {
        int normalized = rawYaw45 % 8;
        return normalized < 0 ? normalized + 8 : normalized;
    }

    private static RoomConfigDescriptor resolveEditableRoomConfigDescriptor(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String trimmed = rawKey.trim();
        int separator = trimmed.indexOf(':');
        if (separator > 0 && separator < trimmed.length() - 1 && separator == trimmed.lastIndexOf(':')) {
            RoomConfigDescriptor descriptor = RoomConfigDescriptorRegistry.find(trimmed.substring(separator + 1)).orElse(null);
            if (descriptor != null) {
                return descriptor;
            }
        }
        return RoomConfigDescriptorRegistry.find(trimmed).orElse(null);
    }

    private static boolean applyClassSelection(ServerPlayer player, String classKey) {
        if (isRandomLoadoutEnabledInCurrentRoom(player)) {
            sendError(player, "当前房间已启用随机配备，兵种将于部署时随机发放");
            return false;
        }
        PlayerClassType classType = PlayerClassType.fromInput(classKey);
        if (classType == null) {
            sendError(player, "兵种选择无效");
            return false;
        }
        PlayerDataStore playerDataHandler = ConquestDataManager.of(player.server).playerData();
        PlayerDataModel playerData = playerDataHandler.getPlayerData(player.getUUID());
        playerData.setCurrentClassType(classType.getKey());
        playerDataHandler.savePlayerData(player.getUUID(), playerData);
        boolean refreshedInRange = refreshRangeLoadoutIfNeeded(player);
        sendInfo(
            player,
            refreshedInRange
                ? "已切换兵种：" + classType.getDisplayName() + "，并已在靶场中刷新当前配装"
                : "已切换兵种：" + classType.getDisplayName() + "，将在下一次重生时发放"
        );
        return true;
    }

    private static boolean applySlotSelection(ServerPlayer player, String classKey, int slotIndex, String itemId) {
        if (isRandomLoadoutEnabledInCurrentRoom(player)) {
            sendError(player, "当前房间已启用随机配备，配装将于部署时随机发放");
            return false;
        }
        PlayerClassType classType = PlayerClassType.fromInput(classKey);
        CustomLoadoutSlot slot = CustomLoadoutSlot.fromIndex(slotIndex);
        String normalizedId = CustomLoadoutService.normalizeEquipmentId(itemId);
        if (classType == null || slot == null || normalizedId.isBlank() || !CustomLoadoutService.isValidEquipmentId(normalizedId)) {
            sendError(player, "配装参数无效");
            return false;
        }
        RoomDefinition room = resolveCurrentRoom(player);
        RunningRuntimeState runningState = resolveCurrentRunningState(player);
        RoomTeamId teamId = resolveCurrentRoomTeam(player);
        boolean roomRestricted = RoomFixedLoadoutRules.hasRestriction(room, teamId, slot);
        if (roomRestricted && runningState == null) {
            sendError(player, "当前房间运行态不可用，暂时无法保存房间限定配装");
            return false;
        }
        EquipmentDataModel equipmentData = ConquestDataManager.of(player.server).equipmentData().getData();
        int level = PlayerLevelService.getEffectiveLevel(player);
        if (roomRestricted && CustomLoadoutService.isReservedEmptyEquipmentId(normalizedId)) {
            if (!RoomFixedLoadoutRules.allowsEmpty(room, teamId, slot)) {
                sendError(player, "当前房间不允许该槽位为空");
                return false;
            }
            CustomLoadoutService.setSelectedIdForRoomSlot(runningState, player.getUUID(), classType, slot, CustomLoadoutService.EMPTY_EQUIPMENT_ID);
            boolean refreshedInRange = refreshRangeLoadoutIfNeeded(player);
            sendSystem(
                player,
                Component.literal(
                    refreshedInRange
                        ? String.format(Locale.ROOT, "已设置当前房间：%s %s -> 空，并已在靶场中刷新当前配装", classType.getDisplayName(), slot.getKey())
                        : String.format(Locale.ROOT, "已设置当前房间：%s %s -> 空，将在下一次重生时发放", classType.getDisplayName(), slot.getKey())
                ).withStyle(ChatFormatting.AQUA)
            );
            return true;
        }
        EquipmentDataModel.EquipmentEntry entry = equipmentData == null ? null : equipmentData.getEquipmentById().get(normalizedId);
        if (entry == null || entry.getSlot() != slot.getIndex()) {
            sendError(player, "该装备不存在或槽位不匹配");
            return false;
        }
        // GD656Mods: 原本只在"房间未限定"时校验兵种(!roomRestricted && ...), 于是限定槽位可以跨兵种选道具;
        // 改为走统一判据 —— 房间保持默认(true)时行为完全不变, 关掉开关才真正按兵种过滤。
        if (!RoomFixedLoadoutRules.allowsEntryForClass(room, entry, classType)) {
            sendError(player, "该装备不适用于当前兵种");
            return false;
        }
        // GD656Mods: 房间开启"忽略枪械等级"时, 武器槽允许保存未解锁装备(试用中)
        boolean trialAllowed = CustomLoadoutService.isWeaponSlot(slot) && RoomFixedLoadoutRules.ignoresGunLevel(room);
        if (!trialAllowed && !CustomLoadoutService.isEntryUnlocked(entry, level)) {
            sendError(player, "等级不足，解锁等级：" + entry.getUnlockLevel());
            return false;
        }
        if (roomRestricted && !RoomFixedLoadoutRules.allowsEntryId(room, teamId, slot, normalizedId)) {
            sendError(player, "当前房间已禁用这个装备");
            return false;
        }
        if (roomRestricted) {
            CustomLoadoutService.setSelectedIdForRoomSlot(runningState, player.getUUID(), classType, slot, normalizedId);
            boolean refreshedInRange = refreshRangeLoadoutIfNeeded(player);
            sendSystem(
                player,
                Component.literal(
                    refreshedInRange
                        ? String.format(Locale.ROOT, "已设置当前房间：%s %s -> %s，并已在靶场中刷新当前配装", classType.getDisplayName(), slot.getKey(), entry.getDisplayName())
                        : String.format(Locale.ROOT, "已设置当前房间：%s %s -> %s，将在下一次重生时发放", classType.getDisplayName(), slot.getKey(), entry.getDisplayName())
                ).withStyle(ChatFormatting.AQUA)
            );
            return true;
        }
        PlayerDataStore playerDataHandler = ConquestDataManager.of(player.server).playerData();
        PlayerDataModel playerData = playerDataHandler.getPlayerData(player.getUUID());
        CustomLoadoutService.setSelectedIdForClassSlot(playerData, classType, slot, normalizedId);
        playerDataHandler.savePlayerData(player.getUUID(), playerData);
        boolean refreshedInRange = refreshRangeLoadoutIfNeeded(player);
        sendSystem(
            player,
            Component.literal(
                refreshedInRange
                    ? String.format(Locale.ROOT, "已设置：%s %s -> %s，并已在靶场中刷新当前配装", classType.getDisplayName(), slot.getKey(), entry.getDisplayName())
                    : String.format(Locale.ROOT, "已设置：%s %s -> %s，将在下一次重生时发放", classType.getDisplayName(), slot.getKey(), entry.getDisplayName())
            )
                .withStyle(ChatFormatting.AQUA)
        );
        return true;
    }

    private static boolean applyAttachmentSelection(
        ServerPlayer player,
        String editedClassKey,
        int slotIndex,
        String loadoutEntryId,
        String attachmentType,
        String attachmentEntryId
    ) {
        if (isRandomLoadoutEnabledInCurrentRoom(player)) {
            sendError(player, "当前房间已启用随机配备，武器配件不会按个人配置发放");
            return false;
        }
        if (isRandomAttachmentEnabledInCurrentRoom(player)) {
            sendError(player, "当前房间已启用房间随机配件，武器配件将由服务器随机发放");
            return false;
        }
        CustomLoadoutSlot slot = CustomLoadoutSlot.fromIndex(slotIndex);
        if (!WeaponAttachmentService.supportsSlot(slot)) {
            sendError(player, "当前槽位不支持配件配置");
            return false;
        }
        String normalizedLoadoutEntryId = CustomLoadoutService.normalizeEquipmentId(loadoutEntryId);
        String normalizedAttachmentType = WeaponAttachmentService.normalizeAttachmentType(attachmentType);
        if (normalizedLoadoutEntryId.isBlank() || normalizedAttachmentType.isBlank()) {
            sendError(player, "配件参数无效");
            return false;
        }
        ConquestDataManager dataManager = ConquestDataManager.of(player.server);
        EquipmentDataModel equipmentData = dataManager.equipmentData().getData();
        WeaponAttachmentDataModel attachmentData = dataManager.weaponAttachments().getData();
        PlayerDataStore playerDataStore = dataManager.playerData();
        PlayerDataModel playerData = playerDataStore.getPlayerData(player.getUUID());
        RunningRuntimeState runningState = resolveCurrentRunningState(player);
        RoomDefinition room = resolveCurrentRoom(player);
        PlayerClassType currentClass = CustomLoadoutService.resolveCurrentClass(playerData, runningState, player.getUUID());
        PlayerClassType editedClass = editedClassKey == null || editedClassKey.isBlank() ? currentClass : PlayerClassType.fromInput(editedClassKey);
        if (editedClass == null) { sendError(player, "兵种配置无效，请重新选择兵种"); return false; }
        currentClass = editedClass;
        String selectedLoadoutId = resolveUiSelectedId(room, runningState, playerData, player.getUUID(), resolveCurrentRoomTeam(player), currentClass, slot);
        if (!normalizedLoadoutEntryId.equals(selectedLoadoutId)) {
            sendError(player, "当前武器已变化，请重新打开配件选择器");
            return false;
        }
        EquipmentDataModel.EquipmentEntry equipmentEntry = equipmentData == null ? null : equipmentData.getEquipmentById().get(selectedLoadoutId);
        if (equipmentEntry == null) {
            sendError(player, "当前武器条目不存在");
            return false;
        }
        ItemStack weaponStack = deserializeItem(equipmentEntry.getItemSnbt());
        TaczAttachmentCompatService compatService = TaczAttachmentCompatService.getInstance();
        if (!compatService.isTaczGun(weaponStack)) {
            sendError(player, "当前武器不是 TACZ 枪械");
            return false;
        }
        if (!compatService.allowAttachmentType(weaponStack, normalizedAttachmentType)) {
            sendError(player, "当前枪械不支持该配件类型");
            return false;
        }
        int playerLevel = PlayerLevelService.getEffectiveLevel(player);
        boolean explicitNone = attachmentEntryId == null || attachmentEntryId.isBlank();
        String normalizedAttachmentEntryId = WeaponAttachmentService.normalizeAttachmentId(attachmentEntryId);
        if (!explicitNone) {
            WeaponAttachmentDataModel.WeaponAttachmentEntry attachmentEntry =
                attachmentData == null ? null : attachmentData.getAttachmentById().get(normalizedAttachmentEntryId);
            if (attachmentEntry == null) {
                sendError(player, "该配件不存在");
                return false;
            }
            if (!normalizedAttachmentType.equals(WeaponAttachmentService.normalizeAttachmentType(attachmentEntry.getAttachmentType()))) {
                sendError(player, "配件类型不匹配");
                return false;
            }
            if (!WeaponAttachmentService.isEntryUnlocked(attachmentEntry, playerLevel)) {
                sendError(player, "等级不足，配件解锁等级：" + attachmentEntry.getUnlockLevel());
                return false;
            }
            ItemStack attachmentStack = compatService.deserializeStack(attachmentEntry.getItemSnbt());
            if (!compatService.isOfficialAttachment(attachmentStack)) {
                sendError(player, "该配件条目不是有效的正式 TACZ 配件");
                return false;
            }
            if (!compatService.allowAttachment(weaponStack, attachmentStack)) {
                sendError(player, "当前枪械无法安装该配件");
                return false;
            }
        }
        WeaponAttachmentService.setSelectedAttachmentId(
            playerData,
            currentClass.getKey(),
            normalizedLoadoutEntryId,
            normalizedAttachmentType,
            normalizedAttachmentEntryId,
            explicitNone
        );
        playerDataStore.savePlayerData(player.getUUID(), playerData);
        boolean refreshedInRange = currentClass == CustomLoadoutService.resolveCurrentClass(playerData) && refreshRangeLoadoutIfNeeded(player);
        sendInfo(
            player,
            refreshedInRange
                ? "已更新 " + WeaponAttachmentService.getAttachmentTypeLabel(normalizedAttachmentType) + "，并已在靶场中刷新当前武器"
                : "已更新 " + WeaponAttachmentService.getAttachmentTypeLabel(normalizedAttachmentType) + "，将在下一次重生时发放"
        );
        return true;
    }

    private static void leaveSession(ServerPlayer player) {
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(player.server);
        RangeRuntimeManager rangeManager = RangeRuntimeManager.of(player.server);
        if (rangeManager.isInRange(player.getUUID())) {
            String result = rangeManager.leaveRange(player);
            QuickAccessItemService.refreshHotbar(player);
            sendInfo(player, result);
            return;
        }
        if (coreManager.isInitialized() && coreManager.findPlayerRoomId(player.getUUID()).isPresent()) {
            RoomSessionTransitionService.ReturnToHubExecutionResult result = RoomSessionTransitionService.of(player.server)
                .returnToHub(player);
            if (result.denied()) {
                sendError(player, result.deniedMessage());
                return;
            }
            sendInfo(player, result.operatorMessage());
            return;
        }
        sendError(player, "当前不在房间或靶场中");
    }

    private static void switchCamp(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(player.server);
        if (!coreManager.isInitialized()) {
            sendError(player, "房间服务尚未初始化");
            return;
        }
        String roomId = coreManager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (roomId == null || roomId.isBlank()) {
            sendError(player, "当前不在房间中");
            return;
        }
        String result = coreManager.requestActivePlayerCampSwitch(
            roomId,
            player.getUUID(),
            player.getScoreboardName(),
            player.hasPermissions(2),
            player.server.getTickCount()
        );
        if (result == null || result.isBlank()) {
            return;
        }
        if (result.startsWith("已")) {
            sendInfo(player, result);
            return;
        }
        sendError(player, result);
    }

    private static boolean isRandomLoadoutEnabledInCurrentRoom(ServerPlayer player) {
        RoomDefinition room = resolveCurrentRoom(player);
        return room != null && room.commonConfig().enableRandomLoadout();
    }

    private static boolean isRandomAttachmentEnabledInCurrentRoom(ServerPlayer player) {
        RoomDefinition room = resolveCurrentRoom(player);
        return room != null && room.commonConfig().enableRandomAttachments();
    }

    private static RoomDefinition resolveCurrentRoom(ServerPlayer player) {
        if (player == null || player.server == null) {
            return null;
        }
        // GD656Mods: 统一走 RoomCoreRuntimeManager.findPlayerRoom(命令行侧也复用同一入口)
        return RoomCoreRuntimeManager.of(player.server).findPlayerRoom(player.getUUID());
    }

    /** 是否为修改配装的动作(兵种/槽位/配件选择) */
    private static boolean isLoadoutEditAction(String normalizedAction) {
        return "select_class".equals(normalizedAction)
            || "select_slot".equals(normalizedAction)
            || "select_attachment".equals(normalizedAction);
    }

    /**
     * GD656Mods: 是否处于"对局进行中且非部署流程"(配装修改需暂存)。
     * 匹配阶段(第一次对局开始前, 房间生命周期非 RUNNING)、部署界面(含第一次部署界面
     * deployViewActivePlayers 与死亡部署 isInDeployFlow)均允许直接修改配装。
     */
    private static boolean isLoadoutEditBlockedInRunning(ServerPlayer player) {
        RunningRuntimeState state = resolveCurrentRunningState(player);
        if (state == null) {
            return false;
        }
        RoomDefinition room = resolveCurrentRoom(player);
        if (room != null && room.lifecycleState() != RoomLifecycleState.RUNNING) {
            // GD656Mods: 匹配阶段(第一次对局开始前)/结算阶段修改配装立即生效, 不走暂存
            return false;
        }
        return !state.isInDeployFlow(player.getUUID())
            && !state.getDeployViewActivePlayers().contains(player.getUUID());
    }

    // GD656Mods: 对局内配装修改暂存区(玩家 UUID -> 待实现编辑列表)
    private static final Map<UUID, List<PendingLoadoutEdit>> PENDING_LOADOUT_EDITS = new LinkedHashMap<>();

    private record PendingLoadoutEdit(
        String action,
        String classKey,
        int slotIndex,
        String itemId,
        String contextId,
        String attachmentType
    ) {
    }

    private static void queuePendingLoadoutEdit(
        ServerPlayer player,
        String action,
        String classKey,
        int slotIndex,
        String itemId,
        String contextId,
        String attachmentType
    ) {
        if (player == null) {
            return;
        }
        PENDING_LOADOUT_EDITS.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>())
            .add(new PendingLoadoutEdit(action, classKey, slotIndex, itemId, contextId, attachmentType));
    }

    /** 暂存前校验房间规则(随机配装/随机配件房间不允许修改配装) */
    private static boolean validatePendingEditForRoom(ServerPlayer player, String action) {
        if (isRandomLoadoutEnabledInCurrentRoom(player)) {
            sendError(player, "select_slot".equals(action)
                ? "当前房间已启用随机配备，配装将于部署时随机发放"
                : "select_attachment".equals(action)
                    ? "当前房间已启用随机配备，武器配件不会按个人配置发放"
                    : "当前房间已启用随机配备，兵种将于部署时随机发放");
            return false;
        }
        if ("select_attachment".equals(action) && isRandomAttachmentEnabledInCurrentRoom(player)) {
            sendError(player, "当前房间已启用房间随机配件，武器配件将由服务器随机发放");
            return false;
        }
        return true;
    }

    /**
     * GD656Mods: 应用玩家暂存的配装修改(玩家死亡/对局结束时调用)。
     * 按暂存顺序逐个应用, 全部完成后发送"实现"提示; 无暂存或全部失败则不发送。
     */
    public static void applyPendingLoadoutEdits(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        List<PendingLoadoutEdit> edits = PENDING_LOADOUT_EDITS.remove(player.getUUID());
        if (edits == null || edits.isEmpty()) {
            return;
        }
        boolean anyApplied = false;
        for (PendingLoadoutEdit edit : edits) {
            if (edit == null) {
                continue;
            }
            boolean applied = switch (edit.action()) {
                case "select_class" -> applyClassSelection(player, edit.classKey());
                case "select_slot" -> applySlotSelection(player, edit.classKey(), edit.slotIndex(), edit.itemId());
                case "select_attachment" -> applyAttachmentSelection(player, edit.classKey(), edit.slotIndex(), edit.contextId(), edit.attachmentType(), edit.itemId());
                default -> false;
            };
            anyApplied |= applied;
        }
        if (anyApplied) {
            sendInfo(player, "您的上一次配装修改暂存已实现");
        }
    }

    /** 玩家离开房间/对局时丢弃未实现的暂存配装修改 */
    public static void discardPendingLoadoutEdits(UUID playerId) {
        if (playerId != null) {
            PENDING_LOADOUT_EDITS.remove(playerId);
        }
    }

    private static RunningRuntimeState resolveCurrentRunningState(ServerPlayer player) {
        if (player == null || player.server == null) {
            return null;
        }
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(player.server);
        String roomId = manager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (roomId == null || roomId.isBlank()) {
            return null;
        }
        return manager.findRunningState(roomId).orElse(null);
    }

    private static RoomTeamId resolveCurrentRoomTeam(ServerPlayer player) {
        if (player == null || player.server == null) {
            return null;
        }
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(player.server);
        String roomId = manager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (roomId == null || roomId.isBlank()) {
            return null;
        }
        return manager.findRoomTeam(roomId, player.getUUID())
            .or(() -> manager.findQueueTeam(roomId, player.getUUID()))
            .orElse(null);
    }

    private static boolean refreshRangeLoadoutIfNeeded(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        RangeRuntimeManager rangeManager = RangeRuntimeManager.of(player.server);
        if (!rangeManager.isInRange(player.getUUID())) {
            return false;
        }
        rangeManager.refreshLoadoutIfInRange(player);
        return true;
    }

    private static LoadoutUiOpenPacket buildSnapshot(
        ServerPlayer player,
        boolean openPanel,
        int targetTabIndex,
        LoadoutVehiclePageSnapshotBuilder.Snapshot vehiclePageSnapshot
    ) {
        ConquestDataManager dataManager = ConquestDataManager.of(player.server);
        EquipmentDataModel equipmentData = dataManager.equipmentData().getData();
        WeaponAttachmentDataModel attachmentData = dataManager.weaponAttachments().getData();
        PlayerDataModel playerData = dataManager.playerData().getPlayerData(player.getUUID());
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(player.server);
        String roomId = roomManager.findPlayerRoomId(player.getUUID()).orElse("");
        RoomDefinition room = roomId.isBlank() ? null : roomManager.findRoom(roomId).orElse(null);
        RunningRuntimeState runningState = roomId.isBlank() ? null : roomManager.findRunningState(roomId).orElse(null);
        RoomTeamId teamId = roomId.isBlank()
            ? null
            : roomManager.findRoomTeam(roomId, player.getUUID()).or(() -> roomManager.findQueueTeam(roomId, player.getUUID())).orElse(null);
        PlayerClassType currentClass = CustomLoadoutService.resolveCurrentClass(playerData, runningState, player.getUUID());
        int level = PlayerLevelService.getEffectiveLevel(player);
        String playerTitleText = PlayerTitleService.of(player.server)
            .findTitle(playerData.getEquippedTitleId())
            .map(definition -> definition.text() == null ? "" : definition.text())
            .orElse("");
        boolean isAdmin = player.hasPermissions(2);
        TaczAttachmentCompatService compatService = TaczAttachmentCompatService.getInstance();
        boolean playerDataChanged = false;

        List<LoadoutUiOpenPacket.ClassPayload> classes = new ArrayList<>();
        if (equipmentData != null) {
            for (PlayerClassType classType : PlayerClassType.values()) {
                List<LoadoutUiOpenPacket.SlotPayload> slots = new ArrayList<>();
                for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
                    List<LoadoutUiOpenPacket.EntryPayload> entries = new ArrayList<>();
                    boolean roomRestricted = RoomFixedLoadoutRules.hasRestriction(room, teamId, slot);
                    // GD656Mods: 原本"房间限定"时直接列出该槽位全部装备(不分兵种), 于是界面里能选到跨兵种道具;
                    // 房间关掉"忽略兵种限制"后, 无论是否限定都按兵种过滤。
                    boolean filterByClass = !roomRestricted || !RoomFixedLoadoutRules.ignoresClassLimitOnRestriction(room);
                    List<EquipmentDataModel.EquipmentEntry> slotEntries = filterByClass
                        ? CustomLoadoutService.listEntriesByClassAndSlot(equipmentData, classType, slot)
                        : CustomLoadoutService.listEntriesBySlot(equipmentData, slot);
                    for (EquipmentDataModel.EquipmentEntry entry : slotEntries) {
                        if (entry.getUnlockLevel() > 100) {
                            continue;
                        }
                        String normalizedEntryId = CustomLoadoutService.normalizeEquipmentId(entry.getId());
                        // GD656Mods: 房间开启"忽略枪械等级"时, 未解锁的武器槽装备仍可选, 并标记为"试用"
                        boolean trial = !CustomLoadoutService.isEntryUnlocked(entry, level)
                            && CustomLoadoutService.isWeaponSlot(slot)
                            && RoomFixedLoadoutRules.ignoresGunLevel(room);
                        entries.add(new LoadoutUiOpenPacket.EntryPayload(
                            normalizedEntryId,
                            entry.getDisplayName() == null ? "" : entry.getDisplayName(),
                            Math.max(0, entry.getUnlockLevel()),
                            CustomLoadoutService.isEntryUnlocked(entry, level),
                            !roomRestricted || RoomFixedLoadoutRules.allowsEntryId(room, teamId, slot, normalizedEntryId),
                            entry.getItemSnbt() == null ? "" : entry.getItemSnbt(),
                            trial
                        ));
                    }
                    if (roomRestricted && RoomFixedLoadoutRules.allowsEmpty(room, teamId, slot)) {
                        EquipmentDataModel.EquipmentEntry emptyEntry = CustomLoadoutService.createReservedEmptyEntry(slot);
                        if (emptyEntry != null) {
                            entries.add(new LoadoutUiOpenPacket.EntryPayload(
                                CustomLoadoutService.EMPTY_EQUIPMENT_ID,
                                emptyEntry.getDisplayName(),
                                0,
                                true,
                                true,
                                "",
                                false
                            ));
                        }
                    }
                    String selectedId = resolveUiSelectedId(room, runningState, playerData, player.getUUID(), teamId, classType, slot);
                    slots.add(new LoadoutUiOpenPacket.SlotPayload(
                        slot.getIndex(),
                        slot.getKey(),
                        slotLabel(slot),
                        selectedId == null ? "" : selectedId,
                        roomRestricted,
                        entries
                    ));
                }
                classes.add(new LoadoutUiOpenPacket.ClassPayload(classType.getKey(), classType.getDisplayName(), slots));
            }
        }

        List<LoadoutUiOpenPacket.AdminEquipmentPayload> adminEquipment = new ArrayList<>();
        if (isAdmin && equipmentData != null) {
            for (EquipmentDataModel.EquipmentEntry entry : equipmentData.getEquipmentById().values()) {
                if (entry == null) {
                    continue;
                }
                adminEquipment.add(new LoadoutUiOpenPacket.AdminEquipmentPayload(
                    CustomLoadoutService.normalizeEquipmentId(entry.getId()),
                    entry.getSlot(),
                    entry.getSlotName() == null ? "" : entry.getSlotName(),
                    entry.getDisplayName() == null ? "" : entry.getDisplayName(),
                    entry.getExtraData(),
                    Math.max(0, entry.getUnlockLevel()),
                    entry.getAvailableClasses(),
                    entry.getItemSnbt() == null ? "" : entry.getItemSnbt(),
                    Math.max(0, entry.getReplenishSeconds()),
                    entry.isAmmoBoxRefillEnabled()
                ));
            }
        }

        List<LoadoutUiOpenPacket.AdminAttachmentPayload> adminAttachments = new ArrayList<>();
        if (isAdmin && attachmentData != null) {
            for (WeaponAttachmentDataModel.WeaponAttachmentEntry entry : attachmentData.getAttachmentById().values()) {
                if (entry == null) {
                    continue;
                }
                adminAttachments.add(new LoadoutUiOpenPacket.AdminAttachmentPayload(
                    WeaponAttachmentService.normalizeAttachmentId(entry.getId()),
                    entry.getDisplayName(),
                    WeaponAttachmentService.normalizeAttachmentType(entry.getAttachmentType()),
                    entry.getUnlockLevel(),
                    entry.getItemSnbt(),
                    entry.getFutureRequirement()
                ));
            }
        }

        List<LoadoutUiOpenPacket.RoomStatisticPayload> roomStatistics = new ArrayList<>();
        for (RoomDefinition definition : dataManager.roomDefinitions().getAllDefinitions()) {
            if (definition == null) {
                continue;
            }
            RoomStatisticDataModel statistic = dataManager.roomStatisticData().getRoomStatistics(definition.roomId());
            roomStatistics.add(new LoadoutUiOpenPacket.RoomStatisticPayload(
                definition.roomId(),
                definition.displayName(),
                definition.gameplayMapId(),
                statistic.totalPlayCountValue(),
                statistic.totalMatchesValue(),
                statistic.campAWinsValue(),
                statistic.campBWinsValue(),
                statistic.drawMatchesValue(),
                statistic.totalMatchDurationSecondsValue(),
                statistic.ratingVoteCountValue(),
                statistic.ratingScoreTotalValue()
            ));
        }

        List<LoadoutUiOpenPacket.WeaponAttachmentPanelPayload> attachmentPanels = new ArrayList<>();
        if (equipmentData != null && attachmentData != null) {
            for (CustomLoadoutSlot slot : List.of(CustomLoadoutSlot.PRIMARY_WEAPON, CustomLoadoutSlot.SECONDARY_WEAPON)) {
                String selectedId = resolveUiSelectedId(room, runningState, playerData, player.getUUID(), teamId, currentClass, slot);
                EquipmentDataModel.EquipmentEntry equipmentEntry = equipmentData.getEquipmentById().get(selectedId);
                ItemStack weaponStack = equipmentEntry == null ? ItemStack.EMPTY : deserializeItem(equipmentEntry.getItemSnbt());
                boolean available = equipmentEntry != null && !weaponStack.isEmpty();
                boolean taczGun = available && compatService.isTaczGun(weaponStack);
                if (available && taczGun) {
                    playerDataChanged |= WeaponAttachmentService.sanitizeSelectionForWeapon(
                        playerData,
                        selectedId,
                        weaponStack,
                        attachmentData,
                        level,
                        compatService
                    );
                }
                attachmentPanels.add(buildAttachmentPanelPayload(
                    player.server,
                    slot,
                    selectedId,
                    equipmentEntry,
                    weaponStack,
                    available,
                    taczGun,
                    attachmentData,
                    playerData,
                    level,
                    compatService
                ));
            }
        }

        if (playerDataChanged) {
            dataManager.playerData().savePlayerData(player.getUUID(), playerData);
        }

        return new LoadoutUiOpenPacket(
            level,
            playerTitleText,
            currentClass.getKey(),
            openPanel,
            targetTabIndex,
            isAdmin,
            vehiclePageSnapshot.available(),
            classes,
            adminEquipment,
            adminAttachments,
            roomStatistics,
            attachmentPanels,
            vehiclePageSnapshot.entries()
        );
    }

    /**
     * The vehicle page is the only part refreshed when another player changes
     * vehicle availability. Reuse the expensive player/loadout payload and
     * replace only that dynamic section.
     */
    private record CachedSnapshot(MinecraftServer server, LoadoutUiOpenPacket packet) {
        private LoadoutUiOpenPacket toPacket(
            boolean openPanel,
            int targetTabIndex,
            LoadoutVehiclePageSnapshotBuilder.Snapshot vehicleSnapshot
        ) {
            return new LoadoutUiOpenPacket(
                packet.getPlayerLevel(),
                packet.getPlayerTitleText(),
                packet.getCurrentClassKey(),
                openPanel,
                targetTabIndex,
                packet.isAdmin(),
                vehicleSnapshot.available(),
                packet.getClasses(),
                packet.getAdminEquipment(),
                packet.getAdminAttachments(),
                packet.getRoomStatistics(),
                packet.getAttachmentPanels(),
                vehicleSnapshot.entries()
            );
        }
    }

    private static LoadoutUiOpenPacket.WeaponAttachmentPanelPayload buildAttachmentPanelPayload(
        MinecraftServer server,
        CustomLoadoutSlot slot,
        String loadoutEntryId,
        EquipmentDataModel.EquipmentEntry equipmentEntry,
        ItemStack weaponStack,
        boolean available,
        boolean taczGun,
        WeaponAttachmentDataModel attachmentData,
        PlayerDataModel playerData,
        int playerLevel,
        TaczAttachmentCompatService compatService
    ) {
        List<LoadoutUiOpenPacket.AttachmentTypePayload> typePayloads = new ArrayList<>();
        String normalizedLoadoutEntryId = CustomLoadoutService.normalizeEquipmentId(loadoutEntryId);
        String weaponGunType = available && taczGun ? compatService.resolveGunType(weaponStack) : "";
        double maxScopeMagnification = resolveMaxScopeMagnification(server, weaponGunType);
        if (available && taczGun && attachmentData != null) {
            for (String attachmentType : WeaponAttachmentService.ORDERED_ATTACHMENT_TYPES) {
                boolean typeAvailable = compatService.allowAttachmentType(weaponStack, attachmentType);
                String selectedAttachmentId = WeaponAttachmentService.getSelectedAttachmentId(playerData, normalizedLoadoutEntryId, attachmentType);
                boolean hasStoredSelection = WeaponAttachmentService.hasStoredSelection(playerData, normalizedLoadoutEntryId, attachmentType);
                String selectedDisplayName = "";
                List<LoadoutUiOpenPacket.AttachmentChoicePayload> choices = new ArrayList<>();
                if (typeAvailable) {
                    for (WeaponAttachmentDataModel.WeaponAttachmentEntry entry : WeaponAttachmentService.listEntriesByType(attachmentData, attachmentType)) {
                        ItemStack attachmentStack = compatService.deserializeStack(entry.getItemSnbt());
                        if (!compatService.allowAttachment(weaponStack, attachmentStack)) {
                            continue;
                        }
                        boolean unlocked = WeaponAttachmentService.isEntryUnlocked(entry, playerLevel);
                        choices.add(new LoadoutUiOpenPacket.AttachmentChoicePayload(
                            WeaponAttachmentService.normalizeAttachmentId(entry.getId()),
                            entry.getDisplayName(),
                            entry.getUnlockLevel(),
                            unlocked,
                            entry.getItemSnbt()
                        ));
                        if (!selectedAttachmentId.isBlank() && selectedAttachmentId.equalsIgnoreCase(entry.getId())) {
                            selectedDisplayName = entry.getDisplayName();
                        }
                    }
                } else {
                    hasStoredSelection = false;
                    selectedAttachmentId = "";
                }
                typePayloads.add(new LoadoutUiOpenPacket.AttachmentTypePayload(
                    attachmentType,
                    WeaponAttachmentService.getAttachmentTypeLabel(attachmentType),
                    typeAvailable,
                    hasStoredSelection,
                    selectedAttachmentId,
                    selectedDisplayName,
                    WeaponAttachmentService.TYPE_SCOPE.equals(attachmentType) ? maxScopeMagnification : 0.0D,
                    choices
                ));
            }
        }
        return new LoadoutUiOpenPacket.WeaponAttachmentPanelPayload(
            slot.getIndex(),
            slot.getKey(),
            slotLabel(slot),
            normalizedLoadoutEntryId,
            equipmentEntry == null ? "" : equipmentEntry.getDisplayName(),
            equipmentEntry == null ? "" : equipmentEntry.getItemSnbt(),
            available,
            taczGun,
            typePayloads
        );
    }

    private static double resolveMaxScopeMagnification(MinecraftServer server, String weaponGunType) {
        if (server == null) {
            return 0.0D;
        }
        ConquestDataManager dataManager = ConquestDataManager.of(server);
        return switch (weaponGunType == null ? "" : weaponGunType.trim().toLowerCase(Locale.ROOT)) {
            case "pistol" -> dataManager.serverConfig().getMaxScopePistol();
            case "sniper" -> dataManager.serverConfig().getMaxScopeSniper();
            case "rifle" -> dataManager.serverConfig().getMaxScopeRifle();
            case "shotgun" -> dataManager.serverConfig().getMaxScopeShotgun();
            case "smg" -> dataManager.serverConfig().getMaxScopeSmg();
            case "rpg" -> dataManager.serverConfig().getMaxScopeRpg();
            case "mg" -> dataManager.serverConfig().getMaxScopeMg();
            default -> 0.0D;
        };
    }

    private static boolean createAttachmentEntry(ServerPlayer player, WeaponAttachmentDataModel data, String id, int unlockLevel) {
        if (data == null) {
            sendError(player, "配件数据不存在");
            return false;
        }
        if (!WeaponAttachmentService.isValidAttachmentId(id)) {
            sendError(player, "配件 ID 格式非法");
            return false;
        }
        if (data.getAttachmentById().containsKey(id)) {
            sendError(player, "配件 ID 已存在: " + id);
            return false;
        }
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (stack.isEmpty()) {
            sendError(player, "请手持一个正式 TACZ 配件再创建");
            return false;
        }
        TaczAttachmentCompatService compatService = TaczAttachmentCompatService.getInstance();
        String attachmentType = compatService.resolveAttachmentType(stack);
        if (attachmentType.isBlank()) {
            sendError(player, "当前手持物品不是正式 TACZ 配件");
            return false;
        }
        WeaponAttachmentDataModel.WeaponAttachmentEntry entry = new WeaponAttachmentDataModel.WeaponAttachmentEntry();
        entry.setId(id);
        entry.setDisplayName(resolveRealItemName(player, stack, BuiltInRegistries.ITEM.getKey(stack.getItem())));
        entry.setAttachmentType(attachmentType);
        entry.setUnlockLevel(Math.max(0, unlockLevel));
        entry.setItemSnbt(serializeStack(stack));
        entry.setFutureRequirement("");
        data.getAttachmentById().put(id, entry);
        return true;
    }

    private static ItemStack parseAttachmentStack(ServerPlayer player, String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        if ("hand".equalsIgnoreCase(normalized) || "mainhand".equalsIgnoreCase(normalized)) {
            if (player == null) {
                sendError(null, "itemSnbt=hand 只能由玩家执行");
                return ItemStack.EMPTY;
            }
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                sendError(player, "当前主手没有物品");
                return ItemStack.EMPTY;
            }
            return held.copy();
        }
        try {
            CompoundTag tag = TagParser.parseTag(normalized);
            ItemStack stack = ItemStack.of(tag);
            if (stack.isEmpty()) {
                sendError(player, "itemSnbt 无法解析成有效物品");
            }
            return stack;
        } catch (Exception exception) {
            sendError(player, "itemSnbt 语法错误");
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack deserializeItem(String itemSnbt) {
        if (itemSnbt == null || itemSnbt.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.of(TagParser.parseTag(itemSnbt));
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String resolveRealItemName(ServerPlayer player, ItemStack stack, net.minecraft.resources.ResourceLocation itemKey) {
        String uploaded = PlayerItemNameUploadService.resolveUploadedMainHandName(player, itemKey);
        if (!uploaded.isBlank()) {
            return uploaded;
        }
        if (TaczDisplayNameResolver.isTaczGun(stack) && player != null && player.server != null) {
            String localized = TaczDisplayNameResolver.resolve(player.server, stack);
            if (!localized.isBlank()) {
                return localized;
            }
        }
        return resolveRealItemName(stack, itemKey);
    }

    private static String resolveRealItemName(ItemStack stack, net.minecraft.resources.ResourceLocation itemKey) {
        if (stack != null && !stack.isEmpty()) {
            String hoverName = normalizeFreeText(stack.getHoverName().getString());
            if (!hoverName.isBlank()) {
                return hoverName;
            }
        }
        return itemKey == null ? "unknown" : itemKey.toString();
    }

    private static String normalizeFreeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String serializeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag tag = new CompoundTag();
        stack.save(tag);
        return tag.toString();
    }

    private static void migrateAttachmentLoadoutEntryIdForAllPlayers(net.minecraft.server.MinecraftServer server, String oldId, String newId) {
        if (server == null) {
            return;
        }
        PlayerDataStore playerDataStore = ConquestDataManager.of(server).playerData();
        for (UUID uuid : playerDataStore.listKnownPlayerIds()) {
            PlayerDataModel playerData = playerDataStore.getPlayerData(uuid);
            boolean affected = playerData.getAllAttachmentSelectionGroups().stream().anyMatch(group -> group.containsKey(CustomLoadoutService.normalizeEquipmentId(oldId)));
            WeaponAttachmentService.renameLoadoutEntryId(playerData, oldId, newId);
            WeaponAttachmentService.cleanupEmptySelectionGroups(playerData);
            if (affected) {
                playerDataStore.savePlayerData(uuid, playerData);
            }
        }
    }

    private static void migrateAttachmentSelectionValueForAllPlayers(net.minecraft.server.MinecraftServer server, String oldId, String newId) {
        if (server == null) {
            return;
        }
        PlayerDataStore playerDataStore = ConquestDataManager.of(server).playerData();
        for (UUID uuid : playerDataStore.listKnownPlayerIds()) {
            PlayerDataModel playerData = playerDataStore.getPlayerData(uuid);
            if (WeaponAttachmentService.replaceAttachmentEntryId(playerData, oldId, newId)) {
                playerDataStore.savePlayerData(uuid, playerData);
            }
        }
    }

    private static void replaceSelectionId(EquipmentDataModel data, String oldId, String newId) {
        if (data == null || oldId == null || oldId.isBlank()) {
            return;
        }
        for (Map.Entry<String, String> entry : data.getDefaultIdBySlot().entrySet()) {
            if (oldId.equals(CustomLoadoutService.normalizeEquipmentId(entry.getValue()))) {
                entry.setValue(newId == null ? "" : newId);
            }
        }
    }

    private static Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value) || "是".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value) || "否".equals(value)) {
            return false;
        }
        return null;
    }

    private static String slotLabel(CustomLoadoutSlot slot) {
        return switch (slot) {
            case PRIMARY_WEAPON -> "主武器选择";
            case SECONDARY_WEAPON -> "副武器选择";
            case MELEE -> "近战武器选择";
            case GADGET_1 -> "战术道具一选择";
            case GADGET_2 -> "战术道具二选择";
            case THROWABLE -> "投掷物选择";
        };
    }

    private static String resolveUiSelectedId(
        RoomDefinition room,
        RunningRuntimeState runningState,
        PlayerDataModel playerData,
        UUID playerId,
        RoomTeamId teamId,
        PlayerClassType classType,
        CustomLoadoutSlot slot
    ) {
        if (slot == null) {
            return "";
        }
        if (RoomFixedLoadoutRules.hasRestriction(room, teamId, slot)) {
            String roomSelected = CustomLoadoutService.getSelectedIdForRoomSlot(runningState, playerId, classType, slot);
            if (!roomSelected.isBlank()) {
                return roomSelected;
            }
        }
        return CustomLoadoutService.getSelectedIdForClassSlot(playerData, classType, slot);
    }

    private static void setDefaultEquipmentForSupportedClasses(EquipmentDataModel data, EquipmentDataModel.EquipmentEntry entry) {
        if (data == null || entry == null) {
            return;
        }
        CustomLoadoutSlot slot = CustomLoadoutSlot.fromIndex(entry.getSlot());
        String normalizedId = CustomLoadoutService.normalizeEquipmentId(entry.getId());
        if (slot == null || normalizedId.isBlank()) {
            return;
        }
        data.getDefaultIdBySlot().put(slot.getKey(), normalizedId);
        for (Integer digit : PlayerClassType.parseAvailableDigits(entry.getAvailableClasses())) {
            PlayerClassType classType = PlayerClassType.fromInput(String.valueOf(digit));
            if (classType != null) {
                data.getDefaultIdBySlot().put(CustomLoadoutService.makeClassSlotKey(classType, slot), normalizedId);
            }
        }
    }

    private static List<RoomEditorSnapshotPacket.ConfigEntryPayload> buildRoomConfigEntries(
        ConquestDataManager dataManager,
        RoomDefinition definition,
        List<String> missingItems,
        List<String> missingConfigIds
    ) {
        List<RoomEditorSnapshotPacket.ConfigEntryPayload> entries = new ArrayList<>();
        if (definition == null) {
            return entries;
        }
        List<String> configuredCaptureCodes = resolveEditorCaptureCodes(definition);
        for (RoomConfigScope scope : RoomConfigDescriptorRegistry.guiScopes()) {
            for (RoomConfigDescriptor descriptor : RoomConfigDescriptorRegistry.guiDescriptorsForScope(scope, configuredCaptureCodes)) {
                addRoomConfigEntry(entries, dataManager, definition, descriptor, scope, missingItems, missingConfigIds);
            }
        }
        return entries;
    }

    private static List<String> resolveEditorCaptureCodes(RoomDefinition definition) {
        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<>();
        if (definition != null) {
            for (RoomCapturePointDefinition point : definition.definedObjectivePoints()) {
                if (point != null && point.code() != null && !point.code().isBlank()) {
                    codes.add(point.code().trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return codes.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private static void addRoomConfigEntry(
        List<RoomEditorSnapshotPacket.ConfigEntryPayload> entries,
        ConquestDataManager dataManager,
        RoomDefinition definition,
        RoomConfigDescriptor descriptor,
        RoomConfigScope scope,
        List<String> missingItems,
        List<String> missingConfigIds
    ) {
        if (entries == null || definition == null || descriptor == null) {
            return;
        }
        Object value = RoomConfigValueResolver.resolve(definition, descriptor);
        String keyPath = descriptor.newPath();
        boolean requiredMissing = isMissing(keyPath, descriptor, missingItems, missingConfigIds);
        boolean supportsGroup = descriptor.supportsGroupOperations();
        List<String> groupValues = resolveGroupValues(definition, descriptor, supportsGroup);
        List<RoomEditorSnapshotPacket.GroupChoicePayload> groupChoices = resolveGroupChoices(dataManager, definition, descriptor, groupValues);
        entries.add(new RoomEditorSnapshotPacket.ConfigEntryPayload(
            keyPath,
            buildConfigEditKey(descriptor),
            renderDescriptorDisplayName(descriptor),
            scope == null ? RoomConfigScope.COMMON.displayName() : scope.displayName(),
            renderDescriptorCategory(descriptor),
            stringifyRoomConfigValue(value),
            !requiredMissing && hasConfiguredRoomConfigValue(value),
            requiredMissing,
            true,
            (scope == null ? RoomConfigScope.COMMON : scope).appliesTo(definition.gameplayId()),
            false,
            descriptor.valueType().name(),
            descriptor.suggestionValues(),
            descriptor.supportsQuotedValue(),
            supportsGroup,
            groupValues,
            groupChoices
        ));
    }

    private static List<String> resolveGroupValues(RoomDefinition definition, RoomConfigDescriptor descriptor, boolean supportsGroup) {
        if (!supportsGroup || definition == null || descriptor == null) {
            return List.of();
        }
        try {
            return List.copyOf(RoomDefinitionGroupConfigEditor.list(definition, descriptor.configId()));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static List<RoomEditorSnapshotPacket.GroupChoicePayload> resolveGroupChoices(
        ConquestDataManager dataManager,
        RoomDefinition definition,
        RoomConfigDescriptor descriptor,
        List<String> groupValues
    ) {
        if (definition == null || descriptor == null || !descriptor.supportsGroupOperations()) {
            return List.of();
        }
        String configId = descriptor.configId();
        if (configId == null || configId.isBlank()) {
            return List.of();
        }
        if (dataManager == null) {
            return fallbackGroupChoices(descriptor, groupValues);
        }
        if (RoomFixedLoadoutRules.resolveSlotByConfigId(configId) != null) {
            return buildEquipmentGroupChoices(dataManager.equipmentData().getData(), configId);
        }
        if (RoomGunmasterWeaponPoolRules.isWeaponPoolConfigId(configId)) {
            return RoomGunmasterWeaponPoolRules.buildChoices(dataManager.equipmentData().getData());
        }
        if ("supportcampaherokits".equalsIgnoreCase(configId) || "supportcampbherokits".equalsIgnoreCase(configId)) {
            return buildHeroKitGroupChoices(dataManager.heroKits().get());
        }
        if ("supportcampashellings".equalsIgnoreCase(configId) || "supportcampbshellings".equalsIgnoreCase(configId)) {
            return buildShellingGroupChoices(dataManager.shellings().get());
        }
        return fallbackGroupChoices(descriptor, groupValues);
    }

    private static List<RoomEditorSnapshotPacket.GroupChoicePayload> fallbackGroupChoices(
        RoomConfigDescriptor descriptor,
        List<String> groupValues
    ) {
        java.util.LinkedHashSet<String> orderedValues = new java.util.LinkedHashSet<>();
        if (descriptor != null && descriptor.suggestionValues() != null) {
            orderedValues.addAll(descriptor.suggestionValues());
        }
        if (groupValues != null) {
            orderedValues.addAll(groupValues);
        }
        List<RoomEditorSnapshotPacket.GroupChoicePayload> payloads = new ArrayList<>();
        for (String value : orderedValues) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isBlank()) {
                continue;
            }
            payloads.add(new RoomEditorSnapshotPacket.GroupChoicePayload(normalized, normalized, normalized, ""));
        }
        return payloads;
    }

    private static List<RoomEditorSnapshotPacket.GroupChoicePayload> buildEquipmentGroupChoices(EquipmentDataModel data, String configId) {
        CustomLoadoutSlot slot = RoomFixedLoadoutRules.resolveSlotByConfigId(configId);
        if (data == null || slot == null) {
            return List.of();
        }
        List<RoomEditorSnapshotPacket.GroupChoicePayload> payloads = new ArrayList<>();
        payloads.add(new RoomEditorSnapshotPacket.GroupChoicePayload(
            CustomLoadoutService.EMPTY_EQUIPMENT_ID,
            "空",
            CustomLoadoutService.EMPTY_EQUIPMENT_ID,
            ""
        ));
        for (EquipmentDataModel.EquipmentEntry entry : CustomLoadoutService.listEntriesBySlot(data, slot)) {
            if (entry == null) {
                continue;
            }
            String id = CustomLoadoutService.normalizeEquipmentId(entry.getId());
            if (id.isBlank()) {
                continue;
            }
            payloads.add(new RoomEditorSnapshotPacket.GroupChoicePayload(
                id,
                safeString(entry.getDisplayName(), id),
                id,
                safeString(entry.getItemSnbt(), "")
            ));
        }
        return payloads;
    }

    private static List<RoomEditorSnapshotPacket.GroupChoicePayload> buildHeroKitGroupChoices(org.mods.gd656conquest.server.data.herokit.HeroKitDataModel data) {
        if (data == null) {
            return List.of();
        }
        List<RoomEditorSnapshotPacket.GroupChoicePayload> payloads = new ArrayList<>();
        data.getHeroKitsById().values().stream()
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(entry -> safeString(entry.getId(), ""), String.CASE_INSENSITIVE_ORDER))
            .forEach(entry -> {
                String id = safeString(entry.getId(), "");
                if (id.isBlank()) {
                    return;
                }
                payloads.add(new RoomEditorSnapshotPacket.GroupChoicePayload(
                    id,
                    safeString(entry.getDisplayName(), id),
                    id,
                    ""
                ));
            });
        return payloads;
    }

    private static List<RoomEditorSnapshotPacket.GroupChoicePayload> buildShellingGroupChoices(org.mods.gd656conquest.server.data.shelling.ShellingDataModel data) {
        if (data == null) {
            return List.of();
        }
        List<RoomEditorSnapshotPacket.GroupChoicePayload> payloads = new ArrayList<>();
        data.getShellingsById().values().stream()
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(entry -> safeString(entry.getId(), ""), String.CASE_INSENSITIVE_ORDER))
            .forEach(entry -> {
                String id = safeString(entry.getId(), "");
                if (id.isBlank()) {
                    return;
                }
                payloads.add(new RoomEditorSnapshotPacket.GroupChoicePayload(
                    id,
                    safeString(entry.getDisplayName(), id),
                    id,
                    safeString(entry.getGunItemSnbt(), "")
                ));
            });
        return payloads;
    }

    private static String safeString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void sendSystem(ServerPlayer player, Component content) {
        ServerMessageSender.system(player, "配装", content);
    }

    private static void sendInfo(ServerPlayer player, String message) {
        ServerMessageSender.system(player, "配装", message, ChatFormatting.AQUA);
    }

    private static void sendError(ServerPlayer player, String message) {
        ServerMessageSender.system(player, "配装", message, ChatFormatting.RED);
    }

    private static String renderDescriptorCategory(RoomConfigDescriptor descriptor) {
        return switch (descriptor.category()) {
            case ROOM -> "Room";
            case MATCHING -> "Matching";
            case TRANSPORT -> "Transport";
            case PLAYER_STATE -> "PlayerState";
            case RULES -> "Rules";
            case RESULT -> "Result";
            case EQUIPMENT -> "Equipment";
            case CAPTURE -> "Capture";
        };
    }

    private static String renderDescriptorDisplayName(RoomConfigDescriptor descriptor) {
        if (descriptor == null) {
            return "";
        }
        return descriptor.displayName();
    }

    private static String buildConfigEditKey(RoomConfigDescriptor descriptor) {
        return descriptor == null ? "" : descriptor.configId();
    }

    private static boolean isMissing(
        String keyPath,
        RoomConfigDescriptor descriptor,
        List<String> missingItems,
        List<String> missingConfigIds
    ) {
        if (descriptor != null && missingConfigIds != null) {
            for (String configId : missingConfigIds) {
                if (configId != null && configId.equalsIgnoreCase(descriptor.configId())) {
                    return true;
                }
            }
        }
        if (missingItems != null && keyPath != null) {
            String lowerKey = keyPath.toLowerCase(Locale.ROOT);
            for (String item : missingItems) {
                if (item == null) {
                    continue;
                }
                String lowerItem = item.toLowerCase(Locale.ROOT);
                if (lowerItem.equals(lowerKey) || lowerItem.startsWith(lowerKey + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String stringifyRoomConfigValue(Object value) {
        if (!hasConfiguredRoomConfigValue(value)) {
            return "";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence || value.getClass().isEnum()) {
            return String.valueOf(value);
        }
        return new com.google.gson.Gson().toJson(value);
    }

    private static boolean hasConfiguredRoomConfigValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number number) {
            return Math.abs(number.doubleValue()) > 1.0e-9D;
        }
        if (value instanceof CharSequence text) {
            return !text.toString().isBlank();
        }
        if (value instanceof java.util.Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) > 0;
        }
        String json = new com.google.gson.Gson().toJson(value);
        String normalized = json == null ? "" : json.trim();
        return !normalized.isBlank() && !"{}".equals(normalized) && !"[]".equals(normalized) && !"0".equals(normalized) && !"0.0".equals(normalized);
    }

    private static RoomLifecycleState parseRoomState(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        for (RoomLifecycleState state : RoomLifecycleState.values()) {
            if (state.name().equalsIgnoreCase(rawValue.trim()) || resolveRoomStateDisplayName(state).equalsIgnoreCase(rawValue.trim())) {
                return state;
            }
        }
        return null;
    }

    private static String resolveRoomStateDisplayName(RoomLifecycleState state) {
        if (state == null) {
            return "已禁用";
        }
        return switch (state) {
            case DISABLED -> "已禁用";
            case MAINTENANCE -> "维护中";
            case IDLE -> "空闲";
            case MATCHING -> "匹配中";
            case RUNNING -> "进行中";
            case ENDING -> "结算中";
            case SAVING -> "保存中";
            case CLEANING -> "清理中";
            case LOCKED -> "已锁定";
        };
    }

    private static RoomDefinition applyGroupDescriptorUpdate(
        RoomDefinition definition,
        RoomConfigDescriptor descriptor,
        String rawValue,
        ServerPlayer editor
    ) {
        if (definition == null || descriptor == null) {
            throw new IllegalArgumentException("组配置参数无效");
        }
        if (descriptor.isSpecialCase()) {
            return RoomDefinitionScalarConfigEditor.apply(definition, descriptor, rawValue, editor);
        }
        String configId = descriptor.configId();
        String captureInnerCode = resolveCaptureInnerSpawnGroupCode(configId);
        if (captureInnerCode != null) {
            return withCapturePoint(definition, captureInnerCode, point -> point.withInnerSpawnGroup(parseSpawnGroupConfig(rawValue)));
        }
        String captureOuterCode = resolveCaptureOuterSpawnGroupCode(configId);
        if (captureOuterCode != null) {
            return withCapturePoint(definition, captureOuterCode, point -> point.withOuterSpawnGroup(parseSpawnGroupConfig(rawValue)));
        }
        RoomCaptureConfig capture = definition.conquestConfig().captureConfig();
        return switch (configId) {
            case "sharedBoundary" -> replaceBoundary(definition, parsePositionList(rawValue), definition.conquestConfig().campABoundary(), definition.conquestConfig().campBBoundary());
            case "campABoundary" -> replaceBoundary(definition, definition.conquestConfig().sharedBoundary(), parsePositionList(rawValue), definition.conquestConfig().campBBoundary());
            case "campBBoundary" -> replaceBoundary(definition, definition.conquestConfig().sharedBoundary(), definition.conquestConfig().campABoundary(), parsePositionList(rawValue));
            case "campASpawnGroup" -> replaceTeamSpawnGroup(definition, RoomTeamId.CAMP_A, parseSpawnGroupConfig(rawValue));
            case "campBSpawnGroup" -> replaceTeamSpawnGroup(definition, RoomTeamId.CAMP_B, parseSpawnGroupConfig(rawValue));
            case "capturePoints" -> replaceCapture(definition, new RoomCaptureConfig(
                parseCapturePoints(rawValue),
                capture.mapId(),
                capture.campADisplayName(),
                capture.campBDisplayName(),
                capture.drawDurationMinutes(),
                capture.fullCaptureEndSeconds(),
                capture.fullCaptureResponseMode(),
                capture.antiBaseNukeTriggerSeconds(),
                capture.antiBaseNukeRespawnDelaySeconds(),
                capture.antiBaseNukeTriggerCooldownSeconds(),
                capture.targetScore(),
                capture.captureScoreIntervalSeconds(),
                capture.scoreMethod(),
                capture.campAInitialTroops(),
                capture.campBInitialTroops(),
                capture.initialCaptureOwnership()
            ));
            default -> throw new IllegalArgumentException("暂不支持的组配置: " + configId);
        };
    }

    private static List<RoomPosition> parsePositionList(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(rawValue);
        if (!root.isJsonArray()) {
            throw new IllegalArgumentException("组配置必须是 JSON 数组");
        }
        List<RoomPosition> positions = new ArrayList<>();
        for (com.google.gson.JsonElement element : root.getAsJsonArray()) {
            positions.add(parseRoomPosition(element));
        }
        return List.copyOf(positions);
    }

    private static RoomSpawnGroupConfig parseSpawnGroupConfig(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return RoomSpawnGroupConfig.defaults();
        }
        com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(rawValue);
        if (root.isJsonArray()) {
            return new RoomSpawnGroupConfig(true, parsePositionList(rawValue));
        }
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("出生组必须是 JSON 对象");
        }
        com.google.gson.JsonObject object = root.getAsJsonObject();
        boolean enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
        List<RoomPosition> spawns = new ArrayList<>();
        com.google.gson.JsonElement spawnElement = object.get("spawns");
        if (spawnElement != null && spawnElement.isJsonArray()) {
            for (com.google.gson.JsonElement element : spawnElement.getAsJsonArray()) {
                spawns.add(parseRoomPosition(element));
            }
        }
        return new RoomSpawnGroupConfig(enabled, spawns);
    }

    private static Map<String, RoomCapturePointDefinition> parseCapturePoints(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Map.of();
        }
        com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(rawValue);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("据点组必须是 JSON 对象");
        }
        Map<String, RoomCapturePointDefinition> points = new HashMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject().entrySet()) {
            String code = entry.getKey() == null ? "" : entry.getKey().trim().toUpperCase(Locale.ROOT);
            if (code.isBlank()) {
                continue;
            }
            points.put(code, parseCapturePointDefinition(code, entry.getValue()));
        }
        return Map.copyOf(points);
    }

    private static RoomCapturePointDefinition parseCapturePointDefinition(String fallbackCode, com.google.gson.JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new RoomCapturePointDefinition(fallbackCode, fallbackCode, null, RoomSpawnGroupConfig.defaults(), RoomSpawnGroupConfig.defaults());
        }
        com.google.gson.JsonObject object = element.getAsJsonObject();
        String code = readString(object, "code", fallbackCode).toUpperCase(Locale.ROOT);
        String displayName = readString(object, "displayName", code);
        RoomPosition centerPosition = object.has("centerPosition") ? parseNullableRoomPosition(object.get("centerPosition")) : null;
        RoomSpawnGroupConfig innerSpawnGroup = object.has("innerSpawnGroup")
            ? parseSpawnGroupConfig(object.get("innerSpawnGroup").toString())
            : RoomSpawnGroupConfig.defaults();
        RoomSpawnGroupConfig outerSpawnGroup = object.has("outerSpawnGroup")
            ? parseSpawnGroupConfig(object.get("outerSpawnGroup").toString())
            : RoomSpawnGroupConfig.defaults();
        java.util.Map<RoomTeamId, RoomSpawnGroupConfig> teamOuterSpawnGroups = parseTeamOuterSpawnGroups(object.get("teamOuterSpawnGroups"));
        return new RoomCapturePointDefinition(code, displayName, centerPosition, innerSpawnGroup, outerSpawnGroup, teamOuterSpawnGroups);
    }

    private static RoomPosition parseRoomPosition(com.google.gson.JsonElement element) {
        RoomPosition position = parseNullableRoomPosition(element);
        if (position == null) {
            throw new IllegalArgumentException("坐标项不能为空");
        }
        return position;
    }

    private static RoomPosition parseNullableRoomPosition(com.google.gson.JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            com.google.gson.JsonObject object = element.getAsJsonObject();
            return new RoomPosition(
                readInt(object, "x", 0),
                readDouble(object, "y", 0.0D),
                readInt(object, "z", 0)
            );
        }
        if (element.isJsonArray() && element.getAsJsonArray().size() >= 3) {
            com.google.gson.JsonArray array = element.getAsJsonArray();
            return new RoomPosition(array.get(0).getAsInt(), array.get(1).getAsDouble(), array.get(2).getAsInt());
        }
        if (element.isJsonPrimitive()) {
            String text = element.getAsString();
            String[] split = text.split(",");
            if (split.length == 3) {
                return new RoomPosition(
                    Integer.parseInt(split[0].trim()),
                    Double.parseDouble(split[1].trim()),
                    Integer.parseInt(split[2].trim())
                );
            }
        }
        throw new IllegalArgumentException("坐标 JSON 格式无效");
    }

    private static java.util.Map<RoomTeamId, RoomSpawnGroupConfig> parseTeamOuterSpawnGroups(com.google.gson.JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return java.util.Map.of();
        }
        java.util.Map<RoomTeamId, RoomSpawnGroupConfig> values = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : element.getAsJsonObject().entrySet()) {
            RoomTeamId teamId;
            try {
                teamId = RoomTeamId.valueOf(entry.getKey().trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                continue;
            }
            values.put(teamId, parseSpawnGroupConfig(entry.getValue().toString()));
        }
        return java.util.Map.copyOf(values);
    }

    private static RoomCapturePointDefinition resolveCapturePoint(RoomDefinition definition, String configId) {
        if (definition == null || configId == null || configId.isBlank()) {
            return null;
        }
        String code = resolveCaptureDisplayNameCode(configId);
        if (code == null) {
            code = resolveCaptureCenterPositionCode(configId);
        }
        if (code == null) {
            code = resolveCaptureInnerSpawnGroupCode(configId);
        }
        if (code == null) {
            code = resolveCaptureOuterSpawnGroupCode(configId);
        }
        if (code == null) {
            return null;
        }
        String normalizedCode = code.toUpperCase(Locale.ROOT);
        return definition.conquestConfig().captureConfig().points().getOrDefault(
            normalizedCode,
            new RoomCapturePointDefinition(normalizedCode, normalizedCode, null, RoomSpawnGroupConfig.defaults(), RoomSpawnGroupConfig.defaults())
        );
    }

    private static String resolveCaptureDisplayNameCode(String configId) {
        return resolveDynamicCaptureCode(configId, "capturedisplayname_");
    }

    private static String resolveCaptureCenterPositionCode(String configId) {
        return resolveDynamicCaptureCode(configId, "capturecenterposition_");
    }

    private static String resolveCaptureInnerSpawnGroupCode(String configId) {
        return resolveDynamicCaptureCode(configId, "captureinnerspawngroup_");
    }

    private static String resolveCaptureOuterSpawnGroupCode(String configId) {
        return resolveDynamicCaptureCode(configId, "captureouterspawngroup_");
    }

    private static String resolveDynamicCaptureCode(String configId, String prefix) {
        if (configId == null || prefix == null) {
            return null;
        }
        String normalized = configId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(prefix)) {
            return null;
        }
        return configId.trim().substring(prefix.length()).toUpperCase(Locale.ROOT);
    }

    private static Map<RoomTeamId, RoomSpawnGroupConfig> replaceSpawnGroupMap(
        Map<RoomTeamId, RoomSpawnGroupConfig> source,
        RoomTeamId teamId,
        RoomSpawnGroupConfig updated
    ) {
        Map<RoomTeamId, RoomSpawnGroupConfig> result = new HashMap<>();
        if (source != null) {
            result.putAll(source);
        }
        result.put(teamId, updated == null ? RoomSpawnGroupConfig.defaults() : updated);
        return result;
    }

    private static String readString(com.google.gson.JsonObject object, String key, String fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        String value = object.get(key).getAsString();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int readInt(com.google.gson.JsonObject object, String key, int fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsInt();
    }

    private static double readDouble(com.google.gson.JsonObject object, String key, double fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsDouble();
    }

    private static RoomDefinition replaceBoundary(
        RoomDefinition definition,
        List<RoomPosition> sharedBoundary,
        List<RoomPosition> campABoundary,
        List<RoomPosition> campBBoundary
    ) {
        return new RoomDefinition(
            definition.roomId(),
            definition.displayName(),
            definition.gameplayId(),
            definition.lifecycleState(),
            definition.commonConfig(),
            new org.mods.gd656conquest.server.room.model.RoomConquestConfig(
                definition.conquestConfig().teamSpawnGroups(),
                definition.conquestConfig().teamDeployIcons(),
                definition.conquestConfig().deployViewPosition(),
                sharedBoundary,
                campABoundary,
                campBBoundary,
                definition.conquestConfig().captureConfig(),
                definition.conquestConfig().ammoBoxPositions(),
                definition.conquestConfig().medicalBoxPositions()
            )
        );
    }

    private static RoomDefinition replaceTeamSpawnGroup(RoomDefinition definition, RoomTeamId teamId, RoomSpawnGroupConfig spawnGroupConfig) {
        return new RoomDefinition(
            definition.roomId(),
            definition.displayName(),
            definition.gameplayId(),
            definition.lifecycleState(),
            definition.commonConfig(),
            new org.mods.gd656conquest.server.room.model.RoomConquestConfig(
                replaceSpawnGroupMap(definition.conquestConfig().teamSpawnGroups(), teamId, spawnGroupConfig),
                definition.conquestConfig().teamDeployIcons(),
                definition.conquestConfig().deployViewPosition(),
                definition.conquestConfig().sharedBoundary(),
                definition.conquestConfig().campABoundary(),
                definition.conquestConfig().campBBoundary(),
                definition.conquestConfig().captureConfig(),
                definition.conquestConfig().ammoBoxPositions(),
                definition.conquestConfig().medicalBoxPositions()
            )
        );
    }

    private static RoomDefinition replaceCapture(RoomDefinition definition, RoomCaptureConfig captureConfig) {
        return new RoomDefinition(
            definition.roomId(),
            definition.displayName(),
            definition.gameplayId(),
            definition.lifecycleState(),
            definition.commonConfig(),
            definition.conquestConfig().withCaptureConfig(captureConfig)
        );
    }

    private static RoomDefinition withCapturePoint(
        RoomDefinition definition,
        String captureCode,
        java.util.function.UnaryOperator<RoomCapturePointDefinition> updater
    ) {
        RoomCaptureConfig capture = definition.conquestConfig().captureConfig();
        Map<String, RoomCapturePointDefinition> points = new HashMap<>(capture.points());
        String normalizedCode = captureCode == null ? "" : captureCode.trim().toUpperCase(Locale.ROOT);
        RoomCapturePointDefinition current = points.getOrDefault(
            normalizedCode,
            new RoomCapturePointDefinition(normalizedCode, normalizedCode, null, RoomSpawnGroupConfig.defaults(), RoomSpawnGroupConfig.defaults())
        );
        points.put(normalizedCode, updater.apply(current));
        return replaceCapture(definition, new RoomCaptureConfig(
            points,
            capture.mapId(),
            capture.campADisplayName(),
            capture.campBDisplayName(),
            capture.drawDurationMinutes(),
            capture.fullCaptureEndSeconds(),
            capture.fullCaptureResponseMode(),
            capture.antiBaseNukeTriggerSeconds(),
            capture.antiBaseNukeRespawnDelaySeconds(),
            capture.antiBaseNukeTriggerCooldownSeconds(),
            capture.targetScore(),
            capture.captureScoreIntervalSeconds(),
            capture.scoreMethod(),
            capture.campAInitialTroops(),
            capture.campBInitialTroops(),
            capture.initialCaptureOwnership()
        ));
    }

    private static boolean createEquipmentEntry(ServerPlayer player, EquipmentDataModel data, String equipmentId) {
        String normalizedId = CustomLoadoutService.normalizeEquipmentId(equipmentId);
        if (!CustomLoadoutService.isValidEquipmentId(normalizedId)) {
            sendError(player, "新装备 ID 格式非法");
            return false;
        }
        if (data.getEquipmentById().containsKey(normalizedId)) {
            sendError(player, "装备已存在: " + normalizedId);
            return false;
        }
        EquipmentDataModel.EquipmentEntry entry = new EquipmentDataModel.EquipmentEntry();
        entry.setId(normalizedId);
        entry.setSlot(CustomLoadoutSlot.PRIMARY_WEAPON.getIndex());
        entry.setSlotName(CustomLoadoutSlot.PRIMARY_WEAPON.getKey());
        entry.setDisplayName(normalizedId);
        entry.setExtraData(0);
        entry.setUnlockLevel(0);
        entry.setAvailableClasses(1234);
        entry.setItemSnbt("");
        entry.setReplenishSeconds(0);
        entry.setAmmoBoxRefillEnabled(Boolean.TRUE);
        data.getEquipmentById().put(normalizedId, entry);
        return true;
    }
}
