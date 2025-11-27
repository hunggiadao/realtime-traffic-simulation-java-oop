import java.util.List;
import java.util.ArrayList;

/**
 * Wrapper class for Traffic Lights in SUMO.
 * Used to get traffic light IDs and their states.
 */
public class TrafficLightWrapper {
    private TraCIConnector connector;

    public TrafficLightWrapper(TraCIConnector connector) {
        this.connector = connector;
    }

    /**
     * Gets a list of all traffic light IDs in the map.
     */
    public List<String> getTrafficLightIds() {
        //  TO DO: Retrieve the list of traffic light IDs from SUMO
        return new ArrayList<String>();
    }

    /**
     * Gets the current state (Red/Green/Yellow) of a traffic light.
     * @param tlids The ID of the traffic light.
     */
    public List<String> getTrafficLightState(String tlids) {
        //  TO DO: Retrieve the state of a specific traffic light
        return new ArrayList<String>();
    }
}