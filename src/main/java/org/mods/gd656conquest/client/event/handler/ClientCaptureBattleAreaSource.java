package org.mods.gd656conquest.client.event.handler;

import java.util.List;

/**
 * GD656Mods: 提供给外部显示层读取的战斗区域边框/蒙版快照入口。
 */
public final class ClientCaptureBattleAreaSource {
    private ClientCaptureBattleAreaSource() {
    }

    public static List<ClientCaptureBattleAreaPolygonSnapshot> snapshotPolygons() {
        return ClientEventHandler.getInstance().battleAreaPolygonSnapshots();
    }

    public static List<ClientCaptureBattleAreaLineSnapshot> snapshotLines() {
        return ClientEventHandler.getInstance().battleAreaLineSnapshots();
    }

    public static ClientCaptureBattleAreaPolygonSnapshot.TeamToken resolveSelfTeam() {
        return ClientEventHandler.getInstance().resolveBattleAreaSelfTeam();
    }
}
