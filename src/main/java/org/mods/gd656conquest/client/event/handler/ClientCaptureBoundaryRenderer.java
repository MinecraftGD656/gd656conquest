package org.mods.gd656conquest.client.event.handler;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.BoundaryPoint;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.BoundaryPolygon;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.LineSegment;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.TeamToken;
import org.mods.gd656conquest.client.render.ClientWorldHudProjectionSupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ClientCaptureBoundaryRenderer {
    private static final int ROUND_CAP_SEGMENTS = 18;
    private static final double ROUND_CAP_RADIUS_MULTIPLIER = 1.35D;

    private long cachedPolygonVersion = Long.MIN_VALUE;
    private List<BoundaryPolygon> cachedRenderablePolygons = List.of();
    private long cachedSegmentVersion = Long.MIN_VALUE;
    private TeamToken cachedSegmentSelfTeam = TeamToken.NEUTRAL;
    private BoundaryRenderMode cachedSegmentMode = BoundaryRenderMode.CONQUEST;
    private ResolvedBoundarySegments cachedRenderableSegments = ResolvedBoundarySegments.empty();

    void captureWorldProjectionState(ClientCaptureOverlayState state, RenderLevelStageEvent event) {
        state.worldProjectionMatrix = ClientWorldHudProjectionSupport.captureViewProjectionMatrix(event);
        if (state.worldProjectionMatrix == null) {
            state.clearProjectionState();
            return;
        }
        state.worldProjectionPartialTick = (float) event.getPartialTick();
    }

    void renderCaptureBoundaryLines(
        ClientCaptureOverlayState state,
        RenderLevelStageEvent event,
        TeamToken selfTeam,
        boolean applyDistanceCulling
    ) {
        ResolvedBoundarySegments segments = resolveRenderableSegments(state, selfTeam);
        if (segments.isEmpty()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            renderBoundaryLines(
                event.getPoseStack(),
                event.getCamera().getPosition(),
                selfTeam,
                segments,
                applyDistanceCulling
            );
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    List<BoundaryPolygon> resolveRenderablePolygons(ClientCaptureOverlayState state, TeamToken selfTeam) {
        if (state == null) {
            return List.of();
        }
        if (cachedPolygonVersion == state.boundaryGeometryVersion) {
            return cachedRenderablePolygons;
        }
        List<BoundaryPolygon> polygons = new ArrayList<>();
        polygons.addAll(resolveBoundaryPolygonsForTeamToken(state, TeamToken.CAMP_A));
        polygons.addAll(resolveBoundaryPolygonsForTeamToken(state, TeamToken.CAMP_B));
        polygons.addAll(resolveBoundaryPolygonsForTeamToken(state, TeamToken.NEUTRAL));
        cachedRenderablePolygons = polygons.isEmpty() ? List.of() : List.copyOf(polygons);
        cachedPolygonVersion = state.boundaryGeometryVersion;
        return cachedRenderablePolygons;
    }

    ResolvedBoundarySegments resolveRenderableSegments(ClientCaptureOverlayState state, TeamToken selfTeam) {
        if (state == null) {
            return ResolvedBoundarySegments.empty();
        }
        BoundaryRenderMode renderMode = resolveBoundaryRenderMode();
        if (cachedSegmentVersion == state.boundaryGeometryVersion
            && cachedSegmentSelfTeam == selfTeam
            && cachedSegmentMode == renderMode) {
            return cachedRenderableSegments;
        }
        List<BoundaryPolygon> campABoundary = resolveBoundaryPolygonsForTeamToken(state, TeamToken.CAMP_A);
        List<BoundaryPolygon> campBBoundary = resolveBoundaryPolygonsForTeamToken(state, TeamToken.CAMP_B);
        List<BoundaryPolygon> neutralBoundary = resolveBoundaryPolygonsForTeamToken(state, TeamToken.NEUTRAL);
        if (campABoundary.isEmpty() && campBBoundary.isEmpty() && neutralBoundary.isEmpty()) {
            cachedRenderableSegments = ResolvedBoundarySegments.empty();
            cachedSegmentVersion = state.boundaryGeometryVersion;
            cachedSegmentSelfTeam = selfTeam;
            cachedSegmentMode = renderMode;
            return cachedRenderableSegments;
        }
        if (renderMode == BoundaryRenderMode.CONQUEST) {
            cachedRenderableSegments = resolveConquestRenderableSegments(campABoundary, campBBoundary, neutralBoundary, selfTeam);
            cachedSegmentVersion = state.boundaryGeometryVersion;
            cachedSegmentSelfTeam = selfTeam;
            cachedSegmentMode = renderMode;
            return cachedRenderableSegments;
        }
        List<LineSegment> campASegments = collectVisibleSegments(campABoundary);
        List<LineSegment> campBSegments = collectVisibleSegments(campBBoundary);
        List<LineSegment> neutralSegments = collectVisibleSegments(neutralBoundary);
        BoundaryRenderBuckets campBuckets = splitSharedBoundarySegments(campASegments, campBSegments);
        if (selfTeam == TeamToken.CAMP_B) {
            cachedRenderableSegments = new ResolvedBoundarySegments(
                campBuckets.enemySegments(),
                campBuckets.selfSegments(),
                neutralSegments,
                campBuckets.sharedSegments()
            );
            cachedSegmentVersion = state.boundaryGeometryVersion;
            cachedSegmentSelfTeam = selfTeam;
            cachedSegmentMode = renderMode;
            return cachedRenderableSegments;
        }
        cachedRenderableSegments = new ResolvedBoundarySegments(
            campBuckets.selfSegments(),
            campBuckets.enemySegments(),
            neutralSegments,
            campBuckets.sharedSegments()
        );
        cachedSegmentVersion = state.boundaryGeometryVersion;
        cachedSegmentSelfTeam = selfTeam;
        cachedSegmentMode = renderMode;
        return cachedRenderableSegments;
    }

    private BoundaryRenderMode resolveBoundaryRenderMode() {
        return "BREAKTHROUGH".equalsIgnoreCase(ClientHudDebugState.getScoreMethod())
            ? BoundaryRenderMode.BREAKTHROUGH
            : BoundaryRenderMode.CONQUEST;
    }

    private ResolvedBoundarySegments resolveConquestRenderableSegments(
        List<BoundaryPolygon> campABoundary,
        List<BoundaryPolygon> campBBoundary,
        List<BoundaryPolygon> neutralBoundary,
        TeamToken selfTeam
    ) {
        BoundaryClassification campAClassified = classifyConquestBoundarySegments(campABoundary, campBBoundary);
        BoundaryClassification campBClassified = classifyConquestBoundarySegments(campBBoundary, campABoundary);
        List<LineSegment> neutralSegments = deduplicateLineSegments(collectPolygonSegments(neutralBoundary));
        List<LineSegment> sharedSegments = new ArrayList<>();
        sharedSegments.addAll(campAClassified.sharedSegments());
        sharedSegments.addAll(campBClassified.sharedSegments());
        if (selfTeam == TeamToken.CAMP_B) {
            return new ResolvedBoundarySegments(
                deduplicateLineSegments(campBClassified.coloredSegments()),
                deduplicateLineSegments(campAClassified.coloredSegments()),
                neutralSegments,
                deduplicateLineSegments(sharedSegments)
            );
        }
        return new ResolvedBoundarySegments(
            deduplicateLineSegments(campAClassified.coloredSegments()),
            deduplicateLineSegments(campBClassified.coloredSegments()),
            neutralSegments,
            deduplicateLineSegments(sharedSegments)
        );
    }

    private List<BoundaryPolygon> resolveBoundaryPolygonsForTeamToken(
        ClientCaptureOverlayState state,
        TeamToken teamToken
    ) {
        if (state.cachedBoundaryPolygons.isEmpty()) {
            return List.of();
        }
        List<BoundaryPolygon> matched = new ArrayList<>();
        for (BoundaryPolygon polygon : state.cachedBoundaryPolygons) {
            if (polygon == null || polygon.points() == null || polygon.points().size() < 2) {
                continue;
            }
            if (polygon.team() != teamToken) {
                continue;
            }
            matched.add(polygon);
        }
        return matched.isEmpty() ? List.of() : List.copyOf(matched);
    }

    private void renderBoundaryLines(
        PoseStack poseStack,
        Vec3 camera,
        TeamToken selfTeam,
        ResolvedBoundarySegments segments,
        boolean applyDistanceCulling
    ) {
        Matrix4f matrix = poseStack.last().pose();
        float[] selfColor = resolveBoundaryColorForTeam(selfTeam, true);
        float[] enemyColor = resolveBoundaryColorForTeam(selfTeam, false);
        float[] neutralColor = new float[]{1.0F, 1.0F, 1.0F};
        renderBoundarySegmentBucket(matrix, camera, segments.selfSegments(), selfColor[0], selfColor[1], selfColor[2], applyDistanceCulling);
        renderBoundarySegmentBucket(matrix, camera, segments.enemySegments(), enemyColor[0], enemyColor[1], enemyColor[2], applyDistanceCulling);
        renderBoundarySegmentBucket(matrix, camera, segments.neutralSegments(), neutralColor[0], neutralColor[1], neutralColor[2], applyDistanceCulling);
        renderBoundarySegmentBucket(matrix, camera, segments.sharedSegments(), neutralColor[0], neutralColor[1], neutralColor[2], applyDistanceCulling);
    }

    private void renderBoundarySegmentBucket(
        Matrix4f matrix,
        Vec3 camera,
        List<LineSegment> segments,
        float red,
        float green,
        float blue,
        boolean applyDistanceCulling
    ) {
        List<LineSegment> drawableSegments = collectDrawableSegments(segments, camera, applyDistanceCulling);
        if (drawableSegments.isEmpty()) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        appendBoundarySegments(buffer, matrix, camera, drawableSegments, red, green, blue, 0.95F);
        appendBoundaryEndpointCaps(buffer, matrix, camera, drawableSegments, red, green, blue, 0.95F);
        BufferUploader.drawWithShader(buffer.end());
    }

    private List<LineSegment> collectDrawableSegments(List<LineSegment> segments, Vec3 camera, boolean applyDistanceCulling) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        if (!applyDistanceCulling) {
            return segments;
        }
        List<LineSegment> drawable = new ArrayList<>();
        for (LineSegment segment : segments) {
            if (isBoundarySegmentWithinRenderDistance(camera, segment)) {
                drawable.add(segment);
            }
        }
        return drawable.isEmpty() ? List.of() : List.copyOf(drawable);
    }

    private void appendBoundarySegments(
        BufferBuilder buffer,
        Matrix4f matrix,
        Vec3 camera,
        List<LineSegment> segments,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        for (LineSegment segment : segments) {
            appendThickSegmentTriangles(buffer, matrix, camera, segment, red, green, blue, alpha);
        }
    }

    private void appendBoundaryEndpointCaps(
        BufferBuilder buffer,
        Matrix4f matrix,
        Vec3 camera,
        List<LineSegment> segments,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        Set<String> seenEndpoints = new HashSet<>();
        double radius = ClientCaptureOverlayModel.BOUNDARY_LINE_WORLD_WIDTH * 0.5D * ROUND_CAP_RADIUS_MULTIPLIER;
        for (LineSegment segment : segments) {
            appendRoundCapTriangles(buffer, matrix, camera, segment.ax(), segment.ay(), segment.az(), radius, red, green, blue, alpha, seenEndpoints);
            appendRoundCapTriangles(buffer, matrix, camera, segment.bx(), segment.by(), segment.bz(), radius, red, green, blue, alpha, seenEndpoints);
        }
    }

    private List<LineSegment> collectVisibleSegments(List<BoundaryPolygon> polygons) {
        List<LineSegment> visible = new ArrayList<>();
        if (polygons == null || polygons.isEmpty()) {
            return visible;
        }
        for (int polygonIndex = 0; polygonIndex < polygons.size(); polygonIndex++) {
            List<BoundaryPoint> polygon = polygons.get(polygonIndex).points();
            if (polygon == null || polygon.size() < 2) {
                continue;
            }
            visible.addAll(collectBoundaryVisiblePartsOutsideOthers(polygons, polygonIndex));
        }
        return deduplicateLineSegments(visible);
    }

    private List<LineSegment> collectPolygonSegments(List<BoundaryPolygon> polygons) {
        List<LineSegment> segments = new ArrayList<>();
        if (polygons == null || polygons.isEmpty()) {
            return segments;
        }
        for (BoundaryPolygon polygon : polygons) {
            if (polygon == null || polygon.points() == null || polygon.points().size() < 2) {
                continue;
            }
            List<BoundaryPoint> points = polygon.points();
            for (int index = 0; index < points.size(); index++) {
                BoundaryPoint start = points.get(index);
                BoundaryPoint end = points.get((index + 1) % points.size());
                segments.add(new LineSegment(start.x(), start.y(), start.z(), end.x(), end.y(), end.z()));
            }
        }
        return segments;
    }

    private BoundaryClassification classifyConquestBoundarySegments(List<BoundaryPolygon> sourcePolygons, List<BoundaryPolygon> oppositePolygons) {
        List<LineSegment> coloredSegments = new ArrayList<>();
        List<LineSegment> sharedSegments = new ArrayList<>();
        if (sourcePolygons == null || sourcePolygons.isEmpty()) {
            return new BoundaryClassification(List.of(), List.of());
        }
        final double epsilon = 1.0E-6D;
        for (BoundaryPolygon sourcePolygon : sourcePolygons) {
            if (sourcePolygon == null || sourcePolygon.points() == null || sourcePolygon.points().size() < 2) {
                continue;
            }
            List<BoundaryPoint> sourceBoundary = sourcePolygon.points();
            for (int pointIndex = 0; pointIndex < sourceBoundary.size(); pointIndex++) {
                BoundaryPoint start = sourceBoundary.get(pointIndex);
                BoundaryPoint end = sourceBoundary.get((pointIndex + 1) % sourceBoundary.size());
                List<Double> cuts = new ArrayList<>();
                cuts.add(0.0D);
                cuts.add(1.0D);
                appendConquestSegmentCuts(cuts, start, end, oppositePolygons);
                cuts.sort(Double::compareTo);
                List<Double> uniqueCuts = new ArrayList<>();
                for (double t : cuts) {
                    double clamped = Mth.clamp(t, 0.0D, 1.0D);
                    if (uniqueCuts.isEmpty() || Math.abs(uniqueCuts.get(uniqueCuts.size() - 1) - clamped) > epsilon) {
                        uniqueCuts.add(clamped);
                    }
                }
                for (int cutIndex = 0; cutIndex < uniqueCuts.size() - 1; cutIndex++) {
                    double startT = uniqueCuts.get(cutIndex);
                    double endT = uniqueCuts.get(cutIndex + 1);
                    if (endT - startT <= epsilon) {
                        continue;
                    }
                    LineSegment part = new LineSegment(
                        lerp(start.x(), end.x(), startT),
                        lerp(start.y(), end.y(), startT),
                        lerp(start.z(), end.z(), startT),
                        lerp(start.x(), end.x(), endT),
                        lerp(start.y(), end.y(), endT),
                        lerp(start.z(), end.z(), endT)
                    );
                    double mid = (startT + endT) * 0.5D;
                    double midX = lerp(start.x(), end.x(), mid);
                    double midZ = lerp(start.z(), end.z(), mid);
                    if (isPointInsideOrOnAnyBoundary(midX, midZ, oppositePolygons)) {
                        sharedSegments.add(part);
                    } else {
                        coloredSegments.add(part);
                    }
                }
            }
        }
        return new BoundaryClassification(
            deduplicateLineSegments(coloredSegments),
            deduplicateLineSegments(sharedSegments)
        );
    }

    private void appendConquestSegmentCuts(
        List<Double> cuts,
        BoundaryPoint start,
        BoundaryPoint end,
        List<BoundaryPolygon> oppositePolygons
    ) {
        if (cuts == null || oppositePolygons == null || oppositePolygons.isEmpty()) {
            return;
        }
        for (BoundaryPolygon oppositePolygon : oppositePolygons) {
            if (oppositePolygon == null || oppositePolygon.points() == null || oppositePolygon.points().size() < 2) {
                continue;
            }
            List<BoundaryPoint> oppositeBoundary = oppositePolygon.points();
            for (int oppositeIndex = 0; oppositeIndex < oppositeBoundary.size(); oppositeIndex++) {
                BoundaryPoint oppositeStart = oppositeBoundary.get(oppositeIndex);
                BoundaryPoint oppositeEnd = oppositeBoundary.get((oppositeIndex + 1) % oppositeBoundary.size());
                Double t = resolveSegmentIntersectionT(start, end, oppositeStart, oppositeEnd);
                if (t != null) {
                    cuts.add(t);
                }
                CollinearOverlap overlap = resolveCollinearOverlap(
                    new LineSegment(start.x(), start.y(), start.z(), end.x(), end.y(), end.z()),
                    new LineSegment(oppositeStart.x(), oppositeStart.y(), oppositeStart.z(), oppositeEnd.x(), oppositeEnd.y(), oppositeEnd.z())
                );
                if (overlap != null) {
                    cuts.add(overlap.selfStart());
                    cuts.add(overlap.selfEnd());
                }
            }
        }
    }

    private boolean isPointInsideOrOnAnyBoundary(double x, double z, List<BoundaryPolygon> polygons) {
        if (polygons == null || polygons.isEmpty()) {
            return false;
        }
        for (BoundaryPolygon polygon : polygons) {
            if (polygon == null || polygon.points() == null || polygon.points().size() < 2) {
                continue;
            }
            if (isPointInsideOrOnBoundary(x, z, polygon.points())) {
                return true;
            }
        }
        return false;
    }

    private List<LineSegment> collectBoundaryVisiblePartsOutsideOthers(List<BoundaryPolygon> polygons, int sourceIndex) {
        List<LineSegment> visible = new ArrayList<>();
        List<BoundaryPoint> sourceBoundary = polygons.get(sourceIndex).points();
        final double epsilon = 1.0E-6D;
        for (int i = 0; i < sourceBoundary.size(); i++) {
            BoundaryPoint start = sourceBoundary.get(i);
            BoundaryPoint end = sourceBoundary.get((i + 1) % sourceBoundary.size());
            List<Double> cuts = new ArrayList<>();
            cuts.add(0.0D);
            cuts.add(1.0D);
            for (int polygonIndex = 0; polygonIndex < polygons.size(); polygonIndex++) {
                if (polygonIndex == sourceIndex) {
                    continue;
                }
                List<BoundaryPoint> otherBoundary = polygons.get(polygonIndex).points();
                if (otherBoundary == null || otherBoundary.size() < 2) {
                    continue;
                }
                for (int j = 0; j < otherBoundary.size(); j++) {
                    BoundaryPoint otherStart = otherBoundary.get(j);
                    BoundaryPoint otherEnd = otherBoundary.get((j + 1) % otherBoundary.size());
                    Double t = resolveSegmentIntersectionT(start, end, otherStart, otherEnd);
                    if (t != null && t > epsilon && t < 1.0D - epsilon) {
                        cuts.add(t);
                    }
                }
            }
            cuts.sort(Double::compareTo);
            List<Double> uniqueCuts = new ArrayList<>();
            for (double t : cuts) {
                if (uniqueCuts.isEmpty() || Math.abs(uniqueCuts.get(uniqueCuts.size() - 1) - t) > epsilon) {
                    uniqueCuts.add(t);
                }
            }
            for (int cutIndex = 0; cutIndex < uniqueCuts.size() - 1; cutIndex++) {
                double t0 = uniqueCuts.get(cutIndex);
                double t1 = uniqueCuts.get(cutIndex + 1);
                if (t1 - t0 <= epsilon) {
                    continue;
                }
                double mid = (t0 + t1) * 0.5D;
                double mx = lerp(start.x(), end.x(), mid);
                double mz = lerp(start.z(), end.z(), mid);
                if (isPointInsideOrOnAnyOtherBoundary(mx, mz, polygons, sourceIndex)) {
                    continue;
                }
                BoundaryPoint visibleStart = interpolatePoint(start, end, t0);
                BoundaryPoint visibleEnd = interpolatePoint(start, end, t1);
                visible.add(new LineSegment(visibleStart.x(), visibleStart.y(), visibleStart.z(), visibleEnd.x(), visibleEnd.y(), visibleEnd.z()));
            }
        }
        return visible;
    }

    private boolean isPointInsideOrOnAnyOtherBoundary(double x, double z, List<BoundaryPolygon> polygons, int sourceIndex) {
        for (int polygonIndex = 0; polygonIndex < polygons.size(); polygonIndex++) {
            if (polygonIndex == sourceIndex) {
                continue;
            }
            List<BoundaryPoint> otherBoundary = polygons.get(polygonIndex).points();
            if (otherBoundary == null || otherBoundary.size() < 2) {
                continue;
            }
            if (isPointInsideOrOnBoundary(x, z, otherBoundary)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPointInsideOrOnBoundary(double x, double z, List<BoundaryPoint> polygon) {
        return isPointOnBoundaryEdge(x, z, polygon) || isPointInsideBoundary(x, z, polygon);
    }

    private boolean isPointOnBoundaryEdge(double x, double z, List<BoundaryPoint> polygon) {
        if (polygon == null || polygon.size() < 2) {
            return false;
        }
        final double epsilon = 1.0E-4D;
        for (int i = 0; i < polygon.size(); i++) {
            BoundaryPoint a = polygon.get(i);
            BoundaryPoint b = polygon.get((i + 1) % polygon.size());
            if (distancePointToSegment2D(x, z, a.x(), a.z(), b.x(), b.z()) <= epsilon) {
                return true;
            }
        }
        return false;
    }

    private boolean isPointInsideBoundary(double x, double z, List<BoundaryPoint> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return true;
        }
        boolean inside = false;
        int size = polygon.size();
        for (int i = 0, j = size - 1; i < size; j = i++) {
            BoundaryPoint pi = polygon.get(i);
            BoundaryPoint pj = polygon.get(j);
            boolean intersect = ((pi.z() > z) != (pj.z() > z))
                && (x < (pj.x() - pi.x()) * (z - pi.z()) / ((pj.z() - pi.z()) + 1.0E-9D) + pi.x());
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private Double resolveSegmentIntersectionT(BoundaryPoint a1, BoundaryPoint a2, BoundaryPoint b1, BoundaryPoint b2) {
        final double epsilon = 1.0E-9D;
        double rx = a2.x() - a1.x();
        double rz = a2.z() - a1.z();
        double sx = b2.x() - b1.x();
        double sz = b2.z() - b1.z();
        double denominator = rx * sz - rz * sx;
        if (Math.abs(denominator) <= epsilon) {
            return null;
        }
        double qpx = b1.x() - a1.x();
        double qpz = b1.z() - a1.z();
        double t = (qpx * sz - qpz * sx) / denominator;
        double u = (qpx * rz - qpz * rx) / denominator;
        if (t < -epsilon || t > 1.0D + epsilon || u < -epsilon || u > 1.0D + epsilon) {
            return null;
        }
        return Mth.clamp(t, 0.0D, 1.0D);
    }

    private BoundaryPoint interpolatePoint(BoundaryPoint start, BoundaryPoint end, double t) {
        return new BoundaryPoint(
            lerp(start.x(), end.x(), t),
            lerp(start.y(), end.y(), t),
            lerp(start.z(), end.z(), t),
            TeamToken.NEUTRAL
        );
    }

    private double distancePointToSegment2D(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared <= 1.0E-12D) {
            double ddx = px - ax;
            double ddz = pz - az;
            return Math.sqrt(ddx * ddx + ddz * ddz);
        }
        double t = ((px - ax) * dx + (pz - az) * dz) / lengthSquared;
        t = Mth.clamp(t, 0.0D, 1.0D);
        double cx = ax + dx * t;
        double cz = az + dz * t;
        double ex = px - cx;
        double ez = pz - cz;
        return Math.sqrt(ex * ex + ez * ez);
    }

    private double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private List<LineSegment> deduplicateLineSegments(List<LineSegment> segments) {
        List<LineSegment> deduplicated = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (LineSegment segment : segments) {
            String key = lineSegmentKey(segment);
            if (seen.add(key)) {
                deduplicated.add(segment);
            }
        }
        return deduplicated;
    }

    private BoundaryRenderBuckets splitSharedBoundarySegments(List<LineSegment> selfSegments, List<LineSegment> enemySegments) {
        if (selfSegments.isEmpty() || enemySegments.isEmpty()) {
            return new BoundaryRenderBuckets(
                deduplicateLineSegments(selfSegments),
                deduplicateLineSegments(enemySegments),
                List.of()
            );
        }
        List<List<Double>> selfCuts = initializeSegmentCuts(selfSegments.size());
        List<List<Double>> enemyCuts = initializeSegmentCuts(enemySegments.size());
        for (int selfIndex = 0; selfIndex < selfSegments.size(); selfIndex++) {
            LineSegment selfSegment = selfSegments.get(selfIndex);
            for (int enemyIndex = 0; enemyIndex < enemySegments.size(); enemyIndex++) {
                LineSegment enemySegment = enemySegments.get(enemyIndex);
                CollinearOverlap overlap = resolveCollinearOverlap(selfSegment, enemySegment);
                if (overlap == null) {
                    continue;
                }
                selfCuts.get(selfIndex).add(overlap.selfStart());
                selfCuts.get(selfIndex).add(overlap.selfEnd());
                enemyCuts.get(enemyIndex).add(overlap.enemyStart());
                enemyCuts.get(enemyIndex).add(overlap.enemyEnd());
            }
        }
        List<LineSegment> selfOnly = splitSegmentsByOverlap(selfSegments, selfCuts, enemySegments, false);
        List<LineSegment> enemyOnly = splitSegmentsByOverlap(enemySegments, enemyCuts, selfSegments, false);
        List<LineSegment> shared = new ArrayList<>();
        shared.addAll(splitSegmentsByOverlap(selfSegments, selfCuts, enemySegments, true));
        shared.addAll(splitSegmentsByOverlap(enemySegments, enemyCuts, selfSegments, true));
        return new BoundaryRenderBuckets(
            deduplicateLineSegments(selfOnly),
            deduplicateLineSegments(enemyOnly),
            deduplicateLineSegments(shared)
        );
    }

    private List<List<Double>> initializeSegmentCuts(int segmentCount) {
        List<List<Double>> cuts = new ArrayList<>();
        for (int index = 0; index < segmentCount; index++) {
            List<Double> segmentCuts = new ArrayList<>();
            segmentCuts.add(0.0D);
            segmentCuts.add(1.0D);
            cuts.add(segmentCuts);
        }
        return cuts;
    }

    private List<LineSegment> splitSegmentsByOverlap(
        List<LineSegment> sourceSegments,
        List<List<Double>> cutsBySegment,
        List<LineSegment> oppositeSegments,
        boolean keepOverlappingParts
    ) {
        List<LineSegment> result = new ArrayList<>();
        final double epsilon = 1.0E-6D;
        for (int segmentIndex = 0; segmentIndex < sourceSegments.size(); segmentIndex++) {
            LineSegment sourceSegment = sourceSegments.get(segmentIndex);
            List<Double> cuts = cutsBySegment.get(segmentIndex);
            cuts.sort(Double::compareTo);
            List<Double> uniqueCuts = new ArrayList<>();
            for (double t : cuts) {
                double clamped = Mth.clamp(t, 0.0D, 1.0D);
                if (uniqueCuts.isEmpty() || Math.abs(uniqueCuts.get(uniqueCuts.size() - 1) - clamped) > epsilon) {
                    uniqueCuts.add(clamped);
                }
            }
            for (int cutIndex = 0; cutIndex < uniqueCuts.size() - 1; cutIndex++) {
                double startT = uniqueCuts.get(cutIndex);
                double endT = uniqueCuts.get(cutIndex + 1);
                if (endT - startT <= epsilon) {
                    continue;
                }
                LineSegment part = interpolateSegment(sourceSegment, startT, endT);
                boolean overlapping = isSegmentPartShared(part, oppositeSegments);
                if (overlapping == keepOverlappingParts) {
                    result.add(part);
                }
            }
        }
        return result;
    }

    private boolean isSegmentPartShared(LineSegment candidate, List<LineSegment> oppositeSegments) {
        if (candidate == null || oppositeSegments == null || oppositeSegments.isEmpty()) {
            return false;
        }
        double midX = (candidate.ax() + candidate.bx()) * 0.5D;
        double midZ = (candidate.az() + candidate.bz()) * 0.5D;
        final double epsilon = 1.0E-4D;
        for (LineSegment opposite : oppositeSegments) {
            if (!areSegmentsCollinear(candidate, opposite)) {
                continue;
            }
            if (distancePointToSegment2D(midX, midZ, opposite.ax(), opposite.az(), opposite.bx(), opposite.bz()) <= epsilon) {
                return true;
            }
        }
        return false;
    }

    private CollinearOverlap resolveCollinearOverlap(LineSegment first, LineSegment second) {
        if (first == null || second == null || !areSegmentsCollinear(first, second)) {
            return null;
        }
        final double epsilon = 1.0E-6D;
        double secondStartOnFirst = resolvePointParamOnSegment(first, second.ax(), second.az());
        double secondEndOnFirst = resolvePointParamOnSegment(first, second.bx(), second.bz());
        if (Double.isNaN(secondStartOnFirst) || Double.isNaN(secondEndOnFirst)) {
            return null;
        }
        double firstOverlapStart = Math.max(0.0D, Math.min(secondStartOnFirst, secondEndOnFirst));
        double firstOverlapEnd = Math.min(1.0D, Math.max(secondStartOnFirst, secondEndOnFirst));
        if (firstOverlapEnd - firstOverlapStart <= epsilon) {
            return null;
        }
        double overlapStartX = lerp(first.ax(), first.bx(), firstOverlapStart);
        double overlapStartZ = lerp(first.az(), first.bz(), firstOverlapStart);
        double overlapEndX = lerp(first.ax(), first.bx(), firstOverlapEnd);
        double overlapEndZ = lerp(first.az(), first.bz(), firstOverlapEnd);
        double secondOverlapStart = resolvePointParamOnSegment(second, overlapStartX, overlapStartZ);
        double secondOverlapEnd = resolvePointParamOnSegment(second, overlapEndX, overlapEndZ);
        if (Double.isNaN(secondOverlapStart) || Double.isNaN(secondOverlapEnd)) {
            return null;
        }
        return new CollinearOverlap(
            Math.min(firstOverlapStart, firstOverlapEnd),
            Math.max(firstOverlapStart, firstOverlapEnd),
            Math.min(secondOverlapStart, secondOverlapEnd),
            Math.max(secondOverlapStart, secondOverlapEnd)
        );
    }

    private boolean areSegmentsCollinear(LineSegment first, LineSegment second) {
        final double epsilon = 1.0E-6D;
        double firstDx = first.bx() - first.ax();
        double firstDz = first.bz() - first.az();
        double secondDx = second.bx() - second.ax();
        double secondDz = second.bz() - second.az();
        double directionCross = firstDx * secondDz - firstDz * secondDx;
        if (Math.abs(directionCross) > epsilon) {
            return false;
        }
        double offsetX = second.ax() - first.ax();
        double offsetZ = second.az() - first.az();
        return Math.abs(offsetX * firstDz - offsetZ * firstDx) <= epsilon;
    }

    private double resolvePointParamOnSegment(LineSegment segment, double x, double z) {
        final double epsilon = 1.0E-9D;
        double dx = segment.bx() - segment.ax();
        double dz = segment.bz() - segment.az();
        if (Math.abs(dx) >= Math.abs(dz)) {
            if (Math.abs(dx) <= epsilon) {
                return Double.NaN;
            }
            return Mth.clamp((x - segment.ax()) / dx, 0.0D, 1.0D);
        }
        if (Math.abs(dz) <= epsilon) {
            return Double.NaN;
        }
        return Mth.clamp((z - segment.az()) / dz, 0.0D, 1.0D);
    }

    private LineSegment interpolateSegment(LineSegment source, double startT, double endT) {
        return new LineSegment(
            lerp(source.ax(), source.bx(), startT),
            lerp(source.ay(), source.by(), startT),
            lerp(source.az(), source.bz(), startT),
            lerp(source.ax(), source.bx(), endT),
            lerp(source.ay(), source.by(), endT),
            lerp(source.az(), source.bz(), endT)
        );
    }

    private String lineSegmentKey(LineSegment segment) {
        String a = roundedPointKey(segment.ax(), segment.ay(), segment.az());
        String b = roundedPointKey(segment.bx(), segment.by(), segment.bz());
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private String roundedPointKey(double x, double y, double z) {
        long rx = Math.round(x * 1000.0D);
        long ry = Math.round(y * 1000.0D);
        long rz = Math.round(z * 1000.0D);
        return rx + "," + ry + "," + rz;
    }

    private boolean isBoundarySegmentWithinRenderDistance(Vec3 camera, LineSegment segment) {
        if (camera == null) {
            return false;
        }
        if (distanceSqr(camera, segment.ax(), segment.ay(), segment.az()) <= ClientCaptureOutlineController.CAPTURE_BOUNDARY_RENDER_MAX_DISTANCE_SQR) {
            return true;
        }
        if (distanceSqr(camera, segment.bx(), segment.by(), segment.bz()) <= ClientCaptureOutlineController.CAPTURE_BOUNDARY_RENDER_MAX_DISTANCE_SQR) {
            return true;
        }
        double midX = (segment.ax() + segment.bx()) * 0.5D;
        double midY = (segment.ay() + segment.by()) * 0.5D;
        double midZ = (segment.az() + segment.bz()) * 0.5D;
        return distanceSqr(camera, midX, midY, midZ) <= ClientCaptureOutlineController.CAPTURE_BOUNDARY_RENDER_MAX_DISTANCE_SQR;
    }

    private double distanceSqr(Vec3 from, double x, double y, double z) {
        double dx = from.x - x;
        double dy = from.y - y;
        double dz = from.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void appendThickSegmentTriangles(
        BufferBuilder buffer,
        Matrix4f matrix,
        Vec3 camera,
        LineSegment segment,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        double dx = segment.bx() - segment.ax();
        double dz = segment.bz() - segment.az();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length <= 1.0E-6D) {
            return;
        }
        double halfWidth = ClientCaptureOverlayModel.BOUNDARY_LINE_WORLD_WIDTH * 0.5D;
        double offsetX = -dz / length * halfWidth;
        double offsetZ = dx / length * halfWidth;
        float axPlus = (float) (segment.ax() + offsetX - camera.x);
        float azPlus = (float) (segment.az() + offsetZ - camera.z);
        float bxPlus = (float) (segment.bx() + offsetX - camera.x);
        float bzPlus = (float) (segment.bz() + offsetZ - camera.z);
        float bxMinus = (float) (segment.bx() - offsetX - camera.x);
        float bzMinus = (float) (segment.bz() - offsetZ - camera.z);
        float axMinus = (float) (segment.ax() - offsetX - camera.x);
        float azMinus = (float) (segment.az() - offsetZ - camera.z);
        float ay = (float) (segment.ay() - camera.y);
        float by = (float) (segment.by() - camera.y);
        appendTriangle(buffer, matrix, axPlus, ay, azPlus, bxPlus, by, bzPlus, bxMinus, by, bzMinus, red, green, blue, alpha);
        appendTriangle(buffer, matrix, axPlus, ay, azPlus, bxMinus, by, bzMinus, axMinus, ay, azMinus, red, green, blue, alpha);
    }

    private void appendRoundCapTriangles(
        BufferBuilder buffer,
        Matrix4f matrix,
        Vec3 camera,
        double x,
        double y,
        double z,
        double radius,
        float red,
        float green,
        float blue,
        float alpha,
        Set<String> seenEndpoints
    ) {
        String key = roundedPointKey(x, y, z);
        if (!seenEndpoints.add(key)) {
            return;
        }
        float centerX = (float) (x - camera.x);
        float centerY = (float) (y - camera.y);
        float centerZ = (float) (z - camera.z);
        for (int step = 0; step < ROUND_CAP_SEGMENTS; step++) {
            double startAngle = (Math.PI * 2.0D * step) / ROUND_CAP_SEGMENTS;
            double endAngle = (Math.PI * 2.0D * (step + 1)) / ROUND_CAP_SEGMENTS;
            float startX = (float) (centerX + Math.cos(startAngle) * radius);
            float startZ = (float) (centerZ + Math.sin(startAngle) * radius);
            float endX = (float) (centerX + Math.cos(endAngle) * radius);
            float endZ = (float) (centerZ + Math.sin(endAngle) * radius);
            appendTriangle(buffer, matrix, centerX, centerY, centerZ, startX, centerY, startZ, endX, centerY, endZ, red, green, blue, alpha);
        }
    }

    private void appendTriangle(
        BufferBuilder buffer,
        Matrix4f matrix,
        float ax,
        float ay,
        float az,
        float bx,
        float by,
        float bz,
        float cx,
        float cy,
        float cz,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        buffer.vertex(matrix, ax, ay, az).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, bx, by, bz).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, cx, cy, cz).color(red, green, blue, alpha).endVertex();
    }

    private float[] resolveBoundaryColorForTeam(TeamToken selfTeam, boolean selfBoundary) {
        if (selfTeam == TeamToken.NEUTRAL) {
            return new float[]{1.0F, 1.0F, 1.0F};
        }
        if (selfBoundary) {
            return new float[]{0.25F, 0.45F, 1.0F};
        }
        return new float[]{1.0F, 0.2F, 0.2F};
    }

    private record CollinearOverlap(double selfStart, double selfEnd, double enemyStart, double enemyEnd) {
    }

    private record BoundaryClassification(List<LineSegment> coloredSegments, List<LineSegment> sharedSegments) {
    }

    private record BoundaryRenderBuckets(List<LineSegment> selfSegments, List<LineSegment> enemySegments, List<LineSegment> sharedSegments) {
    }

    private enum BoundaryRenderMode {
        CONQUEST,
        BREAKTHROUGH
    }

    record ResolvedBoundarySegments(
        List<LineSegment> selfSegments,
        List<LineSegment> enemySegments,
        List<LineSegment> neutralSegments,
        List<LineSegment> sharedSegments
    ) {
        private static final ResolvedBoundarySegments EMPTY =
            new ResolvedBoundarySegments(List.of(), List.of(), List.of(), List.of());

        static ResolvedBoundarySegments empty() {
            return EMPTY;
        }

        boolean isEmpty() {
            return selfSegments.isEmpty()
                && enemySegments.isEmpty()
                && neutralSegments.isEmpty()
                && sharedSegments.isEmpty();
        }
    }

}
