package org.mods.gd656conquest.client.hud;

import org.mods.gd656conquest.common.overlay.AdminDebugOverlayProtocol;
import org.mods.gd656conquest.common.overlay.AdminDebugOverlayRowTable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GD656Mods: 客户端 debug overlay 行表。
 *
 * <p>服务端不再每 20 tick 全量重发一整张点表(超长会被截断丢点), 而是:
 * <ol>
 *     <li>会话建立(玩家开启调试 / 重连 / 换维度 / 房间配置变化过大)时按分片发基线;</li>
 *     <li>之后每次只发增量(条目新增/变更 + 键删除)。</li>
 * </ol>
 * 本类把行表还原成与旧协议<strong>完全相同</strong>的 payload 字符串写回
 * {@link ClientHudDebugState}, 因此渲染/交互侧(解析器、轮廓、投影、部署 UI)无需任何改动,
 * 并且"字符串未变即跳过重解析"的既有优化继续有效。
 *
 * <p>边界多边形等"一键多行"的点位: 键对应一整个多边形, 值内部用 {@code ;}
 * 保持顶点顺序, 因此增删改以多边形为单位, 顶点数变化也能正确表达。
 */
public final class ClientAdminDebugOverlayStore {
    private static Map<String, String> rows = new LinkedHashMap<>();
    private static int sessionId = Integer.MIN_VALUE;
    private static int pendingBatchCount;
    private static final Map<Integer, String> pendingChunks = new HashMap<>();
    private static String publishedPayload = AdminDebugOverlayRowTable.EMPTY_PAYLOAD;

    private ClientAdminDebugOverlayStore() {
    }

    public static void handle(String mode, int incomingSessionId, int batchIndex, int batchCount, String payload, String removedKeys) {
        if (AdminDebugOverlayProtocol.MODE_CLEAR.equals(mode)) {
            // GD656Mods: 只清空点表, 保留 sessionId —— 服务端可能在"空点表"后继续用同一会话
            // 发增量(玩家重新进入房间维度), 此时若把 sessionId 也清掉, 增量会因会话不匹配被丢弃。
            rows = new LinkedHashMap<>();
            clearPending();
            publish();
            return;
        }
        if (AdminDebugOverlayProtocol.MODE_BASELINE_CHUNK.equals(mode)) {
            handleBaselineChunk(incomingSessionId, batchIndex, batchCount, payload);
            return;
        }
        if (AdminDebugOverlayProtocol.MODE_DELTA.equals(mode)) {
            handleDelta(incomingSessionId, payload, removedKeys);
        }
    }

    /** GD656Mods: 断线/退出房间时清空, 避免把上一局的行表带进新会话。 */
    public static void reset() {
        clearState();
        publishedPayload = AdminDebugOverlayRowTable.EMPTY_PAYLOAD;
    }

    /** GD656Mods: 当前行表只读副本(保序), 供单测与诊断使用。 */
    public static Map<String, String> currentRows() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(rows));
    }

    /** GD656Mods: 当前已发布的 payload 字符串。 */
    public static String currentPayload() {
        return publishedPayload;
    }

    private static void handleBaselineChunk(int incomingSessionId, int batchIndex, int batchCount, String payload) {
        if (batchCount <= 0) {
            // GD656Mods: 服务端标记本轮基线作废(负载异常超限), 丢弃而不显示半张表。
            clearPending();
            return;
        }
        if (incomingSessionId != sessionId) {
            // GD656Mods: 新会话(开启调试/重连/服务端重建会话): 本地旧表作废, 从本轮基线重建。
            sessionId = incomingSessionId;
            rows = new LinkedHashMap<>();
            clearPending();
        }
        if (pendingBatchCount != batchCount) {
            clearPending();
            pendingBatchCount = batchCount;
        }
        pendingChunks.put(batchIndex, payload == null ? "" : payload);
        if (pendingChunks.size() < pendingBatchCount) {
            return;
        }
        StringBuilder merged = new StringBuilder();
        for (int index = 0; index < pendingBatchCount; index++) {
            String chunk = pendingChunks.get(index);
            if (chunk == null) {
                // GD656Mods: 缺片说明本轮基线不完整, 丢弃并等待下一次基线自愈。
                clearPending();
                return;
            }
            if (merged.length() > 0 && !chunk.isEmpty()) {
                merged.append(AdminDebugOverlayRowTable.ENTRY_SEPARATOR);
            }
            merged.append(chunk);
        }
        Map<String, String> decoded = AdminDebugOverlayRowTable.decodeEntries(merged.toString());
        clearPending();
        if (decoded.isEmpty()) {
            // GD656Mods: 空基线等价于"当前维度无点可显示", 保持与旧协议一致。
            rows = new LinkedHashMap<>();
            publish();
            return;
        }
        rows = new LinkedHashMap<>(decoded);
        publish();
    }

    private static void handleDelta(int incomingSessionId, String upsertEntries, String removedKeys) {
        if (incomingSessionId != sessionId) {
            // GD656Mods: 本地尚无该会话基线, 增量无从应用(等基线, 由服务端下一轮基线自愈)。
            return;
        }
        if (removedKeys != null && !removedKeys.isEmpty()) {
            for (String key : removedKeys.split(String.valueOf(AdminDebugOverlayRowTable.ENTRY_SEPARATOR))) {
                if (!key.isEmpty()) {
                    rows.remove(key);
                }
            }
        }
        Map<String, String> upserts = AdminDebugOverlayRowTable.decodeEntries(upsertEntries);
        if (!upserts.isEmpty()) {
            // GD656Mods: 已存在的键保持原位置(LinkedHashMap 语义), 新键追加, 顺序稳定。
            rows.putAll(upserts);
        }
        publish();
    }

    private static void publish() {
        String payload = AdminDebugOverlayRowTable.toPayload(rows);
        if (payload.equals(publishedPayload)) {
            return;
        }
        publishedPayload = payload;
        ClientHudDebugState.setAdminDebugOverlay(payload);
    }

    private static void clearPending() {
        pendingChunks.clear();
        pendingBatchCount = 0;
    }

    private static void clearState() {
        rows = new LinkedHashMap<>();
        sessionId = Integer.MIN_VALUE;
        clearPending();
    }
}
