package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$disableVanillaMusicTick(CallbackInfo callbackInfo) {
        MusicManager manager = (MusicManager) (Object) this;
        manager.stopPlaying();
        callbackInfo.cancel();
    }
}
