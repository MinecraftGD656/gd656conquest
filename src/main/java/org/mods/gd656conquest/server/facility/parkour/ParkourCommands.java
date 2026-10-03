package org.mods.gd656conquest.server.facility.parkour;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.mods.gd656conquest.server.shared.command.PlayerNameSuggestions;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;

public final class ParkourCommands {
    private ParkourCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("parkour")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("del")
                .then(Commands.argument("player", StringArgumentType.string())
                    .suggests(PlayerNameSuggestions::suggestKnownPlayers)
                    .executes(ParkourCommands::executeDeleteRecord)));
    }

    private static int executeDeleteRecord(CommandContext<CommandSourceStack> context) {
        String playerName = StringArgumentType.getString(context, "player");
        ParkourRuntimeManager.RemoveRecordResult result = ParkourRuntimeManager.of(context.getSource().getServer()).removeRecord(playerName);
        return result.success()
            ? CommandFeedbacks.info(context.getSource(), result.message() + "，玩家=" + result.playerName())
            : CommandFeedbacks.failure(context.getSource(), result.message());
    }
}
