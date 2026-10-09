package org.mods.gd656conquest.server.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.entry.RoomCommands;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.social.chat.ChatChannelNoticeService;
import org.mods.gd656conquest.server.social.party.presentation.PartyActionController;
import org.mods.gd656conquest.server.title.PlayerTitleService;

public final class ActionCommands {
    private ActionCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("action")
            .then(Commands.literal("acceptchat").executes(ActionCommands::executeAcceptChat))
            .then(Commands.literal("acceptcamp").executes(ActionCommands::executeAcceptCamp))
            .then(Commands.literal("joinroom")
                .then(Commands.argument("roomId", StringArgumentType.string())
                    .executes(ActionCommands::executeJoinRoom)))
            .then(Commands.literal("party")
                .then(Commands.literal("accept")
                    .then(Commands.argument("requestId", StringArgumentType.string())
                        .executes(context -> PartyActionController.executeAccept(context, StringArgumentType.getString(context, "requestId")))))
                .then(Commands.literal("refuse")
                    .then(Commands.argument("requestId", StringArgumentType.string())
                        .executes(context -> PartyActionController.executeRefuse(context, StringArgumentType.getString(context, "requestId"))))))
            .then(Commands.literal("weartitle")
                .then(Commands.argument("titleId", StringArgumentType.string())
                    .executes(ActionCommands::executeWearTitle)))
            .then(Commands.literal("cleartitle").executes(ActionCommands::executeClearTitle));
    }

    private static int executeAcceptChat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean changed = ChatChannelNoticeService.acknowledgeTeamChannelTip(player);
        return CommandFeedbacks.info(context.getSource(), changed ? "已记录，你将不再收到该阵营频道提醒" : "你已经确认过该阵营频道提醒");
    }

    private static int executeAcceptCamp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return RoomCommands.executeCurrentCampAccept(context.getSource());
    }

    private static int executeJoinRoom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return RoomCommands.executeJoinById(context.getSource(), StringArgumentType.getString(context, "roomId"));
    }

    private static int executeWearTitle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerTitleService.WearResult result = PlayerTitleService.of(context.getSource().getServer()).wearTitle(
            player,
            StringArgumentType.getString(context, "titleId")
        );
        PlayerTitleService.of(context.getSource().getServer()).sendMyTitleList(player);
        return result.success()
            ? CommandFeedbacks.info(context.getSource(), result.message())
            : CommandFeedbacks.failure(context.getSource(), result.message());
    }

    private static int executeClearTitle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerTitleService.WearResult result = PlayerTitleService.of(context.getSource().getServer()).clearEquippedTitle(player);
        PlayerTitleService.of(context.getSource().getServer()).sendMyTitleList(player);
        return result.success()
            ? CommandFeedbacks.info(context.getSource(), result.message())
            : CommandFeedbacks.failure(context.getSource(), result.message());
    }
}
