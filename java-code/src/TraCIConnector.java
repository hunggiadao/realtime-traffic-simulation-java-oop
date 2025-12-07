import it.polito.appeal.traci.SumoTraciConnection;
import de.tudresden.sumo.cmd.Vehicle;

/**
 * Wrapper class for managing the TraCI connection to SUMO.
 * Handles connection lifecycle and basic simulation stepping.
 */
public class TraCIConnector {
    private final String sumoBinary;
    private final String configFile;
    private final double stepLengthSeconds;

    private SumoTraciConnection connection;
    private boolean isConnected = false;
    private int currentStep = 0;

    public TraCIConnector(String sumoBinary, String configFile, double stepLengthSeconds) {
        //  TO DO: Initialize connection logic code
        this.sumoBinary = sumoBinary;
        this.configFile = configFile;
        this.stepLengthSeconds = stepLengthSeconds;
    }

    public boolean connect() {
        //  TO DO: Start SUMO
        //  conn.runServer() from Main.java
        try {
            connection = new SumoTraciConnection(sumoBinary, configFile);
            connection.addOption("start", "true");
            connection.addOption("delay", "50");
            connection.addOption("step-length", Double.toString(stepLengthSeconds));

            connection.runServer();
            isConnected = true;
            currentStep = 0;
            System.out.println("TraCIConnector: Connected to SUMO");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            isConnected = false;
            return false;
        }
    }

    public boolean simulationStep() {
        //  TO DO: Advance simulation by one step
        //  conn.do_timestep() from Main.java
        if (!isConnected || connection == null) {
            return false;
        }
        try {
            connection.do_timestep();
            currentStep++;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public int getVehicleCount() {
        //  TO DO: Retrieve the vehicle count from SUMO
        //  Vehicle.getIDCount() from Main.java
        if (!isConnected || connection == null) {
            return 0;
        }
        try {
            return (int) connection.do_job_get(Vehicle.getIDCount());
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void disconnect() {
        //  TO DO: Close connection
        //  conn.close() from Main.java
        if (!isConnected || connection == null) {
            return;
        }
        try {
            connection.close();
            System.out.println("TraCIConnector: Connection closed");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isConnected = false;
        }
    } 

    public boolean isConnected() {
        //  TO DO: Return connection status
        return isConnected;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public double getSimTimeSeconds() {
        return currentStep * stepLengthSeconds;
    }

    public SumoTraciConnection getConnection() {
        return connection;
    }
}
