package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

final class ClientCaptureOverlayController {
    private final ClientCaptureOverlayRuntime runtime = new ClientCaptureOverlayRuntime();
    private final ClientDeployBoundaryOverlayRenderer deployBoundaryOverlayRenderer =
        new ClientDeployBoundaryOverlayRenderer(runtime.boundaryRenderer());
    private final ClientCaptureIconRenderer iconRenderer = new ClientCaptureIconRenderer();

    void onDeployUiOpened() {
        runtime.onDeployUiOpened();
    }

    void onDeployUiClosed() {
        runtime.onDeployUiClosed();
    }

    void handleClientLogout() {
        runtime.handleClientLogout();
    }

    boolean isDeployMouseScreen(Screen screen) {
        return runtime.isDeployMouseScreen(screen);
    }

    boolean shouldRenderCaptureLinesForStage(RenderLevelStageEvent.Stage stage) {
        return runtime.shouldRenderCaptureLinesForStage(stage);
    }

    void captureWorldProjectionState(RenderLevelStageEvent event) {
        runtime.captureWorldProjectionState(event);
    }

    void renderCaptureBoundaryLines(RenderLevelStageEvent event) {
        runtime.renderCaptureBoundaryLines(event);
    }

    void renderCaptureIconHudOverlay(RenderGuiOverlayEvent.Post event) {
        renderCaptureIconHudOverlay(event.getGuiGraphics());
    }

    void renderCaptureIconHudOverlay(net.minecraft.client.gui.GuiGraphics guiGraphics) {
        if (!runtime.ensureWorldReady()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientCaptureOverlayState state = runtime.state();
        boolean deployPhaseActive = org.mods.gd656conquest.client.room.ClientDeathFlowState.shouldRenderDeployBoundaries();
        boolean downedActive = org.mods.gd656conquest.client.room.ClientDownedState.isActive();
        if (org.mods.gd656conquest.client.room.ClientDeathFlowState.hasActiveCameraOverride()
            && !downedActive
            && !deployPhaseActive) {
            runtime.clearDeploySelection();
            runtime.syncOverlayState();
            if (!runtime.hasCaptureIcons()) {
                return;
            }
            float deltaSeconds = runtime.resolveCaptureRenderDeltaSeconds();
            runtime.updateInterpolatedProgress(deltaSeconds);
            iconRenderer.renderHudQueueOnly(state, guiGraphics, runtime.resolveEffectiveSelfTeam(), deltaSeconds);
            return;
        }
        runtime.syncOverlayState();
        if (!runtime.hasCaptureIcons()) {
            return;
        }
        float deltaSeconds = runtime.resolveCaptureRenderDeltaSeconds();
        runtime.updateInterpolatedProgress(deltaSeconds);
        iconRenderer.renderCaptureIconHudOverlay(state, guiGraphics, runtime.resolveEffectiveSelfTeam(), deltaSeconds);
    }

    void renderDeployBoundaryHudOverlay(RenderGuiOverlayEvent.Post event) {
        if (!runtime.ensureWorldReady()) {
            return;
        }
        runtime.syncOverlayState();
        deployBoundaryOverlayRenderer.renderDeployBoundaryOverlay(runtime.state(), event, runtime.resolveEffectiveSelfTeam());
    }

    void renderDeployBoundaryHudOverlay(net.minecraft.client.gui.GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (!runtime.ensureWorldReady()) {
            return;
        }
        runtime.syncOverlayState();
        deployBoundaryOverlayRenderer.renderDeployBoundaryOverlay(runtime.state(), guiGraphics, screenWidth, screenHeight, runtime.resolveEffectiveSelfTeam());
    }

    void handleMouseInput(InputEvent.MouseButton event) {
        runtime.handleMouseInput(event);
    }

    ClientCaptureOverlayModel.TeamToken resolveSelfTeamToken() {
        return runtime.resolveSelfTeamToken();
    }
}
