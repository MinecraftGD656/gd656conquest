package org.mods.gd656conquest.client.gui.elements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.compat.tacz.TaczGunStatCompat;

public class GunStatComparator {

    public static void render(GuiGraphics guiGraphics, Font font, int x, int y, int width, ItemStack originalStack, ItemStack compareStack) {
        if (originalStack == null || originalStack.isEmpty()) {
            return;
        }

        TaczGunStatCompat.GunStatData originalData = TaczGunStatCompat.getInstance().resolveGunStats(originalStack);
        if (originalData == null) {
            return; // Not a TACZ gun
        }

        TaczGunStatCompat.GunStatData compareData = null;
        if (compareStack != null && !compareStack.isEmpty()) {
            compareData = TaczGunStatCompat.getInstance().resolveGunStats(compareStack);
        }

        int currentY = y;
        int rowHeight = 14;

        currentY = renderStatRow(guiGraphics, font, x, currentY, width, rowHeight, "伤害", 
                originalData.damageModified, compareData != null ? compareData.damageModified : null, 100.0, false);
        
        currentY = renderStatRow(guiGraphics, font, x, currentY, width, rowHeight, "射速", 
                originalData.rpmModified, compareData != null ? compareData.rpmModified : null, 1200.0, false);

        currentY = renderStatRow(guiGraphics, font, x, currentY, width, rowHeight, "载弹量", 
                originalData.capacityModified, compareData != null ? compareData.capacityModified : null, 150.0, false);
        
        currentY = renderStatRow(guiGraphics, font, x, currentY, width, rowHeight, "换弹耗时", 
                originalData.reloadTimeModified, compareData != null ? compareData.reloadTimeModified : null, 5.0, true);
        
        currentY = renderStatRow(guiGraphics, font, x, currentY, width, rowHeight, "垂直后坐力", 
                originalData.recoilPitchModified, compareData != null ? compareData.recoilPitchModified : null, 2.0, true);

        currentY = renderStatRow(guiGraphics, font, x, currentY, width, rowHeight, "水平后坐力", 
                originalData.recoilYawModified, compareData != null ? compareData.recoilYawModified : null, 2.0, true);
    }

    private static int renderStatRow(GuiGraphics guiGraphics, Font font, int x, int y, int width, int height, 
                                     String label, double originalVal, Double compareVal, double maxVal, boolean lowerIsBetter) {
        int labelWidth = 60;
        int valueWidth = 40;
        int barX = x + labelWidth;
        int barWidth = width - labelWidth - valueWidth;
        
        guiGraphics.drawString(font, label, x, y + 2, 0xFFD7D7D7, false);
        
        double originalRatio = Math.max(0.0, Math.min(1.0, originalVal / maxVal));
        int originalBarWidth = (int) (barWidth * originalRatio);
        
        guiGraphics.fill(barX, y + 4, barX + barWidth, y + height - 4, 0x44FFFFFF);
        guiGraphics.fill(barX, y + 4, barX + originalBarWidth, y + height - 4, 0xFFFFFFFF);
        
        String valText = String.format("%.1f", originalVal);
        int valColor = 0xFFFFFFFF;

        if (compareVal != null && Math.abs(compareVal - originalVal) > 0.01) {
            double compareRatio = Math.max(0.0, Math.min(1.0, compareVal / maxVal));
            int compareBarWidth = (int) (barWidth * compareRatio);
            
            boolean isBetter = (compareVal > originalVal && !lowerIsBetter) || (compareVal < originalVal && lowerIsBetter);
            int diffColor = isBetter ? 0xFF55FF55 : 0xFFFF5555;
            
            if (compareBarWidth > originalBarWidth) {
                guiGraphics.fill(barX + originalBarWidth, y + 4, barX + compareBarWidth, y + height - 4, diffColor);
            } else {
                guiGraphics.fill(barX + compareBarWidth, y + 4, barX + originalBarWidth, y + height - 4, diffColor);
            }
            
            valText = String.format("%.1f", compareVal);
            valColor = diffColor;
        }

        guiGraphics.drawString(font, valText, x + width - valueWidth + 4, y + 2, valColor, false);
        
        return y + height;
    }
}
