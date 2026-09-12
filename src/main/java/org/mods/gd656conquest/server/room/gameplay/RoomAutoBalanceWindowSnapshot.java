package org.mods.gd656conquest.server.room.gameplay;

import java.util.Objects;

/**
 * 统一描述当前玩法是否允许执行实验性局内自动换队。
 * 所有玩法运行器都必须显式返回该快照，避免新增玩法时遗漏关键平衡窗口规则。
 */
public record RoomAutoBalanceWindowSnapshot(boolean autoSwitchAllowed, String reason) {
    private static final RoomAutoBalanceWindowSnapshot ALLOWED = new RoomAutoBalanceWindowSnapshot(true, "");

    public RoomAutoBalanceWindowSnapshot {
        reason = Objects.requireNonNullElse(reason, "");
    }

    public static RoomAutoBalanceWindowSnapshot allowed() {
        return ALLOWED;
    }

    public static RoomAutoBalanceWindowSnapshot locked(String reason) {
        return new RoomAutoBalanceWindowSnapshot(false, reason);
    }
}
