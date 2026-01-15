import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

final class UIHandlers {
    private UIHandlers() {
    }

    static void handleKeyRelease(UI ui, KeyEvent event) {
        KeyCode code = event.getCode();

        switch (code) {
            case UP:
                System.out.println("Up");
                ui.changeTrafficLightPhase(1);
                break;
            case DOWN:
                System.out.println("Down");
                ui.changeTrafficLightPhase(-1);
                break;
            case LEFT:
                System.out.println("Left");
                ui.keyController.selectPreviousTrafficLight();
                ui.cmbTrafficLight.getSelectionModel().select(ui.keyController.getCurrentTrafficLightIndex());
                break;
            case RIGHT:
                System.out.println("Right");
                ui.keyController.selectNextTrafficLight();
                ui.cmbTrafficLight.getSelectionModel().select(ui.keyController.getCurrentTrafficLightIndex());
                break;
            case P:
                System.out.println("P");
                ui.keyController.togglePause();
                break;
            default:
                break;
        }
    }

    static void onOpenConfig(UI ui) {
        Window window = null;
        if (ui.btnBrowseConfig != null && ui.btnBrowseConfig.getScene() != null) {
            window = ui.btnBrowseConfig.getScene().getWindow();
        } else if (ui.btnOpenConfig != null && ui.btnOpenConfig.getScene() != null) {
            window = ui.btnOpenConfig.getScene().getWindow();
        } else if (ui.rootPane != null && ui.rootPane.getScene() != null) {
            window = ui.rootPane.getScene().getWindow();
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open SUMO Config");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SUMO config (*.sumocfg)", "*.sumocfg")
        );
        File file = chooser.showOpenDialog(window);
        if (file != null && ui.txtConfigPath != null) {
            ui.txtConfigPath.setText(file.getAbsolutePath());
        }
    }

    static void onInject(UI ui) {
        if (ui.connector == null || !ui.connector.isConnected()) {
            ui.setStatusText("Status: Not connected");
            return;
        }

        // edge where new vehicles are injected
        String edge = "";
        if (ui.cmbInjectEdge != null) {
            edge = ui.cmbInjectEdge.getValue();
            if (edge == null || edge.isEmpty()) edge = ui.cmbInjectEdge.getEditor().getText();
        }
        if (edge == null || edge.isEmpty()) {
            ui.setStatusText("Status: Edge ID required");
            return;
        }

        // number of injected vehicles
        int count = 1;
        try {
            if (ui.txtInjectCount != null) count = Integer.parseInt(ui.txtInjectCount.getText().trim());
        } catch (NumberFormatException e) {
        }

        // color of vehicles
        javafx.scene.paint.Color color = (ui.cpInjectColor != null) ? ui.cpInjectColor.getValue() : javafx.scene.paint.Color.RED;
        if (color == null) color = javafx.scene.paint.Color.RED;

        // Use edge as route ID for now
        String routeId = edge;

        if (ui.running) {
            for (int i = 0; i < count; i++) {
                ui.pendingInjections.addLast(new UI.PendingInjection(routeId, color));
            }
            ui.processPendingInjections(System.nanoTime());
            ui.setStatusText("Status: Queued " + count + " vehicles");
            ui.pendingMapRefresh = true;
            return;
        }

        // If paused, keep legacy behavior: inject immediately.
        for (int i = 0; i < count; i++) {
            String vehId = "inj_" + (++ui.injectSeq);
            if (ui.vehicleWrapper != null) {
                ui.vehicleWrapper.addVehicle(vehId, routeId, UI.DEFAULT_INJECT_SPEED_MS, color);
            }
        }
        ui.setStatusText("Status: Injected " + count + " vehicles");
        ui.updateMapView();
    }
}
