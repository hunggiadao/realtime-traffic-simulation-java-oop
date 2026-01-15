import javafx.scene.paint.Color;

final class UIState {
    private UIState() {
    }

    static void setDisconnectedUI(UI ui) {
        if (ui.lblStatus != null) ui.lblStatus.setText("Status: Disconnected");
        if (ui.btnConnect != null) {
            ui.btnConnect.setDisable(false);
            ui.btnConnect.setText("Connect");
        }
        if (ui.btnOpenConfig != null) ui.btnOpenConfig.setDisable(false);
        if (ui.btnBrowseConfig != null) ui.btnBrowseConfig.setDisable(false);
        if (ui.txtConfigPath != null) ui.txtConfigPath.setDisable(false);
        if (ui.txtStepMs != null) ui.txtStepMs.setDisable(false);
        if (ui.btnStart != null) {
            ui.btnStart.setDisable(true);
            ui.btnStart.setText("Start");
        }
        if (ui.btnStep != null) ui.btnStep.setDisable(true);

        updateExportAvailability(ui);
    }

    static void setConnectedUI(UI ui) {
        if (ui.lblStatus != null) ui.lblStatus.setText("Status: Connected");
        if (ui.btnConnect != null) {
            ui.btnConnect.setDisable(false);
            ui.btnConnect.setText("Disconnect");
        }
        if (ui.btnOpenConfig != null) ui.btnOpenConfig.setDisable(true);
        if (ui.btnBrowseConfig != null) ui.btnBrowseConfig.setDisable(true);
        if (ui.txtConfigPath != null) ui.txtConfigPath.setDisable(true);
        if (ui.txtStepMs != null) ui.txtStepMs.setDisable(true);
        if (ui.btnStart != null) {
            ui.btnStart.setDisable(false);
            ui.btnStart.setText("Start");
        }
        if (ui.btnStep != null) ui.btnStep.setDisable(false);

        updateExportAvailability(ui);
    }

    static void setRunningUI(UI ui) {
        if (ui.lblStatus != null) ui.lblStatus.setText("Status: Running");
        if (ui.btnConnect != null) {
            ui.btnConnect.setDisable(false);
            ui.btnConnect.setText("Disconnect");
        }
        if (ui.btnStart != null) {
            ui.btnStart.setDisable(false);
            ui.btnStart.setText("Pause");
        }
        if (ui.btnStep != null) ui.btnStep.setDisable(true);

        updateExportAvailability(ui);
    }

    static boolean canExportNow(UI ui) {
        return ui.connector != null && ui.connector.isConnected() && !ui.running;
    }

    static void updateExportAvailability(UI ui) {
        if (ui.btnExport == null) return;
        // Export should be enabled ONLY when SUMO is connected and currently paused.
        ui.btnExport.setDisable(!canExportNow(ui));
    }

    static void disconnectFromSumo(UI ui) {
        ui.stopLoop();
        ui.stopConnectionMonitor();
        if (ui.connector != null) {
            ui.connector.disconnect();
        }
        ui.connector = null;
        ui.vehicleWrapper = null;
        if (ui.cmbTrafficLight != null) {
            ui.cmbTrafficLight.getItems().clear();
        }
        if (ui.lblPhaseInfo != null) {
            ui.lblPhaseInfo.setText("Phase: -");
        }
        if (ui.txtPhaseDuration != null) {
            ui.txtPhaseDuration.setText("");
        }
        setDisconnectedUI(ui);
    }

    static void setStatusText(UI ui, String text) {
        if (ui.lblStatus != null) ui.lblStatus.setText(text);
    }
}
