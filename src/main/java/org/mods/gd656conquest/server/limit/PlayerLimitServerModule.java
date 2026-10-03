package org.mods.gd656conquest.server.limit;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.shared.log.ServerLogger;

/**
 * GD656Mods: 服务器人数限制模块。
 *
 * <p>每新登录一名玩家检测一次: 若其加入后导致"计入人数"超出上限, 立刻踢出并显示配置的提示。
 * 与 {@code MaintenanceServerModule} 同构 —— 都在 {@code onPlayerLogin} 里用
 * {@code player.connection.disconnect(Component)} 拒绝, 因此不会有"进服后被弹回"的中间态。
 */
public final class PlayerLimitServerModule implements ServerModule {

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerLimitDataStore store = ConquestDataManager.of(player.server).playerLimit();
        if (!store.isEnabled()) {
            return;
        }
        // GD656Mods: PlayerLoggedInEvent 触发时该玩家已进入在线列表, 因此这里统计到的就是"他加入之后"的人数。
        // 管理员不计入统计(A)且自身豁免(B), 两个条件都由 PlayerLimitService 统一表达。
        int countedPlayers = PlayerLimitService.countCountedPlayers(player.server);
        boolean operator = PlayerLimitService.isOperator(player);
        int maxPlayers = store.getMaxPlayers();
        if (!PlayerLimitService.shouldKickNewLogin(true, operator, countedPlayers, maxPlayers)) {
            return;
        }
        String reason = store.getReason();
        ServerLogger.info(
            "人数限制触发: 踢出 %s (计入人数=%d, 上限=%d)",
            player.getScoreboardName(),
            countedPlayers,
            maxPlayers
        );
        player.connection.disconnect(Component.literal(reason));
    }
}
