import javafx.fxml.FXML;

public class MainUI {

    public MainUI() {
        // constructor - called when FXML is loaded
    }

    @FXML
    private void initialize() {
        // this runs after all @FXML fields are injected
        System.out.println("MainController initialized!");
    }
}
