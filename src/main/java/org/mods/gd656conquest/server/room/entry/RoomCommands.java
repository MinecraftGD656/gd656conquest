package org.mods.gd656conquest.server.room.entry;

import com.google.gson.Gson;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.core.RoomJoinResult;
import org.mods.gd656conquest.server.room.config.RoomConfigDescriptor;
import org.mods.gd656conquest.server.room.config.RoomConfigDescriptorRegistry;
import org.mods.gd656conquest.server.room.config.RoomConfigScope;
import org.mods.gd656conquest.server.room.config.RoomValidationMessageFormatter;
import org.mods.gd656conquest.server.room.config.RoomDefinitionGroupConfigEditor;
import org.mods.gd656conquest.server.room.config.RoomDefinitionScalarConfigEditor;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomCommonConfig;
import org.mods.gd656conquest.server.room.model.RoomCapturePointDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomFixedLoadoutRules;
import org.mods.gd656conquest.server.room.runtime.RoomGunmasterWeaponPoolRules;
import org.mods.gd656conquest.server.room.runtime.RoomListPresentationService;
import org.mods.gd656conquest.server.shared.contract.ClickActionSpec;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.shared.text.ServerText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class RoomCommands {
    private static final Gson GSON = new Gson();
    private static final String ROOM_SERVICE_NOT_INITIALIZED = "房间服务尚未初始化";
    private static final long ROOM_ADMIN_CONFIRM_WINDOW_MS = 5_000L;
    private static final Map<String, Long> ROOM_ADMIN_CONFIRMATIONS = new ConcurrentHashMap<>();

    private RoomCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("room")
            .then(Commands.literal("list").executes(RoomCommands::executeList))
            .then(Commands.literal("current").executes(RoomCommands::executeCurrent))
            .then(Commands.literal("leave").executes(RoomCommands::executeLeave))
            .then(Commands.literal("copy")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("templateRoomId", StringArgumentType.string())
                    .suggests(RoomCommands::suggestRoomIds)
                    .then(Commands.argument("targetRoomId", StringArgumentType.string())
                        .suggests(RoomCommands::suggestRoomIds)
                        .executes(RoomCommands::executeCopyRoomConfig))))
            .then(Commands.literal("delete")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("targetRoomId", StringArgumentType.string())
                    .suggests(RoomCommands::suggestRoomIds)
                    .executes(RoomCommands::executeDeleteRoom)))
            .then(buildCurrentTeamTree())
            .then(buildRoomScopedTree());
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildRedeployTree() {
        return Commands.literal("redeploy").executes(RoomCommands::executeRedeploy);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildCurrentTeamTree() {
        return Commands.literal("camp")
            .then(Commands.literal("join")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("teamId", StringArgumentType.string())
                    .suggests(RoomCommands::suggestTeamIds)
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(RoomCommands::executeCurrentCampJoin))))
            .then(Commands.literal("accept").executes(RoomCommands::executeCurrentCampAccept))
            .then(Commands.literal("get").executes(RoomCommands::executeCurrentCampGet));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildRoomScopedTree() {
        return Commands.argument("roomId", StringArgumentType.string())
            .suggests(RoomCommands::suggestRoomIds)
            .then(Commands.literal("get").executes(RoomCommands::executeGet))
            .then(Commands.literal("join").executes(RoomCommands::executeJoin))
            .then(Commands.literal("invite")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(RoomCommands::executeInvite)))
            .then(Commands.literal("startRoom")
                .requires(source -> source.hasPermission(2))
                .executes(RoomCommands::executeStartRoom))
            .then(Commands.literal("cleanRoom")
                .requires(source -> source.hasPermission(2))
                .executes(RoomCommands::executeCleanRoom))
            .then(Commands.literal("saveRoom")
                .requires(source -> source.hasPermission(2))
                .executes(RoomCommands::executeSaveRoom))
            .then(Commands.literal("mergeWeaponWhitelist")
                .requires(source -> source.hasPermission(2))
                .executes(RoomCommands::executeMergeWeaponWhitelist))
            .then(Commands.literal("maintenanceRoom")
                .requires(source -> source.hasPermission(2))
                .executes(RoomCommands::executeMaintenanceRoom))
            .then(Commands.literal("camp")
                .then(Commands.literal("join")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("teamId", StringArgumentType.string())
                        .suggests(RoomCommands::suggestTeamIds)
                        .then(Commands.argument("target", EntityArgument.player())
                            .executes(RoomCommands::executeCampJoin))))
                .then(Commands.literal("switch")
                    .requires(source -> source.hasPermission(2))
                    .executes(RoomCommands::executeCampSwitchSelf)
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(RoomCommands::executeCampSwitchTarget)))
                .then(Commands.literal("accept").executes(RoomCommands::executeCampAccept)))
            .then(Commands.literal("team")
                .then(Commands.literal("get").executes(RoomCommands::executeTeamGet)))
            .then(VehicleRoomCommands.buildTree())
            .then(buildConfigTree());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildConfigTree() {
        return Commands.literal("config")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("reload").executes(RoomCommands::executeConfigReload))
            .then(buildConfigCommonBranch())
            .then(buildConfigConquestBranch("conquest"))
            .then(buildConfigBreakthroughBranch("breakthrough"))
            .then(buildConfigDeathmatchBranch("deathmatch"))
            .then(buildConfigGunmasterBranch("gunmaster"));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildConfigCommonBranch() {
        return Commands.literal("common")
            .then(Commands.argument("configId", StringArgumentType.string())
                .suggests(RoomCommands::suggestCommonConfigIds)
                .then(Commands.argument("actionOrValue", StringArgumentType.string())
                    .suggests(RoomCommands::suggestCommonActionOrValue)
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(RoomCommands::suggestCommonActionValue)
                        .executes(RoomCommands::executeConfigCommonActionWithValue))
                    .executes(RoomCommands::executeConfigCommonActionOrValue))
                .then(Commands.argument("actionOrValue", StringArgumentType.greedyString())
                    .suggests(RoomCommands::suggestCommonActionOrValue)
                    .executes(RoomCommands::executeConfigCommonActionOrValue)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildConfigConquestBranch(String literalName) {
        return Commands.literal(literalName)
            .then(Commands.argument("configId", StringArgumentType.string())
                .suggests(RoomCommands::suggestPrivateConfigIds)
                .then(Commands.argument("actionOrValue", StringArgumentType.string())
                    .suggests(RoomCommands::suggestPrivateActionOrValue)
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(RoomCommands::suggestPrivateActionValue)
                        .executes(RoomCommands::executeConfigConquestActionWithValue))
                    .executes(RoomCommands::executeConfigConquestActionOrValue))
                .then(Commands.argument("actionOrValue", StringArgumentType.greedyString())
                    .suggests(RoomCommands::suggestPrivateActionOrValue)
                    .executes(RoomCommands::executeConfigConquestActionOrValue)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildConfigBreakthroughBranch(String literalName) {
        return Commands.literal(literalName)
            .then(Commands.argument("configId", StringArgumentType.string())
                .suggests(RoomCommands::suggestBreakthroughConfigIds)
                .then(Commands.argument("actionOrValue", StringArgumentType.string())
                    .suggests(RoomCommands::suggestBreakthroughActionOrValue)
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(RoomCommands::suggestBreakthroughActionValue)
                        .executes(RoomCommands::executeConfigBreakthroughActionWithValue))
                    .executes(RoomCommands::executeConfigBreakthroughActionOrValue))
                .then(Commands.argument("actionOrValue", StringArgumentType.greedyString())
                    .suggests(RoomCommands::suggestBreakthroughActionOrValue)
                    .executes(RoomCommands::executeConfigBreakthroughActionOrValue)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildConfigDeathmatchBranch(String literalName) {
        return Commands.literal(literalName)
            .then(Commands.argument("configId", StringArgumentType.string())
                .suggests(RoomCommands::suggestDeathmatchConfigIds)
                .then(Commands.argument("actionOrValue", StringArgumentType.string())
                    .suggests(RoomCommands::suggestDeathmatchActionOrValue)
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(RoomCommands::suggestDeathmatchActionValue)
                        .executes(RoomCommands::executeConfigDeathmatchActionWithValue))
                    .executes(RoomCommands::executeConfigDeathmatchActionOrValue))
                .then(Commands.argument("actionOrValue", StringArgumentType.greedyString())
                    .suggests(RoomCommands::suggestDeathmatchActionOrValue)
                    .executes(RoomCommands::executeConfigDeathmatchActionOrValue)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildConfigGunmasterBranch(String literalName) {
        return Commands.literal(literalName)
            .then(Commands.argument("configId", StringArgumentType.string())
                .suggests(RoomCommands::suggestGunmasterConfigIds)
                .then(Commands.argument("actionOrValue", StringArgumentType.string())
                    .suggests(RoomCommands::suggestGunmasterActionOrValue)
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(RoomCommands::suggestGunmasterActionValue)
                        .executes(RoomCommands::executeConfigGunmasterActionWithValue))
                    .executes(RoomCommands::executeConfigGunmasterActionOrValue))
                .then(Commands.argument("actionOrValue", StringArgumentType.greedyString())
                    .suggests(RoomCommands::suggestGunmasterActionOrValue)
                    .executes(RoomCommands::executeConfigGunmasterActionOrValue)));
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        List<RoomDefinition> rooms = manager.listRooms().stream()
            .sorted(Comparator.comparing(RoomDefinition::roomId, String.CASE_INSENSITIVE_ORDER))
            .toList();
        sendLine(context.getSource(), Component.literal("========== Rooms ==========").withStyle(ChatFormatting.LIGHT_PURPLE));
        ServerPlayer viewer = context.getSource().getPlayer();
        for (RoomDefinition room : rooms) {
            sendLine(context.getSource(), RoomListPresentationService.buildRoomListLine(room, viewer, manager));
        }
        return replyInfo(context.getSource(), "房间数量: " + rooms.size());
    }

    private static int executeCurrent(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        String roomId = manager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (roomId == null) {
            return replyInfo(context.getSource(), "你当前不在任何房间中");
        }
        String team = manager.findRoomTeam(roomId, player.getUUID())
            .or(() -> manager.findQueueTeam(roomId, player.getUUID()))
            .map(Enum::name)
            .orElse("-");
        String squad = manager.findPlayerSquadLabel(player.getUUID()).orElse("-");
        return CommandFeedbacks.info(context.getSource(), "当前房间: " + roomId + " | 阵营: " + team + " | 小队: " + squad);
    }

    private static int executeLeave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        RoomSessionTransitionService.LeaveExecutionResult result = RoomSessionTransitionService.of(context.getSource().getServer())
            .leaveSession(player, RoomSessionTransitionService.LeaveMode.DETACH_ONLY);
        if (result.denied()) {
            return replyFailure(context.getSource(), result.deniedMessage());
        }
        if (result.groupAction()) {
            return replyInfo(context.getSource(), result.operatorMessage());
        }
        return replyInfo(context.getSource(), result.operatorHadSession() ? "已离开当前房间" : "已离开当前匹配队列");
    }

    private static int executeGet(CommandContext<CommandSourceStack> context) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        if (room == null) {
            return replyFailure(context.getSource(), "未找到房间: " + roomId);
        }
        int queueCount = manager.findMatchingState(roomId).map(state -> state.getQueuePlayers().size()).orElse(0);
        int roomPlayerCount = manager.countRoomPlayers(roomId);
        sendLine(context.getSource(), Component.literal("========== Room Detail ==========").withStyle(ChatFormatting.LIGHT_PURPLE));
        sendLine(context.getSource(), Component.literal("ID: " + room.roomId()).withStyle(ChatFormatting.AQUA));
        sendLine(context.getSource(), Component.literal("显示名: " + room.displayName()).withStyle(ChatFormatting.WHITE));
        sendLine(context.getSource(), Component.literal("玩法: " + room.gameplayId()).withStyle(ChatFormatting.GOLD));
        sendLine(context.getSource(), Component.literal("状态: " + room.lifecycleState().name()).withStyle(ChatFormatting.GRAY));
        sendLine(context.getSource(), Component.literal("队列人数: " + queueCount).withStyle(ChatFormatting.GRAY));
        sendLine(context.getSource(), Component.literal("对局人数: " + roomPlayerCount).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int executeJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeJoinById(context.getSource(), StringArgumentType.getString(context, "roomId"));
    }

    public static int executeJoinById(CommandSourceStack source, String rawRoomId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(source.getServer());
        if (!manager.isInitialized()) {
            return replyFailure(source, ROOM_SERVICE_NOT_INITIALIZED);
        }
        String roomId = normalizeRoomId(rawRoomId);
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        if (room == null) {
            return replyFailure(source, "未找到房间: " + roomId);
        }
        RoomJoinAccessPolicy.JoinCheckResult access = RoomJoinAccessPolicy.evaluate(player, room, manager);
        if (!access.allowed()) {
            return replyFailure(source, access.deniedMessage());
        }
        boolean maintenance = access.maintenance();
        RoomSessionTransitionService.JoinExecutionResult result = RoomSessionTransitionService.of(source.getServer())
            .joinRoom(player, roomId, maintenance);
        if (result.denied()) {
            return replyFailure(source, result.deniedMessage());
        }
        boolean joined = result.success();
        if (!joined) {
            return replyFailure(source, maintenance ? "进入维护房间失败: " + roomId : "加入房间失败: " + roomId);
        }
        if (result.groupAction() && result.operatorMessage() != null && !result.operatorMessage().isBlank()) {
            return replyInfo(source, result.operatorMessage());
        }
        if (maintenance) {
            return replyInfo(source, "已进入维护房间: " + roomId);
        }
        return replyInfo(source, describeSingleJoinMessage(roomId, room, result.operatorJoinResult(), manager, player.getUUID()));
    }

    private static int executeInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        if (room == null) {
            return replyFailure(context.getSource(), "未找到房间: " + roomId);
        }
        String command = "/gdc action joinroom " + roomId;
        MutableComponent message = ServerText.sectionPrefix("房间")
            .append(ServerText.colored(context.getSource().getTextName(), ChatFormatting.WHITE))
            .append(ServerText.colored(" 邀请你加入 ", ChatFormatting.AQUA))
            .append(ServerText.colored(room.displayName(), ChatFormatting.GOLD))
            .append(Component.literal(" "))
            .append(ServerText.action(new ClickActionSpec("[点击加入]", ChatFormatting.GOLD, command, "点击加入该房间")));
        List<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets").stream().toList();
        for (ServerPlayer target : targets) {
            ServerMessageSender.rawSystem(target, message.copy());
        }
        return CommandFeedbacks.info(context.getSource(), "已发送邀请，人数: " + targets.size());
    }

    private static int executeCurrentCampJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        String roomId = manager.findPlayerRoomId(target.getUUID()).orElse(null);
        if (roomId == null) {
            return replyFailure(context.getSource(), "目标玩家当前不在任何房间中");
        }
        return executeCampJoinInternal(context, roomId, target);
    }

    private static int executeCampJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        return executeCampJoinInternal(context, roomId, target);
    }

    private static int executeCampSwitchSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeCampSwitchInternal(
            context,
            normalizeRoomId(StringArgumentType.getString(context, "roomId")),
            context.getSource().getPlayerOrException()
        );
    }

    private static int executeCampSwitchTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeCampSwitchInternal(
            context,
            normalizeRoomId(StringArgumentType.getString(context, "roomId")),
            EntityArgument.getPlayer(context, "target")
        );
    }

    private static int executeCampJoinInternal(CommandContext<CommandSourceStack> context, String roomId, ServerPlayer target) throws CommandSyntaxException {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        RoomTeamId teamId = parseTeamId(StringArgumentType.getString(context, "teamId"));
        String result = manager.queueTeamJoinChange(
            roomId,
            target.getUUID(),
            teamId,
            context.getSource().getTextName(),
            context.getSource().getServer().getTickCount()
        );
        return replyInfo(context.getSource(), result);
    }

    private static int executeCampSwitchInternal(CommandContext<CommandSourceStack> context, String roomId, ServerPlayer target) {
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        String result = manager.switchPlayerToOppositeTeam(
            roomId,
            target == null ? null : target.getUUID(),
            context.getSource().getTextName(),
            context.getSource().getServer().getTickCount()
        );
        return replyInfo(context.getSource(), result);
    }

    private static int executeCurrentCampAccept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeCurrentCampAccept(context.getSource());
    }

    public static int executeCurrentCampAccept(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(source.getServer());
        if (!manager.isInitialized()) {
            return replyFailure(source, ROOM_SERVICE_NOT_INITIALIZED);
        }
        String roomId = manager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (roomId == null) {
            return replyFailure(source, "你当前不在任何房间中");
        }
        String result = manager.acceptAutoTeamSwitch(roomId, player.getUUID(), source.getServer().getTickCount());
        return replyInfo(source, result);
    }

    private static int executeCampAccept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        String result = manager.acceptAutoTeamSwitch(roomId, player.getUUID(), context.getSource().getServer().getTickCount());
        return replyInfo(context.getSource(), result);
    }

    private static int executeCurrentCampGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        String roomId = manager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (roomId == null) {
            return replyFailure(context.getSource(), "你当前不在任何房间中");
        }
        return sendTeamSummary(context.getSource(), manager, roomId, player);
    }

    private static int executeTeamGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        return sendTeamSummary(context.getSource(), manager, roomId, player);
    }

    private static int executeRedeploy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        String result = manager.requestRedeploy(player);
        return replyInfo(context.getSource(), result);
    }

    private static int executeStartRoom(CommandContext<CommandSourceStack> context) {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        RoomAdminLifecycleService.OperationResult result = new RoomAdminLifecycleService(context.getSource().getServer())
            .startRoom(roomId, context.getSource().getServer().getTickCount());
        return result.success() ? replyInfo(context.getSource(), result.message()) : replyFailure(context.getSource(), result.message());
    }

    private static int executeCleanRoom(CommandContext<CommandSourceStack> context) {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        sendLine(context.getSource(), Component.literal("正在执行 cleanRoom: " + roomId).withStyle(ChatFormatting.YELLOW));
        RoomAdminLifecycleService.OperationResult result = new RoomAdminLifecycleService(context.getSource().getServer())
            .cleanRoom(roomId, context.getSource().getServer().getTickCount());
        return result.success() ? replyInfo(context.getSource(), result.message()) : replyFailure(context.getSource(), result.message());
    }

    private static int executeSaveRoom(CommandContext<CommandSourceStack> context) {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        sendLine(context.getSource(), Component.literal("正在执行 saveRoom: " + roomId).withStyle(ChatFormatting.YELLOW));
        RoomAdminLifecycleService.OperationResult result = new RoomAdminLifecycleService(context.getSource().getServer())
            .saveRoom(roomId, context.getSource().getServer().getTickCount());
        return result.success() ? replyInfo(context.getSource(), result.message()) : replyFailure(context.getSource(), result.message());
    }

    private static int executeMaintenanceRoom(CommandContext<CommandSourceStack> context) {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        RoomAdminLifecycleService.OperationResult result = new RoomAdminLifecycleService(context.getSource().getServer())
            .maintenanceRoom(roomId, context.getSource().getServer().getTickCount());
        return result.success() ? replyInfo(context.getSource(), result.message()) : replyFailure(context.getSource(), result.message());
    }

    private static int executeCopyRoomConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(source.getServer());
        if (!manager.isInitialized()) {
            return replyFailure(source, ROOM_SERVICE_NOT_INITIALIZED);
        }
        String templateRoomId = normalizeRoomId(StringArgumentType.getString(context, "templateRoomId"));
        String targetRoomId = normalizeRoomId(StringArgumentType.getString(context, "targetRoomId"));
        String confirmCommand = "/gdc room copy " + templateRoomId + " " + targetRoomId;
        if (!confirmRoomAdminAction(source, "copy:" + templateRoomId + "->" + targetRoomId)) {
            return replyInfo(source, "请在5秒内再次输入 " + confirmCommand);
        }
        sendLine(source, Component.literal("正在复制房间配置: " + templateRoomId + " -> " + targetRoomId).withStyle(ChatFormatting.YELLOW));
        RoomAdminLifecycleService.OperationResult result = new RoomAdminConfigTemplateService(source.getServer())
            .copyRoomConfig(templateRoomId, targetRoomId, source.getServer().getTickCount());
        return result.success() ? replyInfo(source, result.message()) : replyFailure(source, result.message());
    }

    private static int executeDeleteRoom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(source.getServer());
        if (!manager.isInitialized()) {
            return replyFailure(source, ROOM_SERVICE_NOT_INITIALIZED);
        }
        String targetRoomId = normalizeRoomId(StringArgumentType.getString(context, "targetRoomId"));
        String confirmCommand = "/gdc room delete " + targetRoomId;
        if (!confirmRoomAdminAction(source, "delete:" + targetRoomId)) {
            return replyInfo(source, "请在5秒内再次输入 " + confirmCommand);
        }
        sendLine(source, Component.literal("正在删除房间配置并清空房间: " + targetRoomId).withStyle(ChatFormatting.YELLOW));
        RoomAdminLifecycleService.OperationResult result = new RoomAdminConfigTemplateService(source.getServer())
            .deleteRoom(targetRoomId, source.getServer().getTickCount());
        return result.success() ? replyInfo(source, result.message()) : replyFailure(source, result.message());
    }

    private static int executeConfigCommonSet(CommandContext<CommandSourceStack> context) {
        return executeConfigSetById(
            context,
            StringArgumentType.getString(context, "configId"),
            StringArgumentType.getString(context, "actionOrValue"),
            RoomConfigScope.COMMON
        );
    }

    private static int executeConfigCommonActionOrValue(CommandContext<CommandSourceStack> context) {
        String configId = StringArgumentType.getString(context, "configId");
        String actionOrValue = StringArgumentType.getString(context, "actionOrValue");
        RoomConfigDescriptor descriptor = resolveDescriptor(configId);
        if (descriptor == null) {
            return replyFailure(context.getSource(), "未找到配置项: " + configId);
        }
        if (descriptor.supportsGroupOperations()) {
            String action = actionOrValue == null ? "" : actionOrValue.trim().toLowerCase(Locale.ROOT);
            if ("list".equals(action)) {
                return executeConfigGroupListById(context, configId, RoomConfigScope.COMMON);
            }
            return replyFailure(context.getSource(), "组配置仅支持 list / add / del");
        }
        return executeConfigSetById(context, configId, actionOrValue, RoomConfigScope.COMMON);
    }

    private static int executeConfigCommonActionWithValue(CommandContext<CommandSourceStack> context) {
        String configId = StringArgumentType.getString(context, "configId");
        String action = StringArgumentType.getString(context, "actionOrValue");
        String value = StringArgumentType.getString(context, "value");
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "add" -> executeConfigGroupMutation(context, configId, value, true, RoomConfigScope.COMMON);
            case "del" -> executeConfigGroupMutation(context, configId, value, false, RoomConfigScope.COMMON);
            default -> executeConfigSetById(context, configId, value, RoomConfigScope.COMMON);
        };
    }

    private static int executeConfigConquestActionOrValue(CommandContext<CommandSourceStack> context) {
        String configId = StringArgumentType.getString(context, "configId");
        String actionOrValue = StringArgumentType.getString(context, "actionOrValue");
        RoomConfigDescriptor descriptor = resolveDescriptor(configId);
        if (descriptor == null) {
            return replyFailure(context.getSource(), "未找到配置项: " + configId);
        }
        if (descriptor.supportsGroupOperations()) {
            String action = actionOrValue == null ? "" : actionOrValue.trim().toLowerCase(Locale.ROOT);
            if ("list".equals(action)) {
                return executeConfigGroupListById(context, configId, RoomConfigScope.CONQUEST);
            }
            return replyFailure(context.getSource(), "组配置仅支持 list / add / del");
        }
        return executeConfigSetById(context, configId, actionOrValue, RoomConfigScope.CONQUEST);
    }

    private static int executeConfigConquestActionWithValue(CommandContext<CommandSourceStack> context) {
        String configId = StringArgumentType.getString(context, "configId");
        String action = StringArgumentType.getString(context, "actionOrValue");
        String value = StringArgumentType.getString(context, "value");
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "add" -> executeConfigGroupMutation(context, configId, value, true, RoomConfigScope.CONQUEST);
            case "del" -> executeConfigGroupMutation(context, configId, value, false, RoomConfigScope.CONQUEST);
            default -> executeConfigSetById(context, configId, value, RoomConfigScope.CONQUEST);
        };
    }

    private static int executeConfigBreakthroughActionOrValue(CommandContext<CommandSourceStack> context) {
        String configId = StringArgumentType.getString(context, "configId");
        String actionOrValue = StringArgumentType.getString(context, "actionOrValue");
        RoomConfigDescriptor descriptor = resolveDescriptor(configId);
        if (descriptor == null) {
            return replyFailure(context.getSource(), "未找到配置项: " + configId);
        }
        if (descriptor.supportsGroupOperations()) {
            String action = actionOrValue == null ? "" : actionOrValue.trim().toLowerCase(Locale.ROOT);
            if ("list".equals(action)) {
                return executeConfigGroupListById(context, configId, RoomConfigScope.BREAKTHROUGH);
            }
            return replyFailure(context.getSource(), "组配置仅支持 list / add / del");
        }
        return executeConfigSetById(context, configId, actionOrValue, RoomConfigScope.BREAKTHROUGH);
    }

    private static int executeConfigBreakthroughActionWithValue(CommandContext<CommandSourceStack> context) {
        String configId = StringArgumentType.getString(context, "configId");
        String action = StringArgumentType.getString(context, "actionOrValue");
        String value = StringArgumentType.getString(context, "value");
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "add" -> executeConfigGroupMutation(context, configId, value, true, RoomConfigScope.BREAKTHROUGH);
            case "del" -> executeConfigGroupMutation(context, configId, value, false, RoomConfigScope.BREAKTHROUGH);
            default -> executeConfigSetById(context, configId, value, RoomConfigScope.BREAKTHROUGH);
        };
    }

    private static int executeConfigDeathmatchActionOrValue(CommandContext<CommandSourceStack> context) {
        String configId = StringArgumentType.getString(context, "configId");
        String actionOrValue = StringArgumentType.getString(context, "actionOrValue");
        RoomConfigDescriptor descriptor = resolveDescriptor(configId);
        if (descriptor == null) {
            return replyFailure(context.getSource(), "未找到配置项: " + configId);
        }
        if (descriptor.supportsGroupOperations()) {
            String action = actionOrValue == null ? "" : actionOrValue.trim().toLowerCase(Locale.ROOT);
            if ("list".equals(action)) {
                return executeConfigGroupListById(context, configId, RoomConfigScope.DEATHMATCH);
            }
            return replyFailure(context.getSource(), "组配置仅支持 list / add / del");
        }
        return executeConfigSetById(context, configId, actionOrValue, RoomConfigScope.DEATHMATCH);
    }

    private static int executeConfigDeathmatchActionWithValue(CommandContext<CommandSourceStack> context) {
        String configId = StringArgumentType.getString(context, "configId");
        String action = StringArgumentType.getString(context, "actionOrValue");
        String value = StringArgumentType.getString(context, "value");
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "add" -> executeConfigGroupMutation(context, configId, value, true, RoomConfigScope.DEATHMATCH);
            case "del" -> executeConfigGroupMutation(context, configId, value, false, RoomConfigScope.DEATHMATCH);
            default -> executeConfigSetById(context, configId, value, RoomConfigScope.DEATHMATCH);
        };
    }

    private static int executeConfigGunmasterActionOrValue(CommandContext<CommandSourceStack> context) {
        String configId = StringArgumentType.getString(context, "configId");
        String actionOrValue = StringArgumentType.getString(context, "actionOrValue");
        RoomConfigDescriptor descriptor = resolveDescriptor(configId);
        if (descriptor == null) {
            return replyFailure(context.getSource(), "未找到配置项: " + configId);
        }
        if (descriptor.supportsGroupOperations()) {
            String action = actionOrValue == null ? "" : actionOrValue.trim().toLowerCase(Locale.ROOT);
            if ("list".equals(action)) {
                return executeConfigGroupListById(context, configId, RoomConfigScope.GUNMASTER);
            }
            return replyFailure(context.getSource(), "组配置仅支持 list / add / del");
        }
        return executeConfigSetById(context, configId, actionOrValue, RoomConfigScope.GUNMASTER);
    }

    private static int executeConfigGunmasterActionWithValue(CommandContext<CommandSourceStack> context) {
        String configId = StringArgumentType.getString(context, "configId");
        String action = StringArgumentType.getString(context, "actionOrValue");
        String value = StringArgumentType.getString(context, "value");
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "add" -> executeConfigGroupMutation(context, configId, value, true, RoomConfigScope.GUNMASTER);
            case "del" -> executeConfigGroupMutation(context, configId, value, false, RoomConfigScope.GUNMASTER);
            default -> executeConfigSetById(context, configId, value, RoomConfigScope.GUNMASTER);
        };
    }

    private static int executeConfigGroupListById(CommandContext<CommandSourceStack> context, String configId, RoomConfigScope expectedScope) {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        if (room == null) {
            return replyFailure(context.getSource(), "未找到房间: " + roomId);
        }
        RoomConfigDescriptor descriptor = resolveGroupCommandDescriptor(configId);
        if (descriptor == null) {
            return replyFailure(context.getSource(), "未找到可增删的组配置项");
        }
        if (!isDescriptorInExpectedScope(room, descriptor, expectedScope)) {
            return replyFailure(context.getSource(), "该配置项不属于当前配置分区");
        }
        try {
            List<String> values = RoomDefinitionGroupConfigEditor.list(room, descriptor.configId());
            sendLine(context.getSource(), Component.literal("组配置: " + RoomValidationMessageFormatter.labelForConfigId(descriptor.configId())).withStyle(ChatFormatting.AQUA));
            if (values.isEmpty()) {
                sendLine(context.getSource(), Component.literal("(empty)").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                for (String entry : values) {
                    sendLine(context.getSource(), Component.literal(entry).withStyle(ChatFormatting.WHITE));
                }
            }
            return 1;
        } catch (Exception exception) {
            String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            return replyFailure(context.getSource(), "读取组配置失败: " + detail);
        }
    }

    private static int executeConfigSetById(
        CommandContext<CommandSourceStack> context,
        String configId,
        String value,
        RoomConfigScope expectedScope
    ) {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        if (room == null) {
            return replyFailure(context.getSource(), "未找到房间: " + roomId);
        }
        RoomConfigDescriptor descriptor = resolveDescriptor(configId);
        if (descriptor == null || descriptor.supportsGroupOperations()) {
            return replyFailure(context.getSource(), "该配置项不支持 set，请改用 group 子命令");
        }
        if (!isDescriptorInExpectedScope(room, descriptor, expectedScope)) {
            return replyFailure(context.getSource(), "该配置项不属于当前配置分区");
        }
        try {
            ServerPlayer editor = context.getSource().getPlayer();
            RoomDefinition updated = RoomDefinitionScalarConfigEditor.apply(room, descriptor, value, editor);
            RoomDefinition persisted = ConquestDataManager.of(context.getSource().getServer()).roomDefinitions().saveDefinition(updated);
            manager.hotUpdateRoomDefinition(persisted, context.getSource().getServer().getTickCount());
            return replyInfo(context.getSource(), "已更新配置: " + RoomValidationMessageFormatter.labelForConfigId(descriptor.configId()));
        } catch (Exception exception) {
            String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            return replyFailure(context.getSource(), "配置更新失败: " + detail);
        }
    }

    private static int executeConfigReload(CommandContext<CommandSourceStack> context) {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        try {
            RoomDefinition persisted = ConquestDataManager.of(context.getSource().getServer())
                .roomDefinitions()
                .reloadDefinitionFromFile(roomId)
                .orElse(null);
            if (persisted == null) {
                return replyFailure(context.getSource(), "未找到配置文件: " + roomId);
            }
            manager.hotUpdateRoomDefinition(persisted, context.getSource().getServer().getTickCount());
            return replyInfo(context.getSource(), "已按配置文件重新读取并刷新房间: " + persisted.roomId());
        } catch (Exception exception) {
            String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            return replyFailure(context.getSource(), "配置重载失败: " + detail);
        }
    }

    private static int executeConfigGroupMutation(
        CommandContext<CommandSourceStack> context,
        String configId,
        String value,
        boolean add,
        RoomConfigScope expectedScope
    ) {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        if (room == null) {
            return replyFailure(context.getSource(), "未找到房间: " + roomId);
        }
        RoomConfigDescriptor descriptor = resolveGroupCommandDescriptor(configId);
        if (descriptor == null) {
            return replyFailure(context.getSource(), "未找到可增删的组配置项");
        }
        if (!isDescriptorInExpectedScope(room, descriptor, expectedScope)) {
            return replyFailure(context.getSource(), "该配置项不属于当前配置分区");
        }
        try {
            ServerPlayer editor = context.getSource().getPlayer();
            RoomDefinition updated = add
                ? RoomDefinitionGroupConfigEditor.add(room, descriptor.configId(), value, editor)
                : RoomDefinitionGroupConfigEditor.delete(room, descriptor.configId(), value, editor);
            RoomDefinition persisted = ConquestDataManager.of(context.getSource().getServer()).roomDefinitions().saveDefinition(updated);
            manager.hotUpdateRoomDefinition(persisted, context.getSource().getServer().getTickCount());
            return replyInfo(context.getSource(), (add ? "已添加组配置项: " : "已删除组配置项: ") + descriptor.configId());
        } catch (Exception exception) {
            String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            return replyFailure(context.getSource(), "组配置更新失败: " + detail);
        }
    }

    private static boolean isDescriptorInExpectedScope(RoomDefinition room, RoomConfigDescriptor descriptor, RoomConfigScope expectedScope) {
        if (descriptor == null || expectedScope == null) {
            return false;
        }
        RoomConfigScope actualScope = RoomConfigDescriptorRegistry.scopeOf(descriptor);
        if (actualScope == expectedScope) {
            return true;
        }
        return room != null
            && room.isArenaGameplay()
            && isArenaScope(actualScope)
            && isArenaScope(expectedScope);
    }

    private static boolean isArenaScope(RoomConfigScope scope) {
        return scope == RoomConfigScope.DEATHMATCH || scope == RoomConfigScope.GUNMASTER;
    }

    private static int sendTeamSummary(CommandSourceStack source, RoomCoreRuntimeManager manager, String roomId, ServerPlayer player) {
        Optional<RoomDefinition> room = manager.findRoom(roomId);
        if (room.isEmpty()) {
            return CommandFeedbacks.failure(source, "未找到房间: " + roomId);
        }
        String team = manager.findRoomTeam(roomId, player.getUUID())
            .or(() -> manager.findQueueTeam(roomId, player.getUUID()))
            .map(Enum::name)
            .orElse("-");
        String squad = manager.findPlayerSquadLabel(player.getUUID()).orElse("-");
        return CommandFeedbacks.info(source, "房间 " + roomId + " | 阵营: " + team + " | 小队: " + squad);
    }

    private static RoomTeamId parseTeamId(String raw) throws CommandSyntaxException {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "campa", "camp_a", "a", "t" -> RoomTeamId.CAMP_A;
            case "campb", "camp_b", "b", "s" -> RoomTeamId.CAMP_B;
            default -> throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect().create(raw);
        };
    }

    private static String normalizeRoomId(String roomId) {
        return roomId == null ? "" : roomId.trim();
    }

    private static CompletableFuture<Suggestions> suggestRoomIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> roomIds = RoomCoreRuntimeManager.of(context.getSource().getServer()).listRooms().stream()
            .map(RoomDefinition::roomId)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        return SharedSuggestionProvider.suggest(roomIds, builder);
    }

    private static CompletableFuture<Suggestions> suggestTeamIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("campa", "campb"), builder);
    }

    private static CompletableFuture<Suggestions> suggestCommonConfigIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(RoomConfigDescriptorRegistry.legacyCommonCommandIds(), builder);
    }

    private static CompletableFuture<Suggestions> suggestCommonConfigValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String configId = StringArgumentType.getString(context, "configId");
        RoomConfigDescriptor descriptor = resolveDescriptor(configId);
        return suggestDescriptorValues(context.getSource(), descriptor, builder);
    }

    private static CompletableFuture<Suggestions> suggestCommonActionOrValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        RoomConfigDescriptor descriptor = resolveDescriptor(StringArgumentType.getString(context, "configId"));
        if (descriptor == null) {
            return builder.buildFuture();
        }
        if (descriptor.supportsGroupOperations()) {
            return SharedSuggestionProvider.suggest(List.of("list", "add", "del"), builder);
        }
        return suggestDescriptorValues(context.getSource(), descriptor, builder);
    }

    private static CompletableFuture<Suggestions> suggestCommonActionValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        RoomConfigDescriptor descriptor = resolveDescriptor(StringArgumentType.getString(context, "configId"));
        if (descriptor == null) {
            return builder.buildFuture();
        }
        String action = StringArgumentType.getString(context, "actionOrValue");
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!descriptor.supportsGroupOperations()) {
            return suggestDescriptorValues(context.getSource(), descriptor, builder);
        }
        if ("del".equals(normalized)) {
            return suggestConfigGroupDeleteValues(context, builder);
        }
        if ("add".equals(normalized)) {
            return suggestConfigGroupAddValues(context, builder);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPrivateConfigIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(RoomConfigDescriptorRegistry.legacyConquestCommandIds(), builder);
    }

    private static CompletableFuture<Suggestions> suggestBreakthroughConfigIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(RoomConfigDescriptorRegistry.legacyBreakthroughCommandIds(), builder);
    }

    private static CompletableFuture<Suggestions> suggestDeathmatchConfigIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(RoomConfigDescriptorRegistry.legacyDeathmatchCommandIds(), builder);
    }

    private static CompletableFuture<Suggestions> suggestGunmasterConfigIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(RoomConfigDescriptorRegistry.legacyGunmasterCommandIds(), builder);
    }

    private static CompletableFuture<Suggestions> suggestPrivateActionOrValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        RoomConfigDescriptor descriptor = resolveDescriptor(StringArgumentType.getString(context, "configId"));
        if (descriptor == null) {
            return builder.buildFuture();
        }
        if (descriptor.supportsGroupOperations()) {
            return SharedSuggestionProvider.suggest(List.of("list", "add", "del"), builder);
        }
        return suggestDescriptorValues(context.getSource(), descriptor, builder);
    }

    private static CompletableFuture<Suggestions> suggestBreakthroughActionOrValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestPrivateActionOrValue(context, builder);
    }

    private static CompletableFuture<Suggestions> suggestDeathmatchActionOrValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestPrivateActionOrValue(context, builder);
    }

    private static CompletableFuture<Suggestions> suggestGunmasterActionOrValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestPrivateActionOrValue(context, builder);
    }

    private static CompletableFuture<Suggestions> suggestPrivateActionValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        RoomConfigDescriptor descriptor = resolveDescriptor(StringArgumentType.getString(context, "configId"));
        if (descriptor == null) {
            return builder.buildFuture();
        }
        String action = StringArgumentType.getString(context, "actionOrValue");
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!descriptor.supportsGroupOperations()) {
            return suggestDescriptorValues(context.getSource(), descriptor, builder);
        }
        if ("del".equals(normalized)) {
            return suggestConfigGroupDeleteValues(context, builder);
        }
        if ("add".equals(normalized)) {
            return suggestConfigGroupAddValues(context, builder);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestBreakthroughActionValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestPrivateActionValue(context, builder);
    }

    private static CompletableFuture<Suggestions> suggestDeathmatchActionValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestPrivateActionValue(context, builder);
    }

    private static CompletableFuture<Suggestions> suggestGunmasterActionValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestPrivateActionValue(context, builder);
    }

    private static CompletableFuture<Suggestions> suggestConfigGroupAddValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        RoomConfigDescriptor descriptor = resolveGroupCommandDescriptor(StringArgumentType.getString(context, "configId"));
        if (descriptor == null) {
            return builder.buildFuture();
        }
        if (supportsHeroKitSuggestions(descriptor)) {
            List<String> heroKitIds = new ArrayList<>(ConquestDataManager.of(context.getSource().getServer()).heroKits().get().getHeroKitsById().keySet());
            heroKitIds.sort(String.CASE_INSENSITIVE_ORDER);
            return SharedSuggestionProvider.suggest(heroKitIds, builder);
        }
        if (supportsShellingSuggestions(descriptor)) {
            List<String> shellingIds = new ArrayList<>(ConquestDataManager.of(context.getSource().getServer()).shellings().get().getShellingsById().keySet());
            shellingIds.sort(String.CASE_INSENSITIVE_ORDER);
            return SharedSuggestionProvider.suggest(shellingIds, builder);
        }
        if (supportsRoomFixedLoadoutSuggestions(descriptor)) {
            List<String> ids = RoomFixedLoadoutRules.suggestionIdsForSlot(
                ConquestDataManager.of(context.getSource().getServer()).equipmentData().getData(),
                RoomFixedLoadoutRules.resolveSlotByConfigId(descriptor.configId())
            );
            return SharedSuggestionProvider.suggest(ids, builder);
        }
        if (supportsGunmasterWeaponPoolSuggestions(descriptor)) {
            List<String> ids = RoomGunmasterWeaponPoolRules.suggestionIds(
                ConquestDataManager.of(context.getSource().getServer()).equipmentData().getData()
            );
            return SharedSuggestionProvider.suggest(ids, builder);
        }
        List<String> suggestions = RoomConfigDescriptorRegistry.suggestionValues(descriptor.configId());
        if (!suggestions.isEmpty()) {
            return SharedSuggestionProvider.suggest(suggestions, builder);
        }
        if (descriptor.configId().toLowerCase(Locale.ROOT).contains("boundary")) {
            return SharedSuggestionProvider.suggest(List.of("here", "0,0", "0,64,0"), builder);
        }
        return SharedSuggestionProvider.suggest(List.of("here", "0,64,0"), builder);
    }

    private static CompletableFuture<Suggestions> suggestConfigGroupDeleteValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        RoomConfigDescriptor descriptor = resolveGroupCommandDescriptor(StringArgumentType.getString(context, "configId"));
        if (room == null || descriptor == null) {
            return builder.buildFuture();
        }
        try {
            List<String> values = new ArrayList<>(RoomDefinitionGroupConfigEditor.list(room, descriptor.configId()));
            if (!values.contains("all")) {
                values.add("all");
            }
            return SharedSuggestionProvider.suggest(values, builder);
        } catch (Exception exception) {
            return builder.buildFuture();
        }
    }

    private static int executePlaceholder(CommandContext<CommandSourceStack> context, String message) {
        return CommandFeedbacks.info(context.getSource(), message);
    }

    private static int replyInfo(CommandSourceStack source, String message) {
        return CommandFeedbacks.info(source, message);
    }

    private static int replyFailure(CommandSourceStack source, String message) {
        return CommandFeedbacks.failure(source, message);
    }

    private static String describeSingleJoinMessage(
        String roomId,
        RoomDefinition room,
        RoomJoinResult result,
        RoomCoreRuntimeManager manager,
        java.util.UUID playerId
    ) {
        if (result == null) {
            return "已加入房间: " + roomId;
        }
        return switch (result.kind()) {
            case MIDJOIN_RUNNING -> "已加入进行中的对局: " + roomId;
            case MIDJOIN_ENDING -> "已加入当前对局结算: " + roomId;
            case REJOIN_RUNNING -> "已重连进行中的对局: " + roomId;
            case REJOIN_ENDING -> "已重连当前对局结算: " + roomId;
            case MATCHING -> {
                String team = manager.findQueueTeam(roomId, playerId).map(Enum::name).orElse("-");
                yield "已加入房间匹配: " + roomId + " | 分配阵营: " + team;
            }
        };
    }

    private static void sendLine(CommandSourceStack source, Component line) {
        CommandFeedbacks.line(source, line);
    }

    private static RoomConfigDescriptor resolveDescriptor(String rawConfigId) {
        return RoomConfigDescriptorRegistry.find(rawConfigId).orElse(null);
    }

    private static RoomConfigDescriptor resolveGroupCommandDescriptor(String rawConfigId) {
        RoomConfigDescriptor descriptor = resolveDescriptor(rawConfigId);
        if (descriptor == null || !isSupportedGroupCommandDescriptor(descriptor)) {
            return null;
        }
        return descriptor;
    }

    private static boolean isSupportedGroupCommandDescriptor(RoomConfigDescriptor descriptor) {
        if (descriptor == null || !descriptor.supportsGroupOperations()) {
            return false;
        }
        return !"capturePoints".equalsIgnoreCase(descriptor.configId());
    }

    private static CompletableFuture<Suggestions> suggestDescriptorValues(
        CommandSourceStack source,
        RoomConfigDescriptor descriptor,
        SuggestionsBuilder builder
    ) {
        if (descriptor == null) {
            return builder.buildFuture();
        }
        if (supportsTemplateMapSuggestions(descriptor)) {
            List<String> templates = ConquestDataManager.of(source.getServer()).roomTemplates().listTemplateIds();
            if (!templates.isEmpty()) {
                return SharedSuggestionProvider.suggest(templates, builder);
            }
        }
        if (supportsHeroKitSuggestions(descriptor)) {
            List<String> heroKitIds = new ArrayList<>(ConquestDataManager.of(source.getServer()).heroKits().get().getHeroKitsById().keySet());
            heroKitIds.sort(String.CASE_INSENSITIVE_ORDER);
            return SharedSuggestionProvider.suggest(heroKitIds, builder);
        }
        if (supportsEquipmentSetSuggestions(descriptor)) {
            List<String> equipmentSetIds = new ArrayList<>(ConquestDataManager.of(source.getServer()).equipmentSets().get().getEntriesById().keySet());
            equipmentSetIds.sort(String.CASE_INSENSITIVE_ORDER);
            return SharedSuggestionProvider.suggest(equipmentSetIds, builder);
        }
        if (supportsRoomFixedLoadoutSuggestions(descriptor)) {
            List<String> ids = RoomFixedLoadoutRules.suggestionIdsForSlot(
                ConquestDataManager.of(source.getServer()).equipmentData().getData(),
                RoomFixedLoadoutRules.resolveSlotByConfigId(descriptor.configId())
            );
            return SharedSuggestionProvider.suggest(ids, builder);
        }
        if (supportsGunmasterWeaponPoolSuggestions(descriptor)) {
            List<String> ids = RoomGunmasterWeaponPoolRules.suggestionIds(
                ConquestDataManager.of(source.getServer()).equipmentData().getData()
            );
            return SharedSuggestionProvider.suggest(ids, builder);
        }
        if (supportsShellingSuggestions(descriptor)) {
            List<String> shellingIds = new ArrayList<>(ConquestDataManager.of(source.getServer()).shellings().get().getShellingsById().keySet());
            shellingIds.sort(String.CASE_INSENSITIVE_ORDER);
            return SharedSuggestionProvider.suggest(shellingIds, builder);
        }
        return SharedSuggestionProvider.suggest(RoomConfigDescriptorRegistry.suggestionValues(descriptor.configId()), builder);
    }

    private static boolean supportsTemplateMapSuggestions(RoomConfigDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        return "mapId".equalsIgnoreCase(descriptor.configId())
            || "btMapID".equalsIgnoreCase(descriptor.configId())
            || "dmMapID".equalsIgnoreCase(descriptor.configId())
            || "gmMapID".equalsIgnoreCase(descriptor.configId())
            || descriptor.newPath().endsWith(".mapId");
    }

    private static boolean supportsHeroKitSuggestions(RoomConfigDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        String configId = descriptor.configId();
        return "supportCampAHeroKits".equalsIgnoreCase(configId)
            || "supportCampBHeroKits".equalsIgnoreCase(configId);
    }

    private static boolean supportsShellingSuggestions(RoomConfigDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        String configId = descriptor.configId();
        return "supportCampAShellings".equalsIgnoreCase(configId)
            || "supportCampBShellings".equalsIgnoreCase(configId);
    }

    private static boolean supportsGunmasterWeaponPoolSuggestions(RoomConfigDescriptor descriptor) {
        return descriptor != null && RoomGunmasterWeaponPoolRules.isWeaponPoolConfigId(descriptor.configId());
    }

    private static boolean supportsEquipmentSetSuggestions(RoomConfigDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        String configId = descriptor.configId();
        return "campAEquipmentSetId".equalsIgnoreCase(configId)
            || "campBEquipmentSetId".equalsIgnoreCase(configId);
    }

    private static boolean supportsRoomFixedLoadoutSuggestions(RoomConfigDescriptor descriptor) {
        return descriptor != null && RoomFixedLoadoutRules.resolveSlotByConfigId(descriptor.configId()) != null;
    }

    private static String stringifyConfigValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence || value.getClass().isEnum()) {
            return String.valueOf(value);
        }
        if (value instanceof RoomPosition position) {
            return position.x() + "," + position.y() + "," + position.z();
        }
        return GSON.toJson(value);
    }

    private static boolean confirmRoomAdminAction(CommandSourceStack source, String actionToken) {
        String key = buildRoomAdminConfirmationKey(source, actionToken);
        long now = System.currentTimeMillis();
        Long deadline = ROOM_ADMIN_CONFIRMATIONS.get(key);
        if (deadline != null && deadline >= now) {
            ROOM_ADMIN_CONFIRMATIONS.remove(key);
            return true;
        }
        ROOM_ADMIN_CONFIRMATIONS.put(key, now + ROOM_ADMIN_CONFIRM_WINDOW_MS);
        return false;
    }

    private static String buildRoomAdminConfirmationKey(CommandSourceStack source, String actionToken) {
        ServerPlayer player = source.getPlayer();
        String actor = source.getTextName();
        if (player != null) {
            UUID uuid = player.getUUID();
            if (uuid != null) {
                actor = "player:" + uuid;
            }
        }
        return actor + "::" + (actionToken == null ? "" : actionToken.trim().toLowerCase(Locale.ROOT));
    }

    private static int executeMergeWeaponWhitelist(CommandContext<CommandSourceStack> context) {
        String roomId = normalizeRoomId(StringArgumentType.getString(context, "roomId"));
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        if (!manager.isInitialized()) {
            return replyFailure(context.getSource(), ROOM_SERVICE_NOT_INITIALIZED);
        }
        RoomDefinition room = manager.findRoom(roomId).orElse(null);
        if (room == null) {
            return replyFailure(context.getSource(), "未找到房间: " + roomId);
        }
        RoomCommonConfig common = room.commonConfig();

        List<List<String>> campA = List.of(
            common.campAPrimaryWeaponIds(),
            common.campASecondaryWeaponIds(),
            common.campAMeleeWeaponIds(),
            common.campAGadget1Ids(),
            common.campAGadget2Ids(),
            common.campAThrowableIds()
        );
        List<List<String>> campB = List.of(
            common.campBPrimaryWeaponIds(),
            common.campBSecondaryWeaponIds(),
            common.campBMeleeWeaponIds(),
            common.campBGadget1Ids(),
            common.campBGadget2Ids(),
            common.campBThrowableIds()
        );

        List<List<String>> merged = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            LinkedHashSet<String> union = new LinkedHashSet<>(campA.get(i));
            union.addAll(campB.get(i));
            merged.add(union.isEmpty() ? List.of() : List.copyOf(union));
        }

        RoomCommonConfig updatedCommon = new RoomCommonConfig(
            common.matchingConfig(),
            common.dimensionId(),
            common.queueSpawn(),
            common.respawnDelaySeconds(),
            common.vehicleBackend(),
            common.playerStateConfig(),
            common.outsideBoundaryDeathSeconds(),
            common.aircraftOutsideBoundaryCleanupSeconds(),
            common.playerMaxY(),
            common.aircraftPassengerMaxY(),
            common.campADisplayName(),
            common.campBDisplayName(),
            common.campAVoicePack(),
            common.campBVoicePack(),
            common.progressSpeed(),
            common.resultConfig(),
            common.disableHunger(),
            common.enableAutoTeamSwitchPrompt(),
            common.disableInventoryChange(),
            common.enableGoatHorn(),
            common.enablePlayerDowned(),
            common.enableRespawnCountdownWhileDowned(),
            common.enableBackAgainstWall(),
            common.enableRandomLoadout(),
            common.enableRandomAttachments(),
            common.enableParachute(),
            common.enableDeathSupportDrop(),
            common.enableGiveArrow(),
            common.disableUndyingTotem(),
            common.allowPlayerCampSwitch(),
            common.supportCampAHeroKits(),
            common.supportCampBHeroKits(),
            common.supportCampAShellings(),
            common.supportCampBShellings(),
            merged.get(0), merged.get(1), merged.get(2),
            merged.get(3), merged.get(4), merged.get(5),
            merged.get(0), merged.get(1), merged.get(2),
            merged.get(3), merged.get(4), merged.get(5),
            common.campAEquipmentSetId(),
            common.campBEquipmentSetId(),
            common.mapName(),
            common.roomDescription()
        );

        RoomDefinition updated = new RoomDefinition(
            room.roomId(), room.displayName(), room.gameplayId(),
            room.lifecycleState(), updatedCommon, room.gameplayConfig()
        );

        try {
            RoomDefinition persisted = ConquestDataManager.of(context.getSource().getServer())
                .roomDefinitions()
                .saveDefinition(updated);
            manager.hotUpdateRoomDefinition(persisted, context.getSource().getServer().getTickCount());
            return replyInfo(context.getSource(), "已合并房间 " + roomId + " 的武器白名单：6个槽位已统一两个阵营的限制数据");
        } catch (Exception exception) {
            String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            return replyFailure(context.getSource(), "合并武器白名单失败: " + detail);
        }
    }
}
