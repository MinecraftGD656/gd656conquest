package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.mods.gd656conquest.client.loadout.screen.LoadoutConfigScreen;
import org.mods.gd656conquest.client.loadout.screen.LoadoutVehicleDeployScreen;

final class ClientCaptureOverlayRuntime {
    private net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private final ClientCaptureOverlayState state = new ClientCaptureOverlayState();
    private final ClientCaptureOverlayCodec codec = new ClientCaptureOverlayCodec();
    private final ClientCaptureBoundaryRenderer boundaryRenderer = new ClientCaptureBoundaryRenderer();
    private final ClientCaptureDeployInput deployInput = new ClientCaptureDeployInput();

    ClientCaptureOverlayState state() {
        return state;
    }

    ClientCaptureBoundaryRenderer boundaryRenderer() {
        return boundaryRenderer;
    }

    boolean isDeployMouseScreen(Screen screen) {
        return screen instanceof LoadoutConfigScreen || screen instanceof LoadoutVehicleDeployScreen;
    }

    boolean shouldRenderCaptureLinesForStage(RenderLevelStageEvent.Stage stage) {
        return codec.shouldRenderCaptureLinesForStage(stage);
    }

    void onDeployUiOpened() {
        state.prepareForDeployUiOpen();
    }

    void onDeployUiClosed() {
        state.handleDeployUiClosed();
    }

    void handleClientLogout() {
        lastLevel=null;state.reset();
    }

    void clearRenderCaches() {
        state.clearRenderCaches();
    }

    int debugTotalCacheSize() {
        return state.debugTotalCacheSize();
    }

    boolean ensureWorldReady() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != lastLevel) {
            state.reset();BattlefieldDeploymentView.clear();lastLevel=minecraft.level;
        }
        if (minecraft.level == null || minecraft.player == null || !org.mods.gd656conquest.client.room.ClientRoomViewScope.current()) {
            state.reset();return false;
        }
        return true;
    }

    void captureWorldProjectionState(RenderLevelStageEvent event) {
        if(!ensureWorldReady())return;
        boundaryRenderer.captureWorldProjectionState(state, event);
    }

    void syncOverlayState() {
        codec.syncCaptureOverlayState(state);
    }

    void syncObjectiveSnapshotCache() {
        if (!ensureWorldReady()) {
            return;
        }
        codec.syncCaptureOverlayState(state);
    }

    // GD656Mods: 临时观测，测完删除
    long debugPayloadParseCount() {
        return codec.payloadParseCount;
    }

    float resolveCaptureRenderDeltaSeconds() {
        return codec.resolveCaptureRenderDeltaSeconds(state);
    }

    void updateInterpolatedProgress(float deltaSeconds) {
        codec.updateInterpolatedProgress(state, deltaSeconds);
    }

    boolean hasCaptureIcons() {
        return !state.cachedCaptureIcons.isEmpty();
    }

    void clearDeploySelection() {
        state.clearDeploySelection();
    }

    void renderCaptureBoundaryLines(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        syncOverlayState();
        if (!state.adminDebugOverlayActive) {
            return;
        }
        boundaryRenderer.renderCaptureBoundaryLines(state, event, resolveEffectiveSelfTeam(), false);
    }

    void handleMouseInput(InputEvent.MouseButton event) {
        deployInput.handleMouseInput(state, event, resolveSelfTeamToken());
    }

    boolean handleKeyInput(InputEvent.Key event) {
        return deployInput.handleKeyInput(state, event, resolveSelfTeamToken());
    }

    ClientCaptureOverlayModel.TeamToken resolveSelfTeamToken() {
        return codec.resolveSelfTeamToken();
    }

    ClientCaptureOverlayModel.TeamToken resolveEffectiveSelfTeam() {
        ClientCaptureOverlayModel.TeamToken selfTeam = resolveSelfTeamToken();
        if (state.adminDebugOverlayActive && selfTeam == ClientCaptureOverlayModel.TeamToken.NEUTRAL) {
            return ClientCaptureOverlayModel.TeamToken.CAMP_A;
        }
        return selfTeam;
    }

    java.util.List<ClientCaptureObjectiveWaypointSnapshot> objectiveWaypointSnapshots() {
        return state.cachedObjectiveWaypointSnapshots;
    }

    ClientCaptureObjectiveWaypointSnapshot.TeamToken resolveObjectiveWaypointSelfTeam() {
        ClientCaptureOverlayModel.TeamToken selfTeam = resolveEffectiveSelfTeam();
        if (selfTeam == ClientCaptureOverlayModel.TeamToken.CAMP_A) {
            return ClientCaptureObjectiveWaypointSnapshot.TeamToken.CAMP_A;
        }
        if (selfTeam == ClientCaptureOverlayModel.TeamToken.CAMP_B) {
            return ClientCaptureObjectiveWaypointSnapshot.TeamToken.CAMP_B;
        }
        return ClientCaptureObjectiveWaypointSnapshot.TeamToken.NEUTRAL;
    }

    java.util.List<ClientCaptureBattleAreaPolygonSnapshot> battleAreaPolygonSnapshots() {
        syncOverlayState();
        java.util.List<ClientCaptureOverlayModel.BoundaryPolygon> polygons =
            boundaryRenderer.resolveRenderablePolygons(state, resolveEffectiveSelfTeam(), ClientCaptureBoundaryRenderer.BoundaryDataSource.ROOM);
        if (polygons.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<ClientCaptureBattleAreaPolygonSnapshot> snapshots = new java.util.ArrayList<>(polygons.size());
        for (ClientCaptureOverlayModel.BoundaryPolygon polygon : polygons) {
            if (polygon == null || polygon.points() == null || polygon.points().size() < 2) {
                continue;
            }
            java.util.List<ClientCaptureBattleAreaPolygonSnapshot.Point> points = new java.util.ArrayList<>(polygon.points().size());
            for (ClientCaptureOverlayModel.BoundaryPoint point : polygon.points()) {
                if (point == null) {
                    continue;
                }
                points.add(new ClientCaptureBattleAreaPolygonSnapshot.Point(point.x(), point.z()));
            }
            if (!points.isEmpty()) {
                snapshots.add(
                    new ClientCaptureBattleAreaPolygonSnapshot(
                        polygon.polygonId(),
                        toBattleAreaTeam(polygon.team()),
                        java.util.List.copyOf(points)
                    )
                );
            }
        }
        return snapshots.isEmpty() ? java.util.List.of() : java.util.List.copyOf(snapshots);
    }

    java.util.List<ClientCaptureBattleAreaLineSnapshot> battleAreaLineSnapshots() {
        syncOverlayState();
        ClientCaptureBoundaryRenderer.ResolvedBoundarySegments segments =
            boundaryRenderer.resolveRenderableSegments(state, resolveEffectiveSelfTeam(), ClientCaptureBoundaryRenderer.BoundaryDataSource.ROOM);
        if (segments.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<ClientCaptureBattleAreaLineSnapshot> snapshots = new java.util.ArrayList<>();
        appendBattleAreaLines(snapshots, segments.selfSegments(), ClientCaptureBattleAreaLineSnapshot.LineKind.SELF);
        appendBattleAreaLines(snapshots, segments.enemySegments(), ClientCaptureBattleAreaLineSnapshot.LineKind.ENEMY);
        appendBattleAreaLines(snapshots, segments.neutralSegments(), ClientCaptureBattleAreaLineSnapshot.LineKind.NEUTRAL);
        appendBattleAreaLines(snapshots, segments.sharedSegments(), ClientCaptureBattleAreaLineSnapshot.LineKind.SHARED);
        return snapshots.isEmpty() ? java.util.List.of() : java.util.List.copyOf(snapshots);
    }

    ClientCaptureBattleAreaPolygonSnapshot.TeamToken resolveBattleAreaSelfTeam() {
        return toBattleAreaTeam(resolveEffectiveSelfTeam());
    }

    private void appendBattleAreaLines(
        java.util.List<ClientCaptureBattleAreaLineSnapshot> target,
        java.util.List<ClientCaptureOverlayModel.LineSegment> segments,
        ClientCaptureBattleAreaLineSnapshot.LineKind kind
    ) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        for (ClientCaptureOverlayModel.LineSegment segment : segments) {
            if (segment == null) {
                continue;
            }
            target.add(new ClientCaptureBattleAreaLineSnapshot(segment.ax(), segment.az(), segment.bx(), segment.bz(), kind));
        }
    }

    private ClientCaptureBattleAreaPolygonSnapshot.TeamToken toBattleAreaTeam(ClientCaptureOverlayModel.TeamToken teamToken) {
        if (teamToken == ClientCaptureOverlayModel.TeamToken.CAMP_A) {
            return ClientCaptureBattleAreaPolygonSnapshot.TeamToken.CAMP_A;
        }
        if (teamToken == ClientCaptureOverlayModel.TeamToken.CAMP_B) {
            return ClientCaptureBattleAreaPolygonSnapshot.TeamToken.CAMP_B;
        }
        return ClientCaptureBattleAreaPolygonSnapshot.TeamToken.NEUTRAL;
    }
}
