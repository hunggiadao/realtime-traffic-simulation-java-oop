import it.polito.appeal.traci.SumoTraciConnection;
import de.tudresden.sumo.cmd.*;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.cmd.Edge;
import de.tudresden.sumo.cmd.Lane;
import de.tudresden.sumo.objects.SumoPosition2D;

import java.io.*; // for throwing exceptions
import java.util.*; // for using List interfaces
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper class for managing the TraCI connection to SUMO.
 * Handles connection lifecycle and basic simulation stepping.
 * 
 * Each instance of this class manages a running simulation
 * Members include: SUMO connection var, list of vehicles, list of traffic lights (junctions)
 */
public class TraCIConnector {
	private static final Logger LOGGER = Logger.getLogger(TraCIConnector.class.getName());
	
	// member fields
	private static String sumoBinary; // same across all objects
	private String configFile;
	private SumoTraciConnection connection;
	private int stepLengthMs; // default 1000, but we want 50
	private boolean isConnected;
	private int currentStep;
	
	// Cached network boundary (SUMO world coordinates)
	private double netMinX;
	private double netMinY;
	private double netMaxX;
	private double netMaxY;
	private boolean netBoundsInitialized;
	
	// currently unused members, can implement later
	
	/**
	 * Constructor with no data, never used
	 * Unused
	 */
	public TraCIConnector() {
		// Initialize constructor
		TraCIConnector.sumoBinary = null;
		this.configFile = null;
		this.connection = null;
		this.stepLengthMs = 0;
		this.isConnected = false;
		this.currentStep = 0;
	}
	/**
	 * Constructor with default data
	 * @param sumoBinary
	 * @param configFile
	 */
	public TraCIConnector(String sumoBinary, String configFile) {
		// Initialize constructor
		TraCIConnector.sumoBinary = sumoBinary;
		this.configFile = configFile;
		this.connection = new SumoTraciConnection(sumoBinary, configFile);
		this.stepLengthMs = 1000; // SUMO default
		this.isConnected = false;
		this.currentStep = 0;
	}
	/**
	 * Constructor with custom stepLengthSeconds
	 * @param sumoBinary
	 * @param configFile
	 * @param stepLengthSeconds in seconds
	 */
	public TraCIConnector(String sumoBinary, String configFile, double stepLengthSeconds) {
		// Initialize constructor
		// call the default constructor, then change values we want
		this(sumoBinary, configFile);
		this.stepLengthMs = (int)(stepLengthSeconds * 1000);
	}
	/**
	 * Constructor with custom stepLengthMs
	 * @param sumoBinary
	 * @param configFile
	 * @param stepLengthMs in ms
	 */
	public TraCIConnector(String sumoBinary, String configFile, int stepLengthMs) {
		// Initialize constructor
		// call the default constructor, then change values we want
		this(sumoBinary, configFile);
		this.stepLengthMs = stepLengthMs;
	}
	
	/**
	 * Connect this TraCIConnector to a live SUMO simulation
	 * @return True on successful connection, False otherwise
	 */
	public boolean connect() {
		if (connection == null) {
			return false;
		}
		//  Start SUMO
		try {
			connection.addOption("start", "false"); // set autostart to false
			// delay and step-length must be equal to simulate real-time
			connection.addOption("delay", this.stepLengthMs + ""); // in ms
			connection.addOption("step-length", Double.toString((double)this.stepLengthMs / 1000)); // in seconds
			connection.addOption("lateral-resolution", "0.1"); // makes lane changing smoother
			connection.runServer(); // throws IOException
			this.isConnected = true;
			this.currentStep = 0;
			LOGGER.info("Connected to SUMO");
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to connect to SUMO", e);
			return false;
		}

		return true;
	}
	
	/**
	 * Advance the SUMO sim by 1 step
	 * @return True on successful step, False otherwise
	 */
	public boolean step() {
		//  Advance simulation by one step
		if (connection == null || !this.isConnected) {
			return false;
		}
		try {
			connection.do_timestep();
			this.currentStep++;
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Step failed", e);
			handleConnectionError(e);
			return false;
		}

		return true;
	}
	
	/**
	 * Get current SUMO step count
	 * @return current step of the simulation
	 */
	public int getCurrentStep() {
		if (connection == null || !this.isConnected) {
			return 0;
		}
		return this.currentStep;
	}
	
	/**
	 * Disconnect from a SUMO simulation, do nothing if SUMO is not connected
	 */
	public void disconnect() {
		//  Close connection
		if (connection == null || !this.isConnected) {
			return;
		}
		try {
			connection.close();
			LOGGER.info("Connection closed");
		} catch (IllegalStateException ignored) {
			// connection may already be closed (e.g. SUMO side terminated)
		} catch (Exception e) {
			LOGGER.log(Level.FINE, "Error while closing connection", e);
		} finally {
			this.isConnected = false;
		}
	}

	/**
	 * Mark the connector as disconnected after a TraCI failure.
	 * SUMO may terminate on its own (simulation end) which closes the socket.
	 */
	public void handleConnectionError(Exception e) {
		this.isConnected = false;
		try {
			if (this.connection != null) {
				this.connection.close();
			}
		} catch (Exception ignored) {
		}
	}
	
	/**
	 * Check if TraCIConnector is connected to a live SUMO sim
	 * @return True if connected, False otherwise
	 */
	public boolean isConnected() {
		// Return connection status
		if (connection == null) {
			return false;
		}
		return this.isConnected;
	}
	
	/**
	 * Internal function for debugging, not meant to be used by the user
	 * @return number of elapsed seconds in the simulation
	 */
	public double getSimTimeSeconds() {
		if (connection == null || !this.isConnected) {
			return 0;
		}
		return this.currentStep * ((double)this.stepLengthMs) / 1000;
	}
	
	/**
	 * Return a SumoTraciConnection object, the one directly used to invoke SUMO commands
	 * @return SumoTraciConnection object
	 */
	public SumoTraciConnection getConnection() {
		if (!this.isConnected) return null;
		return connection; // null if connection doesn't exist
	}

	/**
	 * Decide whether an edge is suitable as a spawn edge.
	 *
	 * Simple, robust rule:
	 * - Skip internal edges (IDs starting with ':').
	 * - Skip very short edges (likely pure intersection connectors) using
	 *   lane 0 length; if length cannot be obtained, also skip.
	 */
	private boolean isGoodSpawnEdge(String edgeId) {
		if (edgeId == null || edgeId.isEmpty()) {
			return false;
		}
		// Internal / junction edges start with ':' in SUMO, not good
		if (edgeId.startsWith(":")) {
			return false;
		}
		try {
			String laneId = edgeId + "_0";
			Object lenObj = connection.do_job_get(Lane.getLength(laneId));
			if (lenObj instanceof Double) {
				double len = (Double) lenObj;
				// Tune this threshold as needed; short edges are usually
				// intersection stubs where spawning looks bad.
				final double minLen = 40.0; // meters
				return len >= minLen;
			}
			// If length is not a Double, treat as not spawnable.
			return false;
		} catch (Exception e) {
			// If we cannot determine length (e.g. lane not found), be
			// conservative and do NOT offer this edge for spawning.
			return false;
		}
	}

	/**
	 * Get list of all edge IDs in the network
	 * @return List of edge IDs
	 */
	public List<String> getGoodSpawnEdgeIds() {
		List<String> edges = new ArrayList<>();
		if (connection == null || !this.isConnected) {
			return edges;
		}
		
		// check to see which edges are good spawning edges out of all
		try {
			Object response = connection.do_job_get(Edge.getIDList());
			if (response instanceof String[]) {
				for (String s : (String[]) response) {
					if (isGoodSpawnEdge(s)) {
						edges.add(s);
					}
				}
			} else if (response instanceof List<?>) {
				for (Object o : (List<?>) response) {
					String s = String.valueOf(o);
					if (isGoodSpawnEdge(s)) {
						edges.add(s);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to retrieve edge IDs", e);
		}
		return edges;
	}

	/**
	 * Connects to SUMO or throws a {@link SimulationException} on failure.
	 *
	 * @throws SimulationException when connection cannot be established
	 */
	public void connectOrThrow() throws SimulationException {
		if (!connect()) {
			throw new SimulationException("Failed to connect to SUMO");
		}
	}
	/**
	 * Advances the simulation by one step or throws a {@link SimulationException}.
	 *
	 * @throws SimulationException when stepping fails or not connected
	 */
	public void stepOrThrow() throws SimulationException {
		if (!isConnected()) {
			throw new SimulationException("Not connected");
		}
		if (!step()) {
			throw new SimulationException("SUMO step failed");
		}
	}
}
