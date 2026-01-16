import de.tudresden.sumo.util.SumoCommand;
import de.tudresden.sumo.config.Constants;

/**
 * Create BusStop's wrapper class due to lack of TraaS support.
 * Using refs from: https://github.com/eclipse-sumo/sumo/blob/main/tools/traci/constants.py
 */

public class BusStop {
    // NOTE: BusStop start/end positions are accessed via the generic
    // TraCI variables VAR_POSITION and VAR_LANEPOSITION.

    /**
     * Returns a list of ids of all bus stops within the simulation.
     * @return SumoCommand to get the list of bus stop IDs
     */
    public static SumoCommand getIDList() {
        return new SumoCommand(Constants.CMD_GET_BUSSTOP_VARIABLE, Constants.TRACI_ID_LIST, "", Constants.TYPE_STRINGLIST);
    }

    /**
     * Returns the lane ID where the bus stop is located.
     * @param stopID The ID of the bus stop
     * @return SumoCommand to get the lane ID
     */
    public static SumoCommand getLaneID(String stopID) {
        return new SumoCommand(Constants.CMD_GET_BUSSTOP_VARIABLE, Constants.VAR_LANE_ID, stopID, Constants.TYPE_STRING);
    }

    /**
     * Returns the number of waiting persons at the given bus stop.
     * @param stopID The ID of the bus stop
     * @return SumoCommand to get the person count
     */
    public static SumoCommand getPersonCount(String stopID) {
        return new SumoCommand(Constants.CMD_GET_BUSSTOP_VARIABLE, Constants.VAR_PERSON_NUMBER, stopID, Constants.TYPE_INTEGER);
    }

    /**
     * Returns the start position of the bus stop on the lane (distance from lane start in meters).
     * @param stopID The ID of the bus stop
     * @return SumoCommand to get the start position
     */
    public static SumoCommand getStartPos(String stopID) {
        return new SumoCommand(Constants.CMD_GET_BUSSTOP_VARIABLE, Constants.VAR_POSITION, stopID, Constants.TYPE_DOUBLE);
    }

    /**
     * Returns the end position of the bus stop on the lane (distance from lane start in meters).
     * @param stopID The ID of the bus stop
     * @return SumoCommand to get the end position
     */
    public static SumoCommand getEndPos(String stopID) {
        return new SumoCommand(Constants.CMD_GET_BUSSTOP_VARIABLE, Constants.VAR_LANEPOSITION, stopID, Constants.TYPE_DOUBLE);
    }

    /**
     * Returns the name of the bus stop.
     * @param stopID The ID of the bus stop
     * @return SumoCommand to get the name
     */
    public static SumoCommand getName(String stopID) {
        return new SumoCommand(Constants.CMD_GET_BUSSTOP_VARIABLE, Constants.VAR_NAME, stopID, Constants.TYPE_STRING);
    }
}
