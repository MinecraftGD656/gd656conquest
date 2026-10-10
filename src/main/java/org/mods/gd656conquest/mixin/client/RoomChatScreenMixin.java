package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.mods.gd656conquest.client.room.ClientRoomChatState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatScreen.class)
public abstract class RoomChatScreenMixin {
    @Shadow protected EditBox input;
    @Inject(method = "init", at = @At("RETURN"))
    private void gd656conquest$input(CallbackInfo ci) { gd656conquest$moveInput(); }
    private void gd656conquest$moveInput() {
        if (input == null) return;
        if (ClientRoomChatState.active()) {
            var box = ClientRoomChatState.layout();
            input.setX(box.left()); input.setY(box.inputY()); input.setWidth(box.width());
        } else {
            var window = Minecraft.getInstance().getWindow();
            input.setX(4); input.setY(window.getGuiScaledHeight() - 12); input.setWidth(window.getGuiScaledWidth() - 4);
        }
    }
    @Inject(method = "render", at = @At("HEAD"))
    private void gd656conquest$updateInput(GuiGraphics gui, int mouseX, int mouseY, float partialTick, CallbackInfo ci) { gd656conquest$moveInput(); }
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0))
    private void gd656conquest$inputBackground(GuiGraphics gui, int x1, int y1, int x2, int y2, int color) {
        if (ClientRoomChatState.active()) {
            var box = ClientRoomChatState.layout();
            gui.fill(box.left() - 2, box.inputY() - 2, box.left() + box.width() + 2, box.inputY() + 12, color);
        } else gui.fill(x1, y1, x2, y2, color);
    }
    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/components/EditBox;Lnet/minecraft/client/gui/Font;ZZIIZI)V"), index = 7)
    private int gd656conquest$suggestionRows(int rows) { return ClientRoomChatState.suggestionRows(rows); }
}
