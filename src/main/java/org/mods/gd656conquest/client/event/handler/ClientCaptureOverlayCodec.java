package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.BoundaryPoint;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.BoundaryPolygon;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.BoundaryPolygonKey;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.CaptureFocusData;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.CaptureIconEntry;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.IconKind;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.ProgressInterpolationState;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.TeamToken;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.VehicleHudState;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ClientCaptureOverlayCodec {
    boolean shouldRenderCaptureLinesForStage(RenderLevelStageEvent.Stage stage) {
        if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return true;
        }
        return stage != null && "AFTER_TRANSLUCENT_BLOCKS".equals(stage.toString());
    }

    TeamToken resolveSelfTeamToken() {
        String selfTeam = ClientHudDebugState.getSelfTeam();
        if (selfTeam != null) {
            if (selfTeam.contains("CampA") || selfTeam.contains("\u9635\u8425A")) {
                return TeamToken.CAMP_A;
            }
            if (selfTeam.contains("CampB") || selfTeam.contains("\u9635\u8425B")) {
                return TeamToken.CAMP_B;
            }
        }
        return TeamToken.NEUTRAL;
    }

    void syncCaptureOverlayState(ClientCaptureOverlayState state) {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
        if (state.lastOverlaySyncGameTime == gameTime) {
            return;
        }
        state.lastOverlaySyncGameTime = gameTime;
        String roomPayload = ClientHudDebugState.getCaptureIcons();
        String debugPayload = ClientHudDebugState.getAdminDebugOverlay();
        boolean hasRoomPayload = isUsablePayload(roomPayload);
        boolean hasDebugPayload = isUsablePayload(debugPayload);
        String iconPayload;
        if (hasRoomPayload && hasDebugPayload) {
            iconPayload = roomPayload + ";" + debugPayload;
        } else if (hasRoomPayload) {
            iconPayload = roomPayload;
        } else if (hasDebugPayload) {
            iconPayload = debugPayload;
        } else {
            state.reset();
            return;
        }
        state.adminDebugOverlayActive = hasDebugPayload;
        if (!iconPayload.equals(state.cachedCaptureIconPayload)) {
            ParsedCaptureIcons parsedIcons = parseCaptureIcons(iconPayload);
            applyParsedBoundaryPolygons(state, parsedIcons.boundaryPolygons());
            state.cachedCaptureIcons = parsedIcons.entries();
            state.cachedObjectiveEntries = parsedIcons.objectiveEntries();
            state.objectiveEntryByCode = parsedIcons.objectiveEntryByCode();
            state.cachedCaptureIconPayload = iconPayload;
            state.cachedAdminDebugPayload = hasDebugPayload ? debugPayload : "-";
            syncProgressTargets(state, state.cachedCaptureIcons);
        }
        if (!hasRoomPayload) {
            state.cachedCaptureFocusPayload = "-";
            state.currentCaptureFocus = null;
            return;
        }
        String focusPayload = ClientHudDebugState.getCaptureFocus();
        if (!isUsablePayload(focusPayload)) {
            state.cachedCaptureFocusPayload = "-";
            state.currentCaptureFocus = null;
            return;
        }
        if (!focusPayload.equals(state.cachedCaptureFocusPayload)) {
            state.currentCaptureFocus = parseCaptureFocus(focusPayload);
            state.cachedCaptureFocusPayload = focusPayload;
        }
    }

    float resolveCaptureRenderDeltaSeconds(ClientCaptureOverlayState state) {
        long now = System.nanoTime();
        if (state.lastRenderNanos <= 0L) {
            state.lastRenderNanos = now;
            return 1.0F / 60.0F;
        }
        float deltaSeconds = Mth.clamp((now - state.lastRenderNanos) / 1_000_000_000.0F, 1.0F / 240.0F, 0.1F);
        state.lastRenderNanos = now;
        return deltaSeconds;
    }

    void updateInterpolatedProgress(ClientCaptureOverlayState state, float deltaSeconds) {
        if (state.progressInterpolation.isEmpty()) {
            return;
        }
        float step = deltaSeconds / ClientCaptureOverlayModel.CAPTURE_ICON_PROGRESS_INTERPOLATION_SECONDS;
        for (ProgressInterpolationState interpolationState : state.progressInterpolation.values()) {
            interpolationState.t = Mth.clamp(interpolationState.t + step, 0.0F, 1.0F);
        }
    }

    private void syncProgressTargets(ClientCaptureOverlayState state, List<CaptureIconEntry> entries) {
        Set<String> seen = new HashSet<>();
        for (CaptureIconEntry entry : entries) {
            String progressKey = entry.renderId();
            seen.add(progressKey);
            ProgressInterpolationState interpolationState = state.progressInterpolation.get(progressKey);
            float target = Mth.clamp(entry.progress(), 0.0F, 100.0F);
            if (interpolationState == null) {
                state.progressInterpolation.put(progressKey, new ProgressInterpolationState(target, target, 1.0F));
                continue;
            }
            float current = interpolationState.current();
            interpolationState.from = current;
            interpolationState.to = target;
            interpolationState.t = 0.0F;
        }
        state.progressInterpolation.keySet().retainAll(seen);
    }

    private ParsedCaptureIcons parseCaptureIcons(String payload) {
        List<CaptureIconEntry> entries = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        Map<BoundaryPolygonKey, List<BoundaryPoint>> polygonsByKey = new LinkedHashMap<>();
        for (String part : payload.split(";")) {
            String row = part.trim();
            if (row.isEmpty()) {
                continue;
            }
            String[] cols = row.split(",");
            if (cols.length < 8) {
                continue;
            }
            try {
                IconKind kind = IconKind.from(cols[0]);
                String code = cols[1];
                double x = decodeWorldCoordinate(kind, cols[2], true);
                double y = decodeWorldCoordinate(kind, cols[3], false) + kind.renderYOffset();
                double z = decodeWorldCoordinate(kind, cols[4], true);
                TeamToken owner = TeamToken.from(cols[5]);
                TeamToken driving = TeamToken.from(cols[6]);
                float progress = Float.parseFloat(cols[7]);
                String note = cols.length >= 9 ? decodeHudText(cols[8]) : "";
                boolean teammateCombat = false;
                boolean stageObjectiveProxy = false;
                VehicleHudState vehicleState = VehicleHudState.READY;
                boolean vehicleLowHealth = false;
                if (kind == IconKind.BOUNDARY) {
                    TeamToken boundaryTeam = TeamToken.from(cols[1]);
                    BoundaryPoint point = new BoundaryPoint(x, y, z, boundaryTeam);
                    String polygonId = note == null || note.isBlank() ? boundaryTeam.name() : note;
                    polygonsByKey.computeIfAbsent(new BoundaryPolygonKey(boundaryTeam, polygonId), ignored -> new ArrayList<>()).add(point);
                    continue;
                }
                if (kind == IconKind.TEAMMATE) {
                    teammateCombat = cols.length >= 10 && "1".equals(cols[9]);
                }
                if (kind == IconKind.SPAWN) {
                    stageObjectiveProxy = cols.length >= 10 && "BT_STAGE_PROXY".equalsIgnoreCase(cols[9]);
                }
                if (kind == IconKind.VEHICLE) {
                    vehicleState = cols.length >= 10 ? VehicleHudState.from(cols[9]) : VehicleHudState.READY;
                    vehicleLowHealth = cols.length >= 11 && "1".equals(cols[10]);
                }
                float normalized = kind == IconKind.TEAMMATE ? progress : Mth.clamp(progress, 0.0F, 100.0F);
                CaptureIconEntry entry = new CaptureIconEntry(
                    kind,
                    code,
                    x,
                    y,
                    z,
                    owner,
                    driving,
                    normalized,
                    note,
                    teammateCombat,
                    stageObjectiveProxy,
                    vehicleState,
                    vehicleLowHealth
                );
                if (dedupe.add(entry.renderId())) {
                    entries.add(entry);
                }
            } catch (Exception ignored) {
            }
        }
        List<CaptureIconEntry> objectiveEntries = buildObjectiveEntries(entries);
        return new ParsedCaptureIcons(
            List.copyOf(entries),
            freezeBoundaryPolygons(polygonsByKey),
            objectiveEntries,
            indexObjectiveEntriesByCode(objectiveEntries)
        );
    }

    private void applyParsedBoundaryPolygons(ClientCaptureOverlayState state, List<BoundaryPolygon> nextPolygons) {
        List<BoundaryPolygon> safePolygons = nextPolygons == null ? List.of() : nextPolygons;
        if (safePolygons.equals(state.cachedBoundaryPolygons)) {
            return;
        }
        state.cachedBoundaryPolygons = safePolygons;
        state.markBoundaryGeometryDirty();
    }

    private List<BoundaryPolygon> freezeBoundaryPolygons(Map<BoundaryPolygonKey, List<BoundaryPoint>> polygonsById) {
        if (polygonsById == null || polygonsById.isEmpty()) {
            return List.of();
        }
        List<BoundaryPolygon> polygons = new ArrayList<>();
        List<Map.Entry<BoundaryPolygonKey, List<BoundaryPoint>>> orderedEntries = new ArrayList<>(polygonsById.entrySet());
        orderedEntries.sort(
            Comparator.comparing((Map.Entry<BoundaryPolygonKey, List<BoundaryPoint>> entry) -> entry.getKey().team().name())
                .thenComparing(entry -> entry.getKey().polygonId(), String.CASE_INSENSITIVE_ORDER)
        );
        for (Map.Entry<BoundaryPolygonKey, List<BoundaryPoint>> entry : orderedEntries) {
            List<BoundaryPoint> polygon = entry.getValue();
            if (polygon != null && polygon.size() >= 2) {
                BoundaryPolygonKey key = entry.getKey();
                polygons.add(new BoundaryPolygon(key.polygonId(), key.team(), List.copyOf(polygon)));
            }
        }
        return polygons.isEmpty() ? List.of() : List.copyOf(polygons);
    }

    private List<CaptureIconEntry> buildObjectiveEntries(List<CaptureIconEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<CaptureIconEntry> objectives = new ArrayList<>();
        for (CaptureIconEntry entry : entries) {
            if (entry == null
                || entry.isSpawnPoint()
                || entry.isDebugMarker()
                || entry.isTeammateMarker()
                || entry.isBeaconMarker()
                || entry.isVehicleMarker()) {
                continue;
            }
            objectives.add(entry);
        }
        if (objectives.isEmpty()) {
            return List.of();
        }
        objectives.sort(Comparator.comparing(CaptureIconEntry::code, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(objectives);
    }

    private Map<String, CaptureIconEntry> indexObjectiveEntriesByCode(List<CaptureIconEntry> objectiveEntries) {
        if (objectiveEntries == null || objectiveEntries.isEmpty()) {
            return Map.of();
        }
        Map<String, CaptureIconEntry> byCode = new LinkedHashMap<>();
        for (CaptureIconEntry entry : objectiveEntries) {
            if (entry == null || entry.code() == null || entry.code().isBlank()) {
                continue;
            }
            byCode.putIfAbsent(entry.code(), entry);
        }
        return byCode.isEmpty() ? Map.of() : Map.copyOf(byCode);
    }

    private record ParsedCaptureIcons(
        List<CaptureIconEntry> entries,
        List<BoundaryPolygon> boundaryPolygons,
        List<CaptureIconEntry> objectiveEntries,
        Map<String, CaptureIconEntry> objectiveEntryByCode
    ) {
    }

    private CaptureFocusData parseCaptureFocus(String payload) {
        String[] cols = payload.split(",");
        if (cols.length < 7) {
            return null;
        }
        try {
            return new CaptureFocusData(
                cols[0],
                decodeHudText(cols[1]),
                Integer.parseInt(cols[2]),
                Integer.parseInt(cols[3]),
                Mth.clamp(Float.parseFloat(cols[4]), 0.0F, 100.0F),
                TeamToken.from(cols[5]),
                TeamToken.from(cols[6])
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String decodeHudText(String encoded) {
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return encoded == null ? "" : encoded;
        }
    }

    private boolean isUsablePayload(String payload) {
        return payload != null && !payload.isBlank() && !"-".equals(payload);
    }

    private double decodeWorldCoordinate(IconKind kind, String rawValue, boolean horizontalAxis) {
        double value = Double.parseDouble(rawValue);
        if (!horizontalAxis) {
            return value;
        }
        return shouldCenterOnBlock(kind) ? value + 0.5D : value;
    }

    private boolean shouldCenterOnBlock(IconKind kind) {
        return kind != IconKind.TEAMMATE && kind != IconKind.VEHICLE;
    }
}
