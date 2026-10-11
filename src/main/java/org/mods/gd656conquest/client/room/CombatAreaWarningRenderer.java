package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.mods.gd656conquest.server.room.runtime.BoundaryCountdown;

/** Shared typography and placement for retreat and the combat-area warning. */
public final class CombatAreaWarningRenderer {
    private CombatAreaWarningRenderer() {}
    public static void render(GuiGraphics gui, String title, int remainingTicks) {
        Minecraft mc = Minecraft.getInstance();
        int w = gui.guiWidth(), h = gui.guiHeight();
        float spacing = .8F; // Same glyph spacing as the existing death-cause subtitles.
        float titleWidth = mc.font.width(title) + (title.codePointCount(0, title.length()) - 1) * spacing;
        float titleScale = Math.max(.65F, h * .027F / 9F);
        titleScale = Math.min(titleScale, w * .7F / Math.max(1, titleWidth));
        drawCentered(gui, title, w * .5F, h * .33F, titleScale, spacing);
        drawCentered(gui, Integer.toString(BoundaryCountdown.displaySeconds(remainingTicks)), w * .5F, h * .39F, titleScale * 1.8F, 0);
    }
    private static void drawCentered(GuiGraphics gui, String value, float x, float y, float scale, float spacing) {
        var font = Minecraft.getInstance().font;
        gui.pose().pushPose();
        try {
            gui.pose().translate(x, y - 4.5F * scale, 0);
            gui.pose().scale(scale, scale, 1);
            if (spacing == 0) {
                gui.drawString(font, value, -font.width(value) / 2, 0, 0xFFFFFFFF, true);
            } else {
                int[] letters = value.codePoints().toArray();
                float cursor = -(font.width(value) + (letters.length - 1) * spacing) / 2;
                for (int letter : letters) {
                    String glyph = new String(Character.toChars(letter));
                    gui.drawString(font, glyph, Math.round(cursor), 0, 0xFFFFFFFF, true);
                    cursor += font.width(glyph) + spacing;
                }
            }
        } finally { gui.pose().popPose(); }
    }
}
