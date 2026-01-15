import javafx.geometry.Point2D;

import java.util.List;
import java.util.Locale;

/**
 * Package-private support types for {@link MapView}.
 * Kept in the default package to match the rest of the project.
 */
final class EdgeInfo {
    final String edgeId;
    final String from;
    final String to;
    final String name;
    final String function;

    EdgeInfo(String edgeId, String from, String to, String name, String function) {
        this.edgeId = edgeId;
        this.from = (from == null) ? "" : from;
        this.to = (to == null) ? "" : to;
        this.name = (name == null) ? "" : name;
        this.function = (function == null) ? "" : function;
    }

    boolean isInternal() {
        return edgeId != null && edgeId.startsWith(":") || "internal".equalsIgnoreCase(function);
    }
}

final class LaneKey {
    final String edgeId;
    final int laneIndex;

    LaneKey(String edgeId, int laneIndex) {
        this.edgeId = edgeId;
        this.laneIndex = laneIndex;
    }
}

final class LaneShape {
    final String laneId;
    final String edgeId;
    final int laneIndex;
    final List<Point2D> polyline;
    final double widthMeters;
    final String allow;
    final String disallow;
    final boolean bikeOnly;
    final boolean pedestrianOnly;

    LaneShape(String id, List<Point2D> p, double w, String allow, String disallow) {
        this.laneId = id;
        LaneKey k = parseLaneKey(id);
        this.edgeId = (k != null) ? k.edgeId : null;
        this.laneIndex = (k != null) ? k.laneIndex : -1;
        this.polyline = p;
        this.widthMeters = w;
        this.allow = (allow == null) ? "" : allow;
        this.disallow = (disallow == null) ? "" : disallow;
        this.bikeOnly = isBikeOnlyLane(id, this.allow);
        this.pedestrianOnly = isPedestrianOnlyLane(id, this.allow);
    }

    static boolean isMotorLane(LaneShape lane) {
        if (lane == null) return false;
        return !lane.pedestrianOnly && !lane.bikeOnly;
    }

    static LaneKey parseLaneKey(String laneId) {
        if (laneId == null || laneId.isEmpty()) return null;
        // SUMO lane ids typically: <edgeId>_<index>
        int underscore = laneId.lastIndexOf('_');
        if (underscore <= 0 || underscore >= laneId.length() - 1) return null;
        String suffix = laneId.substring(underscore + 1);
        int idx;
        try {
            idx = Integer.parseInt(suffix);
        } catch (NumberFormatException ignored) {
            return null;
        }
        String edgeId = laneId.substring(0, underscore);
        return new LaneKey(edgeId, idx);
    }

    static boolean isBikeOnlyLane(String laneId, String allowAttr) {
        // SUMO bicycle lanes are typically encoded via lane permissions:
        // <lane ... allow="bicycle" .../>
        // We treat a lane as bicycle-only if 'bicycle' is allowed and no common motorized classes are allowed.
        String allow = (allowAttr == null) ? "" : allowAttr.trim().toLowerCase(Locale.ROOT);
        String id = (laneId == null) ? "" : laneId.toLowerCase(Locale.ROOT);
        if (id.contains("bicycle") || id.contains("bike")) return true;
        if (allow.isEmpty()) return false;
        if (!allow.contains("bicycle")) return false;

        // Token-ish check (whitespace separated classes).
        String[] tokens = allow.split("\\s+");
        boolean hasBicycle = false;
        boolean hasMotor = false;
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            if ("bicycle".equals(t)) {
                hasBicycle = true;
                continue;
            }
            // If any common motorized class is allowed, it's not a bike-only lane.
            if ("passenger".equals(t) || "bus".equals(t) || "truck".equals(t) || "taxi".equals(t) ||
                "delivery".equals(t) || "motorcycle".equals(t) || "tram".equals(t) || "rail".equals(t)) {
                hasMotor = true;
            }
        }
        return hasBicycle && !hasMotor;
    }

    static boolean isPedestrianOnlyLane(String laneId, String allowAttr) {
        // SUMO sidewalk/footpath lanes are typically encoded via lane permissions:
        // <lane ... allow="pedestrian" .../>
        // These appear as gray paths in SUMO's GUI "standard" scheme.
        String allow = (allowAttr == null) ? "" : allowAttr.trim().toLowerCase(Locale.ROOT);
        String id = (laneId == null) ? "" : laneId.toLowerCase(Locale.ROOT);
        if (id.contains("sidewalk") || id.contains("foot") || id.contains("ped")) return true;
        if (allow.isEmpty()) return false;
        if (!allow.contains("pedestrian")) return false;

        String[] tokens = allow.split("\\s+");
        boolean hasPed = false;
        boolean hasMotor = false;
        boolean hasBicycle = false;
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            if ("pedestrian".equals(t)) {
                hasPed = true;
                continue;
            }
            if ("bicycle".equals(t)) {
                hasBicycle = true;
                continue;
            }
            if ("passenger".equals(t) || "bus".equals(t) || "truck".equals(t) || "taxi".equals(t) ||
                "delivery".equals(t) || "motorcycle".equals(t) || "tram".equals(t) || "rail".equals(t)) {
                hasMotor = true;
            }
        }

        // If it's mixed ped+bike, prefer treating it as bicycle lane visually (orange), not sidewalk.
        if (hasBicycle) return false;
        return hasPed && !hasMotor;
    }
}

final class JunctionShape {
    final String id;
    final boolean hasTrafficLight;
    final List<Point2D> polygon;

    JunctionShape(String id, boolean trafficLight, List<Point2D> p) {
        this.id = id;
        this.hasTrafficLight = trafficLight;
        this.polygon = p;
    }
}

final class TextMarker {
    final String text;
    final Point2D world;

    TextMarker(String text, Point2D world) {
        this.text = text;
        this.world = world;
    }
}

/**
 * Represents a bus stop location for rendering on the map.
 * World position and direction are pre-cached to avoid recalculating every frame.
 */
final class BusStopMarker {
    final String id;
    final String name;
    final String laneId;
    final double startPos;  // distance from lane start in meters
    final double endPos;    // distance from lane start in meters
    final double stopLength;
    double laneWidth;

    // Pre-cached values for fast rendering
    Point2D worldPos;     // position on lane (already offset to side)
    Point2D direction;    // normalized direction vector in world coords

    BusStopMarker(String id, String name, String laneId, double startPos, double endPos) {
        this.id = id;
        this.name = name;
        this.laneId = laneId;
        this.startPos = startPos;
        this.endPos = endPos;
        this.stopLength = Math.max(1.0, Math.abs(endPos - startPos));
        this.laneWidth = 3.2; // default, will be updated
    }
}
