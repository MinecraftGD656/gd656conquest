package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.runtime.RoomListPresentationService;
import org.mods.gd656conquest.server.social.party.presentation.PartyUiFacade;

public final class RoomJoinAccessPolicy {
    private static final String ROOM_SERVICE_NOT_INITIALIZED = "房间服务尚未初始化";
    private static final String MAINTENANCE_ADMIN_ONLY = "加入失败，维护房间仅管理员可进入";

    /** GD656Mods: 组队进入门槛 —— 队伍人数 x 时, 房间当前人数必须 >= x * 该倍数。 */
    private static final int PARTY_JOIN_PLAYER_RATIO = 3;
    /**
     * GD656Mods: 门槛只对 2 人及以上组队生效。
     * 若连 x = 1 也限制, 单人将无法进入任何当前人数 < 3 的房间, 相当于禁掉单人开局。
     */
    private static final int PARTY_JOIN_MIN_RESTRICTED_SIZE = 2;
    private static final String PARTY_JOIN_COUNT_DENIED = "当前对局人数不满足组队进入条件";

    private RoomJoinAccessPolicy() {
    }

    public static JoinCheckResult evaluate(ServerPlayer player, RoomDefinition room, RoomCoreRuntimeManager manager) {
        if (player == null || player.server == null) {
            return JoinCheckResult.denied("", false);
        }
        if (manager == null || !manager.isInitialized()) {
            return JoinCheckResult.denied(ROOM_SERVICE_NOT_INITIALIZED, false);
        }
        if (room == null) {
            return JoinCheckResult.denied("未找到房间", false);
        }
        RoomLifecycleState lifecycleState = room.lifecycleState();
        if (lifecycleState == RoomLifecycleState.LOCKED) {
            // GD656Mods: 活跃对局数量达到上限时的临时锁定
            return JoinCheckResult.denied("加入失败，该房间已被锁定：服务器当前活跃对局数量已达上限", false);
        }
        if (lifecycleState == RoomLifecycleState.DISABLED
            || lifecycleState == RoomLifecycleState.ENDING
            || lifecycleState == RoomLifecycleState.CLEANING
            || lifecycleState == RoomLifecycleState.SAVING) {
            return JoinCheckResult.denied("加入失败，当前房间状态为: " + lifecycleState.name(), false);
        }
        boolean maintenance = lifecycleState == RoomLifecycleState.MAINTENANCE;
        if (maintenance && !player.hasPermissions(2)) {
            return JoinCheckResult.denied(MAINTENANCE_ADMIN_ONLY, true);
        }
        // GD656Mods: 组队进入门槛。
        // 之所以落在这里: evaluate 是所有进房路径的唯一准入点 —— 队长个人进局、队伍整体进局
        // (PartyRoomBridgeService / RoomSessionTransitionService 逐成员调用), 以及房间列表与
        // 告示牌的可点性判定都经过它。因此这三处天然一致, 不会出现"列表显示能进、点进去被拒"。
        int partySize = resolvePartySize(player);
        if (!isPartyJoinCountSatisfied(partySize, RoomListPresentationService.resolveRoomPlayerCount(room, manager))) {
            return JoinCheckResult.denied(PARTY_JOIN_COUNT_DENIED, maintenance);
        }
        return JoinCheckResult.allowed(maintenance);
    }

    /**
     * GD656Mods: 组队进入门槛的纯判定 —— 便于单测。
     *
     * <p>x &lt; 2 不受限; 否则要求房间当前人数 &gt;= 3x。
     * 房间人数口径与房间列表保持一致(匹配中的房间取队列人数, 否则取房间运行态的玩家数)。
     */
    static boolean isPartyJoinCountSatisfied(int partySize, int roomPlayerCount) {
        if (partySize < PARTY_JOIN_MIN_RESTRICTED_SIZE) {
            return true;
        }
        return roomPlayerCount >= partySize * PARTY_JOIN_PLAYER_RATIO;
    }

    /** GD656Mods: 玩家当前所在队伍的人数; 不在队伍中返回 0。 */
    private static int resolvePartySize(ServerPlayer player) {
        if (player == null || player.server == null) {
            return 0;
        }
        return PartyUiFacade.of(player.server)
            .findCurrentParty(player)
            .map(description -> description.members() == null ? 0 : description.members().size())
            .orElse(0);
    }

    public record JoinCheckResult(boolean allowed, String deniedMessage, boolean maintenance) {
        private static JoinCheckResult allowed(boolean maintenance) {
            return new JoinCheckResult(true, "", maintenance);
        }

        private static JoinCheckResult denied(String deniedMessage, boolean maintenance) {
            return new JoinCheckResult(false, deniedMessage == null ? "" : deniedMessage, maintenance);
        }
    }
}
