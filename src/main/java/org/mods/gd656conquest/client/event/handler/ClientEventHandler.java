package org.mods.gd656conquest.client.event.handler;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import org.mods.gd656conquest.client.animation.ClientPlayerAnimatorDebugState;
import org.mods.gd656conquest.client.audio.ClientMusicTriggerService;
import org.mods.gd656conquest.client.hud.ClientHudDebugPreviewState;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.hud.ClientHudRenderer;
import org.mods.gd656conquest.client.hud.ClientHudSubtitleManager;
import org.mods.gd656conquest.client.hud.ClientMatchResultOverlayState;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiController;
import org.mods.gd656conquest.client.marker.ClientMarkerDebugPreviewState;
import org.mods.gd656conquest.client.marker.ClientRoomMarkerState;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientDownedState;
import org.mods.gd656conquest.client.room.ClientReviveState;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.client.room.ClientRoomTeamMirror;
import org.mods.gd656conquest.common.audio.AudioManager;
import org.mods.gd656conquest.common.block.ConquestBlocks;
import org.mods.gd656conquest.common.entity.PlayerAnimatorDebugDimensionsState;

public final class ClientEventHandler {
    private static final ClientEventHandler INSTANCE = new ClientEventHandler();

    private final ClientHudRenderer hudRenderer = new ClientHudRenderer();
    private final ClientLoadoutUiController loadoutUiController = new ClientLoadoutUiController();
    private final ClientCaptureOverlayController captureOverlayController = new ClientCaptureOverlayController();
    private final ClientCaptureOutlineController captureOutlineController = new ClientCaptureOutlineController();
    private final ClientRoomSessionController roomSessionController = new ClientRoomSessionController(loadoutUiController);

    public static ClientEventHandler getInstance() {
        return INSTANCE;
    }

    private ClientEventHandler() {
    }

    public void handleClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        AudioManager.getInstance().tickClient();
        ClientMusicTriggerService.tick();
        ClientPlayerAnimatorDebugState.tick();
        ClientHudSubtitleManager.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ClientHudSubtitleManager.reset();
            captureOverlayController.handleClientLogout();
            captureOutlineController.clear();
            return;
        }
        while (org.mods.gd656conquest.client.input.ClientKeyMappings.PLACE_TACTICAL_MARKER.consumeClick()) {
            ClientRoomMarkerState.handleKeyPress(minecraft);
        }
        ClientRoomMarkerState.tick();
        ClientDeathFlowState.tick();
        ClientDownedState.tick();
        ClientReviveState.tick();
        ClientMatchResultOverlayState.tick();
        String roomId = ClientHudDebugState.getRoomId();
        String roomState = ClientHudDebugState.getRoomState();
        boolean inRoom = roomId != null && !roomId.isBlank() && !"-".equals(roomId) && (roomState == null || !roomState.contains("未加入"));
        if (!inRoom) {
            ClientHudSubtitleManager.reset();
        }
        roomSessionController.tick(minecraft, captureOverlayController);
        captureOutlineController.tick(minecraft);
    }

    public void handleRenderLevel(RenderLevelStageEvent event) {
        if (!captureOverlayController.shouldRenderCaptureLinesForStage(event.getStage())) {
            return;
        }
        ClientDeathFlowState.renderFrame(event.getPartialTick());
        captureOverlayController.captureWorldProjectionState(event);
        ClientRoomMarkerState.captureProjectionState(event);
        try {
            captureOverlayController.renderCaptureBoundaryLines(event);
        } catch (Throwable ignored) {
        }
        try {
            captureOutlineController.renderCaptureBlockOutlineLines(event, captureOverlayController.resolveSelfTeamToken());
        } catch (Throwable ignored) {
        }
    }

    public void handleCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        roomSessionController.enforceFirstPersonDuringCameraSetup();
        if (!ClientDeathFlowState.hasActiveCameraOverride()) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        ClientDeathFlowState.applyCameraOverride(event.getCamera(), partialTick);
        event.setYaw(ClientDeathFlowState.getInterpolatedCameraYaw(partialTick));
        event.setPitch(ClientDeathFlowState.getInterpolatedCameraPitch(partialTick));
    }

    public void handleBlockHighlight(RenderHighlightEvent.Block event) {
        if (event.getTarget().getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (ConquestBlocks.isCapturePoint(minecraft.level.getBlockState(event.getTarget().getBlockPos()))) {
            boolean holdingCapturePoint = minecraft.player != null
                && (ConquestBlocks.isCapturePointItem(minecraft.player.getMainHandItem().getItem())
                || ConquestBlocks.isCapturePointItem(minecraft.player.getOffhandItem().getItem()));
            if (!holdingCapturePoint) {
                event.setCanceled(true);
            }
        }
    }

    public void handleHudRender(RenderGuiOverlayEvent.Post event, boolean isMainOverlayPass) {
        // Main HUD now renders through registered Forge overlays so we can control ordering
        // relative to third-party HUDs and F1 visibility consistently.
    }

    public void renderBaseHudEffects(net.minecraftforge.client.gui.overlay.ForgeGui gui, net.minecraft.client.gui.GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        captureOverlayController.renderDeployBoundaryHudOverlay(guiGraphics, screenWidth, screenHeight);
        roomSessionController.renderVignetteOverlay(guiGraphics, screenWidth, screenHeight);
    }

    public void renderMainHud(net.minecraftforge.client.gui.overlay.ForgeGui gui, net.minecraft.client.gui.GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService.shouldHideAllHudButKeepHand()
            || org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService.shouldHideAllHudAndHand()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            if (!ClientMatchResultOverlayState.shouldHideConquestHud()) {
                hudRenderer.render(guiGraphics);
                captureOverlayController.renderCaptureIconHudOverlay(guiGraphics);
                ClientRoomMarkerState.renderHudOverlay(guiGraphics);
            }
            ClientMarkerDebugPreviewState.renderHudOverlay(guiGraphics);
            roomSessionController.renderSessionOverlay(guiGraphics, partialTick);
            ClientMatchResultOverlayState.render(guiGraphics);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    public void handleRenderHand(RenderHandEvent event) {
        if (event == null) {
            return;
        }
        if (org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService.shouldHideAllHudAndHand()) {
            event.setCanceled(true);
        }
    }

    public void handleClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMusicTriggerService.reset();
        AudioManager.getInstance().stopAllMusic(0);
        ClientRoomTeamMirror.reset();
        ClientHudDebugState.reset();
        ClientHudSubtitleManager.reset();
        ClientMatchResultOverlayState.clear();
        ClientDeathFlowState.clear();
        ClientDownedState.clear();
        ClientReviveState.reset();
        ClientRoomLockState.reset();
        ClientRoomMarkerState.reset();
        ClientMarkerDebugPreviewState.reset();
        ClientHudDebugPreviewState.reset();
        ClientPlayerAnimatorDebugState.reset();
        roomSessionController.handleLogout();
        captureOverlayController.handleClientLogout();
        captureOutlineController.clear();
    }

    public void handleClientDimensionChange(ClientPlayerNetworkEvent.Clone event) {
    }

    public void handleScreenOpening(ScreenEvent.Opening event) {
        roomSessionController.handleScreenOpening(event, captureOverlayController);
    }

    public void handleClientKeyInput(InputEvent.Key event) {
        roomSessionController.handleKeyInput(event);
    }

    public void handleMouseInput(InputEvent.MouseButton event) {
        if (roomSessionController.handleMouseButton(event)) {
            return;
        }
        captureOverlayController.handleMouseInput(event);
        if (event.isCancelable() && event.isCanceled()) {
            return;
        }
        if (ClientPlayerAnimatorDebugState.handleMouseInput(event)) {
            if (event.isCancelable() && Minecraft.getInstance().screen == null) {
                event.setCanceled(true);
            }
            return;
        }
    }

    public void handleMouseScroll(InputEvent.MouseScrollingEvent event) {
        roomSessionController.handleMouseScroll(event);
    }

    public void handleMovementInput(MovementInputUpdateEvent event) {
        roomSessionController.handleMovementInput(event);
    }

    public void handleEntitySize(EntityEvent.Size event) {
        PlayerAnimatorDebugDimensionsState.handleEntitySize(event);
    }

    public void handleScreenCharTyped(ScreenEvent.CharacterTyped event) {
        roomSessionController.handleScreenCharTyped(event);
    }
}
