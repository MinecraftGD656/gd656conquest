package org.mods.gd656conquest.server.room.runtime;

import java.util.List;
import org.mods.gd656conquest.server.room.model.RoomPosition;

/** A Euclidean buffer around the allowed polygons, including concave corners and separate areas. */
public final class FlightBoundaryGeometry {
    private FlightBoundaryGeometry() {}
    public static boolean outside(double x, double z, List<List<RoomPosition>> boundaries, double padding) {
        if (boundaries == null) return false;
        boolean configured = false;
        double squared = Math.max(0, padding) * Math.max(0, padding);
        for (List<RoomPosition> polygon : boundaries) {
            if (polygon == null || polygon.size() < 3) continue;
            configured = true;
            if (RoomBoundaryPolygonSupport.isPointInsideBoundary(x, z, polygon)) return false;
            for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
                RoomPosition a = polygon.get(j), b = polygon.get(i);
                if (distanceSquared(x, z, a.x(), a.z(), b.x(), b.z()) <= squared) return false;
            }
        }
        return configured;
    }
    static double distanceSquared(double x, double z, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, length = dx * dx + dz * dz;
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / length));
        double ex = x - ax - t * dx, ez = z - az - t * dz;
        return ex * ex + ez * ez;
    }
}
