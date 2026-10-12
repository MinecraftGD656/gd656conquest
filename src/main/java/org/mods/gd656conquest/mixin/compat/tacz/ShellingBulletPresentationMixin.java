package org.mods.gd656conquest.mixin.compat.tacz;

import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.compat.tacz.ShellingPresentationProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public abstract class ShellingBulletPresentationMixin {
    @Shadow private float damageModifier;
    @Shadow private float knockback;
    @Shadow private float explosionDamage;
    @Shadow private boolean explosionKnockback;
    @Shadow private boolean explosionDestroyBlock;
    @Shadow private boolean igniteEntity;
    @Shadow private boolean igniteBlock;

    // TACZ resolves both impact explosions and its delayed explosion timer inside this method.
    @Inject(method = "onBulletTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void gd656conquest$visualShells(CallbackInfo ci) {
        Entity shell = (Entity)(Object)this;
        if (shell.level().isClientSide()) return;
        if (ShellingPresentationProtection.expired(shell)) {
            shell.discard();
            ci.cancel();
            return;
        }
        if (ShellingPresentationProtection.harmless(shell)) {
            damageModifier = 0;
            knockback = 0;
            explosionDamage = 0;
            explosionKnockback = false;
            explosionDestroyBlock = false;
            igniteEntity = false;
            igniteBlock = false;
        }
    }
}
