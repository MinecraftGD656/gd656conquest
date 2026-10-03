package org.mods.gd656conquest.server.social.party.presentation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.shared.command.PlayerNameSuggestions;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class PartyCommands {
    private PartyCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("party")
            .executes(PartyActionController::executeHelp)
            .then(Commands.literal("help").executes(PartyActionController::executeHelp))
            .then(Commands.literal("create").executes(PartyActionController::executeCreate))
            .then(Commands.literal("leave").executes(PartyActionController::executeLeave))
            .then(Commands.literal("disband").executes(PartyActionController::executeDisband))
            .then(Commands.literal("info").executes(PartyActionController::executeInfo))
            .then(Commands.literal("list").executes(PartyActionController::executeList))
            .then(Commands.literal("invite")
                .then(Commands.argument("player", StringArgumentType.string())
                    .suggests(PlayerNameSuggestions::suggestKnownPlayers)
                    .executes(context -> PartyActionController.executeInvite(context, StringArgumentType.getString(context, "player")))))
            .then(Commands.literal("apply")
                .then(Commands.argument("partyId", StringArgumentType.string())
                    .suggests(PartyCommands::suggestPartyIds)
                    .executes(context -> PartyActionController.executeApply(context, StringArgumentType.getString(context, "partyId")))))
            .then(Commands.literal("accept")
                .then(Commands.argument("requestId", StringArgumentType.string())
                    .suggests(PartyCommands::suggestPendingRequestIds)
                    .executes(context -> PartyActionController.executeAccept(context, StringArgumentType.getString(context, "requestId")))))
            .then(Commands.literal("refuse")
                .then(Commands.argument("requestId", StringArgumentType.string())
                    .suggests(PartyCommands::suggestPendingRequestIds)
                    .executes(context -> PartyActionController.executeRefuse(context, StringArgumentType.getString(context, "requestId")))))
            .then(Commands.literal("pending").executes(PartyActionController::executePending))
            .then(Commands.literal("leave").executes(PartyActionController::executeLeave))
            .then(Commands.literal("leader")
                .then(Commands.literal("transfer")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(PartyCommands::suggestCurrentPartyMemberNames)
                        .executes(context -> PartyActionController.executeLeaderTransfer(context, StringArgumentType.getString(context, "player"))))));
    }

    private static CompletableFuture<Suggestions> suggestPartyIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(PartyUiFacade.of(context.getSource().getServer()).suggestPartyIds(), builder);
    }

    private static CompletableFuture<Suggestions> suggestPendingRequestIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return SharedSuggestionProvider.suggest(PartyUiFacade.of(context.getSource().getServer()).suggestPendingRequestIds(player), builder);
    }

    private static CompletableFuture<Suggestions> suggestCurrentPartyMemberNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> suggestions = PartyUiFacade.of(context.getSource().getServer())
            .findCurrentParty(player)
            .map(description -> description.members().stream().map(member -> member.displayName()).sorted(String.CASE_INSENSITIVE_ORDER).toList())
            .orElse(List.of());
        return PlayerNameSuggestions.suggest(suggestions, builder);
    }
}
