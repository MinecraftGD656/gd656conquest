package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.Minecraft;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Guard actual world actions, leaving raw mouse events available to UI handlers. */
@Mixin(Minecraft.class)
public abstract class PresentationCombatInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void gd656$blockAttack(CallbackInfoReturnable<Boolean> cir) {
        if (ClientRoomLockState.shouldRestrictGameplayInteraction()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void gd656$blockHeldAttack(boolean held, CallbackInfo ci) {
        if (ClientRoomLockState.shouldRestrictGameplayInteraction()) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void gd656$blockUse(CallbackInfo ci) {
        if (ClientRoomLockState.shouldRestrictHeldItemUse()) ci.cancel();
    }

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void gd656$blockPick(CallbackInfo ci) {
        if (ClientRoomLockState.shouldRestrictGameplayInteraction()) ci.cancel();
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void gd656$discardBattleBindings(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !ClientRoomLockState.requiresCombatFreeze()) return;
        for (var key : new net.minecraft.client.KeyMapping[]{mc.options.keyAttack, mc.options.keyUse,
            mc.options.keyPickItem, mc.options.keyDrop, mc.options.keySwapOffhand}) {
            while (key.consumeClick()) {}
            key.setDown(false);
        }
        for (var key : mc.options.keyHotbarSlots) while (key.consumeClick()) {}
    }
}
