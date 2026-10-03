package org.mods.gd656conquest.server.room.model;

public enum RoomLifecycleState {
    DISABLED,
    MAINTENANCE,
    IDLE,
    MATCHING,
    RUNNING,
    ENDING,
    SAVING,
    CLEANING,
    /**
     * GD656Mods: 活跃对局数已达上限时的临时锁定 —— 玩家无法进入、维度保持卸载。
     * 它由 {@code RoomActiveLimitService} 维护(内存账本), 活跃数下降并出现空位后自动回到 IDLE。
     * 注意: 这是"派生状态", 持久化值在服务器启动时会被重算, 不会跨重启残留。
     */
    LOCKED
}