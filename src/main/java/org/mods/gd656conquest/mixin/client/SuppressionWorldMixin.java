package org.mods.gd656conquest.mixin.client;
import net.minecraft.client.renderer.GameRenderer;
import org.mods.gd656conquest.client.room.ClientSuppressionState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class SuppressionWorldMixin {
    @Inject(method="render(FJZ)V",at=@At("HEAD"),require=1)
    private void gd656$begin(float tick,long nanos,boolean world,CallbackInfo ci){ClientSuppressionState.beginFrame();}
    @Inject(method="render(FJZ)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V",remap=false,ordinal=0),require=1)
    private void gd656$draw(float tick,long nanos,boolean world,CallbackInfo ci){ClientSuppressionState.renderWorld(tick);}
    @Inject(method="reloadShaders(Lnet/minecraft/server/packs/resources/ResourceProvider;)V",at=@At("HEAD"),require=1)
    private void gd656$reload(CallbackInfo ci){ClientSuppressionState.release();}
    @Inject(method="close",remap=false,at=@At("HEAD"),require=1)
    private void gd656$release(CallbackInfo ci){ClientSuppressionState.release();}
}
