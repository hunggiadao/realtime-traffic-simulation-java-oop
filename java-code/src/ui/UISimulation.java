import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

final class UISimulation {
    private UISimulation() {
    }

    static void initialize(UI ui) {
        AppLogger.init();
        ui.LOGGER.info("UI initialized");

        ui.userSettings.load();

        // Optional: allow increasing verbosity to diagnose issues that otherwise only log at Level.FINE.
        // Set in bin/settings/user.properties: log.level=FINE
        applyLogLevel(ui);

        // Make Filters + Traffic Lights UI reactive (no need to wait for a simulation step)
        if (ui.chkFilterRed != null) {
            ui.chkFilterRed.selectedProperty().addListener((obs, oldV, newV) -> ui.updateMapView());
        }
        if (ui.cpFilterColor != null) {
            ui.cpFilterColor.setValue(Color.RED);
            ui.cpFilterColor.valueProperty().addListener((obs, oldV, newV) -> {
                if (ui.chkFilterRed != null && ui.chkFilterRed.isSelected()) ui.updateMapView();
            });
        }
        if (ui.chkFilterSpeed != null) {
            ui.chkFilterSpeed.selectedProperty().addListener((obs, oldV, newV) -> ui.updateMapView());
        }
        if (ui.chkFilterCongested != null) {
            ui.chkFilterCongested.selectedProperty().addListener((obs, oldV, newV) -> ui.updateMapView());
        }
        if (ui.cmbTrafficLight != null) {
            ui.cmbTrafficLight.valueProperty().addListener((obs, oldV, newV) -> ui.updateTrafficLightUI());
        }

        // Table setup
        if (ui.colId != null && ui.colSpeed != null && ui.colEdge != null && ui.colColor != null) {
            ui.colId.setCellValueFactory(data -> data.getValue().idProperty());
            ui.colSpeed.setCellValueFactory(data -> data.getValue().speedProperty());
            ui.colEdge.setCellValueFactory(data -> data.getValue().edgeProperty());
            ui.colColor.setCellValueFactory(data -> data.getValue().colorProperty());
        }
        if (ui.vehicleTable != null) {
            ui.vehicleTable.setItems(ui.vehicleData);
        }

        if (ui.vehicleColorPie != null) {
            ui.vehicleColorPie.setAnimated(true);
            ui.vehicleColorPie.setLegendVisible(false);
            ui.vehicleColorPie.setLabelsVisible(false);
            ui.vehicleColorPie.setTitle("Vehicle Colors (0)");
            ui.vehicleColorPie.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
            if (!ui.vehicleColorPieSlotsInitialized) {
                javafx.collections.ObservableList<javafx.scene.chart.PieChart.Data> init = javafx.collections.FXCollections.observableArrayList();
                for (int i = 0; i < ui.vehicleColorPieSlots.length; i++) {
                    ui.vehicleColorPieSlots[i] = new javafx.scene.chart.PieChart.Data("", 0.0);
                    final int idx = i;
                    ui.vehicleColorPieSlots[i].nodeProperty().addListener((obs, oldN, newN) -> {
                        if (newN != null) {
                            String css = ui.vehicleColorPieSlotCss[idx];
                            if (css != null && !css.isEmpty()) {
                                newN.setStyle("-fx-pie-color: " + css + ";");
                            }
                        }
                    });
                    init.add(ui.vehicleColorPieSlots[i]);
                }
                ui.vehicleColorPie.setData(init);
                ui.vehicleColorPieSlotsInitialized = true;
            }
        }

        ui.setVehicleColorLegendVisible(false);

        // Chart setup
        if (ui.vehicleCountChart != null) {
            // Use a style class (not fx:id) so CSS can target the chart reliably.
            if (!ui.vehicleCountChart.getStyleClass().contains("vehicle-count-chart")) {
                ui.vehicleCountChart.getStyleClass().add("vehicle-count-chart");
            }
            ui.vehicleSeries = new javafx.scene.chart.XYChart.Series<>();
            ui.vehicleSeries.setName("Vehicle count");
            ui.vehicleCountChart.getData().add(ui.vehicleSeries);

            ui.vehicleCountChart.setLegendVisible(false);
            ui.vehicleCountChart.setCreateSymbols(false);
        }

        if (ui.avgSpeedChart != null) {
            ui.avgSpeedSeries = new javafx.scene.chart.XYChart.Series<>();
            ui.avgSpeedSeries.setName("Avg speed");
            ui.avgSpeedChart.getData().add(ui.avgSpeedSeries);

            ui.avgSpeedChart.setLegendVisible(false);
            ui.avgSpeedChart.setCreateSymbols(false);
        }

        if (ui.speedDistChart != null) {
            ui.speedDistSeries = new javafx.scene.chart.XYChart.Series<>();
            ui.speedDistSeries.setName("Vehicles");
            ui.speedDistChart.getData().add(ui.speedDistSeries);
            ui.speedDistChart.setLegendVisible(false);

            ui.speedDistChart.setAnimated(false);
            if (ui.speedDistChartXAxis != null) ui.speedDistChartXAxis.setAnimated(false);
            if (ui.speedDistChartYAxis != null) ui.speedDistChartYAxis.setAnimated(false);

            for (int i = 0; i < UI.SPEED_BUCKET_LABELS.length; i++) {
                javafx.scene.chart.XYChart.Data<String, Number> d = new javafx.scene.chart.XYChart.Data<>(UI.SPEED_BUCKET_LABELS[i], 0.0);
                ui.speedDistBucketData[i] = d;
                ui.speedDistSeries.getData().add(d);
            }
        }

        // Defaults - adjust to your project paths
        if (ui.txtConfigPath != null) {
            // default relative to project root (java-code/../SumoConfig) but accept current dir too
            File preferParent = java.nio.file.Paths.get("..\\SumoConfig\\G.sumocfg").toFile();
            if (preferParent.exists()) {
                ui.txtConfigPath.setText("..\\SumoConfig\\G.sumocfg");
            } else {
                ui.txtConfigPath.setText(".\\SumoConfig\\G.sumocfg");
            }

            // Override defaults with last saved value if present
            String lastCfg = ui.userSettings.getString("configPath", "");
            if (lastCfg != null && !lastCfg.isBlank()) {
                ui.txtConfigPath.setText(lastCfg);
            }
        }
        if (ui.txtStepMs != null) {
            ui.txtStepMs.setText(ui.userSettings.getString("stepMs", "50"));
        }
        if (ui.sliderSpeed != null) {
            ui.sliderSpeed.setMin(0.25);
            ui.sliderSpeed.setMax(5.0);
            ui.sliderSpeed.setValue(1.0);
        }
        if (ui.cpInjectColor != null) {
            // Always start with red for injection.
            ui.cpInjectColor.setValue(Color.RED);
        }
        if (ui.mapPane != null) {
            ui.mapView = new MapView();
            ui.mapView.prefWidthProperty().bind(ui.mapPane.widthProperty());
            ui.mapView.prefHeightProperty().bind(ui.mapPane.heightProperty());
            ui.mapPane.getChildren().clear();
            ui.mapPane.getChildren().add(ui.mapView);
        }

        // Clear traffic light UI until we connect
        if (ui.cmbTrafficLight != null) {
            ui.cmbTrafficLight.getItems().clear();
        }
        if (ui.lblPhaseInfo != null) {
            ui.lblPhaseInfo.setText("Phase: -");
        }
        if (ui.txtPhaseDuration != null) {
            ui.txtPhaseDuration.setText("");
        }

        // set focus on main window for key press listening
        if (ui.rootPane != null) {
            ui.rootPane.setFocusTraversable(true);
            Platform.runLater(() -> ui.rootPane.requestFocus());
            ui.rootPane.setOnMouseClicked(event -> ui.rootPane.requestFocus());
        }

        ui.setDisconnectedUI();
    }

    private static void applyLogLevel(UI ui) {
        try {
            String raw = (ui.userSettings != null)
                    ? ui.userSettings.getString("log.level", "")
                    : "";
            if (raw == null || raw.trim().isEmpty()) return;
            java.util.logging.Level level = java.util.logging.Level.parse(raw.trim().toUpperCase());
            AppLogger.setLevel(level);
            ui.LOGGER.info("Log level set to " + level.getName());
        } catch (Exception e) {
            ui.LOGGER.log(Level.WARNING, "Invalid log.level setting; expected INFO/FINE/WARNING/SEVERE", e);
        }
    }

    static void onStartPause(UI ui) {
        if (ui.running) {
            ui.stopLoop();
            ui.setConnectedUI();
            ui.trafWrapper.togglePauseSimulation();
            return;
        }
        ui.startLoop();
        ui.setRunningUI();
        ui.trafWrapper.togglePauseSimulation();
    }

    static void onStep(UI ui) {
        ui.stopLoop();
        if (ui.connector == null || !ui.connector.isConnected()) {
            ui.setStatusText("Status: Not connected");
            return;
        }
        boolean ok = ui.connector.step();
        if (!ok) {
            ui.setStatusText("Status: Step failed");
            return;
        }
        ui.setStatusText("Status: Stepped");
        ui.updateAfterStep();
    }

    static void shutdown(UI ui) {
        ui.stopLoop();
        ui.stopConnectionMonitor();
        if (ui.connector != null) {
            ui.connector.disconnect();
        }
        ui.setDisconnectedUI();
    }

    static void startConnectionMonitor(UI ui) {
        stopConnectionMonitor(ui);
        ui.monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConnectionMonitor");
            t.setDaemon(true);
            return t;
        });

        ui.monitorExecutor.scheduleAtFixedRate(() -> {
            try {
                boolean connected = ui.connector != null && ui.connector.isConnected();
                if (!connected) {
                    Platform.runLater(() -> ui.setStatusText("Status: Disconnected"));
                }
            } catch (Exception e) {
                ui.LOGGER.log(Level.WARNING, "Monitor thread error", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    static void stopConnectionMonitor(UI ui) {
        if (ui.monitorExecutor != null) {
            ui.monitorExecutor.shutdownNow();
            ui.monitorExecutor = null;
        }
    }

    static void onConnectToggle(UI ui) {
        // If already connected, act as Disconnect
        if (ui.connector != null && ui.connector.isConnected()) {
            ui.disconnectFromSumo();
            return;
        }

        // Get config path and step length from UI
        String configPath = (ui.txtConfigPath != null) ? ui.txtConfigPath.getText().trim() : "..\\SumoConfig\\G.sumocfg";

        try {
            if (ui.txtStepMs != null) {
                ui.stepLengthMs = Integer.parseInt(ui.txtStepMs.getText().trim());
            }
        } catch (NumberFormatException e) {
            ui.stepLengthMs = 50;
            ui.txtStepMs.setText("50");
        }

        ui.stepLengthSeconds = ui.stepLengthMs / 1000.0;
        File cfgFile = UISumoFiles.resolveConfigFile(configPath);
        if (cfgFile == null || !cfgFile.exists()) {
            ui.setStatusText("Config not found: " + configPath);
            return;
        }

        // Persist settings (explicit file I/O requirement)
        ui.userSettings.putString("configPath", configPath);
        ui.userSettings.putString("stepMs", String.valueOf(ui.stepLengthMs));
        ui.userSettings.save();

        // Path to SUMO (headless calculation only, render inside JavaFX)
        String sumoBinary = UISumoFiles.resolveSumoBinary();

        if (ui.btnConnect != null) {
            ui.btnConnect.setDisable(true);
            ui.btnConnect.setText("Connecting...");
        }
        ui.setStatusText("Status: Connecting...");

        Thread connectThread = new Thread(() -> {
            TraCIConnector localConnector = new TraCIConnector(sumoBinary, cfgFile.getPath(), (double) ui.stepLengthSeconds);
            boolean ok = localConnector.connect();
            if (!ok) {
                Platform.runLater(() -> {
                    ui.setStatusText("Status: Connection failed");
                    if (ui.btnConnect != null) {
                        ui.btnConnect.setDisable(false);
                        ui.btnConnect.setText("Connect");
                    }
                });
                return;
            }

            ui.vehicleWrapper = new VehicleWrapper(localConnector);
            ui.trafWrapper = new TrafficLightWrapper(localConnector);
            ui.keyController = new UIKeys(ui.trafWrapper, ui);
            ui.edgeWrapper = new EdgeWrapper(localConnector, ui.vehicleWrapper);
            ui.infWrapper = new InfrastructureWrapper(localConnector);

            Platform.runLater(() -> {
                // Adopt the connected instance on the UI thread
                ui.connector = localConnector;
                ui.resetSessionStats();

                if (ui.cpInjectColor != null) {
                    ui.cpInjectColor.setValue(Color.RED);
                }

                ui.startConnectionMonitor();

                // Load network asynchronously (parsing can be large)
                // Bus stops are loaded AFTER network is ready (in the callback)
                File netFile = UISumoFiles.resolveNetFile(ui, cfgFile.getPath());
                if (ui.mapView != null) {
                    ui.mapView.loadNetworkAsync(netFile, lanes -> {
                        if (lanes <= 0) ui.setStatusText("Loaded SUMO, but net file missing/empty");
                        else ui.setStatusText("Loaded SUMO, net lanes: " + lanes);

                        // Load bus stops AFTER network is loaded so lane positions are available
                        ui.loadBusStopsForMap();
                    });
                }

                // Populate spawnable edge list
                if (ui.cmbInjectEdge != null && ui.edgeWrapper != null) {
                    List<String> allEdges = ui.edgeWrapper.getEdgeIDs();
                    List<String> spawnableEdges = new ArrayList<>();
                    for (String e : allEdges) {
                        // SUMO internal/junction edges look like ":J27_0" and are not valid for spawning.
                        if (e == null || e.isBlank()) continue;
                        if (e.startsWith(":")) continue;
                        spawnableEdges.add(e);
                    }
                    ui.cmbInjectEdge.getItems().setAll(spawnableEdges);
                    if (!spawnableEdges.isEmpty()) ui.cmbInjectEdge.getSelectionModel().select(0);
                }

                // Populate traffic light list (UI work)
                ui.populateTrafficLights();

                ui.setConnectedUI();
                ui.updateAfterStep();
                if (ui.btnConnect != null) {
                    ui.btnConnect.setDisable(false);
                    ui.btnConnect.setText("Disconnect");
                }
            });
        }, "ConnectSUMO");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    static void updateAfterStep(UI ui) {
        if (ui.connector == null) return;

        long nowNs = System.nanoTime();
        ui.processPendingInjections(nowNs);

        int step = ui.connector.getCurrentStep();
        double simTime = ui.connector.getSimTimeSeconds();
        int vehicleCount = (ui.vehicleWrapper != null) ? ui.vehicleWrapper.getVehicleCount() : 0;

        if (ui.lblStep != null) {
            ui.lblStep.setText("Step: " + step);
        }
        if (ui.lblSimTime != null) {
            ui.lblSimTime.setText(String.format("Sim time: %.1f s", simTime));
        }
        if (ui.lblVehicles != null) {
            ui.lblVehicles.setText("Vehicles: " + vehicleCount);
        }

        // Throttle expensive map refresh while running.
        boolean shouldUpdateMap = true;
        if (ui.running) {
            long now = nowNs;
            if (ui.pendingMapRefresh) {
                ui.pendingMapRefresh = false;
                ui.lastMapUpdateNs = now;
                shouldUpdateMap = true;
            } else if (ui.lastMapUpdateNs != 0L && (now - ui.lastMapUpdateNs) < UI.MAP_UPDATE_MIN_INTERVAL_NS) {
                shouldUpdateMap = false;
            } else {
                ui.lastMapUpdateNs = now;
                shouldUpdateMap = true;
            }
        }

        if (shouldUpdateMap) {
            ui.updateMapView();
        }

        ui.updateCharts(step, vehicleCount);
    }
}
