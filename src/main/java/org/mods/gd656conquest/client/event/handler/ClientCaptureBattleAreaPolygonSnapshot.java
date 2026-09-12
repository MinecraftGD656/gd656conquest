package org.mods.gd656conquest.client.event.handler;

import java.util.List;

/**
 * GD656Mods: 提供给外部显示层读取的战斗区域多边形快照。
 */
public record ClientCaptureBattleAreaPolygonSnapshot(
    String polygonId,
    TeamToken team,
    List<Point> points
) {
    public enum TeamToken {
        NEUTRAL,
        CAMP_A,
        CAMP_B
    }

    public record Point(double x, double z) {
    }
}
