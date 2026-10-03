package org.mods.gd656conquest.client.marker;

import java.util.UUID;

/**
 * GD656Mods: 提供给小地图联动层读取的房间标记快照。
 *
 * <p>该快照只保留 xaero 需要的最小信息:
 * 稳定键、图标类型、坐标、剩余时长以及可选的跟随目标玩家 UUID。</p>
 */
public record ClientRoomMinimapMarkerSnapshot(
    String stableKey,
    MarkerIconKind iconKind,
    double x,
    double y,
    double z,
    int remainingTicks,
    UUID followPlayerId,
    String label
) {
    public enum MarkerIconKind {
        ALLY,
        ALLY_TEAM,
        ALLY_SQUAD,
        MAP_MARKER,
        OBSERVE_GROUND,
        REQUEST_STRIKE,
        ENEMY_GROUND,
        ENEMY_TRACKED,
        SUPPLY_BOX,
        SUPPLY_BAG,
        RESPAWN_BEACON
    }
}
