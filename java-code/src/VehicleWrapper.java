import de.tudresden.sumo.cmd.Edge;
import de.tudresden.sumo.cmd.Route;
import de.tudresden.sumo.cmd.Simulation;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.objects.SumoColor;
import de.tudresden.sumo.objects.SumoStage;
import de.tudresden.sumo.objects.SumoStringList;
import it.polito.appeal.traci.SumoTraciConnection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

/**
 * VehicleWrapper
 * -------------
 * Object-oriented wrapper around TraCI vehicle operations.
 *
 * This class wraps a single {@link TraCIConnector} instance (composition) and
 * exposes higher-level vehicle operations for UI and simulation code.
 */
public final class VehicleWrapper {
    private static final Logger LOGGER = Logger.getLogger(VehicleWrapper.class.getName());

    private final TraCIConnector traci;

    // Some SUMO operations (e.g. setColor) may fail if the vehicle is not yet inserted.
    // Queue them and retry on subsequent UI refresh/steps.
    private final Map<String, SumoColor> pendingColors = new HashMap<>();
    private final Map<String, Double> pendingMaxSpeeds = new HashMap<>();
	private final Map<String, SumoStringList> pendingRoutes = new HashMap<>();

	// Colors requested by the user for locally-injected vehicles.
	// We prefer these for rendering so the UI reflects what the user chose even
	// if SUMO temporarily reports an unset/default color for a freshly-added vehicle.
	private final Map<String, Color> preferredVehicleColors = new HashMap<>();

    private static final int RANDOM_ROUTE_TRIES = 10;
    private static final int ROUTING_MODE_DEFAULT = 0;

    public VehicleWrapper(TraCIConnector traci) {
        this.traci = Objects.requireNonNull(traci, "traci");
    }

    /**
     * Returns the number of vehicles currently running within the scenario.
     */
    public int getVehicleCount() {
        if (traci.getConnection() == null || !traci.isConnected()) return 0;
        try {
            return (int) traci.getConnection().do_job_get(Vehicle.getIDCount());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch vehicle count", e);
            return 0;
        }
    }

    /**
     * Returns a list of ids of all vehicles currently running within the scenario
     * @return
     */
    @SuppressWarnings("unchecked")
	public List<String> getVehicleIds() {
        if (traci.getConnection() == null || !traci.isConnected()) {
    		return new ArrayList<String>();
    	}
        try {
            Object response = traci.getConnection().do_job_get(Vehicle.getIDList());
        	if (response instanceof String[]) {
                return Arrays.asList((String[]) response);
            } else if (response instanceof List) {
                return (List<String>) response;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch vehicle IDs", e);
        }
        return new ArrayList<String>(); // error for whatever reason
    }

    /**
     * Fetch current vehicle rows (id, speed, edge) from SUMO.
     * @return rows
     */
    public List<VehicleRow> getVehicleRows() {
        List<VehicleRow> rows = new ArrayList<>();
		if (traci.getConnection() == null || !traci.isConnected()) {
            return rows;
        }

        applyPendingUpdates();

        try {
            Object idsObj = traci.getConnection().do_job_get(Vehicle.getIDList());
            List<String> ids = new ArrayList<>();
            if (idsObj instanceof String[]) {
                for (String s : (String[]) idsObj) ids.add(s);
            } else if (idsObj instanceof List<?>) {
                for (Object o : (List<?>) idsObj) ids.add(String.valueOf(o));
            }

            for (String id : ids) {
                try {
                    Color color = preferredVehicleColors.getOrDefault(id, Color.RED);

                    Object speedObj = traci.getConnection().do_job_get(Vehicle.getSpeed(id));
                    double speed = (speedObj instanceof Number) ? ((Number)speedObj).doubleValue() : 0.0;

                    Object edgeObj = traci.getConnection().do_job_get(Vehicle.getRoadID(id));
                    String edge = (edgeObj != null) ? edgeObj.toString() : "";

                    Object cl = traci.getConnection().do_job_get(Vehicle.getColor(id));
                    int[] rgba = extractRgba(cl);
					if (rgba != null) {
						int r = clampInt(rgba[0], 0, 255);
						int g = clampInt(rgba[1], 0, 255);
						int b = clampInt(rgba[2], 0, 255);
						double opacity = clamp(rgba[3] / 255.0, 0.0, 1.0);
                        // Only override the preferred injected color if SUMO returns a valid color.
                        color = Color.rgb(r, g, b, opacity);
					}

                    rows.add(new VehicleRow(id, speed, edge, color));
                } catch (Exception perVehicle) {
                    // Skip only the problematic vehicle; keep the rest visible.
                    LOGGER.log(Level.FINE, "Failed to fetch row for vehicle " + id, perVehicle);
                }
            }
        } catch (Exception e) {
			if (e instanceof IllegalStateException) {
				traci.handleConnectionError(e);
				return rows;
			}
            LOGGER.log(Level.WARNING, "Failed to fetch vehicle rows", e);
        }
        return rows;
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    private static int[] extractRgba(Object colorObj) {
        if (colorObj == null) return null;
        if (colorObj instanceof SumoColor) {
        	SumoColor sc = (SumoColor) colorObj;
            // SUMO/TraaS can return -1 for unset/unknown colors.
            if (sc.r < 0 || sc.g < 0 || sc.b < 0 || sc.a < 0) return null;
            return new int[] {sc.r, sc.g, sc.b, sc.a };
        }
        if (colorObj instanceof int[]) {
        	int[] a = (int[]) colorObj;
            if (a.length < 3) return null;
            int alpha = (a.length >= 4) ? a[3] : 255;
            // Treat negative values as invalid/unset.
            if (a[0] < 0 || a[1] < 0 || a[2] < 0 || alpha < 0) return null;
            return new int[] { a[0], a[1], a[2], alpha };
        }
        if (colorObj instanceof byte[]) {
        	byte[] a = (byte[]) colorObj;
            if (a.length < 3) return null;
            int r = a[0] & 0xFF;
            int g = a[1] & 0xFF;
            int b = a[2] & 0xFF;
            int alpha = (a.length >= 4) ? (a[3] & 0xFF) : 255;
            return new int[] { r, g, b, alpha };
        }
        if (colorObj instanceof Object[]) {
        	Object[] a = (Object[]) colorObj;
            if (a.length < 3) return null;
            Integer r = safeParseInt(a[0]);
            Integer g = safeParseInt(a[1]);
            Integer b = safeParseInt(a[2]);
            Integer alpha = (a.length >= 4) ? safeParseInt(a[3]) : 255;
            if (r == null || g == null || b == null) return null;
            if (r < 0 || g < 0 || b < 0 || (alpha != null && alpha < 0)) return null;
            return new int[] { r, g, b, (alpha == null ? 255 : alpha) };
        }
        if (colorObj instanceof List<?>) {
        	List<?> list = (List<?>) colorObj;
            if (list.size() < 3) return null;
            Integer r = safeParseInt(list.get(0));
            Integer g = safeParseInt(list.get(1));
            Integer b = safeParseInt(list.get(2));
            Integer alpha = (list.size() >= 4) ? safeParseInt(list.get(3)) : 255;
            if (r == null || g == null || b == null) return null;
            if (r < 0 || g < 0 || b < 0 || (alpha != null && alpha < 0)) return null;
            return new int[] { r, g, b, (alpha == null ? 255 : alpha) };
        }
        return null;
    }

    private static Integer safeParseInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number)v).intValue();
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception ignored) {
            return null;
        }
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
		if (traci.getConnection() == null || !traci.isConnected()) {
            return out;
        }

        applyPendingUpdates();

        try {
            Object idsObj = traci.getConnection().do_job_get(Vehicle.getIDList());
            List<String> ids = new ArrayList<>();
            if (idsObj instanceof String[]) {
                for (String s : (String[]) idsObj) ids.add(s);
            } else if (idsObj instanceof List<?>) {
                for (Object o : (List<?>) idsObj) {
                    ids.add(String.valueOf(o));
                }
            }

            for (String id : ids) {
				Object posObj = traci.getConnection().do_job_get(Vehicle.getPosition(id));
                Point2D p = extractPoint(posObj);
                if (p != null) out.put(id, p);
            }
        } catch (Exception e) {
			if (e instanceof IllegalStateException) {
				traci.handleConnectionError(e);
				return out;
			}
            LOGGER.log(Level.WARNING, "Failed to fetch vehicle positions", e);
        }
        return out;
    }

    /**
     * Fetch current lane IDs for all vehicles.
     */
    public Map<String, String> getVehicleLaneIds() {
        Map<String, String> out = new HashMap<>();
        if (traci.getConnection() == null || !traci.isConnected()) {
            return out;
        }

        applyPendingUpdates();

        try {
            Object idsObj = traci.getConnection().do_job_get(Vehicle.getIDList());
            List<String> ids = new ArrayList<>();
            if (idsObj instanceof String[]) {
                for (String s : (String[]) idsObj) ids.add(s);
            } else if (idsObj instanceof List<?>) {
                for (Object o : (List<?>) idsObj) ids.add(String.valueOf(o));
            }

            for (String id : ids) {
                try {
                    Object laneObj = traci.getConnection().do_job_get(Vehicle.getLaneID(id));
                    String laneId = (laneObj != null) ? laneObj.toString() : "";
                    if (!laneId.isEmpty()) out.put(id, laneId);
                } catch (Exception ignored) {
                    // ignore per-vehicle lane failures
                }
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                traci.handleConnectionError(e);
                return out;
            }
            LOGGER.log(Level.FINE, "Failed to fetch vehicle lane IDs", e);
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
        if (posObj instanceof Object[]) {
            Object[] arr = (Object[]) posObj;
            if (arr.length >= 2) {
                Double x = safeParseDouble(arr[0]);
                Double y = safeParseDouble(arr[1]);
                if (x != null && y != null) return new Point2D(x, y);
            }
        }
        if (posObj instanceof List<?>) {
            List<?> list = (List<?>) posObj;
            if (list.size() >= 2) {
				Double x = safeParseDouble(list.get(0));
				Double y = safeParseDouble(list.get(1));
				if (x != null && y != null) {
					return new Point2D(x, y);
				}
//                try {
//                } catch (NumberFormatException ignored) {}
            }
        }

        // Common TraaS type is de.tudresden.sumo.objects.SumoPosition2D; handle via reflection
        // so we work regardless of whether fields are public or accessed via getters.
        try {
            Class<?> cls = posObj.getClass();
            Double x = null;
            Double y = null;

            // 1) public fields x/y
            try {
                Field fx = cls.getField("x");
                Field fy = cls.getField("y");
                x = fx.getType() == double.class ? fx.getDouble(posObj) : safeParseDouble(fx.get(posObj));
                y = fy.getType() == double.class ? fy.getDouble(posObj) : safeParseDouble(fy.get(posObj));
            } catch (NoSuchFieldException ignored) {
                // ignore
            }

            // 2) non-public fields x/y
            if (x == null || y == null) {
                try {
                    Field fx = cls.getDeclaredField("x");
                    Field fy = cls.getDeclaredField("y");
                    fx.setAccessible(true);
                    fy.setAccessible(true);
                    x = fx.getType() == double.class ? fx.getDouble(posObj) : safeParseDouble(fx.get(posObj));
                    y = fy.getType() == double.class ? fy.getDouble(posObj) : safeParseDouble(fy.get(posObj));
                } catch (NoSuchFieldException ignored) {
                    // ignore
                }
            }

            // 3) getters getX/getY
            if (x == null || y == null) {
                try {
                    Method mx = cls.getMethod("getX");
                    Method my = cls.getMethod("getY");
                    x = safeParseDouble(mx.invoke(posObj));
                    y = safeParseDouble(my.invoke(posObj));
                } catch (NoSuchMethodException ignored) {
                    // ignore
                }
            }

            // 4) methods x()/y()
            if (x == null || y == null) {
                try {
                    Method mx = cls.getMethod("x");
                    Method my = cls.getMethod("y");
                    x = safeParseDouble(mx.invoke(posObj));
                    y = safeParseDouble(my.invoke(posObj));
                } catch (NoSuchMethodException ignored) {
                    // ignore
                }
            }

            if (x != null && y != null) {
                return new javafx.geometry.Point2D(x, y);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Double safeParseDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number)o).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the speed of the named vehicle within the last step [m/s]
     * @param id
     * @return
     */
    public double getSpeed(String vehId) {
		if (traci.getConnection() == null || !traci.isConnected()) return 0;
        try {
			return (double) traci.getConnection().do_job_get(Vehicle.getSpeed(vehId));
        } catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to fetch speed for " + vehId, e);
        }
        return 0;
    }

    /**
     * Sets the speed in m/s for the named vehicle within the last step. Calling with speed=-1 hands the vehicle control back to SUMO.
     */
    public void setSpeed(String vehId, double newSpeed) {
        if (traci.getConnection() == null || !traci.isConnected()) {
            LOGGER.fine("setSpeed ignored: not connected");
            return;
        }
        try {
            traci.getConnection().do_job_set(Vehicle.setSpeed(vehId, newSpeed));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set speed for " + vehId, e);
        }
    }

    /**
     * Returns the position(two doubles) of the named vehicle (center of the front bumper) within the last step [m,m];
     * @param vehId
     * @return
     */
    public double[] getPosition(String vehId) {
		if (traci.getConnection() == null || !traci.isConnected()) return new double[] {0, 0};
        try {
            Object posObj = traci.getConnection().do_job_get(Vehicle.getPosition(vehId));
            Point2D p = extractPoint(posObj);
            if (p != null) {
                return new double[] { p.getX(), p.getY() };
            }
        } catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to fetch position for " + vehId, e);
        }
        return new double[] {0, 0};
    }

    /**
     * Returns the id of the edge the named vehicle was at within the last step; error value: ""
     * Returns the current road edge ID where the vehicle is located.
     * @return edge ID as String, or null if unavailable
     */
    public String getEdgeId(String vehId) {
		if (traci.getConnection() == null || !traci.isConnected()) return "";
        try {
			return (String) traci.getConnection().do_job_get(Vehicle.getRoadID(vehId));
        } catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to fetch edge for " + vehId, e);
        }
        return ""; // error
    }

    /**
     * Returns the id of the lane the named vehicle was at within the last step; error value: ""
     * @param vehId
     * @return
     */
    public String getLaneId(String vehId) {
		if (traci.getConnection() == null || !traci.isConnected()) return "";
        try {
			return (String) traci.getConnection().do_job_get(Vehicle.getLaneID(vehId));
        } catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to fetch lane for " + vehId, e);
        }
        return ""; // error
    }

    /**
     * Returns the index of the lane the named vehicle was at within the last step; error value: -2^30
     * @param vehId
     * @return
     */
    public int getLaneIndex(String vehId) {
		if (traci.getConnection() == null || !traci.isConnected()) return -2^30;
        try {
			return (int) traci.getConnection().do_job_get(Vehicle.getLaneIndex(vehId));
        } catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to fetch lane index for " + vehId, e);
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
		if (traci.getConnection() == null || !traci.isConnected()) return new int[] {0, 0, 0, 0};
        try {
			Object colorObj = traci.getConnection().do_job_get(Vehicle.getColor(typeId));
			int[] rgba = extractRgba(colorObj);
			if (rgba != null) return rgba;
        } catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to fetch color for " + typeId, e);
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
        if (traci.getConnection() == null || !traci.isConnected()) {
            LOGGER.fine("setColor ignored: not connected");
            return;
        }
    	try {
    		SumoColor color = new SumoColor(newColor[0], newColor[1], newColor[2], newColor[3]);
            traci.getConnection().do_job_set(Vehicle.setColor(typeId, color));
//	        try {
//	            synchronized (traci) {
//	                SumoTraciConnection conn = traci.getConnection();
//	                if (conn != null) {
//	                    conn.do_job_set(Vehicle.setParameter(id, "color", rgba));
//	                }
//	            }
//	        } catch (Exception ignored) {}
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set color for " + typeId, e);
        }
    }
    public void addVehicle(String vehicleId, String routeOrEdgeId, double speed, Color color) {
		if (traci.getConnection() == null || !traci.isConnected()) return;
        try {
                        if (color == null) color = Color.RED;
			// Remember requested color for rendering.
			preferredVehicleColors.put(vehicleId, color);

            // Check if routeOrEdgeId is a known route
            boolean isRoute = false;
            try {
                @SuppressWarnings("unchecked")
				List<String> routes = (List<String>) traci.getConnection().do_job_get(Route.getIDList());
                if (routes.contains(routeOrEdgeId)) {
                    isRoute = true;
                }
            } catch (Exception e) {
                // ignore, assume not a route or error fetching
            }

            String finalRouteId = routeOrEdgeId;
			String startEdgeId = null;

            if (!isRoute) {
                // Assume it's a "dead street" / entry edge where we want to spawn vehicles.
                // Build a route that starts on this edge and heads into the network using a precomputed path.
                finalRouteId = "route_" + vehicleId;
				startEdgeId = routeOrEdgeId;

                SumoStringList edgeList = new SumoStringList();
                edgeList.add(routeOrEdgeId);

				// We create a minimal route for spawning (start edge). After insertion we will
				// try to set a destination edge (changeTarget) so the vehicle traverses multiple edges.

                try {
                    traci.getConnection().do_job_set(Route.add(finalRouteId, edgeList));
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
                traci.getConnection().do_job_set(Vehicle.addFull(
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
				if (e instanceof IllegalStateException) {
					traci.handleConnectionError(e);
					return;
				}
                // Try with empty type if DEFAULT_VEHTYPE fails
                try {
                    traci.getConnection().do_job_set(Vehicle.addFull(
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
					if (ex instanceof IllegalStateException) {
						traci.handleConnectionError(ex);
						return;
					}
                }
            }

            // If caller provided only a start edge (not a pre-existing route), compute a longer route
            // by picking a reachable destination edge and setting the computed route.
            if (startEdgeId != null && !startEdgeId.isBlank()) {
                SumoStringList routeEdges = pickRandomReachableRoute(startEdgeId, "DEFAULT_VEHTYPE");
                if (routeEdges != null && routeEdges.size() >= 2) {
                    try {
                        traci.getConnection().do_job_set(Vehicle.setRoute(vehicleId, routeEdges));
                    } catch (Exception e) {
                        if (e instanceof IllegalStateException) {
                            traci.handleConnectionError(e);
                            return;
                        }
                        pendingRoutes.put(vehicleId, routeEdges);
                    }
                }
            }

            // Add vehicle: id, type="DEFAULT_VEHTYPE", route, depart=-2 (now), pos=0, speed=0, lane=0
            // this.getConnection().do_job_set(Vehicle.add(vehicleId, "DEFAULT_VEHTYPE", finalRouteId, -2, 0.0, 0.0, (byte) 0));

            // Set color
            int r = (int) Math.round(color.getRed() * 255.0);
            int g = (int) Math.round(color.getGreen() * 255.0);
            int b = (int) Math.round(color.getBlue() * 255.0);
            SumoColor sumoColor = new SumoColor(r, g, b, 255);

            try {
				traci.getConnection().do_job_set(Vehicle.setColor(vehicleId, sumoColor));
            } catch (Exception e) {
                if (e instanceof IllegalStateException) {
                    traci.handleConnectionError(e);
                    return;
                }
                pendingColors.put(vehicleId, sumoColor);
            }

            // Set max speed
            if (speed > 0) {
                try {
					traci.getConnection().do_job_set(Vehicle.setMaxSpeed(vehicleId, speed));
                } catch (Exception e) {
					if (e instanceof IllegalStateException) {
						traci.handleConnectionError(e);
						return;
					}
                    pendingMaxSpeeds.put(vehicleId, speed);
                }
            }
        } catch (Exception e) {
			if (e instanceof IllegalStateException) {
				traci.handleConnectionError(e);
				return;
			}
            LOGGER.log(Level.WARNING, "Failed to add vehicle " + vehicleId, e);
        }
    }

    public void applyPendingUpdates() {
        if (traci.getConnection() == null || !traci.isConnected()) return;
        SumoTraciConnection conn = traci.getConnection();

        if (!pendingColors.isEmpty()) {
            List<String> done = new ArrayList<>();
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
            List<String> done = new ArrayList<>();
            for (Map.Entry<String, Double> e : pendingMaxSpeeds.entrySet()) {
                try {
                    conn.do_job_set(Vehicle.setMaxSpeed(e.getKey(), e.getValue()));
                    done.add(e.getKey());
                } catch (Exception ignored) {
                }
            }
            for (String id : done) pendingMaxSpeeds.remove(id);
        }

		if (!pendingRoutes.isEmpty()) {
            List<String> done = new ArrayList<>();
            for (Map.Entry<String, SumoStringList> e : pendingRoutes.entrySet()) {
                try {
                    conn.do_job_set(Vehicle.setRoute(e.getKey(), e.getValue()));
                    done.add(e.getKey());
                } catch (Exception ex) {
                    if (ex instanceof IllegalStateException) {
                        traci.handleConnectionError(ex);
                        return;
                    }
                }
            }
            for (String id : done) pendingRoutes.remove(id);
        }
    }

    private SumoStringList pickRandomReachableRoute(String startEdgeId, String vehicleTypeId) {
        if (startEdgeId == null || startEdgeId.isBlank()) return null;
        if (traci.getConnection() == null || !traci.isConnected()) return null;
        try {
            List<String> edges = getAllEdgeIds();
            if (edges.isEmpty()) return null;
            for (int i = 0; i < RANDOM_ROUTE_TRIES; i++) {
                String candidate = edges.get((int) (Math.random() * edges.size()));
                if (candidate == null || candidate.isBlank()) continue;
                if (candidate.equals(startEdgeId)) continue;
                if (candidate.startsWith(":")) continue;
                SumoStringList route = findRouteEdges(startEdgeId, candidate, vehicleTypeId);
                if (route != null && route.size() >= 2) {
                    return route;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> getAllEdgeIds() {
        List<String> edges = new ArrayList<>();
        if (traci.getConnection() == null || !traci.isConnected()) return edges;
        try {
            Object resp = traci.getConnection().do_job_get(Edge.getIDList());
            if (resp instanceof String[]) {
                for (String s : (String[]) resp) edges.add(s);
            } else if (resp instanceof List<?>) {
                for (Object o : (List<?>) resp) edges.add(String.valueOf(o));
            }
        } catch (Exception ignored) {
        }
        return edges;
    }

    private SumoStringList findRouteEdges(String fromEdgeId, String toEdgeId, String vehicleTypeId) {
        if (traci.getConnection() == null || !traci.isConnected()) return null;
        try {
            Object routeObj = traci.getConnection().do_job_get(
                    Simulation.findRoute(fromEdgeId, toEdgeId, vehicleTypeId, 0.0, ROUTING_MODE_DEFAULT));
            if (routeObj instanceof SumoStage) {
                return ((SumoStage)routeObj).edges;
            }
            // Some SUMO versions may return a list of stages
            if (routeObj instanceof List<?>) {
            	List<?> list = (List<?>) routeObj;
            	if (!list.isEmpty() && list.get(0) instanceof SumoStage) {
            		return ((SumoStage)list.get(0)).edges;
            	}
            }
        } catch (Exception ignored) {}
        return null;
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
		if (traci.getConnection() == null || !traci.isConnected()) return;
        if (vehId == null || vehId.isEmpty()) return;   // invalid vehicle ID
        if (!isValidSpeed(maxSpeed)) return;      // invalid max speed

        // Clamp speed ratio to [0.0, 1.0] and compute actual speed
        double clampedRatio = clamp(speedRatio, 0.0, 1.0);
        double actualSpeed = maxSpeed * clampedRatio;

        try {
            traci.getConnection().do_job_set(Vehicle.setMaxSpeed(vehId, maxSpeed));   // set max speed
            traci.getConnection().do_job_set(Vehicle.setSpeed(vehId, actualSpeed));   // set current speed
            setColor(vehId, new int[] {r, g, b, a});
//          this.getConnection().do_job_set(Vehicle.setParameter(id, "color", color)); // set vehicle color
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to configure vehicle " + vehId, e);
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

	/**
     * Fetch current vehicle angles (heading in degrees) from SUMO.
     * Angle is measured from North (0 degrees) clockwise.
     * @return map of vehicle id to angle in degrees
     */
    public Map<String, Double> getVehicleAngles() {
        Map<String, Double> out = new HashMap<>();
        if (traci.getConnection() == null || !traci.isConnected()) {
            return out;
        }

        try {
            Object idsObj = traci.getConnection().do_job_get(Vehicle.getIDList());
            List<String> ids = new ArrayList<>();
            if (idsObj instanceof String[]) {
                for (String s : (String[]) idsObj) ids.add(s);
            } else if (idsObj instanceof List<?>) {
                for (Object o : (List<?>) idsObj) ids.add(String.valueOf(o));
            }

            for (String id : ids) {
                try {
                    // Get vehicle angle (heading) from SUMO
                    Object angleObj = traci.getConnection().do_job_get(Vehicle.getAngle(id));
                    if (angleObj instanceof Number) {
                        out.put(id, ((Number) angleObj).doubleValue());
                    }
                } catch (Exception ignored) {
                    // ignore per-vehicle angle failures
                }
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                traci.handleConnectionError(e);
                return out;
            }
            LOGGER.log(Level.FINE, "Failed to fetch vehicle angles", e);
        }
        return out;
    }

    /**
     * Fetch current vehicle types from SUMO.
     * Returns the vehicle type ID for each vehicle (e.g., "car", "bus", "motorcycle").
     * @return map of vehicle id to vehicle type string
     */
    public Map<String, String> getVehicleTypes() {
        Map<String, String> out = new HashMap<>();
        if (traci.getConnection() == null || !traci.isConnected()) {
            return out;
        }

        try {
            Object idsObj = traci.getConnection().do_job_get(Vehicle.getIDList());
            List<String> ids = new ArrayList<>();
            if (idsObj instanceof String[]) {
                for (String s : (String[]) idsObj) ids.add(s);
            } else if (idsObj instanceof List<?>) {
                for (Object o : (List<?>) idsObj) ids.add(String.valueOf(o));
            }

            for (String id : ids) {
                try {
                    // Get vehicle type ID from SUMO
                    Object typeObj = traci.getConnection().do_job_get(Vehicle.getTypeID(id));
                    if (typeObj != null) {
                        out.put(id, typeObj.toString());
                    }
                } catch (Exception ignored) {
                    // ignore per-vehicle type failures
                }
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                traci.handleConnectionError(e);
                return out;
            }
            LOGGER.log(Level.FINE, "Failed to fetch vehicle types", e);
        }
        return out;
    }

    // Method to Convert Data into PDF, CSV
    public List<String> getVehicleData(){
        List<String> exportRows = new ArrayList<>();
        List<String> ids = getVehicleIds();

        for (String id : ids) {
            try {
                // TODO: Color is always [0 0 0] --> always black
                // TODO: Have to fix the bug
                int[] color = getColor(id);
                String colorStr = color[0] + "-" + color[1] + "-" + color[2]; // R-G-B ID     0 0 0 For Black
                double speed = getSpeed(id);
                double[] pos = getPosition(id); // returns [x, y]
                String edge = getEdgeId(id);

                // Format: Vehicle-ID, Color, Speed, PosX, PosY, Egde-ID, Empty, Empty, Empty [no Vehicle Data]
                String row = String.format("%s;%s;%.2f;%.2f;%.2f;%s",
                        id, colorStr, speed, pos[0], pos[1], edge
                );

                exportRows.add(row);
            } catch (Exception e) {
                LOGGER.fine("Could not get data for vehicle: " + id);
            }
        }
        return exportRows;
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
