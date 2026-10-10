package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import org.mods.gd656conquest.client.room.ClientBoundaryState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class BoundaryWorldRenderMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"), require = 1)
    private void gd656conquest$beginBoundaryFrame(float tick, long nanos, boolean world, CallbackInfo ci) {
        ClientBoundaryState.beginFrame();
    }
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", remap = false, ordinal = 0), require = 1)
    private void gd656conquest$finishBoundaryWorld(float tick, long nanos, boolean world, CallbackInfo ci) {
        ClientBoundaryState.renderWorld(tick);
    }
    @Inject(method = "reloadShaders(Lnet/minecraft/server/packs/resources/ResourceProvider;)V", at = @At("HEAD"), require = 1)
    private void gd656conquest$releaseBoundaryResourcesOnReload(CallbackInfo ci) { ClientBoundaryState.releaseResources(); }
    @Inject(method = "close", remap = false, at = @At("HEAD"), require = 1)
    private void gd656conquest$releaseBoundaryResources(CallbackInfo ci) { ClientBoundaryState.releaseResources(); }
}
