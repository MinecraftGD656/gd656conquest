package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.common.match.MatchResultType;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 对局结束请求 —— 结果以"每个玩家单独一条结算条目"表达, 不再使用全局"哪个队伍/哪个人赢"。
 * 各玩法运行器(裁判)负责为对局内全部玩家写入结果表; 结算层只查表执行。
 */
public record RoomMatchEndRequest(Map<UUID, PlayerResultEntry> playerResults, String reason) {

    public RoomMatchEndRequest {
        playerResults = playerResults == null ? Map.of() : Map.copyOf(playerResults);
        reason = reason == null ? "-" : reason.trim();
        if (reason.isEmpty()) {
            reason = "-";
        }
    }

    /**
     * 结构保证: 结果表必须覆盖 allPlayerIds 中的全部玩家。
     * 任何缺失都在创建期直接失败(而非结算期兜底), 从结构上杜绝"正常对局玩家结束后无结果"。
     */
    public RoomMatchEndRequest(Collection<UUID> allPlayerIds, Map<UUID, PlayerResultEntry> playerResults, String reason) {
        this(playerResults, reason);
        if (allPlayerIds != null) {
            for (UUID playerId : allPlayerIds) {
                if (playerId != null && !playerResults.containsKey(playerId)) {
                    throw new IllegalArgumentException("对局结果表缺少玩家: " + playerId);
                }
            }
        }
    }

    /** 全员同一结果(全平局/单阵营全胜全败等) */
    public static RoomMatchEndRequest forAll(Collection<UUID> allPlayerIds, MatchResultType resultType, String reason) {
        PlayerResultEntry entry = PlayerResultEntry.of(resultType);
        java.util.LinkedHashMap<UUID, PlayerResultEntry> results = new java.util.LinkedHashMap<>();
        if (allPlayerIds != null) {
            for (UUID playerId : allPlayerIds) {
                if (playerId != null) {
                    results.put(playerId, entry);
                }
            }
        }
        return new RoomMatchEndRequest(allPlayerIds, results, reason);
    }

    /**
     * 按队伍结果: winnerTeam 非空 -> 该队全员胜、另一队全员败; winnerTeam 为空 -> 全员平局。
     */
    public static RoomMatchEndRequest byTeam(
        Collection<UUID> allPlayerIds,
        Map<UUID, RoomTeamId> teamByPlayer,
        RoomTeamId winnerTeam,
        String reason
    ) {
        java.util.LinkedHashMap<UUID, PlayerResultEntry> results = new java.util.LinkedHashMap<>();
        if (allPlayerIds != null) {
            for (UUID playerId : allPlayerIds) {
                if (playerId == null) {
                    continue;
                }
                MatchResultType type;
                if (winnerTeam == null) {
                    type = MatchResultType.DRAW;
                } else {
                    RoomTeamId selfTeam = teamByPlayer == null ? null : teamByPlayer.get(playerId);
                    type = winnerTeam == selfTeam ? MatchResultType.VICTORY : MatchResultType.DEFEAT;
                }
                results.put(playerId, PlayerResultEntry.of(type));
            }
        }
        return new RoomMatchEndRequest(allPlayerIds, results, reason);
    }

    /** 每个玩家的结算条目: 结果状态 + 附加额外分(由对局运行器控制, 如枪神名次加成; 无则 0) */
    public record PlayerResultEntry(MatchResultType resultType, int extraScore) {
        public PlayerResultEntry(MatchResultType resultType) {
            this(resultType, 0);
        }

        public PlayerResultEntry {
            resultType = resultType == null ? MatchResultType.DRAW : resultType;
            extraScore = Math.max(0, extraScore);
        }

        public static PlayerResultEntry of(MatchResultType resultType) {
            return new PlayerResultEntry(resultType, 0);
        }
    }
}
