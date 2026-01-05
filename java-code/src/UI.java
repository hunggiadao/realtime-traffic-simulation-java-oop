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
import javafx.scene.paint.Color;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

public class UI {
	private static final Logger LOGGER = Logger.getLogger(UI.class.getName());

	// Top toolbar
	@FXML private Button btnOpenConfig;
	@FXML private Button btnConnect;
	@FXML private Button btnStart;
	@FXML private Button btnStep;
	@FXML private Slider sliderSpeed;

	// Left Simulation tab
	@FXML private TextField txtConfigPath;
	@FXML private TextField txtStepMs;
	@FXML private Button btnBrowseConfig;

	// Inject tab
	@FXML private ComboBox<String> cmbInjectEdge;
	@FXML private TextField txtInjectCount;
	@FXML private ColorPicker cpInjectColor;
	@FXML private TextField txtInjectSpeed;
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
	private final Map<String, Integer> trafficLightPhaseCountCache = new HashMap<>();
	private final Map<String, List<SumoLink>> trafficLightLinksCache = new HashMap<>();

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

		setDisconnectedUI();
	}

	// UI state helpers
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
	}

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
	}

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

		// New session: clear old chart/table stats.
		resetSessionStats();

		vehicleWrapper = new VehicleWrapper(connector);

		if (cpInjectColor != null) {
			// Reset injection color on (re)connect.
			cpInjectColor.setValue(Color.RED);
		}

		startConnectionMonitor();

		int lanes = loadNetworkForMap(cfgFile.getPath());
		if (lanes <= 0) {
			setStatusText("Loaded SUMO, but net file missing/empty");
		} else {
			setStatusText("Loaded SUMO, net lanes: " + lanes);
		}

		// Populate edge list
		List<String> edges = connector.getEdgeIds();
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
	private void onStartPause() {
		if (running) {
			stopLoop();
			setConnectedUI();
			return;
		}
		startLoop();
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
			LOGGER.log(Level.FINE, "Failed to resolve net-file from config", e);
		}
		return null;
	}

	private void updateMapView() {
		if (mapView == null || connector == null || vehicleWrapper == null) return;
		// Fetch latest positions and data
		Map<String, Point2D> allPositions = vehicleWrapper.getVehiclePositions();
		Map<String, String> allLaneIds = vehicleWrapper.getVehicleLaneIds();
		List<VehicleRow> allRows = vehicleWrapper.getVehicleRows();
		Map<String, Color> colorMap = new HashMap<>();
		Map<String, Point2D> filteredPositions = new HashMap<>();
		Map<String, String> filteredLaneIds = new HashMap<>();
		List<VehicleRow> filteredRows = new ArrayList<>();

		// Cache for edge mean speeds (only used when congestion filter enabled)
		Map<String, Double> edgeMeanSpeedCache = new HashMap<>();

		boolean filterColor = (chkFilterRed != null) && chkFilterRed.isSelected();
		boolean filterSpeed = (chkFilterSpeed != null) && chkFilterSpeed.isSelected();
		boolean filterCongested = (chkFilterCongested != null) && chkFilterCongested.isSelected();
		Color targetColor = (cpFilterColor != null) ? cpFilterColor.getValue() : Color.RED;
		if (targetColor == null) targetColor = Color.RED;

		for (VehicleRow row : allRows) {
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
			String laneId = (allLaneIds != null) ? allLaneIds.get(row.getId()) : null;
			if (laneId != null && !laneId.isEmpty()) {
				filteredLaneIds.put(row.getId(), laneId);
			}
		}

		// Update map (only filtered vehicles)
		if (filteredPositions.isEmpty() && allRows.isEmpty() && allPositions != null && !allPositions.isEmpty()) {
			// If row fetching fails for any reason, still render positions so vehicles remain visible.
			mapView.updateVehicles(allPositions, Collections.emptyMap(), allLaneIds);
		} else {
			mapView.updateVehicles(filteredPositions, colorMap, filteredLaneIds);
		}

		// Overlay traffic-light stop lines (R/Y/G) so it's obvious why vehicles stop.
		mapView.updateTrafficSignals(buildLaneSignalColorMap());

		// Update table
		vehicleData.setAll(filteredRows);
		// mapView.updateVehicles(((VehicleWrapper)connector).getVehiclePositions());
		// Update table with live vehicles
		// vehicleData.setAll(((VehicleWrapper)connector).getVehicleRows());
		if (vehicleTable != null) {
			vehicleTable.refresh();
		}
	}

	private Map<String, Color> buildLaneSignalColorMap() {
		if (connector == null || !connector.isConnected() || connector.getConnection() == null) {
			return Collections.emptyMap();
		}
		try {
			Object idsObj = connector.getConnection().do_job_get(Trafficlight.getIDList());
			List<String> ids = new ArrayList<>();
			if (idsObj instanceof String[]) {
				for (String s : (String[]) idsObj) ids.add(s);
			} else if (idsObj instanceof List<?>) {
				for (Object o : (List<?>) idsObj) ids.add(String.valueOf(o));
			}
			if (ids.isEmpty()) return Collections.emptyMap();

			Map<String, Integer> lanePriority = new HashMap<>();
			Map<String, Color> laneColor = new HashMap<>();
			for (String tlId : ids) {
				if (tlId == null || tlId.isEmpty()) continue;
				String state = String.valueOf(connector.getConnection().do_job_get(Trafficlight.getRedYellowGreenState(tlId)));
				if (state == null || state.isEmpty()) continue;

				List<SumoLink> links = trafficLightLinksCache.get(tlId);
				if (links == null) {
					links = fetchTrafficLightLinks(tlId);
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

	private List<SumoLink> fetchTrafficLightLinks(String tlId) {
		if (connector == null || !connector.isConnected() || connector.getConnection() == null) {
			return Collections.emptyList();
		}
		try {
			Object obj = connector.getConnection().do_job_get(Trafficlight.getControlledLinks(tlId));
			List<SumoLink> out = new ArrayList<>();
			flattenControlledLinks(obj, out);
			return out;
		} catch (Exception e) {
			return Collections.emptyList();
		}
	}

	private static void flattenControlledLinks(Object obj, List<SumoLink> out) {
		if (obj == null) return;
		if (obj instanceof SumoLink) {
			out.add((SumoLink) obj);
			return;
		}
		if (obj instanceof de.tudresden.sumo.objects.SumoLinkList) {
			for (Object o : (de.tudresden.sumo.objects.SumoLinkList) obj) {
				flattenControlledLinks(o, out);
			}
			return;
		}
		if (obj instanceof java.lang.Iterable<?>) {
			for (Object o : (java.lang.Iterable<?>) obj) {
				flattenControlledLinks(o, out);
			}
			return;
		}
		Class<?> c = obj.getClass();
		if (c.isArray()) {
			int n = java.lang.reflect.Array.getLength(obj);
			for (int i = 0; i < n; i++) {
				flattenControlledLinks(java.lang.reflect.Array.get(obj, i), out);
			}
		}
	}

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

	private static boolean isSimilarColor(Color actual, Color target, double tol) {
		if (actual == null || target == null) return false;
		return Math.abs(actual.getRed() - target.getRed()) <= tol
				&& Math.abs(actual.getGreen() - target.getGreen()) <= tol
				&& Math.abs(actual.getBlue() - target.getBlue()) <= tol;
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
			Object resp = connector.getConnection().do_job_get(Trafficlight.getIDList());
			List<String> ids = new ArrayList<>();
			if (resp instanceof String[]) {
				for (String s : (String[]) resp) ids.add(s);
			} else if (resp instanceof List<?>) {
				for (Object o : (List<?>) resp) ids.add(String.valueOf(o));
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
			} else if (def instanceof List<?>) {
				// Some SUMO networks may return a list of programs; take the first one.
				List<?> list = (List<?>) def;
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

		Color color = (cpInjectColor != null) ? cpInjectColor.getValue() : Color.RED;
		if (color == null) color = Color.RED;

		// Use edge as route ID for now
		String routeId = edge; 

		for (int i = 0; i < count; i++) {
			String vehId = "inj_" + System.currentTimeMillis() + "_" + i;
			if (vehicleWrapper != null) {
				vehicleWrapper.addVehicle(vehId, routeId, speed, color);
			}
		}
		setStatusText("Status: Injected " + count + " vehicles");

		// Refresh table/map immediately (even if the vehicle is inserted on the next sim step,
		// pending updates like color will be applied on refresh).
		updateMapView();
	}
}
