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

import javafx.geometry.Point2D;

/**
 * Class for retrieving and setting vehicle data in the simulation
 * Provides thread-safe access to vehicle data via TraCI.
 */
public abstract class VehicleWrapper extends TraCIConnector {
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
        try {
            Object idsObj = this.getConnection().do_job_get(Vehicle.getIDList());
            List<String> ids = new ArrayList<>();
            if (idsObj instanceof String[]) {
                for (String s : (String[]) idsObj) ids.add(s);
            } else if (idsObj instanceof List<?>) {
                for (Object o : (List<?>) idsObj) ids.add(String.valueOf(o));
            }

            for (String id : ids) {
                double speed = (double) this.getConnection().do_job_get(Vehicle.getSpeed(id));
                String edge = (String) this.getConnection().do_job_get(Vehicle.getRoadID(id));
                rows.add(new VehicleRow(id, speed, edge));
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
                // Assume it's an edge ID. Create a temporary route for this vehicle.
                finalRouteId = "route_" + vehicleId;
                SumoStringList edgeList = new SumoStringList();
                edgeList.add(routeOrEdgeId);
                try {
                    this.getConnection().do_job_set(Route.add(finalRouteId, edgeList));
                } catch (Exception e) {
                    System.err.println("Failed to create route " + finalRouteId + " for edge " + routeOrEdgeId + ": " + e.getMessage());
                    // If this fails, maybe the edge doesn't exist. We'll try to add vehicle anyway, it might fail.
                }
            }

            // Add vehicle: id, type="DEFAULT_VEHTYPE", route, depart=-2 (now), pos=0, speed=0, lane=0
            this.getConnection().do_job_set(Vehicle.add(vehicleId, "DEFAULT_VEHTYPE", finalRouteId, -2, 0.0, 0.0, (byte) 0));

            // Set color
            int r = (int) (color.getRed() * 255);
            int g = (int) (color.getGreen() * 255);
            int b = (int) (color.getBlue() * 255);
            SumoColor sumoColor = new SumoColor(r, g, b, 255);
            this.getConnection().do_job_set(Vehicle.setColor(vehicleId, sumoColor));

            // Set max speed
            if (speed > 0) {
                 this.getConnection().do_job_set(Vehicle.setMaxSpeed(vehicleId, speed));
            }
        } catch (Exception e) {
            System.err.println("Failed to add vehicle " + vehicleId + ": " + e.getMessage());
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
    
    // not yet implemented
//    getRouteID()
//    getRouteIndex()
//    getRoute()
//    getDistance()
//    getSignals()
//    getAcceleration()
//    getAngle()
}
























