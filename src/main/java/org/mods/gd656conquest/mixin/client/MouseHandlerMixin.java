package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.common.player.lock.PresentationLockPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$blockCapturedMotionWhenLocked(long windowPointer, double xpos, double ypos, CallbackInfo callbackInfo) {
        Minecraft mc = Minecraft.getInstance();
        // Avoid accumulating camera motion in hard locks, without blocking any UI cursor.
        if (PresentationLockPolicy.blocksPointerMovement(ClientRoomLockState.shouldBlockLookInput(),
            ClientRoomLockState.shouldReleaseMouseForUi(), mc.screen != null, mc.mouseHandler.isMouseGrabbed())) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$blockTurnWhenLocked(CallbackInfo callbackInfo) {
        if (!ClientRoomLockState.shouldBlockLookInput()) {
            return;
        }
        callbackInfo.cancel();
    }
}
