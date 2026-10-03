package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.core.RoomCoreService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.shared.text.ServerText;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * GD656Mods: 活跃房间数量上限(最大同时进行的对局数)。
 *
 * <p>规则:
 * <ul>
 *     <li>"活跃"= {@link RoomLifecycleState#RUNNING}(匹配不算);上限由服务端配置 {@code maxActiveRooms} 控制,
 *         {@code <=0} 表示不限制;</li>
 *     <li>某个房间进入 RUNNING 后若活跃数已达上限, 其余<strong>非维护/非禁用</strong>房间全部转入
 *         {@link RoomLifecycleState#LOCKED}:空闲房间直接锁定, 正在匹配的房间先清退队列(并给出字幕反馈)、
 *         卸载维度并重置地图(与 cleanRoom 相同, 但<strong>不重新加载维度</strong>)再锁定;</li>
 *     <li><strong>正在对局(RUNNING)与正在收尾(ENDING/SAVING/CLEANING)的房间绝不干预</strong>;</li>
 *     <li>活跃数下降出现空位时, 被锁定的房间自动回到 IDLE(维度保持卸载, 需要时再加载)。</li>
 * </ul>
 *
 * <p>性能: 判定只在"房间进入 RUNNING / 回到 IDLE"这类状态变化时触发, 不做每 tick 扫描;
 * 单次判定是 32 个房间的内存比较(微秒级), 唯一的重活是"清退匹配房间"(卸载+重置), 而匹配中的房间通常只有 0~2 个。
 */
public final class RoomActiveLimitService {
    private static final Map<MinecraftServer, RoomActiveLimitService> INSTANCES = new WeakHashMap<>();

    /** GD656Mods: 占用活跃名额的状态 —— 只有真正开打才算。 */
    private static final Set<RoomLifecycleState> ACTIVE_STATES = Set.of(RoomLifecycleState.RUNNING);

    private final MinecraftServer server;
    /** GD656Mods: 被本服务锁定的房间(内存账本, 不持久化 —— 重启后由 recomputeOnStartup 重算)。 */
    private final Set<String> lockedRoomIds = new LinkedHashSet<>();
    /** GD656Mods: 防重入 —— reevaluate 内部会改动房间状态并可能再次回调。 */
    private boolean applying;

    private RoomActiveLimitService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public static RoomActiveLimitService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, RoomActiveLimitService::new);
        }
    }

    public static void forget(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }

    public boolean isLocked(String roomId) {
        return roomId != null && lockedRoomIds.contains(roomId);
    }

    public Set<String> lockedRoomIds() {
        return Set.copyOf(lockedRoomIds);
    }

    /**
     * GD656Mods: 是否还有空位(即不需要锁定房间)。
     *
     * <ul>
     *     <li>{@code limit < 0} —— 功能已禁用(不限制), 恒为"有空位";</li>
     *     <li>{@code limit == 0} —— 上限为零, 恒为"无空位"(锁定全部房间, 不允许任何对局);</li>
     *     <li>其余按 {@code active < limit} 判断。</li>
     * </ul>
     */
    public static boolean hasFreeSlot(int limit, int active) {
        return limit < 0 || active < limit;
    }

    /** GD656Mods: 房间进入 RUNNING 之后调用。 */
    public void onRoomEnteredRunning(RoomCoreService coreService, String runningRoomId, long gameTick) {
        reevaluate(coreService, gameTick, runningRoomId);
    }

    /** GD656Mods: 房间回到 IDLE 之后调用(有空位就解锁)。 */
    public void reevaluate(RoomCoreService coreService, long gameTick) {
        reevaluate(coreService, gameTick, null);
    }

    /**
     * GD656Mods: 服务器启动后重算 —— 清掉上次运行遗留的 LOCKED(它是派生状态, 不能跨重启残留),
     * 若恢复流程里已有对局在跑则立即重新评估一次。
     */
    public void recomputeOnStartup(RoomCoreService coreService, long gameTick) {
        lockedRoomIds.clear();
        if (coreService == null) {
            return;
        }
        for (RoomDefinition room : coreService.listRooms()) {
            if (room != null && room.lifecycleState() == RoomLifecycleState.LOCKED) {
                coreService.persistRoomLifecycleState(room.roomId(), RoomLifecycleState.IDLE);
            }
        }
        int limit = resolveLimit();
        if (limit >= 0 && countActive(coreService) >= limit) {
            reevaluate(coreService, gameTick);
        }
    }

    private void reevaluate(RoomCoreService coreService, long gameTick, String justStartedRoomId) {
        if (coreService == null || applying) {
            return;
        }
        int limit = resolveLimit();
        int active = countActive(coreService);
        // GD656Mods: limit < 0 表示功能已禁用(不限制); 否则 active < limit 表示仍有空位。
        // 注意 limit == 0 时 0 < 0 为 false, 会走到下面的锁定分支 —— 即"不允许任何对局"。
        if (hasFreeSlot(limit, active)) {
            // 未启用限制, 或仍有空位: 把锁定房间放回空闲(维度不主动加载, 需要时再加载)
            unlockAll(coreService);
            return;
        }
        applying = true;
        try {
            for (RoomDefinition room : coreService.listRooms()) {
                if (room == null || room.roomId() == null || room.roomId().isBlank()) {
                    continue;
                }
                if (justStartedRoomId != null && justStartedRoomId.equalsIgnoreCase(room.roomId())) {
                    continue;
                }
                RoomLifecycleState state = room.lifecycleState();
                if (state == null
                    || state == RoomLifecycleState.DISABLED
                    || state == RoomLifecycleState.MAINTENANCE
                    || state == RoomLifecycleState.LOCKED) {
                    continue;
                }
                if (ACTIVE_STATES.contains(state)
                    || state == RoomLifecycleState.ENDING
                    || state == RoomLifecycleState.SAVING
                    || state == RoomLifecycleState.CLEANING) {
                    // 正在对局或正在收尾: 绝不干预(否则会踢掉正在打的玩家 / 打断维度重置流程)
                    continue;
                }
                lock(coreService, room, gameTick);
            }
        } finally {
            applying = false;
        }
    }

    private void lock(RoomCoreService coreService, RoomDefinition room, long gameTick) {
        String roomId = room.roomId();
        RoomLifecycleState previous = room.lifecycleState();
        if (previous == RoomLifecycleState.MATCHING) {
            // 正在匹配: 先清退队列(带字幕反馈), 再卸载维度 + 重置地图(不重新加载维度) —— 与 cleanRoom 相同的地图重置
            evictMatchingPlayers(coreService, room);
            resetDimensionWithoutReload(room);
        } else {
            // 空闲: 维度若被加载则卸载, 让出内存
            releaseDimension(room);
        }
        if (coreService.persistRoomLifecycleState(roomId, RoomLifecycleState.LOCKED)) {
            lockedRoomIds.add(roomId);
            ServerLogger.info("活跃对局已达上限, 房间已锁定: room=%s 原状态=%s", roomId, previous);
        }
    }

    private void evictMatchingPlayers(RoomCoreService coreService, RoomDefinition room) {
        Set<UUID> queued = RoomCoreRuntimeManager.of(server)
            .findMatchingState(room.roomId())
            .map(state -> Set.copyOf(state.getQueuePlayers()))
            .orElse(Set.of());
        for (UUID playerId : queued) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(ServerText.prefixed(
                    null,
                    "房间「" + room.displayName() + "」已被锁定：服务器活跃对局数量已达上限，你已退出匹配并返回大厅",
                    ChatFormatting.GOLD
                ));
            }
            coreService.leaveMatching(playerId);
        }
    }

    private void resetDimensionWithoutReload(RoomDefinition room) {
        try {
            ConquestDataManager.of(server).roomTemplates().resetRoomDimensionFromTemplate(room, false);
        } catch (RuntimeException exception) {
            ServerLogger.failure(
                "锁定房间时重置地图失败: room=%s reason=%s",
                room.roomId(),
                exception.getMessage()
            );
        }
    }

    private void releaseDimension(RoomDefinition room) {
        try {
            RoomDimensionControlService.unloadRoomDimension(server, room);
        } catch (RuntimeException exception) {
            ServerLogger.failure("锁定房间时卸载维度失败: room=%s reason=%s", room.roomId(), exception.getMessage());
        }
    }

    private void unlockAll(RoomCoreService coreService) {
        if (lockedRoomIds.isEmpty()) {
            return;
        }
        applying = true;
        try {
            for (String roomId : Set.copyOf(lockedRoomIds)) {
                lockedRoomIds.remove(roomId);
                RoomDefinition room = coreService.findRoom(roomId).orElse(null);
                if (room == null || room.lifecycleState() != RoomLifecycleState.LOCKED) {
                    continue;
                }
                coreService.persistRoomLifecycleState(roomId, RoomLifecycleState.IDLE);
                ServerLogger.info("活跃名额已释放, 房间已解锁回到空闲: room=%s", roomId);
            }
        } finally {
            applying = false;
        }
    }

    private int countActive(RoomCoreService coreService) {
        int count = 0;
        for (RoomDefinition room : coreService.listRooms()) {
            if (room != null && ACTIVE_STATES.contains(room.lifecycleState())) {
                count++;
            }
        }
        return count;
    }

    private int resolveLimit() {
        try {
            return ConquestDataManager.of(server).serverConfig().getMaxActiveRooms();
        } catch (RuntimeException exception) {
            return 0;
        }
    }
}
