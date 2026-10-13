package org.mods.gd656conquest.mixin.client;

import net.minecraft.world.entity.player.Inventory;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class PresentationHotbarScrollMixin {
    @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
    private void gd656$blockHotbarChange(double delta, CallbackInfo ci) {
        if (ClientRoomLockState.shouldLockHotbarSelection()) ci.cancel();
    }
}
