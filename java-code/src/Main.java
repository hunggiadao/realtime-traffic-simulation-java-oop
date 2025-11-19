import de.tudresden.sumo.cmd.Vehicle; 
import it.polito.appeal.traci.SumoTraciConnection;
import org.eclipse.sumo.libtraci.*;

public class Main {

	public static void main(String[] args) {
		// some testing code by AI

		// 1. Path to your SUMO executable
		String sumoBinary = "C:/Program Files (x86)/Eclipse/Sumo/bin/sumo-gui.exe"; // <-- FIXED

		// 2. Path to your SUMO configuration file
		String configFile = "C:\\Program Files (x86)\\Eclipse\\Sumo\\doc\\tutorial\\quickstart\\data\\quickstart.sumocfg"; // <-- FIXED

//		String configFile = "C:\\Program Files (x86)\\Eclipse\\Sumo\\doc\\tutorial\\quickstart\\data\\quickstart.sumocfg";

		// TraaS syntax, we'll use this for this project
		// 3. Create the connection
		SumoTraciConnection conn = new SumoTraciConnection(sumoBinary, configFile);
		conn.addOption("start", "true"); // Start simulation automatically
		conn.addOption("delay", "50");
		conn.addOption("step-length", "0.1");

		try {
			// 4. Start SUMO
			conn.runServer(); // <-- Corrected (no 'true')

			// 5. Run the simulation for 1000 steps
			for (int i = 0; i < 10000; i++) {
				conn.do_timestep();

				// You can add your code here, for example:
				int vehicleCount = (int) conn.do_job_get(Vehicle.getIDCount()); // <-- Corrected
				System.out.println("Step " + i + ": Vehicles = " + vehicleCount);
			}

			// 6. Close the connection
			conn.close();
			System.out.println("Simulation finished.");

		} catch (Exception e) {
			e.printStackTrace();
		}

		// TraCI syntax, not used for this project
		//		Simulation.preloadLibraries();
		//		Simulation.start(new StringVector(new String[] {"sumo", "-n", "test1.net.xml"}));
		//		for (int i = 0; i < 5; i++) {
		//			Simulation.step();
		//		}
		//		Simulation.close();
	}

}





































