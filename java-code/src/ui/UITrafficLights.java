import de.tudresden.sumo.objects.SumoLink;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

final class UITrafficLights {
    private UITrafficLights() {
    }

    static Map<String, Color> buildLaneSignalColorMap(UI ui) {
        if (ui.connector == null || !ui.connector.isConnected() || ui.connector.getConnection() == null) {
            return Collections.emptyMap();
        }
        try {
            List<String> ids = new ArrayList<>();
            ids = ui.trafWrapper.getTrafficLightIds();
            if (ids.isEmpty()) return Collections.emptyMap();

            Map<String, Integer> lanePriority = new HashMap<>();
            Map<String, Color> laneColor = new HashMap<>();
            for (String tlId : ids) {
                // draw the TL marker in the correct spot with the correct color
                if (tlId == null || tlId.isEmpty()) continue;
                String state = ui.trafWrapper.getTrafficLightState(tlId);
                if (state == null || state.isEmpty()) continue;

                List<SumoLink> links = ui.trafficLightLinksCache.get(tlId);
                if (links == null) {
                    links = ui.trafWrapper.getTrafficLightLinks(tlId);
                    ui.trafficLightLinksCache.put(tlId, links);
                }
                if (links == null || links.isEmpty()) continue;

                int limit = Math.min(state.length(), links.size());
                for (int i = 0; i < limit; i++) {
                    SumoLink link = links.get(i);
                    if (link == null) continue;
                    String laneId = (link.notInternalLane != null && !link.notInternalLane.isEmpty())
                            ? link.notInternalLane
                            : link.from;
                    if (laneId == null || laneId.isEmpty()) continue;

                    char ch = state.charAt(i);
                    int prio = signalPriority(ch);
                    Integer existing = lanePriority.get(laneId);
                    if (existing == null || prio > existing) {
                        lanePriority.put(laneId, prio);
                        laneColor.put(laneId, signalColorForState(ch));
                    }
                }
            }
            return laneColor;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    // 3 is the highest, 0 is the lowest
    static int signalPriority(char state) {
        switch (Character.toLowerCase(state)) {
            case 'r':
                return 3;
            case 'y':
                return 2;
            case 'g':
                return 1;
            default:
                return 0;
        }
    }

    static Color signalColorForState(char state) {
        switch (Character.toLowerCase(state)) {
            case 'g':
                return Color.LIMEGREEN;
            case 'y':
                return Color.GOLD;
            case 'r':
                return Color.RED;
            default:
                return Color.GRAY;
        }
    }

    static void populateTrafficLights(UI ui) {
        if (ui.connector == null || !ui.connector.isConnected() || ui.cmbTrafficLight == null) return;
        try {
            ui.trafficLightPhaseCountCache.clear();
            List<String> ids = ui.trafWrapper.getTrafficLightIds();
            ui.cmbTrafficLight.getItems().setAll(ids);
            if (!ids.isEmpty()) {
                ui.cmbTrafficLight.getSelectionModel().select(0);
                updateTrafficLightUI(ui);
            }
        } catch (Exception e) {
            ui.LOGGER.warning("Failed to populate traffic lights");
        }
    }

    static int getTLPhaseCount(UI ui, String id) {
        if (id == null || id.isEmpty() || ui.connector == null || !ui.connector.isConnected()) return -1;

        // if phase count already exists, just retrieve it
        Integer cached = ui.trafficLightPhaseCountCache.get(id);
        if (cached != null) return cached;

        // else use trafWrapper, fallback method
        int count = ui.trafWrapper.getTrafficLightPhaseCount(id);
        ui.trafficLightPhaseCountCache.put(id, count);
        return count;
    }

    static void updateTrafficLightUI(UI ui) {
        if (ui.connector == null || !ui.connector.isConnected() || ui.cmbTrafficLight == null) return;
        String tlid = ui.cmbTrafficLight.getValue();
        if (tlid == null || tlid.isEmpty()) return;
        try {
            String state = ui.trafWrapper.getTrafficLightState(tlid); // RGB state of the TL
            int phaseIndex = ui.trafWrapper.getPhaseIndex(tlid); // phase index in the phase cycle
            double dur = ui.trafWrapper.getPhaseDuration(tlid); // in seconds

            if (ui.lblPhaseInfo != null) {
                ui.lblPhaseInfo.setText("Phase " + phaseIndex + ": " + state);
            }
            if (ui.txtPhaseDuration != null) {
                ui.txtPhaseDuration.setText(String.format(Locale.US, "%.1f", dur));
            }
        } catch (Exception e) {
            ui.LOGGER.fine("Failed to update traffic light UI");
        }
    }

    static void changeTrafficLightPhase(UI ui, int delta) {
        if (ui.connector == null || !ui.connector.isConnected() || ui.cmbTrafficLight == null) return;
        String id = ui.cmbTrafficLight.getValue();
        if (id == null || id.isEmpty()) return;
        try {
            int curPhase = ui.trafWrapper.getPhaseIndex(id);
            int phaseCount = getTLPhaseCount(ui, id);
            ui.LOGGER.info("Phase count for TL " + id + ": " + phaseCount);

            int newPhase;
            if (phaseCount > 0) {
                int raw = curPhase + delta;
                newPhase = (raw + phaseCount) % phaseCount; // safe wrap for out-of-bound index
            } else {
                newPhase = Math.max(0, curPhase + delta);
            }

            ui.trafWrapper.setPhaseIndex(id, newPhase);
            ui.LOGGER.info("Changing to phase " + newPhase);

            updateTrafficLightUI(ui);
        } catch (Exception e) {
            // Don't spam stack traces for user-driven UI actions.
            ui.setStatusText("Traffic light phase change failed");
        }
    }

    static void applyTrafficLightDuration(UI ui) {
        if (ui.connector == null || !ui.connector.isConnected() || ui.cmbTrafficLight == null || ui.txtPhaseDuration == null) return;
        String id = ui.cmbTrafficLight.getValue();
        if (id == null || id.isEmpty()) return;
        try {
            double dur = Double.parseDouble(ui.txtPhaseDuration.getText().trim());
            ui.trafWrapper.setRemainingPhaseDuration(id, dur);
            updateTrafficLightUI(ui);
        } catch (NumberFormatException ignored) {
            // ignore invalid input
        } catch (Exception e) {
            ui.LOGGER.warning("Failed to apply traffic light duration");
        }
    }
}
