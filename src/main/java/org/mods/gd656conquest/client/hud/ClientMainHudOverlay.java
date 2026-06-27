package org.mods.gd656conquest.client.hud;

import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.mods.gd656conquest.client.event.handler.ClientEventHandler;

public final class ClientMainHudOverlay implements IGuiOverlay {
    public static final String ID = "main_hud";

    @Override
    public void render(ForgeGui gui, net.minecraft.client.gui.GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        ClientEventHandler.getInstance().renderMainHud(gui, guiGraphics, partialTick, screenWidth, screenHeight);
    }
}
