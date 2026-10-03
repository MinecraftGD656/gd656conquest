package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.common.overlay.AdminDebugOverlayProtocol;
import org.mods.gd656conquest.common.overlay.AdminDebugOverlayRowTable;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.AdminDebugOverlaySyncPacket;
import org.mods.gd656conquest.server.room.core.RoomCoreService;
import org.mods.gd656conquest.server.shared.log.ServerLogger;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * GD656Mods: debug overlay 行表同步服务(每玩家会话化)。
 *
 * <p>旧实现每 20 tick 对每个开启调试的玩家全量重建 payload 并单包发送, payload 超过
 * writeUtf 上限(32767)时被静默截断, 点一多就"传不到客户端"。现在:
 * <ul>
 *     <li>会话建立(开启调试 / 重连 / 服务端曾丢失客户端状态)时按
 *         {@link AdminDebugOverlayProtocol#DEFAULT_CHUNK_BYTES} 分片发基线, 分片可完整承载任意点数;</li>
 *     <li>之后每 20 tick 只把行表 diff(新增/变更/删除)发给客户端;</li>
 *     <li>diff 过大时(换房间维度、关卡阶段切换、房间配置重载)自动降级为重新发基线,
 *         避免增量包自身撞上长度上限;</li>
 *     <li>未开启调试的玩家不再每 20 tick 收空包, 只在关闭时收到一次清空。</li>
 * </ul>
 *
 * <p>玩家重连后会拿到新的 {@code ServerPlayer} 实例, 本服务据此重建会话并重发基线,
 * 因此客户端不会因为"服务端以为已经同步过"而长期空显示。
 */
public final class RoomAdminDebugOverlaySyncService {
    private final MinecraftServer server;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private int nextSessionId = 1;

    public RoomAdminDebugOverlaySyncService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /** GD656Mods: 每个同步周期(20 tick)调用一次。 */
    public void tick(RoomCoreService coreService, Set<UUID> enabledPlayers) {
        if (coreService == null) {
            return;
        }
        Set<UUID> onlinePlayerIds = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            onlinePlayerIds.add(playerId);
            if (enabledPlayers != null && enabledPlayers.contains(playerId)) {
                syncPlayer(coreService, player);
                continue;
            }
            releaseSession(playerId);
        }
        sessions.keySet().retainAll(onlinePlayerIds);
    }

    /** GD656Mods: 玩家开启调试覆盖层: 立即建立会话并发送基线, 不再等下一个同步周期。 */
    public void beginForPlayer(ServerPlayer player, RoomCoreService coreService) {
        if (player == null || coreService == null) {
            return;
        }
        Session session = new Session(player, nextSessionId++);
        sessions.put(player.getUUID(), session);
        sendBaseline(coreService, session);
    }

    /** GD656Mods: 玩家关闭调试覆盖层: 发送一次清空并销毁会话。 */
    public void endForPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        releaseSession(player.getUUID());
    }

    /** GD656Mods: 服务器停止/玩家数据清理时丢弃全部会话状态。 */
    public void reset() {
        sessions.clear();
    }

    private void syncPlayer(RoomCoreService coreService, ServerPlayer player) {
        UUID playerId = player.getUUID();
        Session session = sessions.get(playerId);
        if (session == null || session.player != player) {
            // GD656Mods: 新会话 —— 首次开启调试, 或玩家重连导致 ServerPlayer 实例更换。
            session = new Session(player, nextSessionId++);
            sessions.put(playerId, session);
            sendBaseline(coreService, session);
            return;
        }
        Map<String, String> rows = resolveRows(coreService, player);
        if (session.needsBaseline) {
            // GD656Mods: 上一轮基线因异常被作废, 本轮重发, 直到客户端拿到完整点集。
            sendBaseline(coreService, session);
            return;
        }
        AdminDebugOverlayRowTable.Diff diff = AdminDebugOverlayRowTable.diff(session.sentRows, rows);
        if (diff.isEmpty()) {
            return;
        }
        if (diff.byteSize() > AdminDebugOverlayProtocol.DEFAULT_CHUNK_BYTES) {
            // GD656Mods: 变化量本身就超过单包承载能力(换维度、阶段切换、配置重载),
            // 降级为重新发基线 —— 基线是分片的, 能完整承载任意点数。
            ServerLogger.info(
                "debug overlay 增量过大, 降级为重新发基线: player=%s upserts=%d removed=%d bytes=%d",
                player.getName().getString(),
                diff.upserts().size(),
                diff.removed().size(),
                diff.byteSize()
            );
            sendBaseline(coreService, session);
            return;
        }
        session.sentRows = new LinkedHashMap<>(rows);
        NetworkManager.sendToPlayer(player, AdminDebugOverlaySyncPacket.delta(
            session.sessionId,
            AdminDebugOverlayRowTable.encodeEntries(diff.upserts()),
            String.join(String.valueOf(AdminDebugOverlayRowTable.ENTRY_SEPARATOR), diff.removed())
        ));
    }

    private void sendBaseline(RoomCoreService coreService, Session session) {
        Map<String, String> rows = resolveRows(coreService, session.player);
        List<String> chunks = AdminDebugOverlayRowTable.chunk(
            AdminDebugOverlayRowTable.encodeEntries(rows),
            AdminDebugOverlayProtocol.DEFAULT_CHUNK_BYTES
        );
        session.sentRows = new LinkedHashMap<>(rows);
        session.needsBaseline = false;
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            if (chunk.length() > AdminDebugOverlayProtocol.MAX_PAYLOAD_CHARS) {
                // GD656Mods: 理论上不可达(单条点文本 < 200 字节); 真发生说明分片逻辑被破坏,
                // 记录并让客户端作废本轮, 不允许静默显示半张点表。
                ServerLogger.failure(
                    "debug overlay 基线分片超长, 已作废本轮: player=%s index=%d chars=%d",
                    session.player.getName().getString(),
                    index,
                    chunk.length()
                );
                session.needsBaseline = true;
                NetworkManager.sendToPlayer(session.player, AdminDebugOverlaySyncPacket.baselineChunk(
                    session.sessionId,
                    index,
                    AdminDebugOverlayProtocol.INVALID_BATCH_COUNT,
                    ""
                ));
                return;
            }
            NetworkManager.sendToPlayer(session.player, AdminDebugOverlaySyncPacket.baselineChunk(
                session.sessionId,
                index,
                chunks.size(),
                chunk
            ));
        }
    }

    private Map<String, String> resolveRows(RoomCoreService coreService, ServerPlayer player) {
        return coreService.buildAdminDebugOverlayRowTable(player).orElseGet(LinkedHashMap::new);
    }

    private void releaseSession(UUID playerId) {
        Session session = sessions.remove(playerId);
        if (session == null) {
            return;
        }
        // GD656Mods: 玩家可能已离线, 此时不再发包(避免对已断开的连接写入)。
        if (server.getPlayerList().getPlayer(playerId) == null) {
            return;
        }
        NetworkManager.sendToPlayer(session.player, AdminDebugOverlaySyncPacket.clear(session.sessionId));
    }

    private static final class Session {
        private final ServerPlayer player;
        private final int sessionId;
        private Map<String, String> sentRows = new LinkedHashMap<>();
        private boolean needsBaseline = true;

        private Session(ServerPlayer player, int sessionId) {
            this.player = player;
            this.sessionId = sessionId;
        }
    }
}
