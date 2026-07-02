package org.mods.gd656conquest.server.room.runtime;

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

    public boolean beginMatchEnd(RoomDefinition room, RunningRuntimeState state, RoomTeamId winner, String reason, long gameTick) {
        if (room == null || state == null || state.getResultSequenceEndTick() > 0) {
            return false;
        }
        state.setResultWinnerTeam(winner);
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
            MatchResultType resultType = resolveResultType(selfTeam, winner);
            NetworkManager.sendToPlayer(player, new RoomMatchResultPacket(
                true,
                resultType,
                MATCH_RESULT_TOTAL_TICKS,
                MATCH_RESULT_BLACK_START_TICKS,
                MATCH_RESULT_RESTORE_TICKS
            ));
            player.setInvulnerable(true);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            ServerPlayerLockService.of(server).syncPlayer(player);
            sendResultChat(room, state, winner, reason, player, resultType, selfTeam);
            awardMatchExperience(room, state, playerId, player, winner, gameTick);
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
            MATCH_RESULT_RESTORE_TICKS
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
        playerTransferQueueService.cancelPendingTransfers(playerId);
        state.clearRespawnFlow(playerId);
        ruleRuntimeService.clearPlayer(playerId);
        playerStateService.normalizePlayerModelState(player);
        NetworkManager.sendToPlayer(player, RoomDownedStatePacket.clear());
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.clear());
        NetworkManager.sendToPlayer(player, RoomDeathFlowPacket.setBoundaryPenalty(false));
        RoomTeamId selfTeam = state.getRoomTeams().get(playerId);
        RoomTeamId winner = state.getResultWinnerTeam();
        MatchResultType resultType = resolveResultType(selfTeam, winner);
        NetworkManager.sendToPlayer(player, new RoomMatchResultPacket(
            true,
            resultType,
            MATCH_RESULT_TOTAL_TICKS,
            MATCH_RESULT_BLACK_START_TICKS,
            MATCH_RESULT_RESTORE_TICKS
        ));
        player.setInvulnerable(true);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        sendResultChat(room, state, winner, state.getResultReason(), player, resultType, selfTeam);
        if (awardExperience && !state.getResultAwardedPlayers().contains(playerId)) {
            awardMatchExperience(room, state, playerId, player, winner, gameTick);
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

    private void sendResultChat(
        RoomDefinition room,
        RunningRuntimeState state,
        RoomTeamId winner,
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
            summary = "对局结束：你所在阵营获胜";
            type = ClientMessageType.SUCCESS;
        } else if (selfTeam == RoomTeamId.CAMP_A || selfTeam == RoomTeamId.CAMP_B) {
            summary = "对局结束：你所在阵营战败";
            type = ClientMessageType.FAILURE;
        } else {
            summary = "对局结束：" + resolveTeamDisplayName(room, state, winner) + " 获胜";
            type = ClientMessageType.INFO;
        }
        ServerLogger.sendToClient(player, type, summary);
        if (winner != null) {
            ServerLogger.sendToClient(player, ClientMessageType.INFO, "胜方：%s", resolveTeamDisplayName(room, state, winner));
        }
        if (reason != null && !reason.isBlank() && !"-".equals(reason)) {
            ServerLogger.sendToClient(player, ClientMessageType.INFO, "结束原因：%s", reason);
        }
    }

    private void awardMatchExperience(RoomDefinition room, RunningRuntimeState state, UUID playerId, ServerPlayer player, RoomTeamId winner, long gameTick) {
        if (room == null || state == null || playerId == null || player == null) {
            return;
        }
        KilliconScoreboardBridge.TempSnapshot snapshot = KilliconScoreboardBridge.getPlayerSnapshot(state, playerId);
        PlayerLevelService.MatchOutcome outcome = resolveMatchOutcome(state.getRoomTeams().get(playerId), winner);
        PlayerLevelService.awardAndNotifyMatchExperience(
            player,
            new PlayerLevelService.MatchStats(
                snapshot.score(),
                snapshot.kill(),
                snapshot.assist(),
                snapshot.death(),
                resolveMatchDurationSeconds(state, gameTick)
            ),
            outcome
        );
    }

    private PlayerLevelService.MatchOutcome resolveMatchOutcome(RoomTeamId selfTeam, RoomTeamId winner) {
        if (winner == null) {
            return PlayerLevelService.MatchOutcome.DRAW;
        }
        return winner == selfTeam
            ? PlayerLevelService.MatchOutcome.WIN
            : PlayerLevelService.MatchOutcome.LOSE;
    }

    private MatchResultType resolveResultType(RoomTeamId selfTeam, RoomTeamId winner) {
        if (winner == null) {
            return MatchResultType.DRAW;
        }
        return winner == selfTeam
            ? MatchResultType.VICTORY
            : MatchResultType.DEFEAT;
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
}
