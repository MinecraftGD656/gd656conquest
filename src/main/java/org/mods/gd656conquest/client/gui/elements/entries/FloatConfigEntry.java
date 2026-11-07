/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.elements.entries;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDTextRenderer;
import org.mods.gd656conquest.client.gui.elements.TextInputDialog;

public class FloatConfigEntry
extends GDRowRenderer {
    private float value;
    private final float defaultValue;
    private final Consumer<Float> onValueChange;
    private final String key;
    private final TextInputDialog textInputDialog;
    private final String configName;

    public FloatConfigEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String configName, String configId, String description, float initialValue, float defaultValue, Consumer<Float> onValueChange, TextInputDialog textInputDialog) {
        this(x1, y1, x2, y2, bgColor, bgAlpha, configName, configId, description, initialValue, defaultValue, onValueChange, textInputDialog, () -> true);
    }

    public FloatConfigEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String configName, String configId, String description, float initialValue, float defaultValue, Consumer<Float> onValueChange, TextInputDialog textInputDialog, Supplier<Boolean> activeCondition) {
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
        this.addColoredColumn(this.parseColoredText(this.value), 60, false, false, btn -> {
            if (this.textInputDialog != null) {
                String initialText = String.format(Locale.ROOT, "%.2f", Float.valueOf(this.value));
                this.textInputDialog.show(initialText, this.configName, newValue -> {
                    try {
                        float f;
                        this.value = f = Float.parseFloat(newValue);
                        this.updateState();
                        if (this.onValueChange != null) {
                            this.onValueChange.accept(Float.valueOf(this.value));
                        }
                    }
                    catch (NumberFormatException numberFormatException) {
                        // empty catch block
                    }
                }, this::isValidFloat);
            }
        });
        this.addColumn("\u21ba", 17, this.getResetButtonColor(), true, true, btn -> {
            if (Math.abs(this.value - this.defaultValue) < 1.0E-4f) {
                return;
            }
            this.value = this.defaultValue;
            this.updateState();
            if (this.onValueChange != null) {
                this.onValueChange.accept(Float.valueOf(this.value));
            }
        });
    }

    public String getKey() {
        return this.key;
    }

    private boolean isValidFloat(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (text.equals("-")) {
            return false;
        }
        return text.matches("^-?\\d+(\\.\\d{0,2})?$") || text.matches("^-?\\.\\d{1,2}$");
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
        return Math.abs(this.value - this.defaultValue) < 1.0E-4f ? -5592406 : BattlefieldUiTheme.palette().accent();
    }

    private List<GDTextRenderer.ColoredText> parseColoredText(float val) {
        ArrayList<GDTextRenderer.ColoredText> list = new ArrayList<GDTextRenderer.ColoredText>();
        String text = String.format(Locale.ROOT, "%.2f", Float.valueOf(val));
        int dotIndex = text.indexOf(46);
        if (dotIndex != -1) {
            String intPart = text.substring(0, dotIndex);
            list.add(new GDTextRenderer.ColoredText(" " + intPart, -1));
            list.add(new GDTextRenderer.ColoredText(".", -1));
            String decPart = text.substring(dotIndex + 1);
            list.add(new GDTextRenderer.ColoredText(decPart, -5592406));
        } else {
            list.add(new GDTextRenderer.ColoredText(" " + text, -1));
        }
        return list;
    }
}

