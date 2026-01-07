import java.util.*;

/**
 * VehicleManager – Manages SUMO vehicles and their cached states.
 * Owns VehicleWrapper instances bound to a single TraCIConnector.
 * All methods assume they are called from a single simulation thread.
 * Optimized for performance with large vehicle counts.
 */
public final class VehicleManager {

    private final TraCIConnector traci; // Connection to SUMO via TraCI
	private final VehicleWrapper vehicleWrapper;

    // Current set of known vehicle IDs
    private final Set<String> vehicleIds;

    // Mapping from vehicle ID to latest immutable snapshot (VehicleState)
    private final Map<String, VehicleState> stateCache;

    /**
     * Constructs a VehicleManager with pre-allocated maps for efficiency.
     * @param traci TraCI connector (non-null)
     * @param estimatedVehicleCount initial map capacity for large fleets
     */
    public VehicleManager(TraCIConnector traci, int estimatedVehicleCount) {
        this.traci = Objects.requireNonNull(traci, "traci");
		this.vehicleWrapper = new VehicleWrapper(this.traci);
		this.vehicleIds = new HashSet<>(estimatedVehicleCount);
        this.stateCache = new HashMap<>(estimatedVehicleCount);
    }

    /**
     * Default constructor with a reasonable initial capacity.
     * @param traci TraCI connector (non-null)
     */
    public VehicleManager(TraCIConnector traci) {
        this(traci, 128);
    }

        /**
         * Checks whether a vehicle ID is currently known to the manager.
         * @param id vehicle ID
         * @return true if present
         */
    public boolean hasVehicle(String id) {
		return vehicleIds.contains(id);
	}

    /**
     * Synchronizes the internal vehicle list with SUMO.
     * - Adds new vehicles discovered in SUMO
     * - Removes vehicles that left the simulation
     */
    public void refreshVehicles() {
		List<String> ids = vehicleWrapper.getVehicleIds();
		vehicleIds.clear();
		vehicleIds.addAll(ids);
    }

    /**
     * Updates and caches the state of all known vehicles.
     * Must be called AFTER refreshVehicles() to ensure consistency.
     */
    public void updateAllStates() {
		vehicleWrapper.applyPendingUpdates();
		for (String id : vehicleIds) {
			VehicleState state = vehicleWrapper.updateState(id);
			stateCache.put(state.id, state);
		}
    }

    /**
     * Returns an immutable snapshot of all cached vehicle states.
     * Safe for reading without affecting internal state.
     * @return Map of vehicle ID to VehicleState
     */
    public Map<String, VehicleState> getAllStates() {
        return Map.copyOf(stateCache);
    }

    /**
     * Returns the number of currently managed vehicles.
     * @return vehicle count
     */
    public int getVehicleCount() {
        return vehicleIds.size();
    }
}
