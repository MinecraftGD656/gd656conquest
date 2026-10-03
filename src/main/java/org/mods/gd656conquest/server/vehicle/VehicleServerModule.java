package org.mods.gd656conquest.server.vehicle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class VehicleServerModule implements ServerModule {
    private final VehicleMountedHotbarLockService mountedHotbarLockService = new VehicleMountedHotbarLockService();

    /**
     * GD656Mods: 玩家在载具上阵亡时强制下车。
     *
     * <p>不下车的话, "死亡 → 重生"这段时间里玩家仍是载具乘客, 服务端对载具武器的
     * "是否是本车乘客 + 座位号"校验依然成立, 于是死后还能继续开火。
     * 放在死亡瞬间(而不是重生流程里)是因为窗口就发生在这一段时间。
     */
    @Override
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VehicleCompatFacade.forceDismountForDeath(player);
    }

    @Override
    public void onPlayerTick(ServerPlayer player) {
        mountedHotbarLockService.onPlayerTick(player);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        mountedHotbarLockService.onPlayerLogout(player);
    }

    @Override
    public void onPlayerRespawn(ServerPlayer player) {
        mountedHotbarLockService.onPlayerRespawn(player);
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        mountedHotbarLockService.onPlayerLogin(player);
    }

    @Override
    public void onPlayerChangedDimension(ServerPlayer player) {
        mountedHotbarLockService.onPlayerChangedDimension(player);
    }
}
