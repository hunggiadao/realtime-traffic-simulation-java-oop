import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.sumo.cmd.Route;
import de.tudresden.sumo.objects.SumoStringList;
import de.tudresden.sumo.objects.SumoColor;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VehicleWrapper {
    private static final Logger LOGGER = Logger.getLogger(VehicleWrapper.class.getName());
    private final TraCIConnector connector;

    // Some SUMO operations (e.g. setColor) may fail if the vehicle is not yet inserted.
    // Queue them and retry on subsequent UI refresh/steps.
    private final Map<String, SumoColor> pendingColors = new HashMap<>();
    private final Map<String, Double> pendingMaxSpeeds = new HashMap<>();

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

        applyPendingUpdates();

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
				javafx.scene.paint.Color color = javafx.scene.paint.Color.RED;
                try {
                    Object sp = connector.getConnection().do_job_get(de.tudresden.sumo.cmd.Vehicle.getSpeed(id));
                    speed = toDouble(sp);
                } catch (Exception ignored) {}
                try {
                    Object rd = connector.getConnection().do_job_get(de.tudresden.sumo.cmd.Vehicle.getRoadID(id));
                    edge = String.valueOf(rd);
                } catch (Exception ignored) {}
                try {
                    Object cl = connector.getConnection().do_job_get(de.tudresden.sumo.cmd.Vehicle.getColor(id));
                    if (cl instanceof SumoColor) {
                        SumoColor sc = (SumoColor) cl;
                        color = javafx.scene.paint.Color.rgb(sc.r, sc.g, sc.b, sc.a / 255.0);
                    }
                } catch (Exception ignored) {}
                rows.add(new VehicleRow(id, speed, edge, color));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch vehicle rows", e);
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

        applyPendingUpdates();

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
            LOGGER.log(Level.WARNING, "Failed to fetch vehicle positions", e);
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
                // Assume it's a "dead street" / entry edge where we want to spawn vehicles.
                // Build a route that starts on this edge and heads into the network using a precomputed path.
                finalRouteId = "route_" + vehicleId;

                SumoStringList edgeList = new SumoStringList();
                edgeList.add(routeOrEdgeId);

                // For now, keep the injected route as just the chosen edge.
                // This guarantees a valid route and avoids "no valid route" errors when spawning many vehicles.
                // Vehicles will travel along this edge and disappear at its downstream end.

                try {
                    conn.do_job_set(Route.add(finalRouteId, edgeList));
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
}
