package org.mods.gd656conquest.server.core.command.debug;

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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareVehicleCompatBridge;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSnapshot;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSummonRequest;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatSummonResult;
import org.mods.gd656conquest.server.compat.ywzjvehicle.YwzjVehicleCompatBridge;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;
import org.mods.gd656conquest.server.room.vehicle.VehicleDeployResolution;
import org.mods.gd656conquest.server.room.vehicle.VehicleRuntimeRecord;
import org.mods.gd656conquest.server.room.vehicle.VehicleRuntimeTeamResolver;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.shared.text.ServerText;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class VehicleDebugCommands {
    private static final int DEFAULT_LIST_LIMIT = 50;

    private VehicleDebugCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("vehicle")
            .then(Commands.literal("status").executes(VehicleDebugCommands::executeStatus))
            .then(Commands.literal("ids")
                .executes(context -> executeListIds(context, ""))
                .then(Commands.argument("filter", StringArgumentType.greedyString())
                    .executes(context -> executeListIds(context, StringArgumentType.getString(context, "filter")))))
            .then(Commands.literal("summon")
                .then(Commands.argument("vehicleId", ResourceLocationArgument.id())
                    .suggests(VehicleDebugCommands::suggestVehicleIds)
                    .executes(context -> executeSummon(context, ResourceLocationArgument.getId(context, "vehicleId"), null))
                    .then(Commands.argument("displayId", ResourceLocationArgument.id())
                        .suggests(VehicleDebugCommands::suggestVehicleIds)
                        .executes(context -> executeSummon(
                            context,
                            ResourceLocationArgument.getId(context, "vehicleId"),
                            ResourceLocationArgument.getId(context, "displayId")
                        )))))
            .then(Commands.literal("inspect")
                .executes(VehicleDebugCommands::executeInspectMounted)
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(VehicleDebugCommands::executeInspectTarget)))
            .then(Commands.literal("runtime")
                .then(Commands.argument("roomId", StringArgumentType.string())
                    .suggests(VehicleDebugCommands::suggestRoomIds)
                    .executes(VehicleDebugCommands::executeRuntimeRoom)
                    .then(Commands.argument("entryId", StringArgumentType.string())
                        .suggests(VehicleDebugCommands::suggestRoomVehicleEntryIds)
                        .executes(VehicleDebugCommands::executeRuntimeEntry))))
            .then(Commands.literal("deploy-preview")
                .then(Commands.argument("roomId", StringArgumentType.string())
                    .suggests(VehicleDebugCommands::suggestRoomIds)
                    .then(Commands.argument("entryId", StringArgumentType.string())
                        .suggests(VehicleDebugCommands::suggestFriendlyDeployEntryIds)
                        .executes(VehicleDebugCommands::executeDeployPreviewSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(VehicleDebugCommands::executeDeployPreviewPlayer)))))
            .then(Commands.literal("deploy")
                .then(Commands.argument("roomId", StringArgumentType.string())
                    .suggests(VehicleDebugCommands::suggestRoomIds)
                    .then(Commands.argument("entryId", StringArgumentType.string())
                        .suggests(VehicleDebugCommands::suggestFriendlyDeployEntryIds)
                        .executes(VehicleDebugCommands::executeDeploySelf)
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(VehicleDebugCommands::executeDeployPlayer)))));
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context) {
        RoomVehicleBackend currentBackend = globalVehicleBackend(context.getSource());
        CommandFeedbacks.line(context.getSource(), ServerText.colored("========== 载具 API 调试状态 ==========", ChatFormatting.GOLD));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("globalVehicleBackend=" + currentBackend.value(), ChatFormatting.AQUA));
        printBackendStatus(context, RoomVehicleBackend.YWZJ);
        printBackendStatus(context, RoomVehicleBackend.SUPERBWARFARE);
        return 1;
    }

    private static int executeListIds(CommandContext<CommandSourceStack> context, String filter) {
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        RoomVehicleBackend backend = globalVehicleBackend(context.getSource());
        List<String> vehicleIds = VehicleCompatFacade.listVehicleIds(backend, resolveDebugLevel(context.getSource())).stream()
            .filter(id -> normalizedFilter.isBlank() || id.toLowerCase(Locale.ROOT).contains(normalizedFilter))
            .toList();
        if (vehicleIds.isEmpty()) {
            return CommandFeedbacks.failure(
                context.getSource(),
                (normalizedFilter.isBlank() ? "未读取到任何载具 ID" : "没有匹配的载具 ID") + " | backend=" + backend.value()
            );
        }
        CommandFeedbacks.line(context.getSource(), ServerText.colored("========== 载具 ID 列表 ==========", ChatFormatting.GOLD));
        CommandFeedbacks.line(
            context.getSource(),
            ServerText.colored("backend=" + backend.value() + " | total=" + vehicleIds.size(), ChatFormatting.AQUA)
        );
        int limit = Math.min(DEFAULT_LIST_LIMIT, vehicleIds.size());
        for (int i = 0; i < limit; i++) {
            CommandFeedbacks.line(context.getSource(), ServerText.colored(vehicleIds.get(i), ChatFormatting.AQUA));
        }
        if (vehicleIds.size() > limit) {
            CommandFeedbacks.line(
                context.getSource(),
                ServerText.colored("仅显示前 " + limit + " 条，剩余 " + (vehicleIds.size() - limit) + " 条", ChatFormatting.GRAY)
            );
        }
        return 1;
    }

    private static int executeSummon(CommandContext<CommandSourceStack> context, ResourceLocation vehicleId, ResourceLocation displayId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 spawnPos = player.position().add(player.getLookAngle().normalize().scale(4.0D));
        RoomVehicleBackend backend = globalVehicleBackend(context.getSource());
        VehicleCompatSummonResult result = VehicleCompatFacade.summon(new VehicleCompatSummonRequest(
            backend,
            player.serverLevel(),
            spawnPos,
            player.getYRot(),
            vehicleId == null ? "" : vehicleId.toString(),
            displayId == null ? "" : displayId.toString()
        ));
        if (!result.success()) {
            return CommandFeedbacks.failure(context.getSource(), "载具生成失败: " + result.message());
        }
        return CommandFeedbacks.info(
            context.getSource(),
            String.format(
                Locale.ROOT,
                "载具生成成功: backend=%s, entityId=%d, uuid=%s, vehicleId=%s, displayId=%s",
                backend.value(),
                result.entityId(),
                result.entityUuid(),
                result.vehicleId(),
                result.displayId()
            )
        );
    }

    private static int executeInspectMounted(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return printSnapshot(context.getSource(), VehicleCompatFacade.snapshot(player.getVehicle()));
    }

    private static int executeInspectTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, "target");
        return printSnapshot(context.getSource(), VehicleCompatFacade.snapshot(entity));
    }

    private static int executeRuntimeRoom(CommandContext<CommandSourceStack> context) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return CommandFeedbacks.failure(context.getSource(), "房间服务尚未初始化");
        }
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        List<VehicleRuntimeRecord> records = manager.coreService().vehicleRuntimeService().getRecords(roomId).stream()
            .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getEntryId(), right.getEntryId()))
            .toList();
        CommandFeedbacks.line(context.getSource(), ServerText.colored("========== 房间载具运行时 ==========", ChatFormatting.GOLD));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("roomId=" + roomId, ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("backend=" + globalVehicleBackend(context.getSource()).value(), ChatFormatting.AQUA));
        if (records.isEmpty()) {
            CommandFeedbacks.line(context.getSource(), ServerText.colored("(empty)", ChatFormatting.DARK_GRAY));
            return 1;
        }
        for (VehicleRuntimeRecord record : records) {
            CommandFeedbacks.line(context.getSource(), ServerText.colored(describeRuntimeRecord(record), ChatFormatting.AQUA));
        }
        return CommandFeedbacks.info(context.getSource(), "运行时载具数量: " + records.size());
    }

    private static int executeRuntimeEntry(CommandContext<CommandSourceStack> context) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return CommandFeedbacks.failure(context.getSource(), "房间服务尚未初始化");
        }
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        String entryId = StringArgumentType.getString(context, "entryId").trim();
        return manager.coreService().vehicleRuntimeService().getRecord(roomId, entryId)
            .map(record -> {
                CommandFeedbacks.line(context.getSource(), ServerText.colored("========== 载具运行时条目 ==========", ChatFormatting.GOLD));
                CommandFeedbacks.line(context.getSource(), ServerText.colored("roomId=" + roomId, ChatFormatting.AQUA));
                CommandFeedbacks.line(context.getSource(), ServerText.colored("backend=" + globalVehicleBackend(context.getSource()).value(), ChatFormatting.AQUA));
                CommandFeedbacks.line(context.getSource(), ServerText.colored(describeRuntimeRecord(record), ChatFormatting.AQUA));
                return 1;
            })
            .orElseGet(() -> CommandFeedbacks.failure(context.getSource(), "未找到载具运行时条目: " + entryId));
    }

    private static int executeDeployPreviewSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeDeployPreview(context, context.getSource().getPlayerOrException());
    }

    private static int executeDeployPreviewPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeDeployPreview(context, EntityArgument.getPlayer(context, "player"));
    }

    private static int executeDeployPreview(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return CommandFeedbacks.failure(context.getSource(), "房间服务尚未初始化");
        }
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        String entryId = StringArgumentType.getString(context, "entryId").trim();
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        RunningRuntimeState state = manager.findRunningState(roomId).orElse(null);
        if (room == null || state == null) {
            return CommandFeedbacks.failure(context.getSource(), "未找到目标房间或房间当前不在运行态");
        }
        RoomTeamId teamId = state.getRoomTeams().get(player.getUUID());
        if (teamId == null) {
            return CommandFeedbacks.failure(context.getSource(), "目标玩家当前不在该房间的运行阵营中");
        }
        VehicleDeployResolution resolution = manager.coreService()
            .deploySelectionService()
            .resolveVehicleSelection(room, state, player.getUUID(), teamId, entryId);
        VehicleRuntimeRecord record = manager.coreService().vehicleRuntimeService().getRecord(roomId, entryId).orElse(null);
        CommandFeedbacks.line(context.getSource(), ServerText.colored("========== 载具部署解析预览 ==========", ChatFormatting.GOLD));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("roomId=" + roomId, ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("backend=" + globalVehicleBackend(context.getSource()).value(), ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("player=" + player.getScoreboardName(), ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("team=" + teamId.name(), ChatFormatting.AQUA));
        if (record != null) {
            CommandFeedbacks.line(context.getSource(), ServerText.colored("runtime=" + describeRuntimeRecord(record), ChatFormatting.GRAY));
        }
        CommandFeedbacks.line(context.getSource(), ServerText.colored("action=" + resolution.actionType(), ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("displayState=" + resolution.displayState(), ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("message=" + resolution.message(), ChatFormatting.AQUA));
        CommandFeedbacks.line(
            context.getSource(),
            ServerText.colored("targetVehicleUuid=" + (resolution.targetVehicleUuid() == null ? "-" : resolution.targetVehicleUuid()), ChatFormatting.AQUA)
        );
        return 1;
    }

    private static int executeDeploySelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeDeploy(context, context.getSource().getPlayerOrException());
    }

    private static int executeDeployPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeDeploy(context, EntityArgument.getPlayer(context, "player"));
    }

    private static int executeDeploy(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return CommandFeedbacks.failure(context.getSource(), "房间服务尚未初始化");
        }
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        String entryId = StringArgumentType.getString(context, "entryId").trim();
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        RunningRuntimeState state = manager.findRunningState(roomId).orElse(null);
        if (room == null || state == null) {
            return CommandFeedbacks.failure(context.getSource(), "未找到目标房间或房间当前不在运行态");
        }
        if (!state.getRoomPlayers().contains(player.getUUID())) {
            return CommandFeedbacks.failure(context.getSource(), "目标玩家当前不在该房间运行态中");
        }
        RoomTeamId teamId = state.getRoomTeams().get(player.getUUID());
        if (teamId == null) {
            return CommandFeedbacks.failure(context.getSource(), "目标玩家当前不在该房间的运行阵营中");
        }
        VehicleDeployResolution resolution = manager.coreService()
            .deploySelectionService()
            .resolveVehicleSelection(room, state, player.getUUID(), teamId, entryId);
        if (!resolution.success()) {
            return CommandFeedbacks.failure(
                context.getSource(),
                "载具部署预检失败: action=" + resolution.actionType()
                    + ", displayState=" + resolution.displayState()
                    + ", message=" + resolution.message()
            );
        }

        long gameTick = context.getSource().getServer().getTickCount();
        Long deployReadyTick = state.getDeployReadyTicks().get(player.getUUID());
        boolean selected = manager.handleDeploySelection(player, "select", "vehicle", entryId, gameTick);
        if (!selected) {
            if (deployReadyTick == null) {
                return CommandFeedbacks.failure(context.getSource(), "目标玩家当前不在可部署阶段，请先死亡进入部署流程或使用 redeploy");
            }
            return CommandFeedbacks.failure(context.getSource(), "载具部署 select 失败，请检查目标条目或玩家当前部署状态");
        }
        boolean confirmed = manager.handleDeploySelection(player, "confirm", "vehicle", entryId, gameTick);
        if (!confirmed) {
            return CommandFeedbacks.failure(context.getSource(), "载具部署 confirm 失败，请检查玩家当前部署状态");
        }

        long remainTicks = Math.max(0L, state.getDeployReadyTicks().getOrDefault(player.getUUID(), gameTick) - gameTick);
        String remainText = remainTicks <= 0L
            ? "已立即触发部署链"
            : "已写入确认，约 " + Math.max(1L, (remainTicks + 19L) / 20L) + " 秒后自动开始";
        CommandFeedbacks.line(context.getSource(), ServerText.colored("========== 载具部署调试触发 ==========", ChatFormatting.GOLD));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("roomId=" + roomId, ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("backend=" + globalVehicleBackend(context.getSource()).value(), ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("player=" + player.getScoreboardName(), ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("entryId=" + entryId, ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("action=" + resolution.actionType(), ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("displayState=" + resolution.displayState(), ChatFormatting.AQUA));
        return CommandFeedbacks.info(context.getSource(), remainText);
    }

    private static int printSnapshot(CommandSourceStack source, java.util.Optional<VehicleCompatSnapshot> snapshotOptional) {
        if (snapshotOptional.isEmpty()) {
            return CommandFeedbacks.failure(source, "目标不是受支持的载具实体");
        }
        VehicleCompatSnapshot snapshot = snapshotOptional.get();
        CommandFeedbacks.line(source, ServerText.colored("========== 载具快照 ==========", ChatFormatting.GOLD));
        CommandFeedbacks.line(source, ServerText.colored("entityId=" + snapshot.entityId(), ChatFormatting.AQUA));
        CommandFeedbacks.line(source, ServerText.colored("entityUuid=" + snapshot.entityUuid(), ChatFormatting.AQUA));
        CommandFeedbacks.line(source, ServerText.colored("vehicleId=" + snapshot.vehicleId(), ChatFormatting.AQUA));
        CommandFeedbacks.line(source, ServerText.colored("displayId=" + snapshot.displayId(), ChatFormatting.AQUA));
        CommandFeedbacks.line(source, ServerText.colored("displayName=" + snapshot.displayName(), ChatFormatting.AQUA));
        CommandFeedbacks.line(source, ServerText.colored("destroyed=" + snapshot.destroyed(), ChatFormatting.AQUA));
        CommandFeedbacks.line(source, ServerText.colored("health=" + snapshot.health() + "/" + snapshot.maxHealth(), ChatFormatting.AQUA));
        CommandFeedbacks.line(source, ServerText.colored("passengerCount=" + snapshot.passengerCount(), ChatFormatting.AQUA));
        CommandFeedbacks.line(source, ServerText.colored("driverUuid=" + snapshot.driverUuid(), ChatFormatting.AQUA));
        CommandFeedbacks.line(source, ServerText.colored("driverName=" + snapshot.driverName(), ChatFormatting.AQUA));
        CommandFeedbacks.line(
            source,
            ServerText.colored(
                String.format(Locale.ROOT, "position=%.2f, %.2f, %.2f", snapshot.x(), snapshot.y(), snapshot.z()),
                ChatFormatting.AQUA
            )
        );
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestVehicleIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            VehicleCompatFacade.listVehicleIds(globalVehicleBackend(context.getSource()), resolveDebugLevel(context.getSource())),
            builder
        );
    }

    private static void printBackendStatus(CommandContext<CommandSourceStack> context, RoomVehicleBackend backend) {
        List<String> vehicleIds = VehicleCompatFacade.listVehicleIds(backend, resolveDebugLevel(context.getSource()));
        boolean loaded = switch (backend) {
            case YWZJ -> YwzjVehicleCompatBridge.isLoaded();
            case SUPERBWARFARE -> SuperbWarfareVehicleCompatBridge.isLoaded();
        };
        boolean available = VehicleCompatFacade.isCompatAvailable(backend);
        String detail = switch (backend) {
            case YWZJ -> YwzjVehicleCompatBridge.describeAvailability();
            case SUPERBWARFARE -> SuperbWarfareVehicleCompatBridge.describeAvailability();
        };
        CommandFeedbacks.line(context.getSource(), ServerText.colored("---- " + backend.value() + " ----", ChatFormatting.GRAY));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("modLoaded=" + loaded, ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("bridgeAvailable=" + available, ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("detail=" + detail, ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("registeredVehicleCount=" + vehicleIds.size(), ChatFormatting.AQUA));
        if (!vehicleIds.isEmpty()) {
            CommandFeedbacks.line(
                context.getSource(),
                ServerText.colored("sample=" + String.join(", ", vehicleIds.subList(0, Math.min(5, vehicleIds.size()))), ChatFormatting.GRAY)
            );
        }
    }

    private static CompletableFuture<Suggestions> suggestRoomIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> roomIds = RoomCoreRuntimeManager.of(context.getSource().getServer()).listRooms().stream()
            .map(room -> room.roomId())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        return SharedSuggestionProvider.suggest(roomIds, builder);
    }

    private static CompletableFuture<Suggestions> suggestRoomVehicleEntryIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> entryIds = resolveSuggestedEntryIds(context, false);
        return SharedSuggestionProvider.suggest(entryIds, builder);
    }

    private static CompletableFuture<Suggestions> suggestFriendlyDeployEntryIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> entryIds = resolveSuggestedEntryIds(context, true);
        return SharedSuggestionProvider.suggest(entryIds, builder);
    }

    private static String normalizeRoomId(String roomId) {
        return roomId == null ? "" : roomId.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> resolveSuggestedEntryIds(CommandContext<CommandSourceStack> context, boolean friendlyOnly) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return List.of();
        }
        String roomId;
        try {
            roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
        RunningRuntimeState state = manager.findRunningState(roomId).orElse(null);
        List<VehicleRuntimeRecord> records = manager.coreService().vehicleRuntimeService().getRecords(roomId);
        if (records.isEmpty()) {
            return List.of();
        }
        ServerPlayer viewer = null;
        try {
            viewer = context.getSource().getPlayerOrException();
        } catch (CommandSyntaxException ignored) {
            viewer = null;
        }
        final RoomTeamId viewerTeam = friendlyOnly && state != null && viewer != null
            ? state.getRoomTeams().get(viewer.getUUID())
            : null;
        return records.stream()
            .filter(record -> !record.getEntryId().isBlank())
            .filter(record -> !friendlyOnly || viewerTeam == null || VehicleRuntimeTeamResolver.resolveConfiguredTeam(record.getConfiguredTeam()) == viewerTeam)
            .map(VehicleRuntimeRecord::getEntryId)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private static String describeRuntimeRecord(VehicleRuntimeRecord record) {
        String entityUuid = record.getSpawnedEntityUuid() == null ? "-" : record.getSpawnedEntityUuid().toString();
        String driverUuid = record.getDriverPlayerId() == null ? "-" : record.getDriverPlayerId().toString();
        String waitingText;
        if (record.getWaitingUntilTick() == Long.MAX_VALUE) {
            waitingText = "never";
        } else if (record.getWaitingUntilTick() < 0L) {
            waitingText = "-";
        } else {
            waitingText = String.valueOf(record.getWaitingUntilTick());
        }
        return String.format(
            Locale.ROOT,
            "%s | vehicle=%s | display=%s | team=%s | enableAt=%d | teamWide=%s | state=%s/%s/%s | entityId=%d | entityUuid=%s | health=%.1f/%.1f | passengers=%d | driver=%s | driverUuid=%s | waitingUntil=%s | pos=%.2f, %.2f, %.2f",
            record.getEntryId(),
            record.getConfiguredVehicleId(),
            record.getConfiguredDisplayId().isBlank() ? "-" : record.getConfiguredDisplayId(),
            record.getConfiguredTeam(),
            record.getConfiguredRequiredTeamSize(),
            record.isConfiguredTeamWideDeployEnabled() ? "1" : "0",
            record.getLogicalState(),
            record.getDisplayState(),
            record.getOwnershipState(),
            record.getSpawnedEntityId(),
            entityUuid,
            record.getHealth(),
            record.getMaxHealth(),
            record.getPassengerCount(),
            record.getDriverName(),
            driverUuid,
            waitingText,
            record.getX(),
            record.getY(),
            record.getZ()
        );
    }

    private static RoomVehicleBackend globalVehicleBackend(CommandSourceStack source) {
        if (source == null || source.getServer() == null) {
            return RoomVehicleBackend.defaultValue();
        }
        return ConquestDataManager.of(source.getServer()).serverConfig().getVehicleBackend();
    }

    private static net.minecraft.server.level.ServerLevel resolveDebugLevel(CommandSourceStack source) {
        if (source == null) {
            return null;
        }
        return source.getLevel();
    }
}
