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
    private static final Logger LOGGER = Logger.getLogger(UI.class.getName());

    // Main window pane
    @FXML private BorderPane rootPane;

    // Top toolbar
    @FXML private Button btnOpenConfig;
    @FXML private Button btnConnect;
    @FXML private Button btnStart;
    @FXML private Button btnStep;
    @FXML private Slider sliderSpeed;

    // Top-right export menu
    @FXML private MenuButton btnExport;

    // Left Simulation tab
    @FXML private TextField txtConfigPath;
    @FXML private TextField txtStepMs;
    @FXML private Button btnBrowseConfig;

    // Inject tab
    @FXML private ComboBox<String> cmbInjectEdge;
    @FXML private TextField txtInjectCount;
    @FXML private ColorPicker cpInjectColor;
    @FXML private Button btnInject;

    // Filter tab
    @FXML private javafx.scene.control.CheckBox chkFilterRed;
    @FXML private ColorPicker cpFilterColor;
    @FXML private javafx.scene.control.CheckBox chkFilterSpeed;
    @FXML private javafx.scene.control.CheckBox chkFilterCongested;

    // Right: chart + table
    @FXML private LineChart<Number, Number> vehicleCountChart;
    @FXML private NumberAxis vehicleCountChartXAxis;
    @FXML private NumberAxis vehicleCountChartYAxis;

    @FXML private LineChart<Number, Number> avgSpeedChart;
    @FXML private NumberAxis avgSpeedChartXAxis;
    @FXML private NumberAxis avgSpeedChartYAxis;

    @FXML private BarChart<String, Number> speedDistChart;
    @FXML private CategoryAxis speedDistChartXAxis;
    @FXML private NumberAxis speedDistChartYAxis;

    @FXML private PieChart vehicleColorPie;
    @FXML private TilePane vehicleColorLegendBox;

    @FXML private TableView<VehicleRow> vehicleTable;
    @FXML private TableColumn<VehicleRow, String> colId;
    @FXML private TableColumn<VehicleRow, Number> colSpeed;
    @FXML private TableColumn<VehicleRow, String> colEdge;
    @FXML private TableColumn<VehicleRow, String> colColor;

    // Traffic lights tab
    @FXML private ComboBox<String> cmbTrafficLight;
    @FXML private Label lblPhaseInfo;
    @FXML private TextField txtPhaseDuration;

    // Bottom status bar
    @FXML private Label lblStep;
    @FXML private Label lblSimTime;
    @FXML private Label lblVehicles;
    @FXML private Label lblStatus;
    @FXML private StackPane mapPane;

    // Data
    private ObservableList<VehicleRow> vehicleData = FXCollections.observableArrayList();
    private XYChart.Series<Number, Number> vehicleSeries;
    private XYChart.Series<Number, Number> avgSpeedSeries;
    private XYChart.Series<String, Number> speedDistSeries;

    @SuppressWarnings("unchecked")
    private final XYChart.Data<String, Number>[] speedDistBucketData = new XYChart.Data[4];
    private final Timeline[] speedDistBucketAnim = new Timeline[4];
    private static final Duration SPEED_DIST_ANIM_DURATION = Duration.millis(350);

    private static final String[] SPEED_BUCKET_LABELS = new String[]{"0-2", "2-5", "5-10", "10+"};

    // Show exactly 4 groups max in BOTH the pie and legend: Top 3 + Others
    private static final int MAX_COLOR_SLICES = 3; // top N, rest grouped into Others
    private static final int MAX_COLOR_LEGEND_ITEMS = 4;

    private int lastVehicleColorPieTotal = -1;
    private int lastVehicleColorPieSignature = 0;

    // Keep a fixed set of pie slices and only update their values.
    // This avoids a JavaFX PieChart bug where reordering/replacing data can throw:
    // "Children: duplicate children added".
    private final PieChart.Data[] vehicleColorPieSlots = new PieChart.Data[MAX_COLOR_SLICES + 1];
    private boolean vehicleColorPieSlotsInitialized = false;
    private final String[] vehicleColorPieSlotCss = new String[MAX_COLOR_SLICES + 1];

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

    private long speedDistSamples = 0L;
    private final double[] speedDistPctSum = new double[]{0.0, 0.0, 0.0, 0.0};

    private static final class ChartSnapshot {
        final int vehicleCount;
        final double avgSpeed;
        final int[] speedBuckets;

        ChartSnapshot(int vehicleCount, double avgSpeed, int[] speedBuckets) {
            this.vehicleCount = vehicleCount;
            this.avgSpeed = avgSpeed;
            this.speedBuckets = speedBuckets;
        }
    }

    private ChartSnapshot lastChartSnapshot;
    private MapView mapView;

    private String colorKey(Color c) {
        // Match the table's format (JavaFX Color.toString() => 0xrrggbbaa)
        if (c == null) return "0x000000ff";
        return c.toString();
    }

    private String cssColorFromKey(String colorKey) {
        // Convert 0xrrggbbaa to #rrggbb for CSS. Fallback to gray.
        if (colorKey == null) return "#9E9E9E";
        String s = colorKey.trim();
        if (s.startsWith("0x") && s.length() == 10) {
            return "#" + s.substring(2, 8);
        }
        if (s.startsWith("#") && (s.length() == 7 || s.length() == 4)) {
            return s;
        }
        return "#9E9E9E";
    }

    private void setVehicleColorLegendVisible(boolean visible) {
        if (vehicleColorLegendBox == null) return;
        vehicleColorLegendBox.setVisible(visible);
        vehicleColorLegendBox.setManaged(visible);
    }

    private void updateVehicleColorLegend(List<String> labels, List<String> cssColors) {
        if (vehicleColorLegendBox == null) return;

        vehicleColorLegendBox.getChildren().clear();
        if (labels == null || labels.isEmpty()) {
            setVehicleColorLegendVisible(false);
            return;
        }

        int n = Math.min(labels.size(), cssColors != null ? cssColors.size() : labels.size());
        n = Math.min(n, MAX_COLOR_LEGEND_ITEMS);
        for (int i = 0; i < n; i++) {
            String text = labels.get(i);
            String css = (cssColors != null) ? cssColors.get(i) : "#9E9E9E";

            Rectangle swatch = new Rectangle(10, 10);
            swatch.setArcWidth(3);
            swatch.setArcHeight(3);
            try {
                swatch.setFill(Color.web(css));
            } catch (Exception ignored) {
                swatch.setFill(Color.web("#9E9E9E"));
            }

            javafx.scene.control.Label lbl = new javafx.scene.control.Label(text);
            lbl.setWrapText(true);
            HBox row = new HBox(8, swatch, lbl);
            vehicleColorLegendBox.getChildren().add(row);
        }

        setVehicleColorLegendVisible(true);
    }

    private void updateVehicleColorPie(Map<String, Integer> bucketCounts, Map<String, String> bucketHex) {
        if (vehicleColorPie == null) return;

        if (!vehicleColorPieSlotsInitialized) {
            // initialize() should do this, but be defensive for any edge cases.
            ObservableList<PieChart.Data> init = FXCollections.observableArrayList();
            for (int i = 0; i < vehicleColorPieSlots.length; i++) {
                vehicleColorPieSlots[i] = new PieChart.Data("", 0.0);
                final int idx = i;
                vehicleColorPieSlots[i].nodeProperty().addListener((obs, oldN, newN) -> {
                    if (newN != null) {
                        String css = vehicleColorPieSlotCss[idx];
                        if (css != null && !css.isEmpty()) {
                            newN.setStyle("-fx-pie-color: " + css + ";");
                        }
                    }
                });
                init.add(vehicleColorPieSlots[i]);
            }
            vehicleColorPie.setData(init);
            vehicleColorPieSlotsInitialized = true;
        }

        if (bucketCounts == null || bucketCounts.isEmpty()) {
            for (PieChart.Data d : vehicleColorPieSlots) {
                if (d != null) {
                    d.setName("");
                    d.setPieValue(0.0);
                }
            }
            Arrays.fill(vehicleColorPieSlotCss, null);
            vehicleColorPie.setTitle("Vehicle Colors (0)");
            updateVehicleColorLegend(Collections.emptyList(), Collections.emptyList());
            return;
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(bucketCounts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int total = 0;
        for (Map.Entry<String, Integer> e : entries) total += e.getValue();

        int kept = 0;
        int others = 0;

        List<String> legendLabels = new ArrayList<>();
        List<String> legendCss = new ArrayList<>();

        // Fill top slots
        for (Map.Entry<String, Integer> e : entries) {
            if (kept >= MAX_COLOR_SLICES) {
                others += e.getValue();
                continue;
            }

            String key = e.getKey();
            int count = e.getValue();
            int pct = (total > 0) ? (int) Math.round(100.0 * (double) count / (double) total) : 0;
            String display = key + " (" + pct + "%)";
            String hex = cssColorFromKey(key);

            PieChart.Data slot = vehicleColorPieSlots[kept];
            if (slot != null) {
                slot.setName(display);
                slot.setPieValue(count);
                vehicleColorPieSlotCss[kept] = hex;
                if (slot.getNode() != null) {
                    slot.getNode().setStyle("-fx-pie-color: " + hex + ";");
                }
            }

            // Legend: cap to 4 items total. Prefer showing Others if there are many colors.
            int remainingSlotsForTopColors = (others > 0) ? (MAX_COLOR_LEGEND_ITEMS - 1) : MAX_COLOR_LEGEND_ITEMS;
            if (legendLabels.size() < remainingSlotsForTopColors) {
                legendLabels.add(display);
                legendCss.add(hex);
            }
            kept++;
        }

        // Clear remaining top slots
        for (int i = kept; i < MAX_COLOR_SLICES; i++) {
            PieChart.Data slot = vehicleColorPieSlots[i];
            if (slot != null) {
                slot.setName("");
                slot.setPieValue(0.0);
            }
            vehicleColorPieSlotCss[i] = null;
        }

        // Others slot
        PieChart.Data othersSlot = vehicleColorPieSlots[MAX_COLOR_SLICES];
        if (othersSlot != null) {
            if (others > 0) {
                int pct = (total > 0) ? (int) Math.round(100.0 * (double) others / (double) total) : 0;
                String label = "Others (" + pct + "%)";
                othersSlot.setName(label);
                othersSlot.setPieValue(others);
                vehicleColorPieSlotCss[MAX_COLOR_SLICES] = "#9E9E9E";
                if (othersSlot.getNode() != null) {
                    othersSlot.getNode().setStyle("-fx-pie-color: #9E9E9E;");
                }

                if (legendLabels.size() < MAX_COLOR_LEGEND_ITEMS) {
                    legendLabels.add(label);
                    legendCss.add("#9E9E9E");
                }
            } else {
                othersSlot.setName("");
                othersSlot.setPieValue(0.0);
                vehicleColorPieSlotCss[MAX_COLOR_SLICES] = null;
            }
        }

        vehicleColorPie.setTitle("Vehicle Colors (" + total + ")");
        updateVehicleColorLegend(legendLabels, legendCss);
    }

    private int computeVehicleColorPieSignature(Map<String, Integer> bucketCounts) {
        if (bucketCounts == null || bucketCounts.isEmpty()) return 0;
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(bucketCounts.entrySet());
        entries.sort((a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));
        int h = 1;
        for (Map.Entry<String, Integer> e : entries) {
            h = 31 * h + e.getKey().hashCode();
            h = 31 * h + e.getValue();
        }
        return h;
    }

    private void updateVehicleColorPieThrottled(Map<String, Integer> bucketCounts, Map<String, String> bucketHex) {
        if (vehicleColorPie == null) return;

        int total = 0;
        if (bucketCounts != null) {
            for (int c : bucketCounts.values()) total += c;
        }
        int signature = computeVehicleColorPieSignature(bucketCounts);

        boolean dataChanged = (total != lastVehicleColorPieTotal) || (signature != lastVehicleColorPieSignature);
        boolean totalDecreased = (lastVehicleColorPieTotal >= 0) && (total < lastVehicleColorPieTotal);

        boolean shouldUpdate;
        if (!running) {
            // When paused/stepping, update immediately when something changes.
            shouldUpdate = dataChanged;
        } else {
            long now = System.nanoTime();
            // While running, keep the same cadence as other charts, but don't lie:
            // if vehicles disappear (total decreases) or distribution changes, refresh immediately.
            shouldUpdate = (totalDecreased && dataChanged)
                    || ((lastVehicleColorPieUpdateNs == 0L) || ((now - lastVehicleColorPieUpdateNs) >= VEHICLE_CHART_UPDATE_INTERVAL_NS)) && dataChanged;
            if (shouldUpdate) lastVehicleColorPieUpdateNs = now;
        }

        if (!shouldUpdate) return;
        lastVehicleColorPieTotal = total;
        lastVehicleColorPieSignature = signature;
        updateVehicleColorPie(bucketCounts, bucketHex);
    }

    // SUMO / TraCI
    private TraCIConnector connector;
    private VehicleWrapper vehicleWrapper;
    private TrafficLightWrapper trafWrapper;
    private EdgeWrapper edgeWrapper;
    private InfrastructureWrapper infWrapper;
    private UIKeys keyController;
    private int stepLengthMs = 50; // default 50
    private double stepLengthSeconds = 0.05; // default 0.05
    private AnimationTimer loopTimer;
    private boolean running = false; // default false
    private long lastStepNs = 0; // default 0

    // Chart throttling: reduce point spam by adding a data point at most every 5 seconds while running.
    private static final long VEHICLE_CHART_UPDATE_INTERVAL_NS = 5_000_000_000L;
    private long lastVehicleChartUpdateNs = 0L;

    // Pie chart throttling: keep consistent cadence with Map Overview charts.
    private long lastVehicleColorPieUpdateNs = 0L;

    // Settings + background monitor thread (explicit extra thread beyond main/JavaFX)
    private UserSettings userSettings = new UserSettings();
    private ScheduledExecutorService monitorExecutor;

    // Map/vehicle refresh can be expensive (per-vehicle TraCI calls). If the user
    // runs with very small step lengths, refreshing every step can overload SUMO.
    private static final long MAP_UPDATE_MIN_INTERVAL_NS = 100_000_000L; // 100ms
    private long lastMapUpdateNs = 0L;
    private boolean pendingMapRefresh = false;

    // Injecting vehicles can be very expensive (multiple TraCI set/get calls + routing).
    // Users reported SUMO may terminate if injection happens at arbitrary cadences.
    // To keep things stable, rate-limit injections while running.
    private static final long INJECT_MIN_INTERVAL_NS = 100_000_000L; // 100ms
    private static final double DEFAULT_INJECT_SPEED_MS = 100.0;
    private static final class PendingInjection {
        final String routeId;
        final Color color;

        PendingInjection(String routeId, Color color) {
            this.routeId = routeId;
            this.color = color;
        }
    }
    private final Deque<PendingInjection> pendingInjections = new ArrayDeque<>();
    private long nextInjectionNs = 0L;
    private long injectSeq = 0L;

    private void processPendingInjections(long nowNs) {
        if (pendingInjections.isEmpty()) return;
        if (connector == null || vehicleWrapper == null || !connector.isConnected()) {
            pendingInjections.clear();
            return;
        }
        if (nextInjectionNs != 0L && nowNs < nextInjectionNs) return;

        PendingInjection req = pendingInjections.pollFirst();
        if (req == null) return;

        String vehId = "inj_" + (++injectSeq);
        vehicleWrapper.addVehicle(vehId, req.routeId, DEFAULT_INJECT_SPEED_MS, req.color);
        nextInjectionNs = nowNs + INJECT_MIN_INTERVAL_NS;
        pendingMapRefresh = true;
    }

    // Cache phase counts per traffic light (used to safely wrap phase +/-)
    private Map<String, Integer> trafficLightPhaseCountCache = new HashMap<>();
    private Map<String, List<SumoLink>> trafficLightLinksCache = new HashMap<>();

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
        AppLogger.init();
        LOGGER.info("UI initialized");

        userSettings.load();

        // Make Filters + Traffic Lights UI reactive (no need to wait for a simulation step)
        if (chkFilterRed != null) {
            chkFilterRed.selectedProperty().addListener((obs, oldV, newV) -> updateMapView());
        }
        if (cpFilterColor != null) {
            cpFilterColor.setValue(Color.RED);
            cpFilterColor.valueProperty().addListener((obs, oldV, newV) -> {
                if (chkFilterRed != null && chkFilterRed.isSelected()) updateMapView();
            });
        }
        if (chkFilterSpeed != null) {
            chkFilterSpeed.selectedProperty().addListener((obs, oldV, newV) -> updateMapView());
        }
        if (chkFilterCongested != null) {
            chkFilterCongested.selectedProperty().addListener((obs, oldV, newV) -> updateMapView());
        }
        if (cmbTrafficLight != null) {
            cmbTrafficLight.valueProperty().addListener((obs, oldV, newV) -> updateTrafficLightUI());
        }

        // Table setup
        if (colId != null && colSpeed != null && colEdge != null && colColor != null) {
            colId.setCellValueFactory(data -> data.getValue().idProperty());
            colSpeed.setCellValueFactory(data -> data.getValue().speedProperty());
            colEdge.setCellValueFactory(data -> data.getValue().edgeProperty());
            colColor.setCellValueFactory(data -> data.getValue().colorProperty());
        }
        if (vehicleTable != null) {
            vehicleTable.setItems(vehicleData);
        }

        if (vehicleColorPie != null) {
            vehicleColorPie.setAnimated(true);
            vehicleColorPie.setLegendVisible(false);
            vehicleColorPie.setLabelsVisible(false);
            vehicleColorPie.setTitle("Vehicle Colors (0)");
            vehicleColorPie.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
            if (!vehicleColorPieSlotsInitialized) {
                ObservableList<PieChart.Data> init = FXCollections.observableArrayList();
                for (int i = 0; i < vehicleColorPieSlots.length; i++) {
                    vehicleColorPieSlots[i] = new PieChart.Data("", 0.0);
                    final int idx = i;
                    vehicleColorPieSlots[i].nodeProperty().addListener((obs, oldN, newN) -> {
                        if (newN != null) {
                            String css = vehicleColorPieSlotCss[idx];
                            if (css != null && !css.isEmpty()) {
                                newN.setStyle("-fx-pie-color: " + css + ";");
                            }
                        }
                    });
                    init.add(vehicleColorPieSlots[i]);
                }
                vehicleColorPie.setData(init);
                vehicleColorPieSlotsInitialized = true;
            }
        }

        setVehicleColorLegendVisible(false);

        // Chart setup
        if (vehicleCountChart != null) {
            // Use a style class (not fx:id) so CSS can target the chart reliably.
            if (!vehicleCountChart.getStyleClass().contains("vehicle-count-chart")) {
                vehicleCountChart.getStyleClass().add("vehicle-count-chart");
            }
            vehicleSeries = new XYChart.Series<>();
            vehicleSeries.setName("Vehicle count");
            vehicleCountChart.getData().add(vehicleSeries);

            vehicleCountChart.setLegendVisible(false);
            vehicleCountChart.setCreateSymbols(false);
        }

        if (avgSpeedChart != null) {
            avgSpeedSeries = new XYChart.Series<>();
            avgSpeedSeries.setName("Avg speed");
            avgSpeedChart.getData().add(avgSpeedSeries);

            avgSpeedChart.setLegendVisible(false);
            avgSpeedChart.setCreateSymbols(false);
        }

        if (speedDistChart != null) {
            speedDistSeries = new XYChart.Series<>();
            speedDistSeries.setName("Vehicles");
            speedDistChart.getData().add(speedDistSeries);
            speedDistChart.setLegendVisible(false);

            speedDistChart.setAnimated(false);
            if (speedDistChartXAxis != null) speedDistChartXAxis.setAnimated(false);
            if (speedDistChartYAxis != null) speedDistChartYAxis.setAnimated(false);

            for (int i = 0; i < SPEED_BUCKET_LABELS.length; i++) {
                XYChart.Data<String, Number> d = new XYChart.Data<>(SPEED_BUCKET_LABELS[i], 0.0);
                speedDistBucketData[i] = d;
                speedDistSeries.getData().add(d);
            }
        }

        // Defaults - adjust to your project paths
        if (txtConfigPath != null) {
            // default relative to project root (java-code/../SumoConfig) but accept current dir too
            File preferParent = Paths.get("..\\SumoConfig\\G.sumocfg").toFile();
            if (preferParent.exists()) {
                txtConfigPath.setText("..\\SumoConfig\\G.sumocfg");
            } else {
                txtConfigPath.setText(".\\SumoConfig\\G.sumocfg");
            }

            // Override defaults with last saved value if present
            String lastCfg = userSettings.getString("configPath", "");
            if (lastCfg != null && !lastCfg.isBlank()) {
                txtConfigPath.setText(lastCfg);
            }
        }
        if (txtStepMs != null) {
            txtStepMs.setText(userSettings.getString("stepMs", "50"));
        }
        if (sliderSpeed != null) {
            sliderSpeed.setMin(0.25);
            sliderSpeed.setMax(5.0);
            sliderSpeed.setValue(1.0);
        }
        if (cpInjectColor != null) {
            // Always start with red for injection.
            cpInjectColor.setValue(Color.RED);
        }
        if (mapPane != null) {
            mapView = new MapView();
            mapView.prefWidthProperty().bind(mapPane.widthProperty());
            mapView.prefHeightProperty().bind(mapPane.heightProperty());
            mapPane.getChildren().clear();
            mapPane.getChildren().add(mapView);
        }

        // Clear traffic light UI until we connect
        if (cmbTrafficLight != null) {
            cmbTrafficLight.getItems().clear();
        }
        if (lblPhaseInfo != null) {
            lblPhaseInfo.setText("Phase: -");
        }
        if (txtPhaseDuration != null) {
            txtPhaseDuration.setText("");
        }

        // set focus on main window for key press listening
        if (rootPane != null) {
            rootPane.setFocusTraversable(true);
            Platform.runLater(() -> rootPane.requestFocus());
            rootPane.setOnMouseClicked(event -> rootPane.requestFocus());
        }

        setDisconnectedUI();
    }

    // UI state helpers
    /**
     * Change the UI to disconnected state, show Connect button,
     * status bar says Disconnected, OpenConfig clickable
     */
    private void setDisconnectedUI() {
        if (lblStatus != null) lblStatus.setText("Status: Disconnected");
        if (btnConnect != null) {
            btnConnect.setDisable(false);
            btnConnect.setText("Connect");
        }
        if (btnOpenConfig != null) btnOpenConfig.setDisable(false);
        if (btnBrowseConfig != null) btnBrowseConfig.setDisable(false);
        if (txtConfigPath != null) txtConfigPath.setDisable(false);
        if (txtStepMs != null) txtStepMs.setDisable(false);
        if (btnStart != null) {
            btnStart.setDisable(true);
            btnStart.setText("Start");
        }
        if (btnStep != null) btnStep.setDisable(true);

        updateExportAvailability();
    }
    /**
     * Change the UI to connected state, show Disconnect button,
     * status bar says Connected, OpenConfig not clickable
     */
    private void setConnectedUI() {
        if (lblStatus != null) lblStatus.setText("Status: Connected");
        if (btnConnect != null) {
            btnConnect.setDisable(false);
            btnConnect.setText("Disconnect");
        }
        if (btnOpenConfig != null) btnOpenConfig.setDisable(true);
        if (btnBrowseConfig != null) btnBrowseConfig.setDisable(true);
        if (txtConfigPath != null) txtConfigPath.setDisable(true);
        if (txtStepMs != null) txtStepMs.setDisable(true);
        if (btnStart != null) {
            btnStart.setDisable(false);
            btnStart.setText("Start");
        }
        if (btnStep != null) btnStep.setDisable(false);

        updateExportAvailability();
    }
    /**
     * Change the UI to running state, show Disconnect button,
     * status bar says Running, OpenConfig not clickable
     */
    private void setRunningUI() {
        if (lblStatus != null) lblStatus.setText("Status: Running");
        if (btnConnect != null) {
            btnConnect.setDisable(false);
            btnConnect.setText("Disconnect");
        }
        if (btnStart != null) {
            btnStart.setDisable(false);
            btnStart.setText("Pause");
        }
        if (btnStep != null) btnStep.setDisable(true);

        updateExportAvailability();
    }

    private boolean canExportNow() {
        return connector != null && connector.isConnected() && !running;
    }

    private void updateExportAvailability() {
        if (btnExport == null) return;
        // Export should be enabled ONLY when SUMO is connected and currently paused.
        btnExport.setDisable(!canExportNow());
    }

    private void disconnectFromSumo() {
        stopLoop();
        stopConnectionMonitor();
        if (connector != null) {
            connector.disconnect();
        }
        connector = null;
        vehicleWrapper = null;
        if (cmbTrafficLight != null) {
            cmbTrafficLight.getItems().clear();
        }
        if (lblPhaseInfo != null) {
            lblPhaseInfo.setText("Phase: -");
        }
        if (txtPhaseDuration != null) {
            txtPhaseDuration.setText("");
        }
        setDisconnectedUI();
    }

    private void resetSessionStats() {
        if (vehicleSeries != null) {
            vehicleSeries.getData().clear();
        }
        if (avgSpeedSeries != null) {
            avgSpeedSeries.getData().clear();
        }
        if (speedDistSeries != null) {
            // Keep 4 persistent bars; reset them to 0 instead of clearing the series.
            for (int i = 0; i < speedDistBucketAnim.length; i++) {
                if (speedDistBucketAnim[i] != null) {
                    speedDistBucketAnim[i].stop();
                    speedDistBucketAnim[i] = null;
                }
            }

            boolean needsRebuild = speedDistSeries.getData().size() != SPEED_BUCKET_LABELS.length;
            for (int i = 0; i < SPEED_BUCKET_LABELS.length && !needsRebuild; i++) {
                if (speedDistBucketData[i] == null) {
                    needsRebuild = true;
                }
            }

            if (needsRebuild) {
                speedDistSeries.getData().clear();
                for (int i = 0; i < SPEED_BUCKET_LABELS.length; i++) {
                    XYChart.Data<String, Number> d = new XYChart.Data<>(SPEED_BUCKET_LABELS[i], 0.0);
                    speedDistBucketData[i] = d;
                    speedDistSeries.getData().add(d);
                }
            } else {
                for (int i = 0; i < SPEED_BUCKET_LABELS.length; i++) {
                    speedDistBucketData[i].setYValue(0.0);
                }
            }
        }
        lastChartSnapshot = null;
        lastVehicleChartUpdateNs = 0L;

        lastVehicleColorPieUpdateNs = 0L;
        lastVehicleColorPieTotal = -1;
        lastVehicleColorPieSignature = 0;
        if (vehicleColorPie != null) {
            for (PieChart.Data d : vehicleColorPieSlots) {
                if (d != null) {
                    d.setName("");
                    d.setPieValue(0.0);
                }
            }
            vehicleColorPie.setTitle("Vehicle Colors (0)");
        }

        speedDistSamples = 0L;
        Arrays.fill(speedDistPctSum, 0.0);

        vehicleData.clear();
        if (vehicleTable != null) {
            vehicleTable.refresh();
        }
    }

    public void setStatusText(String text) {
        if (lblStatus != null) lblStatus.setText(text);
    }

    // Update after each step
    private void updateAfterStep() {
        if (connector == null) return;

        long nowNs = System.nanoTime();
        processPendingInjections(nowNs);

        int step = connector.getCurrentStep();
        double simTime = connector.getSimTimeSeconds();
        int vehicleCount = (vehicleWrapper != null) ? vehicleWrapper.getVehicleCount() : 0;

        if (lblStep != null) {
            lblStep.setText("Step: " + step);
        }
        if (lblSimTime != null) {
            lblSimTime.setText(String.format("Sim time: %.1f s", simTime));
        }
        if (lblVehicles != null) {
            lblVehicles.setText("Vehicles: " + vehicleCount);
        }

        // Throttle expensive map refresh while running.
        boolean shouldUpdateMap = true;
        if (running) {
            long now = nowNs;
            if (pendingMapRefresh) {
                pendingMapRefresh = false;
                lastMapUpdateNs = now;
                shouldUpdateMap = true;
            } else if (lastMapUpdateNs != 0L && (now - lastMapUpdateNs) < MAP_UPDATE_MIN_INTERVAL_NS) {
                shouldUpdateMap = false;
            } else {
                lastMapUpdateNs = now;
                shouldUpdateMap = true;
            }
        }

        if (shouldUpdateMap) {
            updateMapView();
        }

        updateCharts(step, vehicleCount);
    }

    private void updateCharts(int step, int vehicleCount) {
        if (vehicleSeries == null) return;

        // While running, throttle chart updates so the plots remain readable.
        // When stepping manually (running == false), update every step.
        boolean shouldUpdate;
        if (!running) {
            shouldUpdate = true;
        } else {
            long now = System.nanoTime();
            shouldUpdate = (lastVehicleChartUpdateNs == 0L) || ((now - lastVehicleChartUpdateNs) >= VEHICLE_CHART_UPDATE_INTERVAL_NS);
            if (shouldUpdate) lastVehicleChartUpdateNs = now;
        }

        if (!shouldUpdate) return;

        vehicleSeries.getData().add(new XYChart.Data<>(step, vehicleCount));

        ChartSnapshot snap = lastChartSnapshot;
        if (snap == null) return;

        if (avgSpeedSeries != null) {
            avgSpeedSeries.getData().add(new XYChart.Data<>(step, snap.avgSpeed));
        }

        if (speedDistSeries != null && speedDistChart != null) {
            double[] targetPct = new double[SPEED_BUCKET_LABELS.length];

            // If there are no vehicles right now, keep bars at 0 (but do not remove them),
            // and reset aggregation so the next vehicles start fresh.
            if (snap.vehicleCount <= 0) {
                speedDistSamples = 0L;
                Arrays.fill(speedDistPctSum, 0.0);
                Arrays.fill(targetPct, 0.0);
            } else {
                // Update distribution only on the same cadence as the other charts.
                // We aggregate as a running-average % per bucket.
                speedDistSamples++;
                for (int i = 0; i < SPEED_BUCKET_LABELS.length; i++) {
                    double pct = 100.0 * (double) snap.speedBuckets[i] / (double) snap.vehicleCount;
                    speedDistPctSum[i] += pct;
                    targetPct[i] = speedDistPctSum[i] / (double) speedDistSamples;
                }
            }

            for (int i = 0; i < SPEED_BUCKET_LABELS.length && i < speedDistBucketData.length; i++) {
                animateSpeedDistBucketTo(i, targetPct[i]);
            }
        }
    }

    private void animateSpeedDistBucketTo(int index, double targetValue) {
        if (index < 0 || index >= speedDistBucketData.length) return;
        XYChart.Data<String, Number> data = speedDistBucketData[index];
        if (data == null) return;

        Timeline old = speedDistBucketAnim[index];
        if (old != null) {
            old.stop();
        }

        KeyValue kv = new KeyValue(data.YValueProperty(), targetValue, Interpolator.EASE_BOTH);
        Timeline tl = new Timeline(new KeyFrame(SPEED_DIST_ANIM_DURATION, kv));
        speedDistBucketAnim[index] = tl;
        tl.play();
    }

    @FXML
    private void handleKeyRelease(KeyEvent event) {
        KeyCode code = event.getCode();

        switch (code) {
            case UP:
                System.out.println("Up");
                changeTrafficLightPhase(1);
                break;
            case DOWN:
                System.out.println("Down");
                changeTrafficLightPhase(-1);
                break;
            case LEFT:
                System.out.println("Left");
                keyController.selectPreviousTrafficLight();
                cmbTrafficLight.getSelectionModel().select(keyController.getCurrentTrafficLightIndex());
                break;
            case RIGHT:
                System.out.println("Right");
                keyController.selectNextTrafficLight();
                cmbTrafficLight.getSelectionModel().select(keyController.getCurrentTrafficLightIndex());
                break;
            case P:
                System.out.println("P");
                keyController.togglePause();
                break;
            default:
                break;
        }
    }

    @FXML
    private void onOpenConfig() {
        Window window = null;
        if (btnBrowseConfig != null && btnBrowseConfig.getScene() != null) {
            window = btnBrowseConfig.getScene().getWindow();
        } else if (btnOpenConfig != null && btnOpenConfig.getScene() != null) {
            window = btnOpenConfig.getScene().getWindow();
        } else if (rootPane != null && rootPane.getScene() != null) {
            window = rootPane.getScene().getWindow();
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open SUMO Config");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SUMO config (*.sumocfg)", "*.sumocfg")
        );
        File file = chooser.showOpenDialog(window);
        if (file != null && txtConfigPath != null) {
            txtConfigPath.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void onConnectToggle() {
        // If already connected, act as Disconnect
        if (connector != null && connector.isConnected()) {
            disconnectFromSumo();
            return;
        }

        // Get config path and step length from UI
        String configPath = (txtConfigPath != null) ? txtConfigPath.getText().trim() : "..\\SumoConfig\\G.sumocfg";

        try {
            if (txtStepMs != null) {
                stepLengthMs = Integer.parseInt(txtStepMs.getText().trim());
            }
        } catch (NumberFormatException e) {
            stepLengthMs = 50;
            txtStepMs.setText("50");
        }

        stepLengthSeconds = stepLengthMs / 1000.0;
        File cfgFile = resolveConfigFile(configPath);
        if (cfgFile == null || !cfgFile.exists()) {
            setStatusText("Config not found: " + configPath);
            return;
        }

        // Persist settings (explicit file I/O requirement)
        userSettings.putString("configPath", configPath);
        userSettings.putString("stepMs", String.valueOf(stepLengthMs));
        userSettings.save();

        // Path to SUMO (headless calculation only, render inside JavaFX)
        String sumoBinary = resolveSumoBinary();

        if (btnConnect != null) {
            btnConnect.setDisable(true);
            btnConnect.setText("Connecting...");
        }
        setStatusText("Status: Connecting...");

        Thread connectThread = new Thread(() -> {
            TraCIConnector localConnector = new TraCIConnector(sumoBinary, cfgFile.getPath(), (double)stepLengthSeconds);
            boolean ok = localConnector.connect();
            if (!ok) {
                Platform.runLater(() -> {
                    setStatusText("Status: Connection failed");
                    if (btnConnect != null) {
                        btnConnect.setDisable(false);
                        btnConnect.setText("Connect");
                    }
                });
                return;
            }

        vehicleWrapper = new VehicleWrapper(localConnector);
        trafWrapper = new TrafficLightWrapper(localConnector);
        keyController = new UIKeys(trafWrapper, this);
        edgeWrapper = new EdgeWrapper(localConnector, vehicleWrapper);
        infWrapper = new InfrastructureWrapper(localConnector);

            Platform.runLater(() -> {
                // Adopt the connected instance on the UI thread
                connector = localConnector;
                resetSessionStats();

                if (cpInjectColor != null) {
                    cpInjectColor.setValue(Color.RED);
                }

                startConnectionMonitor();

                // Load network asynchronously (parsing can be large)
                // Bus stops are loaded AFTER network is ready (in the callback)
                File netFile = resolveNetFile(cfgFile.getPath());
                if (mapView != null) {
                    mapView.loadNetworkAsync(netFile, lanes -> {
                        if (lanes <= 0) setStatusText("Loaded SUMO, but net file missing/empty");
                        else setStatusText("Loaded SUMO, net lanes: " + lanes);

                        // Load bus stops AFTER network is loaded so lane positions are available
                        loadBusStopsForMap();
                    });
                }

                // Populate spawnable edge list
                if (cmbInjectEdge != null && edgeWrapper != null) {
                    List<String> edges = edgeWrapper.getEdgeIDs();
                    cmbInjectEdge.getItems().setAll(edges);
                    if (!edges.isEmpty()) cmbInjectEdge.getSelectionModel().select(0);
                }

                // Populate traffic light list (UI work)
                populateTrafficLights();

                setConnectedUI();
                updateAfterStep();
                if (btnConnect != null) {
                    btnConnect.setDisable(false);
                    btnConnect.setText("Disconnect");
                }
            });
        }, "ConnectSUMO");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @FXML
    public void onStartPause() {
        if (running) {
            stopLoop();
            setConnectedUI();
            trafWrapper.togglePauseSimulation();
            return;
        }
        startLoop();
        setRunningUI();
        trafWrapper.togglePauseSimulation();
    }

    @FXML
    private void onStep() {
        stopLoop();
        if (connector == null || !connector.isConnected()) {
            setStatusText("Status: Not connected");
            return;
        }
        boolean ok = connector.step();
        if (!ok) {
            setStatusText("Status: Step failed");
            return;
        }
        setStatusText("Status: Stepped");
        updateAfterStep();
    }

    /**
     * Called from the JavaFX Application stop() hook to close resources.
     */
    public void shutdown() {
        stopLoop();
        stopConnectionMonitor();
        if (connector != null) {
            connector.disconnect();
        }
        setDisconnectedUI();
    }

    private void startConnectionMonitor() {
        stopConnectionMonitor();
        monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConnectionMonitor");
            t.setDaemon(true);
            return t;
        });

        monitorExecutor.scheduleAtFixedRate(() -> {
            try {
                boolean connected = connector != null && connector.isConnected();
                if (!connected) {
                    Platform.runLater(() -> setStatusText("Status: Disconnected"));
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Monitor thread error", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopConnectionMonitor() {
        if (monitorExecutor != null) {
            monitorExecutor.shutdownNow();
            monitorExecutor = null;
        }
    }
    /**
     * Parse strings for Windows environment
     * @return
     */
    private String resolveSumoBinary() {
        // Prefer SUMO_HOME env, otherwise fall back to common default
        String sumoHome = System.getenv("SUMO_HOME");
        if (sumoHome != null && !sumoHome.isEmpty()) {
            return sumoHome.replaceAll("[/\\\\]+$", "") + "\\\\bin\\\\sumo.exe";
        }
        return "C:\\\\Program Files (x86)\\\\Eclipse\\\\Sumo\\\\bin\\\\sumo.exe";
    }
    /**
     * helper function to load network file
     * @param configPath
     * @return
     */
    private int loadNetworkForMap(String configPath) {
        if (mapView == null) return 0;
        File netFile = resolveNetFile(configPath);
        return mapView.loadNetwork(netFile);
    }

    /**
     * Load bus stop data and update the map view.
     * Runs asynchronously to avoid blocking the UI thread.
     * Called once after connecting to SUMO.
     */
    private void loadBusStopsForMap() {
        if (mapView == null) return;

        // Parse from SUMO config/additional files instead of TraCI.
        // The custom TraCI busstop vars can desync the stream if the varId is unsupported.
        new Thread(() -> {
            List<String[]> busStopData = new ArrayList<>();
            try {
                String configPath = (txtConfigPath != null) ? txtConfigPath.getText().trim() : "";
                File cfgFile = resolveConfigFile(configPath);
                String cfgPath = (cfgFile != null) ? cfgFile.getPath() : configPath;
                List<File> additionalFiles = resolveAdditionalFiles(cfgPath);
                busStopData = parseBusStopsFromAdditionalFiles(additionalFiles);
                LOGGER.info("Parsed " + busStopData.size() + " bus stops from config");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load bus stops for map (from files)", e);
            }

            final List<String[]> finalData = busStopData;
            Platform.runLater(() -> {
                if (mapView != null) {
                    mapView.updateBusStops(finalData);
                }
            });
        }, "BusStopLoader").start();
    }

    private List<File> resolveAdditionalFiles(String configPath) {
        List<File> out = new ArrayList<>();
        if (configPath == null || configPath.isEmpty()) return out;
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(configPath));
            doc.getDocumentElement().normalize();
            NodeList nodes = doc.getElementsByTagName("additional-files");
            if (nodes.getLength() <= 0) return out;

            Element el = (Element) nodes.item(0);
            String value = el.getAttribute("value");
            if (value == null || value.trim().isEmpty()) return out;

            Path cfg = Paths.get(configPath).toAbsolutePath().normalize();
            Path base = cfg.getParent();
            String[] parts = value.split(",");
            for (String p : parts) {
                if (p == null) continue;
                String trimmed = p.trim();
                if (trimmed.isEmpty()) continue;
                File candidate = (base != null) ? base.resolve(trimmed).toFile() : new File(trimmed);
                if (candidate.exists()) {
                    out.add(candidate);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to resolve additional-files from config", e);
        }
        return out;
    }

    private List<String[]> parseBusStopsFromAdditionalFiles(List<File> additionalFiles) {
        List<String[]> out = new ArrayList<>();
        if (additionalFiles == null || additionalFiles.isEmpty()) return out;
        for (File f : additionalFiles) {
            if (f == null || !f.exists()) continue;
            try {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
                doc.getDocumentElement().normalize();
                NodeList stops = doc.getElementsByTagName("busStop");
                for (int i = 0; i < stops.getLength(); i++) {
                    Element s = (Element) stops.item(i);
                    String id = s.getAttribute("id");
                    String laneId = s.getAttribute("lane");
                    if (laneId == null || laneId.isEmpty()) continue;
                    String name = s.hasAttribute("name") ? s.getAttribute("name") : "";

                    double startPos = parseDoubleOrDefault(s.getAttribute("startPos"), 0.0);
                    double endPos = parseDoubleOrDefault(s.getAttribute("endPos"), startPos + 10.0);
                    if (endPos < startPos) {
                        double tmp = startPos;
                        startPos = endPos;
                        endPos = tmp;
                    }

                    String label = (name != null && !name.isEmpty()) ? name : ((id != null && !id.isEmpty()) ? id : "busStop");
                    String stopId = (id != null && !id.isEmpty()) ? id : label;

                    out.add(new String[] {
                        stopId,
                        label,
                        laneId,
                        String.valueOf(startPos),
                        String.valueOf(endPos)
                    });
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to parse bus stops from " + f.getName(), e);
            }
        }
        return out;
    }

    private double parseDoubleOrDefault(String s, double def) {
        if (s == null) return def;
        try {
            String t = s.trim();
            if (t.isEmpty()) return def;
            return Double.parseDouble(t);
        } catch (Exception ignored) {
            return def;
        }
    }

    private File resolveNetFile(String configPath) {
        if (configPath == null || configPath.isEmpty()) return null;
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(configPath));
            doc.getDocumentElement().normalize();
            NodeList netNodes = doc.getElementsByTagName("net-file");
            if (netNodes.getLength() > 0) {
                Element el = (Element) netNodes.item(0);
                String value = el.getAttribute("value");
                Path cfg = Paths.get(configPath).toAbsolutePath().normalize();
                Path base = cfg.getParent();
                File candidate = base.resolve(value).toFile();
                if (candidate.exists()) return candidate;
                // also try relative to CWD in case cfg path was relative without parent
                File alt = Paths.get(value).toFile();
                if (alt.exists()) return alt;
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to resolve net-file from config", e);
        }
        return null;
    }
    /**
     * update map and vehicleRows data
     */
    private void updateMapView() {
        if (mapView == null || connector == null || vehicleWrapper == null) return;

        // If SUMO terminated / TraCI disconnected, don't keep issuing TraCI queries.
        if (!connector.isConnected() || connector.getConnection() == null) {
            setStatusText("Status: Disconnected");
            setDisconnectedUI();
            return;
        }
        // Fetch latest positions and data
        Map<String, Point2D> allPositions = vehicleWrapper.getVehiclePositions(); // gets a new list every time
        Map<String, String> allLaneIds = vehicleWrapper.getVehicleLaneIds(); // gets a new list every time
        // Fetch vehicle angles for realistic orientation rendering
        Map<String, Double> allAngles = vehicleWrapper.getVehicleAngles(); // gets a new list every time
        // Fetch vehicle types for rendering appropriate vehicle shapes (car, bus, motorbike, etc.)
        Map<String, String> allTypes = vehicleWrapper.getVehicleTypes(); // gets a new list every time, not efficient
        List<VehicleRow> allRows = vehicleWrapper.getVehicleRows(); // also update vehicleRows every time, not efficient

        Map<String, Color> colorMap = new HashMap<>();
        Map<String, Point2D> filteredPositions = new HashMap<>();
        Map<String, String> filteredLaneIds = new HashMap<>();
        Map<String, Double> filteredAngles = new HashMap<>();
        Map<String, String> filteredTypes = new HashMap<>();
        List<VehicleRow> filteredRows = new ArrayList<>();

        // Cache for edge mean speeds (only used when congestion filter enabled)
        Map<String, Double> edgeMeanSpeedCache = new HashMap<>();

        boolean filterColor = (chkFilterRed != null) && chkFilterRed.isSelected();
        boolean filterSpeed = (chkFilterSpeed != null) && chkFilterSpeed.isSelected();
        boolean filterCongested = (chkFilterCongested != null) && chkFilterCongested.isSelected();
        Color targetColor = (cpFilterColor != null) ? cpFilterColor.getValue() : Color.RED;
        if (targetColor == null) targetColor = Color.RED;

        // ---- Stats collection for Charts tab (uses ALL vehicles, not filtered set) ----
        double sumSpeed = 0.0;
        int total = 0;
        // Note: this is used for the charts, not filtering.
        int[] speedBuckets = new int[]{0, 0, 0, 0};

        Map<String, Integer> colorBuckets = new HashMap<>();

        for (VehicleRow row : allRows) {
            total++;
            double s = row.getSpeed();
            sumSpeed += s;
            if (s < 2.0) speedBuckets[0]++;
            else if (s < 5.0) speedBuckets[1]++;
            else if (s < 10.0) speedBuckets[2]++;
            else speedBuckets[3]++;

            // Filter by color (user-selected)
            if (filterColor) {
                Color c = row.getColor();
                if (!isSimilarColor(c, targetColor, 0.18)) {
                    continue;
                }
            }
            // Filter by speed (> 10 m/s)
            if (filterSpeed) {
                if (row.getSpeed() <= 10.0) {
                    continue;
                }
            }
            // Filter by congestion (speed < 5 m/s) - example logic for "congested"
            if (filterCongested) {
                String edgeId = row.getEdge();
                boolean congested = false;

                // Prefer edge-level mean speed (more like a "congested edge" definition)
                if (connector != null && connector.isConnected() && edgeId != null && !edgeId.isEmpty()) {
                    Double mean = edgeMeanSpeedCache.get(edgeId);
                    if (mean == null && !edgeMeanSpeedCache.containsKey(edgeId)) {
                        try {
                            mean = (double) edgeWrapper.getLastStepMeanSpeed(edgeId);
                        } catch (Exception ignored) {
                            mean = null;
                        }
                        edgeMeanSpeedCache.put(edgeId, mean);
                    }
                    if (mean != null && mean >= 0.0) {
                        congested = mean <= 5.0;
                    }
                }

                // Fallback: vehicle-level speed heuristic
                if (!congested) {
                    congested = row.getSpeed() < 5.0;
                }
                if (!congested) {
                    continue;
                }
            }

            // for each vehicleRow that satisfies filter conditions, add it to the filter list
            filteredRows.add(row);
            colorMap.put(row.getId(), row.getColor());

            // Pie chart distribution based on displayed rows.
            String key = colorKey(row.getColor());
            colorBuckets.merge(key, 1, Integer::sum);

            Point2D pos = (allPositions != null) ? allPositions.get(row.getId()) : null;
            if (pos != null) {
                filteredPositions.put(row.getId(), pos);
            }
            String laneId = (allLaneIds != null) ? allLaneIds.get(row.getId()) : null;
            if (laneId != null && !laneId.isEmpty()) {
                filteredLaneIds.put(row.getId(), laneId);
            }
            // Include vehicle angle for orientation in rendering
            Double angle = (allAngles != null) ? allAngles.get(row.getId()) : null;
            if (angle != null) {
                filteredAngles.put(row.getId(), angle);
            }
            // Include vehicle type for shape rendering (car, bus, motorbike, etc.)
            String vehType = (allTypes != null) ? allTypes.get(row.getId()) : null;
            if (vehType != null && !vehType.isEmpty()) {
                filteredTypes.put(row.getId(), vehType);
            }
        }

        // Update map (only filtered vehicles) with angles and types for realistic rendering
        if (filteredPositions.isEmpty() && allRows.isEmpty() && allPositions != null && !allPositions.isEmpty()) {
            // If row fetching fails for any reason, still render positions so vehicles remain visible.
            mapView.updateVehicles(allPositions, Collections.emptyMap(), allLaneIds, allAngles, allTypes);
        } else {
            mapView.updateVehicles(filteredPositions, colorMap, filteredLaneIds, filteredAngles, filteredTypes);
        }

        // Overlay traffic-light stop lines (R/Y/G) so it's obvious why vehicles stop.
        Map<String, Color> laneSignalMap = buildLaneSignalColorMap();
        mapView.updateTrafficSignals(laneSignalMap);

        // Finalize charts snapshot for this frame.
        double avgSpeed = (total > 0) ? (sumSpeed / (double) total) : 0.0;
        lastChartSnapshot = new ChartSnapshot(total, avgSpeed, speedBuckets);

        // Update table
        vehicleData.setAll(filteredRows);

        if (vehicleTable != null) {
            vehicleTable.refresh();
        }

        updateVehicleColorPieThrottled(colorBuckets, null);
    }

    /**
     * build a map for traffic light states in different lanes
     * @return
     */
    private Map<String, Color> buildLaneSignalColorMap() {
        if (connector == null || !connector.isConnected() || connector.getConnection() == null) {
            return Collections.emptyMap();
        }
        try {
            List<String> ids = new ArrayList<>();
            ids = trafWrapper.getTrafficLightIds();
            if (ids.isEmpty()) return Collections.emptyMap();

            Map<String, Integer> lanePriority = new HashMap<>();
            Map<String, Color> laneColor = new HashMap<>();
            for (String tlId : ids) {
                // draw the TL marker in the correct spot with the correct color
                if (tlId == null || tlId.isEmpty()) continue;
                String state = trafWrapper.getTrafficLightState(tlId);
                if (state == null || state.isEmpty()) continue;

                List<SumoLink> links = trafficLightLinksCache.get(tlId);
                if (links == null) {
                    links = trafWrapper.getTrafficLightLinks(tlId);
                    trafficLightLinksCache.put(tlId, links);
                }
                if (links == null || links.isEmpty()) continue;

                int limit = Math.min(state.length(), links.size());
                for (int i = 0; i < limit; i++) {
                    SumoLink link = links.get(i);
                    if (link == null) continue;
                    String laneId = (link.notInternalLane != null && !link.notInternalLane.isEmpty())
                            ? link.notInternalLane
                            : link.from;
                    if (laneId == null || laneId.isEmpty()) continue;

                    char ch = state.charAt(i);
                    int prio = signalPriority(ch);
                    Integer existing = lanePriority.get(laneId);
                    if (existing == null || prio > existing) {
                        lanePriority.put(laneId, prio);
                        laneColor.put(laneId, signalColorForState(ch));
                    }
                }
            }
            return laneColor;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    // 3 is the highest, 0 is the lowest
    private static int signalPriority(char state) {
        switch (Character.toLowerCase(state)) {
            case 'r':
                return 3;
            case 'y':
                return 2;
            case 'g':
                return 1;
            default:
                return 0;
        }
    }

    private static Color signalColorForState(char state) {
        switch (Character.toLowerCase(state)) {
            case 'g':
                return Color.LIMEGREEN;
            case 'y':
                return Color.GOLD;
            case 'r':
                return Color.RED;
            default:
                return Color.GRAY;
        }
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
    private File resolveConfigFile(String path) {
        if (path == null || path.isEmpty()) return null;
        File f = Paths.get(path).toAbsolutePath().normalize().toFile();

        if (f.exists()) return f;
        // try also if path was relative to project parent (java-code/..)
        f = Paths.get("..").resolve(path).toAbsolutePath().normalize().toFile();
        if (f.exists()) return f;
        return null;
    }

    private void startLoop() {
        if (loopTimer == null) {
            loopTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (!running) return;
                    if (lastStepNs == 0) {
                        lastStepNs = now;
                        return;
                    }
                    double speedFactor = (sliderSpeed != null) ? sliderSpeed.getValue() : 1.0;
                    double stepIntervalNs = (stepLengthSeconds / Math.max(sliderSpeed.getMin(), speedFactor)) * 1_000_000_000.0;
                    if ((now - lastStepNs) >= stepIntervalNs) {
                        doStep();
                        lastStepNs = now;
                    }
                }
            };
        }
        running = true;
        lastStepNs = 0;
        loopTimer.start();
    }

    private void stopLoop() {
        running = false;
        if (loopTimer != null) {
            loopTimer.stop();
        }
    }

    private void doStep() {
        if (connector == null || !connector.isConnected()) {
            stopLoop();
            setStatusText("Status: Not connected");
            setDisconnectedUI();
            return;
        }
        boolean ok = connector.step();
        if (!ok) {
            stopLoop();
            setStatusText("Status: Disconnected");
            setDisconnectedUI();
            return;
        }
        setStatusText("Status: Running");
        updateAfterStep();

        // Also keep traffic light info in sync
        updateTrafficLightUI();
    }

    // ------- Traffic light helpers -------

    private void populateTrafficLights() {
        if (connector == null || !connector.isConnected() || cmbTrafficLight == null) return;
        try {
            trafficLightPhaseCountCache.clear();
            List<String> ids = trafWrapper.getTrafficLightIds();
            cmbTrafficLight.getItems().setAll(ids);
            if (!ids.isEmpty()) {
                cmbTrafficLight.getSelectionModel().select(0);
                updateTrafficLightUI();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to populate traffic lights", e);
        }
    }

    /**
     * get the number of phases for this TL
     * @param id
     * @return
     */
    private int getTLPhaseCount(String id) {
        if (id == null || id.isEmpty() || connector == null || !connector.isConnected()) return -1;

        // if phase count already exists, just retrieve it
        Integer cached = trafficLightPhaseCountCache.get(id);
        if (cached != null) return cached;

        // else use trafWrapper, fallback method
        int count = trafWrapper.getTrafficLightPhaseCount(id);
        trafficLightPhaseCountCache.put(id, count);
        return count;
    }

    /**
     * supply TL state to each TL id, then update the UI accordingly
     */
    private void updateTrafficLightUI() {
        if (connector == null || !connector.isConnected() || cmbTrafficLight == null) return;
        String tlid = cmbTrafficLight.getValue();
        if (tlid == null || tlid.isEmpty()) return;
        try {
            String state = trafWrapper.getTrafficLightState(tlid); // RGB state of the TL
            int phaseIndex = trafWrapper.getPhaseIndex(tlid); // phase index in the phase cycle
            double dur = trafWrapper.getPhaseDuration(tlid); // in seconds

            if (lblPhaseInfo != null) {
                lblPhaseInfo.setText("Phase " + phaseIndex + ": " + state);
            }
            if (txtPhaseDuration != null) {
                txtPhaseDuration.setText(String.format(Locale.US, "%.1f", dur));
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to update traffic light UI", e);
        }
    }

    @FXML
    private void onTrafficLightPrevPhase() {
        changeTrafficLightPhase(-1);
    }

    @FXML
    private void onTrafficLightNextPhase() {
        changeTrafficLightPhase(1);
    }
    // helper function for changing TL phase
    private void changeTrafficLightPhase(int delta) {
        if (connector == null || !connector.isConnected() || cmbTrafficLight == null) return;
        String id = cmbTrafficLight.getValue();
        if (id == null || id.isEmpty()) return;
        try {
            int curPhase = trafWrapper.getPhaseIndex(id);
            int phaseCount = getTLPhaseCount(id);
            LOGGER.info("Phase count for TL " + id + ": " + phaseCount);

            int newPhase;
            if (phaseCount > 0) {
                int raw = curPhase + delta;
                newPhase = (raw + phaseCount) % phaseCount; // safe wrap for out-of-bound index
            } else {
                newPhase = Math.max(0, curPhase + delta);
            }

            trafWrapper.setPhaseIndex(id, newPhase);
            LOGGER.info("Changing to phase " + newPhase);

            updateTrafficLightUI();
        } catch (Exception e) {
            // Don't spam stack traces for user-driven UI actions.
            setStatusText("Traffic light phase change failed");
        }
    }

    @FXML
    private void onTrafficLightApply() {
        if (connector == null || !connector.isConnected() || cmbTrafficLight == null || txtPhaseDuration == null) return;
        String id = cmbTrafficLight.getValue();
        if (id == null || id.isEmpty()) return;
        try {
            double dur = Double.parseDouble(txtPhaseDuration.getText().trim());
            trafWrapper.setRemainingPhaseDuration(id, dur);
            updateTrafficLightUI();
        } catch (NumberFormatException ignored) {
            // ignore invalid input
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply traffic light duration", e);
        }
    }

    @FXML
    private void onInject() {
        if (connector == null || !connector.isConnected()) {
            setStatusText("Status: Not connected");
            return;
        }

        // edge where new vehicles are injected
        String edge = "";
        if (cmbInjectEdge != null) {
             edge = cmbInjectEdge.getValue();
             if (edge == null || edge.isEmpty()) edge = cmbInjectEdge.getEditor().getText();
        }
        if (edge == null || edge.isEmpty()) {
            setStatusText("Status: Edge ID required");
            return;
        }

        // number of injected vehicles
        int count = 1;
        try {
            if (txtInjectCount != null) count = Integer.parseInt(txtInjectCount.getText().trim());
        } catch (NumberFormatException e) {}

        // color of vehicles
        Color color = (cpInjectColor != null) ? cpInjectColor.getValue() : Color.RED;
        if (color == null) color = Color.RED;

        // Use edge as route ID for now
        String routeId = edge;

        if (running) {
            for (int i = 0; i < count; i++) {
                pendingInjections.addLast(new PendingInjection(routeId, color));
            }
            processPendingInjections(System.nanoTime());
            setStatusText("Status: Queued " + count + " vehicles");
            pendingMapRefresh = true;
            return;
        }

        // If paused, keep legacy behavior: inject immediately.
        for (int i = 0; i < count; i++) {
            String vehId = "inj_" + (++injectSeq);
            if (vehicleWrapper != null) {
                vehicleWrapper.addVehicle(vehId, routeId, DEFAULT_INJECT_SPEED_MS, color);
            }
        }
        setStatusText("Status: Injected " + count + " vehicles");
        updateMapView();
    }

    @FXML
    public void handlePdfExport() {
        if (!canExportNow() || vehicleWrapper == null || trafWrapper == null) {
            setStatusText("Status: Pause + connect to export");
            return;
        }
        // open a FileChooser to let the user select the where he wants to save the pdf
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF Report");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("pdfExport");

        // Use the primary stage to show the dialog
        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try {
                // Prepare the data for Export
                List<String> currentData = new ArrayList<>();
                // Add your actual simulation stats here!
                List<String> vehicleData = vehicleWrapper.getVehicleData();
                List<String> tlData = trafWrapper.getTrafficLightData();
                int maxRow = Math.max(vehicleData.size(), tlData.size());

                for(int j = 0; j < maxRow; j++) {
                    // Vehicle Data output: (ID, Color, Speed, PosX, PosY, Edge)
                    String vehicle = (j < vehicleData.size()) ? vehicleData.get(j): ",,,,,,"; // ; for empty space
                    // TrafficLight Data output: (ID, Phase, Index)
                    String tl = (j < tlData.size()) ? tlData.get(j): ",,";
                    currentData.add(j + "," + vehicle + tl);
                }
                // Export the Data
                Export exporter = new Export();
                // pdf export needs more data for metrics and stats
                exporter.createPDF(file.getAbsolutePath(), "Sumo Simulation Report", currentData, this);
                System.out.println("PDF successfully created: " + file.getAbsolutePath());
                LOGGER.fine("Sumo-PDF Export successful saved in: " + file.getAbsolutePath());
            } catch (Exception e) {
                LOGGER.warning("Failed to export PDF from Sumo-UI: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleCSVExport() {
        if (!canExportNow() || vehicleWrapper == null || trafWrapper == null) {
            setStatusText("Status: Pause + connect to export");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV Report");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName("csvExport");

        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try {
                List<String> currentData = new ArrayList<>();

                // Export the Data
                List<String> vehicleData = vehicleWrapper.getVehicleData();
                List<String> tlData = trafWrapper.getTrafficLightData();
                int maxRow = Math.max(vehicleData.size(), tlData.size());

                for(int j = 0; j < maxRow; j++) {
                    // Vehicle Data output: (ID, Color, Speed, PosX, PosY, Edge)
                    String vehicle = (j < vehicleData.size()) ?  vehicleData.get(j): ",,,,,,"; // ; for empty space
                    // TrafficLight Data output: (ID, Phase, Index)
                    String tl = (j < tlData.size()) ? tlData.get(j): ",,";
                    currentData.add(j + "," + vehicle + tl);
                }
                Export exporter = new Export();
                exporter.createCSV(file.getAbsolutePath(), currentData);
                System.out.println("CSV successfully created: " + file.getAbsolutePath());
                LOGGER.fine("Sumo-CSV Export successful saved in: " + file.getAbsolutePath());
            } catch (Exception e) {
                LOGGER.warning("Failed to export CSV from Sumo-UI: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    /**
     * Returns the 4 graphs to include in PDF export (in display order).
     * Keys are human-friendly titles used as captions.
     */
    public Map<String, javafx.scene.Node> getExportGraphs() {
        Map<String, javafx.scene.Node> out = new LinkedHashMap<>();
        if (vehicleCountChart != null) out.put("Vehicle Count", vehicleCountChart);
        if (avgSpeedChart != null) out.put("Average Speed", avgSpeedChart);
        if (speedDistChart != null) out.put("Speed Distribution", speedDistChart);
        if (vehicleColorPie != null) out.put("Vehicle Color Density", vehicleColorPie);
        return out;
    }

    /**
     * Export-friendly snapshot of the vehicle color pie (Top3 + Others).
     * This avoids JavaFX snapshot issues when the pie chart lives in a non-selected Tab.
     */
    public List<PieSliceExport> getVehicleColorPieExport() {
        List<PieSliceExport> out = new ArrayList<>();

        // Prefer the stable slots (keeps ordering consistent with the UI pie).
        if (vehicleColorPieSlotsInitialized) {
            double total = 0.0;
            for (int i = 0; i < vehicleColorPieSlots.length; i++) {
                PieChart.Data d = vehicleColorPieSlots[i];
                if (d == null) continue;
                double v = d.getPieValue();
                if (v <= 0.0) continue;
                String name = d.getName();
                if (name == null || name.isEmpty()) continue;
                String css = vehicleColorPieSlotCss[i];
                out.add(new PieSliceExport(name, v, css));
                total += v;
            }
            if (total > 0.0 && !out.isEmpty()) return out;
            out.clear();
        }

        // Fallback: read from chart data if slots aren't initialized.
        if (vehicleColorPie != null && vehicleColorPie.getData() != null) {
            for (PieChart.Data d : vehicleColorPie.getData()) {
                if (d == null) continue;
                double v = d.getPieValue();
                if (v <= 0.0) continue;
                String name = d.getName();
                if (name == null || name.isEmpty()) continue;
                out.add(new PieSliceExport(name, v, null));
            }
        }

        if (!out.isEmpty()) return out;

        // Final fallback: compute from the currently displayed table rows.
        // Keeps the same rule: Top 3 + Others.
        List<VehicleRow> rows = null;
        if (vehicleTable != null && vehicleTable.getItems() != null) {
            rows = new ArrayList<>(vehicleTable.getItems());
        } else if (vehicleData != null) {
            rows = new ArrayList<>(vehicleData);
        }
        if (rows == null || rows.isEmpty()) return out;

        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> cssByKey = new HashMap<>();
        for (VehicleRow r : rows) {
            if (r == null) continue;
            Color c = r.getColor();
            if (c == null) continue;
            String key = c.toString(); // 0xrrggbbaa
            counts.put(key, counts.getOrDefault(key, 0) + 1);
            if (!cssByKey.containsKey(key)) {
                String css = null;
                if (key != null && key.startsWith("0x") && key.length() == 10) {
                    css = "#" + key.substring(2, 8);
                }
                cssByKey.put(key, css);
            }
        }
        if (counts.isEmpty()) return out;

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int kept = 0;
        int others = 0;
        for (Map.Entry<String, Integer> e : sorted) {
            if (kept < MAX_COLOR_SLICES) {
                String key = e.getKey();
                int v = e.getValue();
                String css = cssByKey.get(key);
                out.add(new PieSliceExport(key, v, css));
                kept++;
            } else {
                others += e.getValue();
            }
        }
        if (others > 0) {
            out.add(new PieSliceExport("Others", others, "#9E9E9E"));
        }
        return out;
    }


}
