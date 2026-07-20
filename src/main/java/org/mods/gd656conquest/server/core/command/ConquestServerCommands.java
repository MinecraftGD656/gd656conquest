package org.mods.gd656conquest.server.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.AdminDebugOverlayPacket;
import org.mods.gd656conquest.network.packet.HudDebugPreviewPacket;
import org.mods.gd656conquest.network.packet.MarkerDebugPreviewPacket;
import org.mods.gd656conquest.network.packet.PlayerAnimatorDebugPacket;
import org.mods.gd656conquest.network.packet.RoomDownedStatePacket;
import org.mods.gd656conquest.common.entity.PlayerAnimatorDebugDimensionsState;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.playerdata.LegacySurvivalDataExportService;
import org.mods.gd656conquest.server.data.serverconfig.ServerConfigDataModel;
import org.mods.gd656conquest.server.data.serverconfig.ServerConfigStore;
import org.mods.gd656conquest.server.compat.tacz.TaczShellingTestService;
import org.mods.gd656conquest.server.core.command.debug.VehicleDebugCommands;
import org.mods.gd656conquest.server.facility.parkour.ParkourCommands;
import org.mods.gd656conquest.server.facility.parkour.ParkourRuntimeManager;
import org.mods.gd656conquest.server.facility.range.RangeCommands;
import org.mods.gd656conquest.server.facility.support.EquipmentSetCommands;
import org.mods.gd656conquest.server.facility.support.HeroKitCommands;
import org.mods.gd656conquest.server.facility.support.ShellingCommands;
import org.mods.gd656conquest.server.loadout.LoadoutCommands;
import org.mods.gd656conquest.server.pack.PackValidationService;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.player.lock.ServerPlayerLockService;
import org.mods.gd656conquest.server.player.state.HubDimensionMigrationService;
import org.mods.gd656conquest.common.player.lock.PlayerLockStrength;
import org.mods.gd656conquest.server.room.entry.RoomCommands;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomSessionTransitionService;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.shared.text.ServerText;
import org.mods.gd656conquest.server.social.party.presentation.PartyCommands;
import org.mods.gd656conquest.server.title.PlayerTitleCommands;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ConquestServerCommands {
    private static final int MARKTEST_PREVIEW_DURATION_MS = 10_000;
    private static final int HUDTEST_PREVIEW_DURATION_MS = 10_000;
    private static final int PLAYER_ANIMATOR_PREVIEW_DURATION_MS = 10_000;
    private static final long HUB_MIGRATION_CONFIRM_WINDOW_MS = 5_000L;
    private static final Map<String, Long> HUB_MIGRATION_CONFIRMATIONS = new ConcurrentHashMap<>();
    private static final List<String> SERVER_CONFIG_KEYS = List.of(
        "boolOverworldInvulnerable",
        "boolRestrictConquestDimension",
        "boolNaturalRegeneration",
        "passiveRegenIntervalTicks",
        "passiveRegenStartDelaySeconds",
        "MaxScopePistol",
        "MaxScopeSniper",
        "MaxScopeRifle",
        "MaxScopeShotgun",
        "MaxScopeSmg",
        "MaxScopeRpg",
        "MaxScopeMg",
        "vehicleBackend",
        "overworldSpawn",
        "rangeJoinSpawn",
        "rangeLeaveSpawn",
        "parkourStartPos",
        "parkourEndPos",
        "parkourCompletionTitleId",
        "parkourBestTitleId",
        "enablePackValidation",
        "missileKillRadius"
    );

    private ConquestServerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildRoot("gd656conquest"));
        dispatcher.register(buildRoot("gdc"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String literal) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(literal);
        root.then(buildPlayerTree());
        root.then(ParkourCommands.buildTree());
        root.then(RangeCommands.buildTree());
        root.then(RoomCommands.buildTree());
        root.then(LoadoutCommands.buildTree());
        root.then(EquipmentSetCommands.buildTree());
        root.then(HeroKitCommands.buildTree());
        root.then(ShellingCommands.buildTree());
        root.then(PlayerTitleCommands.buildTree());
        root.then(PartyCommands.buildTree());
        root.then(ActionCommands.buildTree());

        root.then(buildConfigAliasTree());
        root.then(buildPackAliasTree());
        root.then(buildDebugAliasTree());
        root.then(buildHubAliasTree());
        root.then(buildLevelAliasTree());
        root.then(RoomCommands.buildRedeployTree());
        root.then(LoadoutCommands.buildCompatibilityTree());
        
        root.then(Commands.literal("migrate")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                var result = org.mods.gd656conquest.server.data.roomdefinition.RoomDefinitionMigrator.migrateLegacyRooms(context.getSource().getServer());
                for (String msg : result.messages()) {
                    context.getSource().sendSuccess(() -> Component.literal(msg), false);
                }
                context.getSource().sendSuccess(() -> Component.literal("迁移完成: 成功 " + result.successCount() + " 个, 失败 " + result.failureCount() + " 个"), true);
                return result.successCount();
            })
        );
        return root;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildPlayerTree() {
        return Commands.literal("player")
            .then(buildHubAliasTree())
            .then(buildLevelAliasTree());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildConfigAliasTree() {
        return Commands.literal("config")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("get")
                .executes(ConquestServerCommands::executeConfigGetAll)
                .then(Commands.argument("configId", StringArgumentType.string())
                    .suggests(ConquestServerCommands::suggestServerConfigIds)
                    .executes(ConquestServerCommands::executeConfigGetSingle)))
            .then(Commands.literal("set")
                .then(Commands.argument("configId", StringArgumentType.string())
                    .suggests(ConquestServerCommands::suggestServerConfigIds)
                    .then(Commands.argument("value", StringArgumentType.string())
                        .suggests(ConquestServerCommands::suggestServerConfigValues)
                        .executes(ConquestServerCommands::executeConfigSet))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildPackAliasTree() {
        return Commands.literal("pack")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("update")
                .executes(ConquestServerCommands::executePackUpdate))
            .then(Commands.literal("list")
                .executes(ConquestServerCommands::executePackList))
            .then(Commands.literal("del")
                .then(Commands.argument("fileName", StringArgumentType.string())
                    .suggests(ConquestServerCommands::suggestPackWhitelistFileNames)
                    .executes(ConquestServerCommands::executePackDelete)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildDebugAliasTree() {
        return Commands.literal("debug")
            .requires(source -> source.hasPermission(2))
            .then(buildDebugOverlayTree())
            .then(buildDebugPreviewTree())
            .then(buildDebugPlayerTree())
            .then(buildDebugRoomTree())
            .then(buildDebugDataTree())
            .then(buildDebugShellingTree())
            .then(buildDebugToolsTree())
            .then(VehicleDebugCommands.buildTree());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildDebugOverlayTree() {
        return Commands.literal("overlay")
            .then(Commands.literal("on").executes(ConquestServerCommands::executeDebugOn))
            .then(Commands.literal("off").executes(ConquestServerCommands::executeDebugOff));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildDebugPreviewTree() {
        return Commands.literal("preview")
            .then(Commands.literal("marker").executes(ConquestServerCommands::executeDebugMarktest))
            .then(Commands.literal("hud")
                .then(Commands.literal("conquest").executes(ConquestServerCommands::executeDebugConquestHud))
                .then(Commands.literal("breakthrough")
                    .executes(ConquestServerCommands::executeDebugBreakthroughHud)
                    .then(Commands.literal("backtowall")
                        .executes(ConquestServerCommands::executeDebugBreakthroughBackToWallAttackHud)
                        .then(Commands.literal("attack").executes(ConquestServerCommands::executeDebugBreakthroughBackToWallAttackHud))
                        .then(Commands.literal("defend").executes(ConquestServerCommands::executeDebugBreakthroughBackToWallDefendHud)))
                    .then(Commands.literal("burnboats").executes(ConquestServerCommands::executeDebugBreakthroughBurnBoatsHud))))
            .then(Commands.literal("playeranimator").executes(ConquestServerCommands::executeDebugPlayerAnimator));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildDebugPlayerTree() {
        return Commands.literal("player")
            .then(Commands.literal("lock")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("strength", IntegerArgumentType.integer(0, PlayerLockStrength.RESERVED.level()))
                        .executes(ConquestServerCommands::executeDebugLock))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildDebugRoomTree() {
        return Commands.literal("room")
            .then(Commands.literal("updateConfig").executes(context -> CommandFeedbacks.failure(context.getSource(), "此命令已被废弃，请使用 /gdc room config migrate")));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildDebugDataTree() {
        return Commands.literal("data")
            .then(Commands.literal("exportLegacySurvivalData").executes(ConquestServerCommands::executeDebugExportLegacySurvivalData));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildDebugShellingTree() {
        return Commands.literal("shellingtest")
            .then(Commands.argument("durationSeconds", DoubleArgumentType.doubleArg(0.05D))
                .then(Commands.argument("intervalSeconds", DoubleArgumentType.doubleArg(0.05D))
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.0D))
                        .then(Commands.argument("shellHeight", DoubleArgumentType.doubleArg(0.0D))
                            .then(Commands.argument("centerX", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("centerZ", DoubleArgumentType.doubleArg())
                                    .executes(ConquestServerCommands::executeDebugShellingTest)))
                            .then(Commands.literal("here")
                                .executes(ConquestServerCommands::executeDebugShellingTest))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildDebugToolsTree() {
        return Commands.literal("tools")
            .then(Commands.literal("screenshot")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(ConquestServerCommands::executeDebugScreenshot)));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildHubAliasTree() {
        return Commands.literal("hub")
            .executes(ConquestServerCommands::executeHub)
            .then(Commands.literal("migrateoverworld")
                .requires(source -> source.hasPermission(2))
                .executes(ConquestServerCommands::executeHubMigrateOverworld));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildLevelAliasTree() {
        return Commands.literal("level")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("get")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ConquestServerCommands::executeLevelGet)))
            .then(Commands.literal("set")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("level", IntegerArgumentType.integer(0, PlayerLevelService.MAX_LEVEL))
                        .executes(ConquestServerCommands::executeLevelSet))));
    }

    private static int executeHub(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RoomSessionTransitionService.ReturnToHubExecutionResult result = RoomSessionTransitionService.of(context.getSource().getServer())
            .returnToHub(player);
        if (result.denied()) {
            return CommandFeedbacks.info(context.getSource(), result.deniedMessage());
        }
        return CommandFeedbacks.info(context.getSource(), result.operatorMessage());
    }

    private static int executeHubMigrateOverworld(CommandContext<CommandSourceStack> context) {
        if (!confirmHubMigration(context.getSource())) {
            return CommandFeedbacks.info(context.getSource(), "请在5秒内再次输入 /gdc hub migrateoverworld");
        }
        try {
            HubDimensionMigrationService.MigrationResult result = new HubDimensionMigrationService(context.getSource().getServer())
                .migrateOverworldToHub();
            CommandFeedbacks.line(
                context.getSource(),
                ServerText.colored("源目录: " + result.sourcePath(), net.minecraft.ChatFormatting.YELLOW)
            );
            CommandFeedbacks.line(
                context.getSource(),
                ServerText.colored("目标目录: " + result.targetPath(), net.minecraft.ChatFormatting.YELLOW)
            );
            return CommandFeedbacks.info(context.getSource(), result.message());
        } catch (IllegalStateException exception) {
            return CommandFeedbacks.failure(context.getSource(), "主城维度迁移失败: " + exception.getMessage());
        }
    }

    private static boolean confirmHubMigration(CommandSourceStack source) {
        String key = buildHubMigrationConfirmationKey(source);
        long now = System.currentTimeMillis();
        Long deadline = HUB_MIGRATION_CONFIRMATIONS.get(key);
        if (deadline != null && deadline >= now) {
            HUB_MIGRATION_CONFIRMATIONS.remove(key);
            return true;
        }
        HUB_MIGRATION_CONFIRMATIONS.put(key, now + HUB_MIGRATION_CONFIRM_WINDOW_MS);
        return false;
    }

    private static String buildHubMigrationConfirmationKey(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            UUID uuid = player.getUUID();
            if (uuid != null) {
                return "player:" + uuid;
            }
        }
        return "source:" + source.getTextName();
    }

    private static int executeDebugOn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        manager.setPlayerDebugOverlayEnabled(player.getUUID(), true);
        String payload = manager.coreService().buildAdminDebugOverlayPayload(player).orElse("-");
        NetworkManager.sendToPlayer(player, new AdminDebugOverlayPacket(payload));
        manager.coreService().resyncPlayerPresentation(player, context.getSource().getServer().getTickCount());
        return CommandFeedbacks.info(context.getSource(), "-".equals(payload) ? "调试覆盖层已开启，当前维度暂无可显示房间数据" : "调试覆盖层已开启");
    }

    private static int executeDebugOff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(context.getSource().getServer());
        manager.setPlayerDebugOverlayEnabled(player.getUUID(), false);
        NetworkManager.sendToPlayer(player, new AdminDebugOverlayPacket("-"));
        manager.coreService().resyncPlayerPresentation(player, context.getSource().getServer().getTickCount());
        return CommandFeedbacks.info(context.getSource(), "调试覆盖层已关闭");
    }

    private static int executeDebugMarktest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NetworkManager.sendToPlayer(player, new MarkerDebugPreviewPacket(MARKTEST_PREVIEW_DURATION_MS));
        return CommandFeedbacks.info(context.getSource(), "标记样式调试预览已开启，持续 10 秒");
    }

    private static int executeDebugConquestHud(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NetworkManager.sendToPlayer(player, new HudDebugPreviewPacket("CONQUEST", HUDTEST_PREVIEW_DURATION_MS));
        return CommandFeedbacks.info(context.getSource(), "Conquest 顶部 HUD 调试预览已开启，持续 10 秒");
    }

    private static int executeDebugBreakthroughHud(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NetworkManager.sendToPlayer(player, new HudDebugPreviewPacket("BREAKTHROUGH", HUDTEST_PREVIEW_DURATION_MS));
        return CommandFeedbacks.info(context.getSource(), "Breakthrough 顶部 HUD 调试预览已开启，持续 10 秒");
    }

    private static int executeDebugBreakthroughBackToWallAttackHud(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NetworkManager.sendToPlayer(player, new HudDebugPreviewPacket("BREAKTHROUGH_BACK_TO_WALL_ATTACK", HUDTEST_PREVIEW_DURATION_MS));
        return CommandFeedbacks.info(context.getSource(), "背水一战 HUD 预览已开启，当前为进攻方视角，持续 10 秒");
    }

    private static int executeDebugBreakthroughBackToWallDefendHud(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NetworkManager.sendToPlayer(player, new HudDebugPreviewPacket("BREAKTHROUGH_BACK_TO_WALL_DEFEND", HUDTEST_PREVIEW_DURATION_MS));
        return CommandFeedbacks.info(context.getSource(), "背水一战 HUD 预览已开启，当前为防守方视角，持续 10 秒");
    }

    private static int executeDebugBreakthroughBurnBoatsHud(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NetworkManager.sendToPlayer(player, new HudDebugPreviewPacket("BREAKTHROUGH_BURN_BOATS", HUDTEST_PREVIEW_DURATION_MS));
        return CommandFeedbacks.info(context.getSource(), "破釜沉舟 HUD 预览已开启，持续 10 秒");
    }

    private static int executeDebugPlayerAnimator(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerAnimatorDebugDimensionsState.startPreview(player, PLAYER_ANIMATOR_PREVIEW_DURATION_MS);
        PlayerAnimatorDebugPacket packet = PlayerAnimatorDebugPacket.start(player.getUUID(), PLAYER_ANIMATOR_PREVIEW_DURATION_MS);
        for (ServerPlayer viewer : context.getSource().getServer().getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(viewer, packet);
        }
        NetworkManager.sendToPlayer(
            player,
            RoomDownedStatePacket.debugPreview(PLAYER_ANIMATOR_PREVIEW_DURATION_MS)
        );
        return CommandFeedbacks.info(context.getSource(), "玩家倒地模型调试已开启，持续 10 秒，所有在线玩家可见");
    }

    private static int executeDebugLock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int strengthLevel = IntegerArgumentType.getInteger(context, "strength");
        PlayerLockStrength strength = PlayerLockStrength.fromLevel(strengthLevel);
        long currentTick = context.getSource().getServer().getTickCount();
        ServerPlayerLockService lockService = ServerPlayerLockService.of(context.getSource().getServer());
        for (ServerPlayer target : targets) {
            lockService.applyDebugLock(target, strength, currentTick);
        }
        return CommandFeedbacks.info(
            context.getSource(),
            strength == PlayerLockStrength.NONE
                ? "已清除锁定调试，目标数: " + targets.size()
                : "已开启锁定调试，强度=" + strength.level() + "，持续 10 秒，目标数: " + targets.size()
        );
    }

    private static int executeDebugScreenshot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        ScreenshotServerHandler.requestScreenshot(context.getSource(), target);
        return 1;
    }

    private static int executeDebugExportLegacySurvivalData(CommandContext<CommandSourceStack> context) {
        LegacySurvivalDataExportService.ExportResult result =
            LegacySurvivalDataExportService.export(context.getSource().getServer());
        CommandFeedbacks.line(
            context.getSource(),
            ServerText.colored("导出文件: " + result.outputFile().toAbsolutePath(), net.minecraft.ChatFormatting.YELLOW)
        );
        return CommandFeedbacks.info(
            context.getSource(),
            "已导出旧生存服数据名单，命中玩家数: " + result.matchedPlayerCount()
        );
    }

    private static int executeDebugShellingTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        double durationSeconds = DoubleArgumentType.getDouble(context, "durationSeconds");
        double intervalSeconds = DoubleArgumentType.getDouble(context, "intervalSeconds");
        double radius = DoubleArgumentType.getDouble(context, "radius");
        double shellHeight = DoubleArgumentType.getDouble(context, "shellHeight");
        Vec3 center = resolveShellingCenter(player, context);
        float pitch = player.getXRot();
        float yaw = player.getYRot();
        TaczShellingTestService.StartResult result = TaczShellingTestService.of(context.getSource().getServer()).startDebugShelling(
            player,
            durationSeconds,
            intervalSeconds,
            radius,
            shellHeight,
            center,
            pitch,
            yaw
        );
        return result.success()
            ? CommandFeedbacks.info(context.getSource(), result.message())
            : CommandFeedbacks.failure(context.getSource(), result.message());
    }

    private static int executePlaceholder(CommandContext<CommandSourceStack> context, String message) {
        return CommandFeedbacks.info(context.getSource(), message);
    }



    private static int executeConfigGetAll(CommandContext<CommandSourceStack> context) {
        ServerConfigStore store = ConquestDataManager.of(context.getSource().getServer()).serverConfig();
        ServerConfigDataModel config = store.get();
        CommandFeedbacks.line(context.getSource(), ServerText.colored("========== 服务端配置 ==========", net.minecraft.ChatFormatting.GOLD));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("boolOverworldInvulnerable=" + store.isOverworldInvulnerableEnabled(), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("boolRestrictConquestDimension=" + store.isConquestDimensionRestricted(), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("boolNaturalRegeneration=" + store.isNaturalRegenerationEnabled(), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("passiveRegenIntervalTicks=" + store.getPassiveRegenIntervalTicks(), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("passiveRegenStartDelaySeconds=" + store.getPassiveRegenStartDelaySeconds(), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("MaxScopePistol=" + formatDecimal(store.getMaxScopePistol()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("MaxScopeSniper=" + formatDecimal(store.getMaxScopeSniper()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("MaxScopeRifle=" + formatDecimal(store.getMaxScopeRifle()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("MaxScopeShotgun=" + formatDecimal(store.getMaxScopeShotgun()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("MaxScopeSmg=" + formatDecimal(store.getMaxScopeSmg()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("MaxScopeRpg=" + formatDecimal(store.getMaxScopeRpg()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("MaxScopeMg=" + formatDecimal(store.getMaxScopeMg()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("vehicleBackend=" + store.getVehicleBackend().value(), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("overworldSpawn=" + formatPosition(config.getOverworldSpawn()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("rangeJoinSpawn=" + formatPosition(config.getRangeJoinSpawn()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("rangeLeaveSpawn=" + formatPosition(config.getRangeLeaveSpawn()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("parkourStartPos=" + formatPosition(config.getParkourStartPos()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("parkourEndPos=" + formatPosition(config.getParkourEndPos()), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("parkourCompletionTitleId=" + store.getParkourCompletionTitleId(), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("parkourBestTitleId=" + store.getParkourBestTitleId(), net.minecraft.ChatFormatting.AQUA));
        CommandFeedbacks.line(context.getSource(), ServerText.colored("enablePackValidation=" + store.isPackValidationEnabled(), net.minecraft.ChatFormatting.AQUA));
        return 1;
    }

    private static int executeConfigGetSingle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String configId = StringArgumentType.getString(context, "configId");
        String normalized = normalizeServerConfigId(configId);
        return CommandFeedbacks.info(context.getSource(), normalized + "=" + readConfigValue(ConquestDataManager.of(context.getSource().getServer()).serverConfig(), normalized));
    }

    private static int executeConfigSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String configId = normalizeServerConfigId(StringArgumentType.getString(context, "configId"));
        String rawValue = StringArgumentType.getString(context, "value").trim();
        ServerConfigStore store = ConquestDataManager.of(context.getSource().getServer()).serverConfig();
        ServerConfigDataModel config = store.get();
        switch (configId) {
            case "boolOverworldInvulnerable" -> config.setBoolOverworldInvulnerable(parseBooleanStrict(rawValue));
            case "boolRestrictConquestDimension" -> config.setBoolRestrictConquestDimension(parseBooleanStrict(rawValue));
            case "boolNaturalRegeneration" -> config.setBoolNaturalRegeneration(parseBooleanStrict(rawValue));
            case "passiveRegenIntervalTicks" -> config.setPassiveRegenIntervalTicks(parsePositiveInt(rawValue));
            case "passiveRegenStartDelaySeconds" -> config.setPassiveRegenStartDelaySeconds(parseNonNegativeInt(rawValue));
            case "MaxScopePistol" -> config.setMaxScopePistol(parseNonNegativeDouble(rawValue));
            case "MaxScopeSniper" -> config.setMaxScopeSniper(parseNonNegativeDouble(rawValue));
            case "MaxScopeRifle" -> config.setMaxScopeRifle(parseNonNegativeDouble(rawValue));
            case "MaxScopeShotgun" -> config.setMaxScopeShotgun(parseNonNegativeDouble(rawValue));
            case "MaxScopeSmg" -> config.setMaxScopeSmg(parseNonNegativeDouble(rawValue));
            case "MaxScopeRpg" -> config.setMaxScopeRpg(parseNonNegativeDouble(rawValue));
            case "MaxScopeMg" -> config.setMaxScopeMg(parseNonNegativeDouble(rawValue));
            case "vehicleBackend" -> config.setVehicleBackend(RoomVehicleBackend.of(rawValue));
            case "overworldSpawn" -> config.setOverworldSpawn(parseConfigPosition(context, rawValue));
            case "rangeJoinSpawn" -> config.setRangeJoinSpawn(parseConfigPosition(context, rawValue));
            case "rangeLeaveSpawn" -> config.setRangeLeaveSpawn(parseConfigPosition(context, rawValue));
            case "parkourStartPos" -> config.setParkourStartPos(parseConfigPosition(context, rawValue));
            case "parkourEndPos" -> config.setParkourEndPos(parseConfigPosition(context, rawValue));
            case "parkourCompletionTitleId" -> config.setParkourCompletionTitleId(rawValue);
            case "parkourBestTitleId" -> config.setParkourBestTitleId(rawValue);
            case "enablePackValidation" -> config.setEnablePackValidation(parseBooleanStrict(rawValue));
            case "missileKillRadius" -> config.setMissileKillRadius(parseNonNegativeDouble(rawValue));
            default -> throw new SimpleCommandExceptionType(Component.literal("未知配置项: " + configId)).create();
        }
        store.save(config);
        applyNaturalRegenerationGameRule(context.getSource().getServer(), store);
        if (configId.startsWith("parkour")) {
            ParkourRuntimeManager.of(context.getSource().getServer()).reconcileConfiguredTitles();
            ParkourRuntimeManager.of(context.getSource().getServer()).resyncOnlineState();
        }
        return CommandFeedbacks.info(context.getSource(), "已更新服务端配置: " + configId + "=" + readConfigValue(store, configId));
    }

    private static CompletableFuture<Suggestions> suggestServerConfigIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(SERVER_CONFIG_KEYS, builder);
    }

    private static CompletableFuture<Suggestions> suggestServerConfigValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String configId = StringArgumentType.getString(context, "configId");
        String normalized = normalizeServerConfigIdSafe(configId);
        if (normalized == null) {
            return builder.buildFuture();
        }
        return switch (normalized) {
            case "boolOverworldInvulnerable", "boolRestrictConquestDimension", "boolNaturalRegeneration", "enablePackValidation" ->
                SharedSuggestionProvider.suggest(List.of("true", "false"), builder);
            case "passiveRegenIntervalTicks" ->
                SharedSuggestionProvider.suggest(List.of("3", "5", "10"), builder);
            case "passiveRegenStartDelaySeconds" ->
                SharedSuggestionProvider.suggest(List.of("6", "8", "10"), builder);
            case "MaxScopePistol" ->
                SharedSuggestionProvider.suggest(List.of("1.5", "2.0", "3.0"), builder);
            case "MaxScopeSniper" ->
                SharedSuggestionProvider.suggest(List.of("8.0", "10.0", "12.0"), builder);
            case "MaxScopeRifle" ->
                SharedSuggestionProvider.suggest(List.of("4.0", "6.0", "8.0"), builder);
            case "MaxScopeShotgun" ->
                SharedSuggestionProvider.suggest(List.of("1.0", "1.5", "2.0"), builder);
            case "MaxScopeSmg" ->
                SharedSuggestionProvider.suggest(List.of("1.5", "2.0", "3.0"), builder);
            case "MaxScopeRpg" ->
                SharedSuggestionProvider.suggest(List.of("1.0", "1.5", "2.0"), builder);
            case "MaxScopeMg" ->
                SharedSuggestionProvider.suggest(List.of("2.0", "4.0", "6.0"), builder);
            case "vehicleBackend" ->
                SharedSuggestionProvider.suggest(RoomVehicleBackend.suggestions(), builder);
            case "overworldSpawn", "rangeJoinSpawn", "rangeLeaveSpawn", "parkourStartPos", "parkourEndPos" ->
                SharedSuggestionProvider.suggest(List.of("here"), builder);
            case "parkourCompletionTitleId", "parkourBestTitleId" ->
                SharedSuggestionProvider.suggest(List.of("\"\"", "RUNNER", "PARKOUR_MASTER"), builder);
            case "missileKillRadius" ->
                SharedSuggestionProvider.suggest(List.of("30.0", "35.0", "40.0"), builder);
            default -> builder.buildFuture();
        };
    }

    private static String normalizeServerConfigId(String raw) throws CommandSyntaxException {
        String normalized = normalizeServerConfigIdSafe(raw);
        if (normalized == null) {
            throw new SimpleCommandExceptionType(Component.literal("未知配置项: " + raw)).create();
        }
        return normalized;
    }

    private static String normalizeServerConfigIdSafe(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "booloverworldinvulnerable", "overworldinvulnerable" -> "boolOverworldInvulnerable";
            case "boolrestrictconquestdimension", "restrictconquestdimension", "blockdimensionentry" -> "boolRestrictConquestDimension";
            case "boolnaturalregeneration", "naturalregeneration", "naturalregen", "vanillaregeneration" -> "boolNaturalRegeneration";
            case "passiveregenintervalticks", "breathregenintervalticks", "breathregeninterval" -> "passiveRegenIntervalTicks";
            case "passiveregenstartdelayseconds", "breathregenstartdelayseconds", "breathregenstartseconds" -> "passiveRegenStartDelaySeconds";
            case "maxscopepistol", "scopepistol", "pistolscope" -> "MaxScopePistol";
            case "maxscopesniper", "scopesniper", "sniperscope" -> "MaxScopeSniper";
            case "maxscoperifle", "scoperifle", "riflescope" -> "MaxScopeRifle";
            case "maxscopeshotgun", "scopeshotgun", "shotgunscope" -> "MaxScopeShotgun";
            case "maxscopesmg", "scopesmg", "smgscope" -> "MaxScopeSmg";
            case "maxscoperpg", "scoperpg", "rpgscope" -> "MaxScopeRpg";
            case "maxscopemg", "scopemg", "mgscope" -> "MaxScopeMg";
            case "vehiclebackend", "vehiclemode", "vehiclemod", "backend" -> "vehicleBackend";
            case "overworldspawn", "lobbyspawn", "spawn" -> "overworldSpawn";
            case "rangejoinspawn", "rangespawn", "rangejoin" -> "rangeJoinSpawn";
            case "rangeleavespawn", "rangeleave" -> "rangeLeaveSpawn";
            case "parkourstartpos", "parkourstart", "parkourjoin", "parkourspawn" -> "parkourStartPos";
            case "parkourendpos", "parkourend", "parkourfinish" -> "parkourEndPos";
            case "parkourcompletiontitleid", "parkourtitle", "parkourcompletiontitle" -> "parkourCompletionTitleId";
            case "parkourbesttitleid", "parkourfirsttitle", "parkourrecordtitle" -> "parkourBestTitleId";
            case "enablepackvalidation", "packvalidation", "checkpacks", "packcheck" -> "enablePackValidation";
            case "missilekillradius", "missileradius", "missilekill" -> "missileKillRadius";
            default -> null;
        };
    }

    private static String readConfigValue(ServerConfigStore store, String configId) {
        ServerConfigDataModel config = store.get();
        return switch (configId) {
            case "boolOverworldInvulnerable" -> String.valueOf(store.isOverworldInvulnerableEnabled());
            case "boolRestrictConquestDimension" -> String.valueOf(store.isConquestDimensionRestricted());
            case "boolNaturalRegeneration" -> String.valueOf(store.isNaturalRegenerationEnabled());
            case "passiveRegenIntervalTicks" -> String.valueOf(store.getPassiveRegenIntervalTicks());
            case "passiveRegenStartDelaySeconds" -> String.valueOf(store.getPassiveRegenStartDelaySeconds());
            case "MaxScopePistol" -> formatDecimal(store.getMaxScopePistol());
            case "MaxScopeSniper" -> formatDecimal(store.getMaxScopeSniper());
            case "MaxScopeRifle" -> formatDecimal(store.getMaxScopeRifle());
            case "MaxScopeShotgun" -> formatDecimal(store.getMaxScopeShotgun());
            case "MaxScopeSmg" -> formatDecimal(store.getMaxScopeSmg());
            case "MaxScopeRpg" -> formatDecimal(store.getMaxScopeRpg());
            case "MaxScopeMg" -> formatDecimal(store.getMaxScopeMg());
            case "vehicleBackend" -> store.getVehicleBackend().value();
            case "overworldSpawn" -> formatPosition(config.getOverworldSpawn());
            case "rangeJoinSpawn" -> formatPosition(config.getRangeJoinSpawn());
            case "rangeLeaveSpawn" -> formatPosition(config.getRangeLeaveSpawn());
            case "parkourStartPos" -> formatPosition(config.getParkourStartPos());
            case "parkourEndPos" -> formatPosition(config.getParkourEndPos());
            case "parkourCompletionTitleId" -> store.getParkourCompletionTitleId();
            case "parkourBestTitleId" -> store.getParkourBestTitleId();
            case "enablePackValidation" -> String.valueOf(store.isPackValidationEnabled());
            case "missileKillRadius" -> formatDecimal(store.getMissileKillRadius());
            default -> "-";
        };
    }

    private static int executePackUpdate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return PackValidationService.of(context.getSource().getServer()).requestManualWhitelistUpdate(context.getSource(), player);
    }

    private static int executePackList(CommandContext<CommandSourceStack> context) {
        return PackValidationService.of(context.getSource().getServer()).listWhitelist(context.getSource());
    }

    private static int executePackDelete(CommandContext<CommandSourceStack> context) {
        String fileName = StringArgumentType.getString(context, "fileName");
        return PackValidationService.of(context.getSource().getServer()).deleteWhitelistEntry(context.getSource(), fileName);
    }

    private static CompletableFuture<Suggestions> suggestPackWhitelistFileNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            PackValidationService.of(context.getSource().getServer()).suggestWhitelistFileNames(),
            builder
        );
    }

    private static String formatPosition(RoomPosition position) {
        if (position == null) {
            return "-";
        }
        return position.x() + "," + String.format(Locale.ROOT, "%.1f", position.y()) + "," + position.z();
    }

    private static boolean parseBooleanStrict(String value) throws CommandSyntaxException {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized) || "off".equals(normalized)) {
            return false;
        }
        throw new SimpleCommandExceptionType(Component.literal("布尔值仅支持 true/false")).create();
    }

    private static int parsePositiveInt(String value) throws CommandSyntaxException {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new SimpleCommandExceptionType(Component.literal("配置值必须大于 0")).create();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new SimpleCommandExceptionType(Component.literal("配置值必须是正整数")).create();
        }
    }

    private static int parseNonNegativeInt(String value) throws CommandSyntaxException {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new SimpleCommandExceptionType(Component.literal("配置值不能小于 0")).create();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new SimpleCommandExceptionType(Component.literal("配置值必须是整数")).create();
        }
    }

    private static double parseNonNegativeDouble(String value) throws CommandSyntaxException {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < 0.0D) {
                throw new SimpleCommandExceptionType(Component.literal("配置值不能小于 0")).create();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new SimpleCommandExceptionType(Component.literal("配置值必须是数字")).create();
        }
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static RoomPosition parseConfigPosition(CommandContext<CommandSourceStack> context, String value) throws CommandSyntaxException {
        if ("here".equalsIgnoreCase(value)) {
            ServerPlayer player = context.getSource().getPlayer();
            if (player == null) {
                throw new SimpleCommandExceptionType(Component.literal("here 仅可由玩家执行")).create();
            }
            return new RoomPosition(player.getBlockX(), player.getY(), player.getBlockZ());
        }
        String[] parts = value.split(",");
        if (parts.length != 3) {
            throw new SimpleCommandExceptionType(Component.literal("坐标格式应为 x,y,z 或 here")).create();
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            return new RoomPosition(x, y, z);
        } catch (NumberFormatException exception) {
            throw new SimpleCommandExceptionType(Component.literal("坐标格式应为 x,y,z 或 here")).create();
        }
    }

    private static Vec3 resolveShellingCenter(ServerPlayer player, CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (player == null) {
            throw new SimpleCommandExceptionType(Component.literal("只有玩家可以执行炮击测试")).create();
        }
        try {
            double centerX = DoubleArgumentType.getDouble(context, "centerX");
            double centerZ = DoubleArgumentType.getDouble(context, "centerZ");
            return new Vec3(centerX, player.getY(), centerZ);
        } catch (IllegalArgumentException ignored) {
            return player.position();
        }
    }

    private static void applyNaturalRegenerationGameRule(net.minecraft.server.MinecraftServer server, ServerConfigStore store) {
        if (server == null || store == null) {
            return;
        }
        boolean enabled = store.isNaturalRegenerationEnabled();
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(enabled, server);
        }
    }

    private static int executeLevelSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int level = IntegerArgumentType.getInteger(context, "level");
        if (targets.isEmpty()) {
            return CommandFeedbacks.failure(context.getSource(), "未找到可设置等级的玩家");
        }
        int changed = 0;
        for (ServerPlayer target : targets) {
            if (target == null) {
                continue;
            }
            PlayerLevelService.setPlayerLevel(target, level);
            changed++;
        }
        return CommandFeedbacks.info(context.getSource(), String.format(Locale.ROOT, "已设置 %d 名玩家等级为 %d", changed, level));
    }

    private static int executeLevelGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        if (targets.isEmpty()) {
            return CommandFeedbacks.failure(context.getSource(), "未找到可查询等级的玩家");
        }
        for (ServerPlayer target : targets) {
            if (target == null) {
                continue;
            }
            int level = PlayerLevelService.getEffectiveLevel(target);
            CommandFeedbacks.line(context.getSource(), org.mods.gd656conquest.server.shared.text.ServerText.colored(
                String.format(Locale.ROOT, "%s 等级: %d", target.getScoreboardName(), level),
                net.minecraft.ChatFormatting.AQUA
            ));
        }
        return 1;
    }
}
