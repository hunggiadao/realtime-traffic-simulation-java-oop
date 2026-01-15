import java.util.ArrayList;
import java.util.List;
import de.tudresden.sumo.cmd.Edge;
import de.tudresden.sumo.cmd.Lane;
import de.tudresden.sumo.cmd.Route;
import de.tudresden.sumo.objects.SumoStringList;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Wrapper class for using TraCI to communicate with SUMO infrastructure components.
 * Provides methods to manage infrastructure elements: BusStops, Edges, Lanes, and Routes.
 * Implements defensive programming to ensure connection stability.
 */
public class InfrastructureWrapper {
    private static final Logger LOGGER = Logger.getLogger(InfrastructureWrapper.class.getName());

    private TraCIConnector connector;

   /**
     * Set up the connector for SUMO infrastructure communication.
     * @param connector The link to SUMO.
     */
    public InfrastructureWrapper(TraCIConnector connector) {
        this.connector = connector;
    }

    /**
     * Logical check to whether the connection is ready.
     * @return true if safe to send commands, false otherwise.
     */
    private boolean isReady(){
        if (connector == null) return false;
        else if (connector.isConnected() == false) return false;
        else if (connector.getConnection() == null) return false;
        else return true;
    }

    // ============================================================
    // 1. BUS STOP LOGIC
    // ============================================================

    /**
     * Retrieves the list of all Bus Stop IDs in the simulation.
     * Uses the custom BusStop class to send TraCI commands.
     * @return A list of Bus Stop IDs. Returns empty list if connection is not ready or error occurs.
     */
    public List<String> getBusStopIds(){
        List<String> IDList = new ArrayList<>();
        // check connection before sending commands
        if (isReady() == false) return IDList;
        try{
            Object res = connector.getConnection().do_job_get(BusStop.getIDList());
            if(res instanceof List){
                for (Object o : (List<?>) res) IDList.add(String.valueOf(o));
            }
            else if(res instanceof String[]){
                for(String s: (String[])res) IDList.add(s);
            }
        }
        catch(Exception ex){
            LOGGER.log(Level.FINE, "[Infra] Bus stop IDs could not be retrieved", ex);
        }
        return IDList;
    }

    /**
     * Gets the Lane ID where the bus stop is located.
     * @param busStopId The ID of the bus stop.
     * @return The Lane ID string. Returns null if error or not ready.
     */
    public String getBusStopLane(String busStopId) {
        if (isReady() == false) return null;
        if( busStopId.isEmpty() || busStopId == null) return null;

        try {
            Object pos = connector.getConnection().do_job_get(BusStop.getLaneID(busStopId));
            return String.valueOf(pos);
        } catch (Exception e) {
            return "";
        }
    }


    public int getBusStopPersonCount(String busStopId) {
        if (isReady() == false) return 0;
        if( busStopId.isEmpty() || busStopId == null) return 0;

        try {
            Object number = connector.getConnection().do_job_get(BusStop.getPersonCount(busStopId));
            return (int) number;
        }
        catch(Exception ex) {
            return 0;
        }
    }

    /**
     * Gets the start position of the bus stop on the lane (distance from lane start in meters).
     * @param busStopId The ID of the bus stop.
     * @return The start position in meters, or -1.0 if error.
     */
    public double getBusStopStartPos(String busStopId) {
        if (isReady() == false) return -1.0;
        if (busStopId == null || busStopId.isEmpty()) return -1.0;

        try {
            Object pos = connector.getConnection().do_job_get(BusStop.getStartPos(busStopId));
            if (pos instanceof Number) {
                return ((Number) pos).doubleValue();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "[Infra] Bus stop start pos error for " + busStopId, ex);
        }
        return -1.0;
    }

    /**
     * Gets the end position of the bus stop on the lane (distance from lane start in meters).
     * @param busStopId The ID of the bus stop.
     * @return The end position in meters, or -1.0 if error.
     */
    public double getBusStopEndPos(String busStopId) {
        if (isReady() == false) return -1.0;
        if (busStopId == null || busStopId.isEmpty()) return -1.0;

        try {
            Object pos = connector.getConnection().do_job_get(BusStop.getEndPos(busStopId));
            if (pos instanceof Number) {
                return ((Number) pos).doubleValue();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "[Infra] Bus stop end pos error for " + busStopId, ex);
        }
        return -1.0;
    }

    /**
     * Gets the name of the bus stop.
     * @param busStopId The ID of the bus stop.
     * @return The name string, or empty string if error.
     */
    public String getBusStopName(String busStopId) {
        if (isReady() == false) return "";
        if (busStopId == null || busStopId.isEmpty()) return "";

        try {
            Object name = connector.getConnection().do_job_get(BusStop.getName(busStopId));
            return (name != null) ? name.toString() : "";
        } catch (Exception ex) {
            return "";
        }
    }

    // ============================================================
    // 2. EDGE LOGIC
    // ============================================================
    public List<String> getEdgeList() {
        List<String> edgeList = new ArrayList<>();
        if (isReady() == false) return edgeList;

        try {
            SumoStringList list = (SumoStringList) connector.getConnection().do_job_get(Edge.getIDList());
            edgeList.addAll(list);
        }
        catch(Exception ex) {
            LOGGER.log(Level.FINE, "[Infra] Error retrieving edge IDs", ex);
        }

        return edgeList;
    }

    public int getLaneCount(String edgeID) {
        if (isReady() == false) return 0;
        if(edgeID == null || edgeID.isEmpty()) return 0;

        try {
            int laneCount = (int) connector.getConnection().do_job_get(Edge.getLaneNumber(edgeID));
            return laneCount;
        } catch (Exception ex) {
            return 0;
        }
    }

    // ============================================================
    // 3. LANE LOGIC
    // ============================================================
    public List<String> getLaneList() {
        List<String> lanes = new ArrayList<>();
        if (isReady() == false) return lanes;

        try {
            SumoStringList list = (SumoStringList) connector.getConnection().do_job_get(Lane.getIDList());
            lanes.addAll(list);
        }
        catch(Exception ex) {
            LOGGER.log(Level.FINE, "[Infra] Error fetching lanesID", ex);
        }

        return lanes;
    }

    /**
     * Gets the maximum allowed speed on a specific lane.
     * @param laneID The ID of the lane.
     * @return The maximum speed in m/s.
     */
    public double getLaneMaxSpeed(String laneID) {
        if (isReady() == false) return 0.0;
        if(laneID == null || laneID.isEmpty()) return 0.0;

        try {
            return (double) connector.getConnection().do_job_get(Lane.getMaxSpeed(laneID));
        }
        catch(Exception ex) {
            return 0.0;
        }
    }

    public double getLaneLength(String laneID) {
        if (isReady() == false) return 0.0;
        if(laneID == null || laneID.isEmpty()) return 0.0;

        try {
            return (double) connector.getConnection().do_job_get(Lane.getLength(laneID));
        }
        catch(Exception ex) {
            LOGGER.log(Level.FINE, "Lane length error for laneID=" + laneID, ex);
            return 0.0;
        }
    }

    /**
     * Checks if a route with the given ID exists.
     * @param routeID The ID of the route to check.
     * @return true if the route exists, false otherwise.
     */

    // ============================================================
    // 4. ROUTE LOGIC
    // ============================================================
    public boolean createRoute(String routeID, List<String> edges) {
        if (isReady() == false) return false;
        if( routeID == null || routeID.isEmpty()) return false;
        if( edges == null || edges.isEmpty()) return false;

        try {
            SumoStringList list = new SumoStringList();
            for(String e: edges ){
                list.add(e);
            }

            connector.getConnection().do_job_set(Route.add(routeID, list));
            LOGGER.fine("[Infra] Created route: " + routeID);
            return true;

        }
        catch(Exception e) {
            LOGGER.log(Level.FINE, "[Infra] Create route error", e);
            return false;
        }
    }

    public boolean routeExists(String routeID) {
        if (isReady() == false) return false;
        if( routeID == null || routeID.isEmpty()) return false;

        try {
            SumoStringList list = (SumoStringList) connector.getConnection().do_job_get(Route.getIDList());
            return list.contains(routeID);
        }
        catch(Exception ex) {
            LOGGER.log(Level.FINE, "[Infra] Unable to check the route existence", ex);
            return false;
        }
    }

    public void createRouteIfMissing(String routeID, String edgeID) {
        if (!isReady()) return;
        if( routeID == null || routeID.isEmpty()) return;
        if( edgeID == null || edgeID.isEmpty()) return;

        if (routeExists(routeID) == false) {
            List<String> edges = new ArrayList<>();
            edges.add(edgeID);
            createRoute(routeID, edges);
        }
    }
}
