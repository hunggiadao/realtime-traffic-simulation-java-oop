import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ColorPicker;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.animation.AnimationTimer;
import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.cmd.Edge;
import javafx.application.Platform;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UI {
    private static final Logger LOGGER = Logger.getLogger(UI.class.getName());

    // Top toolbar
    @FXML private Button btnOpenConfig;
    @FXML private Button btnConnect;
    @FXML private Button btnStart;
    @FXML private Button btnPause;
    @FXML private Button btnStep;
    @FXML private Slider sliderSpeed;

    // Left Simulation tab
    @FXML private TextField txtConfigPath;
    @FXML private TextField txtStepMs;

    // Inject tab
    @FXML private ComboBox<String> cmbInjectEdge;
    @FXML private TextField txtInjectCount;
    @FXML private ColorPicker cpInjectColor;
    @FXML private TextField txtInjectSpeed;
    @FXML private Button btnInject;

    // Filter tab
    @FXML private javafx.scene.control.CheckBox chkFilterRed;
    @FXML private javafx.scene.control.CheckBox chkFilterSpeed;
    @FXML private javafx.scene.control.CheckBox chkFilterCongested;

    // Right: chart + table
    @FXML private LineChart<Number, Number> vehicleCountChart;
    @FXML private NumberAxis vehicleCountChartXAxis;
    @FXML private NumberAxis vehicleCountChartYAxis;

    @FXML private TableView<VehicleRow> vehicleTable;
    @FXML private TableColumn<VehicleRow, String> colId;
    @FXML private TableColumn<VehicleRow, Number> colSpeed;
    @FXML private TableColumn<VehicleRow, String> colEdge;

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
    private final ObservableList<VehicleRow> vehicleData = FXCollections.observableArrayList();
    private XYChart.Series<Number, Number> vehicleSeries;
    private MapView mapView;

    // SUMO / TraCI
    private TraCIConnector connector;
    private VehicleWrapper vehicleWrapper;
    private int stepLengthMs = 100;
    private double stepLengthSeconds = 0.1;
    private AnimationTimer loopTimer;
    private boolean running = false;
    private long lastStepNs = 0;

    // Settings + background monitor thread (explicit extra thread beyond main/JavaFX)
    private final UserSettings userSettings = new UserSettings();
    private ScheduledExecutorService monitorExecutor;

    // Cache phase counts per traffic light (used to safely wrap phase +/-)
    private final java.util.Map<String, Integer> trafficLightPhaseCountCache = new java.util.HashMap<>();

    public UI() {
        // called when FXML is loaded
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
        if (colId != null && colSpeed != null && colEdge != null) {
            colId.setCellValueFactory(data -> data.getValue().idProperty());
            colSpeed.setCellValueFactory(data -> data.getValue().speedProperty());
            colEdge.setCellValueFactory(data -> data.getValue().edgeProperty());
        }
        if (vehicleTable != null) {
            vehicleTable.setItems(vehicleData);
        }

        // Chart setup
        if (vehicleCountChart != null) {
            vehicleSeries = new XYChart.Series<>();
            vehicleSeries.setName("Vehicle count");
            vehicleCountChart.getData().add(vehicleSeries);
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
            txtStepMs.setText(userSettings.getString("stepMs", "100"));
        }
        if (sliderSpeed != null) {
            sliderSpeed.setMin(0.25);
            sliderSpeed.setMax(4.0);
            sliderSpeed.setValue(1.0);
        }

        if (cpInjectColor != null && cpInjectColor.getValue() == null) {
            cpInjectColor.setValue(javafx.scene.paint.Color.RED);
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

        setDisconnectedUI();
    }

    // UI state helpers
    private void setDisconnectedUI() {
        if (lblStatus != null) lblStatus.setText("Status: Disconnected");
        if (btnConnect != null) btnConnect.setDisable(false);
        if (btnStart != null) btnStart.setDisable(true);
        if (btnPause != null) btnPause.setDisable(true);
        if (btnStep != null) btnStep.setDisable(true);
    }

    private void setConnectedUI() {
        if (lblStatus != null) lblStatus.setText("Status: Connected");
        if (btnConnect != null) btnConnect.setDisable(true);
        if (btnStart != null) btnStart.setDisable(false);
        if (btnPause != null) btnPause.setDisable(true);
        if (btnStep != null) btnStep.setDisable(false);
    }

    private void setRunningUI() {
        if (lblStatus != null) lblStatus.setText("Status: Running");
        if (btnStart != null) btnStart.setDisable(true);
        if (btnPause != null) btnPause.setDisable(false);
        if (btnStep != null) btnStep.setDisable(true);
    }

    public void setStatusText(String text) {
        if (lblStatus != null) lblStatus.setText(text);
    }

    // Update after each step
    private void updateAfterStep() {
        if (connector == null || vehicleWrapper == null) return;

        int step = connector.getCurrentStep();
        double simTime = connector.getSimTimeSeconds();
        int vehicleCount = vehicleWrapper.getVehicleCount();

        if (lblStep != null) {
            lblStep.setText("Step: " + step);
        }
        if (lblSimTime != null) {
            lblSimTime.setText(String.format("Sim time: %.1f s", simTime));
        }
        if (lblVehicles != null) {
            lblVehicles.setText("Vehicles: " + vehicleCount);
        }

        if (vehicleSeries != null) {
            vehicleSeries.getData().add(new XYChart.Data<>(step, vehicleCount));
        }

        updateMapView();
    }

    @FXML
    private void onOpenConfig() {
        if (btnOpenConfig == null) return;
        Window window = btnOpenConfig.getScene().getWindow();
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
    private void onConnect() {
        // Get config path and step length from UI
        String configPath = (txtConfigPath != null) ? txtConfigPath.getText().trim() : "..\\SumoConfig\\G.sumocfg";

        try {
            if (txtStepMs != null) {
                stepLengthMs = Integer.parseInt(txtStepMs.getText().trim());
            }
        } catch (NumberFormatException e) {
            stepLengthMs = 100;
            txtStepMs.setText("100");
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

        connector = new TraCIConnector(sumoBinary, cfgFile.getPath(), stepLengthSeconds);
        boolean ok = connector.connect();
        if (!ok) {
            setStatusText("Status: Connection failed");
            return;
        }

        startConnectionMonitor();

        vehicleWrapper = new VehicleWrapper(connector);
        int lanes = loadNetworkForMap(cfgFile.getPath());
        if (lanes <= 0) {
            setStatusText("Loaded SUMO, but net file missing/empty");
        } else {
            setStatusText("Loaded SUMO, net lanes: " + lanes);
        }

        // Populate edge list
        java.util.List<String> edges = connector.getEdgeIds();
        if (cmbInjectEdge != null) {
            cmbInjectEdge.getItems().setAll(edges);
            if (!edges.isEmpty()) cmbInjectEdge.getSelectionModel().select(0);
        }

		// Populate traffic light list
		populateTrafficLights();

        setConnectedUI();
        updateAfterStep(); // initial values (step 0, 0s)
    }

    @FXML
    private void onStart() {
        // treat Start as "do one step" for now.
        startLoop();
    }

    @FXML
    private void onPause() {
        stopLoop();
        setConnectedUI();
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

    private String resolveSumoBinary() {
        // Prefer SUMO_HOME env, otherwise fall back to common default
        String sumoHome = System.getenv("SUMO_HOME");
        if (sumoHome != null && !sumoHome.isEmpty()) {
            return sumoHome.replaceAll("[/\\\\]+$", "") + "\\\\bin\\\\sumo.exe";
        }
        return "C:\\\\Program Files (x86)\\\\Eclipse\\\\Sumo\\\\bin\\\\sumo.exe";
    }

    private int loadNetworkForMap(String configPath) {
        if (mapView == null) return 0;
        File netFile = resolveNetFile(configPath);
        return mapView.loadNetwork(netFile);
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
			LOGGER.log(java.util.logging.Level.FINE, "Failed to resolve net-file from config", e);
        }
        return null;
    }

    private void updateMapView() {
        if (mapView == null || vehicleWrapper == null) return;
        // Fetch latest positions and data
        java.util.Map<String, Point2D> allPositions = vehicleWrapper.getVehiclePositions();
        java.util.List<VehicleRow> allRows = vehicleWrapper.getVehicleRows();
        java.util.Map<String, javafx.scene.paint.Color> colorMap = new java.util.HashMap<>();
        java.util.Map<String, Point2D> filteredPositions = new java.util.HashMap<>();
        java.util.List<VehicleRow> filteredRows = new java.util.ArrayList<>();

        // Cache for edge mean speeds (only used when congestion filter enabled)
        java.util.Map<String, Double> edgeMeanSpeedCache = new java.util.HashMap<>();

        boolean filterRed = (chkFilterRed != null) && chkFilterRed.isSelected();
        boolean filterSpeed = (chkFilterSpeed != null) && chkFilterSpeed.isSelected();
        boolean filterCongested = (chkFilterCongested != null) && chkFilterCongested.isSelected();

        for (VehicleRow row : allRows) {
            // Filter by color (Red)
            if (filterRed) {
                javafx.scene.paint.Color c = row.getColor();
                // Simple check for "red-ish" color
                if (c.getRed() < 0.8 || c.getGreen() > 0.2 || c.getBlue() > 0.2) {
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
                            Object m = connector.getConnection().do_job_get(Edge.getLastStepMeanSpeed(edgeId));
                            mean = (m instanceof Number) ? ((Number) m).doubleValue() : null;
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
            filteredRows.add(row);
			colorMap.put(row.getId(), row.getColor());
			Point2D pos = (allPositions != null) ? allPositions.get(row.getId()) : null;
			if (pos != null) {
				filteredPositions.put(row.getId(), pos);
			}
        }

		// Update map (only filtered vehicles)
		mapView.updateVehicles(filteredPositions, colorMap);

		// Update table
		vehicleData.setAll(filteredRows);
        if (vehicleTable != null) {
            vehicleTable.refresh();
        }
    }

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
                    double stepIntervalNs = (stepLengthSeconds / Math.max(0.1, speedFactor)) * 1_000_000_000.0;
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
        setRunningUI();
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
            setConnectedUI();
            return;
        }
        boolean ok = connector.step();
        if (!ok) {
            stopLoop();
            setStatusText("Status: Step failed");
            setConnectedUI();
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
            Object resp = connector.getConnection().do_job_get(Trafficlight.getIDList());
            java.util.List<String> ids = new java.util.ArrayList<>();
            if (resp instanceof String[]) {
                for (String s : (String[]) resp) ids.add(s);
            } else if (resp instanceof java.util.List<?>) {
                for (Object o : (java.util.List<?>) resp) ids.add(String.valueOf(o));
            }
            cmbTrafficLight.getItems().setAll(ids);
            if (!ids.isEmpty()) {
                cmbTrafficLight.getSelectionModel().select(0);
                updateTrafficLightUI();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to populate traffic lights", e);
        }
    }

    private int getTrafficLightPhaseCount(String id) {
        if (id == null || id.isEmpty() || connector == null || !connector.isConnected()) return -1;
        Integer cached = trafficLightPhaseCountCache.get(id);
        if (cached != null) return cached;
        try {
            Object def = connector.getConnection().do_job_get(Trafficlight.getCompleteRedYellowGreenDefinition(id));
            int count = -1;

            if (def instanceof de.tudresden.sumo.objects.SumoTLSProgram) {
                de.tudresden.sumo.objects.SumoTLSProgram p = (de.tudresden.sumo.objects.SumoTLSProgram) def;
                if (p.phases != null) count = p.phases.size();
            } else if (def instanceof java.util.List<?>) {
                // Some SUMO networks may return a list of programs; take the first one.
                java.util.List<?> list = (java.util.List<?>) def;
                if (!list.isEmpty() && list.get(0) instanceof de.tudresden.sumo.objects.SumoTLSProgram) {
                    de.tudresden.sumo.objects.SumoTLSProgram p = (de.tudresden.sumo.objects.SumoTLSProgram) list.get(0);
                    if (p.phases != null) count = p.phases.size();
                }
            }

            trafficLightPhaseCountCache.put(id, count);
            return count;
        } catch (Exception e) {
            trafficLightPhaseCountCache.put(id, -1);
            return -1;
        }
    }

    private void updateTrafficLightUI() {
        if (connector == null || !connector.isConnected() || cmbTrafficLight == null) return;
        String id = cmbTrafficLight.getValue();
        if (id == null || id.isEmpty()) return;
        try {
            Object stateObj = connector.getConnection().do_job_get(Trafficlight.getRedYellowGreenState(id));
            Object phaseObj = connector.getConnection().do_job_get(Trafficlight.getPhase(id));
            Object durObj = connector.getConnection().do_job_get(Trafficlight.getPhaseDuration(id));
            String state = String.valueOf(stateObj);
            int phase = (phaseObj instanceof Number) ? ((Number) phaseObj).intValue() : 0;
            double dur = (durObj instanceof Number) ? ((Number) durObj).doubleValue() : 0.0;
            if (lblPhaseInfo != null) {
                lblPhaseInfo.setText("Phase " + phase + ": " + state);
            }
            if (txtPhaseDuration != null) {
                txtPhaseDuration.setText(String.format(java.util.Locale.US, "%.1f", dur));
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

    private void changeTrafficLightPhase(int delta) {
        if (connector == null || !connector.isConnected() || cmbTrafficLight == null) return;
        String id = cmbTrafficLight.getValue();
        if (id == null || id.isEmpty()) return;
        try {
            Object phaseObj = connector.getConnection().do_job_get(Trafficlight.getPhase(id));
            int phase = (phaseObj instanceof Number) ? ((Number) phaseObj).intValue() : 0;
            int phaseCount = getTrafficLightPhaseCount(id);
            int newPhase;
            if (phaseCount > 0) {
                int raw = phase + delta;
                newPhase = ((raw % phaseCount) + phaseCount) % phaseCount; // safe wrap for negatives
            } else {
                newPhase = Math.max(0, phase + delta);
            }

            connector.getConnection().do_job_set(Trafficlight.setPhase(id, newPhase));
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
            connector.getConnection().do_job_set(Trafficlight.setPhaseDuration(id, dur));
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

        String edge = "";
        if (cmbInjectEdge != null) {
             edge = cmbInjectEdge.getValue();
             if (edge == null || edge.isEmpty()) edge = cmbInjectEdge.getEditor().getText();
        }
        
        if (edge == null || edge.isEmpty()) {
            setStatusText("Status: Edge ID required");
            return;
        }

        int count = 1;
        try {
            if (txtInjectCount != null) count = Integer.parseInt(txtInjectCount.getText().trim());
        } catch (NumberFormatException e) {}

        double speed = -1;
        try {
            if (txtInjectSpeed != null) speed = Double.parseDouble(txtInjectSpeed.getText().trim());
        } catch (NumberFormatException e) {}

        javafx.scene.paint.Color color = (cpInjectColor != null) ? cpInjectColor.getValue() : javafx.scene.paint.Color.YELLOW;

        // Use edge as route ID for now
        String routeId = edge; 

        for (int i = 0; i < count; i++) {
            String vehId = "inj_" + System.currentTimeMillis() + "_" + i;
            vehicleWrapper.addVehicle(vehId, routeId, speed, color);
        }
        setStatusText("Status: Injected " + count + " vehicles");

        // Refresh table/map immediately (even if the vehicle is inserted on the next sim step,
        // pending updates like color will be applied on refresh).
        updateMapView();
    }
}
