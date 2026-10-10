package org.mods.gd656conquest.server.room.entry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One room/match owns this ledger. A fatal event is counted once, including entry into downed. */
public final class MatchDuelStats {
    public record Score(int playerKills, int enemyKills) {}
    private record Pair(UUID killer, UUID victim) {}
    private final Map<Pair, Integer> kills = new HashMap<>();
    private final Map<UUID, Long> latestDeath = new HashMap<>();
    private long matchId = Long.MIN_VALUE;

    public Score record(long match, long deathId, UUID killer, UUID victim) {
        if (matchId != match) { matchId = match; kills.clear(); latestDeath.clear(); }
        if (deathId > latestDeath.getOrDefault(victim, 0L) && !killer.equals(victim)) {
            latestDeath.put(victim, deathId);
            kills.merge(new Pair(killer, victim), 1, (a, b) -> a == Integer.MAX_VALUE ? a : a + b);
        }
        return new Score(kills.getOrDefault(new Pair(victim, killer), 0), kills.getOrDefault(new Pair(killer, victim), 0));
    }
}
