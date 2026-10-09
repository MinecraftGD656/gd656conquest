package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.MouseHandler;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$blockOnMoveWhenLocked(long windowPointer, double xpos, double ypos, CallbackInfo callbackInfo) {
        if (!ClientRoomLockState.shouldBlockLookInput()) {
            return;
        }
        callbackInfo.cancel();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$blockTurnWhenLocked(CallbackInfo callbackInfo) {
        if (!ClientRoomLockState.shouldBlockLookInput()) {
            return;
        }
        callbackInfo.cancel();
    }
}
