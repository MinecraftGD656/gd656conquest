package org.mods.gd656conquest.server.facility.support;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.herokit.HeroKitDataModel;
import org.mods.gd656conquest.server.data.herokit.HeroKitDataStore;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.shared.text.ServerText;
import org.mods.gd656conquest.server.shared.util.ItemStackSnbtCodec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class HeroKitCommands {
    private static final List<String> HERO_KIT_EDIT_FIELDS = List.of(
        "displayName",
        "cost",
        "type",
        "cooldownSeconds"
    );
    private static final List<String> HERO_KIT_TYPES = List.of(
        "antivehicle",
        "flametrooper",
        "runner",
        "sentry",
        "trenchraider"
    );
    private static final DynamicCommandExceptionType INVALID_ID = new DynamicCommandExceptionType(
        value -> Component.literal("非法精英兵 ID: " + value + "，仅支持字母、数字和下划线")
    );
    private static final DynamicCommandExceptionType HERO_KIT_EXISTS = new DynamicCommandExceptionType(
        value -> Component.literal("精英兵已存在: " + value)
    );
    private static final DynamicCommandExceptionType HERO_KIT_NOT_FOUND = new DynamicCommandExceptionType(
        value -> Component.literal("未找到精英兵: " + value)
    );
    private static final DynamicCommandExceptionType INVALID_TYPE = new DynamicCommandExceptionType(
        value -> Component.literal("非法精英兵类型: " + value)
    );
    private static final DynamicCommandExceptionType INVALID_FIELD = new DynamicCommandExceptionType(
        value -> Component.literal("不支持修改的精英兵配置项: " + value)
    );
    private static final SimpleCommandExceptionType EMPTY_DISPLAY_NAME = new SimpleCommandExceptionType(
        Component.literal("显示名称不能为空")
    );

    private HeroKitCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("herokits")
            .requires(source -> source.hasPermission(2))
            .then(buildAddTree())
            .then(Commands.literal("edit")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(HeroKitCommands::suggestIds)
                    .executes(HeroKitCommands::executeEdit)))
            .then(Commands.literal("del")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(HeroKitCommands::suggestIds)
                    .executes(HeroKitCommands::executeDelete)))
            .then(Commands.literal("list")
                .executes(HeroKitCommands::executeList))
            .then(Commands.literal("reload")
                .executes(HeroKitCommands::executeReload))
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests(HeroKitCommands::suggestIds)
                .then(Commands.argument("field", StringArgumentType.string())
                    .suggests(HeroKitCommands::suggestEditFields)
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(HeroKitCommands::suggestEditValues)
                        .executes(HeroKitCommands::executeSetField))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildAddTree() {
        return Commands.literal("add")
            .then(Commands.argument("id", StringArgumentType.string())
                .then(Commands.argument("displayName", StringArgumentType.string())
                    .then(Commands.argument("cost", IntegerArgumentType.integer(0))
                        .then(Commands.argument("type", StringArgumentType.string())
                            .suggests(HeroKitCommands::suggestTypes)
                                .then(Commands.argument("cooldownSeconds", IntegerArgumentType.integer(0))
                                    .executes(HeroKitCommands::executeAdd))))));
    }

    private static int executeAdd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = normalizeHeroKitId(StringArgumentType.getString(context, "id"));
        String displayName = normalizeDisplayName(StringArgumentType.getString(context, "displayName"));
        int cost = IntegerArgumentType.getInteger(context, "cost");
        String type = normalizeHeroKitType(StringArgumentType.getString(context, "type"));
        int cooldownSeconds = IntegerArgumentType.getInteger(context, "cooldownSeconds");
        HeroKitDataStore store = ConquestDataManager.of(context.getSource().getServer()).heroKits();
        HeroKitDataModel data = store.get();
        if (data.getHeroKitsById().containsKey(id)) {
            throw HERO_KIT_EXISTS.create(id);
        }
        data.getHeroKitsById().put(id, captureHeroKitEntry(player, id, displayName, cost, type, cooldownSeconds));
        persistHeroKits(context.getSource(), store, data);
        return CommandFeedbacks.info(
            context.getSource(),
            String.format(
                Locale.ROOT,
                "已新增精英兵 %s，名称=%s，分数=%d，类型=%s，冷却=%ds",
                id,
                displayName,
                cost,
                type,
                cooldownSeconds
            )
        );
    }

    private static int executeEdit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = normalizeHeroKitId(StringArgumentType.getString(context, "id"));
        HeroKitDataStore store = ConquestDataManager.of(context.getSource().getServer()).heroKits();
        HeroKitDataModel data = store.get();
        HeroKitDataModel.HeroKitEntry existing = data.getHeroKitsById().get(id);
        if (existing == null) {
            throw HERO_KIT_NOT_FOUND.create(id);
        }
        data.getHeroKitsById().put(
            id,
            captureHeroKitEntry(
                player,
                id,
                existing.getDisplayName(),
                existing.getCost(),
                existing.getType(),
                existing.getCooldownSeconds()
            )
        );
        persistHeroKits(context.getSource(), store, data);
        return CommandFeedbacks.info(context.getSource(), "已用当前玩家完整背包覆盖精英兵: " + id);
    }

    private static int executeDelete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = normalizeHeroKitId(StringArgumentType.getString(context, "id"));
        HeroKitDataStore store = ConquestDataManager.of(context.getSource().getServer()).heroKits();
        HeroKitDataModel data = store.get();
        HeroKitDataModel.HeroKitEntry removed = data.getHeroKitsById().remove(id);
        if (removed == null) {
            throw HERO_KIT_NOT_FOUND.create(id);
        }
        persistHeroKits(context.getSource(), store, data);
        return CommandFeedbacks.info(context.getSource(), "已删除精英兵: " + id);
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        HeroKitDataModel data = ConquestDataManager.of(context.getSource().getServer()).heroKits().get();
        List<HeroKitDataModel.HeroKitEntry> entries = new ArrayList<>(data.getHeroKitsById().values());
        entries.removeIf(entry -> entry == null || entry.getId().isBlank());
        entries.sort(Comparator.comparing(HeroKitDataModel.HeroKitEntry::getId, String.CASE_INSENSITIVE_ORDER));
        if (entries.isEmpty()) {
            return CommandFeedbacks.info(context.getSource(), "当前没有任何精英兵模板");
        }
        CommandFeedbacks.line(context.getSource(), ServerText.colored("========== 精英兵列表 ==========", ChatFormatting.GOLD));
        for (HeroKitDataModel.HeroKitEntry entry : entries) {
            CommandFeedbacks.line(
                context.getSource(),
                ServerText.colored(
                    String.format(
                        Locale.ROOT,
                        "%s | 名称=%s | 分数=%d | 类型=%s | 冷却=%ds",
                        entry.getId(),
                        entry.getDisplayName().isBlank() ? "<empty>" : entry.getDisplayName(),
                        entry.getCost(),
                        entry.getType().isBlank() ? "<empty>" : entry.getType(),
                        entry.getCooldownSeconds()
                    ),
                    ChatFormatting.AQUA
                )
            );
        }
        return CommandFeedbacks.info(context.getSource(), "精英兵总数: " + entries.size());
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {
        HeroKitDataModel data = ConquestDataManager.of(context.getSource().getServer()).heroKits().reloadFromDisk();
        return CommandFeedbacks.info(context.getSource(), "已从磁盘重载精英兵数据，当前条目数: " + data.getHeroKitsById().size());
    }

    private static int executeSetField(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = normalizeHeroKitId(StringArgumentType.getString(context, "id"));
        String field = normalizeEditField(StringArgumentType.getString(context, "field"));
        String rawValue = unwrapQuotedValue(StringArgumentType.getString(context, "value"));
        HeroKitDataStore store = ConquestDataManager.of(context.getSource().getServer()).heroKits();
        HeroKitDataModel data = store.get();
        HeroKitDataModel.HeroKitEntry entry = data.getHeroKitsById().get(id);
        if (entry == null) {
            throw HERO_KIT_NOT_FOUND.create(id);
        }
        switch (field) {
            case "displayname" -> entry.setDisplayName(normalizeDisplayName(rawValue));
            case "cost" -> entry.setCost(parseNonNegativeInteger(field, rawValue));
            case "type" -> entry.setType(normalizeHeroKitType(rawValue));
            case "cooldownseconds" -> entry.setCooldownSeconds(parseNonNegativeInteger(field, rawValue));
            default -> throw INVALID_FIELD.create(field);
        }
        persistHeroKits(context.getSource(), store, data);
        return CommandFeedbacks.info(
            context.getSource(),
            String.format(Locale.ROOT, "已更新精英兵 %s 的 %s = %s", id, field, rawValue)
        );
    }

    private static CompletableFuture<Suggestions> suggestIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        HeroKitDataModel data = ConquestDataManager.of(context.getSource().getServer()).heroKits().get();
        List<String> ids = new ArrayList<>(data.getHeroKitsById().keySet());
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static CompletableFuture<Suggestions> suggestTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(HERO_KIT_TYPES, builder);
    }

    private static CompletableFuture<Suggestions> suggestEditFields(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(HERO_KIT_EDIT_FIELDS, builder);
    }

    private static CompletableFuture<Suggestions> suggestEditValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String field = normalizeEditField(StringArgumentType.getString(context, "field"));
        if ("type".equals(field)) {
            return SharedSuggestionProvider.suggest(HERO_KIT_TYPES, builder);
        }
        return Suggestions.empty();
    }

    private static String normalizeHeroKitId(String raw) throws CommandSyntaxException {
        String normalized = CustomLoadoutService.normalizeEquipmentId(raw);
        if (!CustomLoadoutService.isValidEquipmentId(normalized)) {
            throw INVALID_ID.create(raw);
        }
        return normalized;
    }

    private static String normalizeDisplayName(String raw) throws CommandSyntaxException {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isBlank()) {
            throw EMPTY_DISPLAY_NAME.create();
        }
        return normalized;
    }

    private static String normalizeHeroKitType(String raw) throws CommandSyntaxException {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!HERO_KIT_TYPES.contains(normalized)) {
            throw INVALID_TYPE.create(raw);
        }
        return normalized;
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

    private static HeroKitDataModel.HeroKitEntry captureHeroKitEntry(
        ServerPlayer player,
        String id,
        String displayName,
        int cost,
        String type,
        int cooldownSeconds
    ) {
        HeroKitDataModel.HeroKitEntry entry = new HeroKitDataModel.HeroKitEntry();
        entry.setId(id);
        entry.setDisplayName(displayName);
        entry.setCost(cost);
        entry.setType(type);
        entry.setCooldownSeconds(cooldownSeconds);
        entry.setHotbarBySlot(captureHotbar(player));
        entry.setInventoryBySlot(captureInventory(player));
        entry.setOffhandItemSnbt(ItemStackSnbtCodec.serialize(player == null ? ItemStack.EMPTY : player.getOffhandItem()));
        HeroKitDataModel.ArmorLoadout armor = new HeroKitDataModel.ArmorLoadout();
        armor.setHeadItemSnbt(ItemStackSnbtCodec.serialize(player == null ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.HEAD)));
        armor.setChestItemSnbt(ItemStackSnbtCodec.serialize(player == null ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.CHEST)));
        armor.setLegsItemSnbt(ItemStackSnbtCodec.serialize(player == null ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.LEGS)));
        armor.setFeetItemSnbt(ItemStackSnbtCodec.serialize(player == null ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.FEET)));
        entry.setArmor(armor);
        return entry;
    }

    private static Map<String, String> captureHotbar(ServerPlayer player) {
        Map<String, String> result = new LinkedHashMap<>();
        if (player == null) {
            return result;
        }
        for (int slot = 0; slot < 9; slot++) {
            result.put(String.valueOf(slot), ItemStackSnbtCodec.serialize(player.getInventory().items.get(slot)));
        }
        return result;
    }

    private static Map<String, String> captureInventory(ServerPlayer player) {
        Map<String, String> result = new LinkedHashMap<>();
        if (player == null) {
            return result;
        }
        for (int slot = 9; slot < 36; slot++) {
            result.put(String.valueOf(slot - 9), ItemStackSnbtCodec.serialize(player.getInventory().items.get(slot)));
        }
        return result;
    }

    private static void persistHeroKits(
        CommandSourceStack source,
        HeroKitDataStore store,
        HeroKitDataModel data
    ) {
        store.save(data);
        ConquestDataManager.of(source.getServer()).flushAllWrites();
    }
}
