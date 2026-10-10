package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.mods.gd656conquest.client.room.ClientRoomChatState;
import org.mods.gd656conquest.client.room.RoomChatLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retains native message wrapping, history, fading, signed-message tags, scrolling and links. */
@Mixin(value = ChatComponent.class)
public abstract class RoomChatMixin {
    @Unique private boolean gd656conquest$translated;
    @Inject(method = "render", at = @At("HEAD"))
    private void gd656conquest$position(GuiGraphics gui, int tick, int mouseX, int mouseY, CallbackInfo ci) {
        gd656conquest$translated = ClientRoomChatState.active();
        if (!gd656conquest$translated) return;
        RoomChatLayout box = ClientRoomChatState.layout();
        double scale = ((ChatComponent)(Object)this).getScale();
        gui.pose().pushPose();
        gui.pose().translate(box.left() - 4 * scale, box.baseline() - Math.floor((gui.guiHeight() - 40) / scale) * scale, 0);
    }
    @Inject(method = "render", at = @At("RETURN"))
    private void gd656conquest$restore(GuiGraphics gui, int tick, int mouseX, int mouseY, CallbackInfo ci) {
        if (gd656conquest$translated) { gui.pose().popPose(); gd656conquest$translated = false; }
    }
    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void gd656conquest$width(CallbackInfoReturnable<Integer> cir) {
        if (ClientRoomChatState.active()) cir.setReturnValue(ClientRoomChatState.layout().width());
    }
    @Inject(method = "getLinesPerPage", at = @At("HEAD"), cancellable = true)
    private void gd656conquest$rows(CallbackInfoReturnable<Integer> cir) {
        if (ClientRoomChatState.active()) cir.setReturnValue(ClientRoomChatState.layout().capacity());
    }
    @Inject(method = "screenToChatX", at = @At("HEAD"), cancellable = true)
    private void gd656conquest$mouseX(double x, CallbackInfoReturnable<Double> cir) {
        if (ClientRoomChatState.active()) cir.setReturnValue(ClientRoomChatState.layout().chatX(x, ((ChatComponent)(Object)this).getScale()));
    }
    @Inject(method = "screenToChatY", at = @At("HEAD"), cancellable = true)
    private void gd656conquest$mouseY(double y, CallbackInfoReturnable<Double> cir) {
        if (ClientRoomChatState.active()) cir.setReturnValue(ClientRoomChatState.layout().chatY(y, ((ChatComponent)(Object)this).getScale(),
            ((KillerCardChatAccessor)this).gd656conquest$chatLineHeight()));
    }
    @ModifyVariable(method = "handleChatQueueClicked", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double gd656conquest$queueX(double x) {
        return ClientRoomChatState.active() ? x - ClientRoomChatState.layout().left() + 4 * ((ChatComponent)(Object)this).getScale() : x;
    }
    @ModifyVariable(method = "handleChatQueueClicked", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double gd656conquest$queueY(double y) {
        return ClientRoomChatState.active() ? y - ClientRoomChatState.layout().baseline() + Minecraft.getInstance().getWindow().getGuiScaledHeight() - 40 : y;
    }
}
