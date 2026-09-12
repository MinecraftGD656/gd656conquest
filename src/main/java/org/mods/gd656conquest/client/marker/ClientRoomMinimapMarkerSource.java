package org.mods.gd656conquest.client.marker;

import java.util.List;

/**
 * GD656Mods: 向小地图兼容层提供房间标记快照。
 */
public final class ClientRoomMinimapMarkerSource {
    private ClientRoomMinimapMarkerSource() {
    }

    /**
     * 返回当前客户端房间标记的只读快照。
     */
    public static List<ClientRoomMinimapMarkerSnapshot> snapshotMarkers() {
        return ClientRoomMarkerState.snapshotMinimapMarkers();
    }
}
