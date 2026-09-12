package org.mods.gd656conquest.client.event.handler;

/**
 * GD656Mods: 提供给外部显示层读取的小地图战斗区域边框线段快照。
 */
public record ClientCaptureBattleAreaLineSnapshot(
    double ax,
    double az,
    double bx,
    double bz,
    LineKind kind
) {
    public enum LineKind {
        SELF,
        ENEMY,
        NEUTRAL,
        SHARED
    }
}
