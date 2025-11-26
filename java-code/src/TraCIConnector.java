public class TraCIConnector {
    private SumoTraciConnection connection;
    private boolean isConnected;
    private int currentStep;

    public TraCIConnector(String sumoBinary, String configFile) {}

    public boolean connect() {} //conn.runServer() từ Main.java

    public boolean simulationStep() {} //conn.do_timestep() từ Main.java

    public int getVehicleCount() {} //Vehicle.getIDCount() từ Main.java

    public void disconnect() {} //conn.close() từ Main.java

    public boolean isConnected() {}
};