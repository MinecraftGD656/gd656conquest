/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.gui.elements;

import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.gui.elements.GDRowRenderer;
import org.mods.gd656conquest.client.gui.elements.GDTextRenderer;
import org.mods.gd656conquest.client.gui.elements.entries.FixedChoiceConfigEntry;

public class ChoiceListDialog {
    private static final int ROW_HEIGHT = 19;
    private static final int ROW_GAP = 1;
    private static final int LIST_WIDTH = 155;
    private static final int PREVIEW_WIDTH = 64;
    private static final int PREVIEW_GAP = 8;
    private static final int PANEL_WIDTH = 227;
    private static final int BUTTON_HEIGHT = 17;
    private static final int INPUT_HEIGHT = 17;
    private static final int ICON_COLUMN_WIDTH = 17;
    private static final int PAD = 8;
    private static final int LIST_HEIGHT = 142;
    private static final int PANEL_HEIGHT = 192;
    private final Minecraft minecraft;
    private Consumer<String> onConfirm;
    private Runnable onCancel;
    private boolean visible = false;
    private String filterText = "";
    private int cursorPosition = 0;
    private int selectionAnchor = -1;
    private final ArrayDeque<EditState> undoStack = new ArrayDeque();
    private int displayOffset = 0;
    private String selectedValue = "";
    private final List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<FixedChoiceConfigEntry.Choice>();
    private final List<FixedChoiceConfigEntry.Choice> filteredChoices = new ArrayList<FixedChoiceConfigEntry.Choice>();
    private final List<GDRowRenderer> rowRenderers = new ArrayList<GDRowRenderer>();
    private Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate;
    private Consumer<FixedChoiceConfigEntry.Choice> onExtraAction;
    private Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate;
    private Consumer<FixedChoiceConfigEntry.Choice> onDirectAction;
    private Predicate<FixedChoiceConfigEntry.Choice> selectablePredicate;
    private Function<FixedChoiceConfigEntry.Choice, RowIcon> iconProvider;
    private Consumer<FixedChoiceConfigEntry.Choice> onHoverChoice;
    private Predicate<FixedChoiceConfigEntry.Choice> disabledPredicate;
    private String extraActionText = "";
    private String primaryActionText = "";
    private int extraActionColor = -65536;
    private int extraActionWidth = 17;
    private double scrollY = 0.0;
    private double targetScrollY = 0.0;
    private int horizontalOffset = 0;
    private float inputHoverProgress = 0.0f;
    private long lastFrameTime;
    private GDButton cancelButton;
    private GDTextRenderer titleRenderer;

    public ChoiceListDialog(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void show(String initialValue, String title, List<FixedChoiceConfigEntry.Choice> options, Consumer<String> onConfirm, Runnable onCancel) {
        this.show(initialValue, title, options, onConfirm, onCancel, null, null, "", -65536, 17, null, null, null, null, null, null, null);
    }

    public void show(String initialValue, String title, List<FixedChoiceConfigEntry.Choice> options, Consumer<String> onConfirm, Runnable onCancel, Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate, Consumer<FixedChoiceConfigEntry.Choice> onExtraAction, String extraActionText, int extraActionColor, int extraActionWidth, Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate, Consumer<FixedChoiceConfigEntry.Choice> onDirectAction, Predicate<FixedChoiceConfigEntry.Choice> selectablePredicate, Function<FixedChoiceConfigEntry.Choice, RowIcon> iconProvider) {
        this.show(initialValue, title, options, onConfirm, onCancel, extraActionVisiblePredicate, onExtraAction, extraActionText, extraActionColor, extraActionWidth, directActionPredicate, onDirectAction, selectablePredicate, iconProvider, null, null, null);
    }

    public void show(String initialValue, String title, List<FixedChoiceConfigEntry.Choice> options, Consumer<String> onConfirm, Runnable onCancel, Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate, Consumer<FixedChoiceConfigEntry.Choice> onExtraAction, String extraActionText, int extraActionColor, int extraActionWidth, Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate, Consumer<FixedChoiceConfigEntry.Choice> onDirectAction, Predicate<FixedChoiceConfigEntry.Choice> selectablePredicate, Function<FixedChoiceConfigEntry.Choice, RowIcon> iconProvider, Consumer<FixedChoiceConfigEntry.Choice> onHoverChoice) {
        this.show(initialValue, title, options, onConfirm, onCancel, extraActionVisiblePredicate, onExtraAction, extraActionText, extraActionColor, extraActionWidth, directActionPredicate, onDirectAction, selectablePredicate, iconProvider, onHoverChoice, null, null);
    }

    public void show(String initialValue, String title, List<FixedChoiceConfigEntry.Choice> options, Consumer<String> onConfirm, Runnable onCancel, Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate, Consumer<FixedChoiceConfigEntry.Choice> onExtraAction, String extraActionText, int extraActionColor, int extraActionWidth, Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate, Consumer<FixedChoiceConfigEntry.Choice> onDirectAction, Predicate<FixedChoiceConfigEntry.Choice> selectablePredicate, Function<FixedChoiceConfigEntry.Choice, RowIcon> iconProvider, Consumer<FixedChoiceConfigEntry.Choice> onHoverChoice, Predicate<FixedChoiceConfigEntry.Choice> disabledPredicate) {
        this.show(initialValue, title, options, onConfirm, onCancel, extraActionVisiblePredicate, onExtraAction, extraActionText, extraActionColor, extraActionWidth, directActionPredicate, onDirectAction, selectablePredicate, iconProvider, onHoverChoice, disabledPredicate, null);
    }

    public void show(String initialValue, String title, List<FixedChoiceConfigEntry.Choice> options, Consumer<String> onConfirm, Runnable onCancel, Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate, Consumer<FixedChoiceConfigEntry.Choice> onExtraAction, String extraActionText, int extraActionColor, int extraActionWidth, Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate, Consumer<FixedChoiceConfigEntry.Choice> onDirectAction, Predicate<FixedChoiceConfigEntry.Choice> selectablePredicate, Function<FixedChoiceConfigEntry.Choice, RowIcon> iconProvider, Consumer<FixedChoiceConfigEntry.Choice> onHoverChoice, Predicate<FixedChoiceConfigEntry.Choice> disabledPredicate, String primaryActionText) {
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.extraActionVisiblePredicate = extraActionVisiblePredicate;
        this.onExtraAction = onExtraAction;
        this.directActionPredicate = directActionPredicate;
        this.onDirectAction = onDirectAction;
        this.selectablePredicate = selectablePredicate;
        this.iconProvider = iconProvider;
        this.onHoverChoice = onHoverChoice;
        this.disabledPredicate = disabledPredicate;
        this.extraActionText = extraActionText == null ? "" : extraActionText;
        this.primaryActionText = primaryActionText == null ? "" : primaryActionText;
        this.extraActionColor = extraActionColor;
        this.extraActionWidth = extraActionWidth <= 0 ? 17 : extraActionWidth;
        this.visible = true;
        this.lastFrameTime = System.currentTimeMillis();
        this.inputHoverProgress = 0.0f;
        this.filterText = "";
        this.cursorPosition = 0;
        this.selectionAnchor = -1;
        this.undoStack.clear();
        this.displayOffset = 0;
        this.scrollY = 0.0;
        this.targetScrollY = 0.0;
        this.horizontalOffset = 0;
        this.choices.clear();
        if (options != null) {
            this.choices.addAll(options);
        }
        this.selectedValue = this.resolveValue(initialValue);
        this.applyFilter(true);
        this.cancelButton = new GDButton(0, 0, 227, 17, Component.literal("\u5173\u95ed"), btn -> this.cancel());
        if (this.titleRenderer == null) {
            this.titleRenderer = new GDTextRenderer(title, 0, 0, 0, 0, 1.0f, BattlefieldUiTheme.palette().accent(), false);
        } else {
            this.titleRenderer.setText(title);
            this.titleRenderer.setColor(BattlefieldUiTheme.palette().accent());
        }
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setHorizontalOffset(int horizontalOffset) {
        this.horizontalOffset = horizontalOffset;
    }

    public void hide() {
        this.visible = false;
    }

    public void refreshContent(String selectedValue, List<FixedChoiceConfigEntry.Choice> options, Consumer<String> onConfirm, Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate, Consumer<FixedChoiceConfigEntry.Choice> onExtraAction, String extraActionText, int extraActionColor, int extraActionWidth, Predicate<FixedChoiceConfigEntry.Choice> selectablePredicate, Function<FixedChoiceConfigEntry.Choice, RowIcon> iconProvider, Predicate<FixedChoiceConfigEntry.Choice> disabledPredicate, String primaryActionText) {
        this.onConfirm = onConfirm;
        this.extraActionVisiblePredicate = extraActionVisiblePredicate;
        this.onExtraAction = onExtraAction;
        this.selectablePredicate = selectablePredicate;
        this.iconProvider = iconProvider;
        this.disabledPredicate = disabledPredicate;
        this.extraActionText = extraActionText == null ? "" : extraActionText;
        this.primaryActionText = primaryActionText == null ? "" : primaryActionText;
        this.extraActionColor = extraActionColor;
        this.extraActionWidth = extraActionWidth <= 0 ? 17 : extraActionWidth;
        this.choices.clear();
        if (options != null) {
            this.choices.addAll(options);
        }
        this.selectedValue = this.resolveValue(selectedValue);
        this.applyFilter(false);
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, 0.0f, 500.0f);
        int screenWidth = org.mods.gd656killicon.client.gui.theme.BattlefieldPageViewport.width();
        int screenHeight = org.mods.gd656killicon.client.gui.theme.BattlefieldPageViewport.height();
        guiGraphics.fill(0, 0, screenWidth, screenHeight, -2008791996);
        int centerX = (screenWidth - 227) / 2 + this.horizontalOffset;
        int centerY = (screenHeight - 192) / 2 + 20;
        int fontHeight = this.minecraft.font.lineHeight;
        int titleY = centerY - fontHeight - 8 + 6;
        int buttonsY = centerY + 192 - 17;
        guiGraphics.fill(centerX - 8, titleY - 8, centerX + 227 + 8, buttonsY + 17 + 8, BattlefieldUiTheme.palette().header());
        if (this.titleRenderer != null) {
            this.titleRenderer.setX1(centerX);
            this.titleRenderer.setY1(titleY);
            this.titleRenderer.setX2(centerX + 227);
            this.titleRenderer.setY2(titleY + fontHeight);
            this.titleRenderer.render(guiGraphics, partialTick);
        }
        int previewX = centerX;
        int previewY = buttonsY - 8 - 17 - 8 - 142;
        int previewW = 64;
        int previewH = 142;
        int listX = centerX + 64 + 8;
        int listY = previewY;
        int listW = 155;
        int listH = 142;
        int inputX = centerX;
        int inputY = buttonsY - 8 - 17;
        long currentTime = System.currentTimeMillis();
        float dt = (float)(currentTime - this.lastFrameTime) / 1000.0f;
        this.lastFrameTime = currentTime;
        boolean hoveringInput = mouseX >= inputX && mouseX <= inputX + 227 && mouseY >= inputY && mouseY <= inputY + 17;
        this.inputHoverProgress = hoveringInput ? Math.min(1.0f, this.inputHoverProgress + dt * 2.0f) : Math.max(0.0f, this.inputHoverProgress - dt * 2.0f);
        this.updateScroll(dt, listH);
        int listBgColor = BattlefieldUiTheme.palette().panel();
        guiGraphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, listBgColor);
        guiGraphics.fill(listX, listY, listX + listW, listY + listH, listBgColor);
        FixedChoiceConfigEntry.Choice hoveredChoice = this.getHoveredChoice(mouseX, mouseY, listX, listY, listW, listH);
        if (this.onHoverChoice != null) {
            this.onHoverChoice.accept(hoveredChoice);
        }
        this.renderHoverPreview(guiGraphics, hoveredChoice, previewX, previewY, previewW, previewH);
        this.renderList(guiGraphics, mouseX, mouseY, partialTick, listX, listY, listX + listW, listY + listH);
        int inputBgColor = BattlefieldUiTheme.palette().panel();
        guiGraphics.fill(inputX, inputY, inputX + 227, inputY + 17, inputBgColor);
        this.renderHoverTrail(guiGraphics, inputX, inputY, 227, 17, this.inputHoverProgress);
        this.renderInputText(guiGraphics, inputX, inputY, 227, 17);
        if (this.cancelButton != null) {
            this.cancelButton.setWidth(227);
            this.cancelButton.setX(centerX);
            this.cancelButton.setY(buttonsY);
            this.cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible) {
            return false;
        }
        if (this.cancelButton != null && this.cancelButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int screenWidth = org.mods.gd656killicon.client.gui.theme.BattlefieldPageViewport.width();
        int screenHeight = org.mods.gd656killicon.client.gui.theme.BattlefieldPageViewport.height();
        int centerX = (screenWidth - 227) / 2 + this.horizontalOffset;
        int centerY = (screenHeight - 192) / 2 + 20;
        int buttonsY = centerY + 192 - 17;
        int inputY = buttonsY - 8 - 17;
        int inputX = centerX;
        int listY = inputY - 8 - 142;
        int listX = centerX + 64 + 8;
        if (mouseX >= (double)inputX && mouseX <= (double)(inputX + 227) && mouseY >= (double)inputY && mouseY <= (double)(inputY + 17)) {
            this.setCursorByMouse((int)mouseX, inputX);
            this.selectionAnchor = -1;
            return true;
        }
        if (mouseX >= (double)listX && mouseX <= (double)(listX + 155) && mouseY >= (double)listY && mouseY <= (double)(listY + 142)) {
            double adjustedMouseY = mouseY + this.scrollY;
            int selectWidth = 30;
            for (int i = 0; i < this.filteredChoices.size(); ++i) {
                FixedChoiceConfigEntry.Choice choice = this.filteredChoices.get(i);
                boolean hasExtraAction = this.hasExtraAction(choice);
                int extraWidth = hasExtraAction ? this.extraActionWidth : 0;
                int selectX1 = listX + 155 - selectWidth - extraWidth;
                int extraX1 = listX + 155 - extraWidth;
                int rowTop = listY + i * 20;
                int rowBottom = rowTop + 19;
                if (adjustedMouseY < (double)rowTop || adjustedMouseY > (double)rowBottom) continue;
                if (button == 0 && this.isDirectAction(choice)) {
                    if (this.onDirectAction != null) {
                        this.onDirectAction.accept(choice);
                    }
                    return true;
                }
                if (button == 0 && hasExtraAction && mouseX >= (double)extraX1) {
                    if (this.onExtraAction != null) {
                        this.onExtraAction.accept(choice);
                    }
                    return true;
                }
                if (button != 0 || !(mouseX >= (double)selectX1) || !this.isSelectable(choice)) break;
                this.selectChoice(choice);
                return true;
            }
            return true;
        }
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!this.visible) {
            return false;
        }
        int screenWidth = org.mods.gd656killicon.client.gui.theme.BattlefieldPageViewport.width();
        int screenHeight = org.mods.gd656killicon.client.gui.theme.BattlefieldPageViewport.height();
        int centerX = (screenWidth - 227) / 2 + this.horizontalOffset;
        int centerY = (screenHeight - 192) / 2 + 20;
        int buttonsY = centerY + 192 - 17;
        int listY = buttonsY - 8 - 17 - 8 - 142;
        int listX = centerX + 64 + 8;
        if (mouseX >= (double)listX && mouseX <= (double)(listX + 155) && mouseY >= (double)listY && mouseY <= (double)(listY + 142)) {
            this.targetScrollY -= delta * 40.0;
            return true;
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.visible) {
            return false;
        }
        if (SharedConstants.isAllowedChatCharacter(codePoint)) {
            this.replaceSelection(String.valueOf(codePoint));
            this.applyFilter(true);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean shiftDown;
        if (!this.visible) {
            return false;
        }
        boolean controlDown = Screen.hasControlDown() || (modifiers & 2) != 0;
        boolean bl = shiftDown = Screen.hasShiftDown() || (modifiers & 1) != 0;
        if (controlDown && keyCode == 65) {
            this.selectionAnchor = 0;
            this.cursorPosition = this.filterText.length();
            return true;
        }
        if (controlDown && keyCode == 67) {
            if (this.hasSelection() && this.minecraft != null && this.minecraft.keyboardHandler != null) {
                this.minecraft.keyboardHandler.setClipboard(this.filterText.substring(this.getSelectionStart(), this.getSelectionEnd()));
            }
            return true;
        }
        if (controlDown && keyCode == 90) {
            this.undoEdit();
            this.applyFilter(true);
            return true;
        }
        if (controlDown && keyCode == 86 || shiftDown && keyCode == 260) {
            this.insertClipboardText();
            this.applyFilter(true);
            return true;
        }
        if (keyCode == 259) {
            if (this.hasSelection()) {
                this.deleteSelection();
                this.applyFilter(true);
                return true;
            }
            if (this.cursorPosition > 0) {
                this.pushUndoState();
                this.filterText = this.filterText.substring(0, this.cursorPosition - 1) + this.filterText.substring(this.cursorPosition);
                --this.cursorPosition;
                this.applyFilter(true);
            }
            return true;
        }
        if (keyCode == 261) {
            if (this.hasSelection()) {
                this.deleteSelection();
                this.applyFilter(true);
                return true;
            }
            if (this.cursorPosition < this.filterText.length()) {
                this.pushUndoState();
                this.filterText = this.filterText.substring(0, this.cursorPosition) + this.filterText.substring(this.cursorPosition + 1);
                this.applyFilter(true);
            }
            return true;
        }
        if (keyCode == 263) {
            if (shiftDown) {
                if (this.selectionAnchor < 0) {
                    this.selectionAnchor = this.cursorPosition;
                }
            } else {
                this.selectionAnchor = -1;
            }
            if (this.cursorPosition > 0) {
                --this.cursorPosition;
            }
            return true;
        }
        if (keyCode == 262) {
            if (shiftDown) {
                if (this.selectionAnchor < 0) {
                    this.selectionAnchor = this.cursorPosition;
                }
            } else {
                this.selectionAnchor = -1;
            }
            if (this.cursorPosition < this.filterText.length()) {
                ++this.cursorPosition;
            }
            return true;
        }
        if (keyCode == 268) {
            if (shiftDown) {
                if (this.selectionAnchor < 0) {
                    this.selectionAnchor = this.cursorPosition;
                }
            } else {
                this.selectionAnchor = -1;
            }
            this.cursorPosition = 0;
            return true;
        }
        if (keyCode == 269) {
            if (shiftDown) {
                if (this.selectionAnchor < 0) {
                    this.selectionAnchor = this.cursorPosition;
                }
            } else {
                this.selectionAnchor = -1;
            }
            this.cursorPosition = this.filterText.length();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            this.submitSelectionAndClose();
            return true;
        }
        if (keyCode == 256) {
            this.cancel();
            return true;
        }
        return true;
    }

    private void renderList(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int x1, int y1, int x2, int y2) {
        if (y2 <= y1) {
            return;
        }
        org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(guiGraphics,x1, y1, x2, y2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0, -this.scrollY, 0.0);
        for (int i = 0; i < this.filteredChoices.size(); ++i) {
            FixedChoiceConfigEntry.Choice choice = this.filteredChoices.get(i);
            int rowTop = y1 + i * 20;
            int rowBottom = rowTop + 19;
            float actualTop = (float)rowTop - (float)this.scrollY;
            float actualBottom = (float)rowBottom - (float)this.scrollY;
            if (!(actualBottom > (float)y1) || !(actualTop < (float)y2)) continue;
            this.renderChoiceRow(guiGraphics, mouseX, (int)((double)mouseY + this.scrollY), partialTick, i, choice, x1, rowTop, x2, rowBottom);
        }
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
    }

    private void renderChoiceRow(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int index, FixedChoiceConfigEntry.Choice choice, int x1, int y1, int x2, int y2) {
        int secondColor;
        int firstColor;
        boolean toggleOnlyAction;
        float alpha;
        int rowBgColor = 5267568;
        float f = alpha = index % 2 == 1 ? 0.1f : 0.3f;
        while (this.rowRenderers.size() <= index) {
            this.rowRenderers.add(new GDRowRenderer(x1, y1, x2, y2, rowBgColor, alpha, false));
        }
        GDRowRenderer renderer = this.rowRenderers.get(index);
        renderer.setBounds(x1, y1, x2, y2);
        renderer.setBackgroundColor(rowBgColor);
        renderer.setBackgroundAlpha(alpha);
        renderer.resetColumnConfig();
        boolean selected = choice.value().equals(this.selectedValue);
        boolean selectable = this.isSelectable(choice);
        boolean disabled = this.isDisabled(choice);
        boolean bl = toggleOnlyAction = this.hasExtraAction(choice) && !selectable;
        firstColor = selectable || toggleOnlyAction ? (selected ? BattlefieldUiTheme.palette().accent() : -1) : (disabled ? -11908534 : -5592406);
        secondColor = selectable ? (selected ? -5592406 : -1) : (toggleOnlyAction ? this.extraActionColor : (disabled ? -11908534 : -5592406));
        if (this.isDirectAction(choice)) {
            this.addRowIconColumn(renderer, choice);
            renderer.addColumn(" " + choice.label(), -1, BattlefieldUiTheme.palette().accent(), true, true, btn -> {
                if (this.onDirectAction != null) {
                    this.onDirectAction.accept(choice);
                }
            });
            renderer.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        this.addRowIconColumn(renderer, choice);
        if (choice.secondaryLabel() != null && !choice.secondaryLabel().isBlank()) {
            int secondaryLabelColor = disabled && !toggleOnlyAction ? -11908534 : -6641992;
            renderer.addNameColumn(choice.label(), choice.secondaryLabel(), firstColor, secondaryLabelColor, true, false);
        } else {
            renderer.addColumn(" " + choice.label(), -1, firstColor, true, false, null);
        }
        if (this.hasExtraAction(choice) && !selectable) {
            renderer.addColumn(this.extraActionText, this.extraActionWidth, this.extraActionColor, true, true, btn -> {
                if (this.onExtraAction != null) {
                    this.onExtraAction.accept(choice);
                }
            });
        } else {
            renderer.addColumn(selectable ? this.resolvePrimaryActionText() : (disabled ? I18n.get("gd656conquest.client.gui.choice.disabled", new Object[0]) : I18n.get("gd656conquest.client.gui.choice.locked", new Object[0])), 30, secondColor, false, true, selectable ? btn -> this.selectChoice(choice) : null);
        }
        if (this.hasExtraAction(choice) && selectable) {
            renderer.addColumn(this.extraActionText, this.extraActionWidth, this.extraActionColor, true, true, btn -> {
                if (this.onExtraAction != null) {
                    this.onExtraAction.accept(choice);
                }
            });
        }
        renderer.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHoverPreview(GuiGraphics guiGraphics, FixedChoiceConfigEntry.Choice hoveredChoice, int previewX, int previewY, int previewW, int previewH) {
        if (hoveredChoice == null || this.iconProvider == null) {
            return;
        }
        RowIcon icon = this.iconProvider.apply(hoveredChoice);
        if (icon == null || icon.isEmpty()) {
            return;
        }
        if (icon.itemStack() != null && !icon.itemStack().isEmpty()) {
            float scale = 2.5f;
            int iconSize = Math.round(16.0f * scale);
            int itemX = previewX + (previewW - iconSize) / 2;
            int itemY = previewY + (previewH - iconSize) / 2 - 6;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(itemX, itemY, 0.0f);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.renderItem(icon.itemStack(), 0, 0);
            guiGraphics.pose().popPose();
            return;
        }
        if (icon.texture() != null) {
            int size = Math.min(40, Math.max(24, previewW - 16));
            int textureX = previewX + (previewW - size) / 2;
            int textureY = previewY + (previewH - size) / 2 - 4;
            guiGraphics.blit(icon.texture(), textureX, textureY, 0.0f, 0.0f, size, size, size, size);
        }
    }

    private FixedChoiceConfigEntry.Choice getHoveredChoice(int mouseX, int mouseY, int listX, int listY, int listW, int listH) {
        if (mouseX < listX || mouseX > listX + listW || mouseY < listY || mouseY > listY + listH) {
            return null;
        }
        double adjustedMouseY = (double)mouseY + this.scrollY;
        for (int i = 0; i < this.filteredChoices.size(); ++i) {
            int rowTop = listY + i * 20;
            int rowBottom = rowTop + 19;
            if (!(adjustedMouseY >= (double)rowTop) || !(adjustedMouseY <= (double)rowBottom)) continue;
            return this.filteredChoices.get(i);
        }
        return null;
    }

    private void addRowIconColumn(GDRowRenderer renderer, FixedChoiceConfigEntry.Choice choice) {
        RowIcon icon;
        RowIcon rowIcon = icon = this.iconProvider == null ? null : this.iconProvider.apply(choice);
        if (icon == null || icon.isEmpty()) {
            return;
        }
        if (icon.itemStack() != null && !icon.itemStack().isEmpty()) {
            renderer.addIconColumn(icon.itemStack(), 17, true, true, null);
            return;
        }
        if (icon.texture() != null) {
            renderer.addCustomColumn(17, null, (graphics, x, y, width, height) -> {
                int iconSize = Math.min(16, Math.max(8, height - 2));
                int iconX = x + (width - iconSize) / 2;
                int iconY = y + (height - iconSize) / 2;
                graphics.blit(icon.texture(), iconX, iconY, 0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize);
            });
        }
    }

    private void renderInputText(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        int cx;
        org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(guiGraphics,x, y, x + w, y + h);
        int textWidth = BattlefieldUiTheme.textWidth(this.filterText);
        int boxInnerWidth = w - 4;
        String textBeforeCursor = this.filterText.substring(0, this.cursorPosition);
        int cursorXRel = BattlefieldUiTheme.textWidth(textBeforeCursor);
        if (cursorXRel < this.displayOffset) {
            this.displayOffset = cursorXRel;
        } else if (cursorXRel > this.displayOffset + boxInnerWidth) {
            this.displayOffset = cursorXRel - boxInnerWidth;
        }
        if (textWidth < boxInnerWidth) {
            this.displayOffset = 0;
        }
        int drawX = x + 2 - this.displayOffset;
        int drawY = y + (h - this.minecraft.font.lineHeight) / 2;
        if (this.hasSelection()) {
            int selStartRel = BattlefieldUiTheme.textWidth(this.filterText.substring(0, this.getSelectionStart()));
            int selEndRel = BattlefieldUiTheme.textWidth(this.filterText.substring(0, this.getSelectionEnd()));
            int sx = drawX + selStartRel;
            int ex = drawX + selEndRel;
            guiGraphics.fill(sx, drawY - 1, ex, drawY + this.minecraft.font.lineHeight + 1, (BattlefieldUiTheme.palette().accent() & 0xffffff) | 0x66000000);
        }
        BattlefieldUiTheme.drawText(guiGraphics, this.filterText, drawX, drawY, -1);
        if (System.currentTimeMillis() / 500L % 2L == 0L && (cx = drawX + cursorXRel) >= x && cx <= x + w) {
            guiGraphics.fill(cx, drawY - 1, cx + 1, drawY + this.minecraft.font.lineHeight + 1, -1);
        }
        guiGraphics.disableScissor();
    }

    private void renderHoverTrail(GuiGraphics guiGraphics, int x, int y, int w, int h, float progress) {
        float segLen;
        float rem;
        if (progress <= 0.001f) {
            return;
        }
        int color = BattlefieldUiTheme.palette().accent();
        float totalLength = w + h + w + h;
        float currentLength = totalLength * this.easeOut(progress);
        float drawn = 0.0f;
        if (currentLength > 0.0f) {
            float segLen2 = Math.min((float)w, currentLength);
            guiGraphics.fill(x, y + h - 1, x + (int)segLen2, y + h, color);
            drawn += (float)w;
        }
        if (currentLength > drawn) {
            rem = currentLength - drawn;
            segLen = Math.min((float)h, rem);
            guiGraphics.fill(x + w - 1, y + h - (int)segLen, x + w, y + h, color);
            drawn += (float)h;
        }
        if (currentLength > drawn) {
            rem = currentLength - drawn;
            segLen = Math.min((float)w, rem);
            guiGraphics.fill(x + w - (int)segLen, y, x + w, y + 1, color);
            drawn += (float)w;
        }
        if (currentLength > drawn) {
            rem = currentLength - drawn;
            segLen = Math.min((float)h, rem);
            guiGraphics.fill(x, y, x + 1, y + (int)segLen, color);
        }
    }

    private float easeOut(float t) {
        return 1.0f - (float)Math.pow(1.0f - t, 3.0);
    }

    private void setCursorByMouse(int mouseX, int inputX) {
        int mouseOffset = mouseX - (inputX + 2) + this.displayOffset;
        if (mouseOffset <= 0) {
            this.cursorPosition = 0;
            return;
        }
        int pos = 0;
        int i = 1;
        while (i <= this.filterText.length()) {
            int width = BattlefieldUiTheme.textWidth(this.filterText.substring(0, i));
            if (width >= mouseOffset) {
                pos = i;
                break;
            }
            pos = i++;
        }
        this.cursorPosition = pos;
    }

    private void insertClipboardText() {
        if (this.minecraft == null || this.minecraft.keyboardHandler == null) {
            return;
        }
        String clipboard = this.minecraft.keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isEmpty()) {
            return;
        }
        String sanitized = clipboard.replace("\r", "").replace("\n", "");
        if (!sanitized.isEmpty()) {
            this.replaceSelection(sanitized);
        }
    }

    private boolean hasSelection() {
        return this.selectionAnchor >= 0 && this.selectionAnchor != this.cursorPosition;
    }

    private int getSelectionStart() {
        return Math.min(this.selectionAnchor, this.cursorPosition);
    }

    private int getSelectionEnd() {
        return Math.max(this.selectionAnchor, this.cursorPosition);
    }

    private void deleteSelection() {
        if (!this.hasSelection()) {
            return;
        }
        this.pushUndoState();
        int start = this.getSelectionStart();
        int end = this.getSelectionEnd();
        this.filterText = this.filterText.substring(0, start) + this.filterText.substring(end);
        this.cursorPosition = start;
        this.selectionAnchor = -1;
    }

    private void replaceSelection(String insertText) {
        if (insertText == null) {
            insertText = "";
        }
        this.pushUndoState();
        if (this.hasSelection()) {
            int start = this.getSelectionStart();
            int end = this.getSelectionEnd();
            this.filterText = this.filterText.substring(0, start) + insertText + this.filterText.substring(end);
            this.cursorPosition = start + insertText.length();
            this.selectionAnchor = -1;
            return;
        }
        this.filterText = this.filterText.substring(0, this.cursorPosition) + insertText + this.filterText.substring(this.cursorPosition);
        this.cursorPosition += insertText.length();
    }

    private void pushUndoState() {
        if (this.undoStack.size() >= 64) {
            this.undoStack.removeFirst();
        }
        this.undoStack.addLast(new EditState(this.filterText, this.cursorPosition, this.selectionAnchor));
    }

    private void undoEdit() {
        if (this.undoStack.isEmpty()) {
            return;
        }
        EditState state = this.undoStack.removeLast();
        this.filterText = state.text;
        this.cursorPosition = Math.max(0, Math.min(state.cursorPosition, this.filterText.length()));
        this.selectionAnchor = state.selectionAnchor < 0 ? -1 : Math.max(0, Math.min(state.selectionAnchor, this.filterText.length()));
    }

    private void updateScroll(float dt, int viewHeight) {
        int contentHeight = this.filteredChoices.size() * 20;
        double maxScroll = Math.max(0, contentHeight - viewHeight);
        this.targetScrollY = Math.max(0.0, Math.min(maxScroll, this.targetScrollY));
        double diff = this.targetScrollY - this.scrollY;
        this.scrollY = Math.abs(diff) < 0.01 ? this.targetScrollY : (this.scrollY += diff * 15.0 * (double)dt);
    }

    private void applyFilter(boolean resetScroll) {
        String filter = this.filterText == null ? "" : this.filterText.toLowerCase(Locale.ROOT);
        this.filteredChoices.clear();
        for (FixedChoiceConfigEntry.Choice choice : this.choices) {
            String value;
            String label = choice.label() == null ? "" : choice.label();
            String secondaryLabel = choice.secondaryLabel() == null ? "" : choice.secondaryLabel();
            String string = value = choice.value() == null ? "" : choice.value();
            if (!filter.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(filter) && !secondaryLabel.toLowerCase(Locale.ROOT).contains(filter) && !value.toLowerCase(Locale.ROOT).contains(filter)) continue;
            this.filteredChoices.add(choice);
        }
        if (resetScroll) {
            this.targetScrollY = 0.0;
            this.scrollY = 0.0;
        } else {
            this.clampScrollPosition();
        }
        this.cursorPosition = Math.min(this.cursorPosition, this.filterText.length());
        if (this.selectionAnchor >= 0) {
            this.selectionAnchor = Math.min(this.selectionAnchor, this.filterText.length());
        }
    }

    private void clampScrollPosition() {
        int contentHeight = this.filteredChoices.size() * 20;
        double maxScroll = Math.max(0, contentHeight - 142);
        this.targetScrollY = Math.max(0.0, Math.min(maxScroll, this.targetScrollY));
        this.scrollY = Math.max(0.0, Math.min(maxScroll, this.scrollY));
    }

    private String resolveValue(String value) {
        if (value != null) {
            for (FixedChoiceConfigEntry.Choice choice : this.choices) {
                if (!choice.value().equals(value)) continue;
                return value;
            }
        }
        if (!this.choices.isEmpty()) {
            return this.choices.get(0).value();
        }
        return "";
    }

    private void selectChoice(FixedChoiceConfigEntry.Choice choice) {
        if (choice != null) {
            this.selectedValue = choice.value();
            if (this.onConfirm != null) {
                this.onConfirm.accept(this.selectedValue);
            }
        }
    }

    private boolean hasExtraAction(FixedChoiceConfigEntry.Choice choice) {
        return choice != null && this.extraActionVisiblePredicate != null && this.onExtraAction != null && this.extraActionVisiblePredicate.test(choice);
    }

    private boolean isDirectAction(FixedChoiceConfigEntry.Choice choice) {
        return choice != null && this.directActionPredicate != null && this.onDirectAction != null && this.directActionPredicate.test(choice);
    }

    private boolean isSelectable(FixedChoiceConfigEntry.Choice choice) {
        return choice != null && (this.selectablePredicate == null || this.selectablePredicate.test(choice));
    }

    private String resolvePrimaryActionText() {
        return this.primaryActionText == null || this.primaryActionText.isBlank() ? I18n.get("gd656conquest.client.gui.choice.select", new Object[0]) : this.primaryActionText;
    }

    private boolean isDisabled(FixedChoiceConfigEntry.Choice choice) {
        return choice != null && this.disabledPredicate != null && this.disabledPredicate.test(choice);
    }

    private void submitSelectionAndClose() {
        if (this.onConfirm != null) {
            String result = this.selectedValue;
            if ((result == null || result.isEmpty()) && !this.choices.isEmpty()) {
                result = this.choices.get(0).value();
            }
            this.onConfirm.accept(result);
        }
        this.visible = false;
    }

    private void cancel() {
        if (this.onCancel != null) {
            this.onCancel.run();
        }
        this.visible = false;
    }

    public record RowIcon(ItemStack itemStack, ResourceLocation texture) {
        public static RowIcon item(ItemStack itemStack) {
            return new RowIcon(itemStack, null);
        }

        public static RowIcon texture(ResourceLocation texture) {
            return new RowIcon(null, texture);
        }

        public boolean isEmpty() {
            return (this.itemStack == null || this.itemStack.isEmpty()) && this.texture == null;
        }
    }

    private static class EditState {
        final String text;
        final int cursorPosition;
        final int selectionAnchor;

        EditState(String text, int cursorPosition, int selectionAnchor) {
            this.text = text;
            this.cursorPosition = cursorPosition;
            this.selectionAnchor = selectionAnchor;
        }
    }
}

