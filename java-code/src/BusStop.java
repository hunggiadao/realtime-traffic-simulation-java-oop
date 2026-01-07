import de.tudresden.sumo.util.SumoCommand;

/**
 * Create BusStop's wrapper class due to lack of TraaS support. 
 * Using refs from: https://github.com/eclipse-sumo/sumo/blob/main/tools/traci/constants.py
 */

public class BusStop {
    // --- Define Traci protocol constants using hex values ---
    // Command ID for BusStop.
    public static final int CMD_GET_BUSSTOP_VARIABLE = 0xaf;

    // Variable ID for Lane ID.
    public static final int VAR_LANE_ID = 0x51; 

    // Variable ID for Person Number.
    public static final int VAR_PERSON_NUMBER = 0x67;

    // --- TraCI data types ---
    private static final int TYPE_INTEGER = 0x09;
    private static final int TYPE_STRING = 0x0C;
    private static final int TYPE_STRINGLIST = 0x0E;
    private static final int ID_LIST = 0x00;
    // ============================================================

    /**
     * Returns a list of ids of all bus stops within the simulation.
     * @return SumoCommand to get the list of bus stop IDs
     */
    public static SumoCommand getIDList() {
        return new SumoCommand(CMD_GET_BUSSTOP_VARIABLE, ID_LIST, "", TYPE_STRINGLIST);
    }

    /**
     * Returns the lane ID where the bus stop is located.
     * @param stopID The ID of the bus stop
     * @return SumoCommand to get the lane ID
     */
    public static SumoCommand getLaneID(String stopID) {
        return new SumoCommand(CMD_GET_BUSSTOP_VARIABLE, VAR_LANE_ID, stopID, TYPE_STRING);
    }

    /**
     * Returns the number of waiting persons at the given bus stop.
     * @param stopID The ID of the bus stop
     * @return SumoCommand to get the person count
     */
    public static SumoCommand getPersonCount(String stopID) {
        return new SumoCommand(CMD_GET_BUSSTOP_VARIABLE, VAR_PERSON_NUMBER, stopID, TYPE_INTEGER);
    }
}
