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
    static final Logger LOGGER = Logger.getLogger(MapView.class.getName());

    // Support types (LaneShape, EdgeInfo, JunctionShape, ...) are split out into MapViewTypes.java.

    // Background canvas: static network rendering (roads, junctions, labels, bus stops)
    Canvas backgroundCanvas = new Canvas();
    // Overlay canvas: dynamic elements (vehicles, traffic signals)
    Canvas canvas = new Canvas();
    List<LaneShape> lanes = new ArrayList<>();
    Map<String, LaneShape> lanesById = new HashMap<>();
    List<JunctionShape> junctions = new ArrayList<>();
    Map<String, Point2D> edgeLabelWorldPos = new HashMap<>();
    List<TextMarker> trafficLightMarkers = new ArrayList<>();
    Map<String, Point2D> vehiclePositions;
    Map<String, Color> vehicleColors;
    Map<String, String> vehicleLaneIds;
    // Vehicle angles in degrees (heading from North, clockwise)
    Map<String, Double> vehicleAngles;
    // Vehicle types (e.g., "car", "bus", "motorcycle", "bike")
    Map<String, String> vehicleTypes;

    // Per-vehicle render smoothing state (keeps headings stable at junctions)
    Map<String, Point2D> lastVehicleWorldPos = new HashMap<>();
    Map<String, Point2D> smoothedVehicleDir = new HashMap<>();
    long lastOverlayRedrawNs = 0L;
    double headingSmoothingAlpha = 0.22;
    Map<String, Color> laneSignalColors;
    List<BusStopMarker> busStops = new ArrayList<>();
    double minX = 0, maxX = 1, minY = 0, maxY = 1;

    final Map<String, EdgeInfo> edgesById = new HashMap<>();

    // Internal connector lanes (":...") that belong to bicycle movements.
    // Populated from SUMO <connection via="..."> so we only draw the relevant dashed guides
    // and avoid cluttering junctions on maps where bicycles are allowed on general lanes.
    final java.util.HashSet<String> bicycleConnectorLaneIds = new java.util.HashSet<>();

    // Render scheduling
    boolean backgroundDirty = true;
    boolean overlayRedrawScheduled = false;

    // zoom / pan state
    double baseScale = 1.0;
    double userScale = 1.0;
    double offsetX = 0.0;
    double offsetY = 0.0;
    double padding = 20.0;
    double lastMouseX;
    double lastMouseY;

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
        return MapViewNetwork.loadNetwork(this, netFile);
    }

    /**
     * Loads the SUMO network on a background thread to avoid UI freezes.
     * Only the final apply + redraw happens on the JavaFX thread.
     */
    public void loadNetworkAsync(File netFile, IntConsumer onDone) {
        MapViewNetwork.loadNetworkAsync(this, netFile, onDone);
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
        MapViewRender.updateBusStops(this, busStopData);
    }

    private void layoutCanvas() {
        MapViewRender.layoutCanvas(this);
    }

    void redraw() {
        MapViewRender.redraw(this);
    }

    private void scheduleOverlayRedraw() {
        MapViewRender.scheduleOverlayRedraw(this);
    }

    static Point2D centroid(List<Point2D> poly) {
        return MapViewGeometry.centroid(poly);
    }

    static Point2D pointAlongPolyline(List<Point2D> polyline, double t) {
        return MapViewGeometry.pointAlongPolyline(polyline, t);
    }

    List<Point2D> parseShape(String shape) {
        return MapViewGeometry.parseShape(shape);
    }

    void computeBaseScale() {
        MapViewRender.computeBaseScale(this);
    }

    private void enableInteractions() {
        MapViewRender.enableInteractions(this);
    }

    private void zoom(double factor, double pivotX, double pivotY) {
        MapViewRender.zoom(this, factor, pivotX, pivotY);
    }

    void clampOffsets() {
        MapViewRender.clampOffsets(this);
    }

    double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

}
