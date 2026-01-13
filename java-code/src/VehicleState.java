import java.util.Objects;

/**
 * VehicleState
 * ----------------
 * Immutable snapshot of a vehicle's state at a specific simulation step.
 *
 * Characteristics:
 * - Immutable: all fields are final.
 * - Represents a single point in time (timestep) in the simulation.
 * - Does NOT reflect live vehicle data; safe for caching and multi-threaded reads.
 * - Edge may be null if the vehicle is off-network, spawned/despawned, or teleported.
 */
public final class VehicleState {

    /** Unique vehicle identifier (non-null) */
    public final String id;

    /** X-coordinate of the vehicle's position in meters */
    public final double x;

    /** Y-coordinate of the vehicle's position in meters */
    public final double y;

    /** Current speed of the vehicle in meters/second */
    public final double speed;

    /** Current road edge ID; may be null if vehicle is off-network */
    public final String edge;

    /**
     * Constructs a new VehicleState snapshot.
     *
     * @param id unique vehicle ID (non-null)
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param speed current speed
     * @param edge road edge ID (may be null)
     * @throws NullPointerException if id is null
     */
    public VehicleState(String id, double x, double y, double speed, String edge) {
        this.id = Objects.requireNonNull(id, "id");
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.edge = edge; // may be null if vehicle is off-network
    }

    /**
     * Returns a human-readable string representation of this state.
     * Useful for logging or debugging.
     */
    @Override
    public String toString() {
        return "VehicleState{" +
                "id='" + id + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", speed=" + speed +
                ", edge='" + edge + '\'' +
                '}';
    }
}
