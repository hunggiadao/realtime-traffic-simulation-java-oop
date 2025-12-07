import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.cmd.Route;
import de.tudresden.sumo.objects.SumoStringList;
import de.tudresden.sumo.objects.SumoColor;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class VehicleWrapper {
    private final TraCIConnector connector;

    public VehicleWrapper(TraCIConnector connector) {
        this.connector = connector;
    }

    public int getVehicleCount() {
        return connector.getVehicleCount();
    }

    /**
     * Fetch current vehicle rows (id, speed, edge) from SUMO.
     */
    public List<VehicleRow> getVehicleRows() {
        List<VehicleRow> rows = new ArrayList<>();
        if (!connector.isConnected() || connector.getConnection() == null) {
            return rows;
        }
        try {
            Object idsObj = connector.getConnection().do_job_get(de.tudresden.sumo.cmd.Vehicle.getIDList());
            List<String> ids = new ArrayList<>();
            if (idsObj instanceof String[]) {
                for (String s : (String[]) idsObj) ids.add(s);
            } else if (idsObj instanceof List<?>) {
                for (Object o : (List<?>) idsObj) ids.add(String.valueOf(o));
            }

            for (String id : ids) {
                double speed = 0.0;
                String edge = "";
                try {
                    Object sp = connector.getConnection().do_job_get(de.tudresden.sumo.cmd.Vehicle.getSpeed(id));
                    speed = toDouble(sp);
                } catch (Exception ignored) {}
                try {
                    Object rd = connector.getConnection().do_job_get(de.tudresden.sumo.cmd.Vehicle.getRoadID(id));
                    edge = String.valueOf(rd);
                } catch (Exception ignored) {}
                rows.add(new VehicleRow(id, speed, edge));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rows;
    }

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
    public Map<String, javafx.geometry.Point2D> getVehiclePositions() {
        Map<String, javafx.geometry.Point2D> out = new HashMap<>();
        if (!connector.isConnected() || connector.getConnection() == null) {
            return out;
        }
        try {
            Object idsObj = connector.getConnection().do_job_get(de.tudresden.sumo.cmd.Vehicle.getIDList());
            List<String> ids = new ArrayList<>();
            if (idsObj instanceof String[]) {
                for (String s : (String[]) idsObj) ids.add(s);
            } else if (idsObj instanceof List<?>) {
                for (Object o : (List<?>) idsObj) {
                    ids.add(String.valueOf(o));
                }
            }

            for (String id : ids) {
                Object posObj = connector.getConnection().do_job_get(de.tudresden.sumo.cmd.Vehicle.getPosition(id));
                javafx.geometry.Point2D p = extractPoint(posObj);
                if (p != null) out.put(id, p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    private javafx.geometry.Point2D extractPoint(Object posObj) {
        if (posObj == null) return null;
        if (posObj instanceof double[]) {
            double[] arr = (double[]) posObj;
            if (arr.length >= 2) return new javafx.geometry.Point2D(arr[0], arr[1]);
        }
        if (posObj instanceof List<?>) {
            List<?> list = (List<?>) posObj;
            if (list.size() >= 2) {
                try {
                    double x = Double.parseDouble(list.get(0).toString());
                    double y = Double.parseDouble(list.get(1).toString());
                    return new javafx.geometry.Point2D(x, y);
                } catch (NumberFormatException ignored) {}
            }
        }
        try {
            Class<?> cls = posObj.getClass();
            java.lang.reflect.Field fx = cls.getField("x");
            java.lang.reflect.Field fy = cls.getField("y");
            double x = fx.getDouble(posObj);
            double y = fy.getDouble(posObj);
            return new javafx.geometry.Point2D(x, y);
        } catch (Exception ignored) {
        }
        return null;
    }

    public double getSpeed(String id) {
        if (!connector.isConnected()) return 0.0;
        try {
            Object sp = connector.getConnection().do_job_get(de.tudresden.sumo.cmd.Vehicle.getSpeed(id));
            return toDouble(sp);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public void addVehicle(String vehicleId, String routeOrEdgeId, double speed, javafx.scene.paint.Color color) {
        if (!connector.isConnected()) return;
        try {
            it.polito.appeal.traci.SumoTraciConnection conn = connector.getConnection();

            // Check if routeOrEdgeId is a known route
            boolean isRoute = false;
            try {
                @SuppressWarnings("unchecked")
                List<String> routes = (List<String>) conn.do_job_get(Route.getIDList());
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
                    conn.do_job_set(Route.add(finalRouteId, edgeList));
                } catch (Exception e) {
                    System.err.println("Failed to create route " + finalRouteId + " for edge " + routeOrEdgeId + ": " + e.getMessage());
                    // If this fails, maybe the edge doesn't exist. We'll try to add vehicle anyway, it might fail.
                }
            }

            // Add vehicle: id, type="DEFAULT_VEHTYPE", route, depart=-2 (now), pos=0, speed=0, lane=0
            conn.do_job_set(Vehicle.add(vehicleId, "DEFAULT_VEHTYPE", finalRouteId, -2, 0.0, 0.0, (byte) 0));

            // Set color
            int r = (int) (color.getRed() * 255);
            int g = (int) (color.getGreen() * 255);
            int b = (int) (color.getBlue() * 255);
            SumoColor sumoColor = new SumoColor(r, g, b, 255);
            conn.do_job_set(Vehicle.setColor(vehicleId, sumoColor));

            // Set max speed
            if (speed > 0) {
                 conn.do_job_set(Vehicle.setMaxSpeed(vehicleId, speed));
            }
        } catch (Exception e) {
            System.err.println("Failed to add vehicle " + vehicleId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
