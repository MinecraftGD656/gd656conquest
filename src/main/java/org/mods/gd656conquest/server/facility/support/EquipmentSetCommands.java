package org.mods.gd656conquest.server.facility.support;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.equipmentset.EquipmentSetDataModel;
import org.mods.gd656conquest.server.data.equipmentset.EquipmentSetDataStore;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.shared.text.ServerText;
import org.mods.gd656conquest.server.shared.util.ItemStackSnbtCodec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class EquipmentSetCommands {
    private static final DynamicCommandExceptionType INVALID_ID = new DynamicCommandExceptionType(
        value -> net.minecraft.network.chat.Component.literal("非法军服 ID: " + value + "，仅支持字母、数字和下划线")
    );
    private static final DynamicCommandExceptionType EQUIPMENT_EXISTS = new DynamicCommandExceptionType(
        value -> net.minecraft.network.chat.Component.literal("军服已存在: " + value)
    );
    private static final DynamicCommandExceptionType EQUIPMENT_NOT_FOUND = new DynamicCommandExceptionType(
        value -> net.minecraft.network.chat.Component.literal("未找到军服: " + value)
    );
    private static final SimpleCommandExceptionType EMPTY_RESERVED = new SimpleCommandExceptionType(
        net.minecraft.network.chat.Component.literal("EMPTY 为系统保留占位 ID，不能删除")
    );

    private EquipmentSetCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("equipment")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("add")
                .then(Commands.argument("id", StringArgumentType.string())
                    .executes(EquipmentSetCommands::executeAdd)))
            .then(Commands.literal("del")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(EquipmentSetCommands::suggestIds)
                    .executes(EquipmentSetCommands::executeDelete)))
            .then(Commands.literal("list")
                .executes(EquipmentSetCommands::executeList))
            .then(Commands.literal("reload")
                .executes(EquipmentSetCommands::executeReload));
    }

    private static int executeAdd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = normalizeEquipmentId(StringArgumentType.getString(context, "id"));
        EquipmentSetDataStore store = ConquestDataManager.of(context.getSource().getServer()).equipmentSets();
        EquipmentSetDataModel data = store.get();
        if (data.getEntriesById().containsKey(id)) {
            throw EQUIPMENT_EXISTS.create(id);
        }
        data.getEntriesById().put(id, captureEntry(player, id));
        persist(context.getSource(), store, data);
        return CommandFeedbacks.info(context.getSource(), "已新增军服模板: " + id);
    }

    private static int executeDelete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = normalizeEquipmentId(StringArgumentType.getString(context, "id"));
        if (EquipmentSetDataModel.EMPTY_ID.equalsIgnoreCase(id)) {
            throw EMPTY_RESERVED.create();
        }
        EquipmentSetDataStore store = ConquestDataManager.of(context.getSource().getServer()).equipmentSets();
        EquipmentSetDataModel data = store.get();
        EquipmentSetDataModel.EquipmentSetEntry removed = data.getEntriesById().remove(id);
        if (removed == null) {
            throw EQUIPMENT_NOT_FOUND.create(id);
        }
        persist(context.getSource(), store, data);
        return CommandFeedbacks.info(context.getSource(), "已删除军服模板: " + id);
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        EquipmentSetDataModel data = ConquestDataManager.of(context.getSource().getServer()).equipmentSets().get();
        List<EquipmentSetDataModel.EquipmentSetEntry> entries = new ArrayList<>(data.getEntriesById().values());
        entries.removeIf(entry -> entry == null || entry.getId().isBlank());
        entries.sort(Comparator.comparing(EquipmentSetDataModel.EquipmentSetEntry::getId, String.CASE_INSENSITIVE_ORDER));
        if (entries.isEmpty()) {
            return CommandFeedbacks.info(context.getSource(), "当前没有任何军服模板");
        }
        CommandFeedbacks.line(context.getSource(), ServerText.colored("========== 军服列表 ==========", ChatFormatting.GOLD));
        for (EquipmentSetDataModel.EquipmentSetEntry entry : entries) {
            CommandFeedbacks.line(
                context.getSource(),
                ServerText.colored(
                    String.format(
                        Locale.ROOT,
                        "%s | 头=%s | 胸=%s | 腿=%s | 脚=%s",
                        entry.getId(),
                        describeSlot(entry.getHeadItemSnbt()),
                        describeSlot(entry.getChestItemSnbt()),
                        describeSlot(entry.getLegsItemSnbt()),
                        describeSlot(entry.getFeetItemSnbt())
                    ),
                    ChatFormatting.AQUA
                )
            );
        }
        return CommandFeedbacks.info(context.getSource(), "军服模板总数: " + entries.size());
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {
        EquipmentSetDataModel data = ConquestDataManager.of(context.getSource().getServer()).equipmentSets().reloadFromDisk();
        return CommandFeedbacks.info(context.getSource(), "已从磁盘重载军服数据，当前条目数: " + data.getEntriesById().size());
    }

    private static CompletableFuture<Suggestions> suggestIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        EquipmentSetDataModel data = ConquestDataManager.of(context.getSource().getServer()).equipmentSets().get();
        List<String> ids = new ArrayList<>(data.getEntriesById().keySet());
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static String normalizeEquipmentId(String raw) throws CommandSyntaxException {
        String normalized = CustomLoadoutService.normalizeEquipmentId(raw);
        if (!CustomLoadoutService.isValidEquipmentId(normalized)) {
            throw INVALID_ID.create(raw);
        }
        return normalized;
    }

    private static EquipmentSetDataModel.EquipmentSetEntry captureEntry(ServerPlayer player, String id) {
        EquipmentSetDataModel.EquipmentSetEntry entry = new EquipmentSetDataModel.EquipmentSetEntry();
        entry.setId(id);
        entry.setHeadItemSnbt(ItemStackSnbtCodec.serialize(player == null ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.HEAD)));
        entry.setChestItemSnbt(ItemStackSnbtCodec.serialize(player == null ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.CHEST)));
        entry.setLegsItemSnbt(ItemStackSnbtCodec.serialize(player == null ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.LEGS)));
        entry.setFeetItemSnbt(ItemStackSnbtCodec.serialize(player == null ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.FEET)));
        return entry;
    }

    private static String describeSlot(String itemSnbt) {
        ItemStack stack = ItemStackSnbtCodec.deserialize(itemSnbt);
        if (stack.isEmpty()) {
            return "-";
        }
        return stack.getHoverName().getString();
    }

    private static void persist(CommandSourceStack source, EquipmentSetDataStore store, EquipmentSetDataModel data) {
        store.save(data);
        ConquestDataManager.of(source.getServer()).flushAllWrites();
    }
}
