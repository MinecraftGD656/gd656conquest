package org.mods.gd656conquest.common.match;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Clip before perspective division, keeping partially visible regions during camera travel. */
public final class BriefingPolygonProjection {
    public record Point(double x, double y) {}
    private static final float NEAR_MARGIN = 1.0E-5F;

    private BriefingPolygonProjection() {}

    public static List<Point> project(List<Vec3> polygon, Matrix4f matrix, int width, int height) {
        if (polygon.size() < 3 || matrix == null) return List.of();
        List<Vector4f> vertices = new ArrayList<>();
        for (Vec3 point : polygon) vertices.add(matrix.transform(new Vector4f((float)point.x, (float)point.y, (float)point.z, 1)));
        vertices = clipNear(vertices);
        if (vertices.size() < 3) return List.of();
        List<Point> result = new ArrayList<>();
        for (Vector4f vertex : vertices) {
            if (vertex.w <= 1.0E-6F) return List.of();
            double x = width * .5 * (1 + vertex.x / (double)vertex.w);
            double y = height * .5 * (1 - vertex.y / (double)vertex.w);
            if (!Double.isFinite(x) || !Double.isFinite(y)) return List.of();
            result.add(new Point(x, y));
        }
        return result;
    }

    private static List<Vector4f> clipNear(List<Vector4f> vertices) {
        List<Vector4f> result = new ArrayList<>();
        Vector4f previous = vertices.get(vertices.size() - 1);
        float previousDistance = previous.z + previous.w - NEAR_MARGIN;
        for (Vector4f current : vertices) {
            float distance = current.z + current.w - NEAR_MARGIN;
            if ((distance >= 0) != (previousDistance >= 0)) {
                float t = previousDistance / (previousDistance - distance);
                result.add(new Vector4f(previous).lerp(current, t));
            }
            if (distance >= 0) result.add(current);
            previous = current;
            previousDistance = distance;
        }
        return result;
    }
}
