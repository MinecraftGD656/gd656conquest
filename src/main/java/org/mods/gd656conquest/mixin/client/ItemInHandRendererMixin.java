package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStack gd656conquest$renderEmptyHandDuringDeathFlow(ItemStack stack) {
        if (!ClientRoomLockState.shouldHideHeldItems()) {
            return stack;
        }
        return ItemStack.EMPTY;
    }
}
