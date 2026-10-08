package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.common.match.MatchResultType;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.network.packet.RoomDownedStatePacket;
import org.mods.gd656conquest.network.packet.RoomMatchResultPacket;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.player.lock.ServerPlayerLockService;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomMatchEndRequest;
import org.mods.gd656conquest.server.room.gameplay.RoomTeamPresentationSupport;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RoomResultRuntimeService {
    public static final int MATCH_RESULT_BLACK_START_TICKS = 100;
    public static final int MATCH_RESULT_RESTORE_TICKS = 80;
    public static final int MATCH_RESULT_CLOSE_WAIT_TICKS = 30 * 20;
    public static final int MATCH_RESULT_TOTAL_TICKS = MATCH_RESULT_BLACK_START_TICKS + MATCH_RESULT_RESTORE_TICKS + MATCH_RESULT_CLOSE_WAIT_TICKS;
    private static final int RESULT_VIEW_TRANSFER_INTERVAL_TICKS = 2;

    private static final String ACTIONBAR_SOURCE_RESULT = "room:result";

    private final MinecraftServer server;
    private final RoomPlayerStateService playerStateService;
    private final RoomRuleRuntimeService ruleRuntimeService;
    private final RoomPlayerTransferQueueService playerTransferQueueService;

    public RoomResultRuntimeService(
        MinecraftServer server,
        RoomPlayerStateService playerStateService,
        RoomRuleRuntimeService ruleRuntimeService,
        RoomPlayerTransferQueueService playerTransferQueueService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerStateService = Objects.requireNonNull(playerStateService, "playerStateService");
        this.ruleRuntimeService = Objects.requireNonNull(ruleRuntimeService, "ruleRuntimeService");
        this.playerTransferQueueService = Objects.requireNonNull(playerTransferQueueService, "playerTransferQueueService");
    }

    public boolean beginMatchEnd(RoomDefinition room, RunningRuntimeState state, RoomMatchEndRequest request, long gameTick) {
        if (room == null || state == null || request == null || state.getResultSequenceEndTick() > 0) {
            return false;
        }
        state.setResultPlayerResults(request.playerResults());
        state.setResultReason(request.reason());
        state.setResultBlackStartTick(gameTick + MATCH_RESULT_BLACK_START_TICKS);
        state.setResultSequenceEndTick(gameTick + MATCH_RESULT_TOTAL_TICKS);
        state.setResultCloseRemainAnnouncedSecond(-1);
        state.setResultViewApplied(false);
        state.setResultLastViewTransferTick(-1L);
        state.getResultViewTransferPending().clear();
        state.getResultViewTransferPending().addAll(state.getRoomPlayers());
        state.getResultAwardedPlayers().clear();
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue; // 掉线/已离场玩家不参与结算
            }
            playerTransferQueueService.cancelPendingTransfers(playerId);
            state.clearRespawnFlow(playerId);
            ruleRuntimeService.clearPlayer(playerId);
            playerStateService.normalizePlayerModelState(player);
            NetworkManager.sendToPlayer(player, RoomDownedStatePacket.clear());
            NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
            NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setBoundaryPenalty(false));
            applyPlayerResult(room, state, player, request.playerResults().get(playerId), request.reason(), gameTick, true);
            player.setInvulnerable(true);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            ServerPlayerLockService.of(server).syncPlayer(player);
            state.getResultAwardedPlayers().add(playerId);
        }
        return true;
    }

    public boolean tickEnding(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null || state.getResultSequenceEndTick() <= 0) {
            return false;
        }
        applyMatchEndViewIfNeeded(room, state, gameTick);
        transferPlayersToEndView(room, state, gameTick);
        broadcastCloseCountdown(state, gameTick);
        return gameTick >= state.getResultSequenceEndTick();
    }

    public void clearPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.setInvulnerable(false);
        NetworkManager.sendToPlayer(player, new RoomMatchResultPacket(
            false,
            MatchResultType.DRAW,
            MATCH_RESULT_TOTAL_TICKS,
            MATCH_RESULT_BLACK_START_TICKS,
            MATCH_RESULT_RESTORE_TICKS,
            ""
        ));
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setMatchEndViewLock(false));
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setBoundaryPenalty(false));
        NetworkManager.sendToPlayer(player, RoomDownedStatePacket.clear());
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        ServerActionBarService.clearSource(player, ACTIONBAR_SOURCE_RESULT);
    }

    public void syncRejoinedPlayer(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick) {
        syncEndingPlayer(room, state, player, gameTick, true);
    }

    public void syncLateJoinedPlayer(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick) {
        syncEndingPlayer(room, state, player, gameTick, false);
    }

    private void syncEndingPlayer(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long gameTick, boolean awardExperience) {
        if (room == null || state == null || player == null || state.getResultSequenceEndTick() <= 0L) {
            return;
        }
        UUID playerId = player.getUUID();
        // GD656Mods: 结果表中无该玩家条目(如对局结束请求创建后才中途加入的玩家) -> 未参与本局, 不参与结算
        RoomMatchEndRequest.PlayerResultEntry entry = state.getResultPlayerResults().get(playerId);
        if (entry == null) {
            return;
        }
        playerTransferQueueService.cancelPendingTransfers(playerId);
        state.clearRespawnFlow(playerId);
        ruleRuntimeService.clearPlayer(playerId);
        playerStateService.normalizePlayerModelState(player);
        NetworkManager.sendToPlayer(player, RoomDownedStatePacket.clear());
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setBoundaryPenalty(false));
        applyPlayerResult(room, state, player, entry, state.getResultReason(), gameTick, awardExperience);
        player.setInvulnerable(true);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (awardExperience && !state.getResultAwardedPlayers().contains(playerId)) {
            state.getResultAwardedPlayers().add(playerId);
        }
        RoomViewPosition endView = room.commonConfig().resultConfig().endViewPosition();
        if (state.isResultViewApplied()) {
            state.getResultViewTransferPending().remove(playerId);
            if (endView != null) {
                playerStateService.setSpectator(player);
                player.teleportTo(player.serverLevel(), endView.x() + 0.5D, endView.y(), endView.z() + 0.5D, endView.yaw(), endView.pitch());
                player.setDeltaMovement(0.0D, 0.0D, 0.0D);
                NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setMatchEndViewLock(true));
            }
            return;
        }
        playerStateService.setSpectator(player);
        state.getResultViewTransferPending().add(playerId);
    }

    private void applyMatchEndViewIfNeeded(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (state.isResultViewApplied()) {
            return;
        }
        if (state.getResultBlackStartTick() <= 0 || gameTick < state.getResultBlackStartTick()) {
            return;
        }
        RoomViewPosition endView = room.commonConfig().resultConfig().endViewPosition();
        if (endView == null) {
            state.setResultViewApplied(true);
            return;
        }
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                state.getResultViewTransferPending().remove(playerId);
                continue;
            }
            double targetEyeY = endView.y() + player.getEyeHeight();
            NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.startMatchEndTransition(
                endView.x() + 0.5D,
                targetEyeY,
                endView.z() + 0.5D,
                endView.yaw(),
                endView.pitch(),
                MATCH_RESULT_RESTORE_TICKS
            ));
        }
        state.setResultViewApplied(true);
    }

    private void transferPlayersToEndView(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null || !state.isResultViewApplied() || state.getResultViewTransferPending().isEmpty()) {
            return;
        }
        if (state.getResultBlackStartTick() <= 0 || gameTick < state.getResultBlackStartTick()) {
            return;
        }
        long lastTransferTick = state.getResultLastViewTransferTick();
        if (lastTransferTick >= 0 && gameTick - lastTransferTick < RESULT_VIEW_TRANSFER_INTERVAL_TICKS) {
            return;
        }
        RoomViewPosition endView = room.commonConfig().resultConfig().endViewPosition();
        if (endView == null) {
            state.getResultViewTransferPending().clear();
            state.setResultLastViewTransferTick(gameTick);
            return;
        }
        UUID selected = null;
        for (UUID playerId : java.util.List.copyOf(state.getResultViewTransferPending())) {
            if (!state.getRoomPlayers().contains(playerId)) {
                state.getResultViewTransferPending().remove(playerId);
                continue;
            }
            selected = playerId;
            break;
        }
        state.setResultLastViewTransferTick(gameTick);
        if (selected == null) {
            return;
        }
        state.getResultViewTransferPending().remove(selected);
        ServerPlayer player = server.getPlayerList().getPlayer(selected);
        if (player == null) {
            return;
        }
        playerStateService.setSpectator(player);
        player.teleportTo(player.serverLevel(), endView.x() + 0.5D, endView.y(), endView.z() + 0.5D, endView.yaw(), endView.pitch());
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setMatchEndViewLock(true));
    }

    private void broadcastCloseCountdown(RunningRuntimeState state, long gameTick) {
        int remainSeconds = (int) Math.max(0L, (state.getResultSequenceEndTick() - gameTick + 19L) / 20L);
        if (remainSeconds <= 0 || remainSeconds % 5 != 0 || remainSeconds == state.getResultCloseRemainAnnouncedSecond()) {
            return;
        }
        state.setResultCloseRemainAnnouncedSecond(remainSeconds);
        for (UUID playerId : state.getRoomPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                ServerActionBarService.showBuilder(
                    player,
                    ACTIONBAR_SOURCE_RESULT,
                    ServerActionBarService.builder()
                        .text("房间将在 ", ChatFormatting.GRAY)
                        .text(String.valueOf(remainSeconds), ChatFormatting.GOLD)
                        .text(" 秒后关闭", ChatFormatting.GRAY),
                    100
                );
            }
        }
    }

    /**
     * GD656Mods: 对局结束判定器(融入结算层) —— 根据结果表中的单个玩家结算条目,
     * 生成并发送该玩家的结果包(客户端 title/subtitle)、聊天框文本与对局结束加分。
     * entry 由对局运行器写入结果表时已保证存在(RoomMatchEndRequest 构造期全量校验), 此处不可为 null。
     */
    private void applyPlayerResult(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        RoomMatchEndRequest.PlayerResultEntry entry,
        String reason,
        long gameTick,
        boolean awardExperience
    ) {
        if (player == null) {
            return;
        }
        if (entry == null) {
            throw new IllegalStateException("对局结束结果表缺少玩家结算条目: " + player.getUUID());
        }
        UUID playerId = player.getUUID();
        MatchResultType resultType = entry.resultType();
        NetworkManager.sendToPlayer(player, new RoomMatchResultPacket(
            true,
            resultType,
            MATCH_RESULT_TOTAL_TICKS,
            MATCH_RESULT_BLACK_START_TICKS,
            MATCH_RESULT_RESTORE_TICKS,
            buildResultSubtitle(room, state, playerId, resultType)
        ));
        MatchRewardFeedback rewardFeedback = resolveRewardFeedback(room, state, playerId, player, entry, gameTick, awardExperience);
        sendResultChat(room, state, player, resultType, reason, rewardFeedback, gameTick);
    }

    private void sendResultChat(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        MatchResultType resultType,
        String reason,
        MatchRewardFeedback rewardFeedback,
        long gameTick
    ) {
        if (player == null || room == null || state == null) {
            return;
        }
        String headlineText = resolveHeadlineText(resultType);
        String topDelimiter = "==========";
        String topPlain = topDelimiter + headlineText + topDelimiter;
        String bottomPlain = "=".repeat(topPlain.length());
        String roomId = formatRoomId(room.roomId());
        String mapId = room.gameplayMapId();
        String displayReason = reason == null || reason.isBlank() ? "-" : reason;
        String currentTeam = resolveTeamDisplayName(room, state, state.getRoomTeams().get(player.getUUID()));
        int durationSeconds = resolveMatchDurationSeconds(state, resolveFeedbackTick(state, gameTick));

        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.empty()
            .append(Component.literal(topDelimiter).withStyle(ChatFormatting.GRAY))
            .append(Component.literal(headlineText).withStyle(resolveHeadlineColor(resultType)))
            .append(Component.literal(topDelimiter).withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(labelValueLine("结束原因：", ChatFormatting.WHITE, displayReason, ChatFormatting.RED));
        player.sendSystemMessage(labelValueLine("对局用时：", ChatFormatting.WHITE, formatDuration(durationSeconds), ChatFormatting.GOLD));
        player.sendSystemMessage(labelValueLine("房间地图：", ChatFormatting.WHITE, mapId, ChatFormatting.YELLOW));
        player.sendSystemMessage(labelValueLine("当前阵营：", ChatFormatting.WHITE, currentTeam, ChatFormatting.GREEN));
        // GD656Mods: 经验加成活动生效(倍率 != 1)时, 在这两行后追加 "(<活动介绍> ×倍率)" 后缀
        MutableComponent scoreLine = Component.empty().append(labelValueLine("分数加成：", ChatFormatting.WHITE, formatRewardValue(rewardFeedback.baseScoreReward()), ChatFormatting.AQUA, ChatFormatting.GREEN));
        MutableComponent extraLine = Component.empty().append(labelValueLine("额外加成：", ChatFormatting.WHITE, String.valueOf(rewardFeedback.extraReward()), ChatFormatting.BLUE, ChatFormatting.GREEN));
        MutableComponent bonusSuffix = buildExperienceBonusSuffix(player);
        if (bonusSuffix != null) {
            scoreLine.append(bonusSuffix);
            extraLine.append(bonusSuffix);
        }
        player.sendSystemMessage(scoreLine);
        player.sendSystemMessage(extraLine);
        player.sendSystemMessage(buildLevelChangeLine(rewardFeedback.levelBefore(), rewardFeedback.levelAfter()));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.empty()
            .append(Component.literal("给地图 ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(mapId).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" 评分：").withStyle(ChatFormatting.WHITE)));
        player.sendSystemMessage(buildRatingOptionsLine(roomId, mapId));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal(bottomPlain).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(""));
    }

    /** GD656Mods: 从结果表推导唯一胜者(单人胜, 如枪神个人战); 无/多个时返回 null */
    private UUID resolveSingleWinnerPlayerId(RunningRuntimeState state, UUID selfPlayerId) {
        if (state == null) {
            return null;
        }
        UUID winner = null;
        for (Map.Entry<UUID, RoomMatchEndRequest.PlayerResultEntry> entry : state.getResultPlayerResults().entrySet()) {
            if (entry.getValue() == null || entry.getValue().resultType() != MatchResultType.VICTORY) {
                continue;
            }
            if (winner != null && !winner.equals(entry.getKey())) {
                return null;
            }
            winner = entry.getKey();
        }
        return winner == null || winner.equals(selfPlayerId) ? null : winner;
    }

    private MatchRewardFeedback resolveRewardFeedback(
        RoomDefinition room,
        RunningRuntimeState state,
        UUID playerId,
        ServerPlayer player,
        RoomMatchEndRequest.PlayerResultEntry entry,
        long gameTick,
        boolean awardExperience
    ) {
        if (room == null || state == null || playerId == null || player == null || entry == null) {
            return MatchRewardFeedback.empty();
        }
        KilliconScoreboardBridge.TempSnapshot snapshot = KilliconScoreboardBridge.getPlayerSnapshot(state, playerId);
        PlayerLevelService.MatchOutcome outcome = resolveMatchOutcome(entry.resultType());
        PlayerLevelService.MatchStats stats = new PlayerLevelService.MatchStats(
            snapshot.score(),
            snapshot.kill(),
            snapshot.assist(),
            snapshot.death(),
            resolveMatchDurationSeconds(state, resolveFeedbackTick(state, gameTick)),
            entry.extraScore()
        );
        if (awardExperience) {
            PlayerLevelService.AwardResult result = PlayerLevelService.awardMatchExperience(player, stats, outcome);
            return new MatchRewardFeedback(snapshot, result.formula(), result.levelBefore(), result.levelAfter());
        }
        int currentLevel = PlayerLevelService.getEffectiveLevel(player);
        return new MatchRewardFeedback(snapshot, PlayerLevelService.previewFormula(stats, outcome, resolveExperienceMultiplier(player)), currentLevel, currentLevel);
    }

    private PlayerLevelService.MatchOutcome resolveMatchOutcome(MatchResultType resultType) {
        return switch (resultType) {
            case VICTORY -> PlayerLevelService.MatchOutcome.WIN;
            case DEFEAT -> PlayerLevelService.MatchOutcome.LOSE;
            default -> PlayerLevelService.MatchOutcome.DRAW;
        };
    }

    private int resolveMatchDurationSeconds(RunningRuntimeState state, long gameTick) {
        if (state == null || gameTick <= 0L) {
            return 0;
        }
        return state.getGameplayRuntime()
            .filter(RoomGameplayPresentationProvider.class::isInstance)
            .map(RoomGameplayPresentationProvider.class::cast)
            .map(runtime -> runtime.matchDurationSeconds(gameTick))
            .orElse(0);
    }

    private String resolveTeamDisplayName(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId) {
        return RoomTeamPresentationSupport.resolveTeamDisplayName(room, state, teamId);
    }

    private String buildResultSubtitle(RoomDefinition room, RunningRuntimeState state, UUID playerId, MatchResultType resultType) {
        if (room == null || state == null || playerId == null) {
            return "";
        }
        if (org.mods.gd656conquest.server.room.model.RoomGameplayId.GUNMASTER.equals(room.gameplayId())
            && room.gunmasterConfig().isSoloGrouping()) {
            return "本次对局您的名次：" + resolveGunmasterSoloRank(state, playerId);
        }
        return "";
    }

    private int resolveGunmasterSoloRank(RunningRuntimeState state, UUID playerId) {
        if (state == null || playerId == null) {
            return 1;
        }
        List<UUID> ranking = new ArrayList<>(state.getRoomPlayers());
        ranking.sort(Comparator
            .comparingInt((UUID id) -> state.getGunmasterStageIndexByPlayer().getOrDefault(id, 0)).reversed()
            .thenComparingInt((UUID id) -> state.getGunmasterStageKillsByPlayer().getOrDefault(id, 0)).reversed()
            .thenComparingInt((UUID id) -> KilliconScoreboardBridge.getPlayerSnapshot(state, id).kill()).reversed()
            .thenComparingInt((UUID id) -> KilliconScoreboardBridge.getPlayerSnapshot(state, id).death())
            .thenComparing(UUID::toString));
        int index = ranking.indexOf(playerId);
        return index >= 0 ? index + 1 : ranking.size() + 1;
    }

    private String resolvePlayerName(UUID playerId) {
        if (playerId == null) {
            return "-";
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player == null ? playerId.toString() : player.getScoreboardName();
    }

    private long resolveFeedbackTick(RunningRuntimeState state, long gameTick) {
        if (state == null) {
            return Math.max(0L, gameTick);
        }
        long finalizedTick = state.getFinalizedMatchEndTick();
        return finalizedTick >= 0L ? finalizedTick : Math.max(0L, gameTick);
    }

    private String resolveHeadlineText(MatchResultType resultType) {
        return switch (resultType) {
            case VICTORY -> "大获全胜";
            case DEFEAT -> "败北";
            case DRAW -> "势均力敌";
        };
    }

    private ChatFormatting resolveHeadlineColor(MatchResultType resultType) {
        return switch (resultType) {
            case VICTORY -> ChatFormatting.GREEN;
            case DEFEAT -> ChatFormatting.RED;
            case DRAW -> ChatFormatting.YELLOW;
        };
    }

    private Component labelValueLine(String label, ChatFormatting labelColor, String value, ChatFormatting valueColor) {
        return labelValueLine(label, labelColor, value, valueColor, null);
    }

    private Component labelValueLine(
        String label,
        ChatFormatting labelColor,
        String value,
        ChatFormatting valueColor,
        ChatFormatting prefixColor
    ) {
        MutableComponent line = Component.empty().append(Component.literal(label).withStyle(labelColor));
        if (prefixColor != null) {
            line.append(Component.literal("+").withStyle(prefixColor));
        }
        line.append(Component.literal(value == null ? "-" : value).withStyle(valueColor));
        return line;
    }

    /** GD656Mods: 服务器配置的"玩家经验加成倍率"(默认 1.0)。 */
    private static double resolveExperienceMultiplier(ServerPlayer player) {
        if (player == null || player.server == null) {
            return 1.0D;
        }
        return org.mods.gd656conquest.server.data.ConquestDataManager.of(player.server)
            .serverConfig()
            .getPlayerExperienceMultiplier();
    }

    /**
     * GD656Mods: "经验加成活动"后缀 —— 倍率为 1(或未配置)时返回 null, 即不显示该后缀。
     * 文本取自服务器配置的「经验加成活动介绍」; 介绍为空时括号内只显示倍率。
     */
    private MutableComponent buildExperienceBonusSuffix(ServerPlayer player) {
        if (player == null || player.server == null) {
            return null;
        }
        org.mods.gd656conquest.server.data.serverconfig.ServerConfigStore store =
            org.mods.gd656conquest.server.data.ConquestDataManager.of(player.server).serverConfig();
        double multiplier = store.getPlayerExperienceMultiplier();
        if (Math.abs(multiplier - 1.0D) < 1.0E-6D) {
            return null;
        }
        String description = store.getExperienceBonusEventDescription();
        String factorText = formatMultiplier(multiplier);
        String inner = description == null || description.isBlank()
            ? "×" + factorText
            : description + " ×" + factorText;
        return Component.literal(" (" + inner + ")").withStyle(ChatFormatting.GOLD);
    }

    /** GD656Mods: 倍率显示 —— 整数不带小数点, 否则保留两位小数。 */
    private static String formatMultiplier(double multiplier) {
        if (Math.abs(multiplier - Math.rint(multiplier)) < 1.0E-6D) {
            return String.valueOf((long) Math.rint(multiplier));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", multiplier);
    }

    private Component buildLevelChangeLine(int levelBefore, int levelAfter) {        return Component.empty()
            .append(Component.literal("等级变化：").withStyle(ChatFormatting.WHITE))
            .append(levelComponent(levelBefore))
            .append(Component.literal(" --> ").withStyle(ChatFormatting.GRAY))
            .append(levelComponent(levelAfter));
    }

    private Component levelComponent(int level) {
        return Component.literal(String.valueOf(Math.max(0, level)))
            .withStyle(style -> style.withColor(PlayerLevelService.resolveLevelColorRgb(level)));
    }

    private Component buildRatingOptionsLine(String roomId, String mapId) {
        ChatFormatting[] colors = new ChatFormatting[] {
            ChatFormatting.RED,
            ChatFormatting.GOLD,
            ChatFormatting.YELLOW,
            ChatFormatting.GREEN,
            ChatFormatting.AQUA
        };
        MutableComponent line = Component.empty();
        for (int rating = 1; rating <= 5; rating++) {
            if (rating > 1) {
                line.append(Component.literal(" "));
            }
            int score = rating;
            line.append(Component.literal("[" + rating + "]")
                .withStyle(style -> style
                    .withColor(colors[score - 1])
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gdc action scoring " + roomId + " " + score))
                    .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.literal("给地图 " + mapId + " 评分 " + score + " 分")
                    ))));
        }
        return line;
    }

    private String formatDuration(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int hours = safeSeconds / 3600;
        int minutes = (safeSeconds % 3600) / 60;
        int seconds = safeSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String formatRewardValue(double value) {
        double safeValue = Math.max(0.0D, value);
        if (Math.abs(safeValue - Math.rint(safeValue)) < 0.0005D) {
            return String.valueOf((int) Math.rint(safeValue));
        }
        String formatted = String.format(java.util.Locale.ROOT, "%.3f", safeValue);
        while (formatted.contains(".") && (formatted.endsWith("0") || formatted.endsWith("."))) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private String formatRoomId(String roomId) {
        if (roomId == null) {
            return "-";
        }
        String normalized = roomId.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.matches("room_\\d{2}")) {
            return "Room_" + normalized.substring(normalized.length() - 2);
        }
        return roomId;
    }

    private record MatchRewardFeedback(
        KilliconScoreboardBridge.TempSnapshot snapshot,
        PlayerLevelService.Formula formula,
        int levelBefore,
        int levelAfter
    ) {
        private static MatchRewardFeedback empty() {
            return new MatchRewardFeedback(KilliconScoreboardBridge.TempSnapshot.EMPTY, null, 0, 0);
        }

        private double baseScoreReward() {
            return formula == null ? 0.0D : Math.max(0.0D, formula.scoreFactorPart());
        }

        private int extraReward() {
            return formula == null ? 0 : Math.max(0, formula.extraPart());
        }
    }
}
