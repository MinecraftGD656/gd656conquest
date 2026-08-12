package org.mods.gd656conquest.server.room.runtime.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 索敌标记(DIRECT_ENEMY)放置成功事件。
 * 由 RoomMarkerRuntimeService.handleMarkerAction 在手动索敌成功时广播,
 * 供外部模组(如 GD656Killicon 标示助攻)监听, 获取"谁标记了谁"。
 */
public final class ConquestMarkerSpotPlacedEvent extends Event {
    private final ServerPlayer spotter;
    private final ServerPlayer targetPlayer;

    public ConquestMarkerSpotPlacedEvent(ServerPlayer spotter, ServerPlayer targetPlayer) {
        this.spotter = spotter;
        this.targetPlayer = targetPlayer;
    }

    /** 触发索敌的玩家 */
    public ServerPlayer getSpotter() {
        return spotter;
    }

    /** 被索敌标记的目标玩家 */
    public ServerPlayer getTargetPlayer() {
        return targetPlayer;
    }
}
