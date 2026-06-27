package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.room.model.RoomPosition;

import java.util.List;

public final class RoomBoundaryPolygonSupport {
    private RoomBoundaryPolygonSupport() {
    }

    public static boolean isPointInsideBoundary(double x, double z, List<RoomPosition> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return true;
        }
        boolean inside = false;
        int size = polygon.size();
        for (int i = 0, j = size - 1; i < size; j = i++) {
            RoomPosition pi = polygon.get(i);
            RoomPosition pj = polygon.get(j);
            double xi = pi.x();
            double zi = pi.z();
            double xj = pj.x();
            double zj = pj.z();
            boolean intersect = ((zi > z) != (zj > z))
                && (x < (xj - xi) * (z - zi) / ((zj - zi) + 1.0E-9D) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }
}
