import javafx.animation.AnimationTimer;

final class UILoop {
    private UILoop() {
    }

    static void startLoop(UI ui) {
        if (ui.loopTimer == null) {
            ui.loopTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (!ui.running) return;
                    if (ui.lastStepNs == 0) {
                        ui.lastStepNs = now;
                        return;
                    }
                    double speedFactor = (ui.sliderSpeed != null) ? ui.sliderSpeed.getValue() : 1.0;
                    double stepIntervalNs = (ui.stepLengthSeconds / Math.max(ui.sliderSpeed.getMin(), speedFactor)) * 1_000_000_000.0;
                    if ((now - ui.lastStepNs) >= stepIntervalNs) {
                        doStep(ui);
                        ui.lastStepNs = now;
                    }
                }
            };
        }
        ui.running = true;
        ui.lastStepNs = 0;
        ui.loopTimer.start();
    }

    static void stopLoop(UI ui) {
        ui.running = false;
        if (ui.loopTimer != null) {
            ui.loopTimer.stop();
        }
    }

    static void doStep(UI ui) {
        if (ui.connector == null || !ui.connector.isConnected()) {
            stopLoop(ui);
            ui.setStatusText("Status: Not connected");
            ui.setDisconnectedUI();
            return;
        }
        boolean ok = ui.connector.step();
        if (!ok) {
            stopLoop(ui);
            ui.setStatusText("Status: Disconnected");
            ui.setDisconnectedUI();
            return;
        }
        ui.setStatusText("Status: Running");
        ui.updateAfterStep();

        // Also keep traffic light info in sync
        ui.updateTrafficLightUI();
    }
}
