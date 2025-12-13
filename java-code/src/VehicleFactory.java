import de.tudresden.sumo.cmd.Vehicle;
import java.util.Objects;

/**
 * VehicleFactory
 * ----------------
 * Applies configuration commands to EXISTING vehicles via TraCI.
 * Does NOT create vehicles and does NOT manage threading.
 * All operations are synchronized on the TraCI connector to ensure thread safety.
 */
public final class VehicleFactory {

    private final TraCIConnector traci; // Connection to SUMO via TraCI

    /**
     * Constructs a VehicleFactory bound to a TraCI connector.
     * @param traci the TraCI connector (non-null)
     */
    public VehicleFactory(TraCIConnector traci) {
        this.traci = Objects.requireNonNull(traci, "traci");
    }

    /**
     * Configure an existing vehicle in SUMO.
     * Sets max speed, actual speed (via ratio), and RGBA color.
     * Values are validated and clamped before sending commands to SUMO.
     *
     * @param id the vehicle ID
     * @param maxSpeed maximum speed of the vehicle in m/s
     * @param speedRatio ratio of max speed to apply (0.0 to 1.0)
     * @param r red component (0–255)
     * @param g green component (0–255)
     * @param b blue component (0–255)
     * @param a alpha component (0–255)
     */
    public void configureVehicle(
            String id,
            double maxSpeed,
            double speedRatio,
            int r, int g, int b, int a
    ) {
        if (id == null || id.isEmpty()) return;   // invalid vehicle ID
        if (!isValidSpeed(maxSpeed)) return;      // invalid max speed

        // Clamp speed ratio to [0.0, 1.0] and compute actual speed
        double clampedRatio = clamp(speedRatio, 0.0, 1.0);
        double actualSpeed = maxSpeed * clampedRatio;

        // Prepare RGBA color string for TraCI
        String color = r + "," + g + "," + b + "," + a;

        try {
            synchronized (traci) {
                if (!traci.isConnected()) return;   // skip if connection is lost

                var conn = traci.getConnection();
                conn.do_job_set(Vehicle.setMaxSpeed(id, maxSpeed));   // set max speed
                conn.do_job_set(Vehicle.setSpeed(id, actualSpeed));   // set current speed
                conn.do_job_set(Vehicle.setParameter(id, "color", color)); // set vehicle color
            }
        } catch (Exception e) {
            System.err.println("VehicleFactory: failed to configure vehicle " + id);
            e.printStackTrace();
        }
    }

    // ---------------- helpers ----------------

    /**
     * Checks whether a speed value is valid (non-negative and not NaN).
     * @param speed speed to validate
     * @return true if speed is valid
     */
    private static boolean isValidSpeed(double speed) {
        return !Double.isNaN(speed) && speed >= 0.0;
    }

    /**
     * Clamps a value to the specified range [min, max].
     * @param v value to clamp
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @return clamped value
     */
    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(v, max));
    }
}
