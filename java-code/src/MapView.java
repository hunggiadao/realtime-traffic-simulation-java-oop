import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Simple JavaFX map renderer that draws the SUMO network and vehicles on a canvas.
 * It parses the SUMO net file for edge shapes and plots vehicle positions supplied
 * by TraCI.
 */
public class MapView extends Pane {
    private static final Logger LOGGER = Logger.getLogger(MapView.class.getName());
    private static class LaneShape {
        final String laneId;
        final String edgeId;
        final int laneIndex;
        final List<Point2D> polyline;
        final double widthMeters;
        LaneShape(String id, List<Point2D> p, double w) {
            this.laneId = id;
            LaneKey k = parseLaneKey(id);
            this.edgeId = (k != null) ? k.edgeId : null;
            this.laneIndex = (k != null) ? k.laneIndex : -1;
            this.polyline = p;
            this.widthMeters = w;
        }
    }

    private static final class LaneKey {
        final String edgeId;
        final int laneIndex;
        LaneKey(String edgeId, int laneIndex) {
            this.edgeId = edgeId;
            this.laneIndex = laneIndex;
        }
    }

    private static LaneKey parseLaneKey(String laneId) {
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

	private static class JunctionShape {
        final String id;
        final boolean trafficLight;
		final List<Point2D> polygon;
        JunctionShape(String id, boolean trafficLight, List<Point2D> p) {
            this.id = id;
            this.trafficLight = trafficLight;
			this.polygon = p;
		}
	}

    private static final class TextMarker {
        final String text;
        final Point2D world;
        TextMarker(String text, Point2D world) {
            this.text = text;
            this.world = world;
        }
    }

    private final Canvas canvas = new Canvas();
    private final List<LaneShape> lanes = new ArrayList<>();
	private final Map<String, LaneShape> lanesById = new HashMap<>();
	private final List<JunctionShape> junctions = new ArrayList<>();
    private final Map<String, Point2D> edgeLabelWorldPos = new HashMap<>();
    private final List<TextMarker> trafficLightMarkers = new ArrayList<>();
    private Map<String, Point2D> vehiclePositions;
	private Map<String, Color> vehicleColors;
    private Map<String, String> vehicleLaneIds;
	private Map<String, Color> laneSignalColors;
    private double minX = 0, maxX = 1, minY = 0, maxY = 1;

    // zoom / pan state
    private double baseScale = 1.0;
    private double userScale = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double padding = 20.0;
    private double lastMouseX;
    private double lastMouseY;

    public MapView() {
        getChildren().add(canvas);
        widthProperty().addListener((obs, o, n) -> layoutCanvas());
        heightProperty().addListener((obs, o, n) -> layoutCanvas());
        enableInteractions();
    }

    /**
     * Loads the SUMO network file and returns the number of lanes parsed.
     */
    public int loadNetwork(File netFile) {
                lanes.clear();
		lanesById.clear();
		junctions.clear();
        edgeLabelWorldPos.clear();
        trafficLightMarkers.clear();
        int laneCount = 0;
        if (netFile == null || !netFile.exists()) {
            LOGGER.warning("Net file missing: " + (netFile == null ? "null" : netFile.getPath()));
            redraw();
            return 0;
        }
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(netFile);
            doc.getDocumentElement().normalize();
            List<Point2D> allPoints = new ArrayList<>();

            // Parse junction shapes (for nicer black junction areas like SUMO GUI)
            NodeList juncNodes = doc.getElementsByTagName("junction");
            for (int i = 0; i < juncNodes.getLength(); i++) {
                Element j = (Element) juncNodes.item(i);
                if (!j.hasAttribute("shape")) continue;
                String jId = j.hasAttribute("id") ? j.getAttribute("id") : "";
                boolean isTl = false;
                if (j.hasAttribute("type")) {
                    String t = j.getAttribute("type");
                    // net.xml commonly uses 'traffic_light' for TLS-controlled junctions.
                    isTl = (t != null) && (t.toLowerCase().contains("traffic_light"));
                    if ("internal".equalsIgnoreCase(t)) continue;
                }
                // Skip internal helper junctions
                List<Point2D> poly = parseShape(j.getAttribute("shape"));
                if (poly.isEmpty()) continue;
			junctions.add(new JunctionShape(jId, isTl, poly));
                allPoints.addAll(poly);

			if (isTl && jId != null && !jId.isEmpty()) {
				Point2D c = centroid(poly);
				if (c != null) {
					trafficLightMarkers.add(new TextMarker("TL " + jId, c));
				}
			}
            }

            NodeList laneNodes = doc.getElementsByTagName("lane");
            for (int i = 0; i < laneNodes.getLength(); i++) {
                Element lane = (Element) laneNodes.item(i);
                if (!lane.hasAttribute("shape")) continue;
                String laneId = lane.hasAttribute("id") ? lane.getAttribute("id") : null;
                String shape = lane.getAttribute("shape");
                double width = 3.2;
                if (lane.hasAttribute("width")) {
                    try {
                        width = Double.parseDouble(lane.getAttribute("width"));
                    } catch (NumberFormatException ignored) {}
                }
                List<Point2D> polyline = parseShape(shape);
                if (polyline.isEmpty()) continue;
				LaneShape ls = new LaneShape(laneId, polyline, width);
				lanes.add(ls);
				if (laneId != null && !laneId.isEmpty()) {
					lanesById.put(laneId, ls);
				}
                allPoints.addAll(polyline);
                laneCount++;
            }

            // Build edge label positions from lanes (one label per edge).
            for (LaneShape lane : lanes) {
                if (lane == null || lane.edgeId == null || lane.edgeId.isEmpty()) continue;
                if (lane.edgeId.startsWith(":")) continue;
                if (edgeLabelWorldPos.containsKey(lane.edgeId)) continue;
                Point2D p = pointAlongPolyline(lane.polyline, 0.55);
                if (p != null) {
                    edgeLabelWorldPos.put(lane.edgeId, p);
                }
            }
            if (!allPoints.isEmpty()) {
                minX = allPoints.stream().mapToDouble(Point2D::getX).min().orElse(0);
                maxX = allPoints.stream().mapToDouble(Point2D::getX).max().orElse(1);
                minY = allPoints.stream().mapToDouble(Point2D::getY).min().orElse(0);
                maxY = allPoints.stream().mapToDouble(Point2D::getY).max().orElse(1);
            } else {
                minX = maxX = minY = maxY = 0;
            }
                LOGGER.info(String.format(Locale.US,
                    "Loaded %d lanes. Bounds x:[%.2f, %.2f] y:[%.2f, %.2f]",
                    laneCount, minX, maxX, minY, maxY));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load network", e);
        }
        offsetX = 0;
        offsetY = 0;
        userScale = 1.0;
        computeBaseScale();
        redraw();
        return laneCount;
    }

    public void updateVehicles(Map<String, Point2D> positions, Map<String, Color> colors) {
        updateVehicles(positions, colors, null);
    }

    public void updateVehicles(Map<String, Point2D> positions, Map<String, Color> colors, Map<String, String> laneIds) {
        this.vehiclePositions = positions;
        this.vehicleColors = colors;
        this.vehicleLaneIds = laneIds;
        redraw();
    }

    public void updateTrafficSignals(Map<String, Color> laneSignalColors) {
        this.laneSignalColors = laneSignalColors;
        recalculateAndRedraw();
    }

    private void recalculateAndRedraw() {
        redraw();
    }

    private void layoutCanvas() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        computeBaseScale();
        redraw();
    }

    private void redraw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        // Simple neutral background; roads themselves carry the focus.
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, w, h);
        if (w <= 0 || h <= 0) return;

        double scale = baseScale * userScale;

        // No network loaded? Show hint.
        if (lanes.isEmpty()) {
            g.setFill(Color.web("#888888"));
            g.fillText("No network loaded. Check your .sumocfg and net path.", 20, 30);
            return;
        }

        // Dark road styling similar to SUMO GUI: black asphalt
        // with slightly lighter lane markings.
        Color roadEdge = Color.web("#000000");
        Color roadFill = Color.web("#101010");
        Color laneLine = Color.web("#d0d0d0");

		g.setLineCap(StrokeLineCap.ROUND);
		g.setLineJoin(StrokeLineJoin.ROUND);

        // First draw junction polygons so they appear under the lane strokes
        g.setFill(roadFill);
        for (JunctionShape j : junctions) {
            if (j.polygon.isEmpty()) continue;
            int n = j.polygon.size();
            double[] xs = new double[n];
            double[] ys = new double[n];
            for (int i = 0; i < n; i++) {
                Point2D tp = transform(j.polygon.get(i), h, scale);
                xs[i] = tp.getX();
                ys[i] = tp.getY();
            }
            g.fillPolygon(xs, ys, n);
        }

		for (LaneShape lane : lanes) {
			double laneScreenWidth = Math.max(1.5, lane.widthMeters * scale);
			double outerW = laneScreenWidth + 1.2;

			// Draw each lane as a single stroked path (smoother joins than per-segment stroking).
			strokePolyline(g, lane.polyline, h, scale, outerW, roadEdge, null);
			strokePolyline(g, lane.polyline, h, scale, laneScreenWidth, roadFill, null);

			// Subtle center dashed marking (avoids the busy double-side markings).
			if (laneScreenWidth >= 6.0) {
				strokePolyline(g, lane.polyline, h, scale, 1.1, laneLine, new double[]{10, 10});
			}

            // Lane edge markings to make lane separation obvious.
            if (laneScreenWidth >= 5.0) {
                drawLaneEdges(g, lane.polyline, h, scale, laneScreenWidth, laneLine);
            }
		}

        // Draw edge IDs and traffic light IDs when zoomed in enough to read.
        drawLabels(g, h, scale);

        // Draw direction arrows once per road (per edgeId) to avoid clutter.
        drawRoadDirectionArrows(g, h, scale, Color.web("#bdbdbd"));

        // Draw traffic light stop lines (overlay on top of roads)
        if (laneSignalColors != null && !laneSignalColors.isEmpty()) {
            for (Entry<String, Color> e : laneSignalColors.entrySet()) {
                LaneShape lane = lanesById.get(e.getKey());
                if (lane == null || lane.polyline == null || lane.polyline.size() < 2) continue;
                int n = lane.polyline.size();
                Point2D worldPrev = lane.polyline.get(n - 2);
                Point2D worldEnd = lane.polyline.get(n - 1);
                Point2D pPrev = transform(worldPrev, h, scale);
                Point2D pEnd = transform(worldEnd, h, scale);
                double dx = pEnd.getX() - pPrev.getX();
                double dy = pEnd.getY() - pPrev.getY();
                double len = Math.hypot(dx, dy);
                if (len < 0.001) continue;
                double ux = dx / len;
                double uy = dy / len;
                // Normal vector for stop line across lane direction
                double nx = -uy;
                double ny = ux;

                double laneScreenWidth = Math.max(1.5, lane.widthMeters * scale);
                double lineLen = Math.max(7.0, laneScreenWidth * 1.4);
                double backShift = Math.max(3.0, laneScreenWidth * 0.7);
                double cx = pEnd.getX() - ux * backShift;
                double cy = pEnd.getY() - uy * backShift;

                double x1 = cx - nx * (lineLen / 2.0);
                double y1 = cy - ny * (lineLen / 2.0);
                double x2 = cx + nx * (lineLen / 2.0);
                double y2 = cy + ny * (lineLen / 2.0);

                Color c = (e.getValue() != null) ? e.getValue() : Color.RED;
                g.setLineDashes(null);
                // Outline for readability on dark roads
                g.setStroke(Color.WHITE);
                g.setLineWidth(Math.max(3.2, laneScreenWidth * 0.6));
                g.strokeLine(x1, y1, x2, y2);

                g.setStroke(c);
                g.setLineWidth(Math.max(2.0, laneScreenWidth * 0.35));
                g.strokeLine(x1, y1, x2, y2);
            }
        }

        // Draw vehicles
        if (vehiclePositions != null) {
            double radius = 4.0;
            for (Entry<String, Point2D> e : vehiclePositions.entrySet()) {
                Color c = Color.RED;
                if (vehicleColors != null) {
                    Color mapped = vehicleColors.get(e.getKey());
                    if (mapped != null) c = mapped;
                }

                Point2D worldPos = e.getValue();
                Point2D drawWorld = worldPos;
                if (vehicleLaneIds != null) {
                    String laneId = vehicleLaneIds.get(e.getKey());
                    LaneShape lane = (laneId != null) ? lanesById.get(laneId) : null;
                    if (lane != null && lane.polyline != null && lane.polyline.size() >= 2 && worldPos != null) {
                        double offsetMeters = Math.max(0.35, lane.widthMeters * 0.22);
                        double sign = ((laneId.hashCode() & 1) == 0) ? 1.0 : -1.0;
                        drawWorld = offsetAlongLaneNormal(worldPos, lane.polyline, offsetMeters * sign);
                    }
                }

                Point2D tp = transform(drawWorld, h, scale);

                // Outline improves visibility on dark roads.
                g.setStroke(Color.WHITE);
                g.setLineWidth(1.2);
                g.strokeOval(tp.getX() - radius, tp.getY() - radius, radius * 2, radius * 2);

                g.setFill(c);
                g.fillOval(tp.getX() - radius, tp.getY() - radius, radius * 2, radius * 2);
            }
        }
    }

    private void drawLabels(GraphicsContext g, double height, double scale) {
        // Reduce clutter: only show labels when zoomed in enough.
        if (scale < 0.8) return;
        double fontSize = clamp(10.0 + (userScale - 1.0) * 2.0, 10.0, 16.0);
        g.setFont(Font.font(fontSize));

        // Edge labels
        for (Entry<String, Point2D> e : edgeLabelWorldPos.entrySet()) {
            Point2D tp = transform(e.getValue(), height, scale);
            drawTag(g, tp.getX(), tp.getY(), e.getKey(), Color.WHITE, Color.web("#333333"));
        }

        // Traffic light labels
        for (TextMarker tl : trafficLightMarkers) {
            Point2D tp = transform(tl.world, height, scale);
            // Small marker dot
            g.setFill(Color.web("#ffcc00"));
            g.fillOval(tp.getX() - 2.5, tp.getY() - 2.5, 5, 5);
            drawTag(g, tp.getX() + 6, tp.getY() - 6, tl.text, Color.WHITE, Color.web("#333333"));
        }
    }

    private void drawTag(GraphicsContext g, double x, double y, String text, Color bg, Color fg) {
        if (text == null || text.isEmpty()) return;
        double padX = 4.0;
        double padY = 2.0;
        // Approximate text bounds without measuring: good enough for short IDs.
        double w = padX * 2 + text.length() * g.getFont().getSize() * 0.55;
        double h = padY * 2 + g.getFont().getSize();
        g.setFill(bg);
        g.fillRoundRect(x - padX, y - h + padY, w, h, 6, 6);
        g.setStroke(Color.web("#000000"));
        g.setLineWidth(0.6);
        g.strokeRoundRect(x - padX, y - h + padY, w, h, 6, 6);
        g.setFill(fg);
        g.fillText(text, x, y);
    }

    private static Point2D centroid(List<Point2D> poly) {
        if (poly == null || poly.isEmpty()) return null;
        double sx = 0.0;
        double sy = 0.0;
        for (Point2D p : poly) {
            sx += p.getX();
            sy += p.getY();
        }
        return new Point2D(sx / poly.size(), sy / poly.size());
    }

    private static Point2D pointAlongPolyline(List<Point2D> polyline, double t) {
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

    private Point2D offsetAlongLaneNormal(Point2D worldPos, List<Point2D> lanePolyline, double offsetMeters) {
        if (worldPos == null || lanePolyline == null || lanePolyline.size() < 2) return worldPos;
        double bestDist2 = Double.POSITIVE_INFINITY;
        double bestTx = 0.0;
        double bestTy = 0.0;

        for (int i = 1; i < lanePolyline.size(); i++) {
            Point2D a = lanePolyline.get(i - 1);
            Point2D b = lanePolyline.get(i);
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
                bestTx = vx / len;
                bestTy = vy / len;
            }
        }

        // Normal to tangent
        double nx = -bestTy;
        double ny = bestTx;
        return new Point2D(worldPos.getX() + nx * offsetMeters, worldPos.getY() + ny * offsetMeters);
    }

    private Point2D transform(Point2D p, double height, double scale) {
        double x = padding + (p.getX() - minX) * scale + offsetX;
        double y = padding + (maxY - p.getY()) * scale + offsetY; // flip Y for screen coords
        return new Point2D(x, y);
    }

    private void strokePolyline(GraphicsContext g, List<Point2D> worldPolyline, double height, double scale,
                             double width, Color stroke, double[] dashes) {
        if (worldPolyline == null || worldPolyline.size() < 2) return;
        g.setLineWidth(width);
        g.setStroke(stroke);
        g.setLineDashes(dashes);
        Point2D p0 = transform(worldPolyline.get(0), height, scale);
        g.beginPath();
        g.moveTo(p0.getX(), p0.getY());
        for (int i = 1; i < worldPolyline.size(); i++) {
            Point2D pi = transform(worldPolyline.get(i), height, scale);
            g.lineTo(pi.getX(), pi.getY());
        }
        g.stroke();
        g.setLineDashes(null);
    }

    private void drawLaneEdges(GraphicsContext g, List<Point2D> worldPolyline, double height, double scale,
                              double laneScreenWidth, Color stroke) {
        if (worldPolyline == null || worldPolyline.size() < 2) return;
        g.setStroke(stroke);
        g.setLineDashes(null);
        g.setLineWidth(Math.max(0.9, Math.min(1.4, laneScreenWidth * 0.12)));

        for (int i = 1; i < worldPolyline.size(); i++) {
            Point2D w1 = worldPolyline.get(i - 1);
            Point2D w2 = worldPolyline.get(i);
            Point2D p1 = transform(w1, height, scale);
            Point2D p2 = transform(w2, height, scale);
            double dx = p2.getX() - p1.getX();
            double dy = p2.getY() - p1.getY();
            double len = Math.hypot(dx, dy);
            if (len < 0.0001) continue;
            double nx = -dy / len;
            double ny = dx / len;

            double offset = Math.max(1.8, laneScreenWidth / 2.0);
            // draw both edges
            g.strokeLine(p1.getX() + nx * offset, p1.getY() + ny * offset,
                         p2.getX() + nx * offset, p2.getY() + ny * offset);
            g.strokeLine(p1.getX() - nx * offset, p1.getY() - ny * offset,
                         p2.getX() - nx * offset, p2.getY() - ny * offset);
        }
    }

    private void drawRoadDirectionArrows(GraphicsContext g, double height, double scale, Color stroke) {
        // Group lanes by edgeId so arrows are drawn once per road direction.
        Map<String, List<LaneShape>> byEdge = new HashMap<>();
        for (LaneShape lane : lanes) {
            if (lane == null || lane.edgeId == null || lane.edgeId.isEmpty()) continue;
            // Skip internal lanes (SUMO usually prefixes with ':')
            if (lane.edgeId.startsWith(":")) continue;
            byEdge.computeIfAbsent(lane.edgeId, k -> new ArrayList<>()).add(lane);
        }
        if (byEdge.isEmpty()) return;

        g.setStroke(stroke);
        g.setLineDashes(null);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);

        for (List<LaneShape> group : byEdge.values()) {
            if (group == null || group.isEmpty()) continue;
            group.sort(Comparator.comparingInt(ls -> (ls != null) ? ls.laneIndex : Integer.MAX_VALUE));
            LaneShape rep = group.get(group.size() / 2);
            if (rep == null || rep.polyline == null || rep.polyline.size() < 2) continue;

            // Estimate road width from number of lanes.
            double lanesCount = group.size();
            double laneScreenWidth = Math.max(1.5, rep.widthMeters * scale);
            double roadScreenWidth = Math.max(laneScreenWidth, laneScreenWidth * lanesCount);
            if (roadScreenWidth < 6.0) continue;

            drawSlimArrowsOnPolyline(g, rep.polyline, height, scale, roadScreenWidth);
        }
    }

    private void drawSlimArrowsOnPolyline(GraphicsContext g, List<Point2D> worldPolyline, double height, double scale,
                                 double roadScreenWidth) {
        if (worldPolyline == null || worldPolyline.size() < 2) return;
        // Arrow cadence in screen pixels.
        double spacing = Math.max(75.0, roadScreenWidth * 2.6);
        double shaftLen = Math.max(10.0, Math.min(22.0, roadScreenWidth * 0.45));
        double headLen = Math.max(4.0, shaftLen * 0.38);
        double headAngle = Math.toRadians(28);
        g.setLineWidth(Math.max(1.1, Math.min(1.8, roadScreenWidth * 0.06)));

        double traveled = 0.0;
        double nextAt = spacing;

        for (int i = 1; i < worldPolyline.size(); i++) {
            Point2D p1 = transform(worldPolyline.get(i - 1), height, scale);
            Point2D p2 = transform(worldPolyline.get(i), height, scale);
            double dx = p2.getX() - p1.getX();
            double dy = p2.getY() - p1.getY();
            double segLen = Math.hypot(dx, dy);
            if (segLen < 0.0001) continue;
            double ux = dx / segLen;
            double uy = dy / segLen;

            while (traveled + segLen >= nextAt) {
                double t = (nextAt - traveled) / segLen;
                double cx = p1.getX() + dx * t;
                double cy = p1.getY() + dy * t;

                double baseX = cx;
                double baseY = cy;
                double tipX = cx + ux * shaftLen;
                double tipY = cy + uy * shaftLen;

                // shaft
                g.strokeLine(baseX, baseY, tipX, tipY);

                // head: two short lines rotated from direction
                double cos = Math.cos(headAngle);
                double sin = Math.sin(headAngle);
                // rotate (-u) by +/- angle
                double bx = -ux;
                double by = -uy;
                double lx = bx * cos - by * sin;
                double ly = bx * sin + by * cos;
                double rx = bx * cos + by * sin;
                double ry = -bx * sin + by * cos;
                g.strokeLine(tipX, tipY, tipX + lx * headLen, tipY + ly * headLen);
                g.strokeLine(tipX, tipY, tipX + rx * headLen, tipY + ry * headLen);

                nextAt += spacing;
            }
            traveled += segLen;
        }
    }

    private List<Point2D> parseShape(String shape) {
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

    private void computeBaseScale() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            baseScale = 1.0;
            return;
        }
        double scaleX = (w - 2 * padding) / (maxX - minX + 1e-6);
        double scaleY = (h - 2 * padding) / (maxY - minY + 1e-6);
        baseScale = Math.max(0.0001, Math.min(scaleX, scaleY));
    }

    private void enableInteractions() {
        setOnScroll(evt -> {
            double factor = (evt.getDeltaY() > 0) ? 1.1 : 0.9;
            zoom(factor, evt.getX(), evt.getY());
            evt.consume();
        });

        setOnMousePressed(evt -> {
            lastMouseX = evt.getX();
            lastMouseY = evt.getY();
        });

        setOnMouseDragged(evt -> {
            double dx = evt.getX() - lastMouseX;
            double dy = evt.getY() - lastMouseY;
            offsetX += dx;
            offsetY += dy;
            lastMouseX = evt.getX();
            lastMouseY = evt.getY();
            redraw();
        });
    }

    private void zoom(double factor, double pivotX, double pivotY) {
        double oldScale = userScale;
        double newScale = clamp(userScale * factor, 0.1, 10.0);
        factor = newScale / oldScale;

        double scale = baseScale * oldScale;
        double worldX = (pivotX - padding - offsetX) / scale;
        double worldY = (pivotY - padding - offsetY) / scale;

        userScale = newScale;

        double newScaleCombined = baseScale * userScale;
        offsetX = pivotX - padding - worldX * newScaleCombined;
        offsetY = pivotY - padding - worldY * newScaleCombined;
        redraw();
    }

    private double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

}
