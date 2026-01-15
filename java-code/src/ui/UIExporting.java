import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class UIExporting {
    private UIExporting() {
    }

    static void handlePdfExport(UI ui) {
        if (!ui.canExportNow() || ui.vehicleWrapper == null || ui.trafWrapper == null) {
            ui.setStatusText("Status: Pause + connect to export");
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
                List<String> vehicleData = ui.vehicleWrapper.getVehicleData();
                List<String> tlData = ui.trafWrapper.getTrafficLightData();
                int maxRow = Math.max(vehicleData.size(), tlData.size());

                for (int j = 0; j < maxRow; j++) {
                    // Vehicle Data output: (ID, Color, Speed, PosX, PosY, Edge)
                    String vehicle = (j < vehicleData.size()) ? vehicleData.get(j) : ",,,,,,"; // ; for empty space
                    // TrafficLight Data output: (ID, Phase, Index)
                    String tl = (j < tlData.size()) ? tlData.get(j) : ",,";
                    currentData.add(j + "," + vehicle + tl);
                }
                // Export the Data
                Export exporter = new Export();
                // pdf export needs more data for metrics and stats
                exporter.createPDF(file.getAbsolutePath(), "Sumo Simulation Report", currentData, ui);
                System.out.println("PDF successfully created: " + file.getAbsolutePath());
                ui.LOGGER.fine("Sumo-PDF Export successful saved in: " + file.getAbsolutePath());
            } catch (Exception e) {
                ui.LOGGER.warning("Failed to export PDF from Sumo-UI: " + e.getMessage());
            }
        }
    }

    static void handleCSVExport(UI ui) {
        if (!ui.canExportNow() || ui.vehicleWrapper == null || ui.trafWrapper == null) {
            ui.setStatusText("Status: Pause + connect to export");
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
                List<String> vehicleData = ui.vehicleWrapper.getVehicleData();
                List<String> tlData = ui.trafWrapper.getTrafficLightData();
                int maxRow = Math.max(vehicleData.size(), tlData.size());

                for (int j = 0; j < maxRow; j++) {
                    // Vehicle Data output: (ID, Color, Speed, PosX, PosY, Edge)
                    String vehicle = (j < vehicleData.size()) ? vehicleData.get(j) : ",,,,,,"; // ; for empty space
                    // TrafficLight Data output: (ID, Phase, Index)
                    String tl = (j < tlData.size()) ? tlData.get(j) : ",,";
                    currentData.add(j + "," + vehicle + tl);
                }
                Export exporter = new Export();
                exporter.createCSV(file.getAbsolutePath(), currentData);
                System.out.println("CSV successfully created: " + file.getAbsolutePath());
                ui.LOGGER.fine("Sumo-CSV Export successful saved in: " + file.getAbsolutePath());
            } catch (Exception e) {
                ui.LOGGER.warning("Failed to export CSV from Sumo-UI: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    static Map<String, Node> getExportGraphs(UI ui) {
        Map<String, Node> out = new LinkedHashMap<>();
        if (ui.vehicleCountChart != null) out.put("Vehicle Count", ui.vehicleCountChart);
        if (ui.avgSpeedChart != null) out.put("Average Speed", ui.avgSpeedChart);
        if (ui.speedDistChart != null) out.put("Speed Distribution", ui.speedDistChart);
        if (ui.vehicleColorPie != null) out.put("Vehicle Color Density", ui.vehicleColorPie);
        return out;
    }

    static List<UI.PieSliceExport> getVehicleColorPieExport(UI ui) {
        List<UI.PieSliceExport> out = new ArrayList<>();

        // Prefer the stable slots (keeps ordering consistent with the UI pie).
        if (ui.vehicleColorPieSlotsInitialized) {
            double total = 0.0;
            for (int i = 0; i < ui.vehicleColorPieSlots.length; i++) {
                PieChart.Data d = ui.vehicleColorPieSlots[i];
                if (d == null) continue;
                double v = d.getPieValue();
                if (v <= 0.0) continue;
                String name = d.getName();
                if (name == null || name.isEmpty()) continue;
                String css = ui.vehicleColorPieSlotCss[i];
                out.add(new UI.PieSliceExport(name, v, css));
                total += v;
            }
            if (total > 0.0 && !out.isEmpty()) return out;
            out.clear();
        }

        // Fallback: read from chart data if slots aren't initialized.
        if (ui.vehicleColorPie != null && ui.vehicleColorPie.getData() != null) {
            for (PieChart.Data d : ui.vehicleColorPie.getData()) {
                if (d == null) continue;
                double v = d.getPieValue();
                if (v <= 0.0) continue;
                String name = d.getName();
                if (name == null || name.isEmpty()) continue;
                out.add(new UI.PieSliceExport(name, v, null));
            }
        }

        if (!out.isEmpty()) return out;

        // Final fallback: compute from the currently displayed table rows.
        // Keeps the same rule: Top 3 + Others.
        List<VehicleRow> rows = null;
        if (ui.vehicleTable != null && ui.vehicleTable.getItems() != null) {
            rows = new ArrayList<>(ui.vehicleTable.getItems());
        } else if (ui.vehicleData != null) {
            rows = new ArrayList<>(ui.vehicleData);
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
            if (kept < UI.MAX_COLOR_SLICES) {
                String key = e.getKey();
                int v = e.getValue();
                String css = cssByKey.get(key);
                out.add(new UI.PieSliceExport(key, v, css));
                kept++;
            } else {
                others += e.getValue();
            }
        }
        if (others > 0) {
            out.add(new UI.PieSliceExport("Others", others, "#9E9E9E"));
        }
        return out;
    }
}
