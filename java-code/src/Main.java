import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends Application {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        AppLogger.init();

        // run with CLI parameters
        if (args.length > 0 && args[0].equalsIgnoreCase("cli")) {
            LOGGER.info("Starting console simulation (cli mode)");
            runConsoleSimulation();
            return;
        }

        // run without parameters
        try {
            LOGGER.info("Launching JavaFX UI");
            launch(args);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to launch JavaFX", e);
        }
    }

    @Override
    /**
     * This function gets called automatically whenever the app starts
     */
    public void start(Stage primaryStage) throws Exception {
        // Both EclipseIDE and VSCode can also read this file path
        String location = "main_ui.fxml";

        URL fxmlUrl = getClass().getResource(location);
        if (fxmlUrl == null) {
            throw new java.io.FileNotFoundException("Could not find " + location);
        }

        // load new window
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        // intialize UI controller
        UI controller = loader.getController();
        primaryStage.setOnCloseRequest(event -> {
            if (controller != null) {
                controller.shutdown();
            }
            javafx.application.Platform.exit();
            System.exit(0);
        });

        // window's properties
        primaryStage.setTitle("Real-time Traffic Simulation");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    /**
     * Minimal console simulation loop including PDF export logic.
     */
    public static void runConsoleSimulation() {
        String sumoBinary = "C:\\Program Files (x86)\\Eclipse\\Sumo\\bin\\sumo-gui.exe";
        String configFile = ".\\SumoConfig\\G.sumocfg";

        TraCIConnector conn = new TraCIConnector(sumoBinary, configFile, (int) 50);

        // Prepare list to store data for the PDF export
//        List<String> exportData = new ArrayList<>();

        if (!conn.connect()) {
            LOGGER.warning("Could not connect to SUMO");
            return;
        }
        try {
            VehicleWrapper vehicleWrapper = new VehicleWrapper(conn);
            TrafficLightWrapper trafficLightWrapper =  new TrafficLightWrapper(conn);
            EdgeWrapper edgeWrapper = new EdgeWrapper(conn, vehicleWrapper);

            // Run the simulation for 1000 steps (example limit)
            for (int i = 0; i < 1000; i++) {
                if (!conn.step()) {
                    break;
                }

                // ADD Data to CSV
//                List<String> vehicleStepData = vehicleWrapper.getVehicleData();
//                List<String> tlstepData = trafficLightWrapper.getTrafficLightData();
//
//                // Counts the Max Row for Vehicle and TL
//                int maxrow = Math.max(vehicleStepData.size(), tlstepData.size());
//                for (int j =  0; j < maxrow; j++) {
//                    String vPart = (j < vehicleStepData.size()) ? vehicleStepData.get(j) : ",,,,,,";
//                    String tlPart = (j <  tlstepData.size() ) ? tlstepData.get(j) : ",,";
//                    exportData.add(j + "," + vPart + tlPart);
//                }

                // Example logging
                int vehicleCount = vehicleWrapper.getVehicleCount();
                double currentSpeed = vehicleWrapper.getSpeed("bus_64_0_0");
                String logEntry = "Vehicles: " + vehicleCount + ", Speed of bus_64_0_0: " + Math.round(currentSpeed) + " m/s";
                LOGGER.info("Step " + i + ": " + logEntry);

                // Collect data for the PDF and CSV
//                exportPDFData.add("Step " + i + " -> " + logEntry);
            }

            // --- PDF AND CSV EXPORT ---
//            Export exporter = new Export();
//            LOGGER.info("Generating PDF Report...");
//            exporter.createPDF("Simulation_Report.pdf", "SUMO Traffic Simulation Results", exportData);
//            LOGGER.info("Generating CSV Report...");
//            exporter.createCSV("Simulation_Report.csv", exportData);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during Simulation or Export", e);
        } finally {
            conn.disconnect();
            LOGGER.info("Console simulation finished");
        }
    }
}
