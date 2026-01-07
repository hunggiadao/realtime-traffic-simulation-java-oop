import de.tudresden.sumo.cmd.*;
import de.tudresden.sumo.cmd.Trafficlight;

import de.tudresden.sumo.config.Constants;
//import de.tudresden.sumo.objects.SumoTLSProgram;
import de.tudresden.sumo.util.SumoCommand;
import de.tudresden.sumo.objects.SumoTLSController;
import de.tudresden.sumo.objects.SumoTLSProgram;
import de.tudresden.sumo.objects.SumoTLSPhase;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper class for Traffic Lights in SUMO.
 * Used to get traffic light IDs and their states.
 */
public final class TrafficLightWrapper {
    private static final Logger LOGGER = Logger.getLogger(TrafficLightWrapper.class.getName());
    private final TraCIConnector traci;
    
    public TrafficLightWrapper(TraCIConnector traci) {
    	this.traci = Objects.requireNonNull(traci, "traci");
    }
    
    /**
     * Gets a list of all traffic light IDs in the map.
     * @return List of Traffic light IDs
     */
    @SuppressWarnings("unchecked")
    public List<String> getTrafficLightIds() {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) {
        	return new ArrayList<>(); // empty list of strings
        }
        try {
            Object response = this.traci.getConnection().do_job_get(Trafficlight.getIDList());
            if (response instanceof String[]) {
                return Arrays.asList((String[]) response);
            } else if (response instanceof List) {
                return (List<String>) response;
            }
        } catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to get traffic light IDs", e);
        }
        return new ArrayList<>();
    }
    /**
     * Gets the current state (Red/Green/Yellow) of a traffic light.
     * @return RYG state of a specified Traffic light
     */
    public String getTrafficLightState(String id) {
        if (this.traci.getConnection() == null || !this.traci.isConnected()) return "N/A";
        try {
            return (String) this.traci.getConnection().do_job_get(Trafficlight.getRedYellowGreenState(id));
        } catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to get traffic light state for id=" + id, e);
        }
        return "Error";
    }
    /**
     * Sets the phase definition to the given in the form of rRgGyYoO
     * @param id
     * @param newState
     */
    public void setTrafficLightState(String id, String newState) {
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) {
			LOGGER.fine("Cannot set state: connection not available");
    	};
    	try {
            this.traci.getConnection().do_job_set(Trafficlight.setRedYellowGreenState(id, newState));
        } catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to set traffic light state for id=" + id, e);
        }
    }
    /**
     * Returns the number of traffic lights within the scenario
     */
    public int getTrafficLightCount() {
    	//  Retrieve the traffic light count from SUMO
		if (this.traci.getConnection() == null || !this.traci.isConnected()) return 0;
        try {
            return (int) this.traci.getConnection().do_job_get(Trafficlight.getIDCount());
        } catch (Exception e) {
			LOGGER.log(Level.FINE, "Failed to get traffic light count", e);
        }
        return -1; // code for error, only if connection exists and fails to get count
    }
    /**
     * Returns the default total duration of the currently active phase in seconds
     * @param id
     * @return
     */
    public double getPhaseDuration(String id) {
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) return 0;
    	try {
    		return (double) this.traci.getConnection().do_job_get(Trafficlight.getPhaseDuration(id));
    	} catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get phase duration for id=" + id, e);
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
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            LOGGER.fine("Cannot set remaining phase duration: connection not available");
    	}
    	try {
    		this.traci.getConnection().do_job_set(Trafficlight.setPhaseDuration(id, newRemaining));
    	} catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to set remaining phase duration for id=" + id, e);
    	}
    }
    /**
     * Eclipse can't find getSpentDuration(), so this is using SumoCommand syntax
     * Returns the time in seconds for which the current phase has been active
     * @param id
     * @return
     */
    public double getPhaseElapsedDuration(String id) {
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) return 0;
    	try {
    		return (double) this.traci.getConnection().do_job_get(
    			new SumoCommand(Constants.CMD_GET_TL_VARIABLE, Constants.TL_SPENT_DURATION, id,
    				Constants.RESPONSE_GET_TL_VARIABLE, Constants.TYPE_DOUBLE));
    	} catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get phase elapsed duration for id=" + id, e);
    	}
    	return 0;
    }
    /**
     * Returns the name of the current phase as a String.
     * @param id
     * @return
     */
    public String getPhaseName(String id) {
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) return null;
    	try {
    		return (String) this.traci.getConnection().do_job_get(Trafficlight.getPhaseName(id));
    	} catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get phase name for id=" + id, e);
    	}
    	return null;
    }
    /**
     * Set newName as the name of the current phase of this TL id
     * @param id
     * @param newName
     */
    public void setPhaseName(String id, String newName) {
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) return;
    	try {
    		this.traci.getConnection().do_job_set(Trafficlight.setPhaseName(id, newName));
    	} catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to set phase name for id=" + id, e);
    	}
    }
    /**
     * Returns the index of the current phase within the list
     * of all phases of the traffic light.
     * @param id
     * @return
     */
    public int getPhaseIndex(String id) {
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) return -1; // error code
    	try {
    		return (int) this.traci.getConnection().do_job_get(Trafficlight.getPhase(id));
    	} catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get phase index for id=" + id, e);
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
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) {
            LOGGER.fine("Cannot set phase index: connection not available");
    	}
    	try {
    		this.traci.getConnection().do_job_set(Trafficlight.setPhase(id, newIndex));
    	} catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to set phase index for id=" + id + ", newIndex=" + newIndex, e);
    	}
    }
    /**
     * Return a SumoTLSController for a specific traffic light
     * Allows user to directly access and control that traffic light
     * @param id
     * @return
     */
    public SumoTLSController getRGBDefinition(String id) {
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) {
    		return null;
    	}
    	try {
    		Object def = this.traci.getConnection().do_job_get(Trafficlight.getCompleteRedYellowGreenDefinition(id));
			
			if (def instanceof SumoTLSController) {
				return (SumoTLSController) def;
			}
    	} catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to cast Object to SumoTLSController", e);
    	}
    	return null; // error
    }
    /**
     * Returns the tuple of vehicles that are blocking the subsequent
     * block for the given tls-linkIndex
     * How many cars are blocking this lane's traffic?
     */
    @SuppressWarnings("unchecked")
	public List<String> getBlockingVehicles(String id, int index) {
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) {
    		return new ArrayList<String>(); // empty array
    	}
    	try {
    		// not sure if this is logically correct, no docs for this
    		Object response = this.traci.getConnection().do_job_get(
    			new SumoCommand(Constants.CMD_GET_TL_VARIABLE, Constants.TL_BLOCKING_VEHICLES, id,
    				Constants.RESPONSE_GET_TL_VARIABLE, Constants.TYPE_STRINGLIST, index + ""));
    		if (response instanceof String[]) {
                return Arrays.asList((String[]) response);
            } else if (response instanceof List) {
                return (List<String>) response;
            }
    	} catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get blocking vehicles for id=" + id + ", index=" + index, e);
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
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) {
    		return new ArrayList<String>(); // empty array
    	}
    	try {
    		// not sure if this is logically correct, no docs for this
    		Object response = this.traci.getConnection().do_job_get(
    			new SumoCommand(Constants.CMD_GET_TL_VARIABLE, Constants.TL_RIVAL_VEHICLES, id,
    				Constants.RESPONSE_GET_TL_VARIABLE, Constants.TYPE_STRINGLIST, index + ""));
    		if (response instanceof String[]) {
                return Arrays.asList((String[]) response);
            } else if (response instanceof List) {
                return (List<String>) response;
            }
    	} catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get waiting vehicles for id=" + id + ", index=" + index, e);
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
    	if (this.traci.getConnection() == null || !this.traci.isConnected()) {
    		return new ArrayList<String>(); // empty array
    	}
    	try {
    		// not sure if this is logically correct, no docs for this
    		Object response = this.traci.getConnection().do_job_get(
    			new SumoCommand(Constants.CMD_GET_TL_VARIABLE, Constants.TL_PRIORITY_VEHICLES, id,
    				Constants.RESPONSE_GET_TL_VARIABLE, Constants.TYPE_STRINGLIST, index + ""));
    		if (response instanceof String[]) {
                return Arrays.asList((String[]) response);
            } else if (response instanceof List) {
                return (List<String>) response;
            }
    	} catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get waiting priority vehicles for id=" + id + ", index=" + index, e);
    	}
    	return new ArrayList<String>(); // error
    }
    
    // not yet implemented
//    addConstraint();
//    removeConstraint();
//    updateConstraints();
//    getControlledLinks(tlId)
}
























