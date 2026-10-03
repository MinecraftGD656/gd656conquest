package org.mods.gd656conquest.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.mods.gd656conquest.client.compat.xaero.XaeroMinimapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GD656Mods: 当 Xaero 小地图位于屏幕下半部分时, 把原版聊天组件整体上移,
 * 使聊天底部固定在屏幕顶端往下 100 像素处; 其它情况聊天按原样渲染。
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentOffsetMixin {
    @Unique
    private boolean gd656conquest$offsetApplied;

    @Inject(method = "render", at = @At("HEAD"))
    private void gd656conquest$shiftChatUp(
        GuiGraphics guiGraphics,
        int tickCount,
        int mouseX,
        int mouseY,
        CallbackInfo callbackInfo
    ) {
        double shift = XaeroMinimapRenderState.resolveShiftUpPixels(guiGraphics.guiHeight());
        if (shift <= 0.0D) {
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, (float) -shift, 0.0F);
        this.gd656conquest$offsetApplied = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void gd656conquest$restoreChatPose(
        GuiGraphics guiGraphics,
        int tickCount,
        int mouseX,
        int mouseY,
        CallbackInfo callbackInfo
    ) {
        if (!this.gd656conquest$offsetApplied) {
            return;
        }
        this.gd656conquest$offsetApplied = false;
        guiGraphics.pose().popPose();
    }

    /** 鼠标命中测试使用同样位移, 保证滚动与悬停提示在移动后依然对齐。 */
    @Redirect(
        method = {"screenToChatY", "handleChatQueueClicked"},
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I")
    )
    private int gd656conquest$shiftHitTestBase(Window window) {
        int guiScaledHeight = window.getGuiScaledHeight();
        return (int) Math.round(
            guiScaledHeight - XaeroMinimapRenderState.resolveShiftUpPixels(guiScaledHeight)
        );
    }
}
