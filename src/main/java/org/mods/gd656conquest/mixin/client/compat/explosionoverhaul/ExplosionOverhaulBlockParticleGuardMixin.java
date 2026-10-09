package org.mods.gd656conquest.mixin.client.compat.explosionoverhaul;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端渲染层保险:在 <code>ClientLevel.addParticle</code> 入口处过滤掉
 * Explosion-Overhaul 模组在玩家头顶生成的洞穴粉尘粒子。
 */
@Mixin(ClientLevel.class) // 不能用 remap=false，因为 ClientLevel 是原版类，需要混淆映射
public abstract class ExplosionOverhaulBlockParticleGuardMixin {

    private static final double XZ_RADIUS = 16.0;     // 玩家附近
    private static final double Y_MIN_OFFSET = 3.5;  // 玩家头顶(脚下+3.5格)
    private static final double Y_MAX_OFFSET = 12.0; // 玩家头顶 12 格
    private static final double SPEED_LIMIT = 0.2;   // 速度阈值

    // 拦截 7 参数的 addParticle (客户端本地触发等调用)
    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$filterBlockDustParticle7(
        ParticleOptions options,
        double x,
        double y,
        double z,
        double xSpeed,
        double ySpeed,
        double zSpeed,
        CallbackInfo ci
    ) {
        if (shouldCancelCaveDust(options, x, y, z, xSpeed, ySpeed, zSpeed)) {
            ci.cancel();
        }
    }

    // 拦截 8 参数的 addParticle (网络包 ClientboundLevelParticlesPacket 调用)
    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$filterBlockDustParticle8(
        ParticleOptions options,
        boolean force,
        double x,
        double y,
        double z,
        double xSpeed,
        double ySpeed,
        double zSpeed,
        CallbackInfo ci
    ) {
        if (shouldCancelCaveDust(options, x, y, z, xSpeed, ySpeed, zSpeed)) {
            ci.cancel();
        }
    }

    private boolean shouldCancelCaveDust(ParticleOptions options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        // 只关心方块粉尘粒子
        if (!(options instanceof BlockParticleOption)) {
            return false;
        }
        
        // 洞穴粉尘的两种速度特征：
        // 1. 本地生成: xSpeed=0, ySpeed=-0.15, zSpeed=0
        // 2. 服务端数据包生成: 经过 Gaussian 随机后，速度极小(基于原版的 speed=0.02 缩放)
        if (Math.abs(xSpeed) > SPEED_LIMIT || Math.abs(ySpeed) > SPEED_LIMIT || Math.abs(zSpeed) > SPEED_LIMIT) {
            return false;
        }
        
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();
        
        // 空间特征匹配
        if (Math.abs(x - playerX) > XZ_RADIUS || Math.abs(z - playerZ) > XZ_RADIUS) {
            return false;
        }
        if (y < playerY + Y_MIN_OFFSET || y > playerY + Y_MAX_OFFSET) {
            return false;
        }
        return true;
    }
}
