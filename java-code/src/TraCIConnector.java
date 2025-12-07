import it.polito.appeal.traci.SumoTraciConnection;
import de.tudresden.sumo.cmd.*;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.cmd.Trafficlight;

import java.io.*; // for throwing exceptions
import java.util.*; // for using List interfaces

/**
 * Wrapper class for managing the TraCI connection to SUMO.
 * Handles connection lifecycle and basic simulation stepping.
 * 
 * Each instance of this class manages a running simulation
 * Members include: SUMO connection var, list of vehicles, list of traffic lights (junctions)
 */
public class TraCIConnector {
	private static String sumoBinary; // same across all objects

	private final String configFile;
	private SumoTraciConnection connection;
	private double stepLengthSeconds; // default is 1, but can change
	private int delay; // we make the default 50
	private boolean isConnected;
	private int currentStep;
	// currently unused members, can implement later
	//	private List<VehicleWrapper> vehicles;
	//	private List<TrafficLightWrapper> trafficLights;
	//	private List<BusStop> busStops;

	// constructor with default data
	public TraCIConnector(String sumoBinary, String configFile) {
		//  Initialize constructor
		TraCIConnector.sumoBinary = sumoBinary;
		this.configFile = configFile;
		this.connection = new SumoTraciConnection(sumoBinary, configFile);
		this.stepLengthSeconds = 1;
		this.delay = 50;
		this.isConnected = false;
		this.currentStep = 0;
		// currently unused members, can implement later
		//		this.vehicles = new ArrayList<VehicleWrapper>();
		//		this.trafficLights = new ArrayList<TrafficLightWrapper>();
	}
	// constructor with custom stepLength
	public TraCIConnector(String sumoBinary, String configFile, double stepLengthSeconds) {
		//  Initialize constructor
		// call the default constructor, then change values we want
		this(sumoBinary, configFile);
		this.stepLengthSeconds = stepLengthSeconds;
	}
	// constructor with custom delay and stepLength
	public TraCIConnector(String sumoBinary, String configFile, int delay, double stepLengthSeconds) {
		//  Initialize constructor
		// call the default constructor, then change values we want
		this(sumoBinary, configFile);
		this.delay = delay;
		this.stepLengthSeconds = stepLengthSeconds;
	}

	public boolean connect() {
		if (connection == null) {
			return false;
		}
		//  Start SUMO
		try {
			connection.addOption("start", "false");
			connection.addOption("delay", this.delay + "");
			connection.addOption("step-length", Double.toString(stepLengthSeconds));
			connection.runServer(); // throws IOException
			this.isConnected = true;
			this.currentStep = 0;
			System.out.println("TraCIConnector: Connected to SUMO");
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public boolean step() {
		//  Advance simulation by one step
		if (connection == null || !this.isConnected) {
			return false;
		}
		try {
			connection.do_timestep();
			this.currentStep++;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	/**
	 * update the vehicles list and return its size
	 * 
	 * @return the number of vehicles in the simulation
	 */
	public int getVehicleCount() {
		//  Retrieve the vehicle count from SUMO
		if (connection == null || !this.isConnected) {
			return 0;
		}
		//		int vehicleCount = 0;
		//		try {
		//			@SuppressWarnings("unchecked")
		//			List<String> vehicle_ids = (ArrayList<String>)connection.do_job_get(Vehicle.getIDList());
		//			vehicleCount = vehicle_ids.size();
		//		} catch (Exception e) {
		//			e.printStackTrace();
		//		}
		//		if (vehicleCount == (int) connection.do_job_get(Vehicle.getIDCount())) {
		//			return vehicleCount;
		//		} else {
		//			return (int) connection.do_job_get(Vehicle.getIDCount());
		//		}

		try {
			return (int) connection.do_job_get(Vehicle.getIDCount());
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	/**
	 * return the size of the traffic light list
	 * 
	 * @return the number of traffic lights in the simulation
	 */
	public int getTrafficLightCount() {
		//  Retrieve the traffic light count from SUMO
		if (connection == null || !this.isConnected) {
			return 0;
		}
		int lightCount = 0;
		try {
			@SuppressWarnings("unchecked")
			List<String> light_ids = (ArrayList<String>)connection.do_job_get(Trafficlight.getIDList());
			lightCount = light_ids.size();
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			if (lightCount == (int) connection.do_job_get(Trafficlight.getIDCount())) {
				return lightCount;
			} else {
				return (int) connection.do_job_get(Trafficlight.getIDCount());
			}
		} catch (Exception e) {
			return 0;
		}

	}

	public int getCurrentStep() {
		if (connection == null || !this.isConnected) {
			return 0;
		}
		return this.currentStep;
	}

	public void disconnect() {
		//  Close connection
		if (connection == null || !this.isConnected) {
			return;
		}
		try {
			connection.close();
			System.out.println("TraCIConnector: Connection closed");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			this.isConnected = false;
		}
	}

	public boolean isConnected() {
		// Return connection status
		if (connection == null) {
			return false;
		}
		return this.isConnected;
	}

	public double getSimTimeSeconds() {
		if (connection == null || !this.isConnected) {
			return 0;
		}
		return this.currentStep * this.stepLengthSeconds;
	}

	public SumoTraciConnection getConnection() {
		return connection; // null if connection doesn't exist
	}
}
















