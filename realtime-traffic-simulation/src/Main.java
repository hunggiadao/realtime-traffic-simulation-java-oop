import de.tudresden.sumo.cmd.Vehicle; 
import it.polito.appeal.traci.SumoTraciConnection;

public class Main {

	public static void main(String[] args) {
		// some testing code by AI

		// 1. Path to your SUMO executable
		String sumoBinary = "C:/Program Files (x86)/Eclipse/Sumo/bin/sumo-gui.exe"; // <-- FIXED

		// 2. Path to your SUMO configuration file
		String configFile = "C:\\Program Files (x86)\\Eclipse\\Sumo\\doc\\tutorial\\quickstart\\data\\quickstart.sumocfg"; // <-- FIXED

		// 3. Create the connection
		SumoTraciConnection conn = new SumoTraciConnection(sumoBinary, configFile);
		conn.addOption("start", "true"); // Start simulation automatically

		try {
			// 4. Start SUMO
			conn.runServer(); // <-- Corrected (no 'true')

			// 5. Run the simulation for 1000 steps
			for (int i = 0; i < 1000; i++) {
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
	}

}





































