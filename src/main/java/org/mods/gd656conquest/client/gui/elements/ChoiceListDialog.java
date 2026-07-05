package org.mods.gd656conquest.client.gui.elements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.mods.gd656conquest.client.gui.GuiConstants;
import org.mods.gd656conquest.client.gui.elements.entries.FixedChoiceConfigEntry;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ChoiceListDialog {
    private static final int ROW_HEIGHT = GuiConstants.ROW_HEADER_HEIGHT + 2;
    private static final int ROW_GAP = 1;
    private static final int LIST_WIDTH = 140 + 15;
    private static final int PREVIEW_WIDTH = 64;
    private static final int PREVIEW_GAP = 8;
    private final Minecraft minecraft;
    private Consumer<String> onConfirm;
    private Runnable onCancel;

    private boolean visible = false;
    private String title = "";
    private String filterText = "";
    private int cursorPosition = 0;
    private int selectionAnchor = -1;
    private final ArrayDeque<EditState> undoStack = new ArrayDeque<>();
    private int displayOffset = 0;
    private String selectedValue = "";

    private final List<ChoiceGroup> groups = new ArrayList<>();
    private final List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
    private final List<RenderItem> renderItems = new ArrayList<>();
    private final List<GDRowRenderer> rowRenderers = new ArrayList<>();
    private Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate;
    private Consumer<FixedChoiceConfigEntry.Choice> onExtraAction;
    private Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate;
    private Consumer<FixedChoiceConfigEntry.Choice> onDirectAction;
    private Predicate<FixedChoiceConfigEntry.Choice> selectablePredicate;
    private Function<FixedChoiceConfigEntry.Choice, RowIcon> iconProvider;
    private String extraActionText = "";
    private int extraActionColor = GuiConstants.COLOR_RED;
    private int extraActionWidth = GuiConstants.ROW_HEADER_HEIGHT;
    private ChoiceListState persistentState;
    private Consumer<FixedChoiceConfigEntry.Choice> onHoverChoice;

    private boolean isDraggingList = false;
    private double lastMouseY = 0;
    private double scrollY = 0;
    private double targetScrollY = 0;

    private float inputHoverProgress = 0.0f;
    private long lastFrameTime;

    private GDButton confirmButton;
    private GDButton cancelButton;
    private GDTextRenderer titleRenderer;

    private static final int PANEL_WIDTH = PREVIEW_WIDTH + PREVIEW_GAP + LIST_WIDTH;
    private static final int BUTTON_HEIGHT = GuiConstants.ROW_HEADER_HEIGHT;
    private static final int INPUT_HEIGHT = GuiConstants.ROW_HEADER_HEIGHT;
    private static final int HUE_BAR_WIDTH = GuiConstants.ROW_HEADER_HEIGHT;
    private static final int ICON_COLUMN_WIDTH = GuiConstants.ROW_HEADER_HEIGHT;
    private static final int PAD = GuiConstants.DEFAULT_PADDING;
    private static final int LIST_HEIGHT = LIST_WIDTH - HUE_BAR_WIDTH - PAD + 12;
    private static final int PANEL_HEIGHT = LIST_HEIGHT + PAD + INPUT_HEIGHT + PAD + BUTTON_HEIGHT;

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

    public record RowIcon(ItemStack itemStack, ResourceLocation texture) {
        public static RowIcon item(ItemStack itemStack) {
            return new RowIcon(itemStack, null);
        }

        public static RowIcon texture(ResourceLocation texture) {
            return new RowIcon(null, texture);
        }

        public boolean isEmpty() {
            return (itemStack == null || itemStack.isEmpty()) && texture == null;
        }
    }

    public static class ChoiceGroup {
        private final String groupId;
        private final String displayName;
        private final List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        private boolean expanded = false;

        public ChoiceGroup(String groupId, String displayName) {
            this.groupId = groupId;
            this.displayName = displayName;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public List<FixedChoiceConfigEntry.Choice> getChoices() {
            return choices;
        }

        public boolean isExpanded() {
            return expanded;
        }

        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
        }
    }

    private static class RenderItem {
        final boolean isGroupHeader;
        final ChoiceGroup group;
        final FixedChoiceConfigEntry.Choice choice;

        RenderItem(ChoiceGroup group) {
            this.isGroupHeader = true;
            this.group = group;
            this.choice = null;
        }

        RenderItem(ChoiceGroup group, FixedChoiceConfigEntry.Choice choice) {
            this.isGroupHeader = false;
            this.group = group;
            this.choice = choice;
        }
    }

    public ChoiceListDialog(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void showWithGroups(
        String initialValue,
        String title,
        List<ChoiceGroup> inputGroups,
        Consumer<String> onConfirm,
        Runnable onCancel,
        Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate,
        Consumer<FixedChoiceConfigEntry.Choice> onExtraAction,
        String extraActionText,
        int extraActionColor,
        int extraActionWidth,
        Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate,
        Consumer<FixedChoiceConfigEntry.Choice> onDirectAction,
        Predicate<FixedChoiceConfigEntry.Choice> selectablePredicate,
        Function<FixedChoiceConfigEntry.Choice, RowIcon> iconProvider,
        ChoiceListState persistentState,
        Consumer<FixedChoiceConfigEntry.Choice> onHoverChoice
    ) {
        this.title = title;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.extraActionVisiblePredicate = extraActionVisiblePredicate;
        this.onExtraAction = onExtraAction;
        this.directActionPredicate = directActionPredicate;
        this.onDirectAction = onDirectAction;
        this.selectablePredicate = selectablePredicate;
        this.iconProvider = iconProvider;
        this.extraActionText = extraActionText == null ? "" : extraActionText;
        this.extraActionColor = extraActionColor;
        this.extraActionWidth = extraActionWidth <= 0 ? GuiConstants.ROW_HEADER_HEIGHT : extraActionWidth;
        this.visible = true;
        this.lastFrameTime = System.currentTimeMillis();
        this.inputHoverProgress = 0.0f;
        this.filterText = "";
        this.cursorPosition = 0;
        this.selectionAnchor = -1;
        this.undoStack.clear();
        this.displayOffset = 0;
        this.persistentState = persistentState;
        this.onHoverChoice = onHoverChoice;

        this.groups.clear();
        this.choices.clear();
        
        if (inputGroups != null) {
            for (ChoiceGroup group : inputGroups) {
                if (group != null && !group.getChoices().isEmpty()) {
                    if (persistentState != null && persistentState.isExpanded(group.getGroupId())) {
                        group.setExpanded(true);
                    }
                    this.groups.add(group);
                    this.choices.addAll(group.getChoices());
                }
            }
        }

        this.selectedValue = resolveValue(initialValue);
        applyFilter();

        if (persistentState != null) {
            this.targetScrollY = persistentState.getScrollY();
            this.scrollY = this.targetScrollY;
        }

        int w1 = (PANEL_WIDTH - 1) / 2;
        int w2 = PANEL_WIDTH - 1 - w1;
        this.confirmButton = new GDButton(0, 0, w2, BUTTON_HEIGHT, net.minecraft.network.chat.Component.literal(I18n.get("gd656conquest.client.gui.config.confirm")), (btn) -> confirm());
        this.cancelButton = new GDButton(0, 0, w1, BUTTON_HEIGHT, net.minecraft.network.chat.Component.literal(I18n.get("gd656conquest.client.gui.config.cancel")), (btn) -> cancel());

        if (this.titleRenderer == null) {
            this.titleRenderer = new GDTextRenderer(title, 0, 0, 0, 0, 1.0f, GuiConstants.COLOR_GOLD, false);
        } else {
            this.titleRenderer.setText(title);
            this.titleRenderer.setColor(GuiConstants.COLOR_GOLD);
        }
    }

    public void show(String initialValue, String title, List<FixedChoiceConfigEntry.Choice> options, Consumer<String> onConfirm, Runnable onCancel) {
        show(initialValue, title, options, onConfirm, onCancel, null, null, "", GuiConstants.COLOR_RED, GuiConstants.ROW_HEADER_HEIGHT, null, null);
    }

    public void show(
        String initialValue,
        String title,
        List<FixedChoiceConfigEntry.Choice> options,
        Consumer<String> onConfirm,
        Runnable onCancel,
        Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate,
        Consumer<FixedChoiceConfigEntry.Choice> onExtraAction,
        String extraActionText,
        int extraActionColor,
        int extraActionWidth
    ) {
        show(
            initialValue,
            title,
            options,
            onConfirm,
            onCancel,
            extraActionVisiblePredicate,
            onExtraAction,
            extraActionText,
            extraActionColor,
            extraActionWidth,
            null,
            null
        );
    }

    public void show(
        String initialValue,
        String title,
        List<FixedChoiceConfigEntry.Choice> options,
        Consumer<String> onConfirm,
        Runnable onCancel,
        Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate,
        Consumer<FixedChoiceConfigEntry.Choice> onExtraAction,
        String extraActionText,
        int extraActionColor,
        int extraActionWidth,
        Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate,
        Consumer<FixedChoiceConfigEntry.Choice> onDirectAction
    ) {
        show(
            initialValue,
            title,
            options,
            onConfirm,
            onCancel,
            extraActionVisiblePredicate,
            onExtraAction,
            extraActionText,
            extraActionColor,
            extraActionWidth,
            directActionPredicate,
            onDirectAction,
            null,
            null
        );
    }

    public void show(
        String initialValue,
        String title,
        List<FixedChoiceConfigEntry.Choice> options,
        Consumer<String> onConfirm,
        Runnable onCancel,
        Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate,
        Consumer<FixedChoiceConfigEntry.Choice> onExtraAction,
        String extraActionText,
        int extraActionColor,
        int extraActionWidth,
        Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate,
        Consumer<FixedChoiceConfigEntry.Choice> onDirectAction,
        Predicate<FixedChoiceConfigEntry.Choice> selectablePredicate
    ) {
        show(
            initialValue,
            title,
            options,
            onConfirm,
            onCancel,
            extraActionVisiblePredicate,
            onExtraAction,
            extraActionText,
            extraActionColor,
            extraActionWidth,
            directActionPredicate,
            onDirectAction,
            selectablePredicate,
            null
        );
    }

    public void show(
        String initialValue,
        String title,
        List<FixedChoiceConfigEntry.Choice> options,
        Consumer<String> onConfirm,
        Runnable onCancel,
        Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate,
        Consumer<FixedChoiceConfigEntry.Choice> onExtraAction,
        String extraActionText,
        int extraActionColor,
        int extraActionWidth,
        Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate,
        Consumer<FixedChoiceConfigEntry.Choice> onDirectAction,
        Predicate<FixedChoiceConfigEntry.Choice> selectablePredicate,
        Function<FixedChoiceConfigEntry.Choice, RowIcon> iconProvider
    ) {
        show(
            initialValue,
            title,
            options,
            onConfirm,
            onCancel,
            extraActionVisiblePredicate,
            onExtraAction,
            extraActionText,
            extraActionColor,
            extraActionWidth,
            directActionPredicate,
            onDirectAction,
            selectablePredicate,
            iconProvider,
            null
        );
    }

    public void show(
        String initialValue,
        String title,
        List<FixedChoiceConfigEntry.Choice> options,
        Consumer<String> onConfirm,
        Runnable onCancel,
        Predicate<FixedChoiceConfigEntry.Choice> extraActionVisiblePredicate,
        Consumer<FixedChoiceConfigEntry.Choice> onExtraAction,
        String extraActionText,
        int extraActionColor,
        int extraActionWidth,
        Predicate<FixedChoiceConfigEntry.Choice> directActionPredicate,
        Consumer<FixedChoiceConfigEntry.Choice> onDirectAction,
        Predicate<FixedChoiceConfigEntry.Choice> selectablePredicate,
        Function<FixedChoiceConfigEntry.Choice, RowIcon> iconProvider,
        Consumer<FixedChoiceConfigEntry.Choice> onHoverChoice
    ) {
        this.title = title;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.extraActionVisiblePredicate = extraActionVisiblePredicate;
        this.onExtraAction = onExtraAction;
        this.directActionPredicate = directActionPredicate;
        this.onDirectAction = onDirectAction;
        this.selectablePredicate = selectablePredicate;
        this.iconProvider = iconProvider;
        this.onHoverChoice = onHoverChoice;
        this.extraActionText = extraActionText == null ? "" : extraActionText;
        this.extraActionColor = extraActionColor;
        this.extraActionWidth = extraActionWidth <= 0 ? GuiConstants.ROW_HEADER_HEIGHT : extraActionWidth;
        this.visible = true;
        this.lastFrameTime = System.currentTimeMillis();
        this.inputHoverProgress = 0.0f;
        this.filterText = "";
        this.cursorPosition = 0;
        this.selectionAnchor = -1;
        this.undoStack.clear();
        this.displayOffset = 0;

        this.choices.clear();
        if (options != null) {
            this.choices.addAll(options);
        }

        this.selectedValue = resolveValue(initialValue);
        applyFilter();

        int w1 = (PANEL_WIDTH - 1) / 2;
        int w2 = PANEL_WIDTH - 1 - w1;
        this.confirmButton = new GDButton(0, 0, w2, BUTTON_HEIGHT, net.minecraft.network.chat.Component.literal(I18n.get("gd656conquest.client.gui.config.confirm")), (btn) -> confirm());
        this.cancelButton = new GDButton(0, 0, w1, BUTTON_HEIGHT, net.minecraft.network.chat.Component.literal(I18n.get("gd656conquest.client.gui.config.cancel")), (btn) -> cancel());

        if (this.titleRenderer == null) {
            this.titleRenderer = new GDTextRenderer(title, 0, 0, 0, 0, 1.0f, GuiConstants.COLOR_GOLD, false);
        } else {
            this.titleRenderer.setText(title);
            this.titleRenderer.setColor(GuiConstants.COLOR_GOLD);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void hide() {
        this.visible = false;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 500.0f);

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int dimColor = 0x88444444;
        guiGraphics.fill(0, 0, screenWidth, screenHeight, dimColor);

        int centerX = (screenWidth - PANEL_WIDTH) / 2;
        int centerY = (screenHeight - PANEL_HEIGHT) / 2 + 20;

        int fontHeight = minecraft.font.lineHeight;
        int titleY = centerY - fontHeight - PAD + 6;
        int buttonsY = centerY + PANEL_HEIGHT - BUTTON_HEIGHT;

        int containerTop = titleY - PAD;
        int containerLeft = centerX - PAD;
        int containerRight = centerX + PANEL_WIDTH + PAD;
        int containerBottom = buttonsY + BUTTON_HEIGHT + PAD;

        guiGraphics.fill(containerLeft, containerTop, containerRight, containerBottom, GuiConstants.COLOR_BG);

        if (titleRenderer != null) {
            titleRenderer.setX1(centerX);
            titleRenderer.setY1(titleY);
            titleRenderer.setX2(centerX + PANEL_WIDTH);
            titleRenderer.setY2(titleY + fontHeight);
            titleRenderer.render(guiGraphics, partialTick);
        }

        int previewX = centerX;
        int previewY = buttonsY - PAD - INPUT_HEIGHT - PAD - LIST_HEIGHT;
        int previewW = PREVIEW_WIDTH;
        int previewH = LIST_HEIGHT;
        int listX = centerX + PREVIEW_WIDTH + PREVIEW_GAP;
        int listY = buttonsY - PAD - INPUT_HEIGHT - PAD - LIST_HEIGHT;
        int listW = LIST_WIDTH;
        int listH = LIST_HEIGHT;

        int inputX = centerX;
        int inputY = buttonsY - PAD - INPUT_HEIGHT;
        int inputW = PANEL_WIDTH;

        long currentTime = System.currentTimeMillis();
        float dt = (currentTime - lastFrameTime) / 1000.0f;
        lastFrameTime = currentTime;

        boolean isHoveringInput = mouseX >= inputX && mouseX <= inputX + inputW && mouseY >= inputY && mouseY <= inputY + INPUT_HEIGHT;
        if (isHoveringInput) inputHoverProgress = Math.min(1.0f, inputHoverProgress + dt * 2.0f);
        else inputHoverProgress = Math.max(0.0f, inputHoverProgress - dt * 2.0f);

        if (isDraggingList) {
            double diff = mouseY - lastMouseY;
            targetScrollY -= diff;
            lastMouseY = mouseY;
        }

        updateScroll(dt, listH);

        int listBgColor = (GuiConstants.COLOR_BLACK & 0x00FFFFFF) | (int)(255 * 0.35f) << 24;
        guiGraphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, listBgColor);
        guiGraphics.fill(listX, listY, listX + listW, listY + listH, listBgColor);
        
        FixedChoiceConfigEntry.Choice hoveredChoice = getHoveredChoice(mouseX, mouseY, listX, listY, listW, listH);
        if (onHoverChoice != null) {
            onHoverChoice.accept(hoveredChoice);
        }
        
        renderHoverPreview(guiGraphics, hoveredChoice, previewX, previewY, previewW, previewH);

        renderList(guiGraphics, mouseX, mouseY, partialTick, listX, listY, listX + listW, listY + listH);

        int inputBgColor = (GuiConstants.COLOR_BLACK & 0x00FFFFFF) | (int)(255 * 0.45f) << 24;
        guiGraphics.fill(inputX, inputY, inputX + inputW, inputY + INPUT_HEIGHT, inputBgColor);

        renderHoverTrail(guiGraphics, inputX, inputY, inputW, INPUT_HEIGHT, inputHoverProgress);
        renderInputText(guiGraphics, inputX, inputY, inputW, INPUT_HEIGHT);

        int btnLeftX = centerX;
        int btnRightX = centerX + (PANEL_WIDTH - 1) / 2 + 1;

        if (cancelButton != null) {
            cancelButton.setX(btnLeftX);
            cancelButton.setY(buttonsY);
            cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        if (confirmButton != null) {
            confirmButton.setX(btnRightX);
            confirmButton.setY(buttonsY);
            confirmButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        guiGraphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        if (confirmButton != null && confirmButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (cancelButton != null && cancelButton.mouseClicked(mouseX, mouseY, button)) return true;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int centerX = (screenWidth - PANEL_WIDTH) / 2;
        int centerY = (screenHeight - PANEL_HEIGHT) / 2 + 20;

        int buttonsY = centerY + PANEL_HEIGHT - BUTTON_HEIGHT;
        int inputY = buttonsY - PAD - INPUT_HEIGHT;
        int inputX = centerX;
        int listY = inputY - PAD - LIST_HEIGHT;
        int listX = centerX + PREVIEW_WIDTH + PREVIEW_GAP;

        if (mouseX >= inputX && mouseX <= inputX + PANEL_WIDTH && mouseY >= inputY && mouseY <= inputY + INPUT_HEIGHT) {
            setCursorByMouse((int)mouseX, inputX);
            selectionAnchor = -1;
            return true;
        }

        if (mouseX >= listX && mouseX <= listX + LIST_WIDTH && mouseY >= listY && mouseY <= listY + LIST_HEIGHT) {
            double adjustedMouseY = mouseY + scrollY;
            int selectWidth = 30;

            for (int i = 0; i < renderItems.size(); i++) {
                RenderItem item = renderItems.get(i);
                if (item.isGroupHeader) {
                    int rowTop = listY + i * (ROW_HEIGHT + ROW_GAP);
                    int rowBottom = rowTop + ROW_HEIGHT;
                    if (adjustedMouseY >= rowTop && adjustedMouseY <= rowBottom) {
                        if (button == 0) {
                            item.group.setExpanded(!item.group.isExpanded());
                            if (persistentState != null) {
                                persistentState.setExpanded(item.group.getGroupId(), item.group.isExpanded());
                            }
                            double oldScroll = targetScrollY;
                            applyFilter();
                            targetScrollY = oldScroll;
                            scrollY = oldScroll;
                        }
                        break;
                    }
                    continue;
                }

                FixedChoiceConfigEntry.Choice choice = item.choice;
                boolean hasExtraAction = hasExtraAction(choice);
                int extraWidth = hasExtraAction ? extraActionWidth : 0;
                int selectX1 = listX + LIST_WIDTH - selectWidth - extraWidth;
                int extraX1 = listX + LIST_WIDTH - extraWidth;
                int rowTop = listY + i * (ROW_HEIGHT + ROW_GAP);
                int rowBottom = rowTop + ROW_HEIGHT;
                if (adjustedMouseY >= rowTop && adjustedMouseY <= rowBottom) {
                    if (button == 0 && isDirectAction(choice)) {
                        if (onDirectAction != null) {
                            onDirectAction.accept(choice);
                        }
                    } else if (button == 0 && hasExtraAction && mouseX >= extraX1) {
                        if (onExtraAction != null) {
                            onExtraAction.accept(choice);
                        }
                    } else if (button == 0 && mouseX >= selectX1 && isSelectable(choice)) {
                        selectChoice(choice);
                    }
                    break;
                }
            }

            isDraggingList = true;
            lastMouseY = mouseY;
            return true;
        }

        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingList = false;
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int centerX = (screenWidth - PANEL_WIDTH) / 2;
        int centerY = (screenHeight - PANEL_HEIGHT) / 2 + 20;
        int buttonsY = centerY + PANEL_HEIGHT - BUTTON_HEIGHT;
        int listY = buttonsY - PAD - INPUT_HEIGHT - PAD - LIST_HEIGHT;
        int listX = centerX + PREVIEW_WIDTH + PREVIEW_GAP;

        if (mouseX >= listX && mouseX <= listX + LIST_WIDTH && mouseY >= listY && mouseY <= listY + LIST_HEIGHT) {
            targetScrollY -= delta * GuiConstants.SCROLL_AMOUNT;
            return true;
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;

        if (net.minecraft.SharedConstants.isAllowedChatCharacter(codePoint)) {
            replaceSelection(String.valueOf(codePoint));
            applyFilter();
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        boolean controlDown = Screen.hasControlDown() || (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shiftDown = Screen.hasShiftDown() || (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (controlDown && keyCode == GLFW.GLFW_KEY_A) {
            selectionAnchor = 0;
            cursorPosition = filterText.length();
            return true;
        }
        if (controlDown && keyCode == GLFW.GLFW_KEY_C) {
            if (hasSelection() && minecraft != null && minecraft.keyboardHandler != null) {
                minecraft.keyboardHandler.setClipboard(filterText.substring(getSelectionStart(), getSelectionEnd()));
            }
            return true;
        }
        if (controlDown && keyCode == GLFW.GLFW_KEY_Z) {
            undoEdit();
            applyFilter();
            return true;
        }
        if ((controlDown && keyCode == GLFW.GLFW_KEY_V) || (shiftDown && keyCode == GLFW.GLFW_KEY_INSERT)) {
            insertClipboardText();
            applyFilter();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                deleteSelection();
                applyFilter();
                return true;
            }
            if (cursorPosition > 0) {
                pushUndoState();
                filterText = filterText.substring(0, cursorPosition - 1) + filterText.substring(cursorPosition);
                cursorPosition--;
                applyFilter();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (hasSelection()) {
                deleteSelection();
                applyFilter();
                return true;
            }
            if (cursorPosition < filterText.length()) {
                pushUndoState();
                filterText = filterText.substring(0, cursorPosition) + filterText.substring(cursorPosition + 1);
                applyFilter();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (shiftDown) {
                if (selectionAnchor < 0) selectionAnchor = cursorPosition;
            } else {
                selectionAnchor = -1;
            }
            if (cursorPosition > 0) cursorPosition--;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (shiftDown) {
                if (selectionAnchor < 0) selectionAnchor = cursorPosition;
            } else {
                selectionAnchor = -1;
            }
            if (cursorPosition < filterText.length()) cursorPosition++;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_HOME) {
            if (shiftDown) {
                if (selectionAnchor < 0) selectionAnchor = cursorPosition;
            } else {
                selectionAnchor = -1;
            }
            cursorPosition = 0;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_END) {
            if (shiftDown) {
                if (selectionAnchor < 0) selectionAnchor = cursorPosition;
            } else {
                selectionAnchor = -1;
            }
            cursorPosition = filterText.length();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }

        return true;
    }

    private void renderList(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int x1, int y1, int x2, int y2) {
        if (y2 <= y1) return;

        guiGraphics.enableScissor(x1, y1, x2, y2);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -scrollY, 0);

        for (int i = 0; i < renderItems.size(); i++) {
            RenderItem item = renderItems.get(i);
            if (item.isGroupHeader) continue;
            FixedChoiceConfigEntry.Choice choice = item.choice;
            int rowTop = y1 + i * (ROW_HEIGHT + ROW_GAP);
            int rowBottom = rowTop + ROW_HEIGHT;

            float actualTop = rowTop - (float)scrollY;
            float actualBottom = rowBottom - (float)scrollY;
            if (actualBottom > y1 && actualTop < y2) {
                renderChoiceRow(guiGraphics, mouseX, (int)(mouseY + scrollY), partialTick, i, choice, x1, rowTop, x2, rowBottom);
            }
        }

        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
    }

    private void renderChoiceRow(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int index, FixedChoiceConfigEntry.Choice choice, int x1, int y1, int x2, int y2) {
        int rowBgColor = GuiConstants.COLOR_BLACK & 0xFFFFFF;
        float alpha = (index % 2 == 1) ? 0.10f : 0.30f;

        while (rowRenderers.size() <= index) {
            rowRenderers.add(new GDRowRenderer(x1, y1, x2, y2, rowBgColor, alpha, false));
        }
        GDRowRenderer renderer = rowRenderers.get(index);
        renderer.setBounds(x1, y1, x2, y2);
        renderer.setBackgroundColor(rowBgColor);
        renderer.setBackgroundAlpha(alpha);
        renderer.resetColumnConfig();

        boolean selected = choice.value().equals(selectedValue);
        boolean selectable = isSelectable(choice);
        int firstColor = selectable
            ? (selected ? GuiConstants.COLOR_GOLD : GuiConstants.COLOR_WHITE)
            : GuiConstants.COLOR_GRAY;
        int secondColor = selectable
            ? (selected ? GuiConstants.COLOR_GRAY : GuiConstants.COLOR_WHITE)
            : GuiConstants.COLOR_GRAY;
        if (isDirectAction(choice)) {
            addRowIconColumn(renderer, choice);
            renderer.addColumn(" " + choice.label(), -1, GuiConstants.COLOR_GOLD, true, true, (btn) -> {
                if (onDirectAction != null) {
                    onDirectAction.accept(choice);
                }
            });
            renderer.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        addRowIconColumn(renderer, choice);
        renderer.addColumn(" " + choice.label(), -1, firstColor, true, false, null);
        renderer.addColumn(
            selectable ? I18n.get("gd656conquest.client.gui.choice.select") : I18n.get("gd656conquest.client.gui.choice.locked"),
            30,
            secondColor,
            false,
            true,
            selectable ? (btn) -> selectChoice(choice) : null
        );
        if (hasExtraAction(choice)) {
            renderer.addColumn(this.extraActionText, this.extraActionWidth, this.extraActionColor, true, true, (btn) -> {
                if (onExtraAction != null) {
                    onExtraAction.accept(choice);
                }
            });
        }

        renderer.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHoverPreview(GuiGraphics guiGraphics, FixedChoiceConfigEntry.Choice hoveredChoice, int previewX, int previewY, int previewW, int previewH) {
        if (hoveredChoice == null || iconProvider == null) {
            return;
        }
        RowIcon icon = iconProvider.apply(hoveredChoice);
        if (icon == null || icon.isEmpty()) {
            return;
        }
        if (icon.itemStack() != null && !icon.itemStack().isEmpty()) {
            float scale = 2.5f;
            int iconSize = Math.round(16 * scale);
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
            guiGraphics.blit(icon.texture(), textureX, textureY, 0, 0, size, size, size, size);
        }
    }

    private FixedChoiceConfigEntry.Choice getHoveredChoice(int mouseX, int mouseY, int listX, int listY, int listW, int listH) {
        if (mouseX < listX || mouseX > listX + listW || mouseY < listY || mouseY > listY + listH) {
            return null;
        }
        double adjustedMouseY = mouseY + scrollY;
        for (int i = 0; i < renderItems.size(); i++) {
            RenderItem item = renderItems.get(i);
            if (item.isGroupHeader) continue;
            int rowTop = listY + i * (ROW_HEIGHT + ROW_GAP);
            int rowBottom = rowTop + ROW_HEIGHT;
            if (adjustedMouseY >= rowTop && adjustedMouseY <= rowBottom) {
                return item.choice;
            }
        }
        return null;
    }

    private void addRowIconColumn(GDRowRenderer renderer, FixedChoiceConfigEntry.Choice choice) {
        RowIcon icon = iconProvider == null ? null : iconProvider.apply(choice);
        if (icon == null || icon.isEmpty()) {
            return;
        }
        if (icon.itemStack() != null && !icon.itemStack().isEmpty()) {
            renderer.addIconColumn(icon.itemStack(), ICON_COLUMN_WIDTH, true, true, null);
            return;
        }
        if (icon.texture() != null) {
            renderer.addCustomColumn(ICON_COLUMN_WIDTH, null, (graphics, x, y, width, height) -> {
                int iconSize = Math.min(16, Math.max(8, height - 2));
                int iconX = x + (width - iconSize) / 2;
                int iconY = y + (height - iconSize) / 2;
                graphics.blit(icon.texture(), iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
            });
        }
    }

    private void renderInputText(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.enableScissor(x, y, x + w, y + h);

        int textWidth = minecraft.font.width(filterText);
        int boxInnerWidth = w - 4;
        String textBeforeCursor = filterText.substring(0, cursorPosition);
        int cursorXRel = minecraft.font.width(textBeforeCursor);

        if (cursorXRel < displayOffset) {
            displayOffset = cursorXRel;
        } else if (cursorXRel > displayOffset + boxInnerWidth) {
            displayOffset = cursorXRel - boxInnerWidth;
        }
        if (textWidth < boxInnerWidth) {
            displayOffset = 0;
        }

        int drawX = x + 2 - displayOffset;
        int drawY = y + (h - minecraft.font.lineHeight) / 2;

        if (hasSelection()) {
            int selStartRel = minecraft.font.width(filterText.substring(0, getSelectionStart()));
            int selEndRel = minecraft.font.width(filterText.substring(0, getSelectionEnd()));
            int sx = drawX + selStartRel;
            int ex = drawX + selEndRel;
            guiGraphics.fill(sx, drawY - 1, ex, drawY + minecraft.font.lineHeight + 1, 0x66FFD700);
        }

        guiGraphics.drawString(minecraft.font, filterText, drawX, drawY, GuiConstants.COLOR_WHITE, true);

        if (System.currentTimeMillis() / 500 % 2 == 0) {
            int cx = drawX + cursorXRel;
            if (cx >= x && cx <= x + w) {
                guiGraphics.fill(cx, drawY - 1, cx + 1, drawY + minecraft.font.lineHeight + 1, GuiConstants.COLOR_WHITE);
            }
        }

        guiGraphics.disableScissor();
    }

    private void renderHoverTrail(GuiGraphics guiGraphics, int x, int y, int w, int h, float progress) {
        if (progress <= 0.001f) return;

        int color = GuiConstants.COLOR_GOLD;
        float totalLength = w + h + w + h;
        float currentLength = totalLength * easeOut(progress);
        float drawn = 0;

        if (currentLength > 0) {
            float segLen = Math.min(w, currentLength);
            guiGraphics.fill(x, y + h - 1, x + (int)segLen, y + h, color);
            drawn += w;
        }

        if (currentLength > drawn) {
            float rem = currentLength - drawn;
            float segLen = Math.min(h, rem);
            guiGraphics.fill(x + w - 1, y + h - (int)segLen, x + w, y + h, color);
            drawn += h;
        }

        if (currentLength > drawn) {
            float rem = currentLength - drawn;
            float segLen = Math.min(w, rem);
            guiGraphics.fill(x + w - (int)segLen, y, x + w, y + 1, color);
            drawn += w;
        }

        if (currentLength > drawn) {
            float rem = currentLength - drawn;
            float segLen = Math.min(h, rem);
            guiGraphics.fill(x, y, x + 1, y + (int)segLen, color);
        }
    }

    private float easeOut(float t) {
        return 1.0f - (float)Math.pow(1.0f - t, 3);
    }

    private void setCursorByMouse(int mouseX, int inputX) {
        int mouseOffset = mouseX - (inputX + 2) + displayOffset;
        if (mouseOffset <= 0) {
            cursorPosition = 0;
            return;
        }
        int pos = 0;
        for (int i = 1; i <= filterText.length(); i++) {
            int width = minecraft.font.width(filterText.substring(0, i));
            if (width >= mouseOffset) {
                pos = i;
                break;
            }
            pos = i;
        }
        cursorPosition = pos;
    }

    private void insertClipboardText() {
        if (minecraft == null || minecraft.keyboardHandler == null) {
            return;
        }
        String clipboard = minecraft.keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isEmpty()) {
            return;
        }
        String sanitized = clipboard.replace("\r", "").replace("\n", "");
        if (sanitized.isEmpty()) {
            return;
        }
        replaceSelection(sanitized);
    }

    private boolean hasSelection() {
        return selectionAnchor >= 0 && selectionAnchor != cursorPosition;
    }

    private int getSelectionStart() {
        return Math.min(selectionAnchor, cursorPosition);
    }

    private int getSelectionEnd() {
        return Math.max(selectionAnchor, cursorPosition);
    }

    private void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        pushUndoState();
        int start = getSelectionStart();
        int end = getSelectionEnd();
        filterText = filterText.substring(0, start) + filterText.substring(end);
        cursorPosition = start;
        selectionAnchor = -1;
    }

    private void replaceSelection(String insertText) {
        if (insertText == null) {
            insertText = "";
        }
        pushUndoState();
        if (hasSelection()) {
            int start = getSelectionStart();
            int end = getSelectionEnd();
            filterText = filterText.substring(0, start) + insertText + filterText.substring(end);
            cursorPosition = start + insertText.length();
            selectionAnchor = -1;
            return;
        }
        filterText = filterText.substring(0, cursorPosition) + insertText + filterText.substring(cursorPosition);
        cursorPosition += insertText.length();
    }

    private void pushUndoState() {
        if (undoStack.size() >= 64) {
            undoStack.removeFirst();
        }
        undoStack.addLast(new EditState(filterText, cursorPosition, selectionAnchor));
    }

    private void undoEdit() {
        if (undoStack.isEmpty()) {
            return;
        }
        EditState state = undoStack.removeLast();
        filterText = state.text;
        cursorPosition = Math.max(0, Math.min(state.cursorPosition, filterText.length()));
        selectionAnchor = state.selectionAnchor < 0 ? -1 : Math.max(0, Math.min(state.selectionAnchor, filterText.length()));
    }

    private void updateScroll(float dt, int viewHeight) {
        int contentHeight = renderItems.size() * (ROW_HEIGHT + ROW_GAP);
        double maxScroll = Math.max(0, contentHeight - viewHeight);
        targetScrollY = Math.max(0, Math.min(maxScroll, targetScrollY));

        double diff = targetScrollY - scrollY;
        if (Math.abs(diff) < 0.01) {
            scrollY = targetScrollY;
        } else {
            scrollY += diff * GuiConstants.SCROLL_SMOOTHING * dt;
        }
    }

    private void applyFilter() {
        String filter = filterText == null ? "" : filterText.toLowerCase(Locale.ROOT);
        renderItems.clear();
        
        if (groups.isEmpty()) {
            for (FixedChoiceConfigEntry.Choice choice : choices) {
                String label = choice.label() == null ? "" : choice.label();
                String value = choice.value() == null ? "" : choice.value();
                if (filter.isEmpty() || label.toLowerCase(Locale.ROOT).contains(filter) || value.toLowerCase(Locale.ROOT).contains(filter)) {
                    renderItems.add(new RenderItem(null, choice));
                }
            }
        } else {
            for (ChoiceGroup group : groups) {
                List<FixedChoiceConfigEntry.Choice> groupMatches = new ArrayList<>();
                for (FixedChoiceConfigEntry.Choice choice : group.getChoices()) {
                    String label = choice.label() == null ? "" : choice.label();
                    String value = choice.value() == null ? "" : choice.value();
                    if (filter.isEmpty() || label.toLowerCase(Locale.ROOT).contains(filter) || value.toLowerCase(Locale.ROOT).contains(filter)) {
                        groupMatches.add(choice);
                    }
                }
                
                if (!groupMatches.isEmpty()) {
                    renderItems.add(new RenderItem(group));
                    if (group.isExpanded() || !filter.isEmpty()) { // Auto expand if filtering
                        for (FixedChoiceConfigEntry.Choice match : groupMatches) {
                            renderItems.add(new RenderItem(group, match));
                        }
                    }
                }
            }
        }
        
        targetScrollY = 0;
        scrollY = 0;
        cursorPosition = Math.min(cursorPosition, filterText.length());
        if (selectionAnchor >= 0) {
            selectionAnchor = Math.min(selectionAnchor, filterText.length());
        }
    }

    private String resolveValue(String value) {
        if (value != null) {
            for (FixedChoiceConfigEntry.Choice choice : choices) {
                if (choice.value().equals(value)) {
                    return value;
                }
            }
        }
        if (!choices.isEmpty()) {
            return choices.get(0).value();
        }
        return "";
    }

    private void selectChoice(FixedChoiceConfigEntry.Choice choice) {
        if (choice != null) {
            selectedValue = choice.value();
        }
    }

    private boolean hasExtraAction(FixedChoiceConfigEntry.Choice choice) {
        return choice != null && extraActionVisiblePredicate != null && onExtraAction != null && extraActionVisiblePredicate.test(choice);
    }

    private boolean isDirectAction(FixedChoiceConfigEntry.Choice choice) {
        return choice != null && directActionPredicate != null && onDirectAction != null && directActionPredicate.test(choice);
    }

    private boolean isSelectable(FixedChoiceConfigEntry.Choice choice) {
        return choice != null && (selectablePredicate == null || selectablePredicate.test(choice));
    }

    private void confirm() {
        if (onConfirm != null) {
            String result = selectedValue;
            if ((result == null || result.isEmpty()) && !choices.isEmpty()) {
                result = choices.get(0).value();
            }
            onConfirm.accept(result);
        }
        this.visible = false;
    }

    private void cancel() {
        if (onCancel != null) {
            onCancel.run();
        }
        this.visible = false;
    }
}
