import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        final List<Point2D> polyline;
        final double widthMeters;
        LaneShape(List<Point2D> p, double w) {
            this.polyline = p;
            this.widthMeters = w;
        }
    }

	private static class JunctionShape {
		final List<Point2D> polygon;
		JunctionShape(List<Point2D> p) {
			this.polygon = p;
		}
	}

    private final Canvas canvas = new Canvas();
    private final List<LaneShape> lanes = new ArrayList<>();
	private final List<JunctionShape> junctions = new ArrayList<>();
    private Map<String, Point2D> vehiclePositions;
	private Map<String, Color> vehicleColors;
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
		junctions.clear();
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
                // Skip internal helper junctions
                if (j.hasAttribute("type")) {
                    String t = j.getAttribute("type");
                    if ("internal".equalsIgnoreCase(t)) continue;
                }
                List<Point2D> poly = parseShape(j.getAttribute("shape"));
                if (poly.isEmpty()) continue;
                junctions.add(new JunctionShape(poly));
                allPoints.addAll(poly);
            }

            NodeList laneNodes = doc.getElementsByTagName("lane");
            for (int i = 0; i < laneNodes.getLength(); i++) {
                Element lane = (Element) laneNodes.item(i);
                if (!lane.hasAttribute("shape")) continue;
                String shape = lane.getAttribute("shape");
                double width = 3.2;
                if (lane.hasAttribute("width")) {
                    try {
                        width = Double.parseDouble(lane.getAttribute("width"));
                    } catch (NumberFormatException ignored) {}
                }
                List<Point2D> polyline = parseShape(shape);
                if (polyline.isEmpty()) continue;
				lanes.add(new LaneShape(polyline, width));
                allPoints.addAll(polyline);
                laneCount++;
            }
            if (!allPoints.isEmpty()) {
                minX = allPoints.stream().mapToDouble(Point2D::getX).min().orElse(0);
                maxX = allPoints.stream().mapToDouble(Point2D::getX).max().orElse(1);
                minY = allPoints.stream().mapToDouble(Point2D::getY).min().orElse(0);
                maxY = allPoints.stream().mapToDouble(Point2D::getY).max().orElse(1);
            } else {
                minX = maxX = minY = maxY = 0;
            }
            LOGGER.info(String.format(java.util.Locale.US,
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
        this.vehiclePositions = positions;
		this.vehicleColors = colors;
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
            for (int i = 1; i < lane.polyline.size(); i++) {
                Point2D p1 = transform(lane.polyline.get(i - 1), h, scale);
                Point2D p2 = transform(lane.polyline.get(i), h, scale);
                g.setLineWidth(outerW);
                g.setStroke(roadEdge);
                g.strokeLine(p1.getX(), p1.getY(), p2.getX(), p2.getY());

                g.setLineWidth(laneScreenWidth);
                g.setStroke(roadFill);
                g.strokeLine(p1.getX(), p1.getY(), p2.getX(), p2.getY());

                // Draw dashed lane boundary markings slightly offset from the
                // lane center so that vehicles (which follow the center
                // line) appear to drive between markings rather than on top
                // of them.
                if (laneScreenWidth >= 4.0) {
                    double dx = p2.getX() - p1.getX();
                    double dy = p2.getY() - p1.getY();
                    double len = Math.hypot(dx, dy);
                    if (len > 0.0001) {
                        double nx = -dy / len;
                        double ny = dx / len;
                        double offset = Math.max(2.0, laneScreenWidth / 2.5);

                        g.setLineWidth(1.2);
                        g.setStroke(laneLine);
                        g.setLineDashes(6, 8);

                        // Left and right boundary relative to driving
                        // direction.
                        for (int side = -1; side <= 1; side += 2) {
                            double ox = nx * offset * side;
                            double oy = ny * offset * side;
                            g.strokeLine(p1.getX() + ox, p1.getY() + oy,
                                         p2.getX() + ox, p2.getY() + oy);
                        }
                        g.setLineDashes(null);
                    }
                }
            }

            // simple direction arrow using first segment
            if (lane.polyline.size() >= 2) {
                Point2D a = transform(lane.polyline.get(0), h, scale);
                Point2D b = transform(lane.polyline.get(1), h, scale);
                double dx = b.getX() - a.getX();
                double dy = b.getY() - a.getY();
                double len = Math.hypot(dx, dy);
                if (len > 4) {
                    double nx = dx / len;
                    double ny = dy / len;
                    double midx = a.getX() + dx * 0.6;
                    double midy = a.getY() + dy * 0.6;
                    double arrowLen = Math.min(14, Math.max(7, laneScreenWidth * 1.2 + 5));
                    double tx = midx + nx * arrowLen;
                    double ty = midy + ny * arrowLen;
                    double px = -ny;
                    double py = nx;
                    double wing = arrowLen * 0.4;
                    g.setFill(Color.web("#606060"));
                    g.fillPolygon(
                        new double[]{tx, midx + px * wing, midx - px * wing},
                        new double[]{ty, midy + py * wing, midy - py * wing},
                        3);
                }
            }
        }

        // Draw vehicles
        if (vehiclePositions != null) {
            double radius = 3.0;
            for (java.util.Map.Entry<String, Point2D> e : vehiclePositions.entrySet()) {
                Color c = Color.RED;
                if (vehicleColors != null) {
                    Color mapped = vehicleColors.get(e.getKey());
                    if (mapped != null) c = mapped;
                }
                g.setFill(c);
                Point2D tp = transform(e.getValue(), h, scale);
                g.fillOval(tp.getX() - radius, tp.getY() - radius, radius * 2, radius * 2);
            }
        }
    }

    private Point2D transform(Point2D p, double height, double scale) {
        double x = padding + (p.getX() - minX) * scale + offsetX;
        double y = padding + (maxY - p.getY()) * scale + offsetY; // flip Y for screen coords
        return new Point2D(x, y);
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
