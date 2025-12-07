import de.tudresden.sumo.cmd.Trafficlight;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

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
        if (!connector.isConnected()) return new ArrayList<>();
        try {
            Object response = connector.getConnection().do_job_get(Trafficlight.getIDList());
            if (response instanceof String[]) {
                return Arrays.asList((String[]) response);
            } else if (response instanceof List) {
                return (List<String>) response;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Gets the current state (Red/Green/Yellow) of a traffic light.
     */
    public String getTrafficLightState(String id) {
        if (!connector.isConnected()) return "N/A";
        try {
            return (String) connector.getConnection().do_job_get(Trafficlight.getRedYellowGreenState(id));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Error";
    }
}
