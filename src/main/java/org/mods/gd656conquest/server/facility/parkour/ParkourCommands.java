package org.mods.gd656conquest.server.facility.parkour;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;

import java.util.concurrent.CompletableFuture;

public final class ParkourCommands {
    private ParkourCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("parkour")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("del")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(ParkourCommands::suggestKnownPlayers)
                    .executes(ParkourCommands::executeDeleteRecord)));
    }

    private static int executeDeleteRecord(CommandContext<CommandSourceStack> context) {
        String playerName = StringArgumentType.getString(context, "player");
        ParkourRuntimeManager.RemoveRecordResult result = ParkourRuntimeManager.of(context.getSource().getServer()).removeRecord(playerName);
        return result.success()
            ? CommandFeedbacks.info(context.getSource(), result.message() + "，玩家=" + result.playerName())
            : CommandFeedbacks.failure(context.getSource(), result.message());
    }

    private static CompletableFuture<Suggestions> suggestKnownPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ParkourRuntimeManager.of(context.getSource().getServer()).suggestKnownPlayers(), builder);
    }
}
