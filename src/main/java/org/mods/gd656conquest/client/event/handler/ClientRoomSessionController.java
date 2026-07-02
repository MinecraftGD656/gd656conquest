package org.mods.gd656conquest.client.event.handler;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.lwjgl.glfw.GLFW;
import org.mods.gd656conquest.client.compat.tacz.TaczClientInputCompat;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiController;
import org.mods.gd656conquest.client.loadout.screen.LoadoutConfigScreen;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientDownedState;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.client.room.ClientReviveState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiActionPacket;
import org.mods.gd656conquest.network.packet.MainHandItemNameUploadPacket;
import org.mods.gd656conquest.network.packet.ServerboundAmmoBoxInteractPacket;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareVehicleCompatBridge;

import java.lang.reflect.Field;
import java.util.Locale;

final class ClientRoomSessionController {
    private static final ResourceLocation VIGNETTE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/vignette.png");
    private static final int VIGNETTE_TEXTURE_SIZE = 256;
    private static final int MAIN_HAND_UPLOAD_INTERVAL_TICKS = 20;
    private static final double DEPLOY_FOV_SCROLL_STEP = 2.0D;
    private static final double DEPLOY_FOV_MIN = 30.0D;
    private static final double DEPLOY_FOV_MAX = 110.0D;
    private static final float DEPLOY_FOV_RESTORE_SECONDS = 1.0F;
    private static final int PRIMARY_DEPLOY_HOTBAR_SLOT = 0;
    private static final int DEATH_FLOW_LOCKED_HOTBAR_SLOT = 6;
    private static final int UI_BUTTON_WIDTH = 132;
    private static final int UI_BUTTON_HEIGHT = 18;
    private static final int UI_BUTTON_GAP = 4;
    private static final int UI_BUTTON_MARGIN = 10;

    private final ClientLoadoutUiController loadoutUiController;
    private boolean deathMouseReleased;
    private long lastTeamColorSyncTick = -1L;
    private String lastTeamColorRoomPrefix = "";
    private boolean wasDeployUiOpen;
    private GDButton deployLoadoutButton;
    private GDButton matchEndExitButton;
    private boolean deployFovActive;
    private boolean deployFovRestoreActive;
    private double deployFovBase = -1.0D;
    private double deployFovRestoreStart = -1.0D;
    private float deployFovRestoreElapsedSeconds;
    private final ClientVehicleMountedHotbarLockService vehicleMountedHotbarLockService = new ClientVehicleMountedHotbarLockService();
    private String lastUploadedMainHandItemId = "";
    private String lastUploadedMainHandDisplayName = "";
    private long lastUploadedMainHandTick = -1L;
    private boolean wasVehiclePerspectiveAllowed;
    private boolean mountedVehicleControlSuppressed;

    ClientRoomSessionController(ClientLoadoutUiController loadoutUiController) {
        this.loadoutUiController = loadoutUiController;
    }

    void tick(Minecraft minecraft, ClientCaptureOverlayController overlayController) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            vehicleMountedHotbarLockService.reset();
            wasVehiclePerspectiveAllowed = false;
            return;
        }
        org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService.setForcedHudOnly(ClientRoomLockState.shouldForceHudOnly());
        org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService.setForcedVisible(ClientRoomLockState.shouldForceVisibleHud());
        loadoutUiController.tick();
        uploadMainHandItemNameIfNeeded(minecraft);
        vehicleMountedHotbarLockService.tick(minecraft);
        while (org.mods.gd656conquest.client.input.ClientKeyMappings.OPEN_LOADOUT_SCREEN.consumeClick()) {
            loadoutUiController.toggleConfigScreen();
        }
        while (org.mods.gd656conquest.client.input.ClientKeyMappings.COMMON_USE.consumeClick()) {
            handleCommonUsePress(minecraft);
        }
        boolean deployUiOpen = ClientRoomLockState.isDeployUiActive();
        if (deployUiOpen && !wasDeployUiOpen) {
            overlayController.onDeployUiOpened();
        } else if (!deployUiOpen && wasDeployUiOpen) {
            overlayController.onDeployUiClosed();
            resetPostDeployUseState(minecraft);
        }
        wasDeployUiOpen = deployUiOpen;
        String roomId = ClientHudDebugState.getRoomId();
        String roomState = ClientHudDebugState.getRoomState();
        boolean inRoom = roomId != null && !roomId.isBlank() && !"-".equals(roomId) && (roomState == null || !roomState.contains("未加入"));
        syncClientTeamColors(minecraft, roomId, inRoom);
        if (ClientDeathFlowState.hasActiveCameraOverride() && minecraft.screen instanceof ReceivingLevelScreen) {
            minecraft.setScreen(null);
        }
        boolean vehiclePerspectiveAllowed = shouldAllowVehiclePerspective(minecraft);
        if (wasVehiclePerspectiveAllowed && !vehiclePerspectiveAllowed) {
            enforceFirstPersonAfterVehicleExit(minecraft);
        } else if (!vehiclePerspectiveAllowed) {
            enforceFirstPersonForNonAdmin(minecraft);
        }
        wasVehiclePerspectiveAllowed = vehiclePerspectiveAllowed;
        enforceMountedVehicleControlLock(minecraft);
        enforceInitialMovementLock(minecraft);
        enforceDeathInputLock(minecraft);
        enforceHeldItemUseLock(minecraft);
        enforceLockedHotbarSelection(minecraft);
        enforceInventoryAccessLock(minecraft);
        syncMouseGrabState(minecraft, overlayController);
        tickDeployFovState(minecraft, 1.0F / 20.0F);
        enforceNoF3ForNonAdmin(minecraft);
    }

    void enforceFirstPersonDuringCameraSetup() {
        Minecraft minecraft = Minecraft.getInstance();
        if (shouldAllowVehiclePerspective(minecraft)) {
            return;
        }
        enforceFirstPersonForNonAdmin(minecraft);
    }

    void handleScreenOpening(ScreenEvent.Opening event, ClientCaptureOverlayController overlayController) {
        if (event.getNewScreen() instanceof InventoryScreen && shouldLockInventoryClient()) {
            event.setCanceled(true);
            return;
        }
        if (!ClientDeathFlowState.hasActiveCameraOverride()) {
            return;
        }
        if (event.getNewScreen() instanceof ReceivingLevelScreen) {
            event.setNewScreen(null);
            return;
        }
        if (overlayController.isDeployMouseScreen(event.getNewScreen())) {
            return;
        }
    }

    void handleKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (vehicleMountedHotbarLockService.shouldCancelHotbarKeyInput(minecraft, event)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return;
        }
        boolean attackKey = minecraft.options.keyAttack.matches(event.getKey(), event.getScanCode());
        boolean useKey = minecraft.options.keyUse.matches(event.getKey(), event.getScanCode());
        if (minecraft.screen == null && TaczClientInputCompat.shouldBlockCombatInputUntilRelease(minecraft, attackKey, useKey)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            TaczClientInputCompat.clearTransientCombatState(minecraft);
            return;
        }
        if (isAdmin(minecraft)) {
            return;
        }
        if (shouldBlockGameplayKeyInput(minecraft, event)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return;
        }
        if (shouldBlockHeldItemUseKeyInput(minecraft, event)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return;
        }
        if (minecraft.options.keyTogglePerspective.matches(event.getKey(), event.getScanCode())) {
            if (shouldAllowVehiclePerspective(minecraft)) {
                return;
            }
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            enforceFirstPersonForNonAdmin(minecraft);
            return;
        }
        if (event.getKey() == GLFW.GLFW_KEY_F3 || minecraft.options.keyShift.isDown() && event.getKey() == GLFW.GLFW_KEY_F3) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            enforceNoF3ForNonAdmin(minecraft);
        }
        if (event.getKey() == GLFW.GLFW_KEY_B || event.getKey() == GLFW.GLFW_KEY_G || event.getKey() == GLFW.GLFW_KEY_Q) {
            if (GLFW.glfwGetKey(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS) {
                if (event.isCancelable()) {
                    event.setCanceled(true);
                }
                enforceNoF3ForNonAdmin(minecraft);
            }
        }
        if (minecraft.options.keyInventory.matches(event.getKey(), event.getScanCode()) && shouldLockInventoryClient()) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            if (minecraft.player != null && minecraft.level != null && minecraft.level.getGameTime() % 20L == 0L) {
                minecraft.player.displayClientMessage(Component.literal("当前房间已禁用背包").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    boolean handleMouseButton(InputEvent.MouseButton event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        if (minecraft.screen != null) {
            return false;
        }
        double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / Math.max(1, minecraft.getWindow().getScreenWidth());
        double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / Math.max(1, minecraft.getWindow().getScreenHeight());
        if (event.getAction() == GLFW.GLFW_PRESS
            && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
            && ClientDownedState.handleLeftClick()) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        boolean allowReleasedMouseUiClick = shouldAllowReleasedMouseUiLeftClick(minecraft, event);
        boolean attackMouse = event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        boolean useMouse = event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        if (TaczClientInputCompat.shouldBlockCombatInputUntilRelease(minecraft, attackMouse, useMouse)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            TaczClientInputCompat.clearTransientCombatState(minecraft);
            return true;
        }
        if (!allowReleasedMouseUiClick && shouldBlockHeldItemUseMouseInput(minecraft, event)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        if (!allowReleasedMouseUiClick && shouldBlockGameplayMouseInput(minecraft)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        if (event.getAction() == GLFW.GLFW_PRESS && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && handleCornerActionButtonsClick(mouseX, mouseY, event.getButton())) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        return false;
    }

    boolean handleMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return false;
        }
        if (minecraft.screen != null) {
            return false;
        }
        boolean allowDeployReleasedMouseScrollZoom = shouldAllowDeployReleasedMouseScrollZoom(minecraft);
        if (vehicleMountedHotbarLockService.shouldCancelMouseScroll(minecraft)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        if (!allowDeployReleasedMouseScrollZoom && shouldBlockGameplayMouseScroll(minecraft)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        if (!allowDeployReleasedMouseScrollZoom && !ClientRoomLockState.shouldReleaseMouse()) {
            return false;
        }
        double delta = event.getScrollDelta();
        if (Math.abs(delta) <= 1.0E-6D) {
            return false;
        }
        double current = getCurrentFov(minecraft);
        if (deployFovBase < 0.0D) {
            deployFovBase = current;
        }
        deployFovActive = true;
        deployFovRestoreActive = false;
        deployFovRestoreElapsedSeconds = 0.0F;
        double target = Mth.clamp(current - delta * DEPLOY_FOV_SCROLL_STEP, DEPLOY_FOV_MIN, DEPLOY_FOV_MAX);
        setCurrentFov(minecraft, target);
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
        return true;
    }

    void handleMovementInput(MovementInputUpdateEvent event) {
        if (!ClientRoomLockState.shouldLockMovement()) {
            return;
        }
        event.getInput().forwardImpulse = 0.0F;
        event.getInput().leftImpulse = 0.0F;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = ClientRoomLockState.shouldForceCrouch();
    }

    void handleScreenCharTyped(ScreenEvent.CharacterTyped event) {
    }

    void renderSessionOverlay(GuiGraphics guiGraphics, float partialTick) {
        renderCornerActionButtons(guiGraphics, partialTick);
    }

    void renderVignetteOverlay(RenderGuiOverlayEvent.Post event) {
        renderVignetteOverlay(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    void renderVignetteOverlay(GuiGraphics guiGraphics, int width, int height) {
        float darknessAlpha = ClientDeathFlowState.currentDarknessAlpha();
        float hurtAlpha = ClientDeathFlowState.currentHurtFlashAlpha() * 0.4F;
        if (darknessAlpha <= 0.001F && hurtAlpha <= 0.001F) {
            return;
        }
        int drawX = (int) (-width * 0.25F);
        int drawY = (int) (-height * 0.25F);
        int drawWidth = (int) (width * 1.5F);
        int drawHeight = (int) (height * 1.5F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (darknessAlpha > 0.001F) {
            RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, darknessAlpha);
            guiGraphics.blit(VIGNETTE_TEXTURE, drawX, drawY, -90, 0.0F, 0.0F, drawWidth, drawHeight, VIGNETTE_TEXTURE_SIZE, VIGNETTE_TEXTURE_SIZE);
        }
        if (hurtAlpha > 0.001F) {
            RenderSystem.setShaderColor(0.8F, 0.08F, 0.08F, hurtAlpha);
            guiGraphics.blit(VIGNETTE_TEXTURE, drawX, drawY, -90, 0.0F, 0.0F, drawWidth, drawHeight, VIGNETTE_TEXTURE_SIZE, VIGNETTE_TEXTURE_SIZE);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    void handleLogout() {
        forceRestoreDeployFov(Minecraft.getInstance());
        deathMouseReleased = false;
        wasDeployUiOpen = false;
        deployFovActive = false;
        deployFovRestoreActive = false;
        deployFovBase = -1.0D;
        deployFovRestoreStart = -1.0D;
        deployFovRestoreElapsedSeconds = 0.0F;
        vehicleMountedHotbarLockService.reset();
        mountedVehicleControlSuppressed = false;
        lastUploadedMainHandItemId = "";
        lastUploadedMainHandDisplayName = "";
        lastUploadedMainHandTick = -1L;
        org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService.setForcedVisible(false);
        org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService.setForcedHudOnly(false);
        loadoutUiController.onClientLogout();
    }

    private void syncClientTeamColors(Minecraft minecraft, String roomId, boolean inRoom) {
        long gameTime = minecraft.level.getGameTime();
        if (gameTime == lastTeamColorSyncTick) {
            return;
        }
        lastTeamColorSyncTick = gameTime;
        Scoreboard scoreboard = minecraft.level.getScoreboard();
        if (scoreboard == null) {
            return;
        }
        if (!inRoom || roomId == null || roomId.isBlank() || "-".equals(roomId)) {
            restoreLastRoomTeamColors(scoreboard);
            return;
        }
        String roomPrefix = roomId.toLowerCase(Locale.ROOT) + "_";
        if (!(minecraft.player.getTeam() instanceof PlayerTeam localTeam)) {
            restoreLastRoomTeamColors(scoreboard);
            return;
        }
        TeamSide selfSide = parseTeamSide(localTeam.getName());
        if (selfSide == null || !selfSide.roomPrefix.equals(roomId.toLowerCase(Locale.ROOT))) {
            restoreLastRoomTeamColors(scoreboard);
            return;
        }
        PlayerTeam campA = scoreboard.getPlayerTeam(roomPrefix + "camp_a");
        PlayerTeam campB = scoreboard.getPlayerTeam(roomPrefix + "camp_b");
        if (campA != null) {
            campA.setColor(selfSide.campIndex == 1 ? ChatFormatting.BLUE : ChatFormatting.RED);
        }
        if (campB != null) {
            campB.setColor(selfSide.campIndex == 2 ? ChatFormatting.BLUE : ChatFormatting.RED);
        }
        lastTeamColorRoomPrefix = roomPrefix;
    }

    private void restoreLastRoomTeamColors(Scoreboard scoreboard) {
        if (lastTeamColorRoomPrefix == null || lastTeamColorRoomPrefix.isBlank()) {
            return;
        }
        PlayerTeam campA = scoreboard.getPlayerTeam(lastTeamColorRoomPrefix + "camp_a");
        PlayerTeam campB = scoreboard.getPlayerTeam(lastTeamColorRoomPrefix + "camp_b");
        if (campA != null) {
            campA.setColor(ChatFormatting.WHITE);
        }
        if (campB != null) {
            campB.setColor(ChatFormatting.WHITE);
        }
        lastTeamColorRoomPrefix = "";
    }

    private TeamSide parseTeamSide(String rawName) {
        String teamName = rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
        if (teamName.isEmpty()) return null;
        if (teamName.endsWith("_camp_a")) return new TeamSide(teamName.substring(0, teamName.length() - "_camp_a".length()), 1);
        if (teamName.endsWith("_camp_b")) return new TeamSide(teamName.substring(0, teamName.length() - "_camp_b".length()), 2);
        if (teamName.endsWith("_campa")) return new TeamSide(teamName.substring(0, teamName.length() - "_campa".length()), 1);
        if (teamName.endsWith("_campb")) return new TeamSide(teamName.substring(0, teamName.length() - "_campb".length()), 2);
        if (teamName.endsWith("_taiga")) return new TeamSide(teamName.substring(0, teamName.length() - "_taiga".length()), 1);
        if (teamName.endsWith("_savanna")) return new TeamSide(teamName.substring(0, teamName.length() - "_savanna".length()), 2);
        if (teamName.endsWith("_t")) return new TeamSide(teamName.substring(0, teamName.length() - "_t".length()), 1);
        if (teamName.endsWith("_s")) return new TeamSide(teamName.substring(0, teamName.length() - "_s".length()), 2);
        return null;
    }

    private void uploadMainHandItemNameIfNeeded(Minecraft minecraft) {
        ItemStack held = minecraft.player.getMainHandItem();
        ResourceLocation itemKey = held == null || held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem());
        String itemId = itemKey == null ? "" : itemKey.toString();
        String displayName = held == null || held.isEmpty() ? "" : normalizeText(held.getHoverName().getString());
        long gameTick = minecraft.level.getGameTime();
        boolean changed = !itemId.equals(lastUploadedMainHandItemId) || !displayName.equals(lastUploadedMainHandDisplayName);
        boolean intervalReached = lastUploadedMainHandTick < 0L || gameTick - lastUploadedMainHandTick >= MAIN_HAND_UPLOAD_INTERVAL_TICKS;
        if (!changed && !intervalReached) {
            return;
        }
        NetworkManager.getChannel().sendToServer(new MainHandItemNameUploadPacket(itemId, displayName));
        lastUploadedMainHandItemId = itemId;
        lastUploadedMainHandDisplayName = displayName;
        lastUploadedMainHandTick = gameTick;
    }

    private void handleCommonUsePress(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        if (ClientRoomLockState.shouldLockMovement()) {
            return;
        }
        if (ClientReviveState.isCommonUseReserved(minecraft)) {
            return;
        }
        NetworkManager.getChannel().sendToServer(new ServerboundAmmoBoxInteractPacket());
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void enforceNoF3ForNonAdmin(Minecraft minecraft) {
        if (isAdmin(minecraft)) {
            return;
        }
        trySetBooleanField(minecraft.options, "renderDebug", false);
        minecraft.getEntityRenderDispatcher().setRenderHitBoxes(false);
        if (minecraft.debugRenderer != null) {
            for (Field field : minecraft.debugRenderer.getClass().getDeclaredFields()) {
                if (field.getType() != boolean.class) {
                    continue;
                }
                String lower = field.getName().toLowerCase(Locale.ROOT);
                if (lower.contains("chunk") || lower.contains("debug")) {
                    try {
                        field.setAccessible(true);
                        field.setBoolean(minecraft.debugRenderer, false);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private void enforceFirstPersonForNonAdmin(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null || minecraft.player == null) {
            return;
        }
        if (isAdmin(minecraft) || ClientDeathFlowState.hasActiveCameraOverride()) {
            return;
        }
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private void enforceFirstPersonAfterVehicleExit(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null || minecraft.player == null) {
            return;
        }
        if (ClientDeathFlowState.hasActiveCameraOverride()) {
            return;
        }
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private boolean shouldAllowVehiclePerspective(Minecraft minecraft) {
        return minecraft != null
            && minecraft.player != null
            && minecraft.player.getVehicle() != null;
    }

    private boolean isAdmin(Minecraft minecraft) {
        return minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    private void enforceDeathInputLock(Minecraft minecraft) {
        boolean downedActive = ClientDownedState.isActive();
        if (!ClientRoomLockState.shouldLockMovement() && !downedActive) {
            return;
        }
        TaczClientInputCompat.clearTransientCombatState(minecraft);
        if (minecraft.player != null) {
            minecraft.player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        }
    }

    private void enforceInitialMovementLock(Minecraft minecraft) {
        if (!isInitialMovementLockActive(minecraft) || minecraft == null || minecraft.options == null) {
            return;
        }
        TaczClientInputCompat.clearTransientCombatState(minecraft);
        if (minecraft.player != null) {
            minecraft.player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        }
    }

    private void resetPostDeployUseState(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        TaczClientInputCompat.clearTransientCombatState(minecraft);
    }

    private void enforceHeldItemUseLock(Minecraft minecraft) {
        if (!shouldRestrictHeldItemUse(minecraft)) {
            return;
        }
        TaczClientInputCompat.clearTransientCombatState(minecraft);
    }

    private void enforceMountedVehicleControlLock(Minecraft minecraft) {
        boolean shouldSuppress = shouldSuppressMountedVehicleControls(minecraft);
        if (!shouldSuppress) {
            if (mountedVehicleControlSuppressed) {
                restoreMountedVehicleControlState(minecraft);
                mountedVehicleControlSuppressed = false;
            }
            return;
        }
        mountedVehicleControlSuppressed = true;
        for (KeyMapping keyMapping : resolveMountedVehicleSuppressedKeys(minecraft)) {
            releaseKeyMapping(keyMapping);
        }
        if (minecraft != null && minecraft.player != null && minecraft.player.getVehicle() != null) {
            minecraft.player.getVehicle().setDeltaMovement(0.0D, 0.0D, 0.0D);
        }
    }

    private void enforceLockedHotbarSelection(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        boolean roomHotbarLocked = ClientRoomLockState.shouldLockHotbarSelection();
        int vehicleLockedSlot = vehicleMountedHotbarLockService.resolveLockedHotbarSlot(minecraft);
        int targetSlot = roomHotbarLocked ? resolveLockedHotbarSlot(minecraft) : vehicleLockedSlot;
        if (targetSlot < 0) {
            return;
        }
        if (minecraft.player.getInventory().selected != targetSlot) {
            minecraft.player.getInventory().selected = targetSlot;
        }
    }

    private int resolveLockedHotbarSlot(Minecraft minecraft) {
        if (isInitialMovementLockActive(minecraft)) {
            int vehicleSlot = vehicleMountedHotbarLockService.resolveLockedHotbarSlot(minecraft);
            if (vehicleSlot >= 0) {
                return vehicleSlot;
            }
            return minecraft != null && minecraft.player != null
                ? minecraft.player.getInventory().selected
                : PRIMARY_DEPLOY_HOTBAR_SLOT;
        }
        return DEATH_FLOW_LOCKED_HOTBAR_SLOT;
    }

    private void enforceInventoryAccessLock(Minecraft minecraft) {
        if (shouldLockInventoryClient() && minecraft.screen instanceof InventoryScreen) {
            minecraft.setScreen(null);
        }
    }

    private boolean shouldLockInventoryClient() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return false;
        }
        if (minecraft.player.hasPermissions(2) && !ClientRoomLockState.isDebugInventoryLockActive()) {
            return false;
        }
        return ClientRoomLockState.shouldLockInventory();
    }

    private boolean shouldBlockGameplayKeyInput(Minecraft minecraft, InputEvent.Key event) {
        if (minecraft == null || event == null || minecraft.screen != null) {
            return false;
        }
        if (!ClientRoomLockState.shouldRestrictGameplayInteraction()) {
            return false;
        }
        if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) {
            return false;
        }
        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }
        if (minecraft.options != null && minecraft.options.keyJump.matches(event.getKey(), event.getScanCode())) {
            return false;
        }
        return true;
    }

    private boolean shouldBlockGameplayMouseInput(Minecraft minecraft) {
        return minecraft != null && minecraft.screen == null && ClientRoomLockState.shouldRestrictGameplayInteraction();
    }

    private boolean shouldAllowReleasedMouseUiLeftClick(Minecraft minecraft, InputEvent.MouseButton event) {
        return minecraft != null
            && event != null
            && minecraft.screen == null
            && event.getAction() == GLFW.GLFW_PRESS
            && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
            && ClientRoomLockState.shouldReleaseMouseForUi();
    }

    private boolean shouldAllowDeployReleasedMouseScrollZoom(Minecraft minecraft) {
        return minecraft != null
            && minecraft.screen == null
            && ClientRoomLockState.shouldReleaseMouseForUi()
            && ClientRoomLockState.isDeployUiActive()
            && !ClientRoomLockState.isEndingState();
    }

    private boolean shouldBlockHeldItemUseMouseInput(Minecraft minecraft, InputEvent.MouseButton event) {
        if (minecraft == null || event == null || minecraft.screen != null || !shouldRestrictHeldItemUse(minecraft)) {
            return false;
        }
        return event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    }

    private boolean shouldBlockGameplayMouseScroll(Minecraft minecraft) {
        return minecraft != null && ClientRoomLockState.shouldRestrictGameplayInteraction();
    }

    private boolean shouldRestrictHeldItemUse(Minecraft minecraft) {
        return minecraft != null && ClientRoomLockState.shouldRestrictHeldItemUse();
    }

    private boolean shouldBlockHeldItemUseKeyInput(Minecraft minecraft, InputEvent.Key event) {
        if (minecraft == null || event == null || minecraft.screen != null || !ClientRoomLockState.shouldRestrictHeldItemUse()) {
            return false;
        }
        if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) {
            return false;
        }
        return minecraft.options.keyAttack.matches(event.getKey(), event.getScanCode())
            || minecraft.options.keyUse.matches(event.getKey(), event.getScanCode())
            || minecraft.options.keyDrop.matches(event.getKey(), event.getScanCode())
            || minecraft.options.keySwapOffhand.matches(event.getKey(), event.getScanCode());
    }

    private boolean isInitialMovementLockActive(Minecraft minecraft) {
        return minecraft != null
            && minecraft.player != null
            && ClientRoomLockState.snapshot().reason().equals("initial_movement_lock");
    }

    private boolean shouldSuppressMountedVehicleControls(Minecraft minecraft) {
        return isInitialMovementLockActive(minecraft)
            && minecraft != null
            && minecraft.player != null
            && SuperbWarfareVehicleCompatBridge.isVehicleEntity(minecraft.player.getVehicle());
    }

    private KeyMapping[] resolveMountedVehicleSuppressedKeys(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return new KeyMapping[0];
        }
        KeyMapping[] hotbarSlots = minecraft.options.keyHotbarSlots;
        KeyMapping[] keys = new KeyMapping[9 + 10];
        int index = 0;
        keys[index++] = minecraft.options.keyUp;
        keys[index++] = minecraft.options.keyDown;
        keys[index++] = minecraft.options.keyLeft;
        keys[index++] = minecraft.options.keyRight;
        keys[index++] = minecraft.options.keyJump;
        keys[index++] = minecraft.options.keyShift;
        keys[index++] = minecraft.options.keySprint;
        keys[index++] = minecraft.options.keyAttack;
        keys[index++] = minecraft.options.keyUse;
        keys[index++] = minecraft.options.keyDrop;
        keys[index++] = minecraft.options.keySwapOffhand;
        for (int slot = 0; slot < hotbarSlots.length && index < keys.length; slot++) {
            keys[index++] = hotbarSlots[slot];
        }
        return keys;
    }

    private void restoreMountedVehicleControlState(Minecraft minecraft) {
        for (KeyMapping keyMapping : resolveMountedVehicleSuppressedKeys(minecraft)) {
            syncKeyMappingToPhysicalState(minecraft, keyMapping);
        }
    }

    private void releaseKeyMapping(KeyMapping keyMapping) {
        if (keyMapping == null) {
            return;
        }
        keyMapping.setDown(false);
        while (keyMapping.consumeClick()) {
        }
    }

    private void syncKeyMappingToPhysicalState(Minecraft minecraft, KeyMapping keyMapping) {
        if (keyMapping == null) {
            return;
        }
        keyMapping.setDown(isPhysicalInputPressed(minecraft, keyMapping));
    }

    private boolean isPhysicalInputPressed(Minecraft minecraft, KeyMapping keyMapping) {
        if (minecraft == null || minecraft.getWindow() == null || keyMapping == null) {
            return false;
        }
        long window = minecraft.getWindow().getWindow();
        InputConstants.Key key = keyMapping.getKey();
        if (key == null) {
            return false;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
    }

    private void syncMouseGrabState(Minecraft minecraft, ClientCaptureOverlayController overlayController) {
        if (minecraft.mouseHandler == null) {
            return;
        }
        boolean shouldReleaseMouse = ClientRoomLockState.shouldReleaseMouseForUi();
        if (shouldReleaseMouse) {
            if (minecraft.screen instanceof ReceivingLevelScreen) {
                minecraft.setScreen(null);
            }
            minecraft.mouseHandler.releaseMouse();
            deathMouseReleased = true;
            return;
        }
        if (!deathMouseReleased) {
            return;
        }
        if (minecraft.screen == null) {
            minecraft.mouseHandler.grabMouse();
            deathMouseReleased = false;
        }
    }

    private void tickDeployFovState(Minecraft minecraft, float deltaSeconds) {
        boolean deployActiveNow = ClientRoomLockState.shouldReleaseMouse();
        if (deployActiveNow) {
            if (!deployFovActive) {
                deployFovActive = true;
                deployFovRestoreActive = false;
                deployFovRestoreElapsedSeconds = 0.0F;
                deployFovBase = getCurrentFov(minecraft);
            }
            return;
        }
        if (deployFovActive) {
            deployFovActive = false;
            if (deployFovBase > 0.0D) {
                deployFovRestoreActive = true;
                deployFovRestoreStart = getCurrentFov(minecraft);
                deployFovRestoreElapsedSeconds = 0.0F;
            }
        }
        if (!deployFovRestoreActive || deployFovBase <= 0.0D) {
            return;
        }
        deployFovRestoreElapsedSeconds = Math.min(DEPLOY_FOV_RESTORE_SECONDS, deployFovRestoreElapsedSeconds + Math.max(0.0F, deltaSeconds));
        float t = Mth.clamp(deployFovRestoreElapsedSeconds / DEPLOY_FOV_RESTORE_SECONDS, 0.0F, 1.0F);
        float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        double fov = Mth.lerp(eased, deployFovRestoreStart, deployFovBase);
        setCurrentFov(minecraft, fov);
        if (t >= 0.999F) {
            setCurrentFov(minecraft, deployFovBase);
            deployFovRestoreActive = false;
            deployFovBase = -1.0D;
            deployFovRestoreStart = -1.0D;
            deployFovRestoreElapsedSeconds = 0.0F;
        }
    }

    private void forceRestoreDeployFov(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            deployFovActive = false;
            deployFovRestoreActive = false;
            deployFovBase = -1.0D;
            deployFovRestoreStart = -1.0D;
            deployFovRestoreElapsedSeconds = 0.0F;
            return;
        }
        if (deployFovBase > 0.0D) {
            setCurrentFov(minecraft, deployFovBase);
        }
        deployFovActive = false;
        deployFovRestoreActive = false;
        deployFovBase = -1.0D;
        deployFovRestoreStart = -1.0D;
        deployFovRestoreElapsedSeconds = 0.0F;
    }

    private void renderCornerActionButtons(GuiGraphics guiGraphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen instanceof LoadoutConfigScreen) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int mouseX = (int) Math.round(minecraft.mouseHandler.xpos() * width / Math.max(1, minecraft.getWindow().getScreenWidth()));
        int mouseY = (int) Math.round(minecraft.mouseHandler.ypos() * height / Math.max(1, minecraft.getWindow().getScreenHeight()));
        boolean ending = isEndingState();
        boolean deployOpen = ClientRoomLockState.isDeployUiActive() && !ending;
        if (deployOpen) {
            if (deployLoadoutButton == null) {
                deployLoadoutButton = new GDButton(0, 0, UI_BUTTON_WIDTH, UI_BUTTON_HEIGHT, Component.translatable("gd656conquest.client.gui.deploy.open_loadout"), btn -> loadoutUiController.requestOpenConfigScreen());
            }
            deployLoadoutButton.setX(width - UI_BUTTON_WIDTH - UI_BUTTON_MARGIN);
            deployLoadoutButton.setY(height - UI_BUTTON_HEIGHT - UI_BUTTON_MARGIN);
            deployLoadoutButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (ending) {
            if (matchEndExitButton == null) {
                matchEndExitButton = new GDButton(0, 0, UI_BUTTON_WIDTH, UI_BUTTON_HEIGHT, Component.translatable("gd656conquest.client.gui.deploy.leave_room"), btn -> NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.leaveSession()));
                matchEndExitButton.setTextColor(0xFFFF6B6B);
            }
            matchEndExitButton.setX(width - UI_BUTTON_WIDTH - UI_BUTTON_MARGIN);
            int y = height - UI_BUTTON_HEIGHT - UI_BUTTON_MARGIN;
            if (deployOpen) {
                y -= UI_BUTTON_HEIGHT + UI_BUTTON_GAP;
            }
            matchEndExitButton.setY(y);
            matchEndExitButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private boolean handleCornerActionButtonsClick(double mouseX, double mouseY, int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen instanceof LoadoutConfigScreen) {
            return false;
        }
        boolean ending = isEndingState();
        boolean deployOpen = ClientRoomLockState.isDeployUiActive() && !ending;
        if (deployOpen && deployLoadoutButton != null && deployLoadoutButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return ending && matchEndExitButton != null && matchEndExitButton.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isEndingState() {
        return ClientRoomLockState.isEndingState();
    }

    private double getCurrentFov(Minecraft minecraft) {
        Object value = minecraft.options.fov().get();
        return value instanceof Number number ? number.doubleValue() : 70.0D;
    }

    private void setCurrentFov(Minecraft minecraft, double value) {
        minecraft.options.fov().set((int) Math.round(Mth.clamp(value, DEPLOY_FOV_MIN, DEPLOY_FOV_MAX)));
    }

    private void trySetBooleanField(Object target, String fieldName, boolean value) {
        if (target == null) {
            return;
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(target, value);
        } catch (Exception ignored) {
        }
    }

    private record TeamSide(String roomPrefix, int campIndex) {
    }
}
