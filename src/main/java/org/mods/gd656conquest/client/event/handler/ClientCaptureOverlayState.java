package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ClientCaptureOverlayState {
    String cachedCaptureIconPayload = "-";
    String cachedCaptureFocusPayload = "-";
    String cachedAdminDebugPayload = "-";
    List<ClientCaptureOverlayModel.CaptureIconEntry> cachedCaptureIcons = List.of();
    List<ClientCaptureOverlayModel.BoundaryPolygon> cachedBoundaryPolygons = List.of();
    long boundaryGeometryVersion;
    final Map<String, ClientCaptureOverlayModel.ProgressInterpolationState> progressInterpolation = new HashMap<>();
    final Map<String, ClientCaptureOverlayModel.HudIconLayoutState> hudIconLayoutStates = new HashMap<>();
    final Map<String, ClientCaptureOverlayModel.TeammateIconScreenState> teammateIconScreenStates = new HashMap<>();
    final Map<String, ClientCaptureOverlayModel.WorldIconRenderState> worldIconRenderStates = new HashMap<>();
    final Map<String, ClientCaptureOverlayModel.SelectedCrosshairRenderState> selectedCrosshairStates = new HashMap<>();
    final List<ClientCaptureOverlayModel.RenderedIconBounds> renderedDeployIcons = new ArrayList<>();
    long lastRenderNanos = -1L;
    long lastOverlaySyncGameTime = Long.MIN_VALUE;
    boolean adminDebugOverlayActive;
    Matrix4f worldProjectionMatrix;
    float worldProjectionPartialTick;
    ClientCaptureOverlayModel.CaptureFocusData currentCaptureFocus;
    ClientCaptureOverlayModel.CaptureIconEntry hoveredDeployIcon;
    String selectedDeployIconId = "";
    String lastClickedDeployIconId = "";
    long lastHandledMousePressMillis = -1L;
    float mouseCrossGapPixels;
    float mouseCrossFeatherPixels;
    long lastDeployInteractionMillis = -1L;
    double lastDeployMouseGuiX = Double.NaN;
    double lastDeployMouseGuiY = Double.NaN;

    void reset() {
        cachedCaptureIconPayload = "-";
        cachedCaptureFocusPayload = "-";
        cachedAdminDebugPayload = "-";
        adminDebugOverlayActive = false;
        cachedCaptureIcons = List.of();
        cachedBoundaryPolygons = List.of();
        boundaryGeometryVersion = 0L;
        currentCaptureFocus = null;
        progressInterpolation.clear();
        hudIconLayoutStates.clear();
        teammateIconScreenStates.clear();
        worldIconRenderStates.clear();
        selectedCrosshairStates.clear();
        renderedDeployIcons.clear();
        lastRenderNanos = -1L;
        lastOverlaySyncGameTime = Long.MIN_VALUE;
        hoveredDeployIcon = null;
        selectedDeployIconId = "";
        lastClickedDeployIconId = "";
        lastHandledMousePressMillis = -1L;
        mouseCrossGapPixels = 0.0F;
        mouseCrossFeatherPixels = 0.0F;
        clearDeployInteractionState();
        clearProjectionState();
    }

    void clearDeploySelection() {
        hoveredDeployIcon = null;
        selectedDeployIconId = "";
        lastClickedDeployIconId = "";
        renderedDeployIcons.clear();
    }

    void prepareForDeployUiOpen() {
        clearDeploySelection();
        worldIconRenderStates.clear();
        selectedCrosshairStates.clear();
        mouseCrossGapPixels = 0.0F;
        mouseCrossFeatherPixels = 0.0F;
        noteDeployInteraction(System.currentTimeMillis());
    }

    void handleDeployUiClosed() {
        clearDeploySelection();
        teammateIconScreenStates.clear();
        worldIconRenderStates.entrySet().removeIf(entry -> {
            ClientCaptureOverlayModel.WorldIconRenderState renderState = entry.getValue();
            ClientCaptureOverlayModel.CaptureIconEntry icon = renderState == null ? null : renderState.entry;
            return icon == null || icon.kind().usesDeployAlpha();
        });
        clearDeployInteractionState();
        clearProjectionState();
    }

    void clearProjectionState() {
        worldProjectionMatrix = null;
        worldProjectionPartialTick = 0.0F;
    }

    void markBoundaryGeometryDirty() {
        boundaryGeometryVersion++;
    }

    void noteDeployInteraction(long nowMillis) {
        if (nowMillis <= 0L) {
            nowMillis = System.currentTimeMillis();
        }
        lastDeployInteractionMillis = nowMillis;
    }

    boolean hasRecentDeployInteraction(long nowMillis, long activeWindowMillis) {
        if (lastDeployInteractionMillis <= 0L) {
            return false;
        }
        return nowMillis - lastDeployInteractionMillis <= Math.max(0L, activeWindowMillis);
    }

    void sampleDeployMouseMovement(Minecraft minecraft, long nowMillis, double thresholdPixels) {
        if (minecraft == null || minecraft.getWindow() == null || minecraft.mouseHandler == null) {
            return;
        }
        double mouseGuiX = minecraft.mouseHandler.xpos()
            * minecraft.getWindow().getGuiScaledWidth()
            / Math.max(1, minecraft.getWindow().getScreenWidth());
        double mouseGuiY = minecraft.mouseHandler.ypos()
            * minecraft.getWindow().getGuiScaledHeight()
            / Math.max(1, minecraft.getWindow().getScreenHeight());
        if (Double.isNaN(lastDeployMouseGuiX) || Double.isNaN(lastDeployMouseGuiY)) {
            lastDeployMouseGuiX = mouseGuiX;
            lastDeployMouseGuiY = mouseGuiY;
            return;
        }
        double dx = mouseGuiX - lastDeployMouseGuiX;
        double dy = mouseGuiY - lastDeployMouseGuiY;
        if (dx * dx + dy * dy >= thresholdPixels * thresholdPixels) {
            noteDeployInteraction(nowMillis);
        }
        lastDeployMouseGuiX = mouseGuiX;
        lastDeployMouseGuiY = mouseGuiY;
    }

    private void clearDeployInteractionState() {
        lastDeployInteractionMillis = -1L;
        lastDeployMouseGuiX = Double.NaN;
        lastDeployMouseGuiY = Double.NaN;
    }
}
