package org.mods.gd656conquest.mixin.client.compat.xaero;

import net.minecraft.client.gui.GuiGraphics;
import org.mods.gd656conquest.client.compat.xaero.XaeroMinimapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.graphics.CustomVertexConsumers;

/**
 * GD656Mods: 捕捉 Xaero 小地图每次真实渲染时的占位尺寸(boxSize)。
 *
 * <p>Xaero 不在屏幕上绘制小地图时不会进入该方法, 因此该记录天然反映"当前是否真的显示小地图"
 * (例如主城不显示), 以及当前分辨率下 Xaero 算出的占位高度。</p>
 */
@Pseudo
@Mixin(targets = "xaero.common.minimap.MinimapProcessor", remap = false)
public class XaeroMinimapProcessorMixin {
    @Inject(method = "onRender", at = @At("HEAD"), remap = false)
    private void gd656conquest$captureMinimapPlaceholder(
        GuiGraphics guiGraphics,
        int x,
        int y,
        int width,
        int height,
        double scale,
        int size,
        int boxSize,
        float partial,
        CustomVertexConsumers cvc,
        CallbackInfo callbackInfo
    ) {
        XaeroMinimapRenderState.recordRenderedPlaceholder(boxSize, y, height);
    }
}
