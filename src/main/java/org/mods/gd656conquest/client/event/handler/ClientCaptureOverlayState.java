package org.mods.gd656conquest.client.event.handler;

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
    List<ClientCaptureOverlayModel.CaptureIconEntry> cachedObjectiveEntries = List.of();
    Map<String, ClientCaptureOverlayModel.CaptureIconEntry> objectiveEntryByCode = Map.of();
    List<ClientCaptureOverlayModel.BoundaryPolygon> cachedBoundaryPolygons = List.of();
    long boundaryGeometryVersion;
    final Map<String, ClientCaptureOverlayModel.ProgressInterpolationState> progressInterpolation = new HashMap<>();
    final Map<String, ClientCaptureOverlayModel.HudIconLayoutState> hudIconLayoutStates = new HashMap<>();
    final Map<String, ClientCaptureOverlayModel.TeammateIconScreenState> teammateIconScreenStates = new HashMap<>();
    final Map<String, ClientCaptureOverlayModel.WorldIconRenderState> worldIconRenderStates = new HashMap<>();
    ClientCaptureOverlayModel.SelectedCrosshairRenderState selectedCrosshairState;
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

    void reset() {
        cachedCaptureIconPayload = "-";
        cachedCaptureFocusPayload = "-";
        cachedAdminDebugPayload = "-";
        adminDebugOverlayActive = false;
        cachedCaptureIcons = List.of();
        cachedObjectiveEntries = List.of();
        objectiveEntryByCode = Map.of();
        cachedBoundaryPolygons = List.of();
        boundaryGeometryVersion = 0L;
        currentCaptureFocus = null;
        progressInterpolation.clear();
        hudIconLayoutStates.clear();
        teammateIconScreenStates.clear();
        worldIconRenderStates.clear();
        selectedCrosshairState = null;
        renderedDeployIcons.clear();
        lastRenderNanos = -1L;
        lastOverlaySyncGameTime = Long.MIN_VALUE;
        hoveredDeployIcon = null;
        selectedDeployIconId = "";
        lastClickedDeployIconId = "";
        lastHandledMousePressMillis = -1L;
        mouseCrossGapPixels = 0.0F;
        mouseCrossFeatherPixels = 0.0F;
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
        selectedCrosshairState = null;
        mouseCrossGapPixels = 0.0F;
        mouseCrossFeatherPixels = 0.0F;
    }

    void handleDeployUiClosed() {
        clearDeploySelection();
        teammateIconScreenStates.clear();
        worldIconRenderStates.entrySet().removeIf(entry -> {
            ClientCaptureOverlayModel.WorldIconRenderState renderState = entry.getValue();
            ClientCaptureOverlayModel.CaptureIconEntry icon = renderState == null ? null : renderState.entry;
            return icon == null || icon.kind().usesDeployAlpha();
        });
        clearProjectionState();
    }

    void clearProjectionState() {
        worldProjectionMatrix = null;
        worldProjectionPartialTick = 0.0F;
    }

    void markBoundaryGeometryDirty() {
        boundaryGeometryVersion++;
    }
}
