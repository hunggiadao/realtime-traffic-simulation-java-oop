import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
        if (args.length > 0 && args[0].equalsIgnoreCase("cli")) {
            LOGGER.info("Starting console simulation (cli mode)");
            runConsoleSimulation();
            return;
        }

        try {
            LOGGER.info("Launching JavaFX UI");
            launch(args);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to launch JavaFX", e);
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // String location = "/ui/main_ui.fxml"; // for VS Code
        String location = "main_ui.fxml"; // for Eclipse

        URL fxmlUrl = getClass().getResource(location);
        if (fxmlUrl == null) {
            throw new java.io.FileNotFoundException("Could not find " + location);
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        UI controller = loader.getController();
        primaryStage.setOnCloseRequest(event -> {
            if (controller != null) {
                controller.shutdown();
            }
            javafx.application.Platform.exit();
            System.exit(0);
        });

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

        TraCIConnector conn = new TraCIConnector(sumoBinary, configFile, 50, 0.1);

        // Prepare list to store data for the PDF export
        List<String> exportPDFData = new ArrayList<>();
        List<String> exportCSVData  = new ArrayList<>();

        if (!conn.connect()) {
            LOGGER.warning("Could not connect to SUMO");
            return;
        }

        try {
            VehicleWrapper vehicleWrapper = new VehicleWrapper(conn);
            TrafficLightWrapper trafficLightWrapper =  new TrafficLightWrapper(conn);

            // Run the simulation for 1000 steps (example limit)
            for (int i = 0; i < 1000; i++) {
                if (!conn.step()) {
                    break;
                }
                // ADD Data to CSV
                List<String> vehicleStepData = vehicleWrapper.getVehicleData();
                List<String> tlstepData = trafficLightWrapper.getTrafficLightData();

                // Counts the Max Row for Vehicle and TL
                int maxrow = Math.max(vehicleStepData.size(), tlstepData.size());
                for (int j =  0; j < maxrow; j++) {
                    String vPart = (j < vehicleStepData.size()) ? vehicleStepData.get(j) : ";;;;;;";
                    String tlPart = (j <  tlstepData.size() ) ? tlstepData.get(j) : ";;;;;;;;;";
                    exportCSVData.add(i + ";" + vPart + tlPart);
                }

                int vehicleCount = vehicleWrapper.getVehicleCount();
                double currentSpeed = vehicleWrapper.getSpeed("bus_64_0_0");

                String logEntry = "Vehicles: " + vehicleCount + ", Speed of bus_64_0_0: " + Math.round(currentSpeed) + " m/s";
                LOGGER.info("Step " + i + ": " + logEntry);

                // Collect data for the PDF and CSV
                exportPDFData.add("Step " + i + " -> " + logEntry);
            }

            // --- PDF AND CSV EXPORT ---
            Export exporter = new Export();
            LOGGER.info("Generating PDF Report...");
            exporter.createPDF("Simulation_Report.pdf", "SUMO Traffic Simulation Results", exportPDFData);
            LOGGER.info("Generating CSV Report...");
            exporter.createCSV("Simulation_Report.csv", exportCSVData);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during Simulation or Export", e);
        } finally {
            conn.disconnect();
            LOGGER.info("Console simulation finished");
        }
    }
}