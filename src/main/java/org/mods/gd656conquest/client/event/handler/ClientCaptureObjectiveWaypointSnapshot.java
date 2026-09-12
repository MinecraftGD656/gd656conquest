package org.mods.gd656conquest.client.event.handler;

/**
 * GD656Mods: 据点路径点只读快照。
 *
 * <p>该快照专供小地图等联动层读取, 与 HUD 渲染层解耦。
 * 实例仅在 capture payload 变化时重建, 避免联动层每 tick 重复解析字符串。</p>
 */
public record ClientCaptureObjectiveWaypointSnapshot(
    String code,
    double x,
    double y,
    double z,
    String name,
    TeamToken ownerTeam
) {
    /**
     * GD656Mods: 据点所属绝对队伍。
     */
    public enum TeamToken {
        NEUTRAL,
        CAMP_A,
        CAMP_B
    }
}
