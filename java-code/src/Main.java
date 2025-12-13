import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;
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
		String location = "/ui/main_ui.fxml";
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
	 * Minimal console simulation loop for quick connection checks.
	 */
	public static void runConsoleSimulation() {
		String sumoBinary = "C:\\Program Files (x86)\\Eclipse\\Sumo\\bin\\sumo-gui.exe";
		String configFile = ".\\SumoConfig\\G.sumocfg";

		TraCIConnector conn = new TraCIConnector(sumoBinary, configFile, 50, 0.1);
		if (!conn.connect()) {
			LOGGER.warning("Could not connect to SUMO");
			return;
		}

		try {
//			VehicleWrapper vehicleWrapper = new VehicleWrapper(conn);
//			TrafficLightWrapper trafficLightWrapper = new TrafficLightWrapper(conn);

			// Run the simulation for 10000 steps, or until finished
			for (int i = 0; i < 10000; i++) {
				if (!conn.step()) {
					break;
				}

				// You can add your code here, for example:
				int vehicleCount = ((VehicleWrapper)conn).getVehicleCount();
				int trafficlightCount = ((TrafficLightWrapper)conn).getTrafficLightIds().size();
				String trafficlightState = ((TrafficLightWrapper)conn).getTrafficLightState("J37");
				// int busStopCount =  (int) BusStop.getVehicleCount("bs_4");
				double currentSpeed = ((VehicleWrapper)conn).getSpeed("bus_64_0_0"); // gets the current Speed of x vehicle
        
        LOGGER.info("Step " + i + ": Vehicles=" + vehicleCount);

				System.out.println("Step " + i + ": Vehicles = " + vehicleCount +
					", TrafficLights = " + trafficlightCount);
				System.out.println("\tCurrent Lights-Color of J37: " + trafficlightState +
					" Current Bus-64-0-0 Speed: " + Math.round(currentSpeed) + " m/s");
		} finally {
			conn.disconnect();
			LOGGER.info("Console simulation finished");
		}
	}
}
