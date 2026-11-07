/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.event.handler;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.joml.Matrix4f;
import org.mods.gd656conquest.client.event.handler.ClientCaptureBoundaryRenderer;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayState;
import org.mods.gd656conquest.client.input.ClientFovScrollService;
import org.mods.gd656conquest.client.render.ClientWorldHudProjectionSupport;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;

final class ClientDeployBoundaryOverlayRenderer {
    private final DeploymentBoundaryMask gpuMask=new DeploymentBoundaryMask();
    void release(){gpuMask.close();cachedGeometry=CachedDeployOverlayGeometry.empty();cachedGeometryBoundaryVersion=Long.MIN_VALUE;}
    private static final float SCREEN_LINE_WIDTH_PIXELS = 2.5f;
    private static final float MASK_ALPHA = 0.12f;
    private static final float LINE_ALPHA = 0.6f;
    private static final float GUI_MASK_Z = -110.0f;
    private static final float GUI_LINE_Z = -108.0f;
    private static final double EDGE_EPSILON = 1.0E-6;
    private static final int ROUND_CAP_SEGMENTS = 18;
    private static final double ROUND_CAP_RADIUS_MULTIPLIER = 1.35;
    private final ClientCaptureBoundaryRenderer boundaryRenderer;
    private long cachedGeometryBoundaryVersion = Long.MIN_VALUE;
    private ClientCaptureOverlayModel.TeamToken cachedGeometrySelfTeam = ClientCaptureOverlayModel.TeamToken.NEUTRAL;
    private int cachedGeometryScreenWidth = -1;
    private int cachedGeometryScreenHeight = -1;
    private CachedDeployOverlayGeometry cachedGeometry = CachedDeployOverlayGeometry.empty();
    private long totalMaskRebuilds;
    private long totalProjectionPasses;
    private long lastDebugStatsSampleMillis;
    private int maskRebuildsPerSecond;
    private int projectionPassesPerSecond;
    private long observedBoundaryVersion = Long.MIN_VALUE;
    private long lastSampleBoundaryVersion = Long.MIN_VALUE;
    private int versionDeltaPerSecond;
    private long totalMaskBuildNanos;
    private long totalGeometryComparisons;
    private long totalGeometryNearHits;
    private int geometryComparePerSecond;
    private int geometryHitPerSecond;
    private int lastMaskBuildCols;
    private int maskColsPerSecond;
    private double avgMaskBuildMillis;

    ClientDeployBoundaryOverlayRenderer(ClientCaptureBoundaryRenderer boundaryRenderer) {
        this.boundaryRenderer = boundaryRenderer;
    }

    void renderDeployBoundaryOverlay(ClientCaptureOverlayState state, RenderGuiOverlayEvent.Post event, ClientCaptureOverlayModel.TeamToken selfTeam) {
        this.renderDeployBoundaryOverlay(state, event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), selfTeam);
    }

    void renderDeployBoundaryOverlay(ClientCaptureOverlayState state, GuiGraphics guiGraphics, int screenWidth, int screenHeight, ClientCaptureOverlayModel.TeamToken selfTeam) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || state.worldProjectionMatrix == null) {
            return;
        }
        float alphaFactor = ClientDeathFlowState.currentDeployIconAlpha();
        if (alphaFactor <= 0.001f) {
            return;
        }
        this.observedBoundaryVersion = state.boundaryGeometryVersion;
        this.sampleDebugStatsPerSecond(System.currentTimeMillis());
        ClientCaptureBoundaryRenderer.ResolvedBoundarySegments segments = this.boundaryRenderer.resolveRenderableSegments(state, selfTeam, ClientCaptureBoundaryRenderer.BoundaryDataSource.ROOM);
        if (segments.isEmpty()) {
            return;
        }
        CachedDeployOverlayGeometry geometry = this.resolveCachedGeometry(state, minecraft, selfTeam, screenWidth, screenHeight, segments);
        if (geometry.isEmpty()) {
            return;
        }
        boolean masked=gpuMask.render(guiGraphics,state,selfTeam,this.boundaryRenderer.resolveRenderablePolygons(state,selfTeam,ClientCaptureBoundaryRenderer.BoundaryDataSource.ROOM),alphaFactor);
        if(!masked)this.drawOutsideMask(guiGraphics, this.buildMaskFills(screenWidth,screenHeight,geometry.sourcePolygons()), alphaFactor);
        this.drawBoundaryLines(guiGraphics, geometry, selfTeam, alphaFactor);
    }

    private CachedDeployOverlayGeometry resolveCachedGeometry(ClientCaptureOverlayState state, Minecraft minecraft, ClientCaptureOverlayModel.TeamToken selfTeam, int screenWidth, int screenHeight, ClientCaptureBoundaryRenderer.ResolvedBoundarySegments segments) {
        if (!this.isCachedGeometryBaseValid(state, selfTeam, screenWidth, screenHeight)) {
            this.cachedGeometry = CachedDeployOverlayGeometry.empty();
        }
        HashMap<WorldPointKey, ScreenPoint> pointProjectionCache = new HashMap<WorldPointKey, ScreenPoint>();
        ++this.totalProjectionPasses;
        List<ProjectedBoundaryPolygon> polygons = this.projectPolygons(state, minecraft, selfTeam, pointProjectionCache);
        if (polygons.isEmpty()) {
            this.cachedGeometry = CachedDeployOverlayGeometry.empty();
            this.updateCachedGeometryBase(state, selfTeam, screenWidth, screenHeight);
            return this.cachedGeometry;
        }
        ++this.totalGeometryComparisons;
        if (!this.cachedGeometry.isEmpty() && this.arePolygonsNear(this.cachedGeometry.sourcePolygons(), polygons)) {
            ++this.totalGeometryNearHits;
            return this.cachedGeometry;
        }
        ++this.totalMaskRebuilds;
        long maskBuildStart = System.nanoTime();
        this.cachedGeometry = new CachedDeployOverlayGeometry(polygons, List.of(), this.projectLineSegments(minecraft, state, segments.selfSegments(), pointProjectionCache), this.projectLineSegments(minecraft, state, segments.enemySegments(), pointProjectionCache), this.projectLineSegments(minecraft, state, segments.neutralSegments(), pointProjectionCache), this.projectLineSegments(minecraft, state, segments.sharedSegments(), pointProjectionCache));
        this.totalMaskBuildNanos += System.nanoTime() - maskBuildStart;
        this.updateCachedGeometryBase(state, selfTeam, screenWidth, screenHeight);
        return this.cachedGeometry;
    }

    private boolean isCachedGeometryBaseValid(ClientCaptureOverlayState state, ClientCaptureOverlayModel.TeamToken selfTeam, int screenWidth, int screenHeight) {
        return this.cachedGeometryBoundaryVersion == state.boundaryGeometryVersion && this.cachedGeometrySelfTeam == selfTeam && this.cachedGeometryScreenWidth == screenWidth && this.cachedGeometryScreenHeight == screenHeight && state.worldProjectionMatrix != null;
    }

    private void sampleDebugStatsPerSecond(long nowMillis) {
        if (this.lastDebugStatsSampleMillis == 0L) {
            this.lastDebugStatsSampleMillis = nowMillis;
            this.lastSampleBoundaryVersion = this.observedBoundaryVersion;
            return;
        }
        if (nowMillis - this.lastDebugStatsSampleMillis < 1000L) {
            return;
        }
        this.maskRebuildsPerSecond = (int)this.totalMaskRebuilds;
        this.projectionPassesPerSecond = (int)this.totalProjectionPasses;
        this.versionDeltaPerSecond = this.observedBoundaryVersion - this.lastSampleBoundaryVersion > Integer.MAX_VALUE ? 0 : (int)(this.observedBoundaryVersion - this.lastSampleBoundaryVersion);
        this.lastSampleBoundaryVersion = this.observedBoundaryVersion;
        this.geometryComparePerSecond = (int)this.totalGeometryComparisons;
        this.geometryHitPerSecond = (int)this.totalGeometryNearHits;
        this.maskColsPerSecond = this.lastMaskBuildCols;
        this.avgMaskBuildMillis = this.totalMaskRebuilds <= 0L ? 0.0 : (double)this.totalMaskBuildNanos / 1000000.0 / (double)this.totalMaskRebuilds;
        this.totalMaskRebuilds = 0L;
        this.totalProjectionPasses = 0L;
        this.totalMaskBuildNanos = 0L;
        this.totalGeometryComparisons = 0L;
        this.totalGeometryNearHits = 0L;
        this.lastDebugStatsSampleMillis = nowMillis;
    }

    String debugStatsLine() {
        double appliedFov = ClientFovScrollService.getLastAppliedFov();
        String fovText = appliedFov < 0.0 ? "-" : String.format("%.1f", appliedFov);
        return String.format("deploy: mask=%d/s proj=%d/s ver=%d d=%d fov=%s", this.maskRebuildsPerSecond, this.projectionPassesPerSecond, this.observedBoundaryVersion, this.versionDeltaPerSecond, fovText);
    }

    String debugStatsDetailLine() {
        return String.format("mask: avg=%.2fms cols=%d hit=%d/%d", this.avgMaskBuildMillis, this.maskColsPerSecond, this.geometryHitPerSecond, this.geometryComparePerSecond);
    }

    private void updateCachedGeometryBase(ClientCaptureOverlayState state, ClientCaptureOverlayModel.TeamToken selfTeam, int screenWidth, int screenHeight) {
        this.cachedGeometryBoundaryVersion = state.boundaryGeometryVersion;
        this.cachedGeometrySelfTeam = selfTeam;
        this.cachedGeometryScreenWidth = screenWidth;
        this.cachedGeometryScreenHeight = screenHeight;
    }

    private boolean arePolygonsNear(List<ProjectedBoundaryPolygon> previous, List<ProjectedBoundaryPolygon> current) {
        if (previous == current) {
            return true;
        }
        if (previous == null || current == null || previous.size() != current.size()) {
            return false;
        }
        for (int index = 0; index < previous.size(); ++index) {
            List<ScreenPoint> prevPoints = previous.get(index).points();
            List<ScreenPoint> currPoints = current.get(index).points();
            if (prevPoints.size() != currPoints.size()) {
                return false;
            }
            for (int pointIndex = 0; pointIndex < prevPoints.size(); ++pointIndex) {
                if (prevPoints.get(pointIndex).isNear(currPoints.get(pointIndex))) continue;
                return false;
            }
        }
        return true;
    }

    private List<ProjectedBoundaryPolygon> projectPolygons(ClientCaptureOverlayState state, Minecraft minecraft, ClientCaptureOverlayModel.TeamToken selfTeam, Map<WorldPointKey, ScreenPoint> pointProjectionCache) {
        List<ClientCaptureOverlayModel.BoundaryPolygon> rawPolygons = this.boundaryRenderer.resolveRenderablePolygons(state, selfTeam, ClientCaptureBoundaryRenderer.BoundaryDataSource.ROOM);
        if (rawPolygons.isEmpty()) {
            return List.of();
        }
        ArrayList<ProjectedBoundaryPolygon> projected = new ArrayList<ProjectedBoundaryPolygon>();
        for (ClientCaptureOverlayModel.BoundaryPolygon polygon : rawPolygons) {
            ProjectedBoundaryPolygon screenPolygon = this.projectPolygon(state, minecraft, polygon, pointProjectionCache);
            if (screenPolygon == null) continue;
            projected.add(screenPolygon);
        }
        return projected.isEmpty() ? List.of() : List.copyOf(projected);
    }

    private ProjectedBoundaryPolygon projectPolygon(ClientCaptureOverlayState state, Minecraft minecraft, ClientCaptureOverlayModel.BoundaryPolygon polygon, Map<WorldPointKey, ScreenPoint> pointProjectionCache) {
        if (polygon == null || polygon.points() == null || polygon.points().size() < 3) {
            return null;
        }
        ArrayList<ScreenPoint> screenPoints = new ArrayList<ScreenPoint>();
        ScreenPoint previous = null;
        for (ClientCaptureOverlayModel.BoundaryPoint point : polygon.points()) {
            ScreenPoint screenPoint = this.projectWorldPoint(minecraft, state, point.x(), point.y(), point.z(), pointProjectionCache);
            if (screenPoint == null) {
                return null;
            }
            if (previous != null && previous.isNear(screenPoint)) continue;
            screenPoints.add(screenPoint);
            previous = screenPoint;
        }
        if (screenPoints.size() >= 2 && ((ScreenPoint)screenPoints.get(0)).isNear((ScreenPoint)screenPoints.get(screenPoints.size() - 1))) {
            screenPoints.remove(screenPoints.size() - 1);
        }
        if (screenPoints.size() < 3) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        ArrayList<PolygonScanEdge> scanEdges = new ArrayList<PolygonScanEdge>();
        for (int index = 0; index < screenPoints.size(); ++index) {
            ScreenPoint start = (ScreenPoint)screenPoints.get(index);
            ScreenPoint end = (ScreenPoint)screenPoints.get((index + 1) % screenPoints.size());
            minX = Math.min(minX, start.x());
            maxX = Math.max(maxX, start.x());
            PolygonScanEdge edge = this.buildScanEdge(start, end);
            if (edge == null) continue;
            scanEdges.add(edge);
        }
        if (scanEdges.isEmpty()) {
            return null;
        }
        return new ProjectedBoundaryPolygon(polygon.team(), List.copyOf(screenPoints), List.copyOf(scanEdges), minX, maxX);
    }

    private List<MaskFill> buildMaskFills(int width, int height, List<ProjectedBoundaryPolygon> polygons) {
        if (width <= 0 || height <= 0 || polygons == null || polygons.isEmpty()) {
            return List.of();
        }
        ArrayList<MutableMaskFill> activeFills = new ArrayList<MutableMaskFill>();
        ArrayList<MaskFill> fills = new ArrayList<MaskFill>();
        List<Span> previousOutsideSpans = List.of();
        double maxY = height;
        int scanStartX = width;
        int scanEndX = -1;
        for (ProjectedBoundaryPolygon polygon : polygons) {
            scanStartX = Math.min(scanStartX, this.clampColumnStart(polygon.minX(), width));
            scanEndX = Math.max(scanEndX, this.clampColumnEndExclusive(polygon.maxX(), width));
        }
        scanStartX = Mth.clamp(scanStartX, 0, width);
        scanEndX = Mth.clamp(scanEndX, 0, width);
        this.lastMaskBuildCols = Math.max(0, scanEndX - scanStartX);
        if (scanStartX > 0) {
            fills.add(new MaskFill(0, 0, scanStartX, height));
        }
        if (scanEndX < width) {
            fills.add(new MaskFill(scanEndX, 0, width, height));
        }
        if (scanStartX >= scanEndX) {
            return fills.isEmpty() ? List.of() : List.copyOf(fills);
        }
        for (int x = scanStartX; x < scanEndX; ++x) {
            List<Span> outsideSpans = this.resolveOutsideSpans(polygons, (double)x + 0.5, maxY, height);
            if (!this.sameSpans(previousOutsideSpans, outsideSpans)) {
                this.flushActiveFills(activeFills, fills);
                for (Span span : outsideSpans) {
                    activeFills.add(new MutableMaskFill(x, (int)Math.floor(span.start()), x + 1, (int)Math.ceil(span.end())));
                }
                previousOutsideSpans = outsideSpans;
                continue;
            }
            for (MutableMaskFill fill : activeFills) {
                fill.endX = x + 1;
            }
        }
        this.flushActiveFills(activeFills, fills);
        return fills.isEmpty() ? List.of() : List.copyOf(fills);
    }

    private List<Span> resolveOutsideSpans(List<ProjectedBoundaryPolygon> polygons, double sampleX, double maxY, int height) {
        List<Span> spans = this.resolveMergedInsideSpans(polygons, sampleX);
        ArrayList<Span> outsideSpans = new ArrayList<Span>();
        double fillStartY = 0.0;
        for (Span span : spans) {
            double insideStart = Mth.clamp(span.start(), 0.0, maxY);
            double insideEnd = Mth.clamp(span.end(), 0.0, maxY);
            if (insideStart > fillStartY) {
                outsideSpans.add(new Span(fillStartY, insideStart));
            }
            if (!((fillStartY = Math.max(fillStartY, insideEnd)) >= maxY)) continue;
            break;
        }
        if (fillStartY < maxY) {
            outsideSpans.add(new Span(fillStartY, height));
        }
        return outsideSpans.isEmpty() ? List.of() : List.copyOf(outsideSpans);
    }

    private boolean sameSpans(List<Span> previous, List<Span> current) {
        if (previous == current) {
            return true;
        }
        if (previous == null || current == null || previous.size() != current.size()) {
            return false;
        }
        for (int index = 0; index < previous.size(); ++index) {
            Span left = previous.get(index);
            Span right = current.get(index);
            if (!(Math.abs(left.start() - right.start()) > 0.5) && !(Math.abs(left.end() - right.end()) > 0.5)) continue;
            return false;
        }
        return true;
    }

    private void flushActiveFills(List<MutableMaskFill> activeFills, List<MaskFill> fills) {
        if (activeFills.isEmpty()) {
            return;
        }
        for (MutableMaskFill fill : activeFills) {
            if (fill.endY <= fill.startY || fill.endX <= fill.startX) continue;
            fills.add(new MaskFill(fill.startX, fill.startY, fill.endX, fill.endY));
        }
        activeFills.clear();
    }

    private void drawOutsideMask(GuiGraphics guiGraphics, List<MaskFill> fills, float alphaFactor) {
        int alpha = Mth.clamp(Math.round(30.6f * alphaFactor), 0, 255);
        if (alpha <= 0 || fills == null || fills.isEmpty()) {
            return;
        }
        float a = (float)alpha / 255.0f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, 0.0f, -110.0f);
        Matrix4f matrix = guiGraphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (MaskFill fill : fills) {
            if (fill.endY() <= fill.startY()) continue;
            float left = fill.startX();
            float top = fill.startY();
            float right = fill.endX();
            float bottom = fill.endY();
            buffer.vertex(matrix, left, top, 0.0f).color(0.0f, 0.0f, 0.0f, a).endVertex();
            buffer.vertex(matrix, left, bottom, 0.0f).color(0.0f, 0.0f, 0.0f, a).endVertex();
            buffer.vertex(matrix, right, bottom, 0.0f).color(0.0f, 0.0f, 0.0f, a).endVertex();
            buffer.vertex(matrix, right, top, 0.0f).color(0.0f, 0.0f, 0.0f, a).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();
    }

    private List<Span> resolveMergedInsideSpans(List<ProjectedBoundaryPolygon> polygons, double sampleX) {
        ArrayList<Span> spans = new ArrayList<Span>();
        for (ProjectedBoundaryPolygon polygon : polygons) {
            spans.addAll(this.resolveInsideSpansForPolygon(polygon, sampleX));
        }
        if (spans.isEmpty()) {
            return List.of();
        }
        spans.sort(Comparator.comparingDouble(Span::start));
        ArrayList<Span> merged = new ArrayList<Span>();
        for (Span span : spans) {
            if (merged.isEmpty()) {
                merged.add(span);
                continue;
            }
            Span last = (Span)merged.get(merged.size() - 1);
            if (span.start() <= last.end() + 0.5) {
                merged.set(merged.size() - 1, new Span(last.start(), Math.max(last.end(), span.end())));
                continue;
            }
            merged.add(span);
        }
        return merged;
    }

    private List<Span> resolveInsideSpansForPolygon(ProjectedBoundaryPolygon polygon, double sampleX) {
        if (polygon == null || sampleX < polygon.minX() || sampleX >= polygon.maxX()) {
            return List.of();
        }
        ArrayList<Double> intersections = new ArrayList<Double>();
        for (PolygonScanEdge edge : polygon.scanEdges()) {
            if (sampleX < edge.minX() || sampleX >= edge.maxX()) continue;
            intersections.add(edge.yAt(sampleX));
        }
        if (intersections.size() < 2) {
            return List.of();
        }
        intersections.sort(Double::compareTo);
        ArrayList<Span> spans = new ArrayList<Span>();
        int index = 0;
        while (index + 1 < intersections.size()) {
            double startY = (Double)intersections.get(index);
            double endY = (Double)intersections.get(index + 1);
            if (!(endY - startY <= 1.0E-6)) {
                spans.add(new Span(startY, endY));
            }
            index += 2;
        }
        return spans;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void drawBoundaryLines(GuiGraphics guiGraphics, CachedDeployOverlayGeometry geometry, ClientCaptureOverlayModel.TeamToken selfTeam, float alphaFactor) {
        float[] selfColor = this.resolveBoundaryColorForTeam(selfTeam, true);
        float[] enemyColor = this.resolveBoundaryColorForTeam(selfTeam, false);
        float[] neutralColor = new float[]{1.0f, 1.0f, 1.0f};
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0f, 0.0f, -108.0f);
        Matrix4f matrix = guiGraphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            this.drawProjectedSegmentBucket(matrix, geometry.selfSegments(), selfColor[0], selfColor[1], selfColor[2], alphaFactor * 0.6f);
            this.drawProjectedSegmentBucket(matrix, geometry.enemySegments(), enemyColor[0], enemyColor[1], enemyColor[2], alphaFactor * 0.6f);
            this.drawProjectedSegmentBucket(matrix, geometry.neutralSegments(), neutralColor[0], neutralColor[1], neutralColor[2], alphaFactor * 0.6f);
            this.drawProjectedSegmentBucket(matrix, geometry.sharedSegments(), neutralColor[0], neutralColor[1], neutralColor[2], alphaFactor * 0.6f);
        }
        finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            guiGraphics.pose().popPose();
        }
    }

    private void drawProjectedSegmentBucket(Matrix4f matrix, List<ProjectedLineSegment> projectedSegments, float red, float green, float blue, float alpha) {
        if (projectedSegments.isEmpty() || alpha <= 0.001f) {
            return;
        }
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        this.appendProjectedSegments(buffer, matrix, projectedSegments, red, green, blue, alpha);
        this.appendProjectedEndpointCaps(buffer, matrix, projectedSegments, red, green, blue, alpha);
        BufferUploader.drawWithShader(buffer.end());
    }

    private List<ProjectedLineSegment> projectLineSegments(Minecraft minecraft, ClientCaptureOverlayState state, List<ClientCaptureOverlayModel.LineSegment> segments, Map<WorldPointKey, ScreenPoint> pointProjectionCache) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        ArrayList<ProjectedLineSegment> projected = new ArrayList<ProjectedLineSegment>();
        for (ClientCaptureOverlayModel.LineSegment segment : segments) {
            ScreenPoint start = this.projectWorldPoint(minecraft, state, segment.ax(), segment.ay(), segment.az(), pointProjectionCache);
            ScreenPoint end = this.projectWorldPoint(minecraft, state, segment.bx(), segment.by(), segment.bz(), pointProjectionCache);
            if (start == null || end == null || start.isNear(end)) continue;
            projected.add(new ProjectedLineSegment(start, end));
        }
        return projected.isEmpty() ? List.of() : List.copyOf(projected);
    }

    private PolygonScanEdge buildScanEdge(ScreenPoint start, ScreenPoint end) {
        double dx = end.x() - start.x();
        if (Math.abs(dx) <= 1.0E-6) {
            return null;
        }
        if (start.x() <= end.x()) {
            return new PolygonScanEdge(start.x(), end.x(), start.y(), (end.y() - start.y()) / dx);
        }
        return new PolygonScanEdge(end.x(), start.x(), end.y(), (start.y() - end.y()) / -dx);
    }

    private int clampColumnStart(double minX, int width) {
        return Mth.clamp((int)Math.floor(minX), 0, width);
    }

    private int clampColumnEndExclusive(double maxX, int width) {
        return Mth.clamp((int)Math.ceil(maxX), 0, width);
    }

    private void appendProjectedSegments(BufferBuilder buffer, Matrix4f matrix, List<ProjectedLineSegment> segments, float red, float green, float blue, float alpha) {
        if (segments == null || segments.isEmpty() || alpha <= 0.001f) {
            return;
        }
        for (ProjectedLineSegment segment : segments) {
            this.appendSegmentTriangles(buffer, matrix, segment.start(), segment.end(), red, green, blue, alpha);
        }
    }

    private void appendProjectedEndpointCaps(BufferBuilder buffer, Matrix4f matrix, List<ProjectedLineSegment> segments, float red, float green, float blue, float alpha) {
        HashSet<String> seenEndpoints = new HashSet<String>();
        double radius = 0.9;
        for (ProjectedLineSegment segment : segments) {
            this.appendScreenRoundCap(buffer, matrix, segment.start(), radius, red, green, blue, alpha, seenEndpoints);
            this.appendScreenRoundCap(buffer, matrix, segment.end(), radius, red, green, blue, alpha, seenEndpoints);
        }
    }

    private ScreenPoint projectWorldPoint(Minecraft minecraft, ClientCaptureOverlayState state, double x, double y, double z, Map<WorldPointKey, ScreenPoint> pointProjectionCache) {
        ScreenPoint screenPoint;
        WorldPointKey key = new WorldPointKey(x, y, z);
        ScreenPoint cached = pointProjectionCache.get(key);
        if (cached != null) {
            return cached;
        }
        ClientWorldHudProjectionSupport.ProjectedPoint projected = ClientWorldHudProjectionSupport.projectToScreen(minecraft, state.worldProjectionMatrix, new Vec3(x, y, z));
        ScreenPoint screenPoint2 = screenPoint = projected == null ? null : new ScreenPoint(projected.screenX(), projected.screenY());
        if (screenPoint != null) {
            pointProjectionCache.put(key, screenPoint);
        }
        return screenPoint;
    }

    private void appendSegmentTriangles(BufferBuilder buffer, Matrix4f matrix, ScreenPoint start, ScreenPoint end, float red, float green, float blue, float alpha) {
        double dy;
        double dx = end.x() - start.x();
        double length = Math.sqrt(dx * dx + (dy = end.y() - start.y()) * dy);
        if (length <= 1.0E-6) {
            return;
        }
        double halfWidth = 0.6;
        double offsetX = -dy / length * halfWidth;
        double offsetY = dx / length * halfWidth;
        float axPlus = (float)(start.x() + offsetX);
        float ayPlus = (float)(start.y() + offsetY);
        float bxPlus = (float)(end.x() + offsetX);
        float byPlus = (float)(end.y() + offsetY);
        float bxMinus = (float)(end.x() - offsetX);
        float byMinus = (float)(end.y() - offsetY);
        float axMinus = (float)(start.x() - offsetX);
        float ayMinus = (float)(start.y() - offsetY);
        this.appendTriangle(buffer, matrix, axPlus, ayPlus, bxPlus, byPlus, bxMinus, byMinus, red, green, blue, alpha);
        this.appendTriangle(buffer, matrix, axPlus, ayPlus, bxMinus, byMinus, axMinus, ayMinus, red, green, blue, alpha);
    }

    private void appendScreenRoundCap(BufferBuilder buffer, Matrix4f matrix, ScreenPoint center, double radius, float red, float green, float blue, float alpha, Set<String> seenEndpoints) {
        String key = this.roundedScreenPointKey(center);
        if (!seenEndpoints.add(key)) {
            return;
        }
        for (int step = 0; step < 18; ++step) {
            double startAngle = Math.PI * 2 * (double)step / 18.0;
            double endAngle = Math.PI * 2 * (double)(step + 1) / 18.0;
            float startX = (float)(center.x() + Math.cos(startAngle) * radius);
            float startY = (float)(center.y() + Math.sin(startAngle) * radius);
            float endX = (float)(center.x() + Math.cos(endAngle) * radius);
            float endY = (float)(center.y() + Math.sin(endAngle) * radius);
            this.appendTriangle(buffer, matrix, (float)center.x(), (float)center.y(), startX, startY, endX, endY, red, green, blue, alpha);
        }
    }

    private void appendTriangle(BufferBuilder buffer, Matrix4f matrix, float ax, float ay, float bx, float by, float cx, float cy, float red, float green, float blue, float alpha) {
        buffer.vertex(matrix, ax, ay, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, bx, by, 0.0f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, cx, cy, 0.0f).color(red, green, blue, alpha).endVertex();
    }

    private String roundedScreenPointKey(ScreenPoint point) {
        long rx = Math.round(point.x() * 100.0);
        long ry = Math.round(point.y() * 100.0);
        return rx + "," + ry;
    }

    private float[] resolveBoundaryColorForTeam(ClientCaptureOverlayModel.TeamToken selfTeam, boolean selfBoundary) {
        if (selfTeam == ClientCaptureOverlayModel.TeamToken.NEUTRAL) {
            return new float[]{1.0f, 1.0f, 1.0f};
        }
        if (selfBoundary) {
            return themeRgb(true);
        }
        return themeRgb(false);
    }

    private double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private record CachedDeployOverlayGeometry(List<ProjectedBoundaryPolygon> sourcePolygons, List<MaskFill> maskFills, List<ProjectedLineSegment> selfSegments, List<ProjectedLineSegment> enemySegments, List<ProjectedLineSegment> neutralSegments, List<ProjectedLineSegment> sharedSegments) {
        private static final CachedDeployOverlayGeometry EMPTY = new CachedDeployOverlayGeometry(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        static CachedDeployOverlayGeometry empty() {
            return EMPTY;
        }

        boolean isEmpty() {
            return this.maskFills.isEmpty() && this.selfSegments.isEmpty() && this.enemySegments.isEmpty() && this.neutralSegments.isEmpty() && this.sharedSegments.isEmpty();
        }
    }

    private record ProjectedBoundaryPolygon(ClientCaptureOverlayModel.TeamToken team, List<ScreenPoint> points, List<PolygonScanEdge> scanEdges, double minX, double maxX) {
    }

    private record ScreenPoint(double x, double y) {
        boolean isNear(ScreenPoint other) {
            if (other == null) {
                return false;
            }
            return Math.abs(this.x - other.x) <= 0.5 && Math.abs(this.y - other.y) <= 0.5;
        }
    }

    private record PolygonScanEdge(double minX, double maxX, double startY, double slopeYPerX) {
        double yAt(double sampleX) {
            return this.startY + (sampleX - this.minX) * this.slopeYPerX;
        }
    }

    private record MaskFill(int startX, int startY, int endX, int endY) {
    }

    private record Span(double start, double end) {
    }

    private static final class MutableMaskFill {
        private final int startX;
        private final int startY;
        private int endX;
        private final int endY;

        private MutableMaskFill(int startX, int startY, int endX, int endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }
    }

    private record ProjectedLineSegment(ScreenPoint start, ScreenPoint end) {
    }

    private record WorldPointKey(long x, long y, long z) {
        private WorldPointKey(double x, double y, double z) {
            this(Math.round(x * 1000.0), Math.round(y * 1000.0), Math.round(z * 1000.0));
        }
    }
    private static float[] themeRgb(boolean ally) {
        var theme=org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme.current();
        int c=ally?org.mods.gd656killicon.client.gui.theme.BattlefieldPovLayout.ally(theme):org.mods.gd656killicon.client.gui.theme.BattlefieldPovLayout.enemy(theme);
        return new float[]{((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f};
    }

}

