package org.mods.gd656conquest.server.integration;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

/**
 * GD656Killicon 荣誉触发桥接(与 KilliconScoreboardBridge 触发加分项完全平行)。
 * <p>
 * 直接编译期调用 Killicon 的荣誉引擎(非反射; GD656Killicon 在 mods.toml 中为 mandatory 依赖)。
 * 每个需要触发荣誉的 Conquest 玩法(救援/索敌/据点等)在此加一个平行方法。
 * </p>
 */
public final class KilliconHonorBridge {

    private KilliconHonorBridge() {
    }

    /**
     * 急救事件(急救使者荣誉): 救援完成时调用, 与加分项 REVIVE 触发点完全平行。
     *
     * @param reviver 完成救援的玩家
     */
    public static void triggerRevive(ServerPlayer reviver) {
        if (reviver == null) {
            return;
        }
        org.mods.gd656killicon.server.ServerCore.HONOR.onRevive(reviver);
    }

    /**
     * 对局结束(爆破专家等对局级荣誉): 房间对局结束时调用, 清空 Killicon 对局级荣誉状态。
     */
    public static void triggerRoundEnd() {
        org.mods.gd656killicon.server.ServerCore.HONOR.onRoundEnd();
    }

    /**
     * 突破/突袭房间单阶段结束(地面控制/突破者): 阶段推进时调用。
     *
     * @param stageKills 本阶段每玩家击杀数(仅统计击杀敌方玩家/载具的击杀者)
     * @param teams      本阶段玩家阵营映射(playerId → "CAMP_A"/"CAMP_B")
     * @param isRush     是否突破(rush)房间: true → 用炸弹事件替代占领分; false → 突袭用占领分
     */
    public static void triggerStageEnd(
        net.minecraft.server.MinecraftServer server,
        java.util.Map<java.util.UUID, Integer> stageKills,
        java.util.Map<java.util.UUID, RoomTeamId> teams,
        boolean isRush
    ) {
        if (server == null || stageKills == null || teams == null) {
            return;
        }
        java.util.Map<java.util.UUID, String> teamNames = new java.util.HashMap<>();
        for (java.util.Map.Entry<java.util.UUID, RoomTeamId> entry : teams.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                teamNames.put(entry.getKey(), entry.getValue().name());
            }
        }
        org.mods.gd656killicon.server.ServerCore.HONOR.onConquestStageEnd(server, stageKills, teamNames, isRush);
    }
}
