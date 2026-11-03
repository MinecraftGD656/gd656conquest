package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.common.match.MatchResultType;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataStore;
import org.mods.gd656conquest.server.data.roomstatisticdata.RoomStatisticDataModel;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayPresentationProvider;
import org.mods.gd656conquest.server.room.gameplay.RoomSoloGameplaySupport;
import org.mods.gd656conquest.server.room.gameplay.RoomMatchEndRequest;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RoomMatchStatisticsRuntimeService {
    private static final double TICKS_PER_MINUTE = 20.0D * 60.0D;
    private static final long MIN_COMPLETED_MATCH_ACTIVE_TICKS = 20L * 60L * 3L;
    private static final double MIN_COMPLETED_MATCH_ACTIVE_RATIO = 0.25D;

    private final MinecraftServer server;

    public RoomMatchStatisticsRuntimeService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void beginMatchSessions(RunningRuntimeState runningState, long gameTick) {
        if (runningState == null) {
            return;
        }
        long matchInstanceId = runningState.getActiveMatchInstanceId();
        if (matchInstanceId <= 0L) {
            return;
        }
        for (UUID playerId : runningState.getRoomPlayers()) {
            RoomTeamId teamId = runningState.getRoomTeams().get(playerId);
            runningState.getMatchParticipantSessions().put(
                playerId,
                new RunningRuntimeState.MatchParticipantSession(matchInstanceId, gameTick, gameTick, false, teamId)
            );
        }
    }

    public void trackMidJoin(RunningRuntimeState runningState, UUID playerId, RoomTeamId teamId, long gameTick) {
        upsertSession(runningState, playerId, teamId, gameTick, true);
    }

    public void trackRejoin(RunningRuntimeState runningState, UUID playerId, RoomTeamId teamId, long gameTick) {
        upsertSession(runningState, playerId, teamId, gameTick, false);
    }

    public void pausePlayerSession(RunningRuntimeState runningState, UUID playerId, long gameTick) {
        if (runningState == null || playerId == null) {
            return;
        }
        RunningRuntimeState.MatchParticipantSession session = runningState.getMatchParticipantSessions().get(playerId);
        if (!isSessionForActiveMatch(runningState, session)) {
            return;
        }
        accumulateActiveTicks(session, gameTick);
        session.setPaused(true);
    }

    public void finalizeMatchResults(RunningRuntimeState runningState, RoomMatchEndRequest request, long gameTick) {
        if (runningState == null || request == null) {
            return;
        }
        long activeMatchInstanceId = runningState.getActiveMatchInstanceId();
        if (activeMatchInstanceId <= 0L) {
            return;
        }
        runningState.setFinalizedMatchEndTick(Math.max(0L, gameTick));
        for (Map.Entry<UUID, RoomMatchEndRequest.PlayerResultEntry> entry : request.playerResults().entrySet()) {
            UUID playerId = entry.getKey();
            if (playerId == null) {
                continue;
            }
            RoomTeamId teamId = runningState.getRoomTeams().get(playerId);
            RunningRuntimeState.MatchParticipantSession session = runningState.getMatchParticipantSessions().computeIfAbsent(
                playerId,
                ignored -> new RunningRuntimeState.MatchParticipantSession(activeMatchInstanceId, gameTick, gameTick, false, teamId)
            );
            session.setLastKnownTeamId(teamId);
            accumulateActiveTicks(session, gameTick);
            session.setPaused(true);
            MatchResultType resultType = entry.getValue() == null ? MatchResultType.DRAW : entry.getValue().resultType();
            session.setFinalResultType(resultType);
        }
    }

    public void persistFinalizedStatistics(RoomDefinition room, RunningRuntimeState runningState, long gameTick) {
        if (room == null || runningState == null) {
            return;
        }
        long activeMatchInstanceId = runningState.getActiveMatchInstanceId();
        if (activeMatchInstanceId <= 0L) {
            return;
        }
        long matchEndTick = resolveMatchEndTick(runningState, gameTick);
        PlayerDataStore playerDataStore = ConquestDataManager.of(server).playerData();
        int completedParticipants = 0;
        for (Map.Entry<UUID, RunningRuntimeState.MatchParticipantSession> entry : runningState.getMatchParticipantSessions().entrySet()) {
            UUID playerId = entry.getKey();
            RunningRuntimeState.MatchParticipantSession session = entry.getValue();
            if (playerId == null || !isSessionForActiveMatch(runningState, session) || session.getFinalResultType() == null) {
                continue;
            }
            PlayerDataModel data = playerDataStore.getPlayerData(playerId);
            PlayerDataModel.MatchStatistics statistics = data.getMatchStatistics();
            statistics.setMatchActiveMinutes(statistics.getMatchActiveMinutes() + ticksToMinutes(session.getAccumulatedActiveTicks()));
            if (session.getAccumulatedActiveTicks() >= resolveCompletedMatchThresholdTicks(runningState, matchEndTick)) {
                completedParticipants++;
                statistics.setMatchesPlayed(statistics.getMatchesPlayed() + 1);
                switch (session.getFinalResultType()) {
                    case VICTORY -> statistics.setWins(statistics.getWins() + 1);
                    case DEFEAT -> statistics.setLosses(statistics.getLosses() + 1);
                    case DRAW -> statistics.setDraws(statistics.getDraws() + 1);
                }
            }
            playerDataStore.savePlayerData(playerId, data);
        }
        accumulateRoomStatistics(room, runningState, completedParticipants, matchEndTick);
    }

    private void upsertSession(RunningRuntimeState runningState, UUID playerId, RoomTeamId teamId, long gameTick, boolean lateJoin) {
        if (runningState == null || playerId == null) {
            return;
        }
        long activeMatchInstanceId = runningState.getActiveMatchInstanceId();
        if (activeMatchInstanceId <= 0L) {
            return;
        }
        RunningRuntimeState.MatchParticipantSession session = runningState.getMatchParticipantSessions().get(playerId);
        if (!isSessionForActiveMatch(runningState, session)) {
            session = new RunningRuntimeState.MatchParticipantSession(activeMatchInstanceId, gameTick, gameTick, lateJoin, teamId);
            runningState.getMatchParticipantSessions().put(playerId, session);
            return;
        }
        session.setLastKnownTeamId(teamId);
        session.setLateJoin(session.isLateJoin() || lateJoin);
        session.setLastResumeTick(Math.max(0L, gameTick));
        session.setPaused(false);
    }

    private boolean isSessionForActiveMatch(RunningRuntimeState runningState, RunningRuntimeState.MatchParticipantSession session) {
        return runningState != null
            && session != null
            && runningState.getActiveMatchInstanceId() > 0L
            && session.getMatchInstanceId() == runningState.getActiveMatchInstanceId();
    }

    private void accumulateActiveTicks(RunningRuntimeState.MatchParticipantSession session, long gameTick) {
        if (session == null || session.isPaused()) {
            return;
        }
        long safeTick = Math.max(0L, gameTick);
        long delta = Math.max(0L, safeTick - session.getLastResumeTick());
        if (delta > 0L) {
            session.setAccumulatedActiveTicks(session.getAccumulatedActiveTicks() + delta);
        }
        session.setLastResumeTick(safeTick);
    }

    private double ticksToMinutes(long ticks) {
        return Math.max(0L, ticks) / TICKS_PER_MINUTE;
    }

    private long resolveCompletedMatchThresholdTicks(RunningRuntimeState runningState, long gameTick) {
        if (runningState == null) {
            return MIN_COMPLETED_MATCH_ACTIVE_TICKS;
        }
        long totalMainPhaseTicks = Math.max(0L, Math.max(0L, gameTick) - resolveMatchStartTick(runningState));
        long ratioThreshold = (long) Math.ceil(totalMainPhaseTicks * MIN_COMPLETED_MATCH_ACTIVE_RATIO);
        return Math.min(MIN_COMPLETED_MATCH_ACTIVE_TICKS, Math.max(0L, ratioThreshold));
    }

    private void accumulateRoomStatistics(
        RoomDefinition room,
        RunningRuntimeState runningState,
        int completedParticipants,
        long matchEndTick
    ) {
        RoomStatisticDataModel roomStatistics = ConquestDataManager.of(server).roomStatisticData().getRoomStatistics(room.roomId());
        roomStatistics.setTotalMatches(roomStatistics.totalMatchesValue() + 1);
        roomStatistics.setTotalPlayCount(roomStatistics.totalPlayCountValue() + Math.max(0, completedParticipants));
        roomStatistics.setTotalMatchDurationSeconds(
            roomStatistics.totalMatchDurationSecondsValue() + resolveMatchDurationSeconds(runningState, matchEndTick)
        );
        if (RoomSoloGameplaySupport.isDeathmatchSolo(room)) {
            roomStatistics.setCampAWins(roomStatistics.campAWinsValue() + 1);
            roomStatistics.setCampBWins(roomStatistics.campBWinsValue() + 1);
            ConquestDataManager.of(server).roomStatisticData().saveRoomStatistics(roomStatistics);
            return;
        }
        RoomTeamId winningTeam = resolveWinningTeam(runningState);
        if (winningTeam == RoomTeamId.CAMP_A) {
            roomStatistics.setCampAWins(roomStatistics.campAWinsValue() + 1);
        } else if (winningTeam == RoomTeamId.CAMP_B) {
            roomStatistics.setCampBWins(roomStatistics.campBWinsValue() + 1);
        } else {
            roomStatistics.setDrawMatches(roomStatistics.drawMatchesValue() + 1);
        }
        ConquestDataManager.of(server).roomStatisticData().saveRoomStatistics(roomStatistics);
    }

    private RoomTeamId resolveWinningTeam(RunningRuntimeState runningState) {
        boolean campAVictory = false;
        boolean campBVictory = false;
        boolean hasDraw = false;
        for (RunningRuntimeState.MatchParticipantSession session : runningState.getMatchParticipantSessions().values()) {
            if (!isSessionForActiveMatch(runningState, session) || session.getFinalResultType() == null) {
                continue;
            }
            if (session.getFinalResultType() == MatchResultType.DRAW) {
                hasDraw = true;
                continue;
            }
            if (session.getFinalResultType() == MatchResultType.VICTORY) {
                if (session.getLastKnownTeamId() == RoomTeamId.CAMP_A) {
                    campAVictory = true;
                } else if (session.getLastKnownTeamId() == RoomTeamId.CAMP_B) {
                    campBVictory = true;
                }
            }
        }
        if (hasDraw || (campAVictory && campBVictory) || (!campAVictory && !campBVictory)) {
            return null;
        }
        return campAVictory ? RoomTeamId.CAMP_A : RoomTeamId.CAMP_B;
    }

    private long resolveMatchEndTick(RunningRuntimeState runningState, long gameTick) {
        if (runningState == null) {
            return Math.max(0L, gameTick);
        }
        long finalizedTick = runningState.getFinalizedMatchEndTick();
        return finalizedTick >= 0L ? finalizedTick : Math.max(0L, gameTick);
    }

    private long resolveMatchDurationSeconds(RunningRuntimeState runningState, long matchEndTick) {
        if (runningState == null) {
            return 0L;
        }
        RoomGameplayPresentationProvider presentationProvider = RoomGameplayPresentationProvider.resolve(runningState);
        if (presentationProvider != null) {
            return Math.max(0L, presentationProvider.matchDurationSeconds(matchEndTick));
        }
        long matchTicks = Math.max(0L, matchEndTick - resolveMatchStartTick(runningState));
        return (long) Math.round(matchTicks / 20.0D);
    }

    private long resolveMatchStartTick(RunningRuntimeState runningState) {
        if (runningState == null) {
            return 0L;
        }
        return Math.max(0L, runningState.getActiveMatchStartTick());
    }
}
