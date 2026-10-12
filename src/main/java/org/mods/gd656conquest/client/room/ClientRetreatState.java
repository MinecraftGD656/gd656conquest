package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Retreat is a warning only; combat remains available until actual death. */
public final class ClientRetreatState {
    private static int remainingTicks;
    private static Object level, player;
    private ClientRetreatState() {}
    public static void accept(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { reset(); return; }
        level = mc.level; player = mc.player;
        remainingTicks = Math.max(0, ticks);
    }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || mc.player != player || mc.player == null || !mc.player.isAlive()
            || ClientDeathFilter.deathActive() || ClientDeathFlowState.shouldRenderDeployBoundaries()) {
            reset(); return;
        }
        if (remainingTicks > 0) remainingTicks--;
    }
    public static void renderWarning(GuiGraphics gui) {
        Minecraft mc = Minecraft.getInstance();
        if (remainingTicks > 0 && !ClientBoundaryState.isActive() && !mc.options.hideGui) {
            CombatAreaWarningRenderer.render(gui, "撤退至下一区域", remainingTicks);
        }
    }
    public static boolean isActive() { return remainingTicks > 0; }
    public static void reset() { remainingTicks = 0; level = null; player = null; }
}
