package org.mods.gd656conquest.server.room.entry;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.vehicledata.VehicleConfigEntry;
import org.mods.gd656conquest.server.data.vehicledata.VehicleConfigRepository;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class VehicleRoomCommands {
    private static final String ROOM_SERVICE_NOT_INITIALIZED = "房间服务尚未初始化";

    private VehicleRoomCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("vehicle")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("list")
                .then(Commands.argument("gameplay", StringArgumentType.string())
                    .suggests(VehicleRoomCommands::suggestGameplayIds)
                    .executes(VehicleRoomCommands::executeList)))
            .then(Commands.literal("add")
                .then(buildAddConquestTree())
                .then(buildAddBreakthroughTree())
                .then(buildAddDeathmatchTree())
                .then(buildAddGunmasterTree()))
            .then(Commands.literal("remove")
                .then(Commands.argument("gameplay", StringArgumentType.string())
                    .suggests(VehicleRoomCommands::suggestGameplayIds)
                    .then(Commands.argument("entryId", StringArgumentType.string())
                        .suggests(VehicleRoomCommands::suggestConfiguredEntryIds)
                        .executes(VehicleRoomCommands::executeRemove))))
            .then(Commands.literal("resync")
                .then(Commands.argument("gameplay", StringArgumentType.string())
                    .suggests(VehicleRoomCommands::suggestGameplayIds)
                    .executes(VehicleRoomCommands::executeResync)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildAddConquestTree() {
        RequiredArgumentBuilder<CommandSourceStack, Integer> teamWideDeployEnabled = Commands.argument(
            "teamWideDeployEnabled",
            IntegerArgumentType.integer(0, 1)
        );
        teamWideDeployEnabled.executes(context -> executeAdd(
            context,
            RoomGameplayId.CONQUEST,
            null,
            IntegerArgumentType.getInteger(context, "requiredTeamSize"),
            IntegerArgumentType.getInteger(context, "teamWideDeployEnabled") == 1,
            null
        ));
        teamWideDeployEnabled.then(Commands.argument("displayId", StringArgumentType.string())
            .executes(context -> executeAdd(
                context,
                RoomGameplayId.CONQUEST,
                null,
                IntegerArgumentType.getInteger(context, "requiredTeamSize"),
                IntegerArgumentType.getInteger(context, "teamWideDeployEnabled") == 1,
                StringArgumentType.getString(context, "displayId")
            )));

        RequiredArgumentBuilder<CommandSourceStack, Integer> requiredTeamSize = Commands.argument(
            "requiredTeamSize",
            IntegerArgumentType.integer(0)
        );
        requiredTeamSize.then(teamWideDeployEnabled);

        RequiredArgumentBuilder<CommandSourceStack, String> team = Commands.argument("team", StringArgumentType.string())
            .suggests(VehicleRoomCommands::suggestTeamIds);
        team.then(requiredTeamSize);

        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> vehicleId = Commands.argument(
            "vehicleId",
            ResourceLocationArgument.id()
        ).suggests(VehicleRoomCommands::suggestVehicleIds);
        vehicleId.then(team);

        RequiredArgumentBuilder<CommandSourceStack, Integer> respawnSeconds = Commands.argument(
            "respawnSeconds",
            IntegerArgumentType.integer(-1)
        );
        respawnSeconds.then(vehicleId);

        RequiredArgumentBuilder<CommandSourceStack, String> entryId = Commands.argument("entryId", StringArgumentType.string());
        entryId.then(respawnSeconds);

        return Commands.literal("conquest").then(entryId);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildAddBreakthroughTree() {
        RequiredArgumentBuilder<CommandSourceStack, Integer> teamWideDeployEnabled = Commands.argument(
            "teamWideDeployEnabled",
            IntegerArgumentType.integer(0, 1)
        );
        teamWideDeployEnabled.executes(context -> executeAdd(
            context,
            RoomGameplayId.BREAKTHROUGH,
            IntegerArgumentType.getInteger(context, "stageIndex"),
            IntegerArgumentType.getInteger(context, "requiredTeamSize"),
            IntegerArgumentType.getInteger(context, "teamWideDeployEnabled") == 1,
            null
        ));
        teamWideDeployEnabled.then(Commands.argument("displayId", StringArgumentType.string())
            .executes(context -> executeAdd(
                context,
                RoomGameplayId.BREAKTHROUGH,
                IntegerArgumentType.getInteger(context, "stageIndex"),
                IntegerArgumentType.getInteger(context, "requiredTeamSize"),
                IntegerArgumentType.getInteger(context, "teamWideDeployEnabled") == 1,
                StringArgumentType.getString(context, "displayId")
            )));

        RequiredArgumentBuilder<CommandSourceStack, Integer> requiredTeamSize = Commands.argument(
            "requiredTeamSize",
            IntegerArgumentType.integer(0)
        );
        requiredTeamSize.then(teamWideDeployEnabled);

        RequiredArgumentBuilder<CommandSourceStack, Integer> stageIndex = Commands.argument(
            "stageIndex",
            IntegerArgumentType.integer(1)
        );
        stageIndex.then(requiredTeamSize);

        RequiredArgumentBuilder<CommandSourceStack, String> team = Commands.argument("team", StringArgumentType.string())
            .suggests(VehicleRoomCommands::suggestTeamIds);
        team.then(stageIndex);

        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> vehicleId = Commands.argument(
            "vehicleId",
            ResourceLocationArgument.id()
        ).suggests(VehicleRoomCommands::suggestVehicleIds);
        vehicleId.then(team);

        RequiredArgumentBuilder<CommandSourceStack, Integer> respawnSeconds = Commands.argument(
            "respawnSeconds",
            IntegerArgumentType.integer(-1)
        );
        respawnSeconds.then(vehicleId);

        RequiredArgumentBuilder<CommandSourceStack, String> entryId = Commands.argument("entryId", StringArgumentType.string());
        entryId.then(respawnSeconds);

        return Commands.literal("breakthrough").then(entryId);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildAddDeathmatchTree() {
        RequiredArgumentBuilder<CommandSourceStack, Integer> teamWideDeployEnabled = Commands.argument(
            "teamWideDeployEnabled",
            IntegerArgumentType.integer(0, 1)
        );
        teamWideDeployEnabled.executes(context -> executeAdd(
            context,
            RoomGameplayId.DEATHMATCH,
            null,
            IntegerArgumentType.getInteger(context, "requiredTeamSize"),
            IntegerArgumentType.getInteger(context, "teamWideDeployEnabled") == 1,
            null
        ));
        teamWideDeployEnabled.then(Commands.argument("displayId", StringArgumentType.string())
            .executes(context -> executeAdd(
                context,
                RoomGameplayId.DEATHMATCH,
                null,
                IntegerArgumentType.getInteger(context, "requiredTeamSize"),
                IntegerArgumentType.getInteger(context, "teamWideDeployEnabled") == 1,
                StringArgumentType.getString(context, "displayId")
            )));

        RequiredArgumentBuilder<CommandSourceStack, Integer> requiredTeamSize = Commands.argument(
            "requiredTeamSize",
            IntegerArgumentType.integer(0)
        );
        requiredTeamSize.then(teamWideDeployEnabled);

        RequiredArgumentBuilder<CommandSourceStack, String> team = Commands.argument("team", StringArgumentType.string())
            .suggests(VehicleRoomCommands::suggestTeamIds);
        team.then(requiredTeamSize);

        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> vehicleId = Commands.argument(
            "vehicleId",
            ResourceLocationArgument.id()
        ).suggests(VehicleRoomCommands::suggestVehicleIds);
        vehicleId.then(team);

        RequiredArgumentBuilder<CommandSourceStack, Integer> respawnSeconds = Commands.argument(
            "respawnSeconds",
            IntegerArgumentType.integer(-1)
        );
        respawnSeconds.then(vehicleId);

        RequiredArgumentBuilder<CommandSourceStack, String> entryId = Commands.argument("entryId", StringArgumentType.string());
        entryId.then(respawnSeconds);

        return Commands.literal("deathmatch").then(entryId);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildAddGunmasterTree() {
        RequiredArgumentBuilder<CommandSourceStack, Integer> teamWideDeployEnabled = Commands.argument(
            "teamWideDeployEnabled",
            IntegerArgumentType.integer(0, 1)
        );
        teamWideDeployEnabled.executes(context -> executeAdd(
            context,
            RoomGameplayId.GUNMASTER,
            null,
            IntegerArgumentType.getInteger(context, "requiredTeamSize"),
            IntegerArgumentType.getInteger(context, "teamWideDeployEnabled") == 1,
            null
        ));
        teamWideDeployEnabled.then(Commands.argument("displayId", StringArgumentType.string())
            .executes(context -> executeAdd(
                context,
                RoomGameplayId.GUNMASTER,
                null,
                IntegerArgumentType.getInteger(context, "requiredTeamSize"),
                IntegerArgumentType.getInteger(context, "teamWideDeployEnabled") == 1,
                StringArgumentType.getString(context, "displayId")
            )));

        RequiredArgumentBuilder<CommandSourceStack, Integer> requiredTeamSize = Commands.argument(
            "requiredTeamSize",
            IntegerArgumentType.integer(0)
        );
        requiredTeamSize.then(teamWideDeployEnabled);

        RequiredArgumentBuilder<CommandSourceStack, String> team = Commands.argument("team", StringArgumentType.string())
            .suggests(VehicleRoomCommands::suggestTeamIds);
        team.then(requiredTeamSize);

        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> vehicleId = Commands.argument(
            "vehicleId",
            ResourceLocationArgument.id()
        ).suggests(VehicleRoomCommands::suggestVehicleIds);
        vehicleId.then(team);

        RequiredArgumentBuilder<CommandSourceStack, Integer> respawnSeconds = Commands.argument(
            "respawnSeconds",
            IntegerArgumentType.integer(-1)
        );
        respawnSeconds.then(vehicleId);

        RequiredArgumentBuilder<CommandSourceStack, String> entryId = Commands.argument("entryId", StringArgumentType.string());
        entryId.then(respawnSeconds);

        return Commands.literal("gunmaster").then(entryId);
    }

    private static int executeAdd(
        CommandContext<CommandSourceStack> context,
        RoomGameplayId gameplayId,
        Integer stageIndex,
        int requiredTeamSize,
        boolean teamWideDeployEnabled,
        String displayId
    ) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResolvedRoomCommandTarget target = resolveTarget(context, gameplayId.value());
        if (target.failureCode() != 0) {
            return target.failureCode();
        }

        String entryId = normalizeEntryId(StringArgumentType.getString(context, "entryId"));
        if (entryId.isEmpty()) {
            return CommandFeedbacks.failure(context.getSource(), "载具条目 ID 不能为空");
        }

        VehicleConfigRepository repository = ConquestDataManager.of(context.getSource().getServer()).vehicleConfigs();
        List<VehicleConfigEntry> entries = new ArrayList<>(repository.getEntries(
            target.roomId(),
            target.gameplayId().value(),
            target.backend()
        ));
        boolean duplicated = entries.stream().anyMatch(entry -> entry != null && entryId.equalsIgnoreCase(entry.getEntryId()));
        if (duplicated) {
            return CommandFeedbacks.failure(context.getSource(), "载具条目已存在: " + entryId);
        }

        ResourceLocation vehicleId = ResourceLocationArgument.getId(context, "vehicleId");
        String team = normalizeTeam(StringArgumentType.getString(context, "team"));
        if (team == null) {
            return CommandFeedbacks.failure(context.getSource(), "阵营仅支持 campA 或 campB");
        }

        VehicleConfigEntry entry = new VehicleConfigEntry();
        entry.setEntryId(entryId);
        entry.setGameplay(target.gameplayId().value());
        entry.setTeam(team);
        entry.setVehicleId(vehicleId.toString());
        entry.setYwzjVehicleId(vehicleId.toString());
        entry.setDisplayName(normalizeDisplayName(displayId));
        entry.setRequiredTeamSize(requiredTeamSize);
        entry.setTeamWideDeployEnabled(teamWideDeployEnabled);
        entry.setRespawnSeconds(IntegerArgumentType.getInteger(context, "respawnSeconds"));
        entry.setSpawnX(player.getX());
        entry.setSpawnY(player.getY());
        entry.setSpawnZ(player.getZ());
        entry.setSpawnYaw45(quantizeYaw45(player.getYRot()));
        entry.setStageIndex(stageIndex);
        entry.setEnabled(true);

        entries.add(entry);
        entries.sort(Comparator.comparing(VehicleConfigEntry::getEntryId, String.CASE_INSENSITIVE_ORDER));
        repository.saveEntries(target.roomId(), target.gameplayId().value(), target.backend(), entries);
        syncRuntime(context, target.roomId(), target.gameplayId(), target.backend());
        return CommandFeedbacks.info(
            context.getSource(),
            buildAddedMessage(target.roomId(), target.gameplayId(), target.backend(), entry)
        );
    }

    private static int executeRemove(CommandContext<CommandSourceStack> context) {
        ResolvedRoomCommandTarget target = resolveTarget(context, StringArgumentType.getString(context, "gameplay"));
        if (target.failureCode() != 0) {
            return target.failureCode();
        }
        String entryId = normalizeEntryId(StringArgumentType.getString(context, "entryId"));
        VehicleConfigRepository repository = ConquestDataManager.of(context.getSource().getServer()).vehicleConfigs();
        List<VehicleConfigEntry> entries = new ArrayList<>(repository.getEntries(
            target.roomId(),
            target.gameplayId().value(),
            target.backend()
        ));
        boolean removed = entries.removeIf(entry -> entry != null && entryId.equalsIgnoreCase(entry.getEntryId()));
        if (!removed) {
            return CommandFeedbacks.failure(context.getSource(), "未找到载具条目: " + entryId);
        }
        repository.saveEntries(target.roomId(), target.gameplayId().value(), target.backend(), entries);
        syncRuntime(context, target.roomId(), target.gameplayId(), target.backend());
        return CommandFeedbacks.info(
            context.getSource(),
            "已删除载具配置: room=" + target.roomId() + " | gameplay=" + target.gameplayId().value() + " | backend=" + target.backend().value() + " | entryId=" + entryId
        );
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        ResolvedRoomCommandTarget target = resolveTarget(context, StringArgumentType.getString(context, "gameplay"));
        if (target.failureCode() != 0) {
            return target.failureCode();
        }
        List<VehicleConfigEntry> entries = ConquestDataManager.of(context.getSource().getServer())
            .vehicleConfigs()
            .getEntries(target.roomId(), target.gameplayId().value(), target.backend())
            .stream()
            .filter(entry -> entry != null)
            .sorted(Comparator.comparing(VehicleConfigEntry::getEntryId, String.CASE_INSENSITIVE_ORDER))
            .toList();
        CommandFeedbacks.line(
            context.getSource(),
            Component.literal("========== 载具配置列表 ==========").withStyle(ChatFormatting.GOLD)
        );
        CommandFeedbacks.line(
            context.getSource(),
            Component.literal("backend=" + target.backend().value()).withStyle(ChatFormatting.AQUA)
        );
        if (entries.isEmpty()) {
            CommandFeedbacks.line(context.getSource(), Component.literal("(empty)").withStyle(ChatFormatting.DARK_GRAY));
            return 1;
        }
        for (VehicleConfigEntry entry : entries) {
            String stageText = entry.getStageIndex() == null ? "-" : String.valueOf(entry.getStageIndex());
            String message = String.format(
                Locale.ROOT,
                "%s | vehicle=%s | display=%s | team=%s | enableAt=%d | teamWide=%s | respawn=%d | stage=%s | pos=%.2f, %.2f, %.2f | yaw45=%d",
                entry.getEntryId(),
                entry.getVehicleId(),
                entry.getDisplayName().isBlank() ? "-" : entry.getDisplayName(),
                entry.getTeam(),
                entry.getRequiredTeamSize(),
                entry.isTeamWideDeployEnabled() ? "1" : "0",
                entry.getRespawnSeconds(),
                stageText,
                entry.getSpawnX(),
                entry.getSpawnY(),
                entry.getSpawnZ(),
                entry.getSpawnYaw45()
            );
            CommandFeedbacks.line(context.getSource(), Component.literal(message).withStyle(ChatFormatting.AQUA));
        }
        return CommandFeedbacks.info(context.getSource(), "载具配置数量: " + entries.size());
    }

    private static int executeResync(CommandContext<CommandSourceStack> context) {
        ResolvedRoomCommandTarget target = resolveTarget(context, StringArgumentType.getString(context, "gameplay"));
        if (target.failureCode() != 0) {
            return target.failureCode();
        }
        syncRuntime(context, target.roomId(), target.gameplayId(), target.backend());
        return CommandFeedbacks.info(
            context.getSource(),
            "已同步载具配置到运行时: room=" + target.roomId() + " | gameplay=" + target.gameplayId().value() + " | backend=" + target.backend().value()
        );
    }

    private static void syncRuntime(
        CommandContext<CommandSourceStack> context,
        String roomId,
        RoomGameplayId gameplayId,
        RoomVehicleBackend backend
    ) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return;
        }
        manager.coreService().vehicleRuntimeService().syncConfiguredEntries(roomId, gameplayId.value(), backend);
    }

    private static ResolvedRoomCommandTarget resolveTarget(CommandContext<CommandSourceStack> context, String gameplayRaw) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return ResolvedRoomCommandTarget.failure(CommandFeedbacks.failure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED));
        }
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        if (room == null) {
            return ResolvedRoomCommandTarget.failure(CommandFeedbacks.failure(context.getSource(), "未找到房间: " + roomId));
        }
        RoomGameplayId gameplayId;
        try {
            gameplayId = RoomGameplayId.of(gameplayRaw);
        } catch (Exception exception) {
            return ResolvedRoomCommandTarget.failure(CommandFeedbacks.failure(context.getSource(), "不支持的玩法: " + gameplayRaw));
        }
        if (!room.gameplayId().equals(gameplayId)) {
            return ResolvedRoomCommandTarget.failure(
                CommandFeedbacks.failure(
                    context.getSource(),
                    "房间玩法不匹配: room=" + room.roomId() + " 当前为 " + room.gameplayId().value() + "，不能操作 " + gameplayId.value()
                )
            );
        }
        return new ResolvedRoomCommandTarget(0, roomId, gameplayId, globalVehicleBackend(context.getSource()));
    }

    private static String buildAddedMessage(String roomId, RoomGameplayId gameplayId, RoomVehicleBackend backend, VehicleConfigEntry entry) {
        String stageText = entry.getStageIndex() == null ? "-" : entry.getStageIndex().toString();
        return String.format(
            Locale.ROOT,
            "已添加载具配置: room=%s | gameplay=%s | backend=%s | entryId=%s | vehicle=%s | display=%s | team=%s | enableAt=%d | teamWide=%s | respawn=%d | stage=%s | pos=%.2f, %.2f, %.2f | yaw45=%d",
            roomId,
            gameplayId.value(),
            backend == null ? RoomVehicleBackend.defaultValue().value() : backend.value(),
            entry.getEntryId(),
            entry.getVehicleId(),
            entry.getDisplayName().isBlank() ? "-" : entry.getDisplayName(),
            entry.getTeam(),
            entry.getRequiredTeamSize(),
            entry.isTeamWideDeployEnabled() ? "1" : "0",
            entry.getRespawnSeconds(),
            stageText,
            entry.getSpawnX(),
            entry.getSpawnY(),
            entry.getSpawnZ(),
            entry.getSpawnYaw45()
        );
    }

    private static String normalizeRoomId(String roomId) {
        return roomId == null ? "" : roomId.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeEntryId(String entryId) {
        return entryId == null ? "" : entryId.trim();
    }

    private static String normalizeDisplayName(String displayName) {
        return displayName == null ? "" : displayName.trim();
    }

    private static String normalizeTeam(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "campa", "camp_a", "a" -> "campA";
            case "campb", "camp_b", "b" -> "campB";
            default -> null;
        };
    }

    private static int quantizeYaw45(float yaw) {
        double normalized = yaw % 360.0D;
        if (normalized < 0.0D) {
            normalized += 360.0D;
        }
        int quantized = ((int) Math.round(normalized / 45.0D) * 45) % 360;
        return quantized < 0 ? quantized + 360 : quantized;
    }

    private static CompletableFuture<Suggestions> suggestGameplayIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            List.of(RoomGameplayId.CONQUEST.value(), RoomGameplayId.BREAKTHROUGH.value(), RoomGameplayId.DEATHMATCH.value(), RoomGameplayId.GUNMASTER.value()),
            builder
        );
    }

    private static CompletableFuture<Suggestions> suggestTeamIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("campA", "campB"), builder);
    }

    private static CompletableFuture<Suggestions> suggestVehicleIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        var level = player == null ? context.getSource().getLevel() : player.serverLevel();
        return SharedSuggestionProvider.suggest(VehicleCompatFacade.listVehicleIds(globalVehicleBackend(context.getSource()), level), builder);
    }

    private static CompletableFuture<Suggestions> suggestConfiguredEntryIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        String gameplay = StringArgumentType.getString(context, "gameplay");
        List<String> entryIds = ConquestDataManager.of(context.getSource().getServer())
            .vehicleConfigs()
            .getEntries(roomId, gameplay, globalVehicleBackend(context.getSource()))
            .stream()
            .filter(entry -> entry != null && entry.getEntryId() != null && !entry.getEntryId().isBlank())
            .map(VehicleConfigEntry::getEntryId)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        return SharedSuggestionProvider.suggest(entryIds, builder);
    }

    private static RoomVehicleBackend globalVehicleBackend(CommandSourceStack source) {
        if (source == null || source.getServer() == null) {
            return RoomVehicleBackend.defaultValue();
        }
        return ConquestDataManager.of(source.getServer()).serverConfig().getVehicleBackend();
    }

    private record ResolvedRoomCommandTarget(int failureCode, String roomId, RoomGameplayId gameplayId, RoomVehicleBackend backend) {
        private static ResolvedRoomCommandTarget failure(int code) {
            return new ResolvedRoomCommandTarget(code, "", RoomGameplayId.CONQUEST, RoomVehicleBackend.defaultValue());
        }
    }
}
