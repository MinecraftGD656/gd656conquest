package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.hud.ClientAdminDebugOverlayStore;
import org.mods.gd656conquest.common.overlay.AdminDebugOverlayProtocol;

import java.util.function.Supplier;

/**
 * GD656Mods: debug overlay 行表同步包。
 *
 * <p>取代旧的 {@code AdminDebugOverlayPacket}(单包全量字符串, 超长即静默截断丢点)。
 * 三种模式见 {@link AdminDebugOverlayProtocol}:
 * 基线分片 / 增量 diff / 清空。
 *
 * <p>包体永不因超长而静默丢数据: 若负载异常超限, 本包会把 batchCount 置为
 * {@link AdminDebugOverlayProtocol#INVALID_BATCH_COUNT} 作废该轮基线,
 * 客户端据此丢弃本轮(宁可不显示, 也不显示半张表)。
 */
public final class AdminDebugOverlaySyncPacket {
    private final String mode;
    private final int sessionId;
    private final int batchIndex;
    private final int batchCount;
    private final String payload;
    private final String removedKeys;

    public AdminDebugOverlaySyncPacket(String mode, int sessionId, int batchIndex, int batchCount, String payload, String removedKeys) {
        this.mode = mode == null ? AdminDebugOverlayProtocol.MODE_CLEAR : mode;
        this.sessionId = sessionId;
        this.batchIndex = batchIndex;
        this.batchCount = batchCount;
        this.payload = payload == null ? "" : payload;
        this.removedKeys = removedKeys == null ? "" : removedKeys;
    }

    public static AdminDebugOverlaySyncPacket baselineChunk(int sessionId, int batchIndex, int batchCount, String chunkPayload) {
        return new AdminDebugOverlaySyncPacket(
            AdminDebugOverlayProtocol.MODE_BASELINE_CHUNK,
            sessionId,
            batchIndex,
            batchCount,
            chunkPayload,
            ""
        );
    }

    public static AdminDebugOverlaySyncPacket delta(int sessionId, String upsertEntries, String removedKeys) {
        return new AdminDebugOverlaySyncPacket(AdminDebugOverlayProtocol.MODE_DELTA, sessionId, 0, 1, upsertEntries, removedKeys);
    }

    public static AdminDebugOverlaySyncPacket clear(int sessionId) {
        return new AdminDebugOverlaySyncPacket(AdminDebugOverlayProtocol.MODE_CLEAR, sessionId, 0, 1, "", "");
    }

    /**
     * GD656Mods: 包内可见的只读访问器。仅供同包单元测试校验"编码 -> 解码"的字段对称性,
     * 运行时消费方一律通过 {@code handle} 的入参读取, 因此不对外暴露 getter。
     */
    String mode() {
        return mode;
    }

    int sessionId() {
        return sessionId;
    }

    int batchIndex() {
        return batchIndex;
    }

    int batchCount() {
        return batchCount;
    }

    String payload() {
        return payload;
    }

    String removedKeys() {
        return removedKeys;
    }

    public static void encode(AdminDebugOverlaySyncPacket packet, FriendlyByteBuf buffer) {
        String safePayload = packet.payload;
        String safeRemovedKeys = packet.removedKeys;
        int safeBatchCount = packet.batchCount;
        if (safePayload.length() > AdminDebugOverlayProtocol.MAX_PAYLOAD_CHARS
            || safeRemovedKeys.length() > AdminDebugOverlayProtocol.MAX_PAYLOAD_CHARS) {
            safePayload = "";
            safeRemovedKeys = "";
            safeBatchCount = AdminDebugOverlayProtocol.INVALID_BATCH_COUNT;
        }
        buffer.writeUtf(packet.mode);
        buffer.writeInt(packet.sessionId);
        buffer.writeInt(packet.batchIndex);
        buffer.writeInt(safeBatchCount);
        buffer.writeUtf(safePayload);
        buffer.writeUtf(safeRemovedKeys);
    }

    public static AdminDebugOverlaySyncPacket decode(FriendlyByteBuf buffer) {
        return new AdminDebugOverlaySyncPacket(
            buffer.readUtf(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readUtf(),
            buffer.readUtf()
        );
    }

    public static void handle(AdminDebugOverlaySyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAdminDebugOverlayStore.handle(
            packet.mode,
            packet.sessionId,
            packet.batchIndex,
            packet.batchCount,
            packet.payload,
            packet.removedKeys
        )));
        context.setPacketHandled(true);
    }
}
