package org.mods.gd656conquest.mixin.client.compat.explosionoverhaul;

import com.vinlanx.explosionoverhaul.client.ClientEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 关闭 Explosion-Overhaul 模组环境音系统触发的洞穴粉尘效果。
 *
 * <p>原逻辑: <code>com.vinlanx.explosionoverhaul.client.ClientEffects#triggerAmbientCaveDust(float power)</code>
 * 会在收到 <code>SpawnAmbientCaveDustPacket</code> 后,从玩家头顶 4~10 格高度生成
 * 大量方块粒子(15 ~ 70 颗,power 越大越多)。</p>
 */
@Mixin(value = ClientEffects.class, remap = false)
public abstract class ExplosionOverhaulAmbientCaveDustDisableMixin {

    @Inject(
        method = "triggerAmbientCaveDust",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void gd656conquest$disableAmbientCaveDust(float power, CallbackInfo ci) {
        ci.cancel();
    }
}
