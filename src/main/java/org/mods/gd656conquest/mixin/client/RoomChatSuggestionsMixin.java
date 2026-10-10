package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ChatScreen;
import org.mods.gd656conquest.client.room.ClientRoomChatState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CommandSuggestions.class)
public abstract class RoomChatSuggestionsMixin {
    @Redirect(method = {"showSuggestions", "renderUsage"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/Screen;height:I"))
    private int gd656conquest$suggestionAnchor(Screen screen) {
        return screen instanceof ChatScreen && ClientRoomChatState.active() ? ClientRoomChatState.layout().inputY() + 12 : screen.height;
    }
}
