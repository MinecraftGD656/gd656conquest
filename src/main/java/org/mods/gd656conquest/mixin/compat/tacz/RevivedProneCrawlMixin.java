package org.mods.gd656conquest.mixin.compat.tacz;

import org.mods.gd656conquest.common.player.RevivedPronePose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"com.tacz.guns.entity.shooter.LivingEntityCrawl", "com.tacz.guns.client.gameplay.LocalPlayerCrawl"}, remap = false)
public abstract class RevivedProneCrawlMixin {
    @Inject(method = "setCrawlPose", at = @At("HEAD"), cancellable = true, require = 1)
    private void gd656conquest$preserveRevivedProne(CallbackInfo ci) {
        if (RevivedPronePose.preserveNativePose(this)) ci.cancel();
    }
}
