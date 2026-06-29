package org.mods.gd656conquest.server.loadout;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareCompatBridge;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.attachment.WeaponAttachmentDataModel;
import org.mods.gd656conquest.server.data.attachment.WeaponAttachmentDataStore;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataStore;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataStore;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.loadout.application.WeaponAttachmentService;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class LoadoutCommands {
    private static final DynamicCommandExceptionType INVALID_SLOT = new DynamicCommandExceptionType(
        value -> Component.literal("非法槽位: " + value)
    );
    private static final DynamicCommandExceptionType INVALID_CLASS = new DynamicCommandExceptionType(
        value -> Component.literal("非法兵种: " + value)
    );
    private static final DynamicCommandExceptionType INVALID_ID = new DynamicCommandExceptionType(
        value -> Component.literal("非法装备 ID: " + value)
    );
    private static final DynamicCommandExceptionType INVALID_CAMP = new DynamicCommandExceptionType(
        value -> Component.literal("非法阵营: " + value + "，仅支持 campA/campB")
    );
    private static final SimpleCommandExceptionType EMPTY_HAND = new SimpleCommandExceptionType(Component.literal("请先手持一个物品再执行 additem"));
    private static final SimpleCommandExceptionType INVALID_LEVEL = new SimpleCommandExceptionType(Component.literal("level 仅支持 0~100"));
    private static final SimpleCommandExceptionType INVALID_EXTRA_SPECIAL_GUN = new SimpleCommandExceptionType(Component.literal("当前手持 TACZ 或 SBW 枪械时，extraData 必须为正整数"));
    private static final SimpleCommandExceptionType INVALID_AVAILABLE_CLASSES = new SimpleCommandExceptionType(Component.literal("availableClasses 至少包含 1~4 中的一个数字"));

    private LoadoutCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("loadout")
            .then(Commands.literal("open").executes(LoadoutCommands::executeOpen))
            .then(Commands.literal("leave").executes(LoadoutCommands::executeLeaveSession))
            .then(Commands.literal("room")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("open").executes(LoadoutCommands::executeOpenRoomEditor)))
            .then(buildEquipmentTree())
            .then(buildAttachmentTree())
            .then(buildClassBranch())
            .then(buildSelectBranch())
            .then(buildDefaultBranch("default"))
            .then(buildArmorBranch())
            .then(buildFixNameBranch());
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildCompatibilityTree() {
        return Commands.literal("customloadout")
            .then(Commands.literal("open").executes(LoadoutCommands::executeOpen))
            .then(Commands.literal("leave").executes(LoadoutCommands::executeLeaveSession))
            .then(buildEquipmentTree())
            .then(buildAttachmentTree())
            .then(Commands.literal("edit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestAllIds)
                    .executes(LoadoutCommands::executeCompatibilityEdit)))
            .then(buildAddItemBranch())
            .then(buildFixNameBranch())
            .then(buildListBranch())
            .then(buildDelBranch())
            .then(buildClassBranch())
            .then(buildSelectBranch())
            .then(buildDefaultBranch("default"))
            .then(buildArmorBranch());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildEquipmentTree() {
        return Commands.literal("equipment")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("info")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestAllIds)
                    .executes(LoadoutCommands::executeEquipmentInfo)))
            .then(Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.string())
                    .executes(LoadoutCommands::executeEquipmentCreate)))
            .then(Commands.literal("edit")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestAllIds)
                    .then(Commands.argument("field", StringArgumentType.word())
                        .suggests(LoadoutCommands::suggestEquipmentFields)
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .suggests(LoadoutCommands::suggestEquipmentEditValues)
                            .executes(LoadoutCommands::executeEquipmentEdit)))))
            .then(Commands.literal("set-default")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestAllIds)
                    .executes(LoadoutCommands::executeEquipmentSetDefault)))
            .then(Commands.literal("setdefault")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestAllIds)
                    .executes(LoadoutCommands::executeEquipmentSetDefault)))
            .then(Commands.literal("additem")
                .then(buildAddItemArguments()))
            .then(Commands.literal("list")
                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                    .executes(LoadoutCommands::executeList)))
            .then(Commands.literal("del")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestAllIds)
                    .executes(LoadoutCommands::executeDel)))
            .then(Commands.literal("delete")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestAllIds)
                    .executes(LoadoutCommands::executeDel)))
            .then(Commands.literal("fixname").executes(LoadoutCommands::executeFixName));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildAttachmentTree() {
        return Commands.literal("attachment")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("info")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestAllAttachmentIds)
                    .executes(LoadoutCommands::executeAttachmentInfo)))
            .then(Commands.literal("additem")
                .then(Commands.argument("level", IntegerArgumentType.integer(0, 100))
                    .executes(LoadoutCommands::executeAttachmentAddItem)
                    .then(Commands.argument("id", StringArgumentType.string())
                        .executes(LoadoutCommands::executeAttachmentAddItem))))
            .then(Commands.literal("addall")
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests(LoadoutCommands::suggestAttachmentTypes)
                    .then(Commands.argument("level", IntegerArgumentType.integer(0, 100))
                        .executes(LoadoutCommands::executeAttachmentAddAllByType))))
            .then(Commands.literal("edit")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestAllAttachmentIds)
                    .then(Commands.argument("field", StringArgumentType.word())
                        .suggests(LoadoutCommands::suggestAttachmentFields)
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .suggests(LoadoutCommands::suggestAttachmentEditValues)
                            .executes(LoadoutCommands::executeAttachmentEdit)))))
            .then(Commands.literal("list")
                .executes(LoadoutCommands::executeAttachmentList)
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests(LoadoutCommands::suggestAttachmentTypes)
                    .executes(LoadoutCommands::executeAttachmentList)))
            .then(Commands.literal("del")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestAllAttachmentIds)
                    .executes(LoadoutCommands::executeAttachmentDel)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildArmorBranch() {
        return Commands.literal("armor")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("set")
                .then(Commands.argument("type", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestClassTypes)
                    .then(Commands.argument("camp", StringArgumentType.word())
                        .suggests(LoadoutCommands::suggestCampTypes)
                        .executes(LoadoutCommands::executeArmorSet))))
            .then(Commands.literal("list").executes(LoadoutCommands::executeArmorList));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildAddItemBranch() {
        return Commands.literal("additem")
            .requires(source -> source.hasPermission(2))
            .then(buildAddItemArguments());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildAddItemArguments() {
        return Commands.argument("slot", IntegerArgumentType.integer(1, 6))
            .suggests(LoadoutCommands::suggestAddItemSlotValues)
            .then(Commands.argument("id", StringArgumentType.string())
                .then(Commands.argument("extradata", IntegerArgumentType.integer(0))
                    .then(Commands.argument("level", IntegerArgumentType.integer(0, 100))
                        .then(Commands.argument("availableclasses", IntegerArgumentType.integer(1))
                            .executes(LoadoutCommands::executeAddItem)
                            .then(Commands.argument("replenishseconds", IntegerArgumentType.integer(0))
                                .executes(LoadoutCommands::executeAddItem)
                                .then(Commands.argument("ammoboxrefillenabled", BoolArgumentType.bool())
                                    .executes(LoadoutCommands::executeAddItem)))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildFixNameBranch() {
        return Commands.literal("fixname")
            .requires(source -> source.hasPermission(2))
            .executes(LoadoutCommands::executeFixName);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildListBranch() {
        return Commands.literal("list")
            .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                .executes(LoadoutCommands::executeList));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildDelBranch() {
        return Commands.literal("del")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(LoadoutCommands::suggestAllIds)
                .executes(LoadoutCommands::executeDel));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildClassBranch() {
        return Commands.literal("class")
            .then(Commands.argument("type", StringArgumentType.string())
                .suggests(LoadoutCommands::suggestClassTypes)
                .executes(LoadoutCommands::executeClassSelect))
            .then(Commands.literal("select")
                .then(Commands.argument("type", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestClassTypes)
                    .executes(LoadoutCommands::executeClassSelect)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildSelectBranch() {
        return Commands.literal("select")
            .then(Commands.argument("type", StringArgumentType.string())
                .suggests(LoadoutCommands::suggestClassTypes)
                .then(Commands.argument("slot", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestSlotNames)
                    .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(LoadoutCommands::suggestSelectableIdsByClassAndSlot)
                        .executes(LoadoutCommands::executeSelectSet))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildDefaultBranch(String literal) {
        return Commands.literal(literal)
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("type", StringArgumentType.string())
                .suggests(LoadoutCommands::suggestClassTypes)
                .then(Commands.argument("slot", StringArgumentType.string())
                    .suggests(LoadoutCommands::suggestSlotNames)
                    .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(LoadoutCommands::suggestDefaultIdsByClassAndSlot)
                        .executes(LoadoutCommands::executeDefaultSet))));
    }

    private static int executeOpen(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LoadoutUiService.sendSnapshot(player, true, true);
        return replyInfo(context.getSource(), "已打开配装界面");
    }

    private static int executeLeaveSession(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LoadoutUiService.handleAction(player, "leave_session", "", -1, "");
        return replyInfo(context.getSource(), "已请求离开当前配装关联场景");
    }

    private static int executeOpenRoomEditor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LoadoutUiService.sendAdminRoomSnapshot(player, true);
        return replyInfo(context.getSource(), "已打开房间配置界面");
    }

    private static int executeAddItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer operator = context.getSource().getPlayerOrException();
        ItemStack held = operator.getMainHandItem();
        if (held.isEmpty()) {
            throw EMPTY_HAND.create();
        }
        CustomLoadoutSlot slot = requireSlot(IntegerArgumentType.getInteger(context, "slot"));
        String normalizedId = parseEquipmentIdInput(StringArgumentType.getString(context, "id"));
        if (!CustomLoadoutService.isValidEquipmentId(normalizedId)) {
            throw INVALID_ID.create(normalizedId);
        }
        int extraData = IntegerArgumentType.getInteger(context, "extradata");
        int level = IntegerArgumentType.getInteger(context, "level");
        int availableClasses = IntegerArgumentType.getInteger(context, "availableclasses");
        int replenishSeconds = 0;
        boolean ammoBoxRefillEnabled = true;
        try {
            replenishSeconds = IntegerArgumentType.getInteger(context, "replenishseconds");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            ammoBoxRefillEnabled = BoolArgumentType.getBool(context, "ammoboxrefillenabled");
        } catch (IllegalArgumentException ignored) {
        }
        if (level < 0 || level > 100) {
            throw INVALID_LEVEL.create();
        }
        if (PlayerClassType.parseAvailableDigits(availableClasses).isEmpty()) {
            throw INVALID_AVAILABLE_CLASSES.create();
        }
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(held.getItem());
        if ((TaczDisplayNameResolver.isTaczGun(held) || SuperbWarfareCompatBridge.isGunItem(held)) && extraData <= 0) {
            throw INVALID_EXTRA_SPECIAL_GUN.create();
        }

        CompoundTag serialized = new CompoundTag();
        held.save(serialized);
        EquipmentDataStore handler = getEquipmentDataStore(context);
        EquipmentDataModel data = handler.getData();
        boolean replaced = data.getEquipmentById().containsKey(normalizedId);

        EquipmentDataModel.EquipmentEntry entry = new EquipmentDataModel.EquipmentEntry();
        entry.setId(normalizedId);
        entry.setSlot(slot.getIndex());
        entry.setSlotName(slot.getKey());
        entry.setDisplayName(resolveRealItemName(operator, held, itemKey));
        entry.setExtraData(extraData);
        entry.setUnlockLevel(level);
        entry.setAvailableClasses(availableClasses);
        entry.setItemSnbt(serialized.toString());
        entry.setReplenishSeconds(Math.max(0, replenishSeconds));
        entry.setAmmoBoxRefillEnabled(ammoBoxRefillEnabled);
        data.getEquipmentById().put(normalizedId, entry);
        handler.saveData(data);
        LoadoutUiService.sendSnapshot(operator, false, true);
        return replyInfo(
            context.getSource(),
            String.format(
                Locale.ROOT,
                "%s装备: %s | slot=%s | level=%d | classes=%d | replenish=%ds | ammoBoxRefill=%s",
                replaced ? "已覆盖" : "已新增",
                normalizedId,
                slot.getKey(),
                level,
                availableClasses,
                Math.max(0, replenishSeconds),
                ammoBoxRefillEnabled
            )
        );
    }

    private static int executeFixName(CommandContext<CommandSourceStack> context) {
        EquipmentDataStore handler = getEquipmentDataStore(context);
        EquipmentDataModel data = handler.getData();
        int scanned = 0;
        int updated = 0;
        int failed = 0;
        for (EquipmentDataModel.EquipmentEntry entry : data.getEquipmentById().values()) {
            if (entry == null || entry.getItemSnbt() == null || entry.getItemSnbt().isBlank()) {
                continue;
            }
            scanned++;
            ItemStack stack = parseItemStack(entry.getItemSnbt());
            if (stack.isEmpty()) {
                failed++;
                continue;
            }
            String displayName = TaczDisplayNameResolver.isTaczGun(stack)
                ? TaczDisplayNameResolver.resolve(context.getSource().getServer(), stack)
                : resolveRealItemName(stack, BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (displayName.isBlank()) {
                failed++;
                continue;
            }
            if (!displayName.equals(entry.getDisplayName())) {
                entry.setDisplayName(displayName);
                updated++;
            }
        }
        if (updated > 0) {
            handler.saveData(data);
        }
        return replyInfo(
            context.getSource(),
            String.format(Locale.ROOT, "fixname 完成: 扫描=%d, 修复=%d, 失败=%d", scanned, updated, failed)
        );
    }

    private static int executeEquipmentCreate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = parseEquipmentIdInput(StringArgumentType.getString(context, "id"));
        LoadoutUiService.handleAdminEquipmentEdit(player, id, "create", "");
        return replyInfo(context.getSource(), "已请求创建装备: " + id);
    }

    private static int executeEquipmentInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = parseEquipmentIdInput(StringArgumentType.getString(context, "id"));
        EquipmentDataModel data = getEquipmentDataStore(context).getData();
        EquipmentDataModel.EquipmentEntry entry = data.getEquipmentById().get(id);
        if (entry == null) {
            throw new SimpleCommandExceptionType(Component.literal("未找到装备ID: " + id)).create();
        }
        sendListLine(context.getSource(), Component.literal("========== 装备详情 ==========").withStyle(ChatFormatting.GOLD));
        sendListLine(context.getSource(), Component.literal("id=").append(Component.literal(entry.getId()).withStyle(ChatFormatting.AQUA)));
        sendListLine(context.getSource(), Component.literal("displayName=").append(Component.literal(normalizeFreeText(entry.getDisplayName())).withStyle(ChatFormatting.WHITE)));
        sendListLine(context.getSource(), Component.literal("slot=").append(Component.literal(entry.getSlotName() + " (" + entry.getSlot() + ")").withStyle(ChatFormatting.YELLOW)));
        sendListLine(context.getSource(), Component.literal("unlockLevel=").append(Component.literal(String.valueOf(entry.getUnlockLevel())).withStyle(ChatFormatting.YELLOW)));
        sendListLine(context.getSource(), Component.literal("availableClasses=").append(Component.literal(String.valueOf(entry.getAvailableClasses())).withStyle(ChatFormatting.GREEN)));
        sendListLine(context.getSource(), Component.literal("extraData=").append(Component.literal(String.valueOf(entry.getExtraData())).withStyle(ChatFormatting.GRAY)));
        sendListLine(context.getSource(), Component.literal("replenishSeconds=").append(Component.literal(String.valueOf(entry.getReplenishSeconds())).withStyle(ChatFormatting.GRAY)));
        sendListLine(context.getSource(), Component.literal("ammoBoxRefillEnabled=").append(Component.literal(String.valueOf(entry.isAmmoBoxRefillEnabled())).withStyle(ChatFormatting.GRAY)));
        sendListLine(context.getSource(), Component.literal("itemSnbt=").append(Component.literal(normalizeSnbtPreview(entry.getItemSnbt())).withStyle(ChatFormatting.DARK_GRAY)));
        List<String> defaults = collectDefaultMappings(data, id);
        sendListLine(
            context.getSource(),
            Component.literal("defaults=").append(Component.literal(defaults.isEmpty() ? "-" : String.join(", ", defaults)).withStyle(defaults.isEmpty() ? ChatFormatting.DARK_GRAY : ChatFormatting.GOLD))
        );
        return 1;
    }

    private static int executeEquipmentEdit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = parseEquipmentIdInput(StringArgumentType.getString(context, "id"));
        String field = StringArgumentType.getString(context, "field");
        String value = StringArgumentType.getString(context, "value");
        if (isItemSnbtField(field)) {
            value = resolveItemSnbtEditValue(player, value);
        }
        LoadoutUiService.handleAdminEquipmentEdit(player, id, field, value);
        return replyInfo(context.getSource(), "已请求更新装备字段: " + id + "." + field);
    }

    private static int executeCompatibilityEdit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            throw EMPTY_HAND.create();
        }
        String id = parseEquipmentIdInput(StringArgumentType.getString(context, "id"));
        if (!CustomLoadoutService.isValidEquipmentId(id)) {
            throw INVALID_ID.create(id);
        }

        EquipmentDataStore handler = getEquipmentDataStore(context);
        EquipmentDataModel data = handler.getData();
        EquipmentDataModel.EquipmentEntry entry = data.getEquipmentById().get(id);
        if (entry == null) {
            throw new SimpleCommandExceptionType(Component.literal("未找到装备ID: " + id)).create();
        }

        String snbt = serializeStackToSnbt(held);
        if (snbt.isBlank()) {
            throw EMPTY_HAND.create();
        }
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(held.getItem());
        entry.setItemSnbt(snbt);
        entry.setDisplayName(resolveRealItemName(player, held, itemKey));
        handler.saveData(data);
        LoadoutUiService.sendSnapshot(player, false, true);
        return replyInfo(
            context.getSource(),
            String.format(
                Locale.ROOT,
                "已用当前手持物品覆盖 %s 的 itemSnbt 与显示名: %s",
                id,
                entry.getDisplayName()
            )
        );
    }

    private static int executeEquipmentSetDefault(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = parseEquipmentIdInput(StringArgumentType.getString(context, "id"));
        LoadoutUiService.handleAdminEquipmentEdit(player, id, "set_default", "1");
        return replyInfo(context.getSource(), "已请求将装备设为默认: " + id);
    }

    private static int executeAttachmentAddItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            throw EMPTY_HAND.create();
        }
        TaczAttachmentCompatService compatService = TaczAttachmentCompatService.getInstance();
        if (!compatService.isOfficialAttachment(held)) {
            throw new SimpleCommandExceptionType(Component.literal("请手持一个正式 TACZ 配件再执行 attachment additem")).create();
        }
        ResourceLocation attachmentDefinitionId = compatService.resolveAttachmentId(held);
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(held.getItem());
        String rawId = "";
        try {
            rawId = StringArgumentType.getString(context, "id");
        } catch (IllegalArgumentException ignored) {
        }
        String id = rawId.isBlank()
            ? buildNextAvailableAttachmentId(
                getAttachmentDataStore(context).getData(),
                buildAttachmentIdFromResourceLocation(attachmentDefinitionId != null ? attachmentDefinitionId : itemKey)
            )
            : parseAttachmentIdInput(rawId);
        if (!WeaponAttachmentService.isValidAttachmentId(id)) {
            throw new SimpleCommandExceptionType(Component.literal("非法配件 ID: " + id)).create();
        }
        int level = IntegerArgumentType.getInteger(context, "level");
        LoadoutUiService.handleAdminAttachmentEdit(player, id, "create", String.valueOf(level));
        return replyInfo(
            context.getSource(),
            String.format(Locale.ROOT, "已请求导入配件: %s | type=%s | level=%d", id, WeaponAttachmentService.getAttachmentTypeLabel(compatService.resolveAttachmentType(held)), level)
        );
    }

    private static int executeAttachmentAddAllByType(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TaczAttachmentCompatService compatService = TaczAttachmentCompatService.getInstance();
        String normalizedType = WeaponAttachmentService.normalizeAttachmentType(StringArgumentType.getString(context, "type"));
        int level = IntegerArgumentType.getInteger(context, "level");
        if (normalizedType.isBlank()) {
            return replyInfo(context.getSource(), "非法配件类型，仅支持: " + String.join(", ", WeaponAttachmentService.ORDERED_ATTACHMENT_TYPES));
        }

        WeaponAttachmentDataStore handler = getAttachmentDataStore(context);
        WeaponAttachmentDataModel data = handler.getData();
        int added = 0;
        int skipped = 0;
        int invalid = 0;
        for (ItemStack stack : compatService.listOfficialAttachmentsByType(normalizedType)) {
            ResourceLocation attachmentDefinitionId = compatService.resolveAttachmentId(stack);
            if (attachmentDefinitionId == null) {
                invalid++;
                continue;
            }
            String generatedId = buildAttachmentIdFromResourceLocation(attachmentDefinitionId);
            if (!WeaponAttachmentService.isValidAttachmentId(generatedId)) {
                invalid++;
                continue;
            }
            if (data.getAttachmentById().containsKey(generatedId)) {
                skipped++;
                continue;
            }
            WeaponAttachmentDataModel.WeaponAttachmentEntry entry = new WeaponAttachmentDataModel.WeaponAttachmentEntry();
            entry.setId(generatedId);
            entry.setDisplayName(resolveRealItemName(player, stack, attachmentDefinitionId));
            entry.setAttachmentType(normalizedType);
            entry.setUnlockLevel(level);
            entry.setItemSnbt(serializeStackToSnbt(stack));
            entry.setFutureRequirement("");
            data.getAttachmentById().put(generatedId, entry);
            added++;
        }
        if (added > 0) {
            handler.saveData(data);
            LoadoutUiService.sendSnapshot(player, false, true);
        }
        return replyInfo(
            context.getSource(),
            String.format(
                Locale.ROOT,
                "已按类型批量导入 %s: 新增=%d, 跳过已存在=%d, 非法ID=%d, level=%d",
                WeaponAttachmentService.getAttachmentTypeLabel(normalizedType),
                added,
                skipped,
                invalid,
                level
            )
        );
    }

    private static int executeAttachmentInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = parseAttachmentIdInput(StringArgumentType.getString(context, "id"));
        WeaponAttachmentDataModel data = getAttachmentDataStore(context).getData();
        WeaponAttachmentDataModel.WeaponAttachmentEntry entry = data.getAttachmentById().get(id);
        if (entry == null) {
            throw new SimpleCommandExceptionType(Component.literal("未找到配件ID: " + id)).create();
        }
        sendListLine(context.getSource(), Component.literal("========== 配件详情 ==========").withStyle(ChatFormatting.GOLD));
        sendListLine(context.getSource(), Component.literal("id=").append(Component.literal(entry.getId()).withStyle(ChatFormatting.AQUA)));
        sendListLine(context.getSource(), Component.literal("displayName=").append(Component.literal(normalizeFreeText(entry.getDisplayName())).withStyle(ChatFormatting.WHITE)));
        sendListLine(context.getSource(), Component.literal("attachmentType=").append(Component.literal(WeaponAttachmentService.getAttachmentTypeLabel(entry.getAttachmentType()) + " (" + normalizeFreeText(entry.getAttachmentType()) + ")").withStyle(ChatFormatting.YELLOW)));
        sendListLine(context.getSource(), Component.literal("unlockLevel=").append(Component.literal(String.valueOf(entry.getUnlockLevel())).withStyle(ChatFormatting.YELLOW)));
        sendListLine(context.getSource(), Component.literal("futureRequirement=").append(Component.literal(normalizeSnbtPreview(entry.getFutureRequirement())).withStyle(ChatFormatting.GRAY)));
        sendListLine(context.getSource(), Component.literal("itemSnbt=").append(Component.literal(normalizeSnbtPreview(entry.getItemSnbt())).withStyle(ChatFormatting.DARK_GRAY)));
        return 1;
    }

    private static int executeAttachmentEdit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = parseAttachmentIdInput(StringArgumentType.getString(context, "id"));
        String field = StringArgumentType.getString(context, "field");
        String value = StringArgumentType.getString(context, "value");
        LoadoutUiService.handleAdminAttachmentEdit(player, id, field, value);
        return replyInfo(context.getSource(), "已请求更新配件字段: " + id + "." + field);
    }

    private static int executeAttachmentList(CommandContext<CommandSourceStack> context) {
        String attachmentType = "";
        try {
            attachmentType = StringArgumentType.getString(context, "type");
        } catch (IllegalArgumentException ignored) {
        }
        String normalizedType = WeaponAttachmentService.normalizeAttachmentType(attachmentType);
        if (!attachmentType.isBlank() && normalizedType.isBlank()) {
            return replyInfo(context.getSource(), "非法配件类型: " + attachmentType);
        }
        WeaponAttachmentDataModel data = getAttachmentDataStore(context).getData();
        List<WeaponAttachmentDataModel.WeaponAttachmentEntry> entries = new ArrayList<>();
        if (normalizedType.isBlank()) {
            entries.addAll(data.getAttachmentById().values());
            entries.sort(Comparator.comparing(WeaponAttachmentDataModel.WeaponAttachmentEntry::getAttachmentType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(WeaponAttachmentDataModel.WeaponAttachmentEntry::getUnlockLevel)
                .thenComparing(WeaponAttachmentDataModel.WeaponAttachmentEntry::getId, String.CASE_INSENSITIVE_ORDER));
        } else {
            entries.addAll(WeaponAttachmentService.listEntriesByType(data, normalizedType));
        }
        ServerPlayer player = context.getSource().getPlayer();
        int level = player == null ? -1 : PlayerLevelService.getEffectiveLevel(player);
        sendListLine(context.getSource(), Component.literal("========== 配件列表 ==========").withStyle(ChatFormatting.GOLD));
        sendListLine(
            context.getSource(),
            Component.literal("过滤类型 ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(normalizedType.isBlank() ? "全部" : WeaponAttachmentService.getAttachmentTypeLabel(normalizedType)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 共 " + entries.size() + " 项").withStyle(ChatFormatting.GRAY))
        );
        if (entries.isEmpty()) {
            sendListLine(context.getSource(), Component.literal("暂无数据").withStyle(ChatFormatting.DARK_GRAY));
            return 1;
        }
        for (WeaponAttachmentDataModel.WeaponAttachmentEntry entry : entries) {
            boolean unlocked = level >= 0 && WeaponAttachmentService.isEntryUnlocked(entry, level);
            String unlockStatus = level < 0
                ? ("需等级>=" + entry.getUnlockLevel())
                : (unlocked ? "已解锁" : ("未解锁，需 Lv." + entry.getUnlockLevel()));
            ChatFormatting statusColor = level < 0 ? ChatFormatting.GRAY : (unlocked ? ChatFormatting.GREEN : ChatFormatting.RED);
            MutableComponent line = Component.literal(entry.getId()).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(normalizeFreeText(entry.getDisplayName())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(WeaponAttachmentService.getAttachmentTypeLabel(entry.getAttachmentType())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.valueOf(entry.getUnlockLevel())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(unlockStatus).withStyle(statusColor));
            sendListLine(context.getSource(), line);
        }
        return 1;
    }

    private static int executeAttachmentDel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = parseAttachmentIdInput(StringArgumentType.getString(context, "id"));
        LoadoutUiService.handleAdminAttachmentEdit(player, id, "delete", "1");
        return replyInfo(context.getSource(), "已请求删除配件: " + id);
    }

    private static int executeList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CustomLoadoutSlot slot = requireSlot(IntegerArgumentType.getInteger(context, "slot"));
        EquipmentDataModel data = getEquipmentDataStore(context).getData();
        List<EquipmentDataModel.EquipmentEntry> entries = CustomLoadoutService.listEntriesBySlot(data, slot);
        ServerPlayer player = context.getSource().getPlayer();
        int level = player == null ? -1 : PlayerLevelService.getEffectiveLevel(player);
        String defaultId = CustomLoadoutService.normalizeEquipmentId(data.getDefaultIdBySlot().get(slot.getKey()));

        sendListLine(context.getSource(), Component.literal("========== 装备列表 ==========").withStyle(ChatFormatting.GOLD));
        sendListLine(
            context.getSource(),
            Component.literal("槽位 ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(slot.getKey()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + slot.getIndex() + ") 共 " + entries.size() + " 项").withStyle(ChatFormatting.GRAY))
        );
        if (entries.isEmpty()) {
            sendListLine(context.getSource(), Component.literal("暂无数据").withStyle(ChatFormatting.DARK_GRAY));
            return 1;
        }
        for (EquipmentDataModel.EquipmentEntry entry : entries) {
            boolean unlocked = level >= 0 && CustomLoadoutService.isEntryUnlocked(entry, level);
            String unlockStatus = level < 0
                ? ("需等级>=" + entry.getUnlockLevel())
                : (unlocked ? "已解锁" : ("未解锁，需 Lv." + entry.getUnlockLevel() + ")"));
            ChatFormatting statusColor = level < 0 ? ChatFormatting.GRAY : (unlocked ? ChatFormatting.GREEN : ChatFormatting.RED);
            MutableComponent line = Component.literal(entry.getId()).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(normalizeFreeText(entry.getDisplayName())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.valueOf(entry.getUnlockLevel())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(unlockStatus).withStyle(statusColor))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(entry.getId().equals(defaultId) ? "默认" : "-").withStyle(entry.getId().equals(defaultId) ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            sendListLine(context.getSource(), line);
        }
        return 1;
    }

    private static int executeDel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String normalizedId = parseEquipmentIdInput(StringArgumentType.getString(context, "id"));
        if (!CustomLoadoutService.isValidEquipmentId(normalizedId)) {
            throw INVALID_ID.create(normalizedId);
        }
        EquipmentDataStore handler = getEquipmentDataStore(context);
        EquipmentDataModel data = handler.getData();
        EquipmentDataModel.EquipmentEntry removed = data.getEquipmentById().remove(normalizedId);
        if (removed == null) {
            throw new SimpleCommandExceptionType(Component.literal("未找到装备 ID: " + normalizedId)).create();
        }

        int removedDefaultSlots = clearDefaultMappings(data, normalizedId);
        handler.saveData(data);

        int repairedPlayers = 0;
        for (ServerPlayer online : context.getSource().getServer().getPlayerList().getPlayers()) {
            boolean changed = CustomLoadoutService.sanitizePlayerSelection(online, data, normalizedId, true);
            if (changed) {
                repairedPlayers++;
                refreshAfterSelectionChange(online);
            }
        }
        ServerPlayer operator = context.getSource().getPlayer();
        if (operator != null) {
            LoadoutUiService.sendSnapshot(operator, false, true);
        }
        return replyInfo(
            context.getSource(),
            String.format(
                Locale.ROOT,
                "已删除装备 %s（槽位 %s），清除默认槽位=%d，在线修复玩家=%d",
                normalizedId,
                removed.getSlotName(),
                removedDefaultSlots,
                repairedPlayers
            )
        );
    }

    private static int executeClassSelect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerClassType classType = parseClassTypeInput(StringArgumentType.getString(context, "type"));
        PlayerDataStore playerDataHandler = ConquestDataManager.of(context.getSource().getServer()).playerData();
        PlayerDataModel playerData = playerDataHandler.getPlayerData(player.getUUID());
        playerData.setCurrentClassType(classType.getKey());
        playerDataHandler.savePlayerData(player.getUUID(), playerData);
        refreshAfterSelectionChange(player);
        return replyInfo(context.getSource(), "已切换当前兵种: " + classType.getDisplayName() + " (" + classType.getKey() + ")");
    }

    private static int executeSelectSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerClassType classType = parseClassTypeInput(StringArgumentType.getString(context, "type"));
        CustomLoadoutSlot slot = parseSlotInput(StringArgumentType.getString(context, "slot"));
        String normalizedId = parseEquipmentIdInput(StringArgumentType.getString(context, "id"));
        if (!CustomLoadoutService.isValidEquipmentId(normalizedId)) {
            throw INVALID_ID.create(normalizedId);
        }
        EquipmentDataModel data = getEquipmentDataStore(context).getData();
        EquipmentDataModel.EquipmentEntry entry = data.getEquipmentById().get(normalizedId);
        if (entry == null || entry.getSlot() != slot.getIndex()) {
            throw new SimpleCommandExceptionType(Component.literal("该装备不存在，或不属于槽位 " + slot.getKey())).create();
        }
        if (!CustomLoadoutService.isEntryAvailableForClass(entry, classType)) {
            throw new SimpleCommandExceptionType(Component.literal("该装备当前兵种不可用")).create();
        }
        int level = PlayerLevelService.getEffectiveLevel(player);
        if (!CustomLoadoutService.isEntryUnlocked(entry, level)) {
            throw new SimpleCommandExceptionType(Component.literal("你的等级不足，当前装备解锁等级为 " + entry.getUnlockLevel())).create();
        }
        PlayerDataStore playerDataHandler = ConquestDataManager.of(context.getSource().getServer()).playerData();
        PlayerDataModel playerData = playerDataHandler.getPlayerData(player.getUUID());
        CustomLoadoutService.setSelectedIdForClassSlot(playerData, classType, slot, normalizedId);
        playerDataHandler.savePlayerData(player.getUUID(), playerData);
        refreshAfterSelectionChange(player);
        return replyInfo(
            context.getSource(),
            String.format(Locale.ROOT, "已设置个人配装: %s %s -> %s (%s)", classType.getDisplayName(), slot.getKey(), entry.getDisplayName(), normalizedId)
        );
    }

    private static int executeDefaultSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PlayerClassType classType = parseClassTypeInput(StringArgumentType.getString(context, "type"));
        CustomLoadoutSlot slot = parseSlotInput(StringArgumentType.getString(context, "slot"));
        String normalizedId = parseEquipmentIdInput(StringArgumentType.getString(context, "id"));
        if (!CustomLoadoutService.isValidEquipmentId(normalizedId)) {
            throw INVALID_ID.create(normalizedId);
        }
        EquipmentDataStore handler = getEquipmentDataStore(context);
        EquipmentDataModel data = handler.getData();
        EquipmentDataModel.EquipmentEntry entry = data.getEquipmentById().get(normalizedId);
        if (entry == null || entry.getSlot() != slot.getIndex()) {
            throw new SimpleCommandExceptionType(Component.literal("该装备不存在，或不属于槽位 " + slot.getKey())).create();
        }
        if (!CustomLoadoutService.isEntryAvailableForClass(entry, classType)) {
            throw new SimpleCommandExceptionType(Component.literal("该装备当前兵种不可用")).create();
        }
        if (entry.getUnlockLevel() != 0) {
            throw new SimpleCommandExceptionType(Component.literal("default 仅可设置解锁等级为 0 的装备")).create();
        }
        data.getDefaultIdBySlot().put(slot.getKey(), normalizedId);
        data.getDefaultIdBySlot().put(CustomLoadoutService.makeClassSlotKey(classType, slot), normalizedId);
        handler.saveData(data);
        return replyInfo(
            context.getSource(),
            String.format(Locale.ROOT, "已设置默认装备: %s %s -> %s (%s)", classType.getDisplayName(), slot.getKey(), entry.getDisplayName(), normalizedId)
        );
    }

    private static int executeArmorSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerClassType classType = parseClassTypeInput(StringArgumentType.getString(context, "type"));
        RoomTeamId camp = parseCampInput(StringArgumentType.getString(context, "camp"));
        EquipmentDataStore handler = getEquipmentDataStore(context);
        EquipmentDataModel data = handler.getData();

        EquipmentDataModel.ArmorEntry entry = new EquipmentDataModel.ArmorEntry();
        entry.setHelmetSnbt(serializeStackToSnbt(player.getItemBySlot(EquipmentSlot.HEAD)));
        entry.setChestplateSnbt(serializeStackToSnbt(player.getItemBySlot(EquipmentSlot.CHEST)));
        entry.setLeggingsSnbt(serializeStackToSnbt(player.getItemBySlot(EquipmentSlot.LEGS)));
        entry.setBootsSnbt(serializeStackToSnbt(player.getItemBySlot(EquipmentSlot.FEET)));
        data.getArmorByCampClass().put(CustomLoadoutService.makeCampClassKey(camp, classType), entry);
        handler.saveData(data);

        return replyInfo(
            context.getSource(),
            String.format(
                Locale.ROOT,
                "已保存护甲模板: camp=%s, class=%s, head=%s, chest=%s, legs=%s, feet=%s",
                normalizeCampDisplay(camp),
                classType.getKey(),
                summarizeArmorStack(player.getItemBySlot(EquipmentSlot.HEAD)),
                summarizeArmorStack(player.getItemBySlot(EquipmentSlot.CHEST)),
                summarizeArmorStack(player.getItemBySlot(EquipmentSlot.LEGS)),
                summarizeArmorStack(player.getItemBySlot(EquipmentSlot.FEET))
            )
        );
    }

    private static int executeArmorList(CommandContext<CommandSourceStack> context) {
        EquipmentDataModel data = getEquipmentDataStore(context).getData();
        Map<String, EquipmentDataModel.ArmorEntry> armorByCampClass = data.getArmorByCampClass();
        if (armorByCampClass.isEmpty()) {
            return replyInfo(context.getSource(), "当前没有任何 armor 模板数据");
        }
        List<Map.Entry<String, EquipmentDataModel.ArmorEntry>> entries = new ArrayList<>(armorByCampClass.entrySet());
        entries.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
        sendListLine(context.getSource(), Component.literal("========== 护甲模板 ==========").withStyle(ChatFormatting.GOLD));
        for (Map.Entry<String, EquipmentDataModel.ArmorEntry> mapEntry : entries) {
            EquipmentDataModel.ArmorEntry armorEntry = mapEntry.getValue();
            sendListLine(context.getSource(), Component.literal(mapEntry.getKey()).withStyle(ChatFormatting.AQUA));
            if (armorEntry == null) {
                sendListLine(context.getSource(), Component.literal("  <null>").withStyle(ChatFormatting.DARK_GRAY));
                continue;
            }
            sendListLine(context.getSource(), Component.literal("  helmet=" + normalizeSnbtText(armorEntry.getHelmetSnbt())).withStyle(ChatFormatting.GRAY));
            sendListLine(context.getSource(), Component.literal("  chestplate=" + normalizeSnbtText(armorEntry.getChestplateSnbt())).withStyle(ChatFormatting.GRAY));
            sendListLine(context.getSource(), Component.literal("  leggings=" + normalizeSnbtText(armorEntry.getLeggingsSnbt())).withStyle(ChatFormatting.GRAY));
            sendListLine(context.getSource(), Component.literal("  boots=" + normalizeSnbtText(armorEntry.getBootsSnbt())).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestSlotNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (String value : CustomLoadoutSlot.localizedNameSuggestions()) {
            builder.suggest(needsQuotedSuggestion(value) ? "\"" + value + "\"" : value);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestAddItemSlotValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("1", "2", "3", "4", "5", "6"), builder);
    }

    private static CompletableFuture<Suggestions> suggestClassTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (String value : PlayerClassType.suggestions()) {
            builder.suggest(needsQuotedSuggestion(value) ? "\"" + value + "\"" : value);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCampTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("campA", "campB"), builder);
    }

    private static CompletableFuture<Suggestions> suggestEquipmentFields(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            List.of(
                "id",
                "displayName",
                "slot",
                "slotName",
                "extraData",
                "unlockLevel",
                "availableClasses",
                "itemSnbt",
                "replenishSeconds",
                "ammoBoxRefillEnabled",
                "set_default",
                "delete"
            ),
            builder
        );
    }

    private static CompletableFuture<Suggestions> suggestAttachmentTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(WeaponAttachmentService.ORDERED_ATTACHMENT_TYPES, builder);
    }

    private static CompletableFuture<Suggestions> suggestAttachmentFields(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            List.of(
                "id",
                "displayName",
                "unlockLevel",
                "itemSnbt",
                "futureRequirement",
                "delete"
            ),
            builder
        );
    }

    private static CompletableFuture<Suggestions> suggestEquipmentEditValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String field;
        try {
            field = StringArgumentType.getString(context, "field");
        } catch (Exception exception) {
            return builder.buildFuture();
        }
        String normalized = field == null ? "" : field.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "slot" -> SharedSuggestionProvider.suggest(List.of("1", "2", "3", "4", "5", "6"), builder);
            case "slotname", "slot_name" -> SharedSuggestionProvider.suggest(List.of(
                "primary_weapon",
                "secondary_weapon",
                "melee",
                "gadget_1",
                "gadget_2",
                "throwable"
            ), builder);
            case "unlocklevel", "unlock_level", "extradata", "extra_data", "replenishseconds", "replenish_seconds" ->
                SharedSuggestionProvider.suggest(List.of("0"), builder);
            case "availableclasses", "available_classes" ->
                SharedSuggestionProvider.suggest(List.of("1", "2", "3", "4", "12", "13", "14", "23", "24", "34", "1234"), builder);
            case "ammoboxrefillenabled", "ammo_box_refill_enabled" ->
                SharedSuggestionProvider.suggest(List.of("true", "false"), builder);
            case "itemsnbt", "item_snbt" -> suggestItemSnbtEditValues(context, builder);
            case "set_default", "setdefault" -> SharedSuggestionProvider.suggest(List.of("1"), builder);
            case "delete" -> SharedSuggestionProvider.suggest(List.of("1"), builder);
            default -> builder.buildFuture();
        };
    }

    private static CompletableFuture<Suggestions> suggestAttachmentEditValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String field;
        try {
            field = StringArgumentType.getString(context, "field");
        } catch (Exception exception) {
            return builder.buildFuture();
        }
        String normalized = field == null ? "" : field.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "unlocklevel", "unlock_level" -> SharedSuggestionProvider.suggest(List.of("0"), builder);
            case "itemsnbt", "item_snbt" -> suggestItemSnbtEditValues(context, builder);
            case "delete" -> SharedSuggestionProvider.suggest(List.of("1"), builder);
            default -> builder.buildFuture();
        };
    }

    private static CompletableFuture<Suggestions> suggestAllIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        EquipmentDataModel data = getEquipmentDataStore(context).getData();
        List<EquipmentDataModel.EquipmentEntry> entries = new ArrayList<>(data.getEquipmentById().values());
        entries.sort(Comparator.comparing(EquipmentDataModel.EquipmentEntry::getId, String.CASE_INSENSITIVE_ORDER));
        for (EquipmentDataModel.EquipmentEntry entry : entries) {
            if (entry == null || CustomLoadoutService.normalizeEquipmentId(entry.getId()).isBlank()) {
                continue;
            }
            builder.suggest(buildQuotedIdSuggestion(entry.getId(), entry.getDisplayName()));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestAllAttachmentIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        WeaponAttachmentDataModel data = getAttachmentDataStore(context).getData();
        List<WeaponAttachmentDataModel.WeaponAttachmentEntry> entries = new ArrayList<>(data.getAttachmentById().values());
        entries.sort(Comparator.comparing(WeaponAttachmentDataModel.WeaponAttachmentEntry::getId, String.CASE_INSENSITIVE_ORDER));
        for (WeaponAttachmentDataModel.WeaponAttachmentEntry entry : entries) {
            if (entry == null || WeaponAttachmentService.normalizeAttachmentId(entry.getId()).isBlank()) {
                continue;
            }
            builder.suggest(buildQuotedIdSuggestion(entry.getId(), entry.getDisplayName()));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestSelectableIdsByClassAndSlot(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestIdsByClassAndSlot(context, builder, false);
    }

    private static CompletableFuture<Suggestions> suggestDefaultIdsByClassAndSlot(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestIdsByClassAndSlot(context, builder, true);
    }

    private static CompletableFuture<Suggestions> suggestIdsByClassAndSlot(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder, boolean defaultOnly) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null && !defaultOnly) {
            return builder.buildFuture();
        }
        PlayerClassType classType;
        CustomLoadoutSlot slot;
        try {
            classType = parseClassTypeInput(StringArgumentType.getString(context, "type"));
            slot = parseSlotInput(StringArgumentType.getString(context, "slot"));
        } catch (Exception exception) {
            return builder.buildFuture();
        }
        int level = defaultOnly ? Integer.MAX_VALUE : PlayerLevelService.getEffectiveLevel(player);
        EquipmentDataModel data = getEquipmentDataStore(context).getData();
        List<EquipmentDataModel.EquipmentEntry> entries = new ArrayList<>();
        for (EquipmentDataModel.EquipmentEntry entry : CustomLoadoutService.listEntriesByClassAndSlot(data, classType, slot)) {
            if (defaultOnly) {
                if (entry.getUnlockLevel() != 0) {
                    continue;
                }
            } else if (!CustomLoadoutService.isEntryUnlocked(entry, level)) {
                continue;
            }
            entries.add(entry);
        }
        entries.sort(Comparator.comparing(EquipmentDataModel.EquipmentEntry::getId, String.CASE_INSENSITIVE_ORDER));
        for (EquipmentDataModel.EquipmentEntry entry : entries) {
            builder.suggest(buildQuotedIdSuggestion(entry.getId(), entry.getDisplayName()));
        }
        return builder.buildFuture();
    }

    private static PlayerClassType parseClassTypeInput(String typeInput) throws CommandSyntaxException {
        String normalizedInput = stripWrappingQuotes(typeInput);
        PlayerClassType classType = PlayerClassType.fromInput(normalizedInput);
        if (classType == null) {
            throw INVALID_CLASS.create(normalizedInput);
        }
        return classType;
    }

    private static CustomLoadoutSlot parseSlotInput(String slotInput) throws CommandSyntaxException {
        String normalizedInput = stripWrappingQuotes(slotInput);
        CustomLoadoutSlot slot = CustomLoadoutSlot.fromInput(normalizedInput);
        if (slot == null) {
            throw INVALID_SLOT.create(normalizedInput);
        }
        return slot;
    }

    private static CustomLoadoutSlot requireSlot(int slotValue) throws CommandSyntaxException {
        CustomLoadoutSlot slot = CustomLoadoutSlot.fromIndex(slotValue);
        if (slot == null) {
            throw INVALID_SLOT.create(slotValue);
        }
        return slot;
    }

    private static RoomTeamId parseCampInput(String campInput) throws CommandSyntaxException {
        if (campInput == null || campInput.isBlank()) {
            throw INVALID_CAMP.create(campInput);
        }
        String normalized = campInput.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "campa", "camp_a", "a" -> RoomTeamId.CAMP_A;
            case "campb", "camp_b", "b" -> RoomTeamId.CAMP_B;
            default -> throw INVALID_CAMP.create(campInput);
        };
    }

    private static void refreshAfterSelectionChange(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        LoadoutUiService.sendSnapshot(player, false, true);
    }

    private static int clearDefaultMappings(EquipmentDataModel data, String normalizedId) {
        int removed = 0;
        for (PlayerClassType classType : PlayerClassType.values()) {
            for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
                String key = CustomLoadoutService.makeClassSlotKey(classType, slot);
                if (normalizedId.equals(CustomLoadoutService.normalizeEquipmentId(data.getDefaultIdBySlot().get(key)))) {
                    data.getDefaultIdBySlot().remove(key);
                    removed++;
                }
            }
        }
        for (CustomLoadoutSlot slot : CustomLoadoutSlot.values()) {
            if (normalizedId.equals(CustomLoadoutService.normalizeEquipmentId(data.getDefaultIdBySlot().get(slot.getKey())))) {
                data.getDefaultIdBySlot().remove(slot.getKey());
                removed++;
            }
        }
        return removed;
    }

    private static String resolveRealItemName(ServerPlayer player, ItemStack stack, ResourceLocation itemKey) {
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

    private static String resolveRealItemName(ItemStack stack, ResourceLocation itemKey) {
        if (stack != null && !stack.isEmpty()) {
            String hoverName = normalizeFreeText(stack.getHoverName().getString());
            if (!hoverName.isBlank()) {
                return hoverName;
            }
        }
        return itemKey == null ? "unknown" : itemKey.toString();
    }

    private static ItemStack parseItemStack(String snbt) {
        try {
            return ItemStack.of(TagParser.parseTag(snbt));
        } catch (Exception exception) {
            return ItemStack.EMPTY;
        }
    }

    private static String normalizeCampDisplay(RoomTeamId teamId) {
        return teamId == RoomTeamId.CAMP_B ? "campB" : "campA";
    }

    private static String serializeStackToSnbt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag tag = new CompoundTag();
        stack.save(tag);
        return tag.toString();
    }

    private static String summarizeArmorStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "<empty>";
        }
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemKey == null ? "unknown" : itemKey.toString();
    }

    private static String normalizeSnbtText(String snbt) {
        return snbt == null || snbt.isBlank() ? "<empty>" : snbt;
    }

    private static String normalizeSnbtPreview(String snbt) {
        String normalized = normalizeSnbtText(snbt);
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 160) + "...";
    }

    private static List<String> collectDefaultMappings(EquipmentDataModel data, String normalizedId) {
        List<String> result = new ArrayList<>();
        if (data == null || normalizedId == null || normalizedId.isBlank()) {
            return result;
        }
        for (Map.Entry<String, String> entry : data.getDefaultIdBySlot().entrySet()) {
            if (normalizedId.equals(CustomLoadoutService.normalizeEquipmentId(entry.getValue()))) {
                result.add(entry.getKey());
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private static CompletableFuture<Suggestions> suggestItemSnbtEditValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        builder.suggest("hand");
        builder.suggest("mainhand");
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return builder.buildFuture();
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return builder.buildFuture();
        }
        CompoundTag tag = new CompoundTag();
        held.save(tag);
        builder.suggest(tag.toString());
        return builder.buildFuture();
    }

    private static boolean isItemSnbtField(String field) {
        if (field == null) {
            return false;
        }
        String normalized = field.trim().toLowerCase(Locale.ROOT);
        return "itemsnbt".equals(normalized) || "item_snbt".equals(normalized);
    }

    private static String resolveItemSnbtEditValue(ServerPlayer player, String value) throws CommandSyntaxException {
        String normalized = value == null ? "" : value.trim();
        if (!"hand".equalsIgnoreCase(normalized) && !"mainhand".equalsIgnoreCase(normalized)) {
            return value;
        }
        if (player == null) {
            throw new SimpleCommandExceptionType(Component.literal("itemSnbt=hand 只能由玩家执行")).create();
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            throw EMPTY_HAND.create();
        }
        CompoundTag tag = new CompoundTag();
        held.save(tag);
        return tag.toString();
    }

    private static void sendListLine(CommandSourceStack source, Component line) {
        ServerMessageSender.line(source, line);
    }

    private static String parseEquipmentIdInput(String raw) {
        String normalized = CustomLoadoutService.normalizeEquipmentId(raw);
        if (CustomLoadoutService.isValidEquipmentId(normalized)) {
            return normalized;
        }
        int splitIndex = normalized.indexOf('-');
        if (splitIndex <= 0) {
            return normalized;
        }
        String idPart = CustomLoadoutService.normalizeEquipmentId(normalized.substring(0, splitIndex));
        return CustomLoadoutService.isValidEquipmentId(idPart) ? idPart : normalized;
    }

    private static String parseAttachmentIdInput(String raw) {
        String normalized = WeaponAttachmentService.normalizeAttachmentId(raw);
        if (WeaponAttachmentService.isValidAttachmentId(normalized)) {
            return normalized;
        }
        int splitIndex = normalized.indexOf('-');
        if (splitIndex <= 0) {
            return normalized;
        }
        String idPart = WeaponAttachmentService.normalizeAttachmentId(normalized.substring(0, splitIndex));
        return WeaponAttachmentService.isValidAttachmentId(idPart) ? idPart : normalized;
    }

    private static String buildAttachmentIdFromResourceLocation(ResourceLocation resourceLocation) {
        if (resourceLocation == null) {
            return "";
        }
        String raw = (resourceLocation.getNamespace() + "_" + resourceLocation.getPath()).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(raw.length());
        boolean lastUnderscore = false;
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            boolean valid = (current >= 'a' && current <= 'z') || (current >= '0' && current <= '9');
            if (valid) {
                builder.append(current);
                lastUnderscore = false;
                continue;
            }
            if (!lastUnderscore) {
                builder.append('_');
                lastUnderscore = true;
            }
        }
        while (builder.length() > 0 && builder.charAt(0) == '_') {
            builder.deleteCharAt(0);
        }
        while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '_') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }

    private static String buildNextAvailableAttachmentId(WeaponAttachmentDataModel data, String baseId) {
        String normalizedBase = WeaponAttachmentService.normalizeAttachmentId(baseId);
        if (!WeaponAttachmentService.isValidAttachmentId(normalizedBase)) {
            return normalizedBase;
        }
        if (data == null || !data.getAttachmentById().containsKey(normalizedBase)) {
            return normalizedBase;
        }
        int suffix = 2;
        while (suffix < 10000) {
            String candidate = normalizedBase + "_" + suffix;
            if (WeaponAttachmentService.isValidAttachmentId(candidate) && !data.getAttachmentById().containsKey(candidate)) {
                return candidate;
            }
            suffix++;
        }
        return normalizedBase;
    }

    private static String buildQuotedIdSuggestion(String id, String displayName) {
        String normalizedId = CustomLoadoutService.normalizeEquipmentId(id);
        String safeDisplayName = normalizeFreeText(displayName).replace("\\", "\\\\").replace("\"", "\\\"");
        String value = safeDisplayName.isBlank() ? normalizedId : (normalizedId + "-" + safeDisplayName);
        return "\"" + value + "\"";
    }

    private static boolean needsQuotedSuggestion(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7F) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeFreeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripWrappingQuotes(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() < 2) {
            return trimmed;
        }
        char first = trimmed.charAt(0);
        char last = trimmed.charAt(trimmed.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static EquipmentDataStore getEquipmentDataStore(CommandContext<CommandSourceStack> context) {
        return ConquestDataManager.of(context.getSource().getServer()).equipmentData();
    }

    private static WeaponAttachmentDataStore getAttachmentDataStore(CommandContext<CommandSourceStack> context) {
        return ConquestDataManager.of(context.getSource().getServer()).weaponAttachments();
    }

    private static int replyInfo(CommandSourceStack source, String message) {
        return CommandFeedbacks.info(source, message);
    }
}
