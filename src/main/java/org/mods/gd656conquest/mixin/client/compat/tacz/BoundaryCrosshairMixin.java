package org.mods.gd656conquest.mixin.client.compat.tacz;

import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import org.mods.gd656conquest.client.room.ClientBoundaryState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TACZ receives canceled overlay events, so vanilla cancellation alone does not hide its reticle. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.RenderCrosshairEvent", remap = false)
public abstract class BoundaryCrosshairMixin {
    @Inject(method = "onRenderOverlay", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gd656conquest$hideBoundaryCrosshair(RenderGuiOverlayEvent.Pre event, CallbackInfo ci) {
        // TACZ receives every overlay's Pre event, not only the crosshair event.
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())
            && ClientBoundaryState.hidePlayerCrosshair()) { event.setCanceled(true); ci.cancel(); }
    }
}
