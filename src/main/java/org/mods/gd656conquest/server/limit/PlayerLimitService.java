package org.mods.gd656conquest.server.limit;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * GD656Mods: 人数上限的判定逻辑(纯逻辑, 便于单测)。
 *
 * <p>口径: <strong>管理员(权限等级 ≥2)既不计入总人数, 自身也不受人数限制</strong>。
 * 统计与判定必须成对使用 —— 只排除统计不豁免判定会把管理员踢掉,
 * 只豁免判定不排除统计会让管理员占用名额、把正常玩家挤掉。
 */
public final class PlayerLimitService {
    /** GD656Mods: 视为"管理员"的权限等级, 与维护模式等既有逻辑保持一致。 */
    public static final int OPERATOR_PERMISSION_LEVEL = 2;

    private PlayerLimitService() {
    }

    public static boolean isOperator(ServerPlayer player) {
        return player != null && player.hasPermissions(OPERATOR_PERMISSION_LEVEL);
    }

    /** GD656Mods: 统计"计入人数上限"的在线玩家数 —— 排除管理员。 */
    public static int countCountedPlayers(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && !isOperator(player)) {
                count++;
            }
        }
        return count;
    }

    /**
     * GD656Mods: 新登录的玩家是否应因超出人数上限被踢出。
     *
     * @param enabled       人数限制是否开启
     * @param isOperator    新登录玩家是否为管理员(不计入也不受限)
     * @param countedPlayers 该玩家加入之后的"计入人数"总数
     * @param maxPlayers    人数上限
     */
    public static boolean shouldKickNewLogin(boolean enabled, boolean isOperator, int countedPlayers, int maxPlayers) {
        if (!enabled) {
            return false;
        }
        if (isOperator) {
            return false;
        }
        return countedPlayers > maxPlayers;
    }
}
