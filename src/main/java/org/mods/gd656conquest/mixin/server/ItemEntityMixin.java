package org.mods.gd656conquest.mixin.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.player.lock.ServerPlayerLockService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 房间禁用背包(disableInventoryChange)时, 服务端阻止玩家拾取掉落物。
 * 拾取由服务端权威判定, 客户端随服务端结果自然跟随。
 * 本模组的弹药包/医疗包掉落物豁免: 拾取即直接使用(补给弹药/回血), 不进入背包。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$blockItemPickupWhenInventoryLocked(Player player, CallbackInfo ci) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return;
        }
        if (!ServerPlayerLockService.of(serverPlayer.getServer()).shouldBlockInventoryOperation(serverPlayer)) {
            return;
        }
        // GD656Mods: 豁免本模组弹药包/医疗包掉落物 —— 拾取后由 PlayerItemInteractionService
        // 直接使用(补给/回血), 不会改变玩家背包, 不违反禁用背包约束。
        ItemStack stack = ((ItemEntity) (Object) this).getItem();
        if (stack != null && !stack.isEmpty()
            && (stack.is(ConquestItems.AMMO_BAG.get()) || stack.is(ConquestItems.MEDIC_BAG.get()))) {
            return;
        }
        ci.cancel();
    }
}
