import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.paint.Color;

public class VehicleRow {
    // private fields
    private StringProperty id = new SimpleStringProperty();
    private DoubleProperty speed = new SimpleDoubleProperty();
    private StringProperty edge = new SimpleStringProperty();
    private Color color; // not SumoColor

    public VehicleRow(String id, double speed, String edge, Color color) {
        this.id.set(id);
        this.speed.set(speed);
        this.edge.set(edge);
        this.color = color;
    }

    public String getId() {return this.id.get();}
    public void setId(String v) {this.id.set(v);}
    public StringProperty idProperty() {return this.id;}

    public double getSpeed() {return this.speed.get();}
    public void setSpeed(double v) {this.speed.set(v);}
    public DoubleProperty speedProperty() {return this.speed;}

    public String getEdge() {return this.edge.get();}
    public void setEdge(String v) {this.edge.set(v);}
    public StringProperty edgeProperty() {return this.edge;}

    public Color getColor() {return this.color;}
    public void setColor(Color newColor) {this.color = newColor;}
    public StringProperty colorProperty() {return new SimpleStringProperty(this.color.toString());}
}
