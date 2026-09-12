package org.mods.gd656conquest.server.room.matching;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RoomTeamBalanceRatingService {
    private static final double MIN_ACTIVE_MINUTES = 1.0D;
    private static final double FULL_SAMPLE_MINUTES = 180.0D;
    private static final double FULL_SAMPLE_MATCHES = 20.0D;
    private static final double BASE_RELIABILITY_WITH_HISTORY = 0.35D;
    private static final double BASE_RELIABILITY_NO_HISTORY = 0.15D;

    private final MinecraftServer server;

    public RoomTeamBalanceRatingService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public double resolvePlayerRating(UUID playerId) {
        if (playerId == null) {
            return 0.0D;
        }
        KilliconScoreboardBridge.TempSnapshot snapshot = KilliconScoreboardBridge.getGlobalSnapshot(playerId);
        PlayerDataModel.MatchStatistics matchStatistics = ConquestDataManager.of(server)
            .playerData()
            .getPlayerData(playerId)
            .getMatchStatistics();

        double activeMinutes = Math.max(MIN_ACTIVE_MINUTES, matchStatistics.getMatchActiveMinutes());
        int matchesPlayed = Math.max(0, matchStatistics.getMatchesPlayed());
        double scorePerMinute = snapshot.score() / activeMinutes;
        double killsPerMinute = snapshot.kill() / activeMinutes;
        double killDeathRatio = snapshot.kill() / Math.max(1.0D, snapshot.death());
        double teamplayPerMinute = (snapshot.assist() + (snapshot.revive() * 1.25D)) / activeMinutes;
        double winRate = matchesPlayed <= 0
            ? 0.5D
            : (matchStatistics.getWins() + (matchStatistics.getDraws() * 0.5D)) / Math.max(1.0D, matchesPlayed);

        double sampleByMinutes = clamp(matchStatistics.getMatchActiveMinutes() / FULL_SAMPLE_MINUTES, 0.0D, 1.0D);
        double sampleByMatches = clamp(matchesPlayed / FULL_SAMPLE_MATCHES, 0.0D, 1.0D);
        double reliability = (sampleByMinutes > 0.0D || sampleByMatches > 0.0D)
            ? BASE_RELIABILITY_WITH_HISTORY + (0.65D * Math.max(sampleByMinutes, sampleByMatches))
            : BASE_RELIABILITY_NO_HISTORY;

        double spmComponent = clamp(scorePerMinute / 600.0D, 0.0D, 1.25D);
        double kpmComponent = clamp(killsPerMinute / 1.2D, 0.0D, 1.2D);
        double kdComponent = clamp(killDeathRatio / 2.5D, 0.0D, 1.1D);
        double winComponent = clamp(winRate / 0.65D, 0.55D, 1.15D);
        double teamplayComponent = clamp(teamplayPerMinute / 0.6D, 0.0D, 1.0D);

        double weighted = (spmComponent * 0.40D)
            + (kpmComponent * 0.25D)
            + (kdComponent * 0.15D)
            + (winComponent * 0.10D)
            + (teamplayComponent * 0.10D);
        return weighted * reliability;
    }

    public Map<UUID, Double> resolveRatings(Collection<UUID> playerIds) {
        LinkedHashMap<UUID, Double> ratings = new LinkedHashMap<>();
        if (playerIds == null) {
            return ratings;
        }
        for (UUID playerId : playerIds) {
            if (playerId == null) {
                continue;
            }
            ratings.put(playerId, resolvePlayerRating(playerId));
        }
        return ratings;
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
