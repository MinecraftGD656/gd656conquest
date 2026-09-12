package org.mods.gd656conquest.client.event.handler;

import java.util.List;

/**
 * GD656Mods: 提供给外部显示层读取的据点路径点快照入口。
 */
public final class ClientCaptureObjectiveWaypointSource {
    private ClientCaptureObjectiveWaypointSource() {
    }

    /**
     * 返回当前缓存的全部据点路径点快照。
     *
     * <p>列表在 payload 未变化时会被复用, 外部显示层可按 tick 读取后自行做增量同步。</p>
     */
    public static List<ClientCaptureObjectiveWaypointSnapshot> snapshotObjectives() {
        return ClientEventHandler.getInstance().objectiveWaypointSnapshots();
    }

    /**
     * 返回当前客户端视角下的自身队伍。
     */
    public static ClientCaptureObjectiveWaypointSnapshot.TeamToken resolveSelfTeam() {
        return ClientEventHandler.getInstance().resolveObjectiveWaypointSelfTeam();
    }
}
