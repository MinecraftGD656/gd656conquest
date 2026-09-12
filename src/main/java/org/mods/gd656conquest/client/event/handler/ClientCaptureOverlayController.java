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

    void clearRenderCaches() {
        runtime.clearRenderCaches();
    }

    int debugTotalRendererCacheSize() {
        return runtime.debugTotalCacheSize();
    }

    // GD656Mods: 临时观测入口，测完删除
    String deployOverlayDebugLine() {
        return deployBoundaryOverlayRenderer.debugStatsLine();
    }

    // GD656Mods: 临时观测入口，测完删除
    String deployOverlayDebugDetailLine() {
        return deployBoundaryOverlayRenderer.debugStatsDetailLine();
    }

    // GD656Mods: 临时观测，payload 解析次数每秒采样，测完删除
    private long lastPayloadParseSample;
    private long lastPayloadParseSampleMillis;
    private int payloadParsePerSecond;

    int payloadParsePerSecond() {
        long now = System.currentTimeMillis();
        long count = runtime.debugPayloadParseCount();
        if (lastPayloadParseSampleMillis == 0L || now - lastPayloadParseSampleMillis >= 1000L) {
            payloadParsePerSecond = (int) (count - lastPayloadParseSample);
            lastPayloadParseSample = count;
            lastPayloadParseSampleMillis = now;
        }
        return payloadParsePerSecond;
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

    void syncObjectiveSnapshotCache() {
        runtime.syncObjectiveSnapshotCache();
    }

    void handleMouseInput(InputEvent.MouseButton event) {
        runtime.handleMouseInput(event);
    }

    boolean handleKeyInput(InputEvent.Key event) {
        return runtime.handleKeyInput(event);
    }

    ClientCaptureOverlayModel.TeamToken resolveSelfTeamToken() {
        return runtime.resolveSelfTeamToken();
    }

    java.util.List<ClientCaptureObjectiveWaypointSnapshot> objectiveWaypointSnapshots() {
        return runtime.objectiveWaypointSnapshots();
    }

    ClientCaptureObjectiveWaypointSnapshot.TeamToken resolveObjectiveWaypointSelfTeam() {
        return runtime.resolveObjectiveWaypointSelfTeam();
    }

    java.util.List<ClientCaptureBattleAreaPolygonSnapshot> battleAreaPolygonSnapshots() {
        return runtime.battleAreaPolygonSnapshots();
    }

    java.util.List<ClientCaptureBattleAreaLineSnapshot> battleAreaLineSnapshots() {
        return runtime.battleAreaLineSnapshots();
    }

    ClientCaptureBattleAreaPolygonSnapshot.TeamToken resolveBattleAreaSelfTeam() {
        return runtime.resolveBattleAreaSelfTeam();
    }
}
