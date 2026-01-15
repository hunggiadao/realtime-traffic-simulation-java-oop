import javafx.application.Platform;
import javafx.geometry.Point2D;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Network parsing/loading logic for {@link MapView}.
 *
 * This class exists only to split MapView into smaller, cleaner files.
 * It intentionally preserves MapView's existing parsing behavior.
 */
final class MapViewNetwork {
    private MapViewNetwork() {}

    static int loadNetwork(MapView view, File netFile) {
        view.lanes.clear();
        view.lanesById.clear();
        view.junctions.clear();
        view.edgeLabelWorldPos.clear();
        view.trafficLightMarkers.clear();
        view.edgesById.clear();
        view.bicycleConnectorLaneIds.clear();

        int laneCount = 0;
        if (netFile == null || !netFile.exists()) {
            MapView.LOGGER.warning("Net file missing: " + (netFile == null ? "null" : netFile.getPath()));
            view.redraw();
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
                view.edgesById.put(edgeId, new EdgeInfo(edgeId, from, to, name, function));
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
                List<Point2D> poly = view.parseShape(j.getAttribute("shape"));
                if (poly.isEmpty()) continue;
                view.junctions.add(new JunctionShape(jId, isTl, poly));
                allPoints.addAll(poly);
                if (isTl && jId != null && !jId.isEmpty()) {
                    Point2D c = MapView.centroid(poly);
                    if (c != null) {
                        view.trafficLightMarkers.add(new TextMarker("TL " + jId, c));
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
                List<Point2D> polyline = view.parseShape(shape);
                if (polyline.isEmpty()) continue;
                LaneShape ls = new LaneShape(laneId, polyline, width, allow, disallow);
                view.lanes.add(ls);
                if (laneId != null && !laneId.isEmpty()) {
                    view.lanesById.put(laneId, ls);
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
                    LaneShape fl = view.lanesById.get(fromEdge + "_" + fromLane);
                    fromBike = (fl != null) && fl.bikeOnly;
                }
                if (!toEdge.isEmpty() && !toLane.isEmpty()) {
                    LaneShape tl = view.lanesById.get(toEdge + "_" + toLane);
                    toBike = (tl != null) && tl.bikeOnly;
                }

                // If lane indices are missing/unusual, fall back to checking whether the edge has ANY bike-only lane.
                if (!fromBike && !fromEdge.isEmpty() && fromLane.isEmpty()) {
                    for (LaneShape l : view.lanes) {
                        if (l == null) continue;
                        if (!fromEdge.equals(l.edgeId)) continue;
                        if (l.bikeOnly) { fromBike = true; break; }
                    }
                }
                if (!toBike && !toEdge.isEmpty() && toLane.isEmpty()) {
                    for (LaneShape l : view.lanes) {
                        if (l == null) continue;
                        if (!toEdge.equals(l.edgeId)) continue;
                        if (l.bikeOnly) { toBike = true; break; }
                    }
                }

                if (fromBike || toBike) {
                    view.bicycleConnectorLaneIds.add(via);
                }
            }

            // Build edge label positions from lanes (one label per edge).
            for (LaneShape lane : view.lanes) {
                if (lane == null || lane.edgeId == null || lane.edgeId.isEmpty()) continue;
                if (lane.edgeId.startsWith(":")) continue;
                if (view.edgeLabelWorldPos.containsKey(lane.edgeId)) continue;
                Point2D p = MapView.pointAlongPolyline(lane.polyline, 0.55);
                if (p != null) {
                    view.edgeLabelWorldPos.put(lane.edgeId, p);
                }
            }

            if (!allPoints.isEmpty()) {
                view.minX = allPoints.stream().mapToDouble(Point2D::getX).min().orElse(0);
                view.maxX = allPoints.stream().mapToDouble(Point2D::getX).max().orElse(1);
                view.minY = allPoints.stream().mapToDouble(Point2D::getY).min().orElse(0);
                view.maxY = allPoints.stream().mapToDouble(Point2D::getY).max().orElse(1);
            } else {
                view.minX = view.maxX = view.minY = view.maxY = 0;
            }

            MapView.LOGGER.info(String.format(Locale.US,
                    "Loaded %d lanes. Bounds x:[%.2f, %.2f] y:[%.2f, %.2f]",
                    laneCount, view.minX, view.maxX, view.minY, view.maxY));
        } catch (Exception e) {
            MapView.LOGGER.log(java.util.logging.Level.WARNING, "Failed to load network", e);
        }

        view.offsetX = 0;
        view.offsetY = 0;
        view.userScale = 1.0;
        view.computeBaseScale();
        view.clampOffsets();
        view.backgroundDirty = true;
        view.redraw();
        return laneCount;
    }

    static final class NetworkData {
        final List<LaneShape> lanes = new ArrayList<>();
        final Map<String, LaneShape> lanesById = new HashMap<>();
        final Map<String, EdgeInfo> edgesById = new HashMap<>();
        final List<JunctionShape> junctions = new ArrayList<>();
        final Map<String, Point2D> edgeLabelWorldPos = new HashMap<>();
        final List<TextMarker> trafficLightMarkers = new ArrayList<>();
        final java.util.HashSet<String> bicycleConnectorLaneIds = new java.util.HashSet<>();
        double minX = 0, maxX = 1, minY = 0, maxY = 1;
        int laneCount = 0;
    }

    static void loadNetworkAsync(MapView view, File netFile, IntConsumer onDone) {
        // If missing file, just clear and return quickly.
        if (netFile == null || !netFile.exists()) {
            Platform.runLater(() -> {
                view.lanes.clear();
                view.lanesById.clear();
                view.junctions.clear();
                view.edgeLabelWorldPos.clear();
                view.trafficLightMarkers.clear();
                view.minX = 0; view.maxX = 1; view.minY = 0; view.maxY = 1;
                view.backgroundDirty = true;
                view.redraw();
                if (onDone != null) onDone.accept(0);
            });
            return;
        }

        Thread t = new Thread(() -> {
            NetworkData data = parseNetworkFile(view, netFile);
            Platform.runLater(() -> {
                applyNetworkData(view, data);
                if (onDone != null) onDone.accept(data.laneCount);
            });
        }, "NetLoader");
        t.setDaemon(true);
        t.start();
    }

    private static NetworkData parseNetworkFile(MapView view, File netFile) {
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
                List<Point2D> poly = view.parseShape(j.getAttribute("shape"));
                if (poly.isEmpty()) continue;
                out.junctions.add(new JunctionShape(jId, isTl, poly));
                allPoints.addAll(poly);

                if (isTl && jId != null && !jId.isEmpty()) {
                    Point2D c = MapView.centroid(poly);
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
                List<Point2D> polyline = view.parseShape(shape);
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
                Point2D p = MapView.pointAlongPolyline(lane.polyline, 0.55);
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
            MapView.LOGGER.log(java.util.logging.Level.WARNING, "Failed to load network (async)", e);
        }
        return out;
    }

    private static void applyNetworkData(MapView view, NetworkData data) {
        view.lanes.clear();
        view.lanesById.clear();
        view.junctions.clear();
        view.edgeLabelWorldPos.clear();
        view.trafficLightMarkers.clear();
        view.edgesById.clear();
        view.bicycleConnectorLaneIds.clear();

        if (data != null) {
            view.lanes.addAll(data.lanes);
            view.lanesById.putAll(data.lanesById);
            view.edgesById.putAll(data.edgesById);
            view.junctions.addAll(data.junctions);
            view.edgeLabelWorldPos.putAll(data.edgeLabelWorldPos);
            view.trafficLightMarkers.addAll(data.trafficLightMarkers);
            view.bicycleConnectorLaneIds.addAll(data.bicycleConnectorLaneIds);
            view.minX = data.minX;
            view.maxX = data.maxX;
            view.minY = data.minY;
            view.maxY = data.maxY;
        }

        view.offsetX = 0;
        view.offsetY = 0;
        view.userScale = 1.0;
        view.computeBaseScale();
        view.clampOffsets();
        view.backgroundDirty = true;
        view.redraw();
    }
}
