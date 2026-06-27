package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Unique
    private static final float GD656CONQUEST_ROOM_TABLIST_OFFSET_Y = 50.0F;

    @Unique
    private boolean gd656conquest$translatedForRoomHud;

    @Inject(method = "render", at = @At("HEAD"))
    private void gd656conquest$pushBelowRoomHud(GuiGraphics guiGraphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        gd656conquest$translatedForRoomHud = false;
        if (!gd656conquest$isJoinedRoom()) {
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, GD656CONQUEST_ROOM_TABLIST_OFFSET_Y, 0.0F);
        gd656conquest$translatedForRoomHud = true;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void gd656conquest$restoreAfterRoomHud(GuiGraphics guiGraphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if (!gd656conquest$translatedForRoomHud) {
            return;
        }
        guiGraphics.pose().popPose();
        gd656conquest$translatedForRoomHud = false;
    }

    @Unique
    private boolean gd656conquest$isJoinedRoom() {
        String roomId = normalize(ClientHudDebugState.getRoomId());
        String roomState = normalize(ClientHudDebugState.getRoomState());
        return !roomId.isEmpty() && !"-".equals(roomId) && !"未加入房间".equals(roomState);
    }

    @Unique
    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
