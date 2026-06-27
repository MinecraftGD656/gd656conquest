package org.mods.gd656conquest.server.title;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.shared.text.ServerText;

import java.util.concurrent.CompletableFuture;

public final class PlayerTitleCommands {
    private PlayerTitleCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return buildLiteral("title");
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildLiteral(String literal) {
        return Commands.literal(literal)
            .then(Commands.literal("list").executes(PlayerTitleCommands::executeList))
            .then(Commands.literal("mylist").executes(PlayerTitleCommands::executeMyList))
            .then(Commands.literal("color").executes(PlayerTitleCommands::executeColor))
            .then(Commands.literal("add")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("titleId", StringArgumentType.word())
                    .then(Commands.argument("text", StringArgumentType.string())
                        .executes(PlayerTitleCommands::executeAdd))))
            .then(Commands.literal("edit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("titleId", StringArgumentType.word())
                    .suggests(PlayerTitleCommands::suggestTitleIds)
                    .then(Commands.argument("text", StringArgumentType.string())
                        .executes(PlayerTitleCommands::executeEdit))))
            .then(Commands.literal("give")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("titleId", StringArgumentType.word())
                    .suggests(PlayerTitleCommands::suggestTitleIds)
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PlayerTitleCommands::suggestKnownPlayerNames)
                        .executes(PlayerTitleCommands::executeGive))))
            .then(Commands.literal("remove")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(PlayerTitleCommands::suggestKnownPlayerNames)
                    .executes(PlayerTitleCommands::executeRemoveAll)
                    .then(Commands.argument("titleId", StringArgumentType.word())
                        .suggests(PlayerTitleCommands::suggestTitleIds)
                        .executes(PlayerTitleCommands::executeRemoveSingle))))
            .then(Commands.literal("import")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("fileName", StringArgumentType.string())
                    .suggests(PlayerTitleCommands::suggestImportFiles)
                    .then(Commands.argument("titleId", StringArgumentType.string())
                        .suggests(PlayerTitleCommands::suggestTitleIds)
                        .executes(PlayerTitleCommands::executeImport))))
            .then(Commands.literal("example")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(PlayerTitleCommands::executeExample)));
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        PlayerTitleService.of(context.getSource().getServer()).sendTitleList(context.getSource());
        return 1;
    }

    private static int executeMyList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerTitleService.of(context.getSource().getServer()).sendMyTitleList(player);
        return 1;
    }

    private static int executeColor(CommandContext<CommandSourceStack> context) {
        CommandFeedbacks.line(context.getSource(), Component.literal("========== 称号颜色字符 ==========").withStyle(ChatFormatting.GOLD));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&0", "黑色", ChatFormatting.BLACK));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&1", "深蓝", ChatFormatting.DARK_BLUE));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&2", "深绿", ChatFormatting.DARK_GREEN));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&3", "深青", ChatFormatting.DARK_AQUA));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&4", "深红", ChatFormatting.DARK_RED));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&5", "深紫", ChatFormatting.DARK_PURPLE));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&6", "金色", ChatFormatting.GOLD));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&7", "灰色", ChatFormatting.GRAY));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&8", "深灰", ChatFormatting.DARK_GRAY));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&9", "蓝色", ChatFormatting.BLUE));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&a", "绿色", ChatFormatting.GREEN));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&b", "青色", ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&c", "红色", ChatFormatting.RED));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&d", "粉色", ChatFormatting.LIGHT_PURPLE));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&e", "黄色", ChatFormatting.YELLOW));
        CommandFeedbacks.line(context.getSource(), buildColorLine("&f", "白色", ChatFormatting.WHITE));
        CommandFeedbacks.line(
            context.getSource(),
            Component.literal("格式: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("&k").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 混淆  ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("&l").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 粗体  ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("&m").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 删除线  ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("&n").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 下划线  ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("&o").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 斜体").withStyle(ChatFormatting.GRAY))
        );
        CommandFeedbacks.line(
            context.getSource(),
            Component.literal("重置: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("&r").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 恢复默认样式").withStyle(ChatFormatting.GRAY))
        );
        CommandFeedbacks.line(
            context.getSource(),
            Component.literal("Hex: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("/RRGGBB").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 设置16进制颜色").withStyle(ChatFormatting.GRAY))
        );
        CommandFeedbacks.line(
            context.getSource(),
            Component.literal("渐变: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("/GRA").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 使用整串首尾颜色做渐变").withStyle(ChatFormatting.GRAY))
        );
        CommandFeedbacks.line(
            context.getSource(),
            Component.literal("示例: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("\"/ff8800传说称号\"").withStyle(ChatFormatting.YELLOW))
        );
        CommandFeedbacks.line(
            context.getSource(),
            Component.literal("渐变示例: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("\"&c/GRA前线老兵&a\"").withStyle(ChatFormatting.YELLOW))
        );
        return CommandFeedbacks.info(context.getSource(), "已显示所有可用称号颜色字符");
    }

    private static int executeAdd(CommandContext<CommandSourceStack> context) {
        String titleId = StringArgumentType.getString(context, "titleId");
        String text = StringArgumentType.getString(context, "text");
        PlayerTitleService service = PlayerTitleService.of(context.getSource().getServer());
        boolean created = service.addTitleDefinition(titleId, text);
        return created
            ? CommandFeedbacks.info(context.getSource(), "已新增称号: " + PlayerTitleService.normalizeTitleId(titleId))
            : CommandFeedbacks.failure(context.getSource(), "称号已存在: " + PlayerTitleService.normalizeTitleId(titleId));
    }

    private static int executeEdit(CommandContext<CommandSourceStack> context) {
        String titleId = StringArgumentType.getString(context, "titleId");
        String text = StringArgumentType.getString(context, "text");
        PlayerTitleService service = PlayerTitleService.of(context.getSource().getServer());
        boolean updated = service.editTitleDefinition(titleId, text);
        return updated
            ? CommandFeedbacks.info(context.getSource(), "已更新称号: " + PlayerTitleService.normalizeTitleId(titleId))
            : CommandFeedbacks.failure(context.getSource(), "未找到称号: " + PlayerTitleService.normalizeTitleId(titleId));
    }

    private static int executeGive(CommandContext<CommandSourceStack> context) {
        PlayerTitleService.GrantResult result = PlayerTitleService.of(context.getSource().getServer()).giveTitle(
            StringArgumentType.getString(context, "titleId"),
            StringArgumentType.getString(context, "player"),
            context.getSource().getTextName()
        );
        return result.success()
            ? CommandFeedbacks.info(context.getSource(), result.message() + "，玩家=" + result.playerName() + "，称号=" + result.titleId())
            : CommandFeedbacks.failure(context.getSource(), result.message());
    }

    private static int executeRemoveAll(CommandContext<CommandSourceStack> context) {
        PlayerTitleService.RemoveResult result = PlayerTitleService.of(context.getSource().getServer()).removeAllTitles(
            StringArgumentType.getString(context, "player")
        );
        return result.success()
            ? CommandFeedbacks.info(context.getSource(), result.message() + "，玩家=" + result.playerName())
            : CommandFeedbacks.failure(context.getSource(), result.message());
    }

    private static int executeRemoveSingle(CommandContext<CommandSourceStack> context) {
        PlayerTitleService.RemoveResult result = PlayerTitleService.of(context.getSource().getServer()).removeSingleTitle(
            StringArgumentType.getString(context, "player"),
            StringArgumentType.getString(context, "titleId")
        );
        return result.success()
            ? CommandFeedbacks.info(context.getSource(), result.message() + "，玩家=" + result.playerName())
            : CommandFeedbacks.failure(context.getSource(), result.message());
    }

    private static int executeImport(CommandContext<CommandSourceStack> context) {
        PlayerTitleService.ImportResult result = PlayerTitleService.of(context.getSource().getServer()).importPlayersFromFile(
            StringArgumentType.getString(context, "fileName"),
            StringArgumentType.getString(context, "titleId"),
            context.getSource().getTextName()
        );
        if (!result.success()) {
            return CommandFeedbacks.failure(context.getSource(), result.message());
        }
        CommandFeedbacks.line(
            context.getSource(),
            ServerText.colored("导入文件: " + result.file().toAbsolutePath(), ChatFormatting.YELLOW)
        );
        if (!result.unresolvedPlayers().isEmpty()) {
            CommandFeedbacks.line(
                context.getSource(),
                ServerText.colored("未解析玩家: " + String.join(", ", result.unresolvedPlayers()), ChatFormatting.RED)
            );
        }
        return CommandFeedbacks.info(
            context.getSource(),
            result.message() + "，新增=" + result.grantedCount() + "，已拥有=" + result.skippedCount() + "，未解析=" + result.unresolvedPlayers().size()
        );
    }

    private static int executeExample(CommandContext<CommandSourceStack> context) {
        PlayerTitleService.ExampleResult result = PlayerTitleService.of(context.getSource().getServer()).writeExampleFile(
            StringArgumentType.getString(context, "name")
        );
        CommandFeedbacks.line(
            context.getSource(),
            ServerText.colored("示例文件: " + result.file().toAbsolutePath(), ChatFormatting.YELLOW)
        );
        return CommandFeedbacks.info(context.getSource(), "已生成称号导入示例文件，示例玩家数: " + result.suggestedPlayerCount());
    }

    private static CompletableFuture<Suggestions> suggestTitleIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(PlayerTitleService.of(context.getSource().getServer()).suggestTitleIds(), builder);
    }

    private static CompletableFuture<Suggestions> suggestKnownPlayerNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(PlayerTitleService.of(context.getSource().getServer()).suggestKnownPlayerNames(), builder);
    }

    private static CompletableFuture<Suggestions> suggestImportFiles(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(PlayerTitleService.of(context.getSource().getServer()).suggestImportFileNames(), builder);
    }

    private static MutableComponent buildColorLine(String code, String name, ChatFormatting format) {
        return Component.literal(code).withStyle(ChatFormatting.WHITE)
            .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(name).withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(name).withStyle(format));
    }
}
