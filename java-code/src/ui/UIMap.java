import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.application.Platform;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

final class UIMap {
    private UIMap() {
    }

    static boolean isSimilarColor(Color actual, Color target, double tol) {
        if (actual == null || target == null) return false;
        return Math.abs(actual.getRed() - target.getRed()) <= tol
                && Math.abs(actual.getGreen() - target.getGreen()) <= tol
                && Math.abs(actual.getBlue() - target.getBlue()) <= tol;
    }

    static void processPendingInjections(UI ui, long nowNs) {
        Deque<UI.PendingInjection> pendingInjections = ui.pendingInjections;
        if (pendingInjections.isEmpty()) return;
        if (ui.connector == null || ui.vehicleWrapper == null || !ui.connector.isConnected()) {
            pendingInjections.clear();
            return;
        }
        if (ui.nextInjectionNs != 0L && nowNs < ui.nextInjectionNs) return;

        UI.PendingInjection req = pendingInjections.pollFirst();
        if (req == null) return;

        String vehId = "inj_" + (++ui.injectSeq);
        ui.vehicleWrapper.addVehicle(vehId, req.routeId, UI.DEFAULT_INJECT_SPEED_MS, req.color);
        ui.nextInjectionNs = nowNs + UI.INJECT_MIN_INTERVAL_NS;
        ui.pendingMapRefresh = true;
    }

    static int loadNetworkForMap(UI ui, String configPath) {
        if (ui.mapView == null) return 0;
        File netFile = UISumoFiles.resolveNetFile(ui, configPath);
        return ui.mapView.loadNetwork(netFile);
    }

    static void loadBusStopsForMap(UI ui) {
        if (ui.mapView == null) return;

        // Parse from SUMO config/additional files instead of TraCI.
        // The custom TraCI busstop vars can desync the stream if the varId is unsupported.
        new Thread(() -> {
            List<String[]> busStopData = new ArrayList<>();
            try {
                String configPath = (ui.txtConfigPath != null) ? ui.txtConfigPath.getText().trim() : "";
                File cfgFile = UISumoFiles.resolveConfigFile(configPath);
                String cfgPath = (cfgFile != null) ? cfgFile.getPath() : configPath;
                List<File> additionalFiles = UISumoFiles.resolveAdditionalFiles(ui, cfgPath);
                busStopData = UISumoFiles.parseBusStopsFromAdditionalFiles(ui, additionalFiles);
                ui.LOGGER.info("Parsed " + busStopData.size() + " bus stops from config");
            } catch (Exception e) {
                ui.LOGGER.log(Level.WARNING, "Failed to load bus stops for map (from files)", e);
            }

            final List<String[]> finalData = busStopData;
            Platform.runLater(() -> {
                if (ui.mapView != null) {
                    ui.mapView.updateBusStops(finalData);
                }
            });
        }, "BusStopLoader").start();
    }

    static void updateMapView(UI ui) {
        if (ui.mapView == null || ui.connector == null || ui.vehicleWrapper == null) return;

        // If SUMO terminated / TraCI disconnected, don't keep issuing TraCI queries.
        if (!ui.connector.isConnected() || ui.connector.getConnection() == null) {
            ui.setStatusText("Status: Disconnected");
            ui.setDisconnectedUI();
            return;
        }
        // Fetch latest positions and data
        Map<String, Point2D> allPositions = ui.vehicleWrapper.getVehiclePositions(); // gets a new list every time
        Map<String, String> allLaneIds = ui.vehicleWrapper.getVehicleLaneIds(); // gets a new list every time
        // Fetch vehicle angles for realistic orientation rendering
        Map<String, Double> allAngles = ui.vehicleWrapper.getVehicleAngles(); // gets a new list every time
        // Fetch vehicle types for rendering appropriate vehicle shapes (car, bus, motorbike, etc.)
        Map<String, String> allTypes = ui.vehicleWrapper.getVehicleTypes(); // gets a new list every time, not efficient
        List<VehicleRow> allRows = ui.vehicleWrapper.getVehicleRows(); // also update vehicleRows every time, not efficient

        Map<String, Color> colorMap = new HashMap<>();
        Map<String, Point2D> filteredPositions = new HashMap<>();
        Map<String, String> filteredLaneIds = new HashMap<>();
        Map<String, Double> filteredAngles = new HashMap<>();
        Map<String, String> filteredTypes = new HashMap<>();
        List<VehicleRow> filteredRows = new ArrayList<>();

        // Cache for edge mean speeds (only used when congestion filter enabled)
        Map<String, Double> edgeMeanSpeedCache = new HashMap<>();

        boolean filterColor = (ui.chkFilterRed != null) && ui.chkFilterRed.isSelected();
        boolean filterSpeed = (ui.chkFilterSpeed != null) && ui.chkFilterSpeed.isSelected();
        boolean filterCongested = (ui.chkFilterCongested != null) && ui.chkFilterCongested.isSelected();
        Color targetColor = (ui.cpFilterColor != null) ? ui.cpFilterColor.getValue() : Color.RED;
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
                if (ui.connector != null && ui.connector.isConnected() && edgeId != null && !edgeId.isEmpty()) {
                    Double mean = edgeMeanSpeedCache.get(edgeId);
                    if (mean == null && !edgeMeanSpeedCache.containsKey(edgeId)) {
                        try {
                            mean = (double) ui.edgeWrapper.getLastStepMeanSpeed(edgeId);
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
            String key = ui.colorKey(row.getColor());
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
            ui.mapView.updateVehicles(allPositions, Collections.emptyMap(), allLaneIds, allAngles, allTypes);
        } else {
            ui.mapView.updateVehicles(filteredPositions, colorMap, filteredLaneIds, filteredAngles, filteredTypes);
        }

        // Overlay traffic-light stop lines (R/Y/G) so it's obvious why vehicles stop.
        Map<String, Color> laneSignalMap = UITrafficLights.buildLaneSignalColorMap(ui);
        ui.mapView.updateTrafficSignals(laneSignalMap);

        // Finalize charts snapshot for this frame.
        double avgSpeed = (total > 0) ? (sumSpeed / (double) total) : 0.0;
        ui.lastChartSnapshot = new UI.ChartSnapshot(total, avgSpeed, speedBuckets);

        // Update table
        ui.vehicleData.setAll(filteredRows);

        if (ui.vehicleTable != null) {
            ui.vehicleTable.refresh();
        }

        ui.updateVehicleColorPieThrottled(colorBuckets, null);
    }
}
