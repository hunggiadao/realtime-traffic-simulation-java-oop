public class VehicleWrapper {
    private TraCIConnector connector;

    public VehicleWrapper(TraCIConnector connector) {
        this.connector = connector;
    }

    public int getVehicleCount() {
        return connector.getVehicleCount();
    }
}