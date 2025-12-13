import de.tudresden.sumo.*;
import de.tudresden.sumo.cmd.*;
import de.tudresden.sumo.cmd.Trafficlight;

import de.tudresden.sumo.config.Constants;
import de.tudresden.sumo.objects.SumoTLSProgram;
import de.tudresden.sumo.util.SumoCommand;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Wrapper class for Traffic Lights in SUMO.
 * Used to get traffic light IDs and their states.
 */
public abstract class TrafficLightWrapper extends TraCIConnector {
    /**
     * Gets a list of all traffic light IDs in the map.
     * @return List of Traffic light IDs
     */
    @SuppressWarnings("unchecked")
    public List<String> getTrafficLightIds() {
        if (this.getConnection() == null || !this.isConnected()) {
        	return new ArrayList<>(); // empty list of strings
        }
        try {
            Object response = this.getConnection().do_job_get(Trafficlight.getIDList());
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
     * @return RYG state of a specified Traffic light
     */
    public String getTrafficLightState(String id) {
        if (this.getConnection() == null || !this.isConnected()) return "N/A";
        try {
            return (String) this.getConnection().do_job_get(Trafficlight.getRedYellowGreenState(id));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Error";
    }
    /**
     * Sets the phase definition to the given in the form of rRgGyYoO
     * @param id
     * @param newState
     */
    public void setTrafficLightState(String id, String newState) {
    	if (this.getConnection() == null || !this.isConnected()) {
    		System.out.println("Cannot set state. Problem with connector");
    	};
    	try {
            this.getConnection().do_job_set(Trafficlight.setRedYellowGreenState(id, newState));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Returns the number of traffic lights within the scenario
     */
    public int getTrafficLightCount() {
    	//  Retrieve the traffic light count from SUMO
		if (this.getConnection() == null || !this.isConnected()) return 0;
        try {
            return (int) this.getConnection().do_job_get(Trafficlight.getIDCount());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1; // code for error, only if connection exists and fails to get count
    }
    /**
     * Returns the default total duration of the currently active phase in seconds
     * @param id
     * @return
     */
    public double getPhaseDuration(String id) {
    	if (this.getConnection() == null || !this.isConnected()) return 0;
    	try {
    		return (double) this.getConnection().do_job_get(Trafficlight.getPhaseDuration(id));
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return 0;
    }
    /**
     * Set the REMAINING phase duration of the current phase in seconds.
     * This value has no effect on subsequent repetitions of this phase.
     * @param id
     * @param newDuration
     */
    public void setRemainingPhaseDuration(String id, double newRemaining) {
    	if (this.getConnection() == null || !this.isConnected()) {
    		System.out.println("Error, connection does not exist");
    	}
    	try {
    		this.getConnection().do_job_set(Trafficlight.setPhaseDuration(id, newRemaining));
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    /**
     * Eclipse can't find getSpentDuration(), so this is using SumoCommand syntax
     * Returns the time in seconds for which the current phase has been active
     * @param id
     * @return
     */
    public double getPhaseElapsedDuration(String id) {
    	if (this.getConnection() == null || !this.isConnected()) return 0;
    	try {
    		return (double) this.getConnection().do_job_get(
    			new SumoCommand(Constants.CMD_GET_TL_VARIABLE, Constants.TL_SPENT_DURATION, id,
    				Constants.RESPONSE_GET_TL_VARIABLE, Constants.TYPE_DOUBLE));
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return 0;
    }
    /**
     * Returns the name of the current phase as a String.
     * @param id
     * @return
     */
    public String getPhaseName(String id) {
    	if (this.getConnection() == null || !this.isConnected()) return null;
    	try {
    		return (String) this.getConnection().do_job_get(Trafficlight.getPhaseName(id));
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return null;
    }
    /**
     * Returns the index of the current phase within the list
     * of all phases of the traffic light.
     * @param id
     * @return
     */
    public int getPhaseIndex(String id) {
    	if (this.getConnection() == null || !this.isConnected()) return -1; // error code
    	try {
    		return (int) this.getConnection().do_job_get(Trafficlight.getPhase(id));
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return -1;
    }
    /**
     * Sets the phase of the traffic light to the given index
     * The given index must be between 0 and length(phases)
     * @param id
     * @param newIndex
     */
    public void setPhaseIndex(String id, int newIndex) {
    	// TODO: need to figure out how to calulation length(phases)
    	// since there is no native method
    	if (this.getConnection() == null || !this.isConnected()) {
    		System.out.println("Error, connection does not exist");
    	}
    	try {
    		this.getConnection().do_job_set(Trafficlight.setPhase(id, newIndex));
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    /**
     * Returns the tuple of vehicles that are blocking the subsequent
     * block for the given tls-linkIndex
     * How many cars are blocking this lane's traffic?
     */
    @SuppressWarnings("unchecked")
	public List<String> getBlockingVehicles(String id, int index) {
    	if (this.getConnection() == null || !this.isConnected()) {
    		return new ArrayList<String>(); // empty array
    	}
    	try {
    		// not sure if this is logically correct, no docs for this
    		Object response = this.getConnection().do_job_get(
    			new SumoCommand(Constants.CMD_GET_TL_VARIABLE, Constants.TL_BLOCKING_VEHICLES, id,
    				Constants.RESPONSE_GET_TL_VARIABLE, Constants.TYPE_STRINGLIST, index + ""));
    		if (response instanceof String[]) return (List<String>) response;
    		return new ArrayList<String>(); // error
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return new ArrayList<String>(); // error
    }
    /**
     * Returns the tuple of vehicles that also wish to enter the
     * subsequent block for the given tls-linkIndex (regardless of priority)
     * How many cars are waiting to move in this lane?
     */
    @SuppressWarnings("unchecked")
	public List<String> getWaitingVehicles(String id, int index) {
    	if (this.getConnection() == null || !this.isConnected()) {
    		return new ArrayList<String>(); // empty array
    	}
    	try {
    		// not sure if this is logically correct, no docs for this
    		Object response = this.getConnection().do_job_get(
    			new SumoCommand(Constants.CMD_GET_TL_VARIABLE, Constants.TL_RIVAL_VEHICLES, id,
    				Constants.RESPONSE_GET_TL_VARIABLE, Constants.TYPE_STRINGLIST, index + ""));
    		if (response instanceof String[]) return (List<String>) response;
    		return new ArrayList<String>(); // error
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return new ArrayList<String>(); // error
    }
    /**
     * Returns the tuple of vehicles that also wish to enter the
     * subsequent block for the given tls-linkIndex (only those with higher priority)
     * How many high priority cars are waiting to move in this lane?
     */
    @SuppressWarnings("unchecked")
	public List<String> getWaitingPriorityVehicles(String id, int index) {
    	if (this.getConnection() == null || !this.isConnected()) {
    		return new ArrayList<String>(); // empty array
    	}
    	try {
    		// not sure if this is logically correct, no docs for this
    		Object response = this.getConnection().do_job_get(
    			new SumoCommand(Constants.CMD_GET_TL_VARIABLE, Constants.TL_PRIORITY_VEHICLES, id,
    				Constants.RESPONSE_GET_TL_VARIABLE, Constants.TYPE_STRINGLIST, index + ""));
    		if (response instanceof String[]) return (List<String>) response;
    		return new ArrayList<String>(); // error
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return new ArrayList<String>(); // error
    }
    
    // not yet implemented
//    addConstraint();
//    removeConstraint();
//    updateConstraints();
}
























