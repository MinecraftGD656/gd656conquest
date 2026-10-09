package org.mods.gd656conquest.server.facility.range;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;

public final class RangeCommands {
    private RangeCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("range")
            .then(Commands.literal("join").executes(RangeCommands::executeJoin))
            .then(Commands.literal("leave").executes(RangeCommands::executeLeave));
    }

    private static int executeJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String result = RangeRuntimeManager.of(context.getSource().getServer()).joinRange(player);
        QuickAccessItemService.refreshHotbar(player);
        return CommandFeedbacks.info(context.getSource(), result);
    }

    private static int executeLeave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String result = RangeRuntimeManager.of(context.getSource().getServer()).leaveRange(player);
        QuickAccessItemService.refreshHotbar(player);
        return CommandFeedbacks.info(context.getSource(), result);
    }
}
