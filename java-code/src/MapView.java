import javafx.geometry.Point2D;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
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
import java.util.function.IntConsumer;
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
        String laneId;
        String edgeId;
        int laneIndex;
        List<Point2D> polyline;
        double widthMeters;
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
        String edgeId;
        int laneIndex;
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
        String id;
        boolean hasTrafficLight;
		List<Point2D> polygon;
		
        JunctionShape(String id, boolean trafficLight, List<Point2D> p) {
            this.id = id;
            this.hasTrafficLight = trafficLight;
			this.polygon = p;
		}
	}

    private static final class TextMarker {
        String text;
        Point2D world;
        
        TextMarker(String text, Point2D world) {
            this.text = text;
            this.world = world;
        }
    }

    /**
     * Represents a bus stop location for rendering on the map.
     * World position and direction are pre-cached to avoid recalculating every frame.
     */
    private static final class BusStopMarker {
        String id;
        String name;
        String laneId;
        double startPos;  // distance from lane start in meters
        double endPos;    // distance from lane start in meters
        double stopLength;
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

    // Background canvas: static network rendering (roads, junctions, labels, bus stops)
    private Canvas backgroundCanvas = new Canvas();
    // Overlay canvas: dynamic elements (vehicles, traffic signals)
    private Canvas canvas = new Canvas();
    private List<LaneShape> lanes = new ArrayList<>();
	private Map<String, LaneShape> lanesById = new HashMap<>();
	private List<JunctionShape> junctions = new ArrayList<>();
    private Map<String, Point2D> edgeLabelWorldPos = new HashMap<>();
    private List<TextMarker> trafficLightMarkers = new ArrayList<>();
    private Map<String, Point2D> vehiclePositions;
	private Map<String, Color> vehicleColors;
    private Map<String, String> vehicleLaneIds;
    // Vehicle angles in degrees (heading from North, clockwise)
    private Map<String, Double> vehicleAngles;
    // Vehicle types (e.g., "car", "bus", "motorcycle", "bike")
    private Map<String, String> vehicleTypes;

    // Per-vehicle render smoothing state (keeps headings stable at junctions)
    private Map<String, Point2D> lastVehicleWorldPos = new HashMap<>();
    private Map<String, Point2D> smoothedVehicleDir = new HashMap<>();
	private Map<String, Color> laneSignalColors;
    private List<BusStopMarker> busStops = new ArrayList<>();
    private double minX = 0, maxX = 1, minY = 0, maxY = 1;

    // Render scheduling
    private boolean backgroundDirty = true;
    private boolean overlayRedrawScheduled = false;

    // zoom / pan state
    private double baseScale = 1.0;
    private double userScale = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double padding = 20.0;
    private double lastMouseX;
    private double lastMouseY;

    public MapView() {
        // Background first, overlay on top.
        getChildren().addAll(backgroundCanvas, canvas);
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
        clampOffsets();
        backgroundDirty = true;
        redraw();
        return laneCount;
    }

    private static final class NetworkData {
        List<LaneShape> lanes = new ArrayList<>();
        Map<String, LaneShape> lanesById = new HashMap<>();
        List<JunctionShape> junctions = new ArrayList<>();
        Map<String, Point2D> edgeLabelWorldPos = new HashMap<>();
        List<TextMarker> trafficLightMarkers = new ArrayList<>();
        double minX = 0, maxX = 1, minY = 0, maxY = 1;
        int laneCount = 0;
    }

    /**
     * Loads the SUMO network on a background thread to avoid UI freezes.
     * Only the final apply + redraw happens on the JavaFX thread.
     */
    public void loadNetworkAsync(File netFile, IntConsumer onDone) {
        // If missing file, just clear and return quickly.
        if (netFile == null || !netFile.exists()) {
            Platform.runLater(() -> {
                lanes.clear();
                lanesById.clear();
                junctions.clear();
                edgeLabelWorldPos.clear();
                trafficLightMarkers.clear();
                minX = 0; maxX = 1; minY = 0; maxY = 1;
                backgroundDirty = true;
                redraw();
                if (onDone != null) onDone.accept(0);
            });
            return;
        }

        Thread t = new Thread(() -> {
            NetworkData data = parseNetworkFile(netFile);
            Platform.runLater(() -> {
                applyNetworkData(data);
                if (onDone != null) onDone.accept(data.laneCount);
            });
        }, "NetLoader");
        t.setDaemon(true);
        t.start();
    }

    private NetworkData parseNetworkFile(File netFile) {
        NetworkData out = new NetworkData();
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(netFile);
            doc.getDocumentElement().normalize();
            List<Point2D> allPoints = new ArrayList<>();

            NodeList juncNodes = doc.getElementsByTagName("junction");
            for (int i = 0; i < juncNodes.getLength(); i++) {
                Element j = (Element) juncNodes.item(i);
                if (!j.hasAttribute("shape")) continue;
                String jId = j.hasAttribute("id") ? j.getAttribute("id") : "";
                boolean isTl = false;
                if (j.hasAttribute("type")) {
                    String t = j.getAttribute("type");
                    isTl = (t != null) && (t.toLowerCase().contains("traffic_light"));
                    if ("internal".equalsIgnoreCase(t)) continue;
                }
                List<Point2D> poly = parseShape(j.getAttribute("shape"));
                if (poly.isEmpty()) continue;
                out.junctions.add(new JunctionShape(jId, isTl, poly));
                allPoints.addAll(poly);

                if (isTl && jId != null && !jId.isEmpty()) {
                    Point2D c = centroid(poly);
                    if (c != null) {
                        out.trafficLightMarkers.add(new TextMarker("TL " + jId, c));
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
                out.lanes.add(ls);
                if (laneId != null && !laneId.isEmpty()) {
                    out.lanesById.put(laneId, ls);
                }
                allPoints.addAll(polyline);
                out.laneCount++;
            }

            for (LaneShape lane : out.lanes) {
                if (lane == null || lane.edgeId == null || lane.edgeId.isEmpty()) continue;
                if (lane.edgeId.startsWith(":")) continue;
                if (out.edgeLabelWorldPos.containsKey(lane.edgeId)) continue;
                Point2D p = pointAlongPolyline(lane.polyline, 0.55);
                if (p != null) {
                    out.edgeLabelWorldPos.put(lane.edgeId, p);
                }
            }

            if (!allPoints.isEmpty()) {
                out.minX = allPoints.stream().mapToDouble(Point2D::getX).min().orElse(0);
                out.maxX = allPoints.stream().mapToDouble(Point2D::getX).max().orElse(1);
                out.minY = allPoints.stream().mapToDouble(Point2D::getY).min().orElse(0);
                out.maxY = allPoints.stream().mapToDouble(Point2D::getY).max().orElse(1);
            } else {
                out.minX = out.maxX = out.minY = out.maxY = 0;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load network (async)", e);
        }
        return out;
    }

    private void applyNetworkData(NetworkData data) {
        lanes.clear();
        lanesById.clear();
        junctions.clear();
        edgeLabelWorldPos.clear();
        trafficLightMarkers.clear();

        if (data != null) {
            lanes.addAll(data.lanes);
            lanesById.putAll(data.lanesById);
            junctions.addAll(data.junctions);
            edgeLabelWorldPos.putAll(data.edgeLabelWorldPos);
            trafficLightMarkers.addAll(data.trafficLightMarkers);
            minX = data.minX;
            maxX = data.maxX;
            minY = data.minY;
            maxY = data.maxY;
        }

        offsetX = 0;
        offsetY = 0;
        userScale = 1.0;
        computeBaseScale();
        clampOffsets();
        backgroundDirty = true;
        redraw();
    }

    public void updateVehicles(Map<String, Point2D> positions, Map<String, Color> colors) {
        updateVehicles(positions, colors, null, null, null);
    }

    public void updateVehicles(Map<String, Point2D> positions, Map<String, Color> colors, Map<String, String> laneIds) {
        updateVehicles(positions, colors, laneIds, null, null);
    }

    /**
     * Update vehicle positions, colors, lane IDs, angles, and types for rendering.
     * This overload supports realistic vehicle shapes with proper orientation.
     */
    public void updateVehicles(Map<String, Point2D> positions, Map<String, Color> colors, 
                               Map<String, String> laneIds, Map<String, Double> angles, 
                               Map<String, String> types) {
        this.vehiclePositions = positions;
        this.vehicleColors = colors;
        this.vehicleLaneIds = laneIds;
        this.vehicleAngles = angles;
        this.vehicleTypes = types;
        scheduleOverlayRedraw();
    }

    public void updateTrafficSignals(Map<String, Color> laneSignalColors) {
        this.laneSignalColors = laneSignalColors;
        scheduleOverlayRedraw();
    }

    /**
     * Update bus stop data for rendering on the map.
     * Pre-caches world positions and directions for fast rendering.
     * Each entry contains: id, name, laneId, startPos, endPos
     * @param busStopData List of bus stop information arrays [id, name, laneId, startPos, endPos]
     */
    /**
     * Updates the list of bus stops to be rendered on the map.
     * 
     * Bus stop position: ON THE LANE (where bus actually stops)
     * The "H" sign and waiting area will be drawn offset to the sidewalk side.
     * This way bus and bus stop are logically together.
     * 
     * @param busStopData List of bus stop data arrays [id, name, laneId, startPos, endPos]
     */
    public void updateBusStops(List<String[]> busStopData) {
        busStops.clear();
        if (busStopData != null) {
            for (String[] data : busStopData) {
                if (data != null && data.length >= 5) {
                    try {
                        String id = data[0];
                        String name = data[1];
                        String laneId = data[2];
                        double startPos = Double.parseDouble(data[3]);
                        double endPos = Double.parseDouble(data[4]);
                        BusStopMarker marker = new BusStopMarker(id, name, laneId, startPos, endPos);
                        
                        // Get lane geometry for positioning
                        LaneShape lane = lanesById.get(laneId);
                        if (lane != null && lane.polyline != null && lane.polyline.size() >= 2) {
                            marker.laneWidth = lane.widthMeters;
                            
                            // Calculate total lane length
                            double laneLength = 0.0;
                            for (int i = 1; i < lane.polyline.size(); i++) {
                                laneLength += lane.polyline.get(i).distance(lane.polyline.get(i - 1));
                            }
                            if (laneLength > 0.001) {
                                // Find midpoint of bus stop along lane
                                double midPos = (startPos + endPos) / 2.0;
                                double t = Math.max(0.0, Math.min(1.0, midPos / laneLength));
                                Point2D lanePos = pointAlongPolyline(lane.polyline, t);
                                if (lanePos != null) {
                                    // Store position ON THE LANE (where bus stops)
                                    // The drawing code will offset the sign to the sidewalk
                                    marker.worldPos = lanePos;
                                    marker.direction = laneTangentAt(lanePos, lane.polyline);
                                }
                            }
                        }
                        
                        // Only add markers with valid computed positions
                        if (marker.worldPos != null) {
                            busStops.add(marker);
                        }
                    } catch (NumberFormatException ignored) {
                        // Skip entries with invalid numeric data
                    }
                }
            }
        }
        LOGGER.info("Loaded " + busStops.size() + " bus stops for map rendering");
        backgroundDirty = true;
        redraw();
    }

    private void layoutCanvas() {
        backgroundCanvas.setWidth(getWidth());
        backgroundCanvas.setHeight(getHeight());
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        computeBaseScale();
        clampOffsets();
        backgroundDirty = true;
        redraw();
    }

    private void redraw() {
        redrawBackgroundIfNeeded();
        redrawOverlay();
    }

    private void redrawBackgroundIfNeeded() {
        if (!backgroundDirty) return;

        double w = backgroundCanvas.getWidth();
        double h = backgroundCanvas.getHeight();
        GraphicsContext g = backgroundCanvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, w, h);
        if (w <= 0 || h <= 0) return;

        double scale = baseScale * userScale;

        // No network loaded? Show hint.
        if (lanes.isEmpty()) {
            g.setFill(Color.web("#888888"));
            g.fillText("No network loaded. Check your .sumocfg and net path.", 20, 30);
            backgroundDirty = false;
            return;
        }

        // Green grass background after config is loaded (network exists)
        g.setFill(Color.web("#e8f5e9"));
        g.fillRect(0, 0, w, h);

        Color roadEdge = Color.web("#000000");
        Color roadFill = Color.web("#101010");
        Color laneLine = Color.web("#d0d0d0");

        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);

        // Junction polygons
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

        // Lanes/roads
        for (LaneShape lane : lanes) {
            double laneScreenWidth = Math.max(1.5, lane.widthMeters * scale);
            double outerW = laneScreenWidth + 1.2;

            strokePolyline(g, lane.polyline, h, scale, outerW, roadEdge, null);
            strokePolyline(g, lane.polyline, h, scale, laneScreenWidth, roadFill, null);

            if (laneScreenWidth >= 6.0) {
                strokePolyline(g, lane.polyline, h, scale, 1.1, laneLine, new double[]{10, 10});
            }

            if (laneScreenWidth >= 5.0) {
                drawLaneEdges(g, lane.polyline, h, scale, laneScreenWidth, laneLine);
            }
        }

        drawLabels(g, h, scale);
        drawRoadDirectionArrows(g, h, scale, Color.web("#bdbdbd"));
        drawBusStops(g, h, scale);

        backgroundDirty = false;
    }

    private void redrawOverlay() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext g = canvas.getGraphicsContext2D();
        // Overlay is transparent; clear only this canvas.
        g.clearRect(0, 0, w, h);

        double scale = baseScale * userScale;
        if (lanes.isEmpty()) return;

        // Vehicles
        if (vehiclePositions != null) {
            for (Entry<String, Point2D> e : vehiclePositions.entrySet()) {
                String vehicleId = e.getKey();
                Color c = Color.RED;
                if (vehicleColors != null) {
                    Color mapped = vehicleColors.get(vehicleId);
                    if (mapped != null) c = mapped;
                }

                Point2D worldPos = e.getValue();
                Point2D drawWorld = worldPos;

                LaneShape laneForHeading = null;
                if (vehicleLaneIds != null) {
                    String laneId = vehicleLaneIds.get(vehicleId);
                    LaneShape lane = (laneId != null) ? lanesById.get(laneId) : null;
                    laneForHeading = lane;
                    if (lane != null && lane.polyline != null && lane.polyline.size() >= 2 && worldPos != null) {
                        double offsetMeters = Math.max(0.35, lane.widthMeters * 0.22);
                        double sign = ((laneId.hashCode() & 1) == 0) ? 1.0 : -1.0;
                        drawWorld = offsetAlongLaneNormal(worldPos, lane.polyline, offsetMeters * sign);
                    }
                }

                Point2D tp = transform(drawWorld, h, scale);

                double angleDegrees = 0.0;
                if (vehicleAngles != null && vehicleAngles.containsKey(vehicleId)) {
                    angleDegrees = vehicleAngles.get(vehicleId);
                }

                String vehicleType = "car";
                if (vehicleTypes != null && vehicleTypes.containsKey(vehicleId)) {
                    vehicleType = vehicleTypes.get(vehicleId).toLowerCase();
                }

                drawVehicleShape(g, vehicleId, worldPos, laneForHeading, tp.getX(), tp.getY(), angleDegrees, vehicleType, c, userScale);
            }
        }

        // Traffic light stop lines
        drawTrafficLightStopLines(g, h, scale);
    }

    private void scheduleOverlayRedraw() {
        if (overlayRedrawScheduled) return;
        overlayRedrawScheduled = true;
        Platform.runLater(() -> {
            overlayRedrawScheduled = false;
            redrawOverlay();
        });
    }

    private void drawTrafficLightStopLines(GraphicsContext g, double height, double scale) {
        // This function draws stop lines at the end of each signaled lane.
        // Sizes are clamped in screen pixels so they don't become huge when zooming.
        if (laneSignalColors == null || laneSignalColors.isEmpty()) return;

        for (Entry<String, Color> e : laneSignalColors.entrySet()) {
            LaneShape lane = lanesById.get(e.getKey());
            if (lane == null || lane.polyline == null || lane.polyline.size() < 2) continue;
            int n = lane.polyline.size();

            Point2D worldPrev = lane.polyline.get(n - 2);
            Point2D worldEnd = lane.polyline.get(n - 1);
            Point2D pPrev = transform(worldPrev, height, scale);
            Point2D pEnd = transform(worldEnd, height, scale);

            double dx = pEnd.getX() - pPrev.getX();
            double dy = pEnd.getY() - pPrev.getY();
            double len = Math.hypot(dx, dy);
            if (len < 0.001) continue;
            double ux = dx / len;
            double uy = dy / len;
            // Normal vector for stop line across lane direction
            double nx = -uy;
            double ny = ux;

            // Keep stop-line thickness mostly constant in screen pixels.
            // Only increase *length* with zoom so the stop line is more visible when zoomed in.
            double lanePx = clamp(Math.max(6.0, lane.widthMeters * baseScale), 6.0, 22.0);

            // Make the stop-line longer when zoomed in, without increasing thickness.
            // Use an "ease-in" curve so mid-zoom doesn't produce overly long lines,
            // while max zoom still gets a clearly longer stop line.
            // NOTE: userScale ranges up to ~25 in this project.
            // We want the stop line to be slightly longer at mid zoom (not tiny),
            // and noticeably longer near max zoom, without becoming road-wide too early.
            double z = clamp(userScale, 1.0, 25.0);

            // Stage 1: small boost up to around userScale ~8.
            double t1 = clamp((z - 1.0) / (8.0 - 1.0), 0.0, 1.0);
            t1 = t1 * t1 * (3.0 - 2.0 * t1); // smoothstep

            // Stage 2: stronger ramp from ~8 up to max zoom.
            double t2 = clamp((z - 8.0) / (25.0 - 8.0), 0.0, 1.0);
            t2 = t2 * t2;

            double baseLen = clamp(lanePx * 1.2, 14.0, 44.0);
            double midExtraPx = 18.0;
            double maxExtra = clamp(lanePx * 3.2, 35.0, 90.0);
            double lineLen = clamp(baseLen + midExtraPx * t1 + maxExtra * t2, 14.0, 120.0);

            // Place the stop line a small, consistent distance before the lane end in *world meters*.
            // If this is in pixels, the world-distance changes with zoom and cars may appear to cross it.
            double backShiftMeters = clamp(lane.widthMeters * 0.20, 0.35, 0.85);
            double backShiftPx = backShiftMeters * scale;
            double cx = pEnd.getX() - ux * backShiftPx;
            double cy = pEnd.getY() - uy * backShiftPx;

            double x1 = cx - nx * (lineLen / 2.0);
            double y1 = cy - ny * (lineLen / 2.0);
            double x2 = cx + nx * (lineLen / 2.0);
            double y2 = cy + ny * (lineLen / 2.0);

            Color c = (e.getValue() != null) ? e.getValue() : Color.RED;
            g.setLineDashes(null);

            // Outline for readability on dark roads
            g.setStroke(Color.WHITE);
            g.setLineWidth(clamp(lanePx * 0.55, 3.2, 14.0));
            g.strokeLine(x1, y1, x2, y2);

            g.setStroke(c);
            g.setLineWidth(clamp(lanePx * 0.35, 2.0, 11.0));
            g.strokeLine(x1, y1, x2, y2);
        }
    }

    /**
     * Draw bus stops on the map as distinctive bus stop markers beside the road.
     * Renders a bus shelter/platform on the sidewalk with a bus stop sign.
     */
    /**
     * Draws bus stops on the map.
     * Simple design: "H" sign on sidewalk + waiting area rectangle.
     * Position is ON the lane (where bus stops), sign is offset to sidewalk.
     */
    private void drawBusStops(GraphicsContext g, double height, double scale) {
        if (busStops == null || busStops.isEmpty()) return;

        // Colors for bus stop
        Color signYellow = Color.web("#FFD600");   // Yellow "H" sign
        Color signBorder = Color.web("#333333");   // Dark border
        Color waitingArea = Color.web("#B0BEC5"); // Light gray waiting area
        
        for (BusStopMarker stop : busStops) {
            if (stop.worldPos == null) continue;

            // Transform bus stop world position (on the lane) to screen
            Point2D tp = transform(stop.worldPos, height, scale);
            double cx = tp.getX();
            double cy = tp.getY();

            // Get lane direction for orientation
            Point2D dirWorld = (stop.direction != null) ? stop.direction : new Point2D(1.0, 0.0);
            double dirLen = Math.hypot(dirWorld.getX(), dirWorld.getY());
            double fx, fy; // forward direction (along lane)
            if (dirLen < 1e-9) {
                fx = 1.0; fy = 0.0;
            } else {
                fx = dirWorld.getX() / dirLen;
                fy = -dirWorld.getY() / dirLen; // flip Y for screen
            }
            // rx, ry = right perpendicular
            // NEGATIVE to go to OUTER sidewalk (not median)
            double rx = -fy;
            double ry = fx;

            // Size based on zoom
            double baseSize = clamp(8.0 + userScale * 2.0, 10.0, 30.0);
            double laneWidthPx = stop.laneWidth * scale;
            
            // --- Draw waiting area (long and thick rectangle at road edge) ---
            double areaLen = clamp(stop.stopLength * scale * 1.0, baseSize * 8.0, baseSize * 16.0);
            double areaWid = baseSize * 0.8; // Thicker for visibility
            
            // Offset just outside the road edge (not overlapping road)
            double sidewalkOffset = laneWidthPx * 0.55 + areaWid * 0.5;
            double signX = cx + rx * sidewalkOffset;
            double signY = cy + ry * sidewalkOffset;
            g.setFill(waitingArea);
            g.setStroke(Color.GRAY);
            g.setLineWidth(1.0);
            g.setLineDashes(null);
            // Rectangle aligned with lane direction
            double[] areaX = new double[4];
            double[] areaY = new double[4];
            areaX[0] = signX + fx * areaLen/2 - rx * areaWid/2;
            areaY[0] = signY + fy * areaLen/2 - ry * areaWid/2;
            areaX[1] = signX + fx * areaLen/2 + rx * areaWid/2;
            areaY[1] = signY + fy * areaLen/2 + ry * areaWid/2;
            areaX[2] = signX - fx * areaLen/2 + rx * areaWid/2;
            areaY[2] = signY - fy * areaLen/2 + ry * areaWid/2;
            areaX[3] = signX - fx * areaLen/2 - rx * areaWid/2;
            areaY[3] = signY - fy * areaLen/2 - ry * areaWid/2;
            g.fillPolygon(areaX, areaY, 4);
            g.strokePolygon(areaX, areaY, 4);

            // --- Draw "H" sign at CENTER of waiting area ---
            double hSize = clamp(baseSize * 0.6, 8.0, 20.0);
            double hX = signX;  // Center of rectangle
            double hY = signY;  // Center of rectangle
            
            // Yellow circle with "H"
            g.setFill(signYellow);
            g.fillOval(hX - hSize/2, hY - hSize/2, hSize, hSize);
            g.setStroke(signBorder);
            g.setLineWidth(1.5);
            g.strokeOval(hX - hSize/2, hY - hSize/2, hSize, hSize);
            
            // Draw "H" letter
            g.setFill(signBorder);
            double fontSize = hSize * 0.6;
            g.setFont(Font.font("Arial", FontWeight.BOLD, fontSize));
            g.fillText("H", hX - fontSize * 0.3, hY + fontSize * 0.35);

            // --- Draw label if zoomed in ---
            if (userScale >= 3.0) {
                String label = (stop.name != null && !stop.name.isEmpty()) ? stop.name : stop.id;
                if (label != null && !label.isEmpty()) {
                    drawTag(g, hX + hSize/2 + 4, hY - hSize/2 - 4, label, signYellow, signBorder);
                }
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

    /**
     * Draws a realistic car shape for all vehicle types.
     * Uses vector-based drawing for smooth rotation at any angle.
     */
    private void drawVehicleShape(GraphicsContext g, String vehicleId, Point2D worldPos, LaneShape lane,
                                  double x, double y, double angleDegrees,
                                  String vehicleType, Color color, double scale) {
        // This function computes a stable heading direction for the car:
        // 1) Prefer lane tangent (smooth on curves)
        // 2) Fallback to movement delta (smooth at merges)
        // 3) Fallback to SUMO angle
        Point2D dirWorld = null;

        // (1) Lane tangent in world coordinates (x right, y up)
        // causes cars to not turn when changing lanes in a straight edge
//        if (lane != null && lane.polyline != null && lane.polyline.size() >= 2 && worldPos != null) {
////        	System.out.println("using lane tangent");
//            dirWorld = laneTangentAt(worldPos, lane.polyline);
//        }

        // (2) Movement delta (world coordinates)
        if (dirWorld == null && worldPos != null && vehicleId != null) {
//        	System.out.println("using movement delta");
            Point2D prev = lastVehicleWorldPos.get(vehicleId);
            if (prev != null) {
                double dx = worldPos.getX() - prev.getX();
                double dy = worldPos.getY() - prev.getY();
                double len = Math.hypot(dx, dy);
                if (len > 1e-6) {
                    dirWorld = new Point2D(dx / len, dy / len);
                }
            }
        }
        
        // (3) SUMO angle fallback (world coordinates)
        if (dirWorld == null) {
//        	System.out.println("using SUMO fallback");
            double angleRad = Math.toRadians(angleDegrees);
            // SUMO 0° = North (+Y), 90° = East (+X)
            dirWorld = new Point2D(Math.sin(angleRad), Math.cos(angleRad));
        }

        // Convert world direction to screen direction (screen Y is flipped)
        Point2D dirScreen = new Point2D(dirWorld.getX(), -dirWorld.getY());
        dirScreen = normalize(dirScreen);

        // Smooth direction to avoid jitter / sudden flips at junctions.
        // Small alpha => smoother but more lag; medium value is a good compromise.
        final double alpha = 0.1;
        if (vehicleId != null) {
            Point2D prevDir = smoothedVehicleDir.get(vehicleId);
            if (prevDir != null) {
                Point2D blended = new Point2D(
                        prevDir.getX() * (1.0 - alpha) + dirScreen.getX() * alpha,
                        prevDir.getY() * (1.0 - alpha) + dirScreen.getY() * alpha
                );
                dirScreen = normalize(blended);
            }
            smoothedVehicleDir.put(vehicleId, dirScreen);
            if (worldPos != null) {
                lastVehicleWorldPos.put(vehicleId, worldPos);
            }
        }

        double fx = dirScreen.getX();
        double fy = dirScreen.getY();
        // Right vector perpendicular to forward (screen coordinates)
        double rx = fy;
        double ry = -fx;

        // Check if this is a bus vehicle (bus type in SUMO typically contains "bus")
        boolean isBus = (vehicleType != null) && vehicleType.toLowerCase().contains("bus");

        // SUMO's reported position is typically near the *front* of the vehicle; we draw centered.
        // Shift the vehicle back by half its screen length so vehicles don't visually cross stop lines.
        if (isBus) {
            // Draw bus - longer than a car
            double halfLenPx = busLengthPxForZoom(scale) * 0.5;
            drawBusShapeWithVectors(g, x - fx * halfLenPx, y - fy * halfLenPx, fx, fy, rx, ry, color, scale);
        } else {
            // Draw regular car
            double halfLenPx = carLengthPxForZoom(scale) * 0.5;
            drawCarShapeWithVectors(g, x - fx * halfLenPx, y - fy * halfLenPx, fx, fy, rx, ry, color, scale);
        }
    }

    private double carSizeMulForZoom(double mapScale) {
        // This function controls how car size changes with zoom.
        // Small when zoomed out, grows smoothly when zooming in.
        double zoom = clamp(mapScale, 1.0, 25.0);
        double t = clamp((zoom - 1.0) / (10.0 - 1.0), 0.0, 1.0);
        t = t * t * (3.0 - 2.0 * t); // smoothstep
        return clamp(0.75 + 1.35 * t, 0.7, 2.2);
    }

    private double carLengthPxForZoom(double mapScale) {
        // This function returns the car length in screen pixels.
        return Math.max(8.5, 12.0 * carSizeMulForZoom(mapScale));
    }

    /**
     * Returns the bus length in screen pixels for a given zoom level.
     * Buses are approximately 1.8x longer than cars (reduced from 2.5x for better visibility).
     */
    private double busLengthPxForZoom(double mapScale) {
        // Reduced bus size: was 28.0, now 18.0 base multiplier
        return Math.max(12.0, 18.0 * carSizeMulForZoom(mapScale));
    }

    /**
     * Returns the bus width in screen pixels for a given zoom level.
     * Buses are slightly wider than cars but proportional.
     */
    private double busWidthPxForZoom(double mapScale) {
        double length = busLengthPxForZoom(mapScale);
        return length * 0.32; // Slightly wider ratio for better proportions
    }
    
    // set x and y components to between -1 and 1
    private static Point2D normalize(Point2D v) {
        if (v == null) return new Point2D(1.0, 0.0);
        double len = Math.hypot(v.getX(), v.getY());
        if (len < 1e-9) return new Point2D(1.0, 0.0);
        return new Point2D(v.getX() / len, v.getY() / len);
    }

    private static Point2D laneTangentAt(Point2D worldPos, List<Point2D> polyline) {
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

    /**
     * Draws a car shape using direction vectors for smooth rotation.
     * @param fx, fy - forward direction vector (normalized)
     * @param rx, ry - right direction vector (perpendicular to forward)
     */
    private void drawCarShapeWithVectors(GraphicsContext g, double cx, double cy,
                                          double fx, double fy, double rx, double ry,
                                          Color color, double mapScale) {
        // Car dimensions in screen pixels.
        // We intentionally scale with the user's zoom (not world scale) so cars stay readable.
        double length = carLengthPxForZoom(mapScale);
        double width = length * 0.45;
        double halfLen = length / 2.0;
        double halfWid = width / 2.0;
        
        // Calculate the 4 corners of the car body
        // Front-right, Front-left, Back-left, Back-right
        double[] xPoints = new double[4];
        double[] yPoints = new double[4];
        
        // Front-right corner
        xPoints[0] = cx + fx * halfLen + rx * halfWid;
        yPoints[0] = cy + fy * halfLen + ry * halfWid;
        // Front-left corner
        xPoints[1] = cx + fx * halfLen - rx * halfWid;
        yPoints[1] = cy + fy * halfLen - ry * halfWid;
        // Back-left corner
        xPoints[2] = cx - fx * halfLen - rx * halfWid;
        yPoints[2] = cy - fy * halfLen - ry * halfWid;
        // Back-right corner
        xPoints[3] = cx - fx * halfLen + rx * halfWid;
        yPoints[3] = cy - fy * halfLen + ry * halfWid;
        
        // Draw car body outline for visibility
        g.setStroke(Color.WHITE);
        g.setLineWidth(1.0);
        g.strokePolygon(xPoints, yPoints, 4);
        
        // Draw car body fill
        g.setFill(color);
        g.fillPolygon(xPoints, yPoints, 4);
        
        // Draw windshield (front window) - darker shade
        Color windowColor = color.darker().darker();
        double windowLen = length * 0.2;
        double windowWid = width * 0.6;
        double windowHalfWid = windowWid / 2.0;
        // Window position: slightly behind the front
        double windowOffset = halfLen - windowLen * 0.8;
        
        double[] wxPoints = new double[4];
        double[] wyPoints = new double[4];
        wxPoints[0] = cx + fx * windowOffset + rx * windowHalfWid;
        wyPoints[0] = cy + fy * windowOffset + ry * windowHalfWid;
        wxPoints[1] = cx + fx * windowOffset - rx * windowHalfWid;
        wyPoints[1] = cy + fy * windowOffset - ry * windowHalfWid;
        wxPoints[2] = cx + fx * (windowOffset - windowLen) - rx * windowHalfWid;
        wyPoints[2] = cy + fy * (windowOffset - windowLen) - ry * windowHalfWid;
        wxPoints[3] = cx + fx * (windowOffset - windowLen) + rx * windowHalfWid;
        wyPoints[3] = cy + fy * (windowOffset - windowLen) + ry * windowHalfWid;
        
        g.setFill(windowColor);
        g.fillPolygon(wxPoints, wyPoints, 4);
        
        // Draw headlights (small circles at front corners)
        g.setFill(Color.YELLOW);
        double lightSize = width * 0.22;
        double lightOffset = halfLen - lightSize * 0.5;
        double lightSide = halfWid - lightSize * 0.3;
        
        // Right headlight
        double lx1 = cx + fx * lightOffset + rx * lightSide;
        double ly1 = cy + fy * lightOffset + ry * lightSide;
        g.fillOval(lx1 - lightSize / 2, ly1 - lightSize / 2, lightSize, lightSize);
        
        // Left headlight
        double lx2 = cx + fx * lightOffset - rx * lightSide;
        double ly2 = cy + fy * lightOffset - ry * lightSide;
        g.fillOval(lx2 - lightSize / 2, ly2 - lightSize / 2, lightSize, lightSize);
    }

    /**
     * Draws a bus shape using direction vectors for smooth rotation.
     * Buses are longer than cars with multiple windows and a distinct appearance.
     * @param fx, fy - forward direction vector (normalized)
     * @param rx, ry - right direction vector (perpendicular to forward)
     */
    private void drawBusShapeWithVectors(GraphicsContext g, double cx, double cy,
                                          double fx, double fy, double rx, double ry,
                                          Color color, double mapScale) {
        // Bus dimensions in screen pixels - buses are longer than cars
        double length = busLengthPxForZoom(mapScale);
        double width = busWidthPxForZoom(mapScale);
        double halfLen = length / 2.0;
        double halfWid = width / 2.0;
        
        // Calculate the 4 corners of the bus body
        double[] xPoints = new double[4];
        double[] yPoints = new double[4];
        
        // Front-right corner
        xPoints[0] = cx + fx * halfLen + rx * halfWid;
        yPoints[0] = cy + fy * halfLen + ry * halfWid;
        // Front-left corner
        xPoints[1] = cx + fx * halfLen - rx * halfWid;
        yPoints[1] = cy + fy * halfLen - ry * halfWid;
        // Back-left corner
        xPoints[2] = cx - fx * halfLen - rx * halfWid;
        yPoints[2] = cy - fy * halfLen - ry * halfWid;
        // Back-right corner
        xPoints[3] = cx - fx * halfLen + rx * halfWid;
        yPoints[3] = cy - fy * halfLen + ry * halfWid;
        
        // Draw bus body outline for visibility
        g.setStroke(Color.WHITE);
        g.setLineWidth(1.2);
        g.setLineDashes(null);
        g.strokePolygon(xPoints, yPoints, 4);
        
        // Draw bus body fill
        g.setFill(color);
        g.fillPolygon(xPoints, yPoints, 4);
        
        // Draw multiple windows along the bus (characteristic bus look)
        Color windowColor = color.darker().darker();
        g.setFill(windowColor);
        
        double windowLen = length * 0.08;
        double windowWid = width * 0.55;
        double windowHalfWid = windowWid / 2.0;
        
        // Draw 4 windows evenly spaced along the bus
        int numWindows = 4;
        double windowSpacing = length * 0.18;
        double firstWindowOffset = halfLen - windowLen * 1.2;
        
        for (int w = 0; w < numWindows; w++) {
            double windowOffset = firstWindowOffset - w * windowSpacing;
            
            double[] wxPoints = new double[4];
            double[] wyPoints = new double[4];
            wxPoints[0] = cx + fx * windowOffset + rx * windowHalfWid;
            wyPoints[0] = cy + fy * windowOffset + ry * windowHalfWid;
            wxPoints[1] = cx + fx * windowOffset - rx * windowHalfWid;
            wyPoints[1] = cy + fy * windowOffset - ry * windowHalfWid;
            wxPoints[2] = cx + fx * (windowOffset - windowLen) - rx * windowHalfWid;
            wyPoints[2] = cy + fy * (windowOffset - windowLen) - ry * windowHalfWid;
            wxPoints[3] = cx + fx * (windowOffset - windowLen) + rx * windowHalfWid;
            wyPoints[3] = cy + fy * (windowOffset - windowLen) + ry * windowHalfWid;
            
            g.fillPolygon(wxPoints, wyPoints, 4);
        }
        
        // Draw headlights (small circles at front corners)
        g.setFill(Color.YELLOW);
        double lightSize = width * 0.25;
        double lightOffset = halfLen - lightSize * 0.4;
        double lightSide = halfWid - lightSize * 0.2;
        
        // Right headlight
        double lx1 = cx + fx * lightOffset + rx * lightSide;
        double ly1 = cy + fy * lightOffset + ry * lightSide;
        g.fillOval(lx1 - lightSize / 2, ly1 - lightSize / 2, lightSize, lightSize);
        
        // Left headlight
        double lx2 = cx + fx * lightOffset - rx * lightSide;
        double ly2 = cy + fy * lightOffset - ry * lightSide;
        g.fillOval(lx2 - lightSize / 2, ly2 - lightSize / 2, lightSize, lightSize);
        
        // Draw tail lights (red at back)
        g.setFill(Color.RED);
        double tailLightOffset = -halfLen + lightSize * 0.4;
        
        // Right tail light
        double tx1 = cx + fx * tailLightOffset + rx * lightSide;
        double ty1 = cy + fy * tailLightOffset + ry * lightSide;
        g.fillOval(tx1 - lightSize / 2, ty1 - lightSize / 2, lightSize, lightSize);
        
        // Left tail light
        double tx2 = cx + fx * tailLightOffset - rx * lightSide;
        double ty2 = cy + fy * tailLightOffset - ry * lightSide;
        g.fillOval(tx2 - lightSize / 2, ty2 - lightSize / 2, lightSize, lightSize);
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
            clampOffsets();
            lastMouseX = evt.getX();
            lastMouseY = evt.getY();
            backgroundDirty = true;
            redraw();
        });
    }

    private void zoom(double factor, double pivotX, double pivotY) {
        double oldScale = userScale;
        double newScale = clamp(userScale * factor, 1.0, 25.0);
        factor = newScale / oldScale;

        double scale = baseScale * oldScale;
        double worldX = (pivotX - padding - offsetX) / scale;
        double worldY = (pivotY - padding - offsetY) / scale;

        userScale = newScale;

        double newScaleCombined = baseScale * userScale;
        offsetX = pivotX - padding - worldX * newScaleCombined;
        offsetY = pivotY - padding - worldY * newScaleCombined;
        clampOffsets();
        backgroundDirty = true;
        redraw();
    }

    private void clampOffsets() {
        // This function limits panning so the map stays within a reasonable "sandbox".
        // It prevents dragging so far that the network becomes hard to find again.
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        if (lanes.isEmpty()) return;

        double scale = baseScale * userScale;
        double contentW = Math.max(0.0, (maxX - minX) * scale);
        double contentH = Math.max(0.0, (maxY - minY) * scale);

        // Allow some overscroll margin so the user can inspect edges without losing the map.
        // Make the margin scale with zoom (in world meters) so it feels consistent.
        // When zoomed out, tighten the sandbox aggressively to avoid getting "lost".
        double marginMeters = 35.0;
        double z = clamp(userScale, 1.0, 25.0);
        double zoomOutT = clamp((z - 1.0) / (3.0 - 1.0), 0.0, 1.0);
        double minMarginWhenZoomedOut = Math.min(w, h) * 0.35;
        double minMargin = (1.0 - zoomOutT) * minMarginWhenZoomedOut + zoomOutT * 80.0;
        double margin = clamp(marginMeters * scale, minMargin, Math.min(360.0, Math.min(w, h) * 0.30));

        // Screen bounds of the network: [padding + offsetX, padding + offsetX + contentW]
        double minOffX = margin - padding - contentW;
        double maxOffX = (w - margin) - padding;
        if (minOffX > maxOffX) {
            // If the content is smaller than the viewport (at this zoom), keep it centered.
            offsetX = (w - contentW) * 0.5 - padding;
        } else {
            offsetX = clamp(offsetX, minOffX, maxOffX);
        }

        // Y uses the same screen-space logic because transform() flips world Y inside the mapping.
        double minOffY = margin - padding - contentH;
        double maxOffY = (h - margin) - padding;
        if (minOffY > maxOffY) {
            offsetY = (h - contentH) * 0.5 - padding;
        } else {
            offsetY = clamp(offsetY, minOffY, maxOffY);
        }
    }

    private double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

}
