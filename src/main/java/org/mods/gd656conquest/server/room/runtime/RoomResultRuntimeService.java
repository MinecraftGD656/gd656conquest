package org.mods.gd656conquest.server.room.runtime;

import java.util.Optional;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayOperationsConclusionProvider;
import org.mods.gd656conquest.common.match.OperationsConclusion;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.common.log.ClientMessageType;
import org.mods.gd656conquest.common.match.MatchResultType;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.network.packet.RoomDownedStatePacket;
import org.mods.gd656conquest.network.packet.RoomMatchResultPacket;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.player.lock.ServerPlayerLockService;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomTeamPresentationSupport;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    public boolean beginMatchEnd(RoomDefinition room, RunningRuntimeState state, RoomTeamId winner, UUID winnerPlayerId, String reason, long gameTick) {
        if (room == null || state == null || state.getResultSequenceEndTick() > 0) {
            return false;
        }
        var conclusion = operationsConclusion(state);
        if (conclusion.isPresent()) return beginOperationsEnd(room, state, winner, reason, gameTick, conclusion.get());
        state.setOperationsPresentationEndTick(-1L);
        state.setResultWinnerTeam(winner);
        state.setResultWinnerPlayerId(winnerPlayerId);
        state.setResultReason(reason);
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
                continue;
            }
            playerTransferQueueService.cancelPendingTransfers(playerId);
            state.clearRespawnFlow(playerId);
            ruleRuntimeService.clearPlayer(playerId);
            playerStateService.normalizePlayerModelState(player);
            NetworkManager.sendToPlayer(player, RoomDownedStatePacket.clear());
            NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
            NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setBoundaryPenalty(false));
            RoomTeamId selfTeam = state.getRoomTeams().get(playerId);
            MatchResultType resultType = resolveResultType(playerId, selfTeam, winner, winnerPlayerId);
            NetworkManager.sendToPlayer(player, new RoomMatchResultPacket(
                true,
                resultType,
                MATCH_RESULT_TOTAL_TICKS,
                MATCH_RESULT_BLACK_START_TICKS,
                MATCH_RESULT_RESTORE_TICKS,
                buildResultSubtitle(room, state, playerId, resultType)
            ));
            player.setInvulnerable(true);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            ServerPlayerLockService.of(server).syncPlayer(player);
            sendResultChat(room, state, winner, winnerPlayerId, reason, player, resultType, selfTeam);
            awardMatchExperience(room, state, playerId, player, winner, winnerPlayerId, gameTick);
            state.getResultAwardedPlayers().add(playerId);
        }
        return true;
    }

    private Optional<OperationsConclusion> operationsConclusion(RunningRuntimeState state) {
        return state.getGameplayRuntime()
            .filter(RoomGameplayOperationsConclusionProvider.class::isInstance)
            .map(RoomGameplayOperationsConclusionProvider.class::cast)
            .flatMap(RoomGameplayOperationsConclusionProvider::operationsConclusion);
    }

    private boolean beginOperationsEnd(RoomDefinition room, RunningRuntimeState state, RoomTeamId winner, String reason,
                                      long tick, OperationsConclusion conclusion) {
        state.setOperationsPresentationEndTick(conclusion.presentationEndTick());
        state.setResultWinnerTeam(winner);
        state.setResultWinnerPlayerId(null);
        state.setResultReason(reason);
        state.setResultBlackStartTick(-1L);
        state.setResultSequenceEndTick(conclusion.closeTick(MATCH_RESULT_CLOSE_WAIT_TICKS));
        state.setResultCloseRemainAnnouncedSecond(-1);
        state.setResultViewApplied(true);
        state.getResultViewTransferPending().clear();
        // Combat and camera preparation already ran. Settlement must not clear cinematic or audio state.
        for (UUID id : List.copyOf(state.getRoomPlayers())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) sendOperationsResult(room, state, player, tick, true);
        }
        return true;
    }

    private void sendOperationsResult(RoomDefinition room, RunningRuntimeState state, ServerPlayer player, long tick, boolean awardExperience) {
        UUID id = player.getUUID();
        RoomTeamId team = state.getRoomTeams().get(id);
        MatchResultType type = resolveResultType(id, team, state.getResultWinnerTeam(), null);
        int remaining = (int)Math.max(1, state.getResultSequenceEndTick()-tick);
        NetworkManager.sendToPlayer(player, new RoomMatchResultPacket(true, type, remaining, 0, 1, "", true));
        player.setInvulnerable(true);
        player.setDeltaMovement(0, 0, 0);
        ServerPlayerLockService.of(server).syncPlayer(player);
        sendResultChat(room, state, state.getResultWinnerTeam(), null, state.getResultReason(), player, type, team);
        // A new spectator joining after settlement must remain ineligible after reconnecting.
        if (!awardExperience) state.getResultAwardedPlayers().add(id);
        if (awardExperience && state.getResultAwardedPlayers().add(id)) {
            long battleEnd = operationsConclusion(state).map(OperationsConclusion::battleEndTick).orElse(tick);
            awardMatchExperience(room, state, id, player, state.getResultWinnerTeam(), null, battleEnd);
        }
    }

    private RoomViewPosition resultView(RoomDefinition room) {
        return RoomGameplayId.BREAKTHROUGH.equals(room.gameplayId())
            ? room.breakthroughConfig().battalionConfig().briefingViewPosition() : room.commonConfig().resultConfig().endViewPosition();
    }

    public boolean tickEnding(RoomDefinition room, RunningRuntimeState state, long gameTick) {
        if (room == null || state == null || state.getResultSequenceEndTick() <= 0) {
            return false;
        }
        if (state.isOperationsResult()) {
            if (gameTick < state.getOperationsPresentationEndTick()) return false;
            broadcastCloseCountdown(state, gameTick);
            return gameTick >= state.getResultSequenceEndTick();
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
        if (state.isOperationsResult()) {
            playerTransferQueueService.cancelPendingTransfers(player.getUUID());
            state.clearRespawnFlow(player.getUUID());
            ruleRuntimeService.clearPlayer(player.getUUID());
            playerStateService.applyDeployHoldingState(player);
            NetworkManager.sendToPlayer(player, RoomDownedStatePacket.clear());
            NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
            sendOperationsResult(room, state, player, gameTick, awardExperience);
            return;
        }
        UUID playerId = player.getUUID();
        playerTransferQueueService.cancelPendingTransfers(playerId);
        state.clearRespawnFlow(playerId);
        ruleRuntimeService.clearPlayer(playerId);
        playerStateService.normalizePlayerModelState(player);
        NetworkManager.sendToPlayer(player, RoomDownedStatePacket.clear());
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setBoundaryPenalty(false));
        RoomTeamId selfTeam = state.getRoomTeams().get(playerId);
        RoomTeamId winner = state.getResultWinnerTeam();
        UUID winnerPlayerId = state.getResultWinnerPlayerId();
        MatchResultType resultType = resolveResultType(playerId, selfTeam, winner, winnerPlayerId);
        NetworkManager.sendToPlayer(player, new RoomMatchResultPacket(
            true,
            resultType,
            MATCH_RESULT_TOTAL_TICKS,
            MATCH_RESULT_BLACK_START_TICKS,
            MATCH_RESULT_RESTORE_TICKS,
            buildResultSubtitle(room, state, playerId, resultType)
        ));
        player.setInvulnerable(true);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        sendResultChat(room, state, winner, winnerPlayerId, state.getResultReason(), player, resultType, selfTeam);
        if (awardExperience && !state.getResultAwardedPlayers().contains(playerId)) {
            awardMatchExperience(room, state, playerId, player, winner, winnerPlayerId, gameTick);
            state.getResultAwardedPlayers().add(playerId);
        }
        RoomViewPosition endView = resultView(room);
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
        RoomViewPosition endView = resultView(room);
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
        RoomViewPosition endView = resultView(room);
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

    private void sendResultChat(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId winner,
        UUID winnerPlayerId,
        String reason,
        ServerPlayer player,
        MatchResultType resultType,
        RoomTeamId selfTeam
    ) {
        if (player == null) {
            return;
        }
        String summary;
        ClientMessageType type;
        if (resultType == MatchResultType.DRAW) {
            summary = "对局结束：本局平局";
            type = ClientMessageType.INFO;
        } else if (resultType == MatchResultType.VICTORY) {
            summary = winner != null ? "对局结束：你所在阵营获胜" : "对局结束：你获得胜利";
            type = ClientMessageType.SUCCESS;
        } else if (winner != null) {
            summary = "对局结束：你所在阵营战败";
            type = ClientMessageType.FAILURE;
        } else if (winnerPlayerId != null) {
            summary = "对局结束：你未能取得胜利";
            type = ClientMessageType.FAILURE;
        } else {
            summary = "对局结束：" + resolveTeamDisplayName(room, state, winner) + " 获胜";
            type = ClientMessageType.INFO;
        }
        ServerLogger.sendToClient(player, type, summary);
        if (winner != null) {
            ServerLogger.sendToClient(player, ClientMessageType.INFO, "胜方：%s", resolveTeamDisplayName(room, state, winner));
            if (winnerPlayerId != null) {
                ServerLogger.sendToClient(player, ClientMessageType.INFO, "达成者：%s", resolvePlayerName(winnerPlayerId));
            }
        } else if (winnerPlayerId != null) {
            ServerLogger.sendToClient(player, ClientMessageType.INFO, "胜者：%s", resolvePlayerName(winnerPlayerId));
        }
        if (reason != null && !reason.isBlank() && !"-".equals(reason)) {
            ServerLogger.sendToClient(player, ClientMessageType.INFO, "结束原因：%s", reason);
        }
    }

    private void awardMatchExperience(RoomDefinition room, RunningRuntimeState state, UUID playerId, ServerPlayer player, RoomTeamId winner, UUID winnerPlayerId, long gameTick) {
        if (room == null || state == null || playerId == null || player == null) {
            return;
        }
        KilliconScoreboardBridge.TempSnapshot snapshot = KilliconScoreboardBridge.getPlayerSnapshot(state, playerId);
        PlayerLevelService.MatchOutcome outcome = resolveMatchOutcome(playerId, state.getRoomTeams().get(playerId), winner, winnerPlayerId);
        
        int rank = -1;
        if (RoomGameplayId.GUNMASTER.equals(room.gameplayId()) && room.gunmasterConfig().isSoloGrouping()) {
            rank = resolveGunmasterSoloRank(state, playerId);
        }

        PlayerLevelService.awardAndNotifyMatchExperience(
            player,
            new PlayerLevelService.MatchStats(
                snapshot.score(),
                snapshot.kill(),
                snapshot.assist(),
                snapshot.death(),
                resolveMatchDurationSeconds(state, gameTick),
                rank
            ),
            outcome
        );
    }

    private PlayerLevelService.MatchOutcome resolveMatchOutcome(UUID selfPlayerId, RoomTeamId selfTeam, RoomTeamId winner, UUID winnerPlayerId) {
        if (winner != null) {
            return winner == selfTeam
                ? PlayerLevelService.MatchOutcome.WIN
                : PlayerLevelService.MatchOutcome.LOSE;
        }
        if (winnerPlayerId != null) {
            return winnerPlayerId.equals(selfPlayerId)
                ? PlayerLevelService.MatchOutcome.WIN
                : PlayerLevelService.MatchOutcome.LOSE;
        }
        return PlayerLevelService.MatchOutcome.DRAW;
    }

    private MatchResultType resolveResultType(UUID selfPlayerId, RoomTeamId selfTeam, RoomTeamId winner, UUID winnerPlayerId) {
        if (winner != null) {
            return winner == selfTeam
                ? MatchResultType.VICTORY
                : MatchResultType.DEFEAT;
        }
        if (winnerPlayerId != null) {
            return winnerPlayerId.equals(selfPlayerId)
                ? MatchResultType.VICTORY
                : MatchResultType.DEFEAT;
        }
        return MatchResultType.DRAW;
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
        if (RoomGameplayId.GUNMASTER.equals(room.gameplayId())
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
}
