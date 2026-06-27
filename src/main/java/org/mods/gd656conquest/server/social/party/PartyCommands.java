package org.mods.gd656conquest.server.social.party;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.shared.contract.ClickActionSpec;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.shared.text.ServerText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class PartyCommands {
    private PartyCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return buildBody(Commands.literal("party"));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildBody(com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root) {
        return root
            .then(Commands.literal("create").executes(PartyCommands::executeCreate))
            .then(Commands.literal("join")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(PartyCommands::suggestPartyIds)
                    .executes(PartyCommands::executeJoin)))
            .then(Commands.literal("invite")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(PartyCommands::suggestOnlinePlayers)
                    .executes(PartyCommands::executeInvite)))
            .then(Commands.literal("leave").executes(PartyCommands::executeLeave))
            .then(Commands.literal("kick")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(PartyCommands::suggestCurrentPartyOthers)
                    .executes(PartyCommands::executeKick)
                    .then(Commands.argument("time", IntegerArgumentType.integer(1))
                        .executes(PartyCommands::executeKickWithTime))))
            .then(Commands.literal("accept")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(PartyCommands::suggestOnlinePlayers)
                    .executes(PartyCommands::executeAccept)))
            .then(Commands.literal("refuse")
                .then(Commands.literal("clear").executes(PartyCommands::executeRefuseClear))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(PartyCommands::suggestOnlinePlayers)
                    .executes(PartyCommands::executeRefuse)
                    .then(Commands.argument("time", IntegerArgumentType.integer(1))
                        .executes(PartyCommands::executeRefuseWithTime))))
            .then(Commands.literal("leader").executes(PartyCommands::executeLeaderApply))
            .then(Commands.literal("list").executes(PartyCommands::executeList))
            .then(Commands.literal("teamlist").executes(PartyCommands::executeTeamList))
            .then(Commands.literal("msg")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(PartyCommands::executeMsg)))
            .then(Commands.literal("disband")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(PartyCommands::suggestPartyIds)
                    .executes(PartyCommands::executeAdminDisband)));
    }

    private static int executeCreate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return replyInfo(context.getSource(), manager(context).createParty(context.getSource().getPlayerOrException()));
    }

    private static int executeJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return replyInfo(context.getSource(), manager(context).requestJoin(
            context.getSource().getPlayerOrException(),
            StringArgumentType.getString(context, "id")
        ));
    }

    private static int executeInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer leader = context.getSource().getPlayerOrException();
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(context, "player"));
        if (target == null) {
            return replyFailure(context.getSource(), "目标玩家不在线");
        }
        return replyInfo(context.getSource(), manager(context).invite(leader, target));
    }

    private static int executeLeave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return replyInfo(context.getSource(), manager(context).leave(context.getSource().getPlayerOrException()));
    }

    private static int executeKick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeKick0(context, 0);
    }

    private static int executeKickWithTime(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeKick0(context, IntegerArgumentType.getInteger(context, "time"));
    }

    private static int executeKick0(CommandContext<CommandSourceStack> context, int seconds) throws CommandSyntaxException {
        return replyInfo(context.getSource(), manager(context).kick(
            context.getSource().getPlayerOrException(),
            StringArgumentType.getString(context, "player"),
            seconds
        ));
    }

    private static int executeAccept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return replyInfo(context.getSource(), manager(context).accept(
            context.getSource().getPlayerOrException(),
            StringArgumentType.getString(context, "player")
        ));
    }

    private static int executeRefuse(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeRefuse0(context, 0);
    }

    private static int executeRefuseWithTime(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeRefuse0(context, IntegerArgumentType.getInteger(context, "time"));
    }

    private static int executeRefuse0(CommandContext<CommandSourceStack> context, int seconds) throws CommandSyntaxException {
        return replyInfo(context.getSource(), manager(context).refuse(
            context.getSource().getPlayerOrException(),
            StringArgumentType.getString(context, "player"),
            seconds
        ));
    }

    private static int executeRefuseClear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return replyInfo(context.getSource(), manager(context).clearAutoRefuse(context.getSource().getPlayerOrException()));
    }

    private static int executeLeaderApply(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return replyInfo(context.getSource(), manager(context).requestLeaderTransfer(context.getSource().getPlayerOrException()));
    }

    private static int executeList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<String> lines = manager(context).describeCurrentParty(context.getSource().getPlayerOrException());
        for (String line : lines) {
            CommandFeedbacks.line(context.getSource(), ServerText.colored(line, ChatFormatting.AQUA));
        }
        return 1;
    }

    private static int executeTeamList(CommandContext<CommandSourceStack> context) {
        PlayerPartyManager manager = manager(context);
        List<String> lines = manager.describeAllParties();
        ServerPlayer viewer = context.getSource().getPlayer();
        if (viewer != null && !lines.isEmpty() && !lines.get(0).contains("没有任何队伍")) {
            for (String line : lines) {
                String partyId = line.split("\\|")[0].trim();
                viewer.sendSystemMessage(ServerText.colored(line + " ", ChatFormatting.GRAY)
                    .append(ServerText.action(new ClickActionSpec("[申请]", ChatFormatting.GREEN, "/gdc action joinparty " + partyId, "申请加入队伍 " + partyId))));
            }
            return 1;
        }
        for (String line : lines) {
            CommandFeedbacks.line(context.getSource(), ServerText.colored(line, ChatFormatting.AQUA));
        }
        return 1;
    }

    private static int executeMsg(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        manager(context).sendPartyChat(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "message"));
        return 1;
    }

    private static int executeAdminDisband(CommandContext<CommandSourceStack> context) {
        return replyInfo(context.getSource(), manager(context).disbandByAdmin(StringArgumentType.getString(context, "id")));
    }

    private static CompletableFuture<Suggestions> suggestPartyIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> ids = manager(context).getAllPartyIds();
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> names = new ArrayList<>();
        context.getSource().getServer().getPlayerList().getPlayers().forEach(player -> names.add(player.getScoreboardName()));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private static CompletableFuture<Suggestions> suggestCurrentPartyOthers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        ServerPlayer self = context.getSource().getPlayerOrException();
        return SharedSuggestionProvider.suggest(manager(context).suggestPartyMemberNames(self.getUUID(), false), builder);
    }

    private static PlayerPartyManager manager(CommandContext<CommandSourceStack> context) {
        return PlayerPartyManager.of(context.getSource().getServer());
    }

    private static int replyInfo(CommandSourceStack source, String message) {
        return CommandFeedbacks.info(source, message);
    }

    private static int replyFailure(CommandSourceStack source, String message) {
        return CommandFeedbacks.failure(source, message);
    }
}
