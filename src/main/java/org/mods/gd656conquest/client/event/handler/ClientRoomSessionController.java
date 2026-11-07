/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.event.handler;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.mods.gd656conquest.client.compat.tacz.TaczClientInputCompat;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayController;
import org.mods.gd656conquest.client.event.handler.ClientVehicleMountedHotbarLockService;
import org.mods.gd656conquest.client.gui.elements.GDButton;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService;
import org.mods.gd656conquest.client.input.ClientKeyMappings;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiController;
import org.mods.gd656conquest.client.loadout.screen.LoadoutConfigScreen;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientDownedState;
import org.mods.gd656conquest.client.room.ClientReviveState;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.client.room.ClientRoomTeamMirror;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiActionPacket;
import org.mods.gd656conquest.network.packet.MainHandItemNameUploadPacket;
import org.mods.gd656conquest.network.packet.ServerboundAmmoBoxInteractPacket;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareVehicleCompatBridge;

final class ClientRoomSessionController {
    private static final ResourceLocation VIGNETTE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/vignette.png");
    private static final int VIGNETTE_TEXTURE_SIZE = 256;
    private static final int MAIN_HAND_UPLOAD_INTERVAL_TICKS = 20;
    private static final double DEPLOY_FOV_SCROLL_STEP = 2.0;
    private static final double DEPLOY_FOV_MIN = 30.0;
    private static final double DEPLOY_FOV_MAX = 110.0;
    private static final float DEPLOY_FOV_RESTORE_SECONDS = 1.0f;
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
    private GDButton battlefieldDeployButton;
    private GDButton deployVehicleButton;
    private GDButton deployTeamSwitchButton;
    private GDButton matchEndExitButton;
    private boolean deployFovActive;
    private boolean deployFovRestoreActive;
    private double deployFovBase = -1.0;
    private double deployFovRestoreStart = -1.0;
    private float deployFovRestoreElapsedSeconds;
    private final ClientVehicleMountedHotbarLockService vehicleMountedHotbarLockService = new ClientVehicleMountedHotbarLockService();
    private String lastUploadedMainHandItemId = "";
    private String lastUploadedMainHandDisplayName = "";
    private long lastUploadedMainHandTick = -1L;
    private boolean wasVehiclePerspectiveAllowed;
    private boolean mountedVehicleControlSuppressed;
    private boolean commonUseHeld;

    ClientRoomSessionController(ClientLoadoutUiController loadoutUiController) {
        this.loadoutUiController = loadoutUiController;
    }

    void tick(Minecraft minecraft, ClientCaptureOverlayController overlayController) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            this.vehicleMountedHotbarLockService.reset();
            this.wasVehiclePerspectiveAllowed = false;
            return;
        }
        ClientHudVisibilityCycleService.setForcedHudOnly(ClientRoomLockState.shouldForceHudOnly());
        ClientHudVisibilityCycleService.setForcedVisible(ClientRoomLockState.shouldForceVisibleHud());
        this.loadoutUiController.tick();
        this.uploadMainHandItemNameIfNeeded(minecraft);
        this.vehicleMountedHotbarLockService.tick(minecraft);
        while (ClientKeyMappings.OPEN_LOADOUT_SCREEN.consumeClick()) {
            this.loadoutUiController.toggleConfigScreen();
        }
        this.tickCommonUseState(minecraft);
        boolean deployUiOpen = ClientRoomLockState.isDeployUiActive();
        if (deployUiOpen && !this.wasDeployUiOpen) {
            overlayController.onDeployUiOpened();
        } else if (!deployUiOpen && this.wasDeployUiOpen) {
            overlayController.onDeployUiClosed();
            this.resetPostDeployUseState(minecraft);
        }
        this.wasDeployUiOpen = deployUiOpen;
        String roomId = ClientHudDebugState.getRoomId();
        String roomState = ClientHudDebugState.getRoomState();
        boolean inRoom = roomId != null && !roomId.isBlank() && !"-".equals(roomId) && (roomState == null || !roomState.contains("\u672a\u52a0\u5165"));
        this.syncClientTeamColors(minecraft, roomId, inRoom);
        if (ClientDeathFlowState.hasActiveCameraOverride() && minecraft.screen instanceof ReceivingLevelScreen) {
            minecraft.setScreen(null);
        }
        boolean vehiclePerspectiveAllowed = this.shouldAllowVehiclePerspective(minecraft);
        if (this.wasVehiclePerspectiveAllowed && !vehiclePerspectiveAllowed) {
            this.enforceFirstPersonAfterVehicleExit(minecraft);
        } else if (!vehiclePerspectiveAllowed) {
            this.enforceFirstPersonForNonAdmin(minecraft);
        }
        this.wasVehiclePerspectiveAllowed = vehiclePerspectiveAllowed;
        this.enforceMountedVehicleControlLock(minecraft);
        this.enforceInitialMovementLock(minecraft);
        this.enforceDeathInputLock(minecraft);
        this.enforceHeldItemUseLock(minecraft);
        this.enforceLockedHotbarSelection(minecraft);
        this.enforceInventoryAccessLock(minecraft);
        this.syncMouseGrabState(minecraft, overlayController);
        this.tickDeployFovState(minecraft, 0.05f);
        this.enforceNoF3ForNonAdmin(minecraft);
    }

    void enforceFirstPersonDuringCameraSetup() {
        Minecraft minecraft = Minecraft.getInstance();
        if (this.shouldAllowVehiclePerspective(minecraft)) {
            return;
        }
        this.enforceFirstPersonForNonAdmin(minecraft);
    }

    void handleScreenOpening(ScreenEvent.Opening event, ClientCaptureOverlayController overlayController) {
        if (event.getNewScreen() instanceof InventoryScreen && this.shouldLockInventoryClient()) {
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
        if (this.vehicleMountedHotbarLockService.shouldCancelHotbarKeyInput(minecraft, event)) {
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
        if (this.isAdmin(minecraft)) {
            return;
        }
        if (this.shouldBlockGameplayKeyInput(minecraft, event)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return;
        }
        if (this.shouldBlockHeldItemUseKeyInput(minecraft, event)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return;
        }
        if (minecraft.options.keyTogglePerspective.matches(event.getKey(), event.getScanCode())) {
            if (this.shouldAllowVehiclePerspective(minecraft)) {
                return;
            }
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            this.enforceFirstPersonForNonAdmin(minecraft);
            return;
        }
        if (event.getKey() == 292 || minecraft.options.keyShift.isDown() && event.getKey() == 292) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            this.enforceNoF3ForNonAdmin(minecraft);
        }
        if ((event.getKey() == 66 || event.getKey() == 71 || event.getKey() == 81) && GLFW.glfwGetKey(minecraft.getWindow().getWindow(), 292) == 1) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            this.enforceNoF3ForNonAdmin(minecraft);
        }
        if (minecraft.options.keyInventory.matches(event.getKey(), event.getScanCode()) && this.shouldLockInventoryClient()) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            if (minecraft.player != null && minecraft.level != null && minecraft.level.getGameTime() % 20L == 0L) {
                minecraft.player.displayClientMessage(Component.literal("\u5f53\u524d\u623f\u95f4\u5df2\u7981\u7528\u80cc\u5305").withStyle(ChatFormatting.RED), true);
            }
        }
        if (minecraft.options.keyDrop.matches(event.getKey(), event.getScanCode()) && this.shouldLockInventoryClient() && event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    boolean handleMouseButton(InputEvent.MouseButton event) {
        boolean useMouse;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        if (minecraft.screen != null) {
            return false;
        }
        if (event.getAction() != 0 && this.shouldLockInventoryClient() && (minecraft.options.keyInventory.matchesMouse(event.getButton()) || minecraft.options.keyDrop.matchesMouse(event.getButton()))) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        double mouseX = minecraft.mouseHandler.xpos() * (double)minecraft.getWindow().getGuiScaledWidth() / (double)Math.max(1, minecraft.getWindow().getScreenWidth());
        double mouseY = minecraft.mouseHandler.ypos() * (double)minecraft.getWindow().getGuiScaledHeight() / (double)Math.max(1, minecraft.getWindow().getScreenHeight());
        if (event.getAction() == 1 && event.getButton() == 0 && ClientDownedState.handleLeftClick()) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        boolean allowReleasedMouseUiClick = this.shouldAllowReleasedMouseUiLeftClick(minecraft, event);
        boolean attackMouse = event.getButton() == 0;
        boolean bl = useMouse = event.getButton() == 1;
        if (TaczClientInputCompat.shouldBlockCombatInputUntilRelease(minecraft, attackMouse, useMouse)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            TaczClientInputCompat.clearTransientCombatState(minecraft);
            return true;
        }
        if (!allowReleasedMouseUiClick && this.shouldBlockHeldItemUseMouseInput(minecraft, event)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        if (!allowReleasedMouseUiClick && this.shouldBlockGameplayMouseInput(minecraft)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        if (event.getAction() == 1 && event.getButton() == 0 && this.handleCornerActionButtonsClick(mouseX, mouseY, event.getButton())) {
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
        boolean allowDeployReleasedMouseScrollZoom = this.shouldAllowDeployReleasedMouseScrollZoom(minecraft);
        if (this.vehicleMountedHotbarLockService.shouldCancelMouseScroll(minecraft)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        if (!allowDeployReleasedMouseScrollZoom && this.shouldBlockGameplayMouseScroll(minecraft)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return true;
        }
        if (!allowDeployReleasedMouseScrollZoom && !ClientRoomLockState.shouldReleaseMouse()) {
            return false;
        }
        double delta = event.getScrollDelta();
        if (Math.abs(delta) <= 1.0E-6) {
            return false;
        }
        double current = this.getCurrentFov(minecraft);
        if (this.deployFovBase < 0.0) {
            this.deployFovBase = current;
        }
        this.deployFovActive = true;
        this.deployFovRestoreActive = false;
        this.deployFovRestoreElapsedSeconds = 0.0f;
        double target = Mth.clamp(current - delta * 2.0, 30.0, 110.0);
        this.setCurrentFov(minecraft, target);
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
        return true;
    }

    void handleMovementInput(MovementInputUpdateEvent event) {
        if (!ClientRoomLockState.shouldLockMovement()) {
            return;
        }
        event.getInput().forwardImpulse = 0.0f;
        event.getInput().leftImpulse = 0.0f;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = ClientRoomLockState.shouldForceCrouch();
    }

    void handleScreenCharTyped(ScreenEvent.CharacterTyped event) {
    }

    void renderSessionOverlay(GuiGraphics guiGraphics, float partialTick) {
        this.renderCornerActionButtons(guiGraphics, partialTick);
    }

    void renderVignetteOverlay(RenderGuiOverlayEvent.Post event) {
        this.renderVignetteOverlay(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    void renderVignetteOverlay(GuiGraphics guiGraphics, int width, int height) {
        float darknessAlpha = org.mods.gd656killicon.client.downed.DownedScene.ownsWorldGrade() ? 0.0f : ClientDeathFlowState.currentDarknessAlpha();
        float hurtAlpha = ClientDeathFlowState.currentHurtFlashAlpha() * 0.4f;
        if (darknessAlpha <= 0.001f && hurtAlpha <= 0.001f) {
            return;
        }
        int drawX = (int)((float)(-width) * 0.25f);
        int drawY = (int)((float)(-height) * 0.25f);
        int drawWidth = (int)((float)width * 1.5f);
        int drawHeight = (int)((float)height * 1.5f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (darknessAlpha > 0.001f) {
            RenderSystem.setShaderColor(0.0f, 0.0f, 0.0f, darknessAlpha);
            guiGraphics.blit(VIGNETTE_TEXTURE, drawX, drawY, -90, 0.0f, 0.0f, drawWidth, drawHeight, 256, 256);
        }
        if (hurtAlpha > 0.001f) {
            RenderSystem.setShaderColor(0.8f, 0.08f, 0.08f, hurtAlpha);
            guiGraphics.blit(VIGNETTE_TEXTURE, drawX, drawY, -90, 0.0f, 0.0f, drawWidth, drawHeight, 256, 256);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    void handleLogout() {
        this.forceRestoreDeployFov(Minecraft.getInstance());
        this.deathMouseReleased = false;
        this.wasDeployUiOpen = false;
        this.deployFovActive = false;
        this.deployFovRestoreActive = false;
        this.deployFovBase = -1.0;
        this.deployFovRestoreStart = -1.0;
        this.deployFovRestoreElapsedSeconds = 0.0f;
        this.vehicleMountedHotbarLockService.reset();
        this.mountedVehicleControlSuppressed = false;
        this.lastUploadedMainHandItemId = "";
        this.lastUploadedMainHandDisplayName = "";
        this.lastUploadedMainHandTick = -1L;
        this.commonUseHeld = false;
        ClientHudVisibilityCycleService.setForcedVisible(false);
        ClientHudVisibilityCycleService.setForcedHudOnly(false);
        this.loadoutUiController.onClientLogout();
    }

    private void syncClientTeamColors(Minecraft minecraft, String roomId, boolean inRoom) {
        long gameTime = minecraft.level.getGameTime();
        if (gameTime == this.lastTeamColorSyncTick) {
            return;
        }
        this.lastTeamColorSyncTick = gameTime;
        Scoreboard scoreboard = minecraft.level.getScoreboard();
        if (scoreboard == null) {
            return;
        }
        if (!inRoom || roomId == null || roomId.isBlank() || "-".equals(roomId)) {
            this.restoreLastRoomTeamColors(scoreboard);
            return;
        }
        String roomPrefix = roomId.toLowerCase(Locale.ROOT) + "_";
        Team team = minecraft.player.getTeam();
        if (!(team instanceof PlayerTeam)) {
            this.restoreLastRoomTeamColors(scoreboard);
            return;
        }
        PlayerTeam localTeam = (PlayerTeam)team;
        TeamSide selfSide = this.parseTeamSide(localTeam.getName());
        if (selfSide == null || !selfSide.roomPrefix.equals(roomId.toLowerCase(Locale.ROOT))) {
            this.restoreLastRoomTeamColors(scoreboard);
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
        this.lastTeamColorRoomPrefix = roomPrefix;
    }

    private void restoreLastRoomTeamColors(Scoreboard scoreboard) {
        if (this.lastTeamColorRoomPrefix == null || this.lastTeamColorRoomPrefix.isBlank()) {
            return;
        }
        PlayerTeam campA = scoreboard.getPlayerTeam(this.lastTeamColorRoomPrefix + "camp_a");
        PlayerTeam campB = scoreboard.getPlayerTeam(this.lastTeamColorRoomPrefix + "camp_b");
        if (campA != null) {
            campA.setColor(ChatFormatting.WHITE);
        }
        if (campB != null) {
            campB.setColor(ChatFormatting.WHITE);
        }
        this.lastTeamColorRoomPrefix = "";
    }

    private TeamSide parseTeamSide(String rawName) {
        String teamName;
        String string = teamName = rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
        if (teamName.isEmpty()) {
            return null;
        }
        if (teamName.endsWith("_camp_a")) {
            return new TeamSide(teamName.substring(0, teamName.length() - "_camp_a".length()), 1);
        }
        if (teamName.endsWith("_camp_b")) {
            return new TeamSide(teamName.substring(0, teamName.length() - "_camp_b".length()), 2);
        }
        if (teamName.endsWith("_campa")) {
            return new TeamSide(teamName.substring(0, teamName.length() - "_campa".length()), 1);
        }
        if (teamName.endsWith("_campb")) {
            return new TeamSide(teamName.substring(0, teamName.length() - "_campb".length()), 2);
        }
        if (teamName.endsWith("_taiga")) {
            return new TeamSide(teamName.substring(0, teamName.length() - "_taiga".length()), 1);
        }
        if (teamName.endsWith("_savanna")) {
            return new TeamSide(teamName.substring(0, teamName.length() - "_savanna".length()), 2);
        }
        if (teamName.endsWith("_t")) {
            return new TeamSide(teamName.substring(0, teamName.length() - "_t".length()), 1);
        }
        if (teamName.endsWith("_s")) {
            return new TeamSide(teamName.substring(0, teamName.length() - "_s".length()), 2);
        }
        return null;
    }

    private void uploadMainHandItemNameIfNeeded(Minecraft minecraft) {
        boolean intervalReached;
        ItemStack held = minecraft.player.getMainHandItem();
        ResourceLocation itemKey = held == null || held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem());
        String itemId = itemKey == null ? "" : itemKey.toString();
        String displayName = held == null || held.isEmpty() ? "" : this.normalizeText(held.getHoverName().getString());
        long gameTick = minecraft.level.getGameTime();
        boolean changed = !itemId.equals(this.lastUploadedMainHandItemId) || !displayName.equals(this.lastUploadedMainHandDisplayName);
        boolean bl = intervalReached = this.lastUploadedMainHandTick < 0L || gameTick - this.lastUploadedMainHandTick >= 20L;
        if (!changed && !intervalReached) {
            return;
        }
        NetworkManager.getChannel().sendToServer(new MainHandItemNameUploadPacket(itemId, displayName));
        this.lastUploadedMainHandItemId = itemId;
        this.lastUploadedMainHandDisplayName = displayName;
        this.lastUploadedMainHandTick = gameTick;
    }

    private void tickCommonUseState(Minecraft minecraft) {
        boolean shouldHold;
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            this.stopCommonUseHold();
            return;
        }
        boolean bl = shouldHold = minecraft.screen == null && !ClientRoomLockState.shouldLockMovement() && !ClientReviveState.isCommonUseReserved(minecraft) && ClientKeyMappings.COMMON_USE.isDown();
        if (shouldHold) {
            if (!this.commonUseHeld) {
                NetworkManager.getChannel().sendToServer(new ServerboundAmmoBoxInteractPacket(ServerboundAmmoBoxInteractPacket.Action.START));
                this.commonUseHeld = true;
            }
            return;
        }
        if (this.commonUseHeld) {
            NetworkManager.getChannel().sendToServer(new ServerboundAmmoBoxInteractPacket(ServerboundAmmoBoxInteractPacket.Action.STOP));
        }
        this.stopCommonUseHold();
    }

    private void stopCommonUseHold() {
        this.commonUseHeld = false;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void enforceNoF3ForNonAdmin(Minecraft minecraft) {
        if (this.isAdmin(minecraft)) {
            return;
        }
        this.trySetBooleanField(minecraft.options, "renderDebug", false);
        minecraft.getEntityRenderDispatcher().setRenderHitBoxes(false);
        if (minecraft.debugRenderer != null) {
            for (Field field : minecraft.debugRenderer.getClass().getDeclaredFields()) {
                String lower;
                if (field.getType() != Boolean.TYPE || !(lower = field.getName().toLowerCase(Locale.ROOT)).contains("chunk") && !lower.contains("debug")) continue;
                try {
                    field.setAccessible(true);
                    field.setBoolean(minecraft.debugRenderer, false);
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        }
    }

    private void enforceFirstPersonForNonAdmin(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null || minecraft.player == null) {
            return;
        }
        if (this.isAdmin(minecraft) || ClientDeathFlowState.hasActiveCameraOverride()) {
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
        return minecraft != null && minecraft.player != null && minecraft.player.getVehicle() != null;
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
            minecraft.player.setDeltaMovement(0.0, 0.0, 0.0);
        }
    }

    private void enforceInitialMovementLock(Minecraft minecraft) {
        if (!this.isInitialMovementLockActive(minecraft) || minecraft == null || minecraft.options == null) {
            return;
        }
        TaczClientInputCompat.clearTransientCombatState(minecraft);
        if (minecraft.player != null) {
            minecraft.player.setDeltaMovement(0.0, 0.0, 0.0);
        }
    }

    private void resetPostDeployUseState(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        TaczClientInputCompat.clearTransientCombatState(minecraft);
    }

    private void enforceHeldItemUseLock(Minecraft minecraft) {
        if (!this.shouldRestrictHeldItemUse(minecraft)) {
            return;
        }
        TaczClientInputCompat.clearTransientCombatState(minecraft);
    }

    private void enforceMountedVehicleControlLock(Minecraft minecraft) {
        boolean shouldSuppress = this.shouldSuppressMountedVehicleControls(minecraft);
        if (!shouldSuppress) {
            if (this.mountedVehicleControlSuppressed) {
                this.restoreMountedVehicleControlState(minecraft);
                this.mountedVehicleControlSuppressed = false;
            }
            return;
        }
        this.mountedVehicleControlSuppressed = true;
        for (KeyMapping keyMapping : this.resolveMountedVehicleSuppressedKeys(minecraft)) {
            this.releaseKeyMapping(keyMapping);
        }
        if (minecraft != null && minecraft.player != null && minecraft.player.getVehicle() != null) {
            minecraft.player.getVehicle().setDeltaMovement(0.0, 0.0, 0.0);
        }
    }

    private void enforceLockedHotbarSelection(Minecraft minecraft) {
        int targetSlot;
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        boolean roomHotbarLocked = ClientRoomLockState.shouldLockHotbarSelection();
        int vehicleLockedSlot = this.vehicleMountedHotbarLockService.resolveLockedHotbarSlot(minecraft);
        int n = targetSlot = roomHotbarLocked ? this.resolveLockedHotbarSlot(minecraft) : vehicleLockedSlot;
        if (targetSlot < 0) {
            return;
        }
        if (minecraft.player.getInventory().selected != targetSlot) {
            minecraft.player.getInventory().selected = targetSlot;
        }
    }

    private int resolveLockedHotbarSlot(Minecraft minecraft) {
        if (this.isInitialMovementLockActive(minecraft)) {
            int vehicleSlot = this.vehicleMountedHotbarLockService.resolveLockedHotbarSlot(minecraft);
            if (vehicleSlot >= 0) {
                return vehicleSlot;
            }
            return minecraft != null && minecraft.player != null ? minecraft.player.getInventory().selected : 0;
        }
        return 6;
    }

    private void enforceInventoryAccessLock(Minecraft minecraft) {
        if (this.shouldLockInventoryClient() && minecraft.screen instanceof InventoryScreen) {
            minecraft.setScreen(null);
        }
    }

    private boolean shouldLockInventoryClient() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return false;
        }
        if (ClientRoomLockState.shouldBypassForAdmin(minecraft)) {
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
        if (event.getAction() != 1 && event.getAction() != 2) {
            return false;
        }
        if (event.getKey() == 256) {
            return false;
        }
        return minecraft.options == null || !minecraft.options.keyJump.matches(event.getKey(), event.getScanCode());
    }

    private boolean shouldBlockGameplayMouseInput(Minecraft minecraft) {
        return minecraft != null && minecraft.screen == null && ClientRoomLockState.shouldRestrictGameplayInteraction();
    }

    private boolean shouldAllowReleasedMouseUiLeftClick(Minecraft minecraft, InputEvent.MouseButton event) {
        return minecraft != null && event != null && minecraft.screen == null && event.getAction() == 1 && event.getButton() == 0 && ClientRoomLockState.shouldReleaseMouseForUi();
    }

    private boolean shouldAllowDeployReleasedMouseScrollZoom(Minecraft minecraft) {
        return minecraft != null && minecraft.screen == null && ClientRoomLockState.shouldReleaseMouseForUi() && ClientRoomLockState.isDeployUiActive() && !ClientRoomLockState.isEndingState();
    }

    private boolean shouldBlockHeldItemUseMouseInput(Minecraft minecraft, InputEvent.MouseButton event) {
        if (minecraft == null || event == null || minecraft.screen != null || !this.shouldRestrictHeldItemUse(minecraft)) {
            return false;
        }
        return event.getButton() == 0 || event.getButton() == 1;
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
        if (event.getAction() != 1 && event.getAction() != 2) {
            return false;
        }
        return minecraft.options.keyAttack.matches(event.getKey(), event.getScanCode()) || minecraft.options.keyUse.matches(event.getKey(), event.getScanCode()) || minecraft.options.keyDrop.matches(event.getKey(), event.getScanCode()) || minecraft.options.keySwapOffhand.matches(event.getKey(), event.getScanCode());
    }

    private boolean isInitialMovementLockActive(Minecraft minecraft) {
        return minecraft != null && minecraft.player != null && ClientRoomLockState.snapshot().reason().equals("initial_movement_lock");
    }

    private boolean shouldSuppressMountedVehicleControls(Minecraft minecraft) {
        return this.isInitialMovementLockActive(minecraft) && minecraft != null && minecraft.player != null && SuperbWarfareVehicleCompatBridge.isVehicleEntity(minecraft.player.getVehicle());
    }

    private KeyMapping[] resolveMountedVehicleSuppressedKeys(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return new KeyMapping[0];
        }
        KeyMapping[] hotbarSlots = minecraft.options.keyHotbarSlots;
        KeyMapping[] keys = new KeyMapping[19];
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
        for (int slot = 0; slot < hotbarSlots.length && index < keys.length; ++slot) {
            keys[index++] = hotbarSlots[slot];
        }
        return keys;
    }

    private void restoreMountedVehicleControlState(Minecraft minecraft) {
        for (KeyMapping keyMapping : this.resolveMountedVehicleSuppressedKeys(minecraft)) {
            this.syncKeyMappingToPhysicalState(minecraft, keyMapping);
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
        keyMapping.setDown(this.isPhysicalInputPressed(minecraft, keyMapping));
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
            return GLFW.glfwGetMouseButton(window, key.getValue()) == 1;
        }
        return GLFW.glfwGetKey(window, key.getValue()) == 1;
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
            this.deathMouseReleased = true;
            return;
        }
        if (!this.deathMouseReleased) {
            return;
        }
        if (minecraft.screen == null) {
            minecraft.mouseHandler.grabMouse();
            this.deathMouseReleased = false;
        }
    }

    private void tickDeployFovState(Minecraft minecraft, float deltaSeconds) {
        boolean deployActiveNow = ClientRoomLockState.shouldReleaseMouse();
        if (deployActiveNow) {
            if (!this.deployFovActive) {
                this.deployFovActive = true;
                this.deployFovRestoreActive = false;
                this.deployFovRestoreElapsedSeconds = 0.0f;
                this.deployFovBase = this.getCurrentFov(minecraft);
            }
            return;
        }
        if (this.deployFovActive) {
            this.deployFovActive = false;
            if (this.deployFovBase > 0.0) {
                this.deployFovRestoreActive = true;
                this.deployFovRestoreStart = this.getCurrentFov(minecraft);
                this.deployFovRestoreElapsedSeconds = 0.0f;
            }
        }
        if (!this.deployFovRestoreActive || this.deployFovBase <= 0.0) {
            return;
        }
        this.deployFovRestoreElapsedSeconds = Math.min(1.0f, this.deployFovRestoreElapsedSeconds + Math.max(0.0f, deltaSeconds));
        float t = Mth.clamp(this.deployFovRestoreElapsedSeconds / 1.0f, 0.0f, 1.0f);
        float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
        double fov = Mth.lerp((double)eased, this.deployFovRestoreStart, this.deployFovBase);
        this.setCurrentFov(minecraft, fov);
        if (t >= 0.999f) {
            this.setCurrentFov(minecraft, this.deployFovBase);
            this.deployFovRestoreActive = false;
            this.deployFovBase = -1.0;
            this.deployFovRestoreStart = -1.0;
            this.deployFovRestoreElapsedSeconds = 0.0f;
        }
    }

    private void forceRestoreDeployFov(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            this.deployFovActive = false;
            this.deployFovRestoreActive = false;
            this.deployFovBase = -1.0;
            this.deployFovRestoreStart = -1.0;
            this.deployFovRestoreElapsedSeconds = 0.0f;
            return;
        }
        if (this.deployFovBase > 0.0) {
            this.setCurrentFov(minecraft, this.deployFovBase);
        }
        this.deployFovActive = false;
        this.deployFovRestoreActive = false;
        this.deployFovBase = -1.0;
        this.deployFovRestoreStart = -1.0;
        this.deployFovRestoreElapsedSeconds = 0.0f;
    }

    private void renderCornerActionButtons(GuiGraphics guiGraphics, float partialTick) {
        boolean deployOpen;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen instanceof LoadoutConfigScreen) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int mouseX = (int)Math.round(minecraft.mouseHandler.xpos() * (double)width / (double)Math.max(1, minecraft.getWindow().getScreenWidth()));
        int mouseY = (int)Math.round(minecraft.mouseHandler.ypos() * (double)height / (double)Math.max(1, minecraft.getWindow().getScreenHeight()));
        boolean ending = this.isEndingState();
        boolean bl = deployOpen = ClientRoomLockState.isDeployUiActive() && !ending;
        if (deployOpen) {
            CampSwitchButtonState switchState = this.resolveCampSwitchButtonState(minecraft);
            if (this.deployTeamSwitchButton == null) {
                this.deployTeamSwitchButton = new GDButton(0, 0, 132, 18, Component.empty(), btn -> NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.switchCamp()));
                this.deployTeamSwitchButton.setAutoScaleTextToFit(true);
                this.deployTeamSwitchButton.setMinAutoScale(0.52f);
            }
            if (this.deployLoadoutButton == null) {
                this.deployLoadoutButton = new GDButton(0, 0, 132, 18, Component.translatable("gd656conquest.client.gui.deploy.open_loadout"), btn -> this.loadoutUiController.requestOpenConfigScreen());
            }
            if (this.deployVehicleButton == null) {
                this.deployVehicleButton = new GDButton(0, 0, 132, 18, Component.translatable("gd656conquest.client.gui.loadout.vehicle.button"), btn -> this.loadoutUiController.requestOpenVehicleDeployScreen());
            }
            this.deployTeamSwitchButton.setMessage(Component.translatable("gd656conquest.client.gui.deploy.switch_team", switchState.targetDisplayName()));
            this.deployTeamSwitchButton.setTextColor(switchState.textColor());
            this.deployTeamSwitchButton.active = switchState.clickable();
            float scale=Math.min(width/1920f,height/1080f),ox=(width-1920*scale)/2,oy=(height-1080*scale)/2;
            if(this.battlefieldDeployButton==null)this.battlefieldDeployButton=new GDButton(0,0,100,30,Component.literal("部署"),b->BattlefieldDeploymentView.confirm());
            this.battlefieldDeployButton.active=BattlefieldDeploymentView.ready();
            GDButton[] buttons={this.deployLoadoutButton,this.deployVehicleButton,this.deployTeamSwitchButton,this.battlefieldDeployButton};
            var actions=org.mods.gd656killicon.client.gui.theme.BattlefieldDeploymentLayout.Action.values();
            for(int i=0;i<buttons.length;i++){
                var r=org.mods.gd656killicon.client.gui.theme.BattlefieldDeploymentLayout.action(org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme.current(),actions[i]);
                buttons[i].setX(Math.round(ox+r.x()*scale));buttons[i].setY(Math.round(oy+r.y()*scale));buttons[i].setWidth(Math.round(r.w()*scale));buttons[i].setHeight(Math.round(r.h()*scale));
            }
            BattlefieldDeploymentView.render(guiGraphics,switchState.visible(),"切换阵营  "+switchState.targetDisplayName(),switchState.clickable(),mouseX,mouseY);

        }
        if (ending) {
            if (this.matchEndExitButton == null) {
                this.matchEndExitButton = new GDButton(0, 0, 132, 18, Component.translatable("gd656conquest.client.gui.deploy.leave_room"), btn -> NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.leaveSession()));
                this.matchEndExitButton.setTextColor(-38037);
            }
            this.matchEndExitButton.setX(width - 132 - 10);
            int y = height - 18 - 10;
            if (deployOpen) {
                y -= 66;
            }
            this.matchEndExitButton.setY(y);
            this.matchEndExitButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private boolean handleCornerActionButtonsClick(double mouseX, double mouseY, int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen instanceof LoadoutConfigScreen) {
            return false;
        }
        boolean ending = this.isEndingState();
        boolean deployOpen = ClientRoomLockState.isDeployUiActive() && !ending;
        CampSwitchButtonState switchState = this.resolveCampSwitchButtonState(minecraft);
        if(deployOpen&&BattlefieldDeploymentView.openLoadout(mouseX,mouseY,button))return true;
        if(deployOpen&&this.battlefieldDeployButton!=null&&this.battlefieldDeployButton.mouseClicked(mouseX,mouseY,button))return true;
        if (deployOpen && switchState.visible() && this.deployTeamSwitchButton != null && this.deployTeamSwitchButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (deployOpen && this.deployVehicleButton != null && this.deployVehicleButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (deployOpen && this.deployLoadoutButton != null && this.deployLoadoutButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return ending && this.matchEndExitButton != null && this.matchEndExitButton.mouseClicked(mouseX, mouseY, button);
    }

    private CampSwitchButtonState resolveCampSwitchButtonState(Minecraft minecraft) {
        boolean admin = this.isAdmin(minecraft);
        boolean configAllowed = ClientHudDebugState.isPlayerCampSwitchAllowed();
        boolean isGunmasterSolo = false;
        String payload = ClientHudDebugState.getExtraPayload();
        if (payload != null && payload.startsWith("GMHUD|") && payload.contains("solo=1")) {
            isGunmasterSolo = true;
        }
        int selfCount = ClientRoomTeamMirror.getSelfTeamPlayerCount();
        int oppositeCount = ClientRoomTeamMirror.getOppositeTeamPlayerCount();
        boolean allowed = !isGunmasterSolo && (admin || configAllowed && oppositeCount < selfCount && selfCount - oppositeCount > 1);
        String targetDisplayName = ClientRoomTeamMirror.getOppositeTeamDisplayNameForSelf();
        if (targetDisplayName == null || targetDisplayName.isBlank()) {
            String selfTeam = ClientHudDebugState.getSelfTeam();
            targetDisplayName = selfTeam != null && (selfTeam.contains("CampA") || selfTeam.contains("\u9635\u8425A")) ? "CampB" : (selfTeam != null && (selfTeam.contains("CampB") || selfTeam.contains("\u9635\u8425B")) ? "CampA" : "\u654c\u65b9");
        }
        int textColor = admin ? -11443 : (allowed ? -1 : -7697782);
        return new CampSwitchButtonState(targetDisplayName, allowed, textColor, !isGunmasterSolo);
    }

    private boolean isEndingState() {
        return ClientRoomLockState.isEndingState();
    }

    private double getCurrentFov(Minecraft minecraft) {
        double d;
        Integer value = minecraft.options.fov().get();
        if (value instanceof Number) {
            Number number = value;
            d = number.doubleValue();
        } else {
            d = 70.0;
        }
        return d;
    }

    private void setCurrentFov(Minecraft minecraft, double value) {
        minecraft.options.fov().set((int)Math.round(Mth.clamp(value, 30.0, 110.0)));
    }

    private void trySetBooleanField(Object target, String fieldName, boolean value) {
        if (target == null) {
            return;
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(target, value);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private record TeamSide(String roomPrefix, int campIndex) {
    }

    private record CampSwitchButtonState(String targetDisplayName, boolean clickable, int textColor, boolean visible) {
    }
}

