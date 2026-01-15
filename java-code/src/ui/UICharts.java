import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class UICharts {
    private UICharts() {
    }

    static String colorKey(Color c) {
        // Match the table's format (JavaFX Color.toString() => 0xrrggbbaa)
        if (c == null) return "0x000000ff";
        return c.toString();
    }

    static String cssColorFromKey(String colorKey) {
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

    static void setVehicleColorLegendVisible(UI ui, boolean visible) {
        if (ui.vehicleColorLegendBox == null) return;
        ui.vehicleColorLegendBox.setVisible(visible);
        ui.vehicleColorLegendBox.setManaged(visible);
    }

    static void updateVehicleColorLegend(UI ui, List<String> labels, List<String> cssColors) {
        if (ui.vehicleColorLegendBox == null) return;

        ui.vehicleColorLegendBox.getChildren().clear();
        if (labels == null || labels.isEmpty()) {
            setVehicleColorLegendVisible(ui, false);
            return;
        }

        int n = Math.min(labels.size(), cssColors != null ? cssColors.size() : labels.size());
        n = Math.min(n, UI.MAX_COLOR_LEGEND_ITEMS);
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
            ui.vehicleColorLegendBox.getChildren().add(row);
        }

        setVehicleColorLegendVisible(ui, true);
    }

    static void updateVehicleColorPie(UI ui, Map<String, Integer> bucketCounts, Map<String, String> bucketHex) {
        if (ui.vehicleColorPie == null) return;

        if (!ui.vehicleColorPieSlotsInitialized) {
            // initialize() should do this, but be defensive for any edge cases.
            ObservableList<PieChart.Data> init = FXCollections.observableArrayList();
            for (int i = 0; i < ui.vehicleColorPieSlots.length; i++) {
                ui.vehicleColorPieSlots[i] = new PieChart.Data("", 0.0);
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

        if (bucketCounts == null || bucketCounts.isEmpty()) {
            for (PieChart.Data d : ui.vehicleColorPieSlots) {
                if (d != null) {
                    d.setName("");
                    d.setPieValue(0.0);
                }
            }
            Arrays.fill(ui.vehicleColorPieSlotCss, null);
            ui.vehicleColorPie.setTitle("Vehicle Colors (0)");
            updateVehicleColorLegend(ui, Collections.emptyList(), Collections.emptyList());
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
            if (kept >= UI.MAX_COLOR_SLICES) {
                others += e.getValue();
                continue;
            }

            String key = e.getKey();
            int count = e.getValue();
            int pct = (total > 0) ? (int) Math.round(100.0 * (double) count / (double) total) : 0;
            String display = key + " (" + pct + "%)";
            String hex = cssColorFromKey(key);

            PieChart.Data slot = ui.vehicleColorPieSlots[kept];
            if (slot != null) {
                slot.setName(display);
                slot.setPieValue(count);
                ui.vehicleColorPieSlotCss[kept] = hex;
                if (slot.getNode() != null) {
                    slot.getNode().setStyle("-fx-pie-color: " + hex + ";");
                }
            }

            // Legend: cap to 4 items total. Prefer showing Others if there are many colors.
            int remainingSlotsForTopColors = (others > 0) ? (UI.MAX_COLOR_LEGEND_ITEMS - 1) : UI.MAX_COLOR_LEGEND_ITEMS;
            if (legendLabels.size() < remainingSlotsForTopColors) {
                legendLabels.add(display);
                legendCss.add(hex);
            }
            kept++;
        }

        // Clear remaining top slots
        for (int i = kept; i < UI.MAX_COLOR_SLICES; i++) {
            PieChart.Data slot = ui.vehicleColorPieSlots[i];
            if (slot != null) {
                slot.setName("");
                slot.setPieValue(0.0);
            }
            ui.vehicleColorPieSlotCss[i] = null;
        }

        // Others slot
        PieChart.Data othersSlot = ui.vehicleColorPieSlots[UI.MAX_COLOR_SLICES];
        if (othersSlot != null) {
            if (others > 0) {
                int pct = (total > 0) ? (int) Math.round(100.0 * (double) others / (double) total) : 0;
                String label = "Others (" + pct + "%)";
                othersSlot.setName(label);
                othersSlot.setPieValue(others);
                ui.vehicleColorPieSlotCss[UI.MAX_COLOR_SLICES] = "#9E9E9E";
                if (othersSlot.getNode() != null) {
                    othersSlot.getNode().setStyle("-fx-pie-color: #9E9E9E;");
                }

                if (legendLabels.size() < UI.MAX_COLOR_LEGEND_ITEMS) {
                    legendLabels.add(label);
                    legendCss.add("#9E9E9E");
                }
            } else {
                othersSlot.setName("");
                othersSlot.setPieValue(0.0);
                ui.vehicleColorPieSlotCss[UI.MAX_COLOR_SLICES] = null;
            }
        }

        ui.vehicleColorPie.setTitle("Vehicle Colors (" + total + ")");
        updateVehicleColorLegend(ui, legendLabels, legendCss);
    }

    static int computeVehicleColorPieSignature(Map<String, Integer> bucketCounts) {
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

    static void updateVehicleColorPieThrottled(UI ui, Map<String, Integer> bucketCounts, Map<String, String> bucketHex) {
        if (ui.vehicleColorPie == null) return;

        int total = 0;
        if (bucketCounts != null) {
            for (int c : bucketCounts.values()) total += c;
        }
        int signature = computeVehicleColorPieSignature(bucketCounts);

        boolean dataChanged = (total != ui.lastVehicleColorPieTotal) || (signature != ui.lastVehicleColorPieSignature);
        boolean totalDecreased = (ui.lastVehicleColorPieTotal >= 0) && (total < ui.lastVehicleColorPieTotal);

        boolean shouldUpdate;
        if (!ui.running) {
            // When paused/stepping, update immediately when something changes.
            shouldUpdate = dataChanged;
        } else {
            long now = System.nanoTime();
            // While running, keep the same cadence as other charts, but don't lie:
            // if vehicles disappear (total decreases) or distribution changes, refresh immediately.
            shouldUpdate = (totalDecreased && dataChanged)
                    || ((ui.lastVehicleColorPieUpdateNs == 0L) || ((now - ui.lastVehicleColorPieUpdateNs) >= UI.VEHICLE_CHART_UPDATE_INTERVAL_NS)) && dataChanged;
            if (shouldUpdate) ui.lastVehicleColorPieUpdateNs = now;
        }

        if (!shouldUpdate) return;
        ui.lastVehicleColorPieTotal = total;
        ui.lastVehicleColorPieSignature = signature;
        updateVehicleColorPie(ui, bucketCounts, bucketHex);
    }

    static void resetSessionStats(UI ui) {
        if (ui.vehicleSeries != null) {
            ui.vehicleSeries.getData().clear();
        }
        if (ui.avgSpeedSeries != null) {
            ui.avgSpeedSeries.getData().clear();
        }
        if (ui.speedDistSeries != null) {
            // Keep 4 persistent bars; reset them to 0 instead of clearing the series.
            for (int i = 0; i < ui.speedDistBucketAnim.length; i++) {
                if (ui.speedDistBucketAnim[i] != null) {
                    ui.speedDistBucketAnim[i].stop();
                    ui.speedDistBucketAnim[i] = null;
                }
            }

            boolean needsRebuild = ui.speedDistSeries.getData().size() != UI.SPEED_BUCKET_LABELS.length;
            for (int i = 0; i < UI.SPEED_BUCKET_LABELS.length && !needsRebuild; i++) {
                if (ui.speedDistBucketData[i] == null) {
                    needsRebuild = true;
                }
            }

            if (needsRebuild) {
                ui.speedDistSeries.getData().clear();
                for (int i = 0; i < UI.SPEED_BUCKET_LABELS.length; i++) {
                    XYChart.Data<String, Number> d = new XYChart.Data<>(UI.SPEED_BUCKET_LABELS[i], 0.0);
                    ui.speedDistBucketData[i] = d;
                    ui.speedDistSeries.getData().add(d);
                }
            } else {
                for (int i = 0; i < UI.SPEED_BUCKET_LABELS.length; i++) {
                    ui.speedDistBucketData[i].setYValue(0.0);
                }
            }
        }
        ui.lastChartSnapshot = null;
        ui.lastVehicleChartUpdateNs = 0L;

        ui.lastVehicleColorPieUpdateNs = 0L;
        ui.lastVehicleColorPieTotal = -1;
        ui.lastVehicleColorPieSignature = 0;
        if (ui.vehicleColorPie != null) {
            for (PieChart.Data d : ui.vehicleColorPieSlots) {
                if (d != null) {
                    d.setName("");
                    d.setPieValue(0.0);
                }
            }
            ui.vehicleColorPie.setTitle("Vehicle Colors (0)");
        }

        ui.speedDistSamples = 0L;
        Arrays.fill(ui.speedDistPctSum, 0.0);

        ui.vehicleData.clear();
        if (ui.vehicleTable != null) {
            ui.vehicleTable.refresh();
        }
    }

    static void updateCharts(UI ui, int step, int vehicleCount) {
        if (ui.vehicleSeries == null) return;

        // While running, throttle chart updates so the plots remain readable.
        // When stepping manually (running == false), update every step.
        boolean shouldUpdate;
        if (!ui.running) {
            shouldUpdate = true;
        } else {
            long now = System.nanoTime();
            shouldUpdate = (ui.lastVehicleChartUpdateNs == 0L) || ((now - ui.lastVehicleChartUpdateNs) >= UI.VEHICLE_CHART_UPDATE_INTERVAL_NS);
            if (shouldUpdate) ui.lastVehicleChartUpdateNs = now;
        }

        if (!shouldUpdate) return;

        ui.vehicleSeries.getData().add(new XYChart.Data<>(step, vehicleCount));

        UI.ChartSnapshot snap = ui.lastChartSnapshot;
        if (snap == null) return;

        if (ui.avgSpeedSeries != null) {
            ui.avgSpeedSeries.getData().add(new XYChart.Data<>(step, snap.avgSpeed));
        }

        if (ui.speedDistSeries != null && ui.speedDistChart != null) {
            double[] targetPct = new double[UI.SPEED_BUCKET_LABELS.length];

            // If there are no vehicles right now, keep bars at 0 (but do not remove them),
            // and reset aggregation so the next vehicles start fresh.
            if (snap.vehicleCount <= 0) {
                ui.speedDistSamples = 0L;
                Arrays.fill(ui.speedDistPctSum, 0.0);
                Arrays.fill(targetPct, 0.0);
            } else {
                // Update distribution only on the same cadence as the other charts.
                // We aggregate as a running-average % per bucket.
                ui.speedDistSamples++;
                for (int i = 0; i < UI.SPEED_BUCKET_LABELS.length; i++) {
                    double pct = 100.0 * (double) snap.speedBuckets[i] / (double) snap.vehicleCount;
                    ui.speedDistPctSum[i] += pct;
                    targetPct[i] = ui.speedDistPctSum[i] / (double) ui.speedDistSamples;
                }
            }

            for (int i = 0; i < UI.SPEED_BUCKET_LABELS.length && i < ui.speedDistBucketData.length; i++) {
                animateSpeedDistBucketTo(ui, i, targetPct[i]);
            }
        }
    }

    static void animateSpeedDistBucketTo(UI ui, int index, double targetValue) {
        if (index < 0 || index >= ui.speedDistBucketData.length) return;
        XYChart.Data<String, Number> data = ui.speedDistBucketData[index];
        if (data == null) return;

        Timeline old = ui.speedDistBucketAnim[index];
        if (old != null) {
            old.stop();
        }

        KeyValue kv = new KeyValue(data.YValueProperty(), targetValue, Interpolator.EASE_BOTH);
        Timeline tl = new Timeline(new KeyFrame(UI.SPEED_DIST_ANIM_DURATION, kv));
        ui.speedDistBucketAnim[index] = tl;
        tl.play();
    }
}
