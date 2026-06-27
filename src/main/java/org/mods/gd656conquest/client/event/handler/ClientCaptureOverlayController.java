package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;

final class ClientCaptureOverlayController {
    private final ClientCaptureOverlayState state = new ClientCaptureOverlayState();
    private final ClientCaptureOverlayCodec codec = new ClientCaptureOverlayCodec();
    private final ClientCaptureBoundaryRenderer boundaryRenderer = new ClientCaptureBoundaryRenderer();
    private final ClientDeployBoundaryOverlayRenderer deployBoundaryOverlayRenderer = new ClientDeployBoundaryOverlayRenderer(boundaryRenderer);
    private final ClientCaptureIconRenderer iconRenderer = new ClientCaptureIconRenderer();
    private final ClientCaptureDeployInput deployInput = new ClientCaptureDeployInput();

    void onDeployUiOpened() {
        state.prepareForDeployUiOpen();
    }

    void onDeployUiClosed() {
        state.handleDeployUiClosed();
    }

    void handleClientLogout() {
        state.reset();
    }

    boolean isDeployMouseScreen(Screen screen) {
        return false;
    }

    boolean shouldRenderCaptureLinesForStage(RenderLevelStageEvent.Stage stage) {
        return codec.shouldRenderCaptureLinesForStage(stage);
    }

    void captureWorldProjectionState(RenderLevelStageEvent event) {
        boundaryRenderer.captureWorldProjectionState(state, event);
    }

    void renderCaptureBoundaryLines(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        codec.syncCaptureOverlayState(state);
        boolean deployBoundaryActive = org.mods.gd656conquest.client.room.ClientDeathFlowState.shouldRenderDeployBoundaries();
        if (!state.adminDebugOverlayActive) {
            return;
        }
        ClientCaptureOverlayModel.TeamToken selfTeam = resolveEffectiveSelfTeam();
        boundaryRenderer.renderCaptureBoundaryLines(state, event, selfTeam, false);
    }

    void renderCaptureIconHudOverlay(RenderGuiOverlayEvent.Post event) {
        renderCaptureIconHudOverlay(event.getGuiGraphics());
    }

    void renderCaptureIconHudOverlay(net.minecraft.client.gui.GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            state.reset();
            return;
        }
        boolean deployPhaseActive = org.mods.gd656conquest.client.room.ClientDeathFlowState.shouldRenderDeployBoundaries();
        if (org.mods.gd656conquest.client.room.ClientDeathFlowState.hasActiveCameraOverride()
            && !deployPhaseActive) {
            state.clearDeploySelection();
            codec.syncCaptureOverlayState(state);
            if (state.cachedCaptureIcons.isEmpty()) {
                return;
            }
            float deltaSeconds = codec.resolveCaptureRenderDeltaSeconds(state);
            codec.updateInterpolatedProgress(state, deltaSeconds);
            iconRenderer.renderHudQueueOnly(state, guiGraphics, resolveEffectiveSelfTeam(), deltaSeconds);
            return;
        }
        codec.syncCaptureOverlayState(state);
        if (state.cachedCaptureIcons.isEmpty()) {
            return;
        }
        float deltaSeconds = codec.resolveCaptureRenderDeltaSeconds(state);
        codec.updateInterpolatedProgress(state, deltaSeconds);
        iconRenderer.renderCaptureIconHudOverlay(state, guiGraphics, resolveEffectiveSelfTeam(), deltaSeconds);
    }

    void renderDeployBoundaryHudOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            state.reset();
            return;
        }
        codec.syncCaptureOverlayState(state);
        deployBoundaryOverlayRenderer.renderDeployBoundaryOverlay(state, event, resolveEffectiveSelfTeam());
    }

    void renderDeployBoundaryHudOverlay(net.minecraft.client.gui.GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            state.reset();
            return;
        }
        codec.syncCaptureOverlayState(state);
        deployBoundaryOverlayRenderer.renderDeployBoundaryOverlay(state, guiGraphics, screenWidth, screenHeight, resolveEffectiveSelfTeam());
    }

    void noteMouseButtonActivity(InputEvent.MouseButton event) {
        if (event != null
            && event.getAction() == GLFW.GLFW_PRESS
            && org.mods.gd656conquest.client.room.ClientDeathFlowState.shouldRenderDeployBoundaries()) {
            state.noteDeployInteraction(System.currentTimeMillis());
        }
    }

    void handleMouseInput(InputEvent.MouseButton event) {
        deployInput.handleMouseInput(state, event, codec.resolveSelfTeamToken());
    }

    void handleMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (event != null
            && Math.abs(event.getScrollDelta()) > 1.0E-6D
            && org.mods.gd656conquest.client.room.ClientDeathFlowState.shouldRenderDeployBoundaries()) {
            state.noteDeployInteraction(System.currentTimeMillis());
        }
    }

    ClientCaptureOverlayModel.TeamToken resolveSelfTeamToken() {
        return codec.resolveSelfTeamToken();
    }

    private ClientCaptureOverlayModel.TeamToken resolveEffectiveSelfTeam() {
        ClientCaptureOverlayModel.TeamToken selfTeam = codec.resolveSelfTeamToken();
        if (state.adminDebugOverlayActive && selfTeam == ClientCaptureOverlayModel.TeamToken.NEUTRAL) {
            return ClientCaptureOverlayModel.TeamToken.CAMP_A;
        }
        return selfTeam;
    }
}
