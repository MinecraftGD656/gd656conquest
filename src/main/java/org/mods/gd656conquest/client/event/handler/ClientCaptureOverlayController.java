/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.event.handler;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.mods.gd656conquest.client.event.handler.ClientCaptureBattleAreaLineSnapshot;
import org.mods.gd656conquest.client.event.handler.ClientCaptureBattleAreaPolygonSnapshot;
import org.mods.gd656conquest.client.event.handler.ClientCaptureIconRenderer;
import org.mods.gd656conquest.client.event.handler.ClientCaptureObjectiveWaypointSnapshot;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayRuntime;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayState;
import org.mods.gd656conquest.client.event.handler.ClientDeployBoundaryOverlayRenderer;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientDownedState;

final class ClientCaptureOverlayController {
    private final ClientCaptureOverlayRuntime runtime = new ClientCaptureOverlayRuntime();
    private final ClientDeployBoundaryOverlayRenderer deployBoundaryOverlayRenderer = new ClientDeployBoundaryOverlayRenderer(this.runtime.boundaryRenderer());
    private final ClientCaptureIconRenderer iconRenderer = new ClientCaptureIconRenderer();
    private long lastPayloadParseSample;
    private long lastPayloadParseSampleMillis;
    private int payloadParsePerSecond;

    ClientCaptureOverlayController() {
    }

    void onDeployUiOpened() {
        this.runtime.onDeployUiOpened();
    }

    void onDeployUiClosed() {
        this.runtime.onDeployUiClosed();
    }

    void handleClientLogout() {
        this.runtime.handleClientLogout();
        this.deployBoundaryOverlayRenderer.release();
        BattlefieldDeploymentView.clear();
    }

    void clearRenderCaches() {
        this.runtime.clearRenderCaches();
        this.deployBoundaryOverlayRenderer.release();
    }

    int debugTotalRendererCacheSize() {
        return this.runtime.debugTotalCacheSize();
    }

    String deployOverlayDebugLine() {
        return this.deployBoundaryOverlayRenderer.debugStatsLine();
    }

    String deployOverlayDebugDetailLine() {
        return this.deployBoundaryOverlayRenderer.debugStatsDetailLine();
    }

    int payloadParsePerSecond() {
        long now = System.currentTimeMillis();
        long count = this.runtime.debugPayloadParseCount();
        if (this.lastPayloadParseSampleMillis == 0L || now - this.lastPayloadParseSampleMillis >= 1000L) {
            this.payloadParsePerSecond = (int)(count - this.lastPayloadParseSample);
            this.lastPayloadParseSample = count;
            this.lastPayloadParseSampleMillis = now;
        }
        return this.payloadParsePerSecond;
    }

    boolean isDeployMouseScreen(Screen screen) {
        return this.runtime.isDeployMouseScreen(screen);
    }

    boolean shouldRenderCaptureLinesForStage(RenderLevelStageEvent.Stage stage) {
        return this.runtime.shouldRenderCaptureLinesForStage(stage);
    }

    void captureWorldProjectionState(RenderLevelStageEvent event) {
        this.runtime.captureWorldProjectionState(event);
    }

    void renderCaptureBoundaryLines(RenderLevelStageEvent event) {
        this.runtime.renderCaptureBoundaryLines(event);
    }

    void renderCaptureIconHudOverlay(RenderGuiOverlayEvent.Post event) {
        this.renderCaptureIconHudOverlay(event.getGuiGraphics());
    }

    void renderCaptureIconHudOverlay(GuiGraphics guiGraphics) {
        if (!this.runtime.ensureWorldReady()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientCaptureOverlayState state = this.runtime.state();
        boolean deployPhaseActive = ClientDeathFlowState.shouldRenderDeployBoundaries();
        boolean downedActive = ClientDownedState.isActive();
        if (ClientDeathFlowState.hasActiveCameraOverride() && !downedActive && !deployPhaseActive) {
            this.runtime.clearDeploySelection();
            this.runtime.syncOverlayState();
            if (!this.runtime.hasCaptureIcons()) {
                return;
            }
            float deltaSeconds = this.runtime.resolveCaptureRenderDeltaSeconds();
            this.runtime.updateInterpolatedProgress(deltaSeconds);
            this.iconRenderer.renderHudQueueOnly(state, guiGraphics, this.runtime.resolveEffectiveSelfTeam(), deltaSeconds);
            return;
        }
        this.runtime.syncOverlayState();
        if (!this.runtime.hasCaptureIcons()) {
            return;
        }
        float deltaSeconds = this.runtime.resolveCaptureRenderDeltaSeconds();
        this.runtime.updateInterpolatedProgress(deltaSeconds);
        if(deployPhaseActive)CaptureRegionOverlay.render(guiGraphics,state,this.runtime.resolveEffectiveSelfTeam(),ClientDeathFlowState.currentDeployIconAlpha());
        this.iconRenderer.renderCaptureIconHudOverlay(state, guiGraphics, this.runtime.resolveEffectiveSelfTeam(), deltaSeconds);
        BattlefieldDeploymentView.publish(state,this.runtime.resolveEffectiveSelfTeam());
    }

    void renderDeployBoundaryHudOverlay(RenderGuiOverlayEvent.Post event) {
        if (!this.runtime.ensureWorldReady()) {
            return;
        }
        this.runtime.syncOverlayState();
        this.deployBoundaryOverlayRenderer.renderDeployBoundaryOverlay(this.runtime.state(), event, this.runtime.resolveEffectiveSelfTeam());
    }

    void renderDeployBoundaryHudOverlay(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (!this.runtime.ensureWorldReady()) {
            return;
        }
        this.runtime.syncOverlayState();
        this.deployBoundaryOverlayRenderer.renderDeployBoundaryOverlay(this.runtime.state(), guiGraphics, screenWidth, screenHeight, this.runtime.resolveEffectiveSelfTeam());
    }

    void syncObjectiveSnapshotCache() {
        this.runtime.syncObjectiveSnapshotCache();
    }

    void handleMouseInput(InputEvent.MouseButton event) {
        this.runtime.handleMouseInput(event);
    }

    boolean handleKeyInput(InputEvent.Key event) {
        return this.runtime.handleKeyInput(event);
    }

    ClientCaptureOverlayModel.TeamToken resolveSelfTeamToken() {
        return this.runtime.resolveSelfTeamToken();
    }

    List<ClientCaptureObjectiveWaypointSnapshot> objectiveWaypointSnapshots() {
        return this.runtime.objectiveWaypointSnapshots();
    }

    ClientCaptureObjectiveWaypointSnapshot.TeamToken resolveObjectiveWaypointSelfTeam() {
        return this.runtime.resolveObjectiveWaypointSelfTeam();
    }

    List<ClientCaptureBattleAreaPolygonSnapshot> battleAreaPolygonSnapshots() {
        return this.runtime.battleAreaPolygonSnapshots();
    }

    List<ClientCaptureBattleAreaLineSnapshot> battleAreaLineSnapshots() {
        return this.runtime.battleAreaLineSnapshots();
    }

    ClientCaptureBattleAreaPolygonSnapshot.TeamToken resolveBattleAreaSelfTeam() {
        return this.runtime.resolveBattleAreaSelfTeam();
    }
}

