/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.elements.entries;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import org.mods.gd656conquest.client.gui.elements.ColorPickerDialog;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDTextRenderer;
import org.mods.gd656conquest.client.gui.elements.TextInputDialog;

public class HexColorConfigEntry
extends GDRowRenderer {
    private String value;
    private final String defaultValue;
    private final Consumer<String> onValueChange;
    private final String key;
    private final TextInputDialog textInputDialog;
    private final ColorPickerDialog colorPickerDialog;
    private final String configName;
    private static final Pattern HEX_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public HexColorConfigEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String configName, String configId, String description, String initialValue, String defaultValue, Consumer<String> onValueChange, TextInputDialog textInputDialog, ColorPickerDialog colorPickerDialog) {
        this(x1, y1, x2, y2, bgColor, bgAlpha, configName, configId, description, initialValue, defaultValue, onValueChange, textInputDialog, colorPickerDialog, () -> true);
    }

    public HexColorConfigEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String configName, String configId, String description, String initialValue, String defaultValue, Consumer<String> onValueChange, TextInputDialog textInputDialog, ColorPickerDialog colorPickerDialog, Supplier<Boolean> activeCondition) {
        super(x1, y1, x2, y2, bgColor, bgAlpha, false);
        this.key = configId;
        this.setActiveCondition(activeCondition);
        this.setSeparateFirstColumn(true);
        this.setHoverInfo(configName, "   " + description);
        this.value = initialValue;
        this.defaultValue = defaultValue;
        this.onValueChange = onValueChange;
        this.textInputDialog = textInputDialog;
        this.colorPickerDialog = colorPickerDialog;
        this.configName = configName;
        this.addNameColumn(configName, configId, -1, -5592406, true, false);
        this.addColoredColumn(this.parseColoredText(this.value), 47, false, false, btn -> this.openDialog());
        this.addCustomColumn(17, btn -> this.openColorPicker(), (guiGraphics, x, y, w, h) -> {
            int size = 13;
            int bx = x + (w - size) / 2;
            int by = y + (h - size) / 2;
            int color = -1;
            try {
                if (this.value != null && this.value.startsWith("#") && this.value.length() == 7) {
                    int rgb = Integer.parseInt(this.value.substring(1), 16);
                    color = 0xFF000000 | rgb;
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
            guiGraphics.fill(bx, by, bx + size, by + size, color);
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

    private void openDialog() {
        if (this.textInputDialog != null) {
            this.textInputDialog.show(this.value, this.configName, newValue -> {
                this.value = newValue;
                this.updateState();
                if (this.onValueChange != null) {
                    this.onValueChange.accept(this.value);
                }
            }, input -> {
                if (input == null || input.length() != 7) {
                    return false;
                }
                return HEX_PATTERN.matcher((CharSequence)input).matches();
            });
        }
    }

    private void openColorPicker() {
        if (this.colorPickerDialog != null) {
            this.colorPickerDialog.show(this.value, this.configName, newValue -> {
                this.value = newValue;
                this.updateState();
                if (this.onValueChange != null) {
                    this.onValueChange.accept(this.value);
                }
            }, null);
        } else {
            this.openDialog();
        }
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
        if ((resetCol = this.getColumn(3)) != null) {
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
        list.add(new GDTextRenderer.ColoredText(processingText, BattlefieldUiTheme.palette().accent()));
        return list;
    }
}

