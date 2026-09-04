package org.mods.gd656conquest.server.capture;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.CaptureBoundaryDebugScanPacket;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CaptureBoundaryBakeCommands {
    private CaptureBoundaryBakeCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("bake")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("scan")
                .then(Commands.argument("mode", IntegerArgumentType.integer(0, 2))
                    .then(Commands.argument("roomId", StringArgumentType.string())
                        .suggests(CaptureBoundaryBakeCommands::suggestRoomIds)
                        .then(Commands.argument("radiusOrIgnored", DoubleArgumentType.doubleArg(0.0D))
                            .then(Commands.argument("minY", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("maxY", DoubleArgumentType.doubleArg())
                                    .executes(CaptureBoundaryBakeCommands::executeScan)))))))
            .then(Commands.literal("list").executes(CaptureBoundaryBakeCommands::executeList))
            .then(Commands.literal("copy")
                .then(Commands.argument("fromRoomId", StringArgumentType.string())
                    .suggests(CaptureBoundaryBakeCommands::suggestRoomIds)
                    .then(Commands.argument("toRoomId", StringArgumentType.string())
                        .suggests(CaptureBoundaryBakeCommands::suggestRoomIds)
                        .executes(CaptureBoundaryBakeCommands::executeCopy))))
            .then(Commands.literal("push")
                .then(Commands.argument("roomId", StringArgumentType.string())
                    .suggests(CaptureBoundaryBakeCommands::suggestRoomIds)
                    .executes(CaptureBoundaryBakeCommands::executePush)));
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildDebugScanTree() {
        return Commands.literal("scan")
            .then(Commands.literal("on").executes(context -> executeDebugScan(context, true)))
            .then(Commands.literal("off").executes(context -> executeDebugScan(context, false)));
    }

    private static int executeScan(CommandContext<CommandSourceStack> context) {
        int mode = IntegerArgumentType.getInteger(context, "mode");
        String roomId = StringArgumentType.getString(context, "roomId");
        double radius = DoubleArgumentType.getDouble(context, "radiusOrIgnored");
        double minY = DoubleArgumentType.getDouble(context, "minY");
        double maxY = DoubleArgumentType.getDouble(context, "maxY");
        CaptureBoundaryBakeService.StartScanResult result = CaptureBoundaryBakeService.of(context.getSource().getServer())
            .startScan(context.getSource(), mode, roomId, radius, minY, maxY);
        return result.success() ? CommandFeedbacks.info(context.getSource(), result.message()) : CommandFeedbacks.failure(context.getSource(), result.message());
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        List<CaptureBoundaryBakeService.BakeFileSummary> summaries = CaptureBoundaryBakeService.of(context.getSource().getServer()).listFiles();
        if (summaries.isEmpty()) {
            return CommandFeedbacks.info(context.getSource(), "当前没有任何 bake 文件");
        }
        for (CaptureBoundaryBakeService.BakeFileSummary summary : summaries) {
            CommandFeedbacks.line(context.getSource(), net.minecraft.network.chat.Component.literal(
                summary.roomId()
                    + " size=" + summary.fileSizeBytes() + "B"
                    + " version=" + summary.version()
                    + " blocks=" + summary.totalBlocks()
                    + (summary.countsLine() == null || summary.countsLine().isBlank() ? "" : " counts=" + summary.countsLine())
            ));
        }
        return summaries.size();
    }

    private static int executeCopy(CommandContext<CommandSourceStack> context) {
        String fromRoomId = StringArgumentType.getString(context, "fromRoomId");
        String toRoomId = StringArgumentType.getString(context, "toRoomId");
        CaptureBoundaryBakeService.CopyResult result = CaptureBoundaryBakeService.of(context.getSource().getServer()).copy(fromRoomId, toRoomId);
        return result.success() ? CommandFeedbacks.info(context.getSource(), result.message()) : CommandFeedbacks.failure(context.getSource(), result.message());
    }

    private static int executePush(CommandContext<CommandSourceStack> context) {
        String roomId = StringArgumentType.getString(context, "roomId");
        CaptureBoundaryBakeService.PushResult result = CaptureBoundaryBakeService.of(context.getSource().getServer()).push(roomId);
        return result.success() ? CommandFeedbacks.info(context.getSource(), result.message()) : CommandFeedbacks.failure(context.getSource(), result.message());
    }

    private static int executeDebugScan(CommandContext<CommandSourceStack> context, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NetworkManager.sendToPlayer(player, new CaptureBoundaryDebugScanPacket(enabled));
        return CommandFeedbacks.info(context.getSource(), enabled ? "调试方块扫描已开启" : "调试方块扫描已关闭");
    }

    private static CompletableFuture<Suggestions> suggestRoomIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> roomIds = new ArrayList<>();
        for (int i = 1; i <= 32; i++) {
            roomIds.add(String.format("Room_%02d", i));
        }
        return SharedSuggestionProvider.suggest(roomIds, builder);
    }
}
