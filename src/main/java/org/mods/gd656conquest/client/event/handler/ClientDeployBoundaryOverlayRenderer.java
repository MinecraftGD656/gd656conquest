package org.mods.gd656conquest.client.event.handler;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.joml.Matrix4f;
import org.mods.gd656conquest.client.event.handler.ClientCaptureBoundaryRenderer.ResolvedBoundarySegments;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.BoundaryPoint;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.BoundaryPolygon;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.LineSegment;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.TeamToken;
import org.mods.gd656conquest.client.render.ClientWorldHudProjectionSupport;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class ClientDeployBoundaryOverlayRenderer {
    private static final float SCREEN_LINE_WIDTH_PIXELS = 2.5F;
    private static final float MASK_ALPHA = 0.48F;
    private static final float LINE_ALPHA = 0.96F;
    private static final float GUI_MASK_Z = -110.0F;
    private static final float GUI_LINE_Z = -108.0F;
    private static final double EDGE_EPSILON = 1.0E-6D;
    private static final int ROUND_CAP_SEGMENTS = 18;
    private static final double ROUND_CAP_RADIUS_MULTIPLIER = 1.35D;

    private final ClientCaptureBoundaryRenderer boundaryRenderer;
    private long cachedGeometryBoundaryVersion = Long.MIN_VALUE;
    private TeamToken cachedGeometrySelfTeam = TeamToken.NEUTRAL;
    private int cachedGeometryScreenWidth = -1;
    private int cachedGeometryScreenHeight = -1;
    private long cachedProjectionSignature = Long.MIN_VALUE;
    private CachedDeployOverlayGeometry cachedGeometry = CachedDeployOverlayGeometry.empty();

    ClientDeployBoundaryOverlayRenderer(ClientCaptureBoundaryRenderer boundaryRenderer) {
        this.boundaryRenderer = boundaryRenderer;
    }

    void renderDeployBoundaryOverlay(
        ClientCaptureOverlayState state,
        RenderGuiOverlayEvent.Post event,
        TeamToken selfTeam
    ) {
        renderDeployBoundaryOverlay(
            state,
            event.getGuiGraphics(),
            event.getWindow().getGuiScaledWidth(),
            event.getWindow().getGuiScaledHeight(),
            selfTeam
        );
    }

    void renderDeployBoundaryOverlay(
        ClientCaptureOverlayState state,
        GuiGraphics guiGraphics,
        int screenWidth,
        int screenHeight,
        TeamToken selfTeam
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || state.worldProjectionMatrix == null) {
            return;
        }
        float alphaFactor = ClientDeathFlowState.currentDeployIconAlpha();
        if (alphaFactor <= 0.001F) {
            return;
        }
        ResolvedBoundarySegments segments = boundaryRenderer.resolveRenderableSegments(state, selfTeam);
        if (segments.isEmpty()) {
            return;
        }
        CachedDeployOverlayGeometry geometry = resolveCachedGeometry(state, minecraft, selfTeam, screenWidth, screenHeight, segments);
        if (geometry.isEmpty()) {
            return;
        }
        drawOutsideMask(guiGraphics, geometry.maskFills(), alphaFactor);
        drawBoundaryLines(guiGraphics, geometry, selfTeam, alphaFactor);
    }

    private CachedDeployOverlayGeometry resolveCachedGeometry(
        ClientCaptureOverlayState state,
        Minecraft minecraft,
        TeamToken selfTeam,
        int screenWidth,
        int screenHeight,
        ResolvedBoundarySegments segments
    ) {
        long projectionSignature = ClientHudProjectionCacheKey.capture(minecraft, state.worldProjectionMatrix);
        if (isCachedGeometryValid(state, selfTeam, screenWidth, screenHeight, projectionSignature)) {
            return cachedGeometry;
        }
        java.util.Map<WorldPointKey, ScreenPoint> pointProjectionCache = new java.util.HashMap<>();
        List<ProjectedBoundaryPolygon> polygons = projectPolygons(state, minecraft, selfTeam, pointProjectionCache);
        if (polygons.isEmpty()) {
            cachedGeometry = CachedDeployOverlayGeometry.empty();
            updateCachedGeometrySignature(state, selfTeam, screenWidth, screenHeight, projectionSignature);
            return cachedGeometry;
        }
        cachedGeometry = new CachedDeployOverlayGeometry(
            buildMaskFills(screenWidth, screenHeight, polygons),
            projectLineSegments(minecraft, state, segments.selfSegments(), pointProjectionCache),
            projectLineSegments(minecraft, state, segments.enemySegments(), pointProjectionCache),
            projectLineSegments(minecraft, state, segments.neutralSegments(), pointProjectionCache),
            projectLineSegments(minecraft, state, segments.sharedSegments(), pointProjectionCache)
        );
        updateCachedGeometrySignature(state, selfTeam, screenWidth, screenHeight, projectionSignature);
        return cachedGeometry;
    }

    private boolean isCachedGeometryValid(
        ClientCaptureOverlayState state,
        TeamToken selfTeam,
        int screenWidth,
        int screenHeight,
        long projectionSignature
    ) {
        if (cachedGeometryBoundaryVersion != state.boundaryGeometryVersion
            || cachedGeometrySelfTeam != selfTeam
            || cachedGeometryScreenWidth != screenWidth
            || cachedGeometryScreenHeight != screenHeight
            || state.worldProjectionMatrix == null) {
            return false;
        }
        return cachedProjectionSignature == projectionSignature;
    }

    private void updateCachedGeometrySignature(
        ClientCaptureOverlayState state,
        TeamToken selfTeam,
        int screenWidth,
        int screenHeight,
        long projectionSignature
    ) {
        cachedGeometryBoundaryVersion = state.boundaryGeometryVersion;
        cachedGeometrySelfTeam = selfTeam;
        cachedGeometryScreenWidth = screenWidth;
        cachedGeometryScreenHeight = screenHeight;
        cachedProjectionSignature = projectionSignature;
    }

    private List<ProjectedBoundaryPolygon> projectPolygons(
        ClientCaptureOverlayState state,
        Minecraft minecraft,
        TeamToken selfTeam,
        java.util.Map<WorldPointKey, ScreenPoint> pointProjectionCache
    ) {
        List<BoundaryPolygon> rawPolygons = boundaryRenderer.resolveRenderablePolygons(state, selfTeam);
        if (rawPolygons.isEmpty()) {
            return List.of();
        }
        List<ProjectedBoundaryPolygon> projected = new ArrayList<>();
        for (BoundaryPolygon polygon : rawPolygons) {
            ProjectedBoundaryPolygon screenPolygon = projectPolygon(state, minecraft, polygon, pointProjectionCache);
            if (screenPolygon != null) {
                projected.add(screenPolygon);
            }
        }
        return projected.isEmpty() ? List.of() : List.copyOf(projected);
    }

    private ProjectedBoundaryPolygon projectPolygon(
        ClientCaptureOverlayState state,
        Minecraft minecraft,
        BoundaryPolygon polygon,
        java.util.Map<WorldPointKey, ScreenPoint> pointProjectionCache
    ) {
        if (polygon == null || polygon.points() == null || polygon.points().size() < 3) {
            return null;
        }
        List<ScreenPoint> screenPoints = new ArrayList<>();
        ScreenPoint previous = null;
        for (BoundaryPoint point : polygon.points()) {
            ScreenPoint screenPoint = projectWorldPoint(minecraft, state, point.x(), point.y(), point.z(), pointProjectionCache);
            if (screenPoint == null) {
                return null;
            }
            if (previous == null || !previous.isNear(screenPoint)) {
                screenPoints.add(screenPoint);
                previous = screenPoint;
            }
        }
        if (screenPoints.size() >= 2 && screenPoints.get(0).isNear(screenPoints.get(screenPoints.size() - 1))) {
            screenPoints.remove(screenPoints.size() - 1);
        }
        if (screenPoints.size() < 3) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        List<PolygonScanEdge> scanEdges = new ArrayList<>();
        for (int index = 0; index < screenPoints.size(); index++) {
            ScreenPoint start = screenPoints.get(index);
            ScreenPoint end = screenPoints.get((index + 1) % screenPoints.size());
            minX = Math.min(minX, start.x());
            maxX = Math.max(maxX, start.x());
            PolygonScanEdge edge = buildScanEdge(start, end);
            if (edge != null) {
                scanEdges.add(edge);
            }
        }
        if (scanEdges.isEmpty()) {
            return null;
        }
        return new ProjectedBoundaryPolygon(
            polygon.team(),
            List.copyOf(screenPoints),
            List.copyOf(scanEdges),
            minX,
            maxX
        );
    }

    private List<MaskFill> buildMaskFills(int width, int height, List<ProjectedBoundaryPolygon> polygons) {
        if (width <= 0 || height <= 0 || polygons == null || polygons.isEmpty()) {
            return List.of();
        }
        List<MutableMaskFill> activeFills = new ArrayList<>();
        List<MaskFill> fills = new ArrayList<>();
        List<Span> previousOutsideSpans = List.of();
        double maxY = height;
        int scanStartX = width;
        int scanEndX = -1;
        for (ProjectedBoundaryPolygon polygon : polygons) {
            scanStartX = Math.min(scanStartX, clampColumnStart(polygon.minX(), width));
            scanEndX = Math.max(scanEndX, clampColumnEndExclusive(polygon.maxX(), width));
        }
        scanStartX = Mth.clamp(scanStartX, 0, width);
        scanEndX = Mth.clamp(scanEndX, 0, width);
        if (scanStartX > 0) {
            fills.add(new MaskFill(0, 0, scanStartX, height));
        }
        if (scanEndX < width) {
            fills.add(new MaskFill(scanEndX, 0, width, height));
        }
        if (scanStartX >= scanEndX) {
            return fills.isEmpty() ? List.of() : List.copyOf(fills);
        }
        for (int x = scanStartX; x < scanEndX; x++) {
            List<Span> outsideSpans = resolveOutsideSpans(polygons, x + 0.5D, maxY, height);
            if (!sameSpans(previousOutsideSpans, outsideSpans)) {
                flushActiveFills(activeFills, fills);
                for (Span span : outsideSpans) {
                    activeFills.add(new MutableMaskFill(x, (int) Math.floor(span.start()), x + 1, (int) Math.ceil(span.end())));
                }
                previousOutsideSpans = outsideSpans;
                continue;
            }
            for (MutableMaskFill fill : activeFills) {
                fill.endX = x + 1;
            }
        }
        flushActiveFills(activeFills, fills);
        return fills.isEmpty() ? List.of() : List.copyOf(fills);
    }

    private List<Span> resolveOutsideSpans(List<ProjectedBoundaryPolygon> polygons, double sampleX, double maxY, int height) {
        List<Span> spans = resolveMergedInsideSpans(polygons, sampleX);
        List<Span> outsideSpans = new ArrayList<>();
        double fillStartY = 0.0D;
        for (Span span : spans) {
            double insideStart = Mth.clamp(span.start(), 0.0D, maxY);
            double insideEnd = Mth.clamp(span.end(), 0.0D, maxY);
            if (insideStart > fillStartY) {
                outsideSpans.add(new Span(fillStartY, insideStart));
            }
            fillStartY = Math.max(fillStartY, insideEnd);
            if (fillStartY >= maxY) {
                break;
            }
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
        for (int index = 0; index < previous.size(); index++) {
            Span left = previous.get(index);
            Span right = current.get(index);
            if (Math.abs(left.start() - right.start()) > 0.5D || Math.abs(left.end() - right.end()) > 0.5D) {
                return false;
            }
        }
        return true;
    }

    private void flushActiveFills(List<MutableMaskFill> activeFills, List<MaskFill> fills) {
        if (activeFills.isEmpty()) {
            return;
        }
        for (MutableMaskFill fill : activeFills) {
            if (fill.endY > fill.startY && fill.endX > fill.startX) {
                fills.add(new MaskFill(fill.startX, fill.startY, fill.endX, fill.endY));
            }
        }
        activeFills.clear();
    }

    private void drawOutsideMask(
        GuiGraphics guiGraphics,
        List<MaskFill> fills,
        float alphaFactor
    ) {
        int alpha = Mth.clamp(Math.round(255.0F * MASK_ALPHA * alphaFactor), 0, 255);
        if (alpha <= 0 || fills == null || fills.isEmpty()) {
            return;
        }
        int color = alpha << 24;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, GUI_MASK_Z);
        for (MaskFill fill : fills) {
            if (fill.endY() > fill.startY()) {
                guiGraphics.fill(fill.startX(), fill.startY(), fill.endX(), fill.endY(), color);
            }
        }
        guiGraphics.pose().popPose();
    }

    private List<Span> resolveMergedInsideSpans(List<ProjectedBoundaryPolygon> polygons, double sampleX) {
        List<Span> spans = new ArrayList<>();
        for (ProjectedBoundaryPolygon polygon : polygons) {
            spans.addAll(resolveInsideSpansForPolygon(polygon, sampleX));
        }
        if (spans.isEmpty()) {
            return List.of();
        }
        spans.sort(Comparator.comparingDouble(Span::start));
        List<Span> merged = new ArrayList<>();
        for (Span span : spans) {
            if (merged.isEmpty()) {
                merged.add(span);
                continue;
            }
            Span last = merged.get(merged.size() - 1);
            if (span.start() <= last.end() + 0.5D) {
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
        List<Double> intersections = new ArrayList<>();
        for (PolygonScanEdge edge : polygon.scanEdges()) {
            if (sampleX < edge.minX() || sampleX >= edge.maxX()) {
                continue;
            }
            intersections.add(edge.yAt(sampleX));
        }
        if (intersections.size() < 2) {
            return List.of();
        }
        intersections.sort(Double::compareTo);
        List<Span> spans = new ArrayList<>();
        for (int index = 0; index + 1 < intersections.size(); index += 2) {
            double startY = intersections.get(index);
            double endY = intersections.get(index + 1);
            if (endY - startY <= EDGE_EPSILON) {
                continue;
            }
            spans.add(new Span(startY, endY));
        }
        return spans;
    }

    private void drawBoundaryLines(
        GuiGraphics guiGraphics,
        CachedDeployOverlayGeometry geometry,
        TeamToken selfTeam,
        float alphaFactor
    ) {
        float[] selfColor = resolveBoundaryColorForTeam(selfTeam, true);
        float[] enemyColor = resolveBoundaryColorForTeam(selfTeam, false);
        float[] neutralColor = new float[]{1.0F, 1.0F, 1.0F};
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, GUI_LINE_Z);
        Matrix4f matrix = guiGraphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            drawProjectedSegmentBucket(matrix, geometry.selfSegments(), selfColor[0], selfColor[1], selfColor[2], alphaFactor * LINE_ALPHA);
            drawProjectedSegmentBucket(matrix, geometry.enemySegments(), enemyColor[0], enemyColor[1], enemyColor[2], alphaFactor * LINE_ALPHA);
            drawProjectedSegmentBucket(matrix, geometry.neutralSegments(), neutralColor[0], neutralColor[1], neutralColor[2], alphaFactor * LINE_ALPHA);
            drawProjectedSegmentBucket(matrix, geometry.sharedSegments(), neutralColor[0], neutralColor[1], neutralColor[2], alphaFactor * LINE_ALPHA);
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            guiGraphics.pose().popPose();
        }
    }

    private void drawProjectedSegmentBucket(
        Matrix4f matrix,
        List<ProjectedLineSegment> projectedSegments,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        if (projectedSegments.isEmpty() || alpha <= 0.001F) {
            return;
        }
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        appendProjectedSegments(buffer, matrix, projectedSegments, red, green, blue, alpha);
        appendProjectedEndpointCaps(buffer, matrix, projectedSegments, red, green, blue, alpha);
        BufferUploader.drawWithShader(buffer.end());
    }

    private List<ProjectedLineSegment> projectLineSegments(
        Minecraft minecraft,
        ClientCaptureOverlayState state,
        List<LineSegment> segments,
        java.util.Map<WorldPointKey, ScreenPoint> pointProjectionCache
    ) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        List<ProjectedLineSegment> projected = new ArrayList<>();
        for (LineSegment segment : segments) {
            ScreenPoint start = projectWorldPoint(minecraft, state, segment.ax(), segment.ay(), segment.az(), pointProjectionCache);
            ScreenPoint end = projectWorldPoint(minecraft, state, segment.bx(), segment.by(), segment.bz(), pointProjectionCache);
            if (start == null || end == null || start.isNear(end)) {
                continue;
            }
            projected.add(new ProjectedLineSegment(start, end));
        }
        return projected.isEmpty() ? List.of() : List.copyOf(projected);
    }

    private PolygonScanEdge buildScanEdge(ScreenPoint start, ScreenPoint end) {
        double dx = end.x() - start.x();
        if (Math.abs(dx) <= EDGE_EPSILON) {
            return null;
        }
        if (start.x() <= end.x()) {
            return new PolygonScanEdge(start.x(), end.x(), start.y(), (end.y() - start.y()) / dx);
        }
        return new PolygonScanEdge(end.x(), start.x(), end.y(), (start.y() - end.y()) / -dx);
    }

    private int clampColumnStart(double minX, int width) {
        return Mth.clamp((int) Math.floor(minX), 0, width);
    }

    private int clampColumnEndExclusive(double maxX, int width) {
        return Mth.clamp((int) Math.ceil(maxX), 0, width);
    }

    private void appendProjectedSegments(
        BufferBuilder buffer,
        Matrix4f matrix,
        List<ProjectedLineSegment> segments,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        if (segments == null || segments.isEmpty() || alpha <= 0.001F) {
            return;
        }
        for (ProjectedLineSegment segment : segments) {
            appendSegmentTriangles(buffer, matrix, segment.start(), segment.end(), red, green, blue, alpha);
        }
    }

    private void appendProjectedEndpointCaps(
        BufferBuilder buffer,
        Matrix4f matrix,
        List<ProjectedLineSegment> segments,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        Set<String> seenEndpoints = new java.util.HashSet<>();
        double radius = SCREEN_LINE_WIDTH_PIXELS * 0.5D * ROUND_CAP_RADIUS_MULTIPLIER;
        for (ProjectedLineSegment segment : segments) {
            appendScreenRoundCap(buffer, matrix, segment.start(), radius, red, green, blue, alpha, seenEndpoints);
            appendScreenRoundCap(buffer, matrix, segment.end(), radius, red, green, blue, alpha, seenEndpoints);
        }
    }

    private ScreenPoint projectWorldPoint(
        Minecraft minecraft,
        ClientCaptureOverlayState state,
        double x,
        double y,
        double z,
        java.util.Map<WorldPointKey, ScreenPoint> pointProjectionCache
    ) {
        WorldPointKey key = new WorldPointKey(x, y, z);
        ScreenPoint cached = pointProjectionCache.get(key);
        if (cached != null) {
            return cached;
        }
        ClientWorldHudProjectionSupport.ProjectedPoint projected =
            ClientWorldHudProjectionSupport.projectToScreen(minecraft, state.worldProjectionMatrix, new Vec3(x, y, z));
        ScreenPoint screenPoint = projected == null ? null : new ScreenPoint(projected.screenX(), projected.screenY());
        if (screenPoint != null) {
            pointProjectionCache.put(key, screenPoint);
        }
        return screenPoint;
    }

    private void appendSegmentTriangles(
        BufferBuilder buffer,
        Matrix4f matrix,
        ScreenPoint start,
        ScreenPoint end,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= EDGE_EPSILON) {
            return;
        }
        double halfWidth = SCREEN_LINE_WIDTH_PIXELS * 0.5D;
        double offsetX = -dy / length * halfWidth;
        double offsetY = dx / length * halfWidth;
        float axPlus = (float) (start.x() + offsetX);
        float ayPlus = (float) (start.y() + offsetY);
        float bxPlus = (float) (end.x() + offsetX);
        float byPlus = (float) (end.y() + offsetY);
        float bxMinus = (float) (end.x() - offsetX);
        float byMinus = (float) (end.y() - offsetY);
        float axMinus = (float) (start.x() - offsetX);
        float ayMinus = (float) (start.y() - offsetY);
        appendTriangle(buffer, matrix, axPlus, ayPlus, bxPlus, byPlus, bxMinus, byMinus, red, green, blue, alpha);
        appendTriangle(buffer, matrix, axPlus, ayPlus, bxMinus, byMinus, axMinus, ayMinus, red, green, blue, alpha);
    }

    private void appendScreenRoundCap(
        BufferBuilder buffer,
        Matrix4f matrix,
        ScreenPoint center,
        double radius,
        float red,
        float green,
        float blue,
        float alpha,
        Set<String> seenEndpoints
    ) {
        String key = roundedScreenPointKey(center);
        if (!seenEndpoints.add(key)) {
            return;
        }
        for (int step = 0; step < ROUND_CAP_SEGMENTS; step++) {
            double startAngle = (Math.PI * 2.0D * step) / ROUND_CAP_SEGMENTS;
            double endAngle = (Math.PI * 2.0D * (step + 1)) / ROUND_CAP_SEGMENTS;
            float startX = (float) (center.x() + Math.cos(startAngle) * radius);
            float startY = (float) (center.y() + Math.sin(startAngle) * radius);
            float endX = (float) (center.x() + Math.cos(endAngle) * radius);
            float endY = (float) (center.y() + Math.sin(endAngle) * radius);
            appendTriangle(buffer, matrix, (float) center.x(), (float) center.y(), startX, startY, endX, endY, red, green, blue, alpha);
        }
    }

    private void appendTriangle(
        BufferBuilder buffer,
        Matrix4f matrix,
        float ax,
        float ay,
        float bx,
        float by,
        float cx,
        float cy,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        buffer.vertex(matrix, ax, ay, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, bx, by, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, cx, cy, 0.0F).color(red, green, blue, alpha).endVertex();
    }

    private String roundedScreenPointKey(ScreenPoint point) {
        long rx = Math.round(point.x() * 100.0D);
        long ry = Math.round(point.y() * 100.0D);
        return rx + "," + ry;
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

    private double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private record ScreenPoint(double x, double y) {
        boolean isNear(ScreenPoint other) {
            if (other == null) {
                return false;
            }
            return Math.abs(x - other.x) <= 0.5D && Math.abs(y - other.y) <= 0.5D;
        }
    }

    private record ProjectedBoundaryPolygon(
        TeamToken team,
        List<ScreenPoint> points,
        List<PolygonScanEdge> scanEdges,
        double minX,
        double maxX
    ) {
    }

    private record PolygonScanEdge(double minX, double maxX, double startY, double slopeYPerX) {
        double yAt(double sampleX) {
            return startY + (sampleX - minX) * slopeYPerX;
        }
    }

    private record ProjectedLineSegment(ScreenPoint start, ScreenPoint end) {
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

    private record WorldPointKey(long x, long y, long z) {
        private WorldPointKey(double x, double y, double z) {
            this(Math.round(x * 1000.0D), Math.round(y * 1000.0D), Math.round(z * 1000.0D));
        }
    }

    private record Span(double start, double end) {
    }

    private record MaskFill(int startX, int startY, int endX, int endY) {
    }

    private record CachedDeployOverlayGeometry(
        List<MaskFill> maskFills,
        List<ProjectedLineSegment> selfSegments,
        List<ProjectedLineSegment> enemySegments,
        List<ProjectedLineSegment> neutralSegments,
        List<ProjectedLineSegment> sharedSegments
    ) {
        private static final CachedDeployOverlayGeometry EMPTY =
            new CachedDeployOverlayGeometry(List.of(), List.of(), List.of(), List.of(), List.of());

        static CachedDeployOverlayGeometry empty() {
            return EMPTY;
        }

        boolean isEmpty() {
            return maskFills.isEmpty()
                && selfSegments.isEmpty()
                && enemySegments.isEmpty()
                && neutralSegments.isEmpty()
                && sharedSegments.isEmpty();
        }
    }
}
