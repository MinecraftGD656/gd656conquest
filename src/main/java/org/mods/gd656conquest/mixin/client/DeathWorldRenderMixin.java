package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.mods.gd656conquest.client.room.ClientDeathFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World/UI boundary hooks use official names and the generated production refmap. */
@Mixin(value = GameRenderer.class)
public abstract class DeathWorldRenderMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"), require = 1)
    private void gd656conquest$beginDeathFrame(float partialTick, long nanos, boolean renderWorld, CallbackInfo ci) {
        ClientDeathFilter.beginFrame();
    }
    @Redirect(method = "render(FJZ)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V"), require = 1)
    private void gd656conquest$deferDeathOutline(LevelRenderer renderer) {
        ClientDeathFilter.compositeOutlines(renderer);
    }
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", remap = false, ordinal = 0), require = 1)
    private void gd656conquest$finishDeathWorld(float partialTick, long nanos, boolean renderWorld, CallbackInfo ci) {
        ClientDeathFilter.finishWorld(partialTick);
    }
    @Inject(method = "reloadShaders(Lnet/minecraft/server/packs/resources/ResourceProvider;)V", at = @At("HEAD"), require = 1)
    private void gd656conquest$releaseDeathResourcesOnReload(CallbackInfo ci) {
        ClientDeathFilter.releaseResources();
    }
    @Inject(method = "close", remap = false, at = @At("HEAD"), require = 1)
    private void gd656conquest$releaseDeathResources(CallbackInfo ci) {
        ClientDeathFilter.releaseResources();
    }
}
