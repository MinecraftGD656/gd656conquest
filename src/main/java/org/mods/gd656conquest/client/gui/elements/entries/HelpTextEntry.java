/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.elements.entries;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.List;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDTextRenderer;

public class HelpTextEntry
extends GDRowRenderer {
    private final String title;
    private final String description;
    private GDTextRenderer descriptionRenderer;

    public HelpTextEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String title, String description) {
        super(x1, y1, x2, y2, bgColor, bgAlpha, false);
        this.title = title;
        this.description = description;
        this.addColumn(title, 120, BattlefieldUiTheme.palette().accent(), false, false, null);
        this.addCustomColumn(-1, null, (guiGraphics, x, y, width, height) -> {
            if (this.descriptionRenderer == null) {
                this.descriptionRenderer = new GDTextRenderer(description, x + 4, y + 4, x + width - 4, y + height - 4, 1.0f, -1, true);
            } else {
                this.descriptionRenderer.setX1(x + 4);
                this.descriptionRenderer.setY1(y + 4);
                this.descriptionRenderer.setX2(x + width - 4);
                this.descriptionRenderer.setY2(y + height - 4);
            }
            this.descriptionRenderer.render(guiGraphics, 0.0f, false);
        });
    }

    public HelpTextEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, List<GDTextRenderer.ColoredText> titleParts, String description) {
        super(x1, y1, x2, y2, bgColor, bgAlpha, false);
        this.title = titleParts == null ? "" : titleParts.stream().map(part -> part.text).reduce("", String::concat);
        this.description = description;
        this.addColoredColumn(titleParts, 120, false, false);
        this.addCustomColumn(-1, null, (guiGraphics, x, y, width, height) -> {
            if (this.descriptionRenderer == null) {
                this.descriptionRenderer = new GDTextRenderer(description, x + 4, y + 4, x + width - 4, y + height - 4, 1.0f, -1, true);
            } else {
                this.descriptionRenderer.setX1(x + 4);
                this.descriptionRenderer.setY1(y + 4);
                this.descriptionRenderer.setX2(x + width - 4);
                this.descriptionRenderer.setY2(y + height - 4);
            }
            this.descriptionRenderer.render(guiGraphics, 0.0f, false);
        });
    }

    public int getRequiredHeight(int width) {
        int descWidth = width - 120 - 8;
        if (descWidth <= 0) {
            return 17;
        }
        if (this.descriptionRenderer == null) {
            this.descriptionRenderer = new GDTextRenderer(this.description, 0, 0, descWidth, 100, 1.0f, -1, true);
        } else {
            this.descriptionRenderer.setX1(0);
            this.descriptionRenderer.setX2(descWidth);
        }
        int textHeight = this.descriptionRenderer.getFinalHeight();
        return Math.max(17, textHeight + 8);
    }
}

