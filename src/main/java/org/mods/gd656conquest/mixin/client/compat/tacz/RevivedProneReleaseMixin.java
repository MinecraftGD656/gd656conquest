package org.mods.gd656conquest.mixin.client.compat.tacz;

import net.minecraft.client.player.LocalPlayer;
import org.mods.gd656conquest.common.player.RevivedPronePose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerCrawl", remap = false)
public abstract class RevivedProneReleaseMixin {
    @Shadow @Final private LocalPlayer player;
    @Shadow private boolean isCrawling;
    @Shadow private int crawCooldownTicks;

    @Inject(method = "crawl", at = @At("HEAD"), cancellable = true, require = 1)
    private void gd656conquest$clearRevivedCrawl(boolean crawling, CallbackInfo ci) {
        if (crawling || !RevivedPronePose.releasingNativeCrawl(player)) return;
        // Normal crawl(false) can refuse a recent revive or a non-gun held item. The existing
        // GD session release packet authorizes the server; only reset our local prediction here.
        isCrawling = false;
        crawCooldownTicks = 0;
        ci.cancel();
    }
}
