import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry helpers for {@link MapView}.
 *
 * This class exists only to split MapView into smaller files.
 * It intentionally preserves the existing behavior.
 */
final class MapViewGeometry {
    private MapViewGeometry() {}

    static Point2D normalize(Point2D v) {
        if (v == null) return new Point2D(1.0, 0.0);
        double len = Math.hypot(v.getX(), v.getY());
        if (len < 1e-9) return new Point2D(1.0, 0.0);
        return new Point2D(v.getX() / len, v.getY() / len);
    }

    static Point2D laneTangentAt(Point2D worldPos, List<Point2D> polyline) {
        // This function finds the closest lane segment to the vehicle and returns its direction.
        if (worldPos == null || polyline == null || polyline.size() < 2) return null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        Point2D bestDir = null;

        for (int i = 1; i < polyline.size(); i++) {
            Point2D a = polyline.get(i - 1);
            Point2D b = polyline.get(i);
            double vx = b.getX() - a.getX();
            double vy = b.getY() - a.getY();
            double len2 = vx * vx + vy * vy;
            if (len2 < 1e-9) continue;

            double wx = worldPos.getX() - a.getX();
            double wy = worldPos.getY() - a.getY();
            double t = (wx * vx + wy * vy) / len2;
            if (t < 0.0) t = 0.0;
            else if (t > 1.0) t = 1.0;
            double px = a.getX() + t * vx;
            double py = a.getY() + t * vy;
            double dx = worldPos.getX() - px;
            double dy = worldPos.getY() - py;
            double dist2 = dx * dx + dy * dy;
            if (dist2 < bestDist2) {
                bestDist2 = dist2;
                double len = Math.sqrt(len2);
                bestDir = new Point2D(vx / len, vy / len);
            }
        }

        return bestDir;
    }

    static Point2D centroid(List<Point2D> poly) {
        if (poly == null || poly.isEmpty()) return null;
        double sx = 0.0;
        double sy = 0.0;
        for (Point2D p : poly) {
            sx += p.getX();
            sy += p.getY();
        }
        return new Point2D(sx / poly.size(), sy / poly.size());
    }

    static Point2D pointAlongPolyline(List<Point2D> polyline, double t) {
        if (polyline == null || polyline.size() < 2) return null;
        t = Math.max(0.0, Math.min(1.0, t));
        double total = 0.0;
        for (int i = 1; i < polyline.size(); i++) {
            total += polyline.get(i).distance(polyline.get(i - 1));
        }
        if (total < 1e-9) return polyline.get(polyline.size() / 2);
        double target = total * t;
        double acc = 0.0;
        for (int i = 1; i < polyline.size(); i++) {
            Point2D a = polyline.get(i - 1);
            Point2D b = polyline.get(i);
            double seg = b.distance(a);
            if (acc + seg >= target) {
                double localT = (target - acc) / Math.max(1e-9, seg);
                double x = a.getX() + (b.getX() - a.getX()) * localT;
                double y = a.getY() + (b.getY() - a.getY()) * localT;
                return new Point2D(x, y);
            }
            acc += seg;
        }
        return polyline.get(polyline.size() - 1);
    }

    static List<Point2D> parseShape(String shape) {
        List<Point2D> pts = new ArrayList<>();
        String[] pairs = shape.trim().split("\\s+");
        for (String pair : pairs) {
            String[] parts = pair.split(",");
            if (parts.length < 2) continue;
            try {
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                pts.add(new Point2D(x, y));
            } catch (NumberFormatException ignored) {
            }
        }
        return pts;
    }
}
