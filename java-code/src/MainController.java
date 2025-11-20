import javafx.fxml.FXML;

public class MainController {

    public MainController() {
        // constructor - called when FXML is loaded
    }

    @FXML
    private void initialize() {
        // this runs after all @FXML fields are injected
        System.out.println("MainController initialized!");
    }
}
