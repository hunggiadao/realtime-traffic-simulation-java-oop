import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ColorPicker;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.scene.paint.Color;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.animation.AnimationTimer;
import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.cmd.Edge;
import de.tudresden.sumo.objects.SumoLink;
import javafx.application.Platform;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.tudresden.sumo.objects.SumoTLSController;
import de.tudresden.sumo.objects.SumoTLSProgram;
import de.tudresden.sumo.objects.SumoTLSPhase;

public class UI {
    static final Logger LOGGER = Logger.getLogger(UI.class.getName());

    // Main window pane
    @FXML BorderPane rootPane;

    // Top toolbar
    @FXML Button btnOpenConfig;
    @FXML Button btnConnect;
    @FXML Button btnStart;
    @FXML Button btnStep;
    @FXML Slider sliderSpeed;

    // Top-right export menu
    @FXML MenuButton btnExport;

    // Left Simulation tab
    @FXML TextField txtConfigPath;
    @FXML TextField txtStepMs;
    @FXML Button btnBrowseConfig;

    // Inject tab
    @FXML ComboBox<String> cmbInjectEdge;
    @FXML TextField txtInjectCount;
    @FXML ColorPicker cpInjectColor;
    @FXML Button btnInject;

    // Filter tab
    @FXML javafx.scene.control.CheckBox chkFilterRed;
    @FXML ColorPicker cpFilterColor;
    @FXML javafx.scene.control.CheckBox chkFilterSpeed;
    @FXML javafx.scene.control.CheckBox chkFilterCongested;

    // Right: chart + table
    @FXML LineChart<Number, Number> vehicleCountChart;
    @FXML NumberAxis vehicleCountChartXAxis;
    @FXML NumberAxis vehicleCountChartYAxis;

    @FXML LineChart<Number, Number> avgSpeedChart;
    @FXML NumberAxis avgSpeedChartXAxis;
    @FXML NumberAxis avgSpeedChartYAxis;

    @FXML BarChart<String, Number> speedDistChart;
    @FXML CategoryAxis speedDistChartXAxis;
    @FXML NumberAxis speedDistChartYAxis;

    @FXML PieChart vehicleColorPie;
    @FXML TilePane vehicleColorLegendBox;

    @FXML TableView<VehicleRow> vehicleTable;
    @FXML TableColumn<VehicleRow, String> colId;
    @FXML TableColumn<VehicleRow, Number> colSpeed;
    @FXML TableColumn<VehicleRow, String> colEdge;
    @FXML TableColumn<VehicleRow, String> colColor;

    // Traffic lights tab
    @FXML ComboBox<String> cmbTrafficLight;
    @FXML Label lblPhaseInfo;
    @FXML TextField txtPhaseDuration;

    // Bottom status bar
    @FXML Label lblStep;
    @FXML Label lblSimTime;
    @FXML Label lblVehicles;
    @FXML Label lblStatus;
    @FXML StackPane mapPane;

    // Data
    ObservableList<VehicleRow> vehicleData = FXCollections.observableArrayList();
    XYChart.Series<Number, Number> vehicleSeries;
    XYChart.Series<Number, Number> avgSpeedSeries;
    XYChart.Series<String, Number> speedDistSeries;

    @SuppressWarnings("unchecked")
    final XYChart.Data<String, Number>[] speedDistBucketData = new XYChart.Data[4];
    final Timeline[] speedDistBucketAnim = new Timeline[4];
    static final Duration SPEED_DIST_ANIM_DURATION = Duration.millis(350);

    static final String[] SPEED_BUCKET_LABELS = new String[]{"0-2", "2-5", "5-10", "10+"};

    // Show exactly 4 groups max in BOTH the pie and legend: Top 3 + Others
    static final int MAX_COLOR_SLICES = 3; // top N, rest grouped into Others
    static final int MAX_COLOR_LEGEND_ITEMS = 4;

    int lastVehicleColorPieTotal = -1;
    int lastVehicleColorPieSignature = 0;

    // Keep a fixed set of pie slices and only update their values.
    // This avoids a JavaFX PieChart bug where reordering/replacing data can throw:
    // "Children: duplicate children added".
    final PieChart.Data[] vehicleColorPieSlots = new PieChart.Data[MAX_COLOR_SLICES + 1];
    boolean vehicleColorPieSlotsInitialized = false;
    final String[] vehicleColorPieSlotCss = new String[MAX_COLOR_SLICES + 1];

    public static final class PieSliceExport {
        public final String label;
        public final double value;
        public final String cssColor;

        public PieSliceExport(String label, double value, String cssColor) {
            this.label = label;
            this.value = value;
            this.cssColor = cssColor;
        }
    }

    long speedDistSamples = 0L;
    final double[] speedDistPctSum = new double[]{0.0, 0.0, 0.0, 0.0};

    static final class ChartSnapshot {
        final int vehicleCount;
        final double avgSpeed;
        final int[] speedBuckets;

        ChartSnapshot(int vehicleCount, double avgSpeed, int[] speedBuckets) {
            this.vehicleCount = vehicleCount;
            this.avgSpeed = avgSpeed;
            this.speedBuckets = speedBuckets;
        }
    }

    ChartSnapshot lastChartSnapshot;
    MapView mapView;

    String colorKey(Color c) { return UICharts.colorKey(c); }

    String cssColorFromKey(String colorKey) { return UICharts.cssColorFromKey(colorKey); }

    void setVehicleColorLegendVisible(boolean visible) { UICharts.setVehicleColorLegendVisible(this, visible); }

    void updateVehicleColorLegend(List<String> labels, List<String> cssColors) { UICharts.updateVehicleColorLegend(this, labels, cssColors); }

    void updateVehicleColorPie(Map<String, Integer> bucketCounts, Map<String, String> bucketHex) { UICharts.updateVehicleColorPie(this, bucketCounts, bucketHex); }

    int computeVehicleColorPieSignature(Map<String, Integer> bucketCounts) { return UICharts.computeVehicleColorPieSignature(bucketCounts); }

    void updateVehicleColorPieThrottled(Map<String, Integer> bucketCounts, Map<String, String> bucketHex) { UICharts.updateVehicleColorPieThrottled(this, bucketCounts, bucketHex); }

    // SUMO / TraCI
    TraCIConnector connector;
    VehicleWrapper vehicleWrapper;
    TrafficLightWrapper trafWrapper;
    EdgeWrapper edgeWrapper;
    InfrastructureWrapper infWrapper;
    UIKeys keyController;
    int stepLengthMs = 50; // default 50
    double stepLengthSeconds = 0.05; // default 0.05
    AnimationTimer loopTimer;
    boolean running = false; // default false
    long lastStepNs = 0; // default 0

    // Chart throttling: reduce point spam by adding a data point at most every 5 seconds while running.
    static final long VEHICLE_CHART_UPDATE_INTERVAL_NS = 5_000_000_000L;
    long lastVehicleChartUpdateNs = 0L;

    // Pie chart throttling: keep consistent cadence with Map Overview charts.
    long lastVehicleColorPieUpdateNs = 0L;

    // Settings + background monitor thread (explicit extra thread beyond main/JavaFX)
    UserSettings userSettings = new UserSettings();
    ScheduledExecutorService monitorExecutor;

    // Map/vehicle refresh can be expensive (per-vehicle TraCI calls). If the user
    // runs with very small step lengths, refreshing every step can overload SUMO.
    static final long MAP_UPDATE_MIN_INTERVAL_NS = 100_000_000L; // 100ms
    long lastMapUpdateNs = 0L;
    boolean pendingMapRefresh = false;

    // Injecting vehicles can be very expensive (multiple TraCI set/get calls + routing).
    // Users reported SUMO may terminate if injection happens at arbitrary cadences.
    // To keep things stable, rate-limit injections while running.
    static final long INJECT_MIN_INTERVAL_NS = 100_000_000L; // 100ms
    static final double DEFAULT_INJECT_SPEED_MS = 100.0;
    static final class PendingInjection {
        final String routeId;
        final Color color;

        PendingInjection(String routeId, Color color) {
            this.routeId = routeId;
            this.color = color;
        }
    }
    final Deque<PendingInjection> pendingInjections = new ArrayDeque<>();
    long nextInjectionNs = 0L;
    long injectSeq = 0L;

    void processPendingInjections(long nowNs) { UIMap.processPendingInjections(this, nowNs); }

    // Cache phase counts per traffic light (used to safely wrap phase +/-)
    Map<String, Integer> trafficLightPhaseCountCache = new HashMap<>();
    Map<String, List<SumoLink>> trafficLightLinksCache = new HashMap<>();

    public UI() {
        // called when FXML is loaded
    }

    /**
     * for debugging
     * @return
     */
    public TraCIConnector getTraCI() {
        return this.connector;
    }
    public VehicleWrapper getVehWrapper() {
        return this.vehicleWrapper;
    }
    public TrafficLightWrapper getTrafWrapper() {
        return this.trafWrapper;
    }
    public EdgeWrapper getEdgeWrapper() {
        return this.edgeWrapper;
    }
    public InfrastructureWrapper getInfWrapper() {
        return this.infWrapper;
    }
    public UIKeys getUIKeyWrapper() {
        return this.keyController;
    }

    @FXML
    private void initialize() {
        UISimulation.initialize(this);
    }

    // UI state helpers
    /**
     * Change the UI to disconnected state, show Connect button,
     * status bar says Disconnected, OpenConfig clickable
     */
    void setDisconnectedUI() { 
        UIState.setDisconnectedUI(this); 
    }
    /**
     * Change the UI to connected state, show Disconnect button,
     * status bar says Connected, OpenConfig not clickable
     */
    void setConnectedUI() { 
        UIState.setConnectedUI(this); 
    }
    /**
     * Change the UI to running state, show Disconnect button,
     * status bar says Running, OpenConfig not clickable
     */
    void setRunningUI() { 
        UIState.setRunningUI(this); 
    }

    boolean canExportNow() { 
        return UIState.canExportNow(this); 
    }

    void updateExportAvailability() { 
        UIState.updateExportAvailability(this); 
    }

    void disconnectFromSumo() { 
        UIState.disconnectFromSumo(this); 
    }

    void resetSessionStats() { 
        UICharts.resetSessionStats(this); 
    }

    public void setStatusText(String text) { 
        UIState.setStatusText(this, text); 
    }

    // Update after each step
    void updateAfterStep() { 
        UISimulation.updateAfterStep(this); 
    }

    void updateCharts(int step, int vehicleCount) { 
        UICharts.updateCharts(this, step, vehicleCount); 
    }

    void animateSpeedDistBucketTo(int index, double targetValue) { 
        UICharts.animateSpeedDistBucketTo(this, index, targetValue); 
    }

    @FXML
    private void handleKeyRelease(KeyEvent event) {
        UIHandlers.handleKeyRelease(this, event);
    }

    @FXML
    private void onOpenConfig() {
        UIHandlers.onOpenConfig(this);
    }

    @FXML
    private void onConnectToggle() {
        UISimulation.onConnectToggle(this);
    }

    @FXML
    public void onStartPause() {
        UISimulation.onStartPause(this);
    }

    @FXML
    private void onStep() {
        UISimulation.onStep(this);
    }

    /**
     * Called from the JavaFX Application stop() hook to close resources.
     */
    public void shutdown() {
        UISimulation.shutdown(this);
    }

    void startConnectionMonitor() {
        UISimulation.startConnectionMonitor(this);
    }

    void stopConnectionMonitor() {
        UISimulation.stopConnectionMonitor(this);
    }
    /**
     * Parse strings for Windows environment
     * @return
     */
    String resolveSumoBinary() {
        return UISumoFiles.resolveSumoBinary();
    }
    /**
     * helper function to load network file
     * @param configPath
     * @return
     */
    int loadNetworkForMap(String configPath) {
        return UIMap.loadNetworkForMap(this, configPath);
    }

    /**
     * Load bus stop data and update the map view.
     * Runs asynchronously to avoid blocking the UI thread.
     * Called once after connecting to SUMO.
     */
    void loadBusStopsForMap() {
        UIMap.loadBusStopsForMap(this);
    }

    List<File> resolveAdditionalFiles(String configPath) {
        return UISumoFiles.resolveAdditionalFiles(this, configPath);
    }

    List<String[]> parseBusStopsFromAdditionalFiles(List<File> additionalFiles) {
        return UISumoFiles.parseBusStopsFromAdditionalFiles(this, additionalFiles);
    }

    double parseDoubleOrDefault(String s, double def) {
        return UISumoFiles.parseDoubleOrDefault(s, def);
    }

    File resolveNetFile(String configPath) {
        return UISumoFiles.resolveNetFile(this, configPath);
    }
    /**
     * update map and vehicleRows data
     */
    void updateMapView() {
        UIMap.updateMapView(this);
    }

    /**
     * Are the colors are within a tolerance range of each other?
     * @param actual
     * @param target
     * @param tol tolerance amount
     * @return
     */
    public static boolean isSimilarColor(Color actual, Color target, double tol) {
        if (actual == null || target == null) return false;
        return Math.abs(actual.getRed() - target.getRed()) <= tol
                && Math.abs(actual.getGreen() - target.getGreen()) <= tol
                && Math.abs(actual.getBlue() - target.getBlue()) <= tol;
    }

    /**
     * return a File given a string path
     * @param path
     * @return
     */
    File resolveConfigFile(String path) { 
        return UISumoFiles.resolveConfigFile(path); 
    }

    void startLoop() {
        UILoop.startLoop(this);
    }

    void stopLoop() {
        UILoop.stopLoop(this);
    }

    void doStep() {
        UILoop.doStep(this);
    }

    // ------- Traffic light helpers -------

    void populateTrafficLights() { UITrafficLights.populateTrafficLights(this); }

    /**
     * get the number of phases for this TL
     * @param id
     * @return
     */
    int getTLPhaseCount(String id) { return UITrafficLights.getTLPhaseCount(this, id); }

    /**
     * supply TL state to each TL id, then update the UI accordingly
     */
    void updateTrafficLightUI() { UITrafficLights.updateTrafficLightUI(this); }

    @FXML
    private void onTrafficLightPrevPhase() {
        changeTrafficLightPhase(-1);
    }

    @FXML
    private void onTrafficLightNextPhase() {
        changeTrafficLightPhase(1);
    }
    // helper function for changing TL phase
    void changeTrafficLightPhase(int delta) { UITrafficLights.changeTrafficLightPhase(this, delta); }

    @FXML
    private void onTrafficLightApply() {
        UITrafficLights.applyTrafficLightDuration(this);
    }

    @FXML
    private void onInject() {
        UIHandlers.onInject(this);
    }

    @FXML
    public void handlePdfExport() {
        UIExporting.handlePdfExport(this);
    }

    @FXML
    public void handleCSVExport() {
        UIExporting.handleCSVExport(this);
    }


    /**
     * Returns the 4 graphs to include in PDF export (in display order).
     * Keys are human-friendly titles used as captions.
     */
    public Map<String, javafx.scene.Node> getExportGraphs() {
        return UIExporting.getExportGraphs(this);
    }

    /**
     * Export-friendly snapshot of the vehicle color pie (Top3 + Others).
     * This avoids JavaFX snapshot issues when the pie chart lives in a non-selected Tab.
     */
    public List<PieSliceExport> getVehicleColorPieExport() {
        return UIExporting.getVehicleColorPieExport(this);
    }
}
