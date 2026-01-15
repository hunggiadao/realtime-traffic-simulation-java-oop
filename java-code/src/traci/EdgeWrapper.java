import de.tudresden.sumo.cmd.Edge;
import de.tudresden.sumo.cmd.Route;
import de.tudresden.sumo.cmd.Simulation;
import de.tudresden.sumo.cmd.Trafficlight;
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

/**
 *
 * @author hungg
 *
 */
public class EdgeWrapper {
    private static final Logger LOGGER = Logger.getLogger(VehicleWrapper.class.getName());

    private TraCIConnector traci;
    private VehicleWrapper vehWrapper;

    private List<String> edgeIDs; // fixed size
    private Map<String, Double> avgEdgeSpeeds; // fixed size
    private Map<String, Integer> numVehicles; // fixed size

    // edge requires access to traCI conn and vehicleWrapper
    public EdgeWrapper(TraCIConnector traci, VehicleWrapper vehWrapper) {
        this.traci = Objects.requireNonNull(traci, "");
        this.vehWrapper = Objects.requireNonNull(vehWrapper, "");

        this.edgeIDs = getEdgeIDsInternal(); // only called once
        this.avgEdgeSpeeds = new HashMap<>();
        this.numVehicles = new HashMap<>();
        for (String id : this.edgeIDs) {
            this.numVehicles.put(id, 0);
            this.avgEdgeSpeeds.put(id, (double)-1); // -1 means there are no vehicles, different from 0
        }
    }

    /**
     * for debugging
     * @return
     */
    public TraCIConnector getTraCI() {
        return this.traci;
    }
    /**
     * Get all edge ids in this scenario
     * @return
     */
    public List<String> getEdgeIDs() {
        return this.edgeIDs;
    }
    /**
     * get a Map of all avgSpeeds for all edges
     * @return
     */
    public Map<String, Double> getAllAvgEdgeSpeeds() {
        // may update in here, or in UI
        this.updateEdgeData();
        return this.avgEdgeSpeeds;
    }
    /**
     * get a Map of all numVehicles for all edges
     * @return
     */
    public Map<String, Integer> getAllNumVehicles() {
        // may update in here, or in UI
        this.updateEdgeData();
        return this.numVehicles;
    }
    /**
     * get avgSpeed of this edge only
     * @param id edgeID
     * @return
     */
    public double getAvgEdgeSpeed(String id) {
        this.updateEdgeData();
        return this.avgEdgeSpeeds.get(id);
    }
    /**
     * get numVehicles of this edge only
     * @param id edgeID
     * @return
     */
    public int getNumVehicle(String id) {
        this.updateEdgeData();
        return this.numVehicles.get(id);
    }
    /**
     * update all member fields of EdgeWrapper
     */
    private void updateEdgeData() {
        // get all vehicles on this edge and calculate their avg speed
        List<String> vehIDs = vehWrapper.getVehicleIds();
        for (String eID : this.getEdgeIDs()) {
            double sumSpeed = 0;
            this.numVehicles.replace(eID, 0);

            for (String vehID : vehIDs) {
                if (vehWrapper.getEdgeId(vehID) == eID) {
                    // vehicle is on this edge
                    this.numVehicles.replace(eID, this.numVehicles.get(eID) + 1);
                    sumSpeed += vehWrapper.getSpeed(vehID);
                }
            }
            if (this.numVehicles.get(eID) > 0) {
                // found some vehicles
                this.avgEdgeSpeeds.replace(eID, sumSpeed / this.numVehicles.get(eID));
            } else {
                // if there are no more vehicles on this edge, reset data
                this.avgEdgeSpeeds.replace(eID, (double)-1);
            }
        }
    }
    /**
     * Returns a list of ids of all edges within the scenario (the given Edge ID is ignored)
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<String> getEdgeIDsInternal() {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            return new ArrayList<>(); // empty list of strings
        }
        try {
            Object response = this.traci.getConnection().do_job_get(Edge.getIDList());
            if (response instanceof String[]) {
                return Arrays.asList((String[]) response);
            } else if (response instanceof List) {
                return (List<String>) response;
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get Edge IDs", e);
        }
        return new ArrayList<>();
    }
    /**
     * Returns the number of edges within the scenario (the given Edge ID is ignored)
     * @return
     */
    public int getEdgeCount() {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            return 0;
        }
        try {
            return (int) this.traci.getConnection().do_job_get(Edge.getIDCount());
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get Edge count", e);
        }
        return 0;
    }
    /**
     * Returns the number of lanes for the given edge ID
     * @param id edgeID
     * @return
     */
    public int getLaneNumberOfEdge(String id) {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            return 0;
        }
        try {
            return (int) this.traci.getConnection().do_job_get(Edge.getLaneNumber(id));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get Edge count", e);
        }
        return 0;
    }
    /**
     * Set the new maximum speed (in m/s) for this edge
     * @param id EdgeID
     * @param newMax speed in m/s
     */
    public void setMaxSpeed(String id, double newMax) {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            return;
        }
        try {
            this.traci.getConnection().do_job_set(Edge.setMaxSpeed(id, newMax));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to set Edge max speed", e);
        }
    }
    /**
     * return the average number of vehicles per edge
     * vehicle density per edge
     * @return
     */
    public double getAvgVehiclesPerEdge() {
        return (double)vehWrapper.getVehicleCount() / this.getEdgeCount();
    }
    /**
     * Returns the mean speed of vehicles that were on the named edge within the last simulation step [m/s]
     * @param id edgeID
     * @return -1 if error
     */
    public double getLastStepMeanSpeed(String id) {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            return -1; // error
        }
        try {
            return (double) this.traci.getConnection().do_job_get(Edge.getLastStepMeanSpeed(id));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get last step mean speed", e);
        }
        return -1; // error
    }
    /**
     * The number of vehicles on this edge within the last time step.
     * @param id edgeID
     * @return
     */
    public int getLastStepVehicleNumber(String id) {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            return 0;
        }
        try {
            return (int) this.traci.getConnection().do_job_get(Edge.getLastStepVehicleNumber(id));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get last step mean speed", e);
        }
        return 0;
    }
    @SuppressWarnings("unchecked")
    /**
     * Returns the list of ids of vehicles that were on the named edge in the last simulation step. The order is from rightmost to leftmost lane and downstream for each lane.
     * @param id
     * @return null if not found
     */
    public List<String> getLastStepVehicleIDs(String id) {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            return null;
        }
        try {
            return (List<String>) this.traci.getConnection().do_job_get(Edge.getLastStepVehicleIDs(id));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get last step mean speed", e);
        }
        return null;
    }
    /**
     * Returns the sum of the waiting times for all vehicles on the edge [s]
     * @param id
     * @return time in seconds
     */
    public double getWaitingTimeSum(String id) {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            return 0;
        }
        try {
            return (double) this.traci.getConnection().do_job_get(Edge.getWaitingTime(id));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get last step mean speed", e);
        }
        return 0;
    }
    /**
     * Returns the total number of halting vehicles for the last time step on the given edge. A speed of less than 0.1 m/s is considered a halt.
     * @param id
     * @return 0 if not found
     */
    public int getLastStepHaltingNumber(String id) {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            return 0;
        }
        try {
            return (int) this.traci.getConnection().do_job_get(Edge.getLastStepHaltingNumber(id));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get last step mean speed", e);
        }
        return 0;
    }
    /**
     * Returns the current travel time (length/mean speed).
     * Expected time to travel this edge
     * @param id edgeID
     * @return 0 if not found
     */
    public double getTravelTime(String id) {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            return 0;
        }
        try {
            return (double) this.traci.getConnection().do_job_get(Edge.getTraveltime(id));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get last step mean speed", e);
        }
        return 0;
    }
}
