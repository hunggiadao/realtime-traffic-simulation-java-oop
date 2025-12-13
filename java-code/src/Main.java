import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.cmd.*;
import it.polito.appeal.traci.SumoTraciConnection;

import java.io.File;
import java.net.URL;
import java.lang.Math;

public class Main extends Application {

	public static void main(String[] args) {
		// If "cli" argument is passed, run the console simulation
		if (args.length > 0 && args[0].equalsIgnoreCase("cli")) {
			System.out.println("Calling user defined console simulation");
			runConsoleSimulation(); // run a standalone SUMO window
		} else {
			System.out.println("Calling launch(args)");
			try {
				launch(args); // call start() in here
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		// Load the FXML file
		// Note: We are in src/, and fxml is in ui/main_ui.fxml relative to project root.
		// When running from compiled classes, we need to find the resource.

		// If running from IDE or command line where 'ui' folder is preserved:
//		File fxmlFile = new File("ui/main_ui.fxml");
		System.out.println("Current working dir: " + System.getProperty("user.dir"));
		
//		String location = "/ui/main_ui.fxml"; // for VS Code
		String location = "main_ui.fxml"; // for Eclipse
		URL fxmlUrl = getClass().getResource(location);
		
//		URL fxmlUrl;
//		if (!fxmlFile.exists()) {
//			// try to find the location of the file
//			System.out.println(System.getProperty("user.dir"));
//			System.out.println("Finished printing current dir");
//			fxmlFile = new File("");
//			if (!fxmlFile.exists()) {
//				// Fallback: try to load from classpath if packaged
//				fxmlUrl = getClass().getResource("/ui/main_ui.fxml");
//				if (fxmlUrl == null) {
//					fxmlUrl = getClass().getResource("/main_ui.fxml");
//				}
//			}
//		}
//		fxmlUrl = fxmlFile.toURI().toURL();

		if (fxmlUrl == null) {
			throw new java.io.FileNotFoundException("Could not find " + location);
		}

		FXMLLoader loader = new FXMLLoader(fxmlUrl);
		Parent root = loader.load();

		primaryStage.setTitle("Real-time Traffic Simulation");
		primaryStage.setScene(new Scene(root));
		primaryStage.show();
	}

	/**
	 * Old console-based simulation logic.
	 * Kept for future implementation/reference.
	 */
	public static void runConsoleSimulation() {
		// Path to SUMO executable
		// String sumoBinary = "C:/Program Files (x86)/Eclipse/Sumo/bin/sumo-gui.exe";
		String sumoBinary = "C:\\Program Files (x86)\\Eclipse\\Sumo\\bin\\sumo-gui.exe"; 

		// Path to SUMO configuration file
		String configFile = ".\\SumoConfig\\G.sumocfg";

		// TraaS syntax, we'll use this for this project
		// Create the connection wrapper
		TraCIConnector conn = new TraCIConnector(sumoBinary, configFile, 50, 0.1);

		if (conn.connect()) {
			VehicleWrapper vehicleWrapper = new VehicleWrapper(conn);
//			TrafficLightWrapper trafficLightWrapper = new TrafficLightWrapper(conn);

			// Run the simulation for 10000 steps, or until finished
			for (int i = 0; i < 10000; i++) {
				if (!conn.step()) {
					break;
				}

				// You can add your code here, for example:
				int vehicleCount = conn.getVehicleCount();
				int trafficlightCount = ((TrafficLightWrapper)conn).getTrafficLightIds().size();
				String trafficlightState = ((TrafficLightWrapper)conn).getTrafficLightState("J37");
				// int busStopCount =  (int) BusStop.getVehicleCount("bs_4");
				double currentSpeed = vehicleWrapper.getSpeed("bus_64_0_0"); // gets the current Speed of x vehicle

				System.out.println("Step " + i + ": Vehicles = " + vehicleCount +  ", TrafficLights = " + trafficlightCount);
				System.out.println("\tCurrent Lights-Color of J37: " + trafficlightState + " Current Bus-64-0-0 Speed: " + Math.round(currentSpeed) + " km/h");

			}

			// Close the connection
			conn.disconnect();
			System.out.println("Simulation finished.");

		} else {
			System.out.println("Could not connect to SUMO.");
		}
	}
}
