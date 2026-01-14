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

    private static final class EdgeInfo {
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

    private static class LaneShape {
        String laneId;
        String edgeId;
        int laneIndex;
        List<Point2D> polyline;
        double widthMeters;
        String allow;
        String disallow;
        boolean bikeOnly;
        boolean pedestrianOnly;
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
    }

    private static boolean isBikeOnlyLane(String laneId, String allowAttr) {
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

    private static boolean isPedestrianOnlyLane(String laneId, String allowAttr) {
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

    private static boolean isMotorLane(LaneShape lane) {
        if (lane == null) return false;
        return !lane.pedestrianOnly && !lane.bikeOnly;
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
    private long lastOverlayRedrawNs = 0L;
    private double headingSmoothingAlpha = 0.22;
    private Map<String, Color> laneSignalColors;
    private List<BusStopMarker> busStops = new ArrayList<>();
    private double minX = 0, maxX = 1, minY = 0, maxY = 1;

    private final Map<String, EdgeInfo> edgesById = new HashMap<>();

    // Internal connector lanes (":...") that belong to bicycle movements.
    // Populated from SUMO <connection via="..."> so we only draw the relevant dashed guides
    // and avoid cluttering junctions on maps where bicycles are allowed on general lanes.
    private final java.util.HashSet<String> bicycleConnectorLaneIds = new java.util.HashSet<>();

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
        edgesById.clear();
        bicycleConnectorLaneIds.clear();
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

            // Parse edges (from/to) so we can reliably detect opposite-direction pairs.
            NodeList edgeNodes = doc.getElementsByTagName("edge");
            for (int i = 0; i < edgeNodes.getLength(); i++) {
                Element e = (Element) edgeNodes.item(i);
                if (!e.hasAttribute("id")) continue;
                String edgeId = e.getAttribute("id");
                if (edgeId == null || edgeId.isEmpty()) continue;
                String function = e.hasAttribute("function") ? e.getAttribute("function") : "";
                if (edgeId.startsWith(":") || "internal".equalsIgnoreCase(function)) continue;
                String from = e.hasAttribute("from") ? e.getAttribute("from") : "";
                String to = e.hasAttribute("to") ? e.getAttribute("to") : "";
                String name = e.hasAttribute("name") ? e.getAttribute("name") : "";
                edgesById.put(edgeId, new EdgeInfo(edgeId, from, to, name, function));
            }

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
                String allow = lane.hasAttribute("allow") ? lane.getAttribute("allow") : "";
                String disallow = lane.hasAttribute("disallow") ? lane.getAttribute("disallow") : "";
                double width = 3.2;
                if (lane.hasAttribute("width")) {
                    try {
                        width = Double.parseDouble(lane.getAttribute("width"));
                    } catch (NumberFormatException ignored) {}
                }
                List<Point2D> polyline = parseShape(shape);
                if (polyline.isEmpty()) continue;
                LaneShape ls = new LaneShape(laneId, polyline, width, allow, disallow);
                lanes.add(ls);
                if (laneId != null && !laneId.isEmpty()) {
                    lanesById.put(laneId, ls);
                }
                allPoints.addAll(polyline);
                laneCount++;
            }

            // Parse connections to identify internal connector lanes used by bicycle movements.
            // This is the most robust way to decide which ":..." lanes should be drawn as dashed
            // bicycle guides (matches SUMO behavior without map-specific rules).
            NodeList connNodes = doc.getElementsByTagName("connection");
            for (int i = 0; i < connNodes.getLength(); i++) {
                Element c = (Element) connNodes.item(i);
                if (!c.hasAttribute("via")) continue;
                String via = c.getAttribute("via");
                if (via == null || via.isEmpty()) continue;

                String fromEdge = c.hasAttribute("from") ? c.getAttribute("from") : "";
                String toEdge = c.hasAttribute("to") ? c.getAttribute("to") : "";
                String fromLane = c.hasAttribute("fromLane") ? c.getAttribute("fromLane") : "";
                String toLane = c.hasAttribute("toLane") ? c.getAttribute("toLane") : "";

                boolean fromBike = false;
                boolean toBike = false;

                if (!fromEdge.isEmpty() && !fromLane.isEmpty()) {
                    LaneShape fl = lanesById.get(fromEdge + "_" + fromLane);
                    fromBike = (fl != null) && fl.bikeOnly;
                }
                if (!toEdge.isEmpty() && !toLane.isEmpty()) {
                    LaneShape tl = lanesById.get(toEdge + "_" + toLane);
                    toBike = (tl != null) && tl.bikeOnly;
                }

                // If lane indices are missing/unusual, fall back to checking whether the edge has ANY bike-only lane.
                if (!fromBike && !fromEdge.isEmpty() && fromLane.isEmpty()) {
                    for (LaneShape l : lanes) {
                        if (l == null) continue;
                        if (!fromEdge.equals(l.edgeId)) continue;
                        if (l.bikeOnly) { fromBike = true; break; }
                    }
                }
                if (!toBike && !toEdge.isEmpty() && toLane.isEmpty()) {
                    for (LaneShape l : lanes) {
                        if (l == null) continue;
                        if (!toEdge.equals(l.edgeId)) continue;
                        if (l.bikeOnly) { toBike = true; break; }
                    }
                }

                if (fromBike || toBike) {
                    bicycleConnectorLaneIds.add(via);
                }
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
        Map<String, EdgeInfo> edgesById = new HashMap<>();
        List<JunctionShape> junctions = new ArrayList<>();
        Map<String, Point2D> edgeLabelWorldPos = new HashMap<>();
        List<TextMarker> trafficLightMarkers = new ArrayList<>();
        java.util.HashSet<String> bicycleConnectorLaneIds = new java.util.HashSet<>();
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

            NodeList edgeNodes = doc.getElementsByTagName("edge");
            for (int i = 0; i < edgeNodes.getLength(); i++) {
                Element e = (Element) edgeNodes.item(i);
                if (!e.hasAttribute("id")) continue;
                String edgeId = e.getAttribute("id");
                if (edgeId == null || edgeId.isEmpty()) continue;
                String function = e.hasAttribute("function") ? e.getAttribute("function") : "";
                if (edgeId.startsWith(":") || "internal".equalsIgnoreCase(function)) continue;
                String from = e.hasAttribute("from") ? e.getAttribute("from") : "";
                String to = e.hasAttribute("to") ? e.getAttribute("to") : "";
                String name = e.hasAttribute("name") ? e.getAttribute("name") : "";
                out.edgesById.put(edgeId, new EdgeInfo(edgeId, from, to, name, function));
            }

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
                String allow = lane.hasAttribute("allow") ? lane.getAttribute("allow") : "";
                String disallow = lane.hasAttribute("disallow") ? lane.getAttribute("disallow") : "";
                double width = 3.2;
                if (lane.hasAttribute("width")) {
                    try {
                        width = Double.parseDouble(lane.getAttribute("width"));
                    } catch (NumberFormatException ignored) {}
                }
                List<Point2D> polyline = parseShape(shape);
                if (polyline.isEmpty()) continue;
                LaneShape ls = new LaneShape(laneId, polyline, width, allow, disallow);
                out.lanes.add(ls);
                if (laneId != null && !laneId.isEmpty()) {
                    out.lanesById.put(laneId, ls);
                }
                allPoints.addAll(polyline);
                out.laneCount++;
            }

            // Identify bicycle connector internals via SUMO <connection via="...">.
            NodeList connNodes = doc.getElementsByTagName("connection");
            for (int i = 0; i < connNodes.getLength(); i++) {
                Element c = (Element) connNodes.item(i);
                if (!c.hasAttribute("via")) continue;
                String via = c.getAttribute("via");
                if (via == null || via.isEmpty()) continue;

                String fromEdge = c.hasAttribute("from") ? c.getAttribute("from") : "";
                String toEdge = c.hasAttribute("to") ? c.getAttribute("to") : "";
                String fromLane = c.hasAttribute("fromLane") ? c.getAttribute("fromLane") : "";
                String toLane = c.hasAttribute("toLane") ? c.getAttribute("toLane") : "";

                boolean fromBike = false;
                boolean toBike = false;

                if (!fromEdge.isEmpty() && !fromLane.isEmpty()) {
                    LaneShape fl = out.lanesById.get(fromEdge + "_" + fromLane);
                    fromBike = (fl != null) && fl.bikeOnly;
                }
                if (!toEdge.isEmpty() && !toLane.isEmpty()) {
                    LaneShape tl = out.lanesById.get(toEdge + "_" + toLane);
                    toBike = (tl != null) && tl.bikeOnly;
                }

                if (!fromBike && !fromEdge.isEmpty() && fromLane.isEmpty()) {
                    for (LaneShape l : out.lanes) {
                        if (l == null) continue;
                        if (!fromEdge.equals(l.edgeId)) continue;
                        if (l.bikeOnly) { fromBike = true; break; }
                    }
                }
                if (!toBike && !toEdge.isEmpty() && toLane.isEmpty()) {
                    for (LaneShape l : out.lanes) {
                        if (l == null) continue;
                        if (!toEdge.equals(l.edgeId)) continue;
                        if (l.bikeOnly) { toBike = true; break; }
                    }
                }

                if (fromBike || toBike) {
                    out.bicycleConnectorLaneIds.add(via);
                }
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
        edgesById.clear();
        bicycleConnectorLaneIds.clear();

        if (data != null) {
            lanes.addAll(data.lanes);
            lanesById.putAll(data.lanesById);
            edgesById.putAll(data.edgesById);
            junctions.addAll(data.junctions);
            edgeLabelWorldPos.putAll(data.edgeLabelWorldPos);
            trafficLightMarkers.addAll(data.trafficLightMarkers);
            bicycleConnectorLaneIds.addAll(data.bicycleConnectorLaneIds);
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

        // Keep smoothing caches bounded, but avoid doing O(n) work every frame.
        // Only prune when caches grow significantly beyond the current visible set.
        if (positions != null && !positions.isEmpty()) {
            int p = positions.size();
            if (lastVehicleWorldPos.size() > p * 2 + 100 || smoothedVehicleDir.size() > p * 2 + 100) {
                lastVehicleWorldPos.keySet().retainAll(positions.keySet());
                smoothedVehicleDir.keySet().retainAll(positions.keySet());
            }
        }
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
                                // Anchor to the START of the stop segment.
                                // SUMO stops are defined as [startPos, endPos] along a lane; vehicles stop at that segment.
                                // By anchoring to startPos and drawing a rectangle of stopLength forward,
                                // the bus will visually sit "inside" the stop area.
                                double tStart = Math.max(0.0, Math.min(1.0, startPos / laneLength));
                                Point2D lanePosStart = pointAlongPolyline(lane.polyline, tStart);
                                if (lanePosStart != null) {
                                    marker.worldPos = lanePosStart;
                                    marker.direction = laneTangentAt(lanePosStart, lane.polyline);
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
        // SUMO "standard" scheme
        // - bicycle lanes: orange/brown
        // - pedestrian paths/sidewalks: gray
        Color bikeLaneFill = Color.web("#d7a08b");
        Color pedPathFill = Color.web("#bdbdbd");

        // Default stroke style for the scene.
        g.setLineJoin(StrokeLineJoin.ROUND);

        // Junction polygons (base intersection surface).
        // This is drawn BEFORE lane borders/separators so the junction doesn't become a gray "blob".
        // SUMO-GUI typically shows a clean junction surface; connector internals are optional.
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
        // PERF/QUALITY:
        // Rendering each lane with a thick outer border causes double-drawn borders between adjacent lanes
        // (two lane boundaries overlap), which makes roads look different from SUMO-GUI.
        // Instead:
        //  1) Draw the road fill for all lanes.
        //  2) Draw ONLY the outer road border (outermost lanes).
        //  3) Draw ONE separator per lane boundary (no duplicates).

        // Precompute lane-index ranges per non-internal edge.
        Map<String, int[]> edgeLaneRange = new HashMap<>(); // edgeId -> [minIdx,maxIdx]
        Map<String, Map<Integer, LaneShape>> edgeLanesByIndex = new HashMap<>(); // edgeId -> (laneIndex -> lane)
        for (LaneShape lane : lanes) {
            if (lane == null || lane.edgeId == null || lane.edgeId.isEmpty()) continue;
            if (lane.edgeId.startsWith(":")) continue; // internal lanes handled separately
            if (lane.laneIndex < 0) continue;
            edgeLanesByIndex.computeIfAbsent(lane.edgeId, k -> new HashMap<>()).put(lane.laneIndex, lane);
            int[] mm = edgeLaneRange.get(lane.edgeId);
            if (mm == null) {
                edgeLaneRange.put(lane.edgeId, new int[]{lane.laneIndex, lane.laneIndex});
            } else {
                if (lane.laneIndex < mm[0]) mm[0] = lane.laneIndex;
                if (lane.laneIndex > mm[1]) mm[1] = lane.laneIndex;
            }
        }

        // Roads: use square/flat caps so road ends look like rectangles (closer to SUMO GUI).
        // We restore ROUND caps later for arrows/markers.
        g.setLineCap(StrokeLineCap.BUTT);

        // Pass 1: draw road fill (continuous areas).
        // Draw motor lanes first, then special lanes (bike/ped) on top so their colors are not covered.
        for (int pass = 0; pass < 2; pass++) {
            for (LaneShape lane : lanes) {
                if (lane == null || lane.polyline == null || lane.polyline.size() < 2) continue;
                double laneScreenWidth = Math.max(1.5, lane.widthMeters * scale);
                boolean internal = (lane.edgeId != null) && lane.edgeId.startsWith(":");

                if (internal) {
                    // Internal connector lanes are rendered later (after junction fill) so they don't clutter the intersection.
                    continue;
                }

                boolean motor = isMotorLane(lane);
                if ((pass == 0) != motor) continue;

                // Non-internal lanes: draw fill only (no per-lane outer border).
                // Slightly widen fill to hide tiny gaps between strokes, but keep it small to avoid
                // double-overdraw bands (which can look like a "super dark" separator).
                Color fill = roadFill;
                if (lane.pedestrianOnly) fill = pedPathFill;
                if (lane.bikeOnly) fill = bikeLaneFill;
                strokePolyline(g, lane.polyline, h, scale, laneScreenWidth + 0.25, fill, null, StrokeLineCap.BUTT);
            }
        }

        // Pass 2: draw borders + separators for non-internal edges.
        for (LaneShape lane : lanes) {
            if (lane == null || lane.polyline == null || lane.polyline.size() < 2) continue;
            if (lane.edgeId == null || lane.edgeId.isEmpty()) continue;
            if (lane.edgeId.startsWith(":")) continue;
            if (lane.laneIndex < 0) continue;
            int[] mm = edgeLaneRange.get(lane.edgeId);
            if (mm == null) continue;

            double laneScreenWidth = Math.max(1.5, lane.widthMeters * scale);

            // Outer border: only for outermost lanes.
            double borderW = clamp(laneScreenWidth * 0.22, 1.4, 2.8);
            if (lane.laneIndex == mm[0]) {
                // Rightmost lane (SUMO lane index 0 is rightmost in edge direction): draw right border.
                drawLaneSingleEdge(g, lane.polyline, h, scale, laneScreenWidth, roadEdge, borderW, -1);
            }
            if (lane.laneIndex == mm[1]) {
                // Leftmost lane: draw left border.
                drawLaneSingleEdge(g, lane.polyline, h, scale, laneScreenWidth, roadEdge, borderW, +1);
            }

            // Lane separator: draw one boundary per lane (between this lane and its neighbor).
            // Using the right edge avoids duplicates when indices increase leftwards.
            if (lane.laneIndex > mm[0]) {
                double sepW = clamp(laneScreenWidth * 0.12, 0.9, 1.6);
                boolean dash = false;
                LaneShape neighbor = null;
                Map<Integer, LaneShape> byIdx = edgeLanesByIndex.get(lane.edgeId);
                if (byIdx != null) {
                    // Find the immediate neighbor lane on the "right".
                    // Some nets use non-contiguous lane indices, so laneIndex-1 may not exist.
                    neighbor = byIdx.get(lane.laneIndex - 1);
                    if (neighbor == null) {
                        for (int idx = lane.laneIndex - 2; idx >= mm[0]; idx--) {
                            neighbor = byIdx.get(idx);
                            if (neighbor != null) break;
                        }
                    }
                    // User rule:
                    // - Dashed means 2+ lanes in SAME direction (within the same edge).
                    // - Solid center divider means OPPOSITE directions (handled in Pass 2b).
                    // Therefore: only dash between motor lanes within the edge.
                    if (neighbor != null && isMotorLane(lane) && isMotorLane(neighbor)) {
                        dash = true;
                    }
                } else {
                    dash = false;
                }
                if (dash) {
                    // SUMO shows same-direction multi-lane separators as dashed lines between lanes.
                    // Use the midline between the two lane center polylines, not an offset boundary,
                    // because some nets have irregular lane geometries where boundary-offset is wrong.
                    List<Point2D> bAligned = (neighbor != null) ? neighbor.polyline : null;
                    if (bAligned != null && lane.polyline != null && lane.polyline.size() >= 2 && bAligned.size() >= 2) {
                        double dStart = lane.polyline.get(0).distance(bAligned.get(0));
                        double dEnd = lane.polyline.get(0).distance(bAligned.get(bAligned.size() - 1));
                        if (dEnd < dStart) {
                            bAligned = new ArrayList<>(bAligned);
                            java.util.Collections.reverse(bAligned);
                        }
                    }
                    List<Point2D> center = buildAveragedCenterline(lane.polyline, bAligned, 32);
                    if (center.size() >= 2) {
                        strokePolyline(g, center, h, scale,
                                clamp(sepW, 1.0, 1.8),
                                Color.web("#f2f2f2").deriveColor(0, 1, 1, 0.90),
                                new double[]{12, 12},
                                StrokeLineCap.BUTT);
                    }
                } else {
                    drawLaneSingleEdge(g, lane.polyline, h, scale, laneScreenWidth, laneLine, sepW, -1);
                }
            }
        }

        // Pass 2b: solid centerline for two-way roads (opposite-direction edge pairs).
        // SUMO draws a solid divider between opposite-direction edges (A: from->to, B: to->from).
        // We pair edges using net topology (from/to IDs) rather than geometry heuristics to avoid
        // incorrect cross-pairing that can "destroy" the map.
        {
            final class EdgeRep {
                final String edgeId;
                final List<LaneShape> motorLanes;
                final Point2D start;
                final Point2D end;
                final double len;
                final Point2D dir;

                EdgeRep(String edgeId, List<LaneShape> motorLanes) {
                    this.edgeId = edgeId;
                    this.motorLanes = motorLanes;
                    List<Point2D> p = motorLanes.get(0).polyline;
                    this.start = p.get(0);
                    this.end = p.get(p.size() - 1);
                    this.len = polylineLength(p);
                    // Use a direction estimate away from the junction endpoints.
                    // Endpoints can include curved junction geometry which makes opposite edges
                    // fail the dot-product test even though they are true two-way pairs.
                    this.dir = polylineDirectionMid(p);
                }
            }

            final class LanePair {
                final LaneShape aLane;
                final List<Point2D> bAligned;
                final double avgDist;
                LanePair(LaneShape aLane, List<Point2D> bAligned, double avgDist) {
                    this.aLane = aLane;
                    this.bAligned = bAligned;
                    this.avgDist = avgDist;
                }
            }

            java.util.function.BiFunction<EdgeRep, EdgeRep, LanePair> bestLanePair = (a, b) -> {
                if (a == null || b == null) return null;
                if (a.motorLanes == null || b.motorLanes == null) return null;
                if (a.motorLanes.isEmpty() || b.motorLanes.isEmpty()) return null;
                LaneShape bestA = null;
                List<Point2D> bestBAligned = null;
                double bestDist = Double.POSITIVE_INFINITY;

                for (LaneShape la : a.motorLanes) {
                    if (la == null || la.polyline == null || la.polyline.size() < 2) continue;
                    for (LaneShape lb : b.motorLanes) {
                        if (lb == null || lb.polyline == null || lb.polyline.size() < 2) continue;

                        List<Point2D> bAligned = lb.polyline;
                        double dStart = la.polyline.get(0).distance(bAligned.get(0));
                        double dEnd = la.polyline.get(0).distance(bAligned.get(bAligned.size() - 1));
                        if (dEnd < dStart) {
                            bAligned = new ArrayList<>(bAligned);
                            java.util.Collections.reverse(bAligned);
                        }

                        double avgDist = averagePolylineDistanceMid(la.polyline, bAligned, 18);
                        if (avgDist < bestDist) {
                            bestDist = avgDist;
                            bestA = la;
                            bestBAligned = bAligned;
                        }
                    }
                }

                if (bestA == null || bestBAligned == null || !Double.isFinite(bestDist)) return null;
                return new LanePair(bestA, bestBAligned, bestDist);
            };

            Map<String, EdgeRep> repByEdge = new HashMap<>();
            for (Map.Entry<String, int[]> e : edgeLaneRange.entrySet()) {
                String edgeId = e.getKey();
                int[] mm = e.getValue();
                Map<Integer, LaneShape> byIdx = edgeLanesByIndex.get(edgeId);
                if (byIdx == null) continue;

                // Collect all MOTOR lanes for this edge.
                // We'll later choose the lane that is closest to the opposite edge so the divider
                // lands on the true median (works for 1-lane and multi-lane roads).
                List<LaneShape> motor = new ArrayList<>();
                for (int idx = mm[0]; idx <= mm[1]; idx++) {
                    LaneShape cand = byIdx.get(idx);
                    if (cand == null) continue;
                    if (!isMotorLane(cand)) continue;
                    if (cand.polyline == null || cand.polyline.size() < 2) continue;
                    motor.add(cand);
                }
                // If the edge has no motor lane at all (pure sidewalk/bike edge), skip it.
                if (!motor.isEmpty()) {
                    // Keep ordering stable (lower lane index first) if possible.
                    motor.sort(Comparator.comparingInt(ls -> ls.laneIndex));
                    repByEdge.put(edgeId, new EdgeRep(edgeId, motor));
                }
            }

            Map<String, List<EdgeRep>> repsByFromTo = new HashMap<>();
            for (EdgeRep r : repByEdge.values()) {
                EdgeInfo info = edgesById.get(r.edgeId);
                if (info == null) continue;
                if (info.from.isEmpty() || info.to.isEmpty()) continue;
                String key = info.from + "->" + info.to;
                repsByFromTo.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }

            // Pair each edge with candidates that swap from/to; choose the best local match.
            Map<String, String> pairs = new HashMap<>();
            for (EdgeRep a : repByEdge.values()) {
                if (pairs.containsKey(a.edgeId)) continue;
                EdgeInfo ai = edgesById.get(a.edgeId);
                if (ai == null || ai.from.isEmpty() || ai.to.isEmpty()) continue;

                List<EdgeRep> candidates = repsByFromTo.get(ai.to + "->" + ai.from);
                if (candidates == null || candidates.isEmpty()) continue;

                // Strong preference: SUMO often uses opposite IDs like E62 and -E62.
                // If that counterpart exists and is a candidate, pick it.
                String preferredId = a.edgeId.startsWith("-") ? a.edgeId.substring(1) : ("-" + a.edgeId);
                EdgeRep preferred = repByEdge.get(preferredId);
                if (preferred != null && candidates.contains(preferred) && !pairs.containsKey(preferred.edgeId)) {
                    pairs.put(a.edgeId, preferred.edgeId);
                    pairs.put(preferred.edgeId, a.edgeId);
                    continue;
                }

                EdgeRep best = null;
                double bestScore = Double.POSITIVE_INFINITY;

                for (EdgeRep b : candidates) {
                    if (b == null) continue;
                    if (b.edgeId.equals(a.edgeId)) continue;
                    if (pairs.containsKey(b.edgeId)) continue;

                    // Must be opposite direction visually.
                    double dot = a.dir.getX() * b.dir.getX() + a.dir.getY() * b.dir.getY();
                    if (dot > -0.55) continue;

                    // Score using the closest motor-lane pair between the two edges.
                    LanePair lp = bestLanePair.apply(a, b);
                    if (lp == null) continue;
                    double avgDist = lp.avgDist;
                    double lenDiff = Math.abs(a.len - b.len);
                    double score = avgDist + 0.05 * lenDiff;

                    // Skip obviously-wrong pairs (too far apart).
                    double maxDist = Math.max(7.0, Math.min(35.0, Math.min(a.len, b.len) * 0.20));
                    if (avgDist > maxDist) continue;

                    if (score < bestScore) {
                        bestScore = score;
                        best = b;
                    }
                }

                if (best != null) {
                    pairs.put(a.edgeId, best.edgeId);
                    pairs.put(best.edgeId, a.edgeId);
                }
            }

            // Draw each pair once.
            java.util.HashSet<String> drawn = new java.util.HashSet<>();
            for (Map.Entry<String, String> p : pairs.entrySet()) {
                String aId = p.getKey();
                String bId = p.getValue();
                if (aId == null || bId == null) continue;
                String canonical = (aId.compareTo(bId) < 0) ? (aId + "|" + bId) : (bId + "|" + aId);
                if (drawn.contains(canonical)) continue;
                drawn.add(canonical);

                EdgeRep a = repByEdge.get(aId);
                EdgeRep b = repByEdge.get(bId);
                if (a == null || b == null) continue;

                LanePair lp = bestLanePair.apply(a, b);
                if (lp == null) continue;
                List<Point2D> center = buildAveragedCenterline(lp.aLane.polyline, lp.bAligned, 30);
                if (center.size() < 2) continue;

                double wPx = clamp(1.4 * userScale, 1.2, 2.6);
                strokePolyline(g, center, h, scale,
                        wPx,
                        Color.web("#f2f2f2").deriveColor(0, 1, 1, 0.95),
                        null,
                        StrokeLineCap.BUTT);
            }
        }

        // Pass 2c: dashed bicycle connector lanes inside junctions (SUMO-style).
        // SUMO shows bicycle turning connectors as dashed white curves over the junction surface.
        // These are typically internal lanes whose edgeId starts with ':' and allow bicycles.
        {
            double dashLen = clamp(6.0 * Math.sqrt(clamp(userScale, 1.0, 25.0)), 5.0, 14.0);
            double[] dashes = new double[]{dashLen, dashLen};
            Color guide = Color.web("#f2f2f2").deriveColor(0, 1, 1, 0.75);

            for (LaneShape lane : lanes) {
                if (lane == null || lane.polyline == null || lane.polyline.size() < 2) continue;
                if (lane.edgeId == null || !lane.edgeId.startsWith(":")) continue;
                if (lane.pedestrianOnly) continue;

                // Draw only internal lanes that are actual bicycle connectors according to SUMO connections,
                // plus any internal lanes that are explicitly bike-only.
                boolean isBikeConnector = lane.bikeOnly;
                if (!isBikeConnector && lane.laneId != null && !lane.laneId.isEmpty()) {
                    isBikeConnector = bicycleConnectorLaneIds.contains(lane.laneId);
                }
                if (!isBikeConnector) continue;

                double laneScreenWidth = Math.max(1.5, lane.widthMeters * scale);
                double wPx = clamp(laneScreenWidth * 0.10, 0.9, 1.7);

                // Use the previous style: dashed boundaries (reads like a dedicated connector lane).
                drawLaneSingleEdge(g, lane.polyline, h, scale, laneScreenWidth, guide, wPx, -1, dashes);
                drawLaneSingleEdge(g, lane.polyline, h, scale, laneScreenWidth, guide, wPx, +1, dashes);
            }
        }

        // Restore round caps for small UI strokes (arrows, labels, markers).
        g.setLineCap(StrokeLineCap.ROUND);

        // NOTE: We only draw bicycle connector internals (not all connectors),
        // to match SUMO's visuals without cluttering the junction.

        drawLabels(g, h, scale);
        drawRoadDirectionArrows(g, h, scale, Color.web("#bdbdbd"));
        drawBusStops(g, h, scale);

        backgroundDirty = false;
    }

    private void redrawOverlay() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        // Compute a single smoothing alpha for this redraw (prevents per-vehicle exp/nanoTime cost).
        long nowNs = System.nanoTime();
        double dtSec = (lastOverlayRedrawNs == 0L) ? 0.0 : Math.max(0.0, (nowNs - lastOverlayRedrawNs) / 1_000_000_000.0);
        lastOverlayRedrawNs = nowNs;
        final double tauSec = 0.14; // smaller => more responsive; larger => smoother
        double alpha = (dtSec <= 0.0) ? 0.30 : (1.0 - Math.exp(-dtSec / tauSec));
        headingSmoothingAlpha = clamp(alpha, 0.18, 0.95);

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
                }

                Point2D tp = transform(drawWorld, h, scale);

                Double angleDegrees = (vehicleAngles != null) ? vehicleAngles.get(vehicleId) : null;

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
            g.setLineDashes();

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
            // IMPORTANT: allow shrinking when zoomed out (userScale < 1)
            // so bus stops don't become huge/overlapping at far zoom.
            double baseSize = clamp(6.0 + userScale * 2.0, 6.0, 30.0);
            double laneWidthPx = stop.laneWidth * scale;

            // Zoom boost for "max zoom only" thickness increase
            // 0.0 at <=2x zoom, approaches 1.0 around >=4x zoom.
            double zoomBoost = clamp((userScale - 2.0) / 2.0, 0.0, 1.0);

            // --- Draw waiting area (long and thick rectangle at road edge) ---
            // The stop rectangle represents the stop segment length.
            // Clamp prevents it being too long at far zoom, but allow bigger at max zoom
            // so buses visually fit into the station area.
            double lenMul = 0.90 + 0.30 * zoomBoost; // 0.90 (far) -> 1.20 (max zoom)
            double maxLen = baseSize * (8.0 + 10.0 * zoomBoost); // 8x..18x baseSize
            double areaLen = clamp(stop.stopLength * scale * lenMul, baseSize * 2.0, maxLen);

            // Make it thicker mainly at high zoom.
            double areaWid = baseSize * (0.65 + 0.35 * zoomBoost); // 0.65..1.00

            // Offset just outside the road edge (not overlapping road)
            double sidewalkOffset = laneWidthPx * 0.55 + areaWid * 0.5;

            // Anchor point is stop START on the lane. Shift half of the area forward so the
            // rectangle covers [start..end] rather than being centered at the start.
            double rectCx = cx + fx * (areaLen * 0.5);
            double rectCy = cy + fy * (areaLen * 0.5);

            // Apply sidewalk offset after the forward shift.
            double signX = rectCx + rx * sidewalkOffset;
            double signY = rectCy + ry * sidewalkOffset;
            g.setFill(waitingArea);
            g.setStroke(Color.GRAY);
            g.setLineWidth(1.0);
            g.setLineDashes();
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

            // --- Draw label with the SAME behavior as road/traffic labels ---
            // drawLabels() hides labels when scale < 0.8 and uses a zoom-scaled font.
            if (scale >= 0.8) {
                String label = (stop.name != null && !stop.name.isEmpty()) ? stop.name : stop.id;
                if (label != null && !label.isEmpty()) {
                    Font oldFont = g.getFont();
                    double labelFontSize = clamp(10.0 + (userScale - 1.0) * 2.0, 10.0, 16.0);
                    g.setFont(Font.font(labelFontSize));

                    // Match the same label style as road edges and traffic lights
                    drawTag(g, hX + hSize / 2 + 6, hY - hSize / 2 - 6, label, Color.WHITE, Color.web("#333333"));

                    g.setFont(oldFont);
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
                                  double x, double y, Double angleDegrees,
                                  String vehicleType, Color color, double scale) {
        // This function computes a stable heading direction:
        // 1) Prefer motion direction (best for lane changes)
        // 2) Prefer lane tangent when speed is ~0 (stable at stops)
        // 3) Prefer SUMO angle (works even if position updates are throttled)
        // For SUMO angle, we auto-detect the convention to avoid 90°-off headings.
        Point2D dirWorld = null;

        Point2D prevWorld = (vehicleId != null) ? lastVehicleWorldPos.get(vehicleId) : null;
        Point2D movementDirWorld = null;
        if (prevWorld != null && worldPos != null) {
            double mdx = worldPos.getX() - prevWorld.getX();
            double mdy = worldPos.getY() - prevWorld.getY();
            double mlen = Math.hypot(mdx, mdy);
            if (mlen > 1e-6) {
                movementDirWorld = new Point2D(mdx / mlen, mdy / mlen);
            }
        }

        Point2D laneDirWorld = null;
        if (lane != null && lane.polyline != null && lane.polyline.size() >= 2 && worldPos != null) {
            laneDirWorld = laneTangentAt(worldPos, lane.polyline);
        }

        // (1) Lane tangent in world coordinates (x right, y up)
        // causes cars to not turn when changing lanes in a straight edge
//        if (lane != null && lane.polyline != null && lane.polyline.size() >= 2 && worldPos != null) {
////        	System.out.println("using lane tangent");
//            dirWorld = laneTangentAt(worldPos, lane.polyline);
//        }

        // (1) Motion direction (world coordinates)
        if (movementDirWorld != null) {
            dirWorld = movementDirWorld;
        }

        // (2) If we're not moving (or missing prev), use lane tangent for stability.
        if (dirWorld == null && laneDirWorld != null) {
            dirWorld = laneDirWorld;
        }

        // (3) SUMO angle (world coordinates) when available.
        if (dirWorld == null && angleDegrees != null) {
            double angleRad = Math.toRadians(angleDegrees);

            // Two common conventions seen in projects:
            // A) 0° = East (+X), 90° = North (+Y)  => (cos, sin)
            // B) 0° = North (+Y), 90° = East (+X)  => (sin, cos)
            Point2D candA = new Point2D(Math.cos(angleRad), Math.sin(angleRad));
            Point2D candB = new Point2D(Math.sin(angleRad), Math.cos(angleRad));

            Point2D ref = (movementDirWorld != null) ? movementDirWorld : laneDirWorld;
            if (ref == null && vehicleId != null) {
                // Use previous smoothed direction as a tie-breaker (convert back to world-like by unflipping Y).
                Point2D prevScreen = smoothedVehicleDir.get(vehicleId);
                if (prevScreen != null) {
                    ref = new Point2D(prevScreen.getX(), -prevScreen.getY());
                }
            }

            if (ref != null) {
                double dotA = candA.getX() * ref.getX() + candA.getY() * ref.getY();
                double dotB = candB.getX() * ref.getX() + candB.getY() * ref.getY();
                dirWorld = (dotA >= dotB) ? candA : candB;
            } else {
                dirWorld = candA;
            }
        }

        // (4) Final fallback
        if (dirWorld == null) dirWorld = new Point2D(1.0, 0.0);

        // Convert world direction to screen direction (screen Y is flipped)
        Point2D dirScreen = new Point2D(dirWorld.getX(), -dirWorld.getY());
        dirScreen = normalize(dirScreen);

        // Smooth direction to avoid jitter / sudden flips at junctions.
        if (vehicleId != null) {
            boolean isBus = (vehicleType != null) && vehicleType.toLowerCase().contains("bus");

            // Buses should rotate more responsively; otherwise the back-shift can look like drifting.
            double alpha = isBus ? clamp(headingSmoothingAlpha * 1.75, 0.35, 0.98) : headingSmoothingAlpha;

            Point2D prevDir = smoothedVehicleDir.get(vehicleId);
            if (prevDir != null) {
                // If the direction flips ~180°, vector blending can collapse to near-zero.
                // Snap in that case to avoid weird spins.
                double dot = prevDir.getX() * dirScreen.getX() + prevDir.getY() * dirScreen.getY();
                if (dot < -0.55) {
                    prevDir = null;
                }
            }

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
     * Kept proportional to cars (uses the same zoom multiplier) but slightly longer.
     */
    private double busLengthPxForZoom(double mapScale) {
        // Slightly longer: ~1.8x car length at typical zooms.
        return Math.max(13.0, 22.0 * carSizeMulForZoom(mapScale));
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
        g.setLineDashes();
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

    private List<Point2D> buildAveragedCenterline(List<Point2D> a, List<Point2D> b, int samples) {
        List<Point2D> out = new ArrayList<>();
        if (a == null || b == null || a.size() < 2 || b.size() < 2) return out;
        int n = Math.max(2, samples);
        for (int i = 0; i < n; i++) {
            double t = (n == 1) ? 0.0 : ((double) i / (double) (n - 1));
            Point2D pa = pointAlongPolyline(a, t);
            Point2D pb = pointAlongPolyline(b, t);
            if (pa == null || pb == null) continue;
            out.add(new Point2D((pa.getX() + pb.getX()) * 0.5, (pa.getY() + pb.getY()) * 0.5));
        }
        return out;
    }

    private double polylineLength(List<Point2D> poly) {
        if (poly == null || poly.size() < 2) return 0.0;
        double sum = 0.0;
        for (int i = 1; i < poly.size(); i++) {
            sum += poly.get(i).distance(poly.get(i - 1));
        }
        return sum;
    }

    private double averagePolylineDistance(List<Point2D> a, List<Point2D> b, int samples) {
        if (a == null || b == null || a.size() < 2 || b.size() < 2) return Double.POSITIVE_INFINITY;
        int n = Math.max(4, samples);
        double sum = 0.0;
        int used = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / (double) (n - 1);
            Point2D pa = pointAlongPolyline(a, t);
            Point2D pb = pointAlongPolyline(b, t);
            if (pa == null || pb == null) continue;
            sum += pa.distance(pb);
            used++;
        }
        if (used == 0) return Double.POSITIVE_INFINITY;
        return sum / (double) used;
    }

    private double averagePolylineDistanceMid(List<Point2D> a, List<Point2D> b, int samples) {
        if (a == null || b == null || a.size() < 2 || b.size() < 2) return Double.POSITIVE_INFINITY;
        int n = Math.max(4, samples);
        double sum = 0.0;
        int used = 0;
        // Ignore the ends (junction curvature) and compare the mid-section.
        double t0 = 0.15;
        double t1 = 0.85;
        for (int i = 0; i < n; i++) {
            double u = (double) i / (double) (n - 1);
            double t = t0 + (t1 - t0) * u;
            Point2D pa = pointAlongPolyline(a, t);
            Point2D pb = pointAlongPolyline(b, t);
            if (pa == null || pb == null) continue;
            sum += pa.distance(pb);
            used++;
        }
        if (used == 0) return Double.POSITIVE_INFINITY;
        return sum / (double) used;
    }

    private Point2D polylineDirectionMid(List<Point2D> poly) {
        if (poly == null || poly.size() < 2) return new Point2D(0, 0);
        Point2D a = pointAlongPolyline(poly, 0.20);
        Point2D b = pointAlongPolyline(poly, 0.80);
        if (a == null || b == null) return polylineDirection(poly);
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) return new Point2D(0, 0);
        return new Point2D(dx / len, dy / len);
    }

    private Point2D polylineDirection(List<Point2D> poly) {
        if (poly == null || poly.size() < 2) return new Point2D(0, 0);
        Point2D a = poly.get(0);
        Point2D b = poly.get(poly.size() - 1);
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) return new Point2D(0, 0);
        return new Point2D(dx / len, dy / len);
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
                             double width, Color stroke, double[] dashes, StrokeLineCap cap) {
        if (worldPolyline == null || worldPolyline.size() < 2) return;
        g.save();
        g.setLineWidth(width);
        g.setStroke(stroke);
        // Be explicit: JavaFX can retain the previous dash state if we pass null,
        // which makes "solid" lines (like the 2-way divider) appear dashed.
        if (dashes == null) g.setLineDashes();
        else g.setLineDashes(dashes);
        if (cap != null) g.setLineCap(cap);

        Point2D p0 = transform(worldPolyline.get(0), height, scale);
        g.beginPath();
        g.moveTo(p0.getX(), p0.getY());
        for (int i = 1; i < worldPolyline.size(); i++) {
            Point2D pi = transform(worldPolyline.get(i), height, scale);
            g.lineTo(pi.getX(), pi.getY());
        }
        g.stroke();
        g.restore();
    }


    private void drawLaneEdges(GraphicsContext g, List<Point2D> worldPolyline, double height, double scale,
                              double laneScreenWidth, Color stroke) {
        if (worldPolyline == null || worldPolyline.size() < 2) return;
        g.setStroke(stroke);
        g.setLineDashes();
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

    /**
     * Draw only one boundary of a lane polyline.
     * sideSign: +1 draws the "left" side (along lane direction), -1 draws the "right" side.
     */
    private void drawLaneSingleEdge(GraphicsContext g, List<Point2D> worldPolyline, double height, double scale,
                                   double laneScreenWidth, Color stroke, double lineWidth, int sideSign) {
        drawLaneSingleEdge(g, worldPolyline, height, scale, laneScreenWidth, stroke, lineWidth, sideSign, null);
    }

    private void drawLaneSingleEdge(GraphicsContext g, List<Point2D> worldPolyline, double height, double scale,
                                   double laneScreenWidth, Color stroke, double lineWidth, int sideSign,
                                   double[] dashes) {
        if (worldPolyline == null || worldPolyline.size() < 2) return;
        g.save();
        g.setStroke(stroke);
        if (dashes == null) g.setLineDashes();
        else g.setLineDashes(dashes);
        g.setLineWidth(lineWidth);
        // Ensure square end-caps for borders/separators too.
        g.setLineCap(StrokeLineCap.BUTT);

        // IMPORTANT:
        // If we draw dashed lines segment-by-segment (strokeLine per segment), JavaFX resets the
        // dash phase each segment. With short segments (common in SUMO polylines), the result can
        // look like a solid line or like dashes are missing.
        // For dashed separators, draw a single continuous path.
        if (dashes != null) {
            List<Point2D> pts = buildOffsetPolylineScreen(worldPolyline, height, scale, laneScreenWidth, sideSign);
            if (pts.size() >= 2) {
                Point2D p0 = pts.get(0);
                g.beginPath();
                g.moveTo(p0.getX(), p0.getY());
                for (int i = 1; i < pts.size(); i++) {
                    Point2D pi = pts.get(i);
                    g.lineTo(pi.getX(), pi.getY());
                }
                g.stroke();
                g.restore();
                return;
            }
        }

        int sign = (sideSign >= 0) ? 1 : -1;
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
            double ox = nx * offset * sign;
            double oy = ny * offset * sign;
            g.strokeLine(p1.getX() + ox, p1.getY() + oy,
                    p2.getX() + ox, p2.getY() + oy);
        }

        g.restore();
    }

    /**
     * Builds a screen-space polyline offset to one side of a lane.
     * Offset is in screen pixels so it stays visually consistent across zoom levels.
     */
    private List<Point2D> buildOffsetPolylineScreen(List<Point2D> worldPolyline, double height, double scale,
                                                    double laneScreenWidth, int sideSign) {
        List<Point2D> out = new ArrayList<>();
        if (worldPolyline == null || worldPolyline.size() < 2) return out;

        int sign = (sideSign >= 0) ? 1 : -1;
        double offset = Math.max(1.8, laneScreenWidth / 2.0) * sign;

        // Transform all points once.
        int n = worldPolyline.size();
        Point2D[] p = new Point2D[n];
        for (int i = 0; i < n; i++) {
            p[i] = transform(worldPolyline.get(i), height, scale);
        }

        // Compute normals per segment in screen space.
        double[] nx = new double[n - 1];
        double[] ny = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            double dx = p[i + 1].getX() - p[i].getX();
            double dy = p[i + 1].getY() - p[i].getY();
            double len = Math.hypot(dx, dy);
            if (len < 1e-6) {
                nx[i] = 0;
                ny[i] = 0;
            } else {
                nx[i] = -dy / len;
                ny[i] = dx / len;
            }
        }

        // Offset each vertex by the averaged adjacent normals.
        for (int i = 0; i < n; i++) {
            double ax, ay;
            if (i == 0) {
                ax = nx[0];
                ay = ny[0];
            } else if (i == n - 1) {
                ax = nx[n - 2];
                ay = ny[n - 2];
            } else {
                ax = nx[i - 1] + nx[i];
                ay = ny[i - 1] + ny[i];
            }
            double alen = Math.hypot(ax, ay);
            if (alen < 1e-6) {
                // Fallback to any available normal.
                if (i > 0) {
                    ax = nx[i - 1];
                    ay = ny[i - 1];
                    alen = Math.hypot(ax, ay);
                }
                if (alen < 1e-6 && i < n - 1) {
                    ax = nx[i];
                    ay = ny[i];
                    alen = Math.hypot(ax, ay);
                }
            }
            if (alen < 1e-6) {
                out.add(p[i]);
            } else {
                ax /= alen;
                ay /= alen;
                out.add(new Point2D(p[i].getX() + ax * offset, p[i].getY() + ay * offset));
            }
        }

        return out;
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
        g.setLineDashes();
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
