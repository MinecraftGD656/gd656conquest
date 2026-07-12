package org.mods.gd656conquest.server.facility.support;

import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.shelling.ShellingDataModel;
import org.mods.gd656conquest.server.data.shelling.ShellingImpactType;
import org.mods.gd656conquest.server.data.shelling.ShellingDataStore;
import org.mods.gd656conquest.server.loadout.TaczAttachmentCompatService;
import org.mods.gd656conquest.server.loadout.TaczDisplayNameResolver;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.shared.text.ServerText;
import org.mods.gd656conquest.server.shared.util.ItemStackSnbtCodec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ShellingCommands {
    private static final List<String> SHELLING_EDIT_FIELDS = List.of(
        "displayName",
        "shellHeight",
        "durationSeconds",
        "intervalSeconds",
        "radius",
        "cost",
        "impactType",
        "pitch",
        "yaw"
    );
    private static final DynamicCommandExceptionType INVALID_ID = new DynamicCommandExceptionType(
        value -> Component.literal("非法炮击 ID: " + value + "，仅支持字母、数字和下划线")
    );
    private static final DynamicCommandExceptionType SHELLING_EXISTS = new DynamicCommandExceptionType(
        value -> Component.literal("炮击模板已存在: " + value)
    );
    private static final DynamicCommandExceptionType SHELLING_NOT_FOUND = new DynamicCommandExceptionType(
        value -> Component.literal("未找到炮击模板: " + value)
    );
    private static final SimpleCommandExceptionType TACZ_NOT_AVAILABLE = new SimpleCommandExceptionType(
        Component.literal("当前环境未检测到 TACZ，无法注册炮击模板")
    );
    private static final SimpleCommandExceptionType MAIN_HAND_NOT_TACZ_GUN = new SimpleCommandExceptionType(
        Component.literal("当前主手物品不是 TACZ 枪械")
    );
    private static final SimpleCommandExceptionType INVALID_GUN_ID = new SimpleCommandExceptionType(
        Component.literal("无法解析当前 TACZ 枪械 ID")
    );
    private static final DynamicCommandExceptionType INVALID_IMPACT_TYPE = new DynamicCommandExceptionType(
        value -> Component.literal("非法炮击类型: " + value + "，仅支持 empty、lightshell、mediumshell、heavyshell、missile、smokeshell、flameshell")
    );
    private static final DynamicCommandExceptionType INVALID_FIELD = new DynamicCommandExceptionType(
        value -> Component.literal("不支持修改的炮击配置项: " + value)
    );

    private ShellingCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("shelling")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("add")
                .then(Commands.argument("id", StringArgumentType.string())
                    .then(Commands.argument("displayName", StringArgumentType.string())
                        .then(Commands.argument("shellHeight", DoubleArgumentType.doubleArg(0.0D))
                            .then(Commands.argument("durationSeconds", DoubleArgumentType.doubleArg(0.05D))
                                .then(Commands.argument("intervalSeconds", DoubleArgumentType.doubleArg(0.05D))
                                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.0D))
                                        .then(Commands.argument("cost", IntegerArgumentType.integer(0))
                                            .then(Commands.argument("impactType", StringArgumentType.string())
                                                .suggests(ShellingCommands::suggestImpactTypes)
                                                .executes(ShellingCommands::executeAdd)
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                ))
            .then(Commands.literal("edit")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(ShellingCommands::suggestIds)
                    .executes(ShellingCommands::executeEdit)))
            .then(Commands.literal("del")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(ShellingCommands::suggestIds)
                    .executes(ShellingCommands::executeDelete)))
            .then(Commands.literal("list")
                .executes(ShellingCommands::executeList))
            .then(Commands.literal("reload")
                .executes(ShellingCommands::executeReload))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(ShellingCommands::suggestIds)
                .then(Commands.argument("field", StringArgumentType.string())
                    .suggests(ShellingCommands::suggestEditFields)
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(ShellingCommands::suggestEditValues)
                        .executes(ShellingCommands::executeSetField))));
    }

    private static int executeAdd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = normalizeShellingId(StringArgumentType.getString(context, "id"));
        ShellingDataStore store = ConquestDataManager.of(context.getSource().getServer()).shellings();
        ShellingDataModel data = store.get();
        if (data.getShellingsById().containsKey(id)) {
            throw SHELLING_EXISTS.create(id);
        }
        ShellingDataModel.ShellingEntry entry = captureShellingEntry(
            context,
            player,
            id,
            StringArgumentType.getString(context, "displayName"),
            DoubleArgumentType.getDouble(context, "shellHeight"),
            DoubleArgumentType.getDouble(context, "durationSeconds"),
            DoubleArgumentType.getDouble(context, "intervalSeconds"),
            DoubleArgumentType.getDouble(context, "radius"),
            IntegerArgumentType.getInteger(context, "cost"),
            resolveImpactType(StringArgumentType.getString(context, "impactType"))
        );
        data.getShellingsById().put(id, entry);
        persistShellings(context.getSource(), store, data);
        return CommandFeedbacks.info(
            context.getSource(),
            String.format(
                Locale.ROOT,
                "已新增炮击模板 %s，显示=%s，武器=%s，类型=%s，分数=%d，高度=%.2f，持续=%.2fs，间隔=%.2fs，半径=%.2f",
                id,
                resolveEntryDisplayName(entry),
                entry.getGunDisplayName(),
                entry.resolveImpactType().displayName() + "(" + entry.resolveImpactType().id() + ")",
                entry.getCost(),
                entry.getShellHeight(),
                entry.getDurationSeconds(),
                entry.getIntervalSeconds(),
                entry.getRadius()
            )
        );
    }

    private static int executeEdit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = normalizeShellingId(StringArgumentType.getString(context, "id"));
        ShellingDataStore store = ConquestDataManager.of(context.getSource().getServer()).shellings();
        ShellingDataModel data = store.get();
        ShellingDataModel.ShellingEntry existing = data.getShellingsById().get(id);
        if (existing == null) {
            throw SHELLING_NOT_FOUND.create(id);
        }
        data.getShellingsById().put(
            id,
            captureShellingEntry(
                context,
                player,
                id,
                existing.getDisplayName(),
                existing.getShellHeight(),
                existing.getDurationSeconds(),
                existing.getIntervalSeconds(),
                existing.getRadius(),
                existing.getCost(),
                existing.resolveImpactType()
            )
        );
        persistShellings(context.getSource(), store, data);
        return CommandFeedbacks.info(context.getSource(), "已使用当前玩家朝向与主手 TACZ 枪械刷新炮击模板: " + id);
    }

    private static int executeDelete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = normalizeShellingId(StringArgumentType.getString(context, "id"));
        ShellingDataStore store = ConquestDataManager.of(context.getSource().getServer()).shellings();
        ShellingDataModel data = store.get();
        ShellingDataModel.ShellingEntry removed = data.getShellingsById().remove(id);
        if (removed == null) {
            throw SHELLING_NOT_FOUND.create(id);
        }
        persistShellings(context.getSource(), store, data);
        return CommandFeedbacks.info(context.getSource(), "已删除炮击模板: " + id);
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        ShellingDataModel data = ConquestDataManager.of(context.getSource().getServer()).shellings().get();
        List<ShellingDataModel.ShellingEntry> entries = new ArrayList<>(data.getShellingsById().values());
        entries.removeIf(entry -> entry == null || entry.getId().isBlank());
        entries.sort(Comparator.comparing(ShellingDataModel.ShellingEntry::getId, String.CASE_INSENSITIVE_ORDER));
        if (entries.isEmpty()) {
            return CommandFeedbacks.info(context.getSource(), "当前没有任何炮击模板");
        }
        CommandFeedbacks.line(context.getSource(), ServerText.colored("========== 炮击模板列表 ==========", ChatFormatting.GOLD));
        for (ShellingDataModel.ShellingEntry entry : entries) {
            CommandFeedbacks.line(
                context.getSource(),
                ServerText.colored(
                    String.format(
                        Locale.ROOT,
                        "%s | 显示=%s | 武器=%s | 类型=%s | 分数=%d | 高度=%.2f | 持续=%.2fs | 间隔=%.2fs | 半径=%.2f | pitch=%.1f | yaw=%.1f",
                        entry.getId(),
                        resolveEntryDisplayName(entry),
                        entry.getGunDisplayName().isBlank() ? fallbackGunName(entry) : entry.getGunDisplayName(),
                        entry.resolveImpactType().displayName() + "(" + entry.resolveImpactType().id() + ")",
                        entry.getCost(),
                        entry.getShellHeight(),
                        entry.getDurationSeconds(),
                        entry.getIntervalSeconds(),
                        entry.getRadius(),
                        entry.getPitch(),
                        entry.getYaw()
                    ),
                    ChatFormatting.AQUA
                )
            );
        }
        return CommandFeedbacks.info(context.getSource(), "炮击模板总数: " + entries.size());
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {
        ShellingDataModel data = ConquestDataManager.of(context.getSource().getServer()).shellings().reloadFromDisk();
        return CommandFeedbacks.info(context.getSource(), "已从磁盘重载炮击模板，当前条目数: " + data.getShellingsById().size());
    }

    private static int executeSetField(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = normalizeShellingId(StringArgumentType.getString(context, "id"));
        String field = normalizeEditField(StringArgumentType.getString(context, "field"));
        String rawValue = unwrapQuotedValue(StringArgumentType.getString(context, "value"));
        ShellingDataStore store = ConquestDataManager.of(context.getSource().getServer()).shellings();
        ShellingDataModel data = store.get();
        ShellingDataModel.ShellingEntry entry = data.getShellingsById().get(id);
        if (entry == null) {
            throw SHELLING_NOT_FOUND.create(id);
        }
        switch (field) {
            case "displayname" -> entry.setDisplayName(normalizeDisplayName(rawValue, id));
            case "shellheight" -> entry.setShellHeight(parseNonNegativeDouble(field, rawValue));
            case "durationseconds" -> entry.setDurationSeconds(parsePositiveOrZeroDouble(field, rawValue));
            case "intervalseconds" -> entry.setIntervalSeconds(parsePositiveOrZeroDouble(field, rawValue));
            case "radius" -> entry.setRadius(parseNonNegativeDouble(field, rawValue));
            case "cost" -> entry.setCost(parseNonNegativeInteger(field, rawValue));
            case "impacttype" -> entry.setImpactType(resolveImpactType(rawValue).id());
            case "pitch" -> entry.setPitch((float) parseFiniteDouble(field, rawValue));
            case "yaw" -> entry.setYaw((float) parseFiniteDouble(field, rawValue));
            default -> throw INVALID_FIELD.create(field);
        }
        persistShellings(context.getSource(), store, data);
        return CommandFeedbacks.info(
            context.getSource(),
            String.format(Locale.ROOT, "已更新炮击模板 %s 的 %s = %s", id, field, rawValue)
        );
    }

    private static CompletableFuture<Suggestions> suggestIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> ids = new ArrayList<>(ConquestDataManager.of(context.getSource().getServer()).shellings().get().getShellingsById().keySet());
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static CompletableFuture<Suggestions> suggestImpactTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ShellingImpactType.suggestionIds(), builder);
    }

    private static CompletableFuture<Suggestions> suggestEditFields(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(SHELLING_EDIT_FIELDS, builder);
    }

    private static CompletableFuture<Suggestions> suggestEditValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String field = normalizeEditField(StringArgumentType.getString(context, "field"));
        if ("impacttype".equals(field)) {
            return SharedSuggestionProvider.suggest(ShellingImpactType.suggestionIds(), builder);
        }
        return Suggestions.empty();
    }

    private static ShellingDataModel.ShellingEntry captureShellingEntry(
        CommandContext<CommandSourceStack> context,
        ServerPlayer player,
        String id,
        String displayName,
        double shellHeight,
        double durationSeconds,
        double intervalSeconds,
        double radius,
        int cost,
        ShellingImpactType impactType
    ) throws CommandSyntaxException {
        if (player == null) {
            throw MAIN_HAND_NOT_TACZ_GUN.create();
        }
        TaczAttachmentCompatService compatService = TaczAttachmentCompatService.getInstance();
        if (!compatService.isAvailable()) {
            throw TACZ_NOT_AVAILABLE.create();
        }
        ItemStack held = player.getMainHandItem();
        if (!compatService.isTaczGun(held)) {
            throw MAIN_HAND_NOT_TACZ_GUN.create();
        }
        ResourceLocation gunId = compatService.resolveGunId(held);
        if (gunId == null) {
            throw INVALID_GUN_ID.create();
        }
        ResourceLocation gunDisplayId = compatService.resolveGunDisplayId(held);
        ShellingDataModel.ShellingEntry entry = new ShellingDataModel.ShellingEntry();
        entry.setId(id);
        entry.setDisplayName(normalizeDisplayName(displayName, id));
        entry.setShellHeight(shellHeight);
        entry.setDurationSeconds(durationSeconds);
        entry.setIntervalSeconds(intervalSeconds);
        entry.setRadius(radius);
        entry.setCost(cost);
        entry.setImpactType(impactType.id());
        entry.setPitch(player.getXRot());
        entry.setYaw(player.getYRot());
        entry.setGunItemSnbt(ItemStackSnbtCodec.serialize(held));
        entry.setGunId(gunId.toString());
        entry.setGunDisplayId(gunDisplayId == null ? gunId.toString() : gunDisplayId.toString());
        entry.setGunDisplayName(resolveGunDisplayName(context, held, gunId));
        return entry;
    }

    private static ShellingImpactType resolveImpactType(String rawValue) throws CommandSyntaxException {
        String normalized = rawValue == null ? "" : rawValue.trim();
        ShellingImpactType impactType = ShellingImpactType.findById(normalized);
        if (impactType == null) {
            throw INVALID_IMPACT_TYPE.create(rawValue);
        }
        return impactType;
    }

    private static String normalizeDisplayName(String rawDisplayName, String fallbackId) {
        String normalized = rawDisplayName == null ? "" : rawDisplayName.trim();
        return normalized.isBlank() ? (fallbackId == null ? "" : fallbackId.trim()) : normalized;
    }

    private static String normalizeEditField(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String unwrapQuotedValue(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private static int parseNonNegativeInteger(String field, String rawValue) throws CommandSyntaxException {
        try {
            return Math.max(0, Integer.parseInt(rawValue));
        } catch (NumberFormatException exception) {
            throw new SimpleCommandExceptionType(Component.literal("配置项 " + field + " 需要非负整数")).create();
        }
    }

    private static double parseFiniteDouble(String field, String rawValue) throws CommandSyntaxException {
        try {
            double value = Double.parseDouble(rawValue);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException(rawValue);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new SimpleCommandExceptionType(Component.literal("配置项 " + field + " 需要有效数字")).create();
        }
    }

    private static double parseNonNegativeDouble(String field, String rawValue) throws CommandSyntaxException {
        double value = parseFiniteDouble(field, rawValue);
        if (value < 0.0D) {
            throw new SimpleCommandExceptionType(Component.literal("配置项 " + field + " 不能小于 0")).create();
        }
        return value;
    }

    private static double parsePositiveOrZeroDouble(String field, String rawValue) throws CommandSyntaxException {
        return parseNonNegativeDouble(field, rawValue);
    }

    private static String resolveEntryDisplayName(ShellingDataModel.ShellingEntry entry) {
        if (entry == null) {
            return "";
        }
        if (!entry.getDisplayName().isBlank()) {
            return entry.getDisplayName();
        }
        return entry.getId();
    }

    private static String resolveGunDisplayName(CommandContext<CommandSourceStack> context, ItemStack held, ResourceLocation gunId) {
        String displayName = TaczDisplayNameResolver.resolve(context.getSource().getServer(), held);
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return gunId == null ? "" : gunId.toString();
    }

    private static String fallbackGunName(ShellingDataModel.ShellingEntry entry) {
        if (entry == null) {
            return "";
        }
        if (!entry.getGunDisplayId().isBlank()) {
            return entry.getGunDisplayId();
        }
        return entry.getGunId();
    }

    private static String normalizeShellingId(String raw) throws CommandSyntaxException {
        String normalized = CustomLoadoutService.normalizeEquipmentId(raw);
        if (!CustomLoadoutService.isValidEquipmentId(normalized)) {
            throw INVALID_ID.create(raw);
        }
        return normalized;
    }

    private static void persistShellings(CommandSourceStack source, ShellingDataStore store, ShellingDataModel data) {
        store.save(data);
        ConquestDataManager.of(source.getServer()).flushAllWrites();
    }
}
