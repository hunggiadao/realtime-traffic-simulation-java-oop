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
			for (int i = 0; i < 200; i++) {
				int vehicleCount = conn.getVehicleCount();
				LOGGER.info("Step " + i + ": Vehicles=" + vehicleCount);
				if (!conn.step()) break;
			}
		} finally {
			conn.disconnect();
			LOGGER.info("Console simulation finished");
		}
	}
}
