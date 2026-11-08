/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.elements;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class GDTextRenderer {
    private final Minecraft minecraft = Minecraft.getInstance();
    private String text;
    private int x1;
    private int y1;
    private int x2;
    private int y2;
    private int width;
    private int height;
    private float fontSize;
    private int color;
    private List<ColoredText> coloredTexts;
    private boolean autoWrap;
    private long pauseStartTime = -1L;
    private float scrollOffset = 0.0f;
    private boolean scrollingForward = true;
    private long lastTime;
    private static final float SCROLL_SPEED = 30.0f;
    private static final long PAUSE_TIME_MS = 1000L;
    private boolean centered = false;
    private Integer overrideColor = null;
    private Integer backgroundColor = null;
    private float scrollY = 0.0f;

    public void setOverrideColor(Integer color) {
        this.overrideColor = color;
    }

    public void setBackgroundColor(Integer backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public GDTextRenderer(String text, int x1, int y1, int x2, int y2, float fontSize, int color, boolean autoWrap) {
        this.text = text;
        this.coloredTexts = null;
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.width = this.x2 - this.x1;
        this.height = this.y2 - this.y1;
        this.fontSize = fontSize;
        this.color = color;
        this.autoWrap = autoWrap;
        this.lastTime = System.currentTimeMillis();
    }

    public GDTextRenderer(List<ColoredText> coloredTexts, int x1, int y1, int x2, int y2, float fontSize, boolean autoWrap) {
        this.coloredTexts = coloredTexts;
        this.text = "";
        for (ColoredText ct : coloredTexts) {
            this.text = this.text + ct.text;
        }
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.width = this.x2 - this.x1;
        this.height = this.y2 - this.y1;
        this.fontSize = fontSize;
        this.color = -1;
        this.autoWrap = autoWrap;
        this.lastTime = System.currentTimeMillis();
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
    }

    public void setFontSize(float fontSize) {
        this.fontSize = Math.max(0.1f, fontSize);
    }

    public void render(GuiGraphics guiGraphics, float partialTick) {
        this.render(guiGraphics, partialTick, true);
    }

    public void render(GuiGraphics guiGraphics, float partialTick, boolean useScissor) {
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastTime) / 1000.0f;
        this.lastTime = now;
        this.renderInternal(guiGraphics, partialTick, useScissor, dt);
    }

    public void renderInternal(GuiGraphics guiGraphics, float partialTick, boolean useOwnScissorAndScroll, float dt) {
        guiGraphics.pose().pushPose();
        if (useOwnScissorAndScroll) {
            org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(guiGraphics,this.x1, this.y1, this.x2, this.y2);
        }
        float scale = this.fontSize;
        guiGraphics.pose().translate(this.x1, this.y1, 0.0f);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        float scaledWidth = (float)this.width / scale;
        if (this.autoWrap) {
            this.renderWrappedText(guiGraphics, scaledWidth);
        } else {
            this.renderScrollingText(guiGraphics, dt, scaledWidth);
        }
        guiGraphics.pose().popPose();
        if (useOwnScissorAndScroll) {
            guiGraphics.disableScissor();
        }
    }

    private void renderWrappedText(GuiGraphics guiGraphics, float maxWidth) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, -this.scrollY, 0.0f);
        this.drawWrappedInternal(guiGraphics, maxWidth);
        guiGraphics.pose().popPose();
    }

    public void setScrollY(float scrollY) {
        this.scrollY = scrollY;
    }

    public float getScrollY() {
        return this.scrollY;
    }

    public float getMaxScrollY() {
        if (!this.autoWrap) {
            return 0.0f;
        }
        int contentHeight = this.getFinalHeight();
        float containerHeight = (float)this.height / this.fontSize;
        return Math.max(0.0f, (float)contentHeight - containerHeight);
    }

    private void drawWrappedInternal(GuiGraphics guiGraphics, float maxWidth) {
        if (this.coloredTexts != null) {
            this.renderMixedColorWrapped(guiGraphics, maxWidth);
            return;
        }
        List<String> lines = this.wrapText(this.text, maxWidth);
        for (int i = 0; i < lines.size(); ++i) {
            String line = lines.get(i);
            float lineX = 0.0f;
            if (this.centered) {
                lineX = (maxWidth - (float)BattlefieldUiTheme.textWidth(line)) / 2.0f;
            }
            this.drawLineBackground(guiGraphics, lineX, i * BattlefieldUiTheme.textHeight(), BattlefieldUiTheme.textWidth(line));
            BattlefieldUiTheme.drawText(guiGraphics, line, (int)lineX, i * BattlefieldUiTheme.textHeight(), this.overrideColor != null ? this.overrideColor : this.color);
        }
    }

    private void renderMixedColorWrapped(GuiGraphics guiGraphics, float maxWidth) {
        int yOffset = 0;
        float currentX = 0.0f;
        for (ColoredText ct : this.coloredTexts) {
            String remaining = ct.text;
            while (!remaining.isEmpty()) {
                int count;
                int n = count = (float)BattlefieldUiTheme.textWidth(remaining) > maxWidth - currentX ? BattlefieldUiTheme.truncate(remaining, (int)(maxWidth - currentX)).length() : remaining.length();
                if (count == 0 && currentX > 0.0f) {
                    currentX = 0.0f;
                    yOffset += BattlefieldUiTheme.textHeight();
                    continue;
                }
                String part = remaining.substring(0, Math.max(1, count));
                this.drawLineBackground(guiGraphics, currentX, yOffset, BattlefieldUiTheme.textWidth(part));
                BattlefieldUiTheme.drawText(guiGraphics, part, (int)currentX, yOffset, this.overrideColor != null ? this.overrideColor : ct.color);
                currentX += (float)BattlefieldUiTheme.textWidth(part);
                remaining = remaining.substring(part.length());
                if (!(currentX >= maxWidth - 1.0f)) continue;
                currentX = 0.0f;
                yOffset += BattlefieldUiTheme.textHeight();
            }
        }
    }

    private List<String> wrapText(String text, float maxWidth) {
        String[] paragraphs;
        ArrayList<String> lines = new ArrayList<String>();
        String processedText = text.replace("\\n", "\n").replace("\r\n", "\n").replace("\r", "\n");
        for (String paragraph : paragraphs = processedText.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder currentLine = new StringBuilder();
            for (int i = 0; i < paragraph.length(); ++i) {
                char c = paragraph.charAt(i);
                String nextText = currentLine.toString() + c;
                if ((float)BattlefieldUiTheme.textWidth(nextText) > maxWidth && currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
                currentLine.append(c);
            }
            if (currentLine.length() <= 0) continue;
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private void renderScrollingText(GuiGraphics guiGraphics, float dt, float maxWidth) {
        int textWidth = 0;
        if (this.coloredTexts == null) {
            textWidth = BattlefieldUiTheme.textWidth(this.text);
        } else {
            for (ColoredText ct : this.coloredTexts) {
                textWidth += BattlefieldUiTheme.textWidth(ct.text);
            }
        }
        if ((float)textWidth <= maxWidth + 1.0f) {
            this.drawInternal(guiGraphics, 0, 0, maxWidth);
            this.scrollOffset = 0.0f;
            this.pauseStartTime = -1L;
            this.scrollingForward = true;
            return;
        }
        float maxScroll = (float)textWidth - maxWidth;
        if (this.pauseStartTime != -1L) {
            long elapsed = System.currentTimeMillis() - this.pauseStartTime;
            if (elapsed > 1000L) {
                this.pauseStartTime = -1L;
            }
        } else if (this.scrollingForward) {
            this.scrollOffset += 30.0f * dt;
            if (this.scrollOffset >= maxScroll) {
                this.scrollOffset = maxScroll;
                this.scrollingForward = false;
                this.pauseStartTime = System.currentTimeMillis();
            }
        } else {
            this.scrollOffset -= 30.0f * dt;
            if (this.scrollOffset <= 0.0f) {
                this.scrollOffset = 0.0f;
                this.scrollingForward = true;
                this.pauseStartTime = System.currentTimeMillis();
            }
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(-this.scrollOffset, 0.0f, 0.0f);
        this.drawInternal(guiGraphics, 0, 0, textWidth);
        guiGraphics.pose().popPose();
    }

    private void drawInternal(GuiGraphics guiGraphics, int x, int y, float containerWidth) {
        if (this.coloredTexts == null) {
            float drawX = x;
            int textWidth = BattlefieldUiTheme.textWidth(this.text);
            if (this.centered && !this.isCurrentlyScrolling()) {
                drawX += (containerWidth - (float)textWidth) / 2.0f;
            }
            int backgroundWidth = this.isCurrentlyScrolling() ? Math.max(0, (int)containerWidth) : textWidth;
            this.drawLineBackground(guiGraphics, drawX, y, backgroundWidth);
            BattlefieldUiTheme.drawText(guiGraphics, this.text, (int)drawX, y, this.overrideColor != null ? this.overrideColor : this.color);
        } else {
            float currentX = x;
            float totalWidth = 0.0f;
            for (ColoredText ct : this.coloredTexts) {
                totalWidth += (float)BattlefieldUiTheme.textWidth(ct.text);
            }
            if (this.centered && !this.isCurrentlyScrolling()) {
                currentX += (containerWidth - totalWidth) / 2.0f;
            }
            int backgroundWidth = this.isCurrentlyScrolling() ? Math.max(0, (int)containerWidth) : (int)totalWidth;
            this.drawLineBackground(guiGraphics, currentX, y, backgroundWidth);
            for (ColoredText ct : this.coloredTexts) {
                BattlefieldUiTheme.drawText(guiGraphics, ct.text, (int)currentX, y, this.overrideColor != null ? this.overrideColor : ct.color);
                currentX += (float)BattlefieldUiTheme.textWidth(ct.text);
            }
        }
    }

    private void drawEllipsizedInternal(GuiGraphics guiGraphics, int x, int y, float maxWidth) {
        String ellipsis = "...";
        int ellipsisWidth = BattlefieldUiTheme.textWidth(ellipsis);
        int targetWidth = Math.max(0, (int)maxWidth - ellipsisWidth);
        if (this.coloredTexts == null) {
            String base = this.text == null ? "" : this.text;
            String clipped = BattlefieldUiTheme.truncate(base, targetWidth);
            BattlefieldUiTheme.drawText(guiGraphics, clipped + ellipsis, x, y, this.overrideColor != null ? this.overrideColor : this.color);
            return;
        }
        List<ColoredText> segments = this.buildEllipsizedColoredTexts(targetWidth);
        float currentX = x;
        int lastColor = this.overrideColor != null ? this.overrideColor : (segments.isEmpty() ? this.color : segments.get((int)(segments.size() - 1)).color);
        for (ColoredText segment : segments) {
            int segmentColor = this.overrideColor != null ? this.overrideColor : segment.color;
            BattlefieldUiTheme.drawText(guiGraphics, segment.text, (int)currentX, y, segmentColor);
            currentX += (float)BattlefieldUiTheme.textWidth(segment.text);
            lastColor = segmentColor;
        }
        BattlefieldUiTheme.drawText(guiGraphics, ellipsis, (int)currentX, y, lastColor);
    }

    private List<ColoredText> buildEllipsizedColoredTexts(int targetWidth) {
        ArrayList<ColoredText> result = new ArrayList<ColoredText>();
        if (targetWidth <= 0 || this.coloredTexts == null) {
            return result;
        }
        int used = 0;
        for (ColoredText ct : this.coloredTexts) {
            String part;
            if (ct == null || ct.text == null || ct.text.isEmpty()) continue;
            int remaining = targetWidth - used;
            if (remaining <= 0 || (part = BattlefieldUiTheme.truncate(ct.text, remaining)) == null || part.isEmpty()) break;
            result.add(new ColoredText(part, ct.color));
            used += BattlefieldUiTheme.textWidth(part);
            if (part.length() >= ct.text.length()) continue;
            break;
        }
        return result;
    }

    private void drawLineBackground(GuiGraphics guiGraphics, float drawX, int drawY, int lineWidth) {
        if (this.backgroundColor == null || this.backgroundColor == 0 || !this.hasContent() || lineWidth <= 0) {
            return;
        }
        int left = (int)Math.floor(drawX);
        int right = (int)Math.ceil(drawX + (float)lineWidth);
        guiGraphics.fill(left, drawY, right, drawY + BattlefieldUiTheme.textHeight(), this.backgroundColor);
    }

    private boolean hasContent() {
        if (this.coloredTexts != null) {
            return !this.coloredTexts.isEmpty();
        }
        return this.text != null && !this.text.isBlank();
    }

    private boolean isCurrentlyScrolling() {
        int textWidth = 0;
        if (this.coloredTexts == null) {
            textWidth = BattlefieldUiTheme.textWidth(this.text);
        } else {
            for (ColoredText ct : this.coloredTexts) {
                textWidth += BattlefieldUiTheme.textWidth(ct.text);
            }
        }
        float scale = this.fontSize;
        float scaledWidth = (float)this.width / scale;
        return (float)textWidth > scaledWidth + 1.0f;
    }

    public int getFinalHeight() {
        if (!this.autoWrap) {
            return (int)Math.ceil(BattlefieldUiTheme.textHeight() * this.fontSize);
        }
        float scale = this.fontSize;
        float scaledWidth = (float)this.width / scale;
        if (this.coloredTexts != null) {
            return (int)((float)this.getMixedColorWrappedHeight(scaledWidth) * scale);
        }
        List<String> lines = this.wrapText(this.text, scaledWidth);
        return (int)Math.ceil(lines.size() * BattlefieldUiTheme.textHeight() * scale);
    }

    private int getMixedColorWrappedHeight(float maxWidth) {
        int lines = 1;
        float currentX = 0.0f;
        for (ColoredText ct : this.coloredTexts) {
            String remaining = ct.text;
            while (!remaining.isEmpty()) {
                int count;
                int n = count = (float)BattlefieldUiTheme.textWidth(remaining) > maxWidth - currentX ? BattlefieldUiTheme.truncate(remaining, (int)(maxWidth - currentX)).length() : remaining.length();
                if (count == 0 && currentX > 0.0f) {
                    currentX = 0.0f;
                    ++lines;
                    continue;
                }
                String part = remaining.substring(0, Math.max(1, count));
                currentX += (float)BattlefieldUiTheme.textWidth(part);
                remaining = remaining.substring(part.length());
                if (!(currentX >= maxWidth - 1.0f) || remaining.isEmpty()) continue;
                currentX = 0.0f;
                ++lines;
            }
        }
        return lines * BattlefieldUiTheme.textHeight();
    }

    public void setText(String text) {
        if (text != null && !text.equals(this.text)) {
            this.text = text;
            this.coloredTexts = null;
        }
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setColoredTexts(List<ColoredText> coloredTexts) {
        if (coloredTexts != null) {
            this.coloredTexts = coloredTexts;
            StringBuilder sb = new StringBuilder();
            for (ColoredText ct : coloredTexts) {
                sb.append(ct.text);
            }
            this.text = sb.toString();
        }
    }

    public void setX1(int x1) {
        this.x1 = x1;
        this.updateWidthHeight();
    }

    public void setY1(int y1) {
        this.y1 = y1;
        this.updateWidthHeight();
    }

    public void setX2(int x2) {
        this.x2 = x2;
        this.updateWidthHeight();
    }

    public void setY2(int y2) {
        this.y2 = y2;
        this.updateWidthHeight();
    }

    private void updateWidthHeight() {
        this.width = Math.abs(this.x2 - this.x1);
        this.height = Math.abs(this.y2 - this.y1);
    }

    public static class ColoredText {
        public String text;
        public int color;

        public ColoredText(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }
}

