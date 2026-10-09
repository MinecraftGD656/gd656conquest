package org.mods.gd656conquest.client.hud;

import net.minecraft.client.Minecraft;

public final class ClientHudVisibilityCycleService {
    private static Mode currentMode = Mode.NORMAL;
    private static boolean forcedHudOnly;
    private static boolean forcedVisible;
    private static boolean bootstrappedFromOption;

    private ClientHudVisibilityCycleService() {
    }

    public static void onF1Pressed() {
        currentMode = switch (currentMode) {
            case NORMAL -> Mode.CINEMATIC_HUD;
            case CINEMATIC_HUD -> Mode.HAND_ONLY;
            case HAND_ONLY -> Mode.FULL_HIDE;
            case FULL_HIDE -> Mode.NORMAL;
        };
        applyModeToClient();
    }

    public static void tick() {
        bootstrapModeFromOptionIfNeeded();
        applyModeToClient();
    }

    public static boolean shouldHideHudOnly() {
        return resolveEffectiveMode() == Mode.CINEMATIC_HUD;
    }

    public static boolean shouldApplyForcedHudOnlyFilter() {
        return !forcedVisible && forcedHudOnly;
    }

    public static boolean shouldHideTopHudOnly() {
        return !forcedVisible && !forcedHudOnly && currentMode == Mode.CINEMATIC_HUD;
    }

    public static boolean shouldHideAllHudButKeepHand() {
        return !forcedVisible && !forcedHudOnly && currentMode == Mode.HAND_ONLY;
    }

    public static boolean shouldHideAllHudAndHand() {
        return !forcedVisible && currentMode == Mode.FULL_HIDE;
    }

    public static void setForcedHudOnly(boolean active) {
        forcedHudOnly = active;
        applyModeToClient();
    }

    public static void setForcedVisible(boolean active) {
        forcedVisible = active;
        applyModeToClient();
    }

    public static void reset() {
        currentMode = Mode.NORMAL;
        forcedHudOnly = false;
        forcedVisible = false;
        bootstrappedFromOption = false;
        applyModeToClient();
    }

    private static void bootstrapModeFromOptionIfNeeded() {
        if (bootstrappedFromOption) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        currentMode = minecraft.options.hideGui ? Mode.FULL_HIDE : Mode.NORMAL;
        bootstrappedFromOption = true;
    }

    private static void applyModeToClient() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        minecraft.options.hideGui = shouldHideAllHudAndHand();
    }

    private static Mode resolveEffectiveMode() {
        if (forcedVisible) {
            return Mode.NORMAL;
        }
        if (forcedHudOnly) {
            return Mode.CINEMATIC_HUD;
        }
        return currentMode;
    }

    private enum Mode {
        NORMAL,
        CINEMATIC_HUD,
        HAND_ONLY,
        FULL_HIDE
    }
}
