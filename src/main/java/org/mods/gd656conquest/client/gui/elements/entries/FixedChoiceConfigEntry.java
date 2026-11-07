/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.elements.entries;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import org.mods.gd656conquest.client.gui.elements.ChoiceListDialog;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;

public class FixedChoiceConfigEntry
extends GDRowRenderer {
    private final List<Choice> choices;
    private int index;
    private final String defaultValue;
    private final Consumer<String> onValueChange;
    private final String key;
    private final ChoiceListDialog choiceListDialog;
    private final String configName;
    private final Predicate<Choice> extraActionVisiblePredicate;
    private final Consumer<Choice> onExtraAction;
    private final Predicate<Choice> directActionPredicate;
    private final Consumer<Choice> onDirectAction;
    private final String extraActionText;
    private final int extraActionColor;
    private final int extraActionWidth;

    public FixedChoiceConfigEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String configName, String configId, String description, String initialValue, String defaultValue, List<Choice> choices, Consumer<String> onValueChange, ChoiceListDialog choiceListDialog) {
        this(x1, y1, x2, y2, bgColor, bgAlpha, configName, configId, description, initialValue, defaultValue, choices, onValueChange, choiceListDialog, () -> true, null, null, "", -65536, 17, null, null);
    }

    public FixedChoiceConfigEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String configName, String configId, String description, String initialValue, String defaultValue, List<Choice> choices, Consumer<String> onValueChange, ChoiceListDialog choiceListDialog, Supplier<Boolean> activeCondition) {
        this(x1, y1, x2, y2, bgColor, bgAlpha, configName, configId, description, initialValue, defaultValue, choices, onValueChange, choiceListDialog, activeCondition, null, null, "", -65536, 17, null, null);
    }

    public FixedChoiceConfigEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String configName, String configId, String description, String initialValue, String defaultValue, List<Choice> choices, Consumer<String> onValueChange, ChoiceListDialog choiceListDialog, Supplier<Boolean> activeCondition, Predicate<Choice> extraActionVisiblePredicate, Consumer<Choice> onExtraAction, String extraActionText, int extraActionColor, int extraActionWidth) {
        this(x1, y1, x2, y2, bgColor, bgAlpha, configName, configId, description, initialValue, defaultValue, choices, onValueChange, choiceListDialog, activeCondition, extraActionVisiblePredicate, onExtraAction, extraActionText, extraActionColor, extraActionWidth, null, null);
    }

    public FixedChoiceConfigEntry(int x1, int y1, int x2, int y2, int bgColor, float bgAlpha, String configName, String configId, String description, String initialValue, String defaultValue, List<Choice> choices, Consumer<String> onValueChange, ChoiceListDialog choiceListDialog, Supplier<Boolean> activeCondition, Predicate<Choice> extraActionVisiblePredicate, Consumer<Choice> onExtraAction, String extraActionText, int extraActionColor, int extraActionWidth, Predicate<Choice> directActionPredicate, Consumer<Choice> onDirectAction) {
        super(x1, y1, x2, y2, bgColor, bgAlpha, false);
        this.key = configId;
        this.setActiveCondition(activeCondition);
        this.setSeparateFirstColumn(true);
        this.setHoverInfo(configName, "   " + description);
        this.choices = choices;
        this.defaultValue = defaultValue;
        this.onValueChange = onValueChange;
        this.choiceListDialog = choiceListDialog;
        this.configName = configName;
        this.extraActionVisiblePredicate = extraActionVisiblePredicate;
        this.onExtraAction = onExtraAction;
        this.directActionPredicate = directActionPredicate;
        this.onDirectAction = onDirectAction;
        this.extraActionText = extraActionText == null ? "" : extraActionText;
        this.extraActionColor = extraActionColor;
        this.extraActionWidth = extraActionWidth <= 0 ? 17 : extraActionWidth;
        this.index = this.resolveIndex(initialValue, defaultValue);
        this.addNameColumn(configName, configId, -1, -5592406, true, false);
        this.addColumn(this.getValueText(), 60, BattlefieldUiTheme.palette().accent(), false, true, btn -> this.openDialog());
        this.addColumn("\u21ba", 17, this.getResetButtonColor(), true, true, btn -> {
            if (!this.hasDefaultChoice()) {
                return;
            }
            if (this.getCurrentValue().equals(this.defaultValue)) {
                return;
            }
            this.index = this.resolveIndex(this.defaultValue, this.getCurrentValue());
            this.updateState();
            if (this.onValueChange != null) {
                this.onValueChange.accept(this.getCurrentValue());
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

    private int resolveIndex(String value, String fallback) {
        if (this.choices != null) {
            int i;
            for (i = 0; i < this.choices.size(); ++i) {
                if (!this.choices.get(i).value().equals(value)) continue;
                return i;
            }
            for (i = 0; i < this.choices.size(); ++i) {
                if (!this.choices.get(i).value().equals(fallback)) continue;
                return i;
            }
        }
        return 0;
    }

    private String getCurrentValue() {
        return this.choices.get(this.index).value();
    }

    private String getValueText() {
        return this.choices.get(this.index).label();
    }

    private void updateState() {
        GDRowRenderer.Column resetCol;
        GDRowRenderer.Column controlCol = this.getColumn(1);
        if (controlCol != null) {
            controlCol.text = this.getValueText();
            controlCol.color = BattlefieldUiTheme.palette().accent();
            if (controlCol.textRenderer != null) {
                controlCol.textRenderer.setText(controlCol.text);
                controlCol.textRenderer.setColor(controlCol.color);
            }
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

    private void openDialog() {
        if (this.choiceListDialog != null) {
            this.choiceListDialog.show(this.getCurrentValue(), this.configName, this.choices, newValue -> {
                if (newValue != null && newValue.equals(this.getCurrentValue())) {
                    return;
                }
                this.index = this.resolveIndex((String)newValue, this.defaultValue);
                this.updateState();
                if (this.onValueChange != null) {
                    this.onValueChange.accept(this.getCurrentValue());
                }
            }, null, this.extraActionVisiblePredicate, this.onExtraAction, this.extraActionText, this.extraActionColor, this.extraActionWidth, this.directActionPredicate, this.onDirectAction, null, null, null);
        }
    }

    private int getResetButtonColor() {
        if (!this.hasDefaultChoice()) {
            return -5592406;
        }
        return this.getCurrentValue().equals(this.defaultValue) ? -5592406 : BattlefieldUiTheme.palette().accent();
    }

    private boolean hasDefaultChoice() {
        if (this.choices == null || this.choices.isEmpty()) {
            return false;
        }
        for (Choice choice : this.choices) {
            if (!choice.value().equals(this.defaultValue)) continue;
            return true;
        }
        return false;
    }

    public static class Choice {
        private final String value;
        private final String label;
        private final String secondaryLabel;

        public Choice(String value, String label) {
            this(value, label, "");
        }

        public Choice(String value, String label, String secondaryLabel) {
            this.value = value;
            this.label = label;
            this.secondaryLabel = secondaryLabel == null ? "" : secondaryLabel;
        }

        public String value() {
            return this.value;
        }

        public String label() {
            return this.label;
        }

        public String secondaryLabel() {
            return this.secondaryLabel;
        }
    }
}

