package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * GD656Mods: 对局阵营内小队(squad)的归属分配。
 *
 * <p>小队容量固定 {@link #SQUAD_SIZE}(4)。匹配阶段与对局阶段各有一张归属表
 * (匹配期 {@code runtimeState.getSquadLabelsByPlayer()}、对局期
 * {@code getRunningState().getSquadLabelsByPlayer()}), 其余规则完全一致 ——
 * 这里用 {@link SquadView} 把两者差异收进一处, 避免同一条规则写两遍。
 *
 * <p><strong>整组(party)分配约定</strong>: 从 A 起依次检索目标阵营的全部小队,
 * 取第一个"剩余空位 &gt;= 组人数"的小队; 若无任何小队装得下整组, 返回空 label,
 * 由调用方退化为逐个落位 —— 绝不允许"整组塞进半满小队"导致超员。
 * 组人数超过单队上限时同样走退化路径(否则必然超员)。
 */
public final class RoomSquadAssignmentService {
    /** GD656Mods: 单个小队的容量上限。 */
    public static final int SQUAD_SIZE = 4;
    /** GD656Mods: label 检索上限(A..Z / 27..2048), 防御性上限。 */
    private static final int MAX_SQUAD_SCAN = 2048;

    private final MinecraftServer server;

    public RoomSquadAssignmentService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public int squadSize() {
        return SQUAD_SIZE;
    }

    /** GD656Mods: 单人落位 —— 保留"已在小队且不算满"的位置, 否则挑第一个未满的小队。 */
    public void assignPlayer(RoomRuntimeState runtimeState, UUID playerId, RoomTeamId teamId, boolean running) {
        SquadView view = viewOf(runtimeState, running);
        if (view == null || playerId == null) {
            return;
        }
        if (teamId == null) {
            view.clear(playerId);
            return;
        }
        String existing = view.labelOf(playerId);
        if (hasText(existing) && !isSquadFull(view, teamId, existing, playerId)) {
            view.write(playerId, existing);
            return;
        }
        view.write(playerId, chooseSingleSquadLabel(countSquadMembers(view, teamId)));
    }

    /** GD656Mods: 指定小队落位(整组分配的结果); {@code squadLabel} 为空时退回单人策略。 */
    public void assignSquad(RoomRuntimeState runtimeState, UUID playerId, RoomTeamId teamId, String squadLabel, boolean running) {
        SquadView view = viewOf(runtimeState, running);
        if (view == null || playerId == null) {
            return;
        }
        if (!hasText(squadLabel)) {
            assignPlayer(runtimeState, playerId, teamId, running);
            return;
        }
        view.write(playerId, squadLabel);
    }

    /**
     * GD656Mods: 为整组挑选小队 —— 从 A 起找第一个"剩余空位 &gt;= groupSize"的小队。
     *
     * @return 目标 label; 若没有任何小队装得下整组(含 groupSize 超过单队上限), 返回空串
     */
    public String allocateSquadLabel(RoomRuntimeState runtimeState, RoomTeamId teamId, int groupSize, boolean running) {
        SquadView view = viewOf(runtimeState, running);
        if (view == null || teamId == null) {
            return "";
        }
        return chooseGroupSquadLabel(countSquadMembers(view, teamId), groupSize);
    }

    public void removePlayer(RoomRuntimeState runtimeState, UUID playerId) {
        if (runtimeState == null || playerId == null) {
            return;
        }
        runtimeState.getSquadLabelsByPlayer().remove(playerId);
    }

    public String describeSquad(RoomRuntimeState runtimeState, UUID playerId) {
        if (runtimeState == null || playerId == null) {
            return "";
        }
        return runtimeState.getSquadLabelsByPlayer().getOrDefault(playerId, "");
    }

    /**
     * GD656Mods: 整组选队(纯逻辑, 便于单测)。
     * 从 A 起顺序检索, 取第一个剩余空位足够的; 都不够(或组人数超过单队上限)时返回空串。
     */
    public static String chooseGroupSquadLabel(Map<String, Integer> counts, int groupSize) {
        int required = Math.max(1, groupSize);
        if (required > SQUAD_SIZE) {
            return "";
        }
        Map<String, Integer> safeCounts = counts == null ? Map.of() : counts;
        for (int index = 0; index < MAX_SQUAD_SCAN; index++) {
            String label = toSquadLabel(index);
            if (SQUAD_SIZE - safeCounts.getOrDefault(label, 0) >= required) {
                return label;
            }
        }
        return "";
    }

    /** GD656Mods: 单人选队(纯逻辑, 便于单测) —— 第一个未满的小队, 都满则开一个未使用的 label。 */
    public static String chooseSingleSquadLabel(Map<String, Integer> counts) {
        Map<String, Integer> safeCounts = counts == null ? Map.of() : counts;
        for (int index = 0; index < MAX_SQUAD_SCAN; index++) {
            String label = toSquadLabel(index);
            if (safeCounts.getOrDefault(label, 0) < SQUAD_SIZE) {
                return label;
            }
        }
        // 所有小队都满: 开新小队(label 必须未使用 —— 直接按 counts.size() 取会在 label 稀疏时撞车)
        for (int index = 0; index < MAX_SQUAD_SCAN; index++) {
            String label = toSquadLabel(index);
            if (!safeCounts.containsKey(label)) {
                return label;
            }
        }
        return toSquadLabel(safeCounts.size());
    }

    private Map<String, Integer> countSquadMembers(SquadView view, RoomTeamId teamId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (UUID memberId : view.members()) {
            if (memberId == null || view.teamByPlayer().get(memberId) != teamId) {
                continue;
            }
            String label = view.labelOf(memberId);
            if (!hasText(label)) {
                continue;
            }
            counts.merge(label, 1, Integer::sum);
        }
        return counts;
    }

    /** GD656Mods: 某玩家(排除自己)所在小队是否已满 —— 用于判断"留在原队是否合适"。 */
    private boolean isSquadFull(SquadView view, RoomTeamId teamId, String squadLabel, UUID excludingPlayerId) {
        int count = 0;
        for (UUID memberId : view.members()) {
            if (memberId == null || memberId.equals(excludingPlayerId)) {
                continue;
            }
            if (view.teamByPlayer().get(memberId) != teamId) {
                continue;
            }
            if (squadLabel.equalsIgnoreCase(view.labelOf(memberId))) {
                count++;
            }
        }
        return count >= SQUAD_SIZE;
    }

    private SquadView viewOf(RoomRuntimeState runtimeState, boolean running) {
        if (runtimeState == null) {
            return null;
        }
        if (!running) {
            MatchingRuntimeState matchingState = runtimeState.getMatchingState();
            return new SquadView(
                matchingState.getQueueTeams(),
                matchingState.getQueuePlayers(),
                runtimeState.getSquadLabelsByPlayer(),
                null
            );
        }
        return new SquadView(
            runtimeState.getRunningState().getRoomTeams(),
            runtimeState.getRunningState().getRoomPlayers(),
            runtimeState.getRunningState().getSquadLabelsByPlayer(),
            runtimeState.getSquadLabelsByPlayer()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String toSquadLabel(int index) {
        int normalized = Math.max(0, index);
        if (normalized < 26) {
            return String.valueOf((char) ('A' + normalized));
        }
        return String.valueOf(normalized + 1);
    }

    /**
     * GD656Mods: 小队归属表的统一视图 —— 匹配期与对局期只差"用哪张表 + 哪些玩家"。
     * 对局期需要同时把结果镜像回总表(原实现就是写两张表), 因此 {@code mirrorLabels} 仅在 running 时非空。
     */
    private record SquadView(
        Map<UUID, RoomTeamId> teamByPlayer,
        Collection<UUID> members,
        Map<UUID, String> labels,
        Map<UUID, String> mirrorLabels
    ) {
        String labelOf(UUID playerId) {
            return labels.get(playerId);
        }

        void write(UUID playerId, String label) {
            labels.put(playerId, label);
            if (mirrorLabels != null) {
                mirrorLabels.put(playerId, label);
            }
        }

        void clear(UUID playerId) {
            labels.remove(playerId);
            if (mirrorLabels != null) {
                mirrorLabels.remove(playerId);
            }
        }
    }
}
