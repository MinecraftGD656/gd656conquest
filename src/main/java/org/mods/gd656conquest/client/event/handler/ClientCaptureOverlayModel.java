package org.mods.gd656conquest.client.event.handler;

import net.minecraft.util.Mth;

import java.util.List;

final class ClientCaptureOverlayModel {
    static final float CAPTURE_ICON_RADIUS = 7.5F;
    static final float TEAMMATE_ICON_RADIUS = 5.2F;
    static final float BEACON_ICON_RADIUS = TEAMMATE_ICON_RADIUS;
    static final float VEHICLE_ICON_RADIUS = TEAMMATE_ICON_RADIUS;
    static final String BEACON_ICON_GLYPH = "\u25CF";
    static final float HUD_QUEUE_ICON_RADIUS = 5.8F;
    static final float HUD_FOCUS_ICON_RADIUS = 11.6F;
    static final float WORLD_ICON_ALPHA_NEAR_CROSSHAIR = 0.05F;
    static final float WORLD_ICON_ALPHA_FAR_CROSSHAIR = 0.40F;
    static final float WORLD_ICON_CROSSHAIR_ALPHA_RADIUS_PIXELS = 300.0F;
    static final float WORLD_ICON_CROSSHAIR_ALPHA_CURVE = 1.20F;
    static final float HUD_QUEUE_ICON_ALPHA = 0.9F;
    static final float HUD_FOCUS_ICON_ALPHA = 0.95F;
    static final float WORLD_LABEL_SCALE = 0.68F;
    static final float HUD_QUEUE_LABEL_SCALE = 0.58F;
    static final float HUD_FOCUS_LABEL_SCALE = 0.76F;
    static final float CAPTURE_ICON_PROGRESS_INTERPOLATION_SECONDS = 0.25F;
    static final float ICON_LAYOUT_APPROACH_SECONDS = 0.18F;
    static final float TEAMMATE_ICON_INTERP_SECONDS = 5.0F / 20.0F;
    static final float WORLD_ICON_VISIBILITY_INTERP_SECONDS = 0.12F;
    static final double WORLD_ICON_HOVER_PADDING_PIXELS = 2.0D;
    static final float BOUNDARY_LINE_WORLD_WIDTH = 0.6F;
    static final float DEPLOY_MOUSE_CROSS_ALPHA = 1.0F;
    static final float DEPLOY_MOUSE_CROSS_HOVER_ALPHA = 1.0F;
    static final float DEPLOY_MOUSE_CROSS_PRESS_ALPHA = 1.0F;
    static final float DEPLOY_MOUSE_CROSS_GAP_PIXELS = 16.0F;
    static final float DEPLOY_MOUSE_CROSS_GAP_HOVER_PIXELS = 28.0F;
    static final float DEPLOY_MOUSE_CROSS_GAP_PRESS_PIXELS = 11.0F;
    static final float DEPLOY_MOUSE_CROSS_FEATHER_PIXELS = 52.0F;
    static final float DEPLOY_MOUSE_CROSS_FEATHER_HOVER_PIXELS = 86.0F;
    static final float DEPLOY_MOUSE_CROSS_FEATHER_PRESS_PIXELS = 34.0F;
    static final float DEPLOY_MOUSE_CROSS_ANIMATION_SHARPNESS = 14.0F;
    static final float DEPLOY_MOUSE_CROSS_LINE_THICKNESS = 2.0F;
    static final float DEPLOY_SELECTED_CROSS_ALPHA = 1.0F;
    static final float DEPLOY_SELECTED_CROSS_LINE_THICKNESS = 1.0F;
    static final float DEPLOY_SELECTED_CROSS_GAP_PIXELS = 17.0F;
    static final float DEPLOY_SELECTED_CROSS_FEATHER_PIXELS = 44.0F;
    static final float DEPLOY_SELECTED_CROSS_FADE_SECONDS = 0.20F;
    static final float DEPLOY_SELECTED_CROSS_PULSE_SECONDS = 1.0F;

    private ClientCaptureOverlayModel() {
    }

    enum TeamToken {
        NEUTRAL,
        CAMP_A,
        CAMP_B;

        static TeamToken from(String token) {
            if ("T".equalsIgnoreCase(token)) {
                return CAMP_A;
            }
            if ("S".equalsIgnoreCase(token)) {
                return CAMP_B;
            }
            return NEUTRAL;
        }
    }

    enum IconKind {
        CAPTURE,
        SPAWN,
        TEAMMATE,
        BEACON,
        BOUNDARY,
        VEHICLE,
        DEBUG;

        double renderYOffset() {
            return switch (this) {
                case SPAWN, TEAMMATE, BEACON -> 2.8D;
                case VEHICLE -> 3.2D;
                case BOUNDARY -> 0.2D;
                case DEBUG -> 2.5D;
                case CAPTURE -> 3.5D;
            };
        }

        boolean usesDeployAlpha() {
            return this == SPAWN || this == TEAMMATE || this == BEACON || this == VEHICLE;
        }

        float iconRadius() {
            return switch (this) {
                case TEAMMATE -> TEAMMATE_ICON_RADIUS;
                case BEACON -> BEACON_ICON_RADIUS;
                case VEHICLE -> VEHICLE_ICON_RADIUS;
                default -> CAPTURE_ICON_RADIUS;
            };
        }

        boolean useCompactGlyph() {
            return this == SPAWN || this == BEACON || this == VEHICLE;
        }

        String iconGlyph(String code) {
            if (this == BEACON) {
                return BEACON_ICON_GLYPH;
            }
            if (this == SPAWN) {
                return "\u25BC";
            }
            if (this == VEHICLE) {
                return "V";
            }
            return code;
        }

        DeployTargetAction resolveDeployTarget(String rawCode) {
            String safeCode = rawCode == null ? "" : rawCode.trim();
            return switch (this) {
                case TEAMMATE -> safeCode.isBlank() ? null : new DeployTargetAction(DeployTargetType.TEAMMATE, safeCode);
                case BEACON -> safeCode.isBlank() ? null : new DeployTargetAction(DeployTargetType.BEACON, safeCode);
                case SPAWN -> {
                    String captureCode = resolveSpawnCaptureCodeStatic(safeCode);
                    if (captureCode != null) {
                        yield new DeployTargetAction(DeployTargetType.CAPTURE, captureCode);
                    }
                    yield safeCode.isBlank() ? null : new DeployTargetAction(DeployTargetType.TEAM, safeCode);
                }
                case VEHICLE -> safeCode.isBlank() ? null : new DeployTargetAction(DeployTargetType.VEHICLE, safeCode);
                case CAPTURE -> safeCode.isBlank() ? null : new DeployTargetAction(DeployTargetType.CAPTURE, safeCode);
                default -> null;
            };
        }

        private static String resolveSpawnCaptureCodeStatic(String rawCode) {
            if (rawCode == null) {
                return null;
            }
            String trimmed = rawCode.trim();
            if (!trimmed.regionMatches(true, 0, "CAPTURE:", 0, "CAPTURE:".length())) {
                return null;
            }
            String code = trimmed.substring("CAPTURE:".length()).trim();
            return code.isBlank() ? null : code;
        }

        static IconKind from(String token) {
            if ("S".equalsIgnoreCase(token)) {
                return SPAWN;
            }
            if ("B".equalsIgnoreCase(token)) {
                return BOUNDARY;
            }
            if ("P".equalsIgnoreCase(token)) {
                return TEAMMATE;
            }
            if ("R".equalsIgnoreCase(token)) {
                return BEACON;
            }
            if ("V".equalsIgnoreCase(token)) {
                return VEHICLE;
            }
            if ("D".equalsIgnoreCase(token)) {
                return DEBUG;
            }
            return CAPTURE;
        }
    }

    enum VehicleHudState {
        READY,
        EMPTY,
        SQUAD,
        TEAM,
        FULL,
        WAITING,
        ENEMY;

        static VehicleHudState from(String token) {
            if (token == null || token.isBlank()) {
                return READY;
            }
            return switch (token.trim().toUpperCase()) {
                case "EMPTY" -> EMPTY;
                case "SQUAD" -> SQUAD;
                case "TEAM" -> TEAM;
                case "FULL" -> FULL;
                case "WAIT", "WAITING" -> WAITING;
                case "ENEMY" -> ENEMY;
                default -> READY;
            };
        }

        boolean deployable() {
            return this == READY || this == EMPTY || this == SQUAD;
        }

        String label() {
            return switch (this) {
                case READY -> "\u53ef\u90e8\u7f72";
                case EMPTY -> "\u7a7a\u8f7d\u5177";
                case SQUAD -> "\u5c0f\u961f\u9a7e\u9a76";
                case TEAM -> "\u53cb\u519b\u9a7e\u9a76";
                case FULL -> "\u5df2\u6ee1";
                case WAITING -> "\u7b49\u5f85\u4e2d";
                case ENEMY -> "\u654c\u519b\u63a7\u5236";
            };
        }
    }

    record CaptureIconEntry(
        IconKind kind,
        String code,
        double x,
        double y,
        double z,
        TeamToken owner,
        TeamToken driving,
        float progress,
        String note,
        boolean teammateCombat,
        boolean stageObjectiveProxy,
        VehicleHudState vehicleState,
        boolean vehicleLowHealth,
        boolean deploymentBlocked
    ) {
        String renderId() {
            return kind.name() + ":" + code;
        }

        boolean isDeployableFor(TeamToken selfTeam) {
            if (deploymentBlocked) return false;
            if (selfTeam == null || selfTeam == TeamToken.NEUTRAL || isDebugMarker()) {
                return false;
            }
            if (isVehicleMarker()) {
                return vehicleState != null && vehicleState.deployable();
            }
            if (owner != selfTeam) {
                return false;
            }
            return !isTeammateMarker() || !teammateCombat;
        }

        DeployTargetAction resolveDeployTarget() {
            return kind.resolveDeployTarget(code);
        }

        boolean isSpawnPoint() {
            return kind == IconKind.SPAWN;
        }

        boolean isDebugMarker() {
            return kind == IconKind.DEBUG;
        }

        boolean isTeammateMarker() {
            return kind == IconKind.TEAMMATE;
        }

        boolean isBeaconMarker() {
            return kind == IconKind.BEACON;
        }

        boolean isVehicleMarker() {
            return kind == IconKind.VEHICLE;
        }

        boolean isStageObjectiveProxy() {
            return kind == IconKind.SPAWN && stageObjectiveProxy;
        }
    }

    record DeployTargetAction(DeployTargetType type, String targetId) {
    }

    enum DeployTargetType {
        TEAM,
        CAPTURE,
        TEAMMATE,
        BEACON,
        VEHICLE
    }

    record BoundaryPoint(double x, double y, double z, TeamToken team) {
    }

    record BoundaryPolygonKey(TeamToken team, String polygonId) {
    }

    record BoundaryPolygon(String polygonId, TeamToken team, List<BoundaryPoint> points) {
    }

    enum BoundaryPerspective {
        SELF,
        ENEMY,
        NEUTRAL;

        boolean matches(TeamToken selfTeam, TeamToken polygonTeam) {
            if (this == NEUTRAL) {
                return polygonTeam == TeamToken.NEUTRAL;
            }
            if (selfTeam == null || selfTeam == TeamToken.NEUTRAL) {
                return false;
            }
            if (this == SELF) {
                return polygonTeam == selfTeam;
            }
            return polygonTeam != TeamToken.NEUTRAL && polygonTeam != selfTeam;
        }
    }

    record LineSegment(double ax, double ay, double az, double bx, double by, double bz) {
    }

    record ScreenProjection(double screenX, double screenY) {
    }

    record RenderedIconBounds(CaptureIconEntry entry, double screenX, double screenY, double hoverRadius) {
    }

    record HoverLabel(String text, double centerX, double topY, float alpha, int rgb) {
    }

    record CaptureFocusData(String code, String name, int allyCount, int enemyCount, float progress, TeamToken owner, TeamToken driving) {
    }

    static final class HudIconLayoutState {
        float x;
        float y;
        float radius;
        float alpha;
        float labelScale;

        HudIconLayoutState(float x, float y, float radius, float alpha, float labelScale) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.alpha = alpha;
            this.labelScale = labelScale;
        }
    }

    static final class TeammateIconScreenState {
        double startX;
        double startY;
        double targetX;
        double targetY;
        double screenX;
        double screenY;
        float yawStart;
        float targetYaw;
        float yaw;
        float positionElapsed;
        float yawElapsed;

        private TeammateIconScreenState(double screenX, double screenY, float yaw) {
            this.startX = screenX;
            this.startY = screenY;
            this.targetX = screenX;
            this.targetY = screenY;
            this.screenX = screenX;
            this.screenY = screenY;
            this.yawStart = yaw;
            this.targetYaw = yaw;
            this.yaw = yaw;
            this.positionElapsed = TEAMMATE_ICON_INTERP_SECONDS;
            this.yawElapsed = TEAMMATE_ICON_INTERP_SECONDS;
        }

        static TeammateIconScreenState create(double screenX, double screenY, float yaw) {
            return new TeammateIconScreenState(screenX, screenY, yaw);
        }
    }

    static final class ProgressInterpolationState {
        float from;
        float to;
        float t;

        ProgressInterpolationState(float from, float to, float t) {
            this.from = from;
            this.to = to;
            this.t = t;
        }

        float current() {
            return Mth.lerp(Mth.clamp(t, 0.0F, 1.0F), from, to);
        }
    }

    static final class WorldIconRenderState {
        CaptureIconEntry entry;
        double screenX;
        double screenY;
        float alpha;
        boolean seenThisFrame;

        WorldIconRenderState(CaptureIconEntry entry, double screenX, double screenY, float alpha) {
            this.entry = entry;
            this.screenX = screenX;
            this.screenY = screenY;
            this.alpha = alpha;
            this.seenThisFrame = false;
        }
    }

    static final class SelectedCrosshairRenderState {
        String renderId;
        double screenX;
        double screenY;
        float alpha;
        boolean seenThisFrame;

        SelectedCrosshairRenderState(String renderId, double screenX, double screenY) {
            this.renderId = renderId == null ? "" : renderId;
            this.screenX = screenX;
            this.screenY = screenY;
            this.alpha = 1.0F;
            this.seenThisFrame = false;
        }
    }
}
