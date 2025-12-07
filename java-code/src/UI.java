import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
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

public class UI {

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
    @FXML private TextField txtInjectEdge;
    @FXML private TextField txtInjectCount;
    @FXML private ColorPicker cpInjectColor;
    @FXML private TextField txtInjectSpeed;
    @FXML private Button btnInject;

    // Right: chart + table
    @FXML private LineChart<Number, Number> vehicleCountChart;
    @FXML private NumberAxis vehicleCountChartXAxis;
    @FXML private NumberAxis vehicleCountChartYAxis;

    @FXML private TableView<VehicleRow> vehicleTable;
    @FXML private TableColumn<VehicleRow, String> colId;
    @FXML private TableColumn<VehicleRow, Number> colSpeed;
    @FXML private TableColumn<VehicleRow, String> colEdge;

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

    public UI() {
        // called when FXML is loaded
    }

    @FXML
    private void initialize() {
        System.out.println("UI initialized");

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
        }
        if (txtStepMs != null) {
            txtStepMs.setText("100");
        }
        if (sliderSpeed != null) {
            sliderSpeed.setMin(0.25);
            sliderSpeed.setMax(4.0);
            sliderSpeed.setValue(1.0);
        }

        if (mapPane != null) {
            mapView = new MapView();
            mapView.prefWidthProperty().bind(mapPane.widthProperty());
            mapView.prefHeightProperty().bind(mapPane.heightProperty());
            mapPane.getChildren().clear();
            mapPane.getChildren().add(mapView);
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

        // Path to SUMO (headless calculation only, render inside JavaFX)
        String sumoBinary = resolveSumoBinary();

        connector = new TraCIConnector(sumoBinary, cfgFile.getPath(), stepLengthSeconds);
        boolean ok = connector.connect();
        if (!ok) {
            setStatusText("Status: Connection failed");
            return;
        }

        vehicleWrapper = new VehicleWrapper(connector);
        int lanes = loadNetworkForMap(cfgFile.getPath());
        if (lanes <= 0) {
            setStatusText("Loaded SUMO, but net file missing/empty");
        } else {
            setStatusText("Loaded SUMO, net lanes: " + lanes);
        }

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
        if (connector != null) {
            connector.disconnect();
        }
        setDisconnectedUI();
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
            e.printStackTrace();
        }
        return null;
    }

    private void updateMapView() {
        if (mapView == null || vehicleWrapper == null) return;
        mapView.updateVehicles(vehicleWrapper.getVehiclePositions());
        // Update table with live vehicles
        vehicleData.setAll(vehicleWrapper.getVehicleRows());
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
    }

    @FXML
    private void onInject() {
        if (connector == null || !connector.isConnected()) {
            setStatusText("Status: Not connected");
            return;
        }

        String edge = (txtInjectEdge != null) ? txtInjectEdge.getText().trim() : "";
        if (edge.isEmpty()) {
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
    }
}
