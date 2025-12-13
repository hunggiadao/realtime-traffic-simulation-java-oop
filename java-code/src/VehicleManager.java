import de.tudresden.sumo.cmd.Vehicle;
import java.util.*;

/**
 * VehicleManager – Manages SUMO vehicles and their cached states.
 * Owns VehicleWrapper instances bound to a single TraCIConnector.
 * All methods assume they are called from a single simulation thread.
 * Optimized for performance with large vehicle counts.
 */
public final class VehicleManager {

    private final TraCIConnector traci; // Connection to SUMO via TraCI

    // Mapping from vehicle ID to its wrapper object
    private final Map<String, VehicleWrapper1> wrappers;

    // Mapping from vehicle ID to latest immutable snapshot (VehicleState)
    private final Map<String, VehicleState> stateCache;

    /**
     * Constructs a VehicleManager with pre-allocated maps for efficiency.
     * @param traci TraCI connector (non-null)
     * @param estimatedVehicleCount initial map capacity for large fleets
     */
    public VehicleManager(TraCIConnector traci, int estimatedVehicleCount) {
        this.traci = Objects.requireNonNull(traci, "traci");
        this.wrappers = new HashMap<>(estimatedVehicleCount);
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
     * Retrieves the wrapper for a specific vehicle by its ID.
     * @param id vehicle ID
     * @return VehicleWrapper1 instance or null if not present
     */
    public VehicleWrapper1 getVehicle(String id) {
        return wrappers.get(id);
    }

    /**
     * Synchronizes the internal vehicle list with SUMO.
     * - Adds new vehicles discovered in SUMO
     * - Removes vehicles that left the simulation
     */
    public void refreshVehicles() {
        List<String> ids = fetchVehicleIds();
        if (ids == null) return;

        Set<String> idSet = new HashSet<>(ids); // for fast removal check

        // Add new vehicles
        for (String id : ids) {
            wrappers.computeIfAbsent(id, v -> new VehicleWrapper1(v, traci));
        }

        // Remove vehicles that are no longer present
        wrappers.keySet().removeIf(id -> !idSet.contains(id));
    }

    /**
     * Updates and caches the state of all known vehicles.
     * Must be called AFTER refreshVehicles() to ensure consistency.
     */
    public void updateAllStates() {
        for (VehicleWrapper1 wrapper : wrappers.values()) {
            VehicleState state = wrapper.updateState();
            stateCache.put(state.id, state); // overwrite existing snapshot
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
        return wrappers.size();
    }

    // ---------------- internal ----------------

    /**
     * Fetches the current list of vehicle IDs from SUMO.
     * Synchronized to ensure thread-safe access to TraCI.
     * @return List of vehicle IDs, or null if an error occurs
     */
    @SuppressWarnings("unchecked")
    private List<String> fetchVehicleIds() {
        try {
            synchronized (traci) {
                return (List<String>) traci
                        .getConnection()
                        .do_job_get(Vehicle.getIDList());
            }
        } catch (Exception e) {
            System.err.println("VehicleManager: failed to fetch vehicle IDs");
            e.printStackTrace();
            return null;
        }
    }
}
