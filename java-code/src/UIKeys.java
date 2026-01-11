import javafx.scene.input.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;



/**
 * Class for Changing TrafficLightState via Keys (UP, DOWN, LEFT, RIGHT)
 */

public class UIKeys {
    private static final Logger LOGGER = Logger.getLogger(UIKeys.class.getName());
    private TrafficLightWrapper trafficLightWrapper;
    private int totalTrafficLights;
    private UI uiController;

    private int currentTrafficLightIndex;
    private int currenPhaseIndex;
    private String selectedTrafficLightId;

    // Predefined traffic light states (example patterns)
    private List<String> trafficLightStates;


    public UIKeys(TrafficLightWrapper trafficLightWrapper, UI uiController) {
        this.trafficLightWrapper = trafficLightWrapper;
        this.uiController = uiController;
        trafficLightStates = new ArrayList<>();
        totalTrafficLights = trafficLightWrapper.getTrafficLightCount();

        // Select the first traffic light by default
        selectFirstTrafficLight();
    }
    public int getCurrentTrafficLightIndex() {
        return currentTrafficLightIndex;
    }

    /**
     * Select the first traffic light from the available list
     */
    public void selectFirstTrafficLight() {
        List<String> trafficLightIds = trafficLightWrapper.getTrafficLightIds();
        if (trafficLightIds != null && !trafficLightIds.isEmpty()) {
            selectedTrafficLightId = trafficLightIds.get(0);
            currentTrafficLightIndex = 0;
            currenPhaseIndex = trafficLightWrapper.getPhaseIndex(selectedTrafficLightId);
            trafficLightStates = trafficLightWrapper.getAllTrafficLightStates(selectedTrafficLightId);
            LOGGER.info("Selected traffic light: " + selectedTrafficLightId);
        } else {
            LOGGER.warning("No traffic lights found in the simulation");
        }
    }

    /**
     * Select next traffic light in the list
     */
    public void selectNextTrafficLight() {
        List<String> trafficLightIds = trafficLightWrapper.getTrafficLightIds();
        if (trafficLightIds != null && !trafficLightIds.isEmpty()) {
            if (currentTrafficLightIndex == totalTrafficLights - 1) {
                selectFirstTrafficLight();
            } else {
                currentTrafficLightIndex++;
                selectedTrafficLightId = trafficLightIds.get(currentTrafficLightIndex);
                currenPhaseIndex = trafficLightWrapper.getPhaseIndex(selectedTrafficLightId);
                trafficLightStates = trafficLightWrapper.getAllTrafficLightStates(selectedTrafficLightId);
                LOGGER.info("Selected traffic light: " + selectedTrafficLightId);
            }
        } else {
            LOGGER.warning("No traffic lights found in the simulation");
        }
    }

    /**
     * Select previous traffic light in the list
     */
    public void selectPreviousTrafficLight() {
        List<String> trafficLightIds = trafficLightWrapper.getTrafficLightIds();
        if (trafficLightIds != null && !trafficLightIds.isEmpty()) {
            if (currentTrafficLightIndex == 0) {
                currentTrafficLightIndex = totalTrafficLights - 1;

            } else {
                currentTrafficLightIndex++;
            }
            selectedTrafficLightId = trafficLightIds.get(currentTrafficLightIndex);
            currenPhaseIndex = trafficLightWrapper.getPhaseIndex(selectedTrafficLightId);
            trafficLightStates = trafficLightWrapper.getAllTrafficLightStates(selectedTrafficLightId);
            LOGGER.info("Selected traffic light: " + selectedTrafficLightId);
        } else {
            LOGGER.warning("No traffic lights found in the simulation");
        }
    }

    /**
     * Update current state index based on actual traffic light state
     */
    public void updateCurrentStateIndex(String currentState) {
        if (currentState != null && !currentState.equals("Error") && !currentState.equals("N/A")) {
            int index = trafficLightStates.indexOf(currentState);
            if (index != -1) {
                currentTrafficLightIndex = index;
                LOGGER.info("Updated current state index to: " + index);
            }
        }
    }

    /**
     * Pause / Resume the Simulation by Printing P
     */
    public void togglePause() {
        if(uiController != null) {
            uiController.onStartPause();
        }
        if (trafficLightWrapper.isPaused()) {
            LOGGER.info("Paused");
        } else {
            LOGGER.info("Resumed");
        }
    }

}