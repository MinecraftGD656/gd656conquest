/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.elements.entries;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDTextRenderer;
import org.mods.gd656conquest.client.gui.elements.TextInputDialog;

public class StringConfigEntry
extends GDRowRenderer {
    private String value;
    private final String defaultValue;
    private final Consumer<String> onValueChange;
    private final String key;
    private final TextInputDialog textInputDialog;
    private final String configName;

    public StringConfigEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String configName, String configId, String description, String initialValue, String defaultValue, Consumer<String> onValueChange, TextInputDialog textInputDialog) {
        this(x1, y1, x2, y2, bgColor, bgAlpha, configName, configId, description, initialValue, defaultValue, onValueChange, textInputDialog, () -> true);
    }

    public StringConfigEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String configName, String configId, String description, String initialValue, String defaultValue, Consumer<String> onValueChange, TextInputDialog textInputDialog, Supplier<Boolean> activeCondition) {
        super(x1, y1, x2, y2, bgColor, bgAlpha, false);
        this.key = configId;
        this.setActiveCondition(activeCondition);
        this.setSeparateFirstColumn(true);
        this.setHoverInfo(configName, "   " + description);
        this.value = initialValue;
        this.defaultValue = defaultValue;
        this.onValueChange = onValueChange;
        this.textInputDialog = textInputDialog;
        this.configName = configName;
        this.addNameColumn(configName, configId, -1, -5592406, true, false);
        this.addColoredColumn(this.parseColoredText(this.value), 120, false, false, btn -> {
            if (this.textInputDialog != null) {
                this.textInputDialog.show(this.value, this.configName, newValue -> {
                    this.value = newValue;
                    this.updateState();
                    if (this.onValueChange != null) {
                        this.onValueChange.accept(this.value);
                    }
                });
            }
        });
        this.addColumn("\u21ba", 17, this.getResetButtonColor(), true, true, btn -> {
            if (this.value != null && this.value.equals(this.defaultValue)) {
                return;
            }
            this.value = this.defaultValue;
            this.updateState();
            if (this.onValueChange != null) {
                this.onValueChange.accept(this.value);
            }
        });
    }

    public String getKey() {
        return this.key;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void updateState() {
        GDRowRenderer.Column resetCol;
        GDRowRenderer.Column controlCol = this.getColumn(1);
        if (controlCol != null) {
            controlCol.coloredTexts = this.parseColoredText(this.value);
            controlCol.textRenderer = null;
        }
        if ((resetCol = this.getColumn(2)) != null) {
            resetCol.color = this.getResetButtonColor();
            resetCol.text = "\u21ba";
            if (resetCol.textRenderer != null) {
                resetCol.textRenderer.setColor(resetCol.color);
                resetCol.textRenderer.setText(resetCol.text);
            }
        }
    }

    private int getResetButtonColor() {
        return this.value != null && this.value.equals(this.defaultValue) ? -5592406 : BattlefieldUiTheme.palette().accent();
    }

    private List<GDTextRenderer.ColoredText> parseColoredText(String text) {
        ArrayList<GDTextRenderer.ColoredText> list = new ArrayList<GDTextRenderer.ColoredText>();
        String processingText = " " + (text == null ? "" : text);
        Pattern pattern = Pattern.compile("(<.*?>)|(/.*?\\\\)");
        Matcher matcher = pattern.matcher(processingText);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                list.add(new GDTextRenderer.ColoredText(processingText.substring(lastEnd, matcher.start()), -1));
            }
            String match = matcher.group();
            if (matcher.group(1) != null) {
                list.add(new GDTextRenderer.ColoredText(match, BattlefieldUiTheme.palette().accent()));
            } else if (matcher.group(2) != null) {
                list.add(new GDTextRenderer.ColoredText(match, BattlefieldUiTheme.palette().accent()));
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < processingText.length()) {
            list.add(new GDTextRenderer.ColoredText(processingText.substring(lastEnd), -1));
        }
        return list;
    }
}

