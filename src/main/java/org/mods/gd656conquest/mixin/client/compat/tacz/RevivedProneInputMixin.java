package org.mods.gd656conquest.mixin.client.compat.tacz;

import net.minecraftforge.client.event.InputEvent;
import org.mods.gd656conquest.client.room.ClientDownedCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.input.CrawlKey", remap = false)
public abstract class RevivedProneInputMixin {
    @Inject(method = "onCrawlPress", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gd656conquest$releaseRevivedProne(InputEvent.Key event, CallbackInfo ci) {
        if (ClientDownedCamera.handleCrawlKey(event)) ci.cancel();
    }
    @Inject(method = "onCrawlControllerPress", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gd656conquest$releaseRevivedProneController(boolean pressed, CallbackInfoReturnable<Boolean> cir) {
        if (ClientDownedCamera.handleCrawlController(pressed)) cir.setReturnValue(true);
    }
}
