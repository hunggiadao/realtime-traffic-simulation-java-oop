import de.tudresden.sumo.cmd.*;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.cmd.Route;
import de.tudresden.sumo.cmd.Trafficlight;
import de.tudresden.sumo.objects.SumoStringList;
import de.tudresden.sumo.objects.SumoColor;
import it.polito.appeal.traci.SumoTraciConnection;

import de.tudresden.sumo.config.Constants;
import de.tudresden.sumo.objects.SumoTLSProgram;
import de.tudresden.sumo.util.SumoCommand;

import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Point2D;

/**
 * Class for retrieving and setting vehicle data in the simulation
 * Provides thread-safe access to vehicle data via TraCI.
 */
public abstract class VehicleWrapper extends TraCIConnector {
    private static final Logger LOGGER = Logger.getLogger(VehicleWrapper.class.getName());

    // Some SUMO operations (e.g. setColor) may fail if the vehicle is not yet inserted.
    // Queue them and retry on subsequent UI refresh/steps.
    private final Map<String, SumoColor> pendingColors = new HashMap<>();
    private final Map<String, Double> pendingMaxSpeeds = new HashMap<>();
    
    /**
     * Returns the number of vehicles currently running within the scenario
     */
    public int getVehicleCount() {
        if (this.getConnection() == null || !this.isConnected()) return 0;
        try {
            return (int) this.getConnection().do_job_get(Vehicle.getIDCount());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1; // error for whatever reason
    }
    /**
     * Returns a list of ids of all vehicles currently running within the scenario
     * @return
     */
    @SuppressWarnings("unchecked")
	public List<String> getVehicleIds() {
    	if (this.getConnection() == null || !this.isConnected()) {
    		return new ArrayList<String>();
    	}
        try {
        	Object response = this.getConnection().do_job_get(Vehicle.getIDList());
        	if (response instanceof String[]) {
                return Arrays.asList((String[]) response);
            } else if (response instanceof List) {
                return (List<String>) response;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<String>(); // error for whatever reason
    }
    /**
     * Fetch current vehicle rows (id, speed, edge) from SUMO.
     * @return rows
     */
    public List<VehicleRow> getVehicleRows() {
        List<VehicleRow> rows = new ArrayList<>();
        if (this.getConnection() == null || !this.isConnected()) {
            return rows;
        }

        applyPendingUpdates();

        try {
            Object idsObj = this.getConnection().do_job_get(Vehicle.getIDList());
            List<String> ids = new ArrayList<>();
            if (idsObj instanceof String[]) {
                for (String s : (String[]) idsObj) ids.add(s);
            } else if (idsObj instanceof List<?>) {
                for (Object o : (List<?>) idsObj) ids.add(String.valueOf(o));
            }

            for (String id : ids) {
				        javafx.scene.paint.Color color = javafx.scene.paint.Color.RED;
              
                double speed = (double) this.getConnection().do_job_get(Vehicle.getSpeed(id));
                String edge = (String) this.getConnection().do_job_get(Vehicle.getRoadID(id));
              
                Object cl = connector.getConnection().do_job_get(Vehicle.getColor(id));
                if (cl instanceof SumoColor) {
                    SumoColor sc = (SumoColor) cl;
                    color = javafx.scene.paint.Color.rgb(sc.r, sc.g, sc.b, sc.a / 255.0);
                }
              
                rows.add(new VehicleRow(id, speed, edge, color));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch vehicle rows", e);
        }
        return rows;
    }
    // helper function to convert an Object type to a double type
    @SuppressWarnings("unused")
	private double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    /**
     * Fetch current vehicle positions from SUMO.
     */
    public Map<String, Point2D> getVehiclePositions() {
        Map<String, Point2D> out = new HashMap<>();
        if (this.getConnection() == null || !this.isConnected()) {
            return out;
        }

        applyPendingUpdates();

        try {
            Object idsObj = this.getConnection().do_job_get(Vehicle.getIDList());
            List<String> ids = new ArrayList<>();
            if (idsObj instanceof String[]) {
                for (String s : (String[]) idsObj) ids.add(s);
            } else if (idsObj instanceof List<?>) {
                for (Object o : (List<?>) idsObj) {
                    ids.add(String.valueOf(o));
                }
            }

            for (String id : ids) {
                Object posObj = this.getConnection().do_job_get(Vehicle.getPosition(id));
                Point2D p = extractPoint(posObj);
                if (p != null) out.put(id, p);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch vehicle positions", e);
        }
        return out;
    }
    // private helper function
    private Point2D extractPoint(Object posObj) {
        if (posObj == null) return null;
        if (posObj instanceof double[]) {
            double[] arr = (double[]) posObj;
            if (arr.length >= 2) return new Point2D(arr[0], arr[1]);
        }
        if (posObj instanceof List<?>) {
            List<?> list = (List<?>) posObj;
            if (list.size() >= 2) {
            	double x = Double.parseDouble(list.get(0).toString());
                double y = Double.parseDouble(list.get(1).toString());
                return new Point2D(x, y);
//                try {
//                } catch (NumberFormatException ignored) {}
            }
        }
        try {
            Class<?> cls = posObj.getClass();
            Field fx = cls.getField("x");
            Field fy = cls.getField("y");
            double x = fx.getDouble(posObj);
            double y = fy.getDouble(posObj);
            return new javafx.geometry.Point2D(x, y);
        } catch (Exception ignored) {
        }
        return null;
    }
    /**
     * Returns the speed of the named vehicle within the last step [m/s]
     * @param id
     * @return
     */
    public double getSpeed(String vehId) {
        if (this.getConnection() == null || !this.isConnected()) return 0;
        try {
            return (double) this.getConnection().do_job_get(Vehicle.getSpeed(vehId));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
    /**
     * Sets the speed in m/s for the named vehicle within the last step.
Calling with speed=-1 hands the vehicle control back to SUMO.
     */
    public void setSpeed(String vehId, double newSpeed) {
    	if (this.getConnection() == null || !this.isConnected()) {
    		System.out.println("Error. Connection does not exist");
    	};
        try {
            this.getConnection().do_job_set(Vehicle.setSpeed(vehId, newSpeed));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Returns the position(two doubles) of the named vehicle (center of the front bumper) within the last step [m,m];
     * @param vehId
     * @return
     */
    public double[] getPosition(String vehId) {
    	if (this.getConnection() == null || !this.isConnected()) return new double[] {0, 0};
        try {
            return (double[]) this.getConnection().do_job_get(Vehicle.getPosition(vehId));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new double[] {0, 0};
    }
    /**
     * Returns the id of the edge the named vehicle was at within the last step; error value: ""
     * Returns the current road edge ID where the vehicle is located.
     * @return edge ID as String, or null if unavailable
     */
    public String getEdgeId(String vehId) {
    	if (this.getConnection() == null || !this.isConnected()) return "";
        try {
        	return (String) this.getConnection().do_job_get(Vehicle.getRoadID(vehId));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ""; // error
    }
    /**
     * Returns the id of the lane the named vehicle was at within the last step; error value: ""
     * @param vehId
     * @return
     */
    public String getLaneId(String vehId) {
    	if (this.getConnection() == null || !this.isConnected()) return "";
        try {
        	return (String) this.getConnection().do_job_get(Vehicle.getLaneID(vehId));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ""; // error
    }
    /**
     * Returns the index of the lane the named vehicle was at within the last step; error value: -2^30
     * @param vehId
     * @return
     */
    public int getLaneIndex(String vehId) {
    	if (this.getConnection() == null || !this.isConnected()) return -2^30;
        try {
        	return (int) this.getConnection().do_job_get(Vehicle.getLaneIndex(vehId));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -2^30; // error
    }
    /**
     * Returns the color (RGBA) (4 integers) of this vehicle type.
     * Can use vehId for this too.
     * This does not return the currently visible color in the GUI
     * but the color value set in the XML file or via TraCI.
     * @param typeId
     * @return
     */
    public int[] getColor(String typeId) {
    	if (this.getConnection() == null || !this.isConnected()) return new int[] {0, 0, 0, 0};
        try {
        	return (int[]) this.getConnection().do_job_get(Vehicle.getColor(typeId));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new int[] {0, 0, 0, 0}; // error
    }
    /**
     * Sets the color of this type.
     * If called in the context of a person or vehicle,
     * it will change the value just for the single instance.
     * @param typeId
     * @param newColor (4 integers r, g, b, a)
     */
    public void setColor(String typeId, int[] newColor) {
    	if (this.getConnection() == null || !this.isConnected()) {
    		System.out.println("Error. Connection does not exist");
    	}
    	try {
    		SumoColor color = new SumoColor(newColor[0], newColor[1], newColor[2], newColor[3]);
        	this.getConnection().do_job_set(Vehicle.setColor(typeId, color));
//	        try {
//	            synchronized (traci) {
//	                SumoTraciConnection conn = traci.getConnection();
//	                if (conn != null) {
//	                    conn.do_job_set(Vehicle.setParameter(id, "color", rgba));
//	                }
//	            }
//	        } catch (Exception ignored) {}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void addVehicle(String vehicleId, String routeOrEdgeId, double speed, javafx.scene.paint.Color color) {
        if (this.getConnection() == null || !this.isConnected()) return;
        try {
            // Check if routeOrEdgeId is a known route
            boolean isRoute = false;
            try {
                @SuppressWarnings("unchecked")
                List<String> routes = (List<String>) this.getConnection().do_job_get(Route.getIDList());
                if (routes.contains(routeOrEdgeId)) {
                    isRoute = true;
                }
            } catch (Exception e) {
                // ignore, assume not a route or error fetching
            }

            String finalRouteId = routeOrEdgeId;

            if (!isRoute) {
                // Assume it's a "dead street" / entry edge where we want to spawn vehicles.
                // Build a route that starts on this edge and heads into the network using a precomputed path.
                finalRouteId = "route_" + vehicleId;

                SumoStringList edgeList = new SumoStringList();
                edgeList.add(routeOrEdgeId);

                // For now, keep the injected route as just the chosen edge.
                // This guarantees a valid route and avoids "no valid route" errors when spawning many vehicles.
                // Vehicles will travel along this edge and disappear at its downstream end.

                try {
                    this.getConnection().do_job_set(Route.add(finalRouteId, edgeList));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to create route " + finalRouteId + " for edge " + routeOrEdgeId, e);
                    // If this fails, maybe the edge doesn't exist. We'll try to add vehicle anyway, it might fail.
                }
            }

            // Add vehicle using addFull (new-style) parameters with textual depart/departLane.
            String depart = "now";
            String departLane = "first";
            String departPos = "base";
            String departSpeed = "0";

            String arrivalLane = "current";
            String arrivalPos = "max";
            String arrivalSpeed = "current";

            String fromTAZ = "";
            String toTAZ = "";
            String line = "";
            int personCapacity = 0;
            int personNumber = 0;

            try {
                conn.do_job_set(Vehicle.addFull(
                        vehicleId,
                        finalRouteId,
                        "DEFAULT_VEHTYPE",
                        depart,
                        departLane,
                        departPos,
                        departSpeed,
                        arrivalLane,
                        arrivalPos,
                        arrivalSpeed,
                        fromTAZ,
                        toTAZ,
                        line,
                        personCapacity,
                        personNumber));
                LOGGER.info("Injected vehicle: " + vehicleId + " on route/edge: " + finalRouteId);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error adding vehicle " + vehicleId, e);
                // Try with empty type if DEFAULT_VEHTYPE fails
                try {
                    conn.do_job_set(Vehicle.addFull(
                            vehicleId,
                            finalRouteId,
                            "",
                            depart,
                            departLane,
                            departPos,
                            departSpeed,
                            arrivalLane,
                            arrivalPos,
                            arrivalSpeed,
                            fromTAZ,
                            toTAZ,
                            line,
                            personCapacity,
                            personNumber));
                    LOGGER.info("Injected vehicle (empty type): " + vehicleId);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Retry with empty type failed for " + vehicleId, ex);
                }
            }
            // Add vehicle: id, type="DEFAULT_VEHTYPE", route, depart=-2 (now), pos=0, speed=0, lane=0
            // this.getConnection().do_job_set(Vehicle.add(vehicleId, "DEFAULT_VEHTYPE", finalRouteId, -2, 0.0, 0.0, (byte) 0));

            // Set color
            int r = (int) (color.getRed() * 255);
            int g = (int) (color.getGreen() * 255);
            int b = (int) (color.getBlue() * 255);
            SumoColor sumoColor = new SumoColor(r, g, b, 255);
            try {
                conn.do_job_set(Vehicle.setColor(vehicleId, sumoColor));
            } catch (Exception e) {
                pendingColors.put(vehicleId, sumoColor);
            }

            // Set max speed
            if (speed > 0) {
                try {
                    conn.do_job_set(Vehicle.setMaxSpeed(vehicleId, speed));
                } catch (Exception e) {
                    pendingMaxSpeeds.put(vehicleId, speed);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to add vehicle " + vehicleId, e);
        }
    }

    public void applyPendingUpdates() {
        if (!connector.isConnected() || connector.getConnection() == null) return;
        it.polito.appeal.traci.SumoTraciConnection conn = connector.getConnection();

        if (!pendingColors.isEmpty()) {
            java.util.List<String> done = new java.util.ArrayList<>();
            for (Map.Entry<String, SumoColor> e : pendingColors.entrySet()) {
                try {
                    conn.do_job_set(Vehicle.setColor(e.getKey(), e.getValue()));
                    done.add(e.getKey());
                } catch (Exception ignored) {
                }
            }
            for (String id : done) pendingColors.remove(id);
        }

        if (!pendingMaxSpeeds.isEmpty()) {
            java.util.List<String> done = new java.util.ArrayList<>();
            for (Map.Entry<String, Double> e : pendingMaxSpeeds.entrySet()) {
                try {
                    conn.do_job_set(Vehicle.setMaxSpeed(e.getKey(), e.getValue()));
                    done.add(e.getKey());
                } catch (Exception ignored) {
                }
            }
            for (String id : done) pendingMaxSpeeds.remove(id);
        }
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
    public void configureVehicle(String vehId, double maxSpeed, double speedRatio,
            int r, int g, int b, int a) {
    	if (this.getConnection() == null) return; // Connection does not exist
        if (vehId == null || vehId.isEmpty()) return;   // invalid vehicle ID
        if (!isValidSpeed(maxSpeed)) return;      // invalid max speed

        // Clamp speed ratio to [0.0, 1.0] and compute actual speed
        double clampedRatio = clamp(speedRatio, 0.0, 1.0);
        double actualSpeed = maxSpeed * clampedRatio;

        // Prepare RGBA color string for TraCI
        String color = r + "," + g + "," + b + "," + a;

        try {
            this.getConnection().do_job_set(Vehicle.setMaxSpeed(vehId, maxSpeed));   // set max speed
            this.getConnection().do_job_set(Vehicle.setSpeed(vehId, actualSpeed));   // set current speed
            this.setColor(vehId, new int[] {r, g, b, a});
//            this.getConnection().do_job_set(Vehicle.setParameter(id, "color", color)); // set vehicle color
        } catch (Exception e) {
            System.err.println("VehicleFactory: failed to configure vehicle " + vehId);
            e.printStackTrace();
        }
    }
    
    // ================= SNAPSHOT =================
    /**
     * Creates an immutable snapshot of the vehicle's current state.
     * @return VehicleState representing current position, speed, and edge
     */
    public VehicleState updateState(String vehId) {
        double[] pos = this.getPosition(vehId);
        return new VehicleState(vehId, pos[0], pos[1], this.getSpeed(vehId), this.getEdgeId(vehId));
    }
    
    // ================= HELPERS =================
    /**
     * Converts an Object to double safely.
     * @param res object to convert
     * @param fallback value to return if conversion fails
     * @return double value or fallback
     */
    @SuppressWarnings("unused")
	private static double toDouble(Object res, double fallback) {
        if (res instanceof Number) return ((Number) res).doubleValue();
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
    @SuppressWarnings("unused")
	private static double[] toPosition(Object res) {
        if (res instanceof double[] && ((double[])res).length >= 2) return (double[])res;
        if (res instanceof Object[] && ((Object[])res).length >= 2) {
        	Object[] a = (Object[])res;
            return new double[]{ toDouble(a[0], 0.0), toDouble(a[1], 0.0) };
        }
        return new double[] {0, 0};
    }
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
    
    // not yet implemented
//    getRouteID()
//    getRouteIndex()
//    getRoute()
//    getDistance()
//    getSignals()
//    getAcceleration()
//    getAngle()
}
























