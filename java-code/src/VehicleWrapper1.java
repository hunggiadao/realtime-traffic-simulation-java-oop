import de.tudresden.sumo.cmd.Vehicle;
import it.polito.appeal.traci.SumoTraciConnection;

import java.util.Objects;

/**
 * VehicleWrapper1 – SIMPLE & CORRECT version.
 * Encapsulates a single SUMO vehicle.
 * Provides thread-safe access to vehicle data via TraCI.
 * No functional interfaces, no unnecessary abstractions.
 */
public final class VehicleWrapper1 {

    // Default position if TraCI query fails
    private static final double[] ZERO_POS = {0.0, 0.0};

    private final String id;            // Unique vehicle ID
    private final TraCIConnector traci; // Connection to SUMO via TraCI

    /**
     * Constructs a VehicleWrapper1 bound to a specific vehicle ID and TraCI connection.
     * @param id the unique vehicle identifier (non-null)
     * @param traci the TraCI connector (non-null)
     */
    public VehicleWrapper1(String id, TraCIConnector traci) {
        this.id = Objects.requireNonNull(id, "id");
        this.traci = Objects.requireNonNull(traci, "traci");
    }

    public String getId() {
        return id;
    }

    // ================= GETTERS =================

    /**
     * Returns the current speed of the vehicle.
     * Synchronized to ensure thread-safe access to TraCI.
     * @return vehicle speed, or -1.0 if unavailable
     */
    public double getSpeed() {
        try {
            synchronized (traci) {
                SumoTraciConnection conn = traci.getConnection();
                if (conn == null) return -1.0;
                Object res = conn.do_job_get(Vehicle.getSpeed(id));
                return toDouble(res, -1.0);
            }
        } catch (Exception e) {
            return -1.0;
        }
    }

    /**
     * Returns the current position of the vehicle as [x, y].
     * Synchronized for thread safety.
     * @return double array of size 2, or ZERO_POS if unavailable
     */
    public double[] getPosition() {
        try {
            synchronized (traci) {
                SumoTraciConnection conn = traci.getConnection();
                if (conn == null) return ZERO_POS;
                Object res = conn.do_job_get(Vehicle.getPosition(id));
                return toPosition(res);
            }
        } catch (Exception e) {
            return ZERO_POS;
        }
    }

    /**
     * Returns the current road edge ID where the vehicle is located.
     * @return edge ID as String, or null if unavailable
     */
    public String getEdge() {
        try {
            synchronized (traci) {
                SumoTraciConnection conn = traci.getConnection();
                if (conn == null) return null;
                Object res = conn.do_job_get(Vehicle.getRoadID(id));
                return res != null ? res.toString() : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ================= SETTERS =================

    /**
     * Sets the RGBA color of the vehicle in SUMO.
     * @param r red (0–255)
     * @param g green (0–255)
     * @param b blue (0–255)
     * @param a alpha (0–255)
     */
    public void setColor(int r, int g, int b, int a) {
        String rgba = r + "," + g + "," + b + "," + a;
        try {
            synchronized (traci) {
                SumoTraciConnection conn = traci.getConnection();
                if (conn != null) {
                    conn.do_job_set(Vehicle.setParameter(id, "color", rgba));
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Overrides the current speed of the vehicle.
     * @param speed new speed in m/s
     */
    public void overrideSpeed(double speed) {
        try {
            synchronized (traci) {
                SumoTraciConnection conn = traci.getConnection();
                if (conn != null) {
                    conn.do_job_set(Vehicle.setSpeed(id, speed));
                }
            }
        } catch (Exception ignored) {}
    }

    // ================= SNAPSHOT =================

    /**
     * Creates an immutable snapshot of the vehicle's current state.
     * @return VehicleState representing current position, speed, and edge
     */
    public VehicleState updateState() {
        double[] pos = getPosition();
        return new VehicleState(id, pos[0], pos[1], getSpeed(), getEdge());
    }

    // ================= HELPERS =================

    /**
     * Converts an Object to double safely.
     * @param res object to convert
     * @param fallback value to return if conversion fails
     * @return double value or fallback
     */
    private static double toDouble(Object res, double fallback) {
        if (res instanceof Number n) return n.doubleValue();
        try {
            return res != null ? Double.parseDouble(res.toString()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Converts an Object to a 2D position array [x, y].
     * Handles double[] or Object[] returned by TraCI.
     * @param res object from TraCI
     * @return double[2] position or ZERO_POS if conversion fails
     */
    private static double[] toPosition(Object res) {
        if (res instanceof double[] d && d.length >= 2) return d;
        if (res instanceof Object[] a && a.length >= 2) {
            return new double[]{ toDouble(a[0], 0.0), toDouble(a[1], 0.0) };
        }
        return ZERO_POS;
    }
}
