package org.mods.gd656conquest.mixin.compat.explosionoverhaul;

import com.vinlanx.explosionoverhaul.ExplosionOverhaul;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 关闭 Explosion-Overhaul 模组在洞穴上方生成的粉尘粒子与落石音效。
 *
 * <p>原逻辑: <code>com.vinlanx.explosionoverhaul.ExplosionOverhaul.CaveEffects#spawnFallingBlocksAndDust</code>
 * 会在玩家处于自然洞穴环境且真实爆炸发生时,在玩家头顶 10 格范围内通过
 * <code>addDelayedParticle</code> 持续投放方块粉尘,有时还会触发 DUST_SOUND /
 * FALLING_STONES_SOUND。该模组未提供对应的服务端配置开关。</p>
 *
 * <p>此 mixin 通过 HEAD + cancel 拦截整段调用,使其在真实爆炸时不再生成洞穴粉尘。</p>
 */
@Mixin(value = ExplosionOverhaul.CaveEffects.class, remap = false)
public abstract class ExplosionOverhaulCaveDustDisableMixin {

    @Inject(
        method = "spawnFallingBlocksAndDust",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void gd656conquest$disableCaveFallingDust(
        ServerLevel level,
        Vec3 explosionPos,
        ServerPlayer player,
        float power,
        long initialDelayTicks,
        CallbackInfo ci
    ) {
        ci.cancel();
    }
}
