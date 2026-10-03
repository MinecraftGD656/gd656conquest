package org.mods.gd656conquest.common.overlay;

/**
 * GD656Mods: debug overlay 行表同步协议的常量单点定义(服务端与客户端共用)。
 *
 * <p>为什么需要这个类: 旧实现用"整张点表单包字符串"传输, 撞上 {@code FriendlyByteBuf.writeUtf}
 * 的 32767 上限后被静默截断, 点一多就传不到客户端。新协议把传输拆成
 * "基线分片 + 增量 diff", 两端必须对模式标记与分片上限有一致认知, 因此集中在此。
 */
public final class AdminDebugOverlayProtocol {
    /** GD656Mods: 基线分片。 */
    public static final String MODE_BASELINE_CHUNK = "CHUNK";
    /** GD656Mods: 增量(条目新增/变更 + 键删除)。 */
    public static final String MODE_DELTA = "DELTA";
    /** GD656Mods: 清空(关闭调试覆盖层 / 当前维度无可显示房间)。 */
    public static final String MODE_CLEAR = "CLEAR";
    /** GD656Mods: 作废标记, 客户端收到后丢弃本轮基线而不是显示半张表。 */
    public static final int INVALID_BATCH_COUNT = -1;
    /** GD656Mods: 单字段字符上限, 与 FriendlyByteBuf.writeUtf 的默认上限保持一致。 */
    public static final int MAX_PAYLOAD_CHARS = 32767;
    /**
     * GD656Mods: 单片上限(UTF-8 字节)。留出充足余量以避免触碰字符上限 ——
     * 旧实现正是撞上该上限后静默截断丢点。超出该阈值时增量自动降级为重新发基线。
     */
    public static final int DEFAULT_CHUNK_BYTES = 24000;

    private AdminDebugOverlayProtocol() {
    }
}
