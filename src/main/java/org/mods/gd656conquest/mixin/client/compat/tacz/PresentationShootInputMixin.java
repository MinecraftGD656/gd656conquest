package org.mods.gd656conquest.mixin.client.compat.tacz;

import net.minecraft.client.Minecraft;
import org.mods.gd656conquest.client.compat.tacz.TaczClientInputCompat;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block gun handling rather than the raw button event that UI also consumes. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.input.ShootKey", remap = false)
public abstract class PresentationShootInputMixin {
    @Inject(method = {"autoShoot", "semiShoot"}, at = @At("HEAD"), cancellable = true, require = 1)
    private static void gd656$blockGunInput(CallbackInfo ci) {
        if (ClientRoomLockState.shouldRestrictHeldItemUse()) {
            TaczClientInputCompat.clearTransientCombatState(Minecraft.getInstance());
            ci.cancel();
        }
    }

    @Inject(method = {"autoShootController", "semiShootController"}, at = @At("HEAD"), cancellable = true, require = 1)
    private static void gd656$blockControllerGunInput(CallbackInfoReturnable<Boolean> cir) {
        if (ClientRoomLockState.shouldRestrictHeldItemUse()) cir.setReturnValue(false);
    }
}
