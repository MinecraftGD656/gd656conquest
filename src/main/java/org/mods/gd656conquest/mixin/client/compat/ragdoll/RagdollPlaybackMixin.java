package org.mods.gd656conquest.mixin.client.compat.ragdoll;

import org.mods.gd656conquest.client.room.RagdollPlaybackGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.client.RenderPlayback", remap = false)
public abstract class RagdollPlaybackMixin {
    @Inject(method = "advance", at = @At("HEAD"), cancellable = true, require = 1)
    private void gd656conquest$holdCompletedSegment(long nowNanos, CallbackInfoReturnable<Object> cir) {
        Object current = RagdollPlaybackGuard.holdCompleted(this, nowNanos);
        if (current != null) cir.setReturnValue(current);
    }
}
