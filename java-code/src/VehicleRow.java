import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class VehicleRow {

    private final StringProperty id = new SimpleStringProperty();
    private final DoubleProperty speed = new SimpleDoubleProperty();
    private final StringProperty edge = new SimpleStringProperty();

    public VehicleRow(String id, double speed, String edge) {
        this.id.set(id);
        this.speed.set(speed);
        this.edge.set(edge);
    }

    public String getId() { return id.get(); }
    public void setId(String v) { id.set(v); }
    public StringProperty idProperty() { return id; }

    public double getSpeed() { return speed.get(); }
    public void setSpeed(double v) { speed.set(v); }
    public DoubleProperty speedProperty() { return speed; }

    public String getEdge() { return edge.get(); }
    public void setEdge(String v) { edge.set(v); }
    public StringProperty edgeProperty() { return edge; }
}
