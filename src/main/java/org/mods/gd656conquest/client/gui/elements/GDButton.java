/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.elements;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.gui.elements.GDTextRenderer;

public class GDButton {
    private static final float DEFAULT_FONT_SCALE = 1.0f;
    private static final float DEFAULT_MIN_AUTO_SCALE = 0.62f;
    private int x;
    private int y;
    private int width;
    private int height;
    private Component message;
    private final Consumer<GDButton> onPress;
    private final Minecraft minecraft;
    private int textColor = -1;
    public boolean active = true;
    private float hoverProgress = 0.0f;
    private long lastTime;
    private boolean autoScaleTextToFit = false;
    private float minAutoScale = 0.62f;
    private GDTextRenderer textRenderer;

    public GDButton(int x, int y, int width, int height, Component message, Consumer<GDButton> onPress) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message;
        this.onPress = onPress;
        this.minecraft = Minecraft.getInstance();
        this.lastTime = System.currentTimeMillis();
        int textY = y + (height - 9) / 2;
        this.textRenderer = new GDTextRenderer(message.getString(), x, textY, x + width, textY + 9, 1.0f, this.textColor, false);
        this.textRenderer.setCentered(true);
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
        BattlefieldUiTheme.button(guiGraphics, this.x, this.y, this.width, this.height, hovered, this.active);
        this.updateTextRendererBounds();
        int rgb = this.textColor & 0xffffff;
        boolean neutral = rgb == 0xffffff || rgb == 0xaaaaaa || rgb == 0x666666;
        int labelColor = BattlefieldUiTheme.textColor(this.textColor);
        if (neutral) labelColor = !this.active ? BattlefieldUiTheme.palette().muted() : hovered ? BattlefieldUiTheme.palette().onSelection() : labelColor;
        this.textRenderer.setOverrideColor(labelColor);
        this.textRenderer.render(guiGraphics, partialTick);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && button == 0 && mouseX >= (double)this.x && mouseX <= (double)(this.x + this.width) && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height)) {
            this.onPress.accept(this);
            return true;
        }
        return false;
    }

    public void setX(int x) {
        this.x = x;
        this.updateTextRendererBounds();
    }

    public void setY(int y) {
        this.y = y;
        this.updateTextRendererBounds();
    }

    public void setWidth(int width) {
        this.width = width;
        this.updateTextRendererBounds();
    }

    public void setHeight(int height) {
        this.height = height;
        this.updateTextRendererBounds();
    }

    public void setMessage(Component message) {
        this.message = message;
        if (this.textRenderer != null) {
            this.textRenderer.setText(message.getString());
        }
    }

    public void setTextColor(int color) {
        this.textColor = color;
        if (this.textRenderer != null) {
            this.textRenderer.setColor(color);
        }
    }

    public void setAutoScaleTextToFit(boolean autoScaleTextToFit) {
        this.autoScaleTextToFit = autoScaleTextToFit;
        this.updateTextRendererBounds();
    }

    public void setMinAutoScale(float minAutoScale) {
        this.minAutoScale = Math.max(0.1f, Math.min(1.0f, minAutoScale));
        this.updateTextRendererBounds();
    }

    private void updateTextRendererBounds() {
        if (this.textRenderer != null) {
            float fontScale = this.resolveFontScale();
            int textHeight = Math.max(1, (int)Math.ceil(BattlefieldUiTheme.textHeight() * fontScale));
            int textY = this.y + (this.height - textHeight) / 2;
            this.textRenderer.setX1(this.x);
            this.textRenderer.setY1(textY);
            this.textRenderer.setX2(this.x + this.width);
            this.textRenderer.setY2(textY + textHeight);
            this.textRenderer.setFontSize(fontScale);
        }
    }

    private float resolveFontScale() {
        // Every button with the same height shares one readable size; long labels scroll.
        return Math.min(1f,Math.max(1,this.height-4)/(float)BattlefieldUiTheme.textHeight());
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public Component getMessage() {
        return this.message;
    }
}

