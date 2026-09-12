package org.mods.gd656conquest.server.room.gameplay;

import org.mods.gd656conquest.server.room.core.RoomPlayerInvalidationReason;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.Optional;
import java.util.UUID;

public interface IRoomGameplayRuntime {
    void onEnterMainPhase(RoomDefinition room, long gameTick);

    void tick(RoomDefinition room, long gameTick);

    /**
     * 返回当前玩法是否允许执行实验性局内自动换队。
     * 不提供 default 实现，目的是强制每一个新玩法在接入时显式补齐该规则。
     */
    RoomAutoBalanceWindowSnapshot autoBalanceWindowSnapshot(RoomDefinition room, long gameTick);

    default void onPlayerInvalidated(
        RoomDefinition room,
        UUID playerId,
        RoomTeamId teamId,
        RoomPlayerInvalidationReason reason,
        int killerEntityId,
        long gameTick
    ) {
    }

    default void onLeaveMainPhase(RoomDefinition room, long gameTick) {
    }

    default Optional<RoomMatchEndRequest> pollMatchEndRequest() {
        return Optional.empty();
    }

    /**
     * 玩家当前是否正在执行"优先操作"(如 Rush 拆弹/装弹)。
     * 优先操作会抢占同键位(通用使用键 F)的其他交互, 如救援倒地队友。
     */
    default boolean isPerformingPriorityAction(UUID playerId) {
        return false;
    }
}
