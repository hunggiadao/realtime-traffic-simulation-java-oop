import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

final class UISumoFiles {
    private UISumoFiles() {
    }

    static String resolveSumoBinary() {
        // Prefer SUMO_HOME env, otherwise fall back to common default
        String sumoHome = System.getenv("SUMO_HOME");
        if (sumoHome != null && !sumoHome.isEmpty()) {
            return sumoHome.replaceAll("[/\\\\]+$", "") + "\\\\bin\\\\sumo.exe";
        }
        return "C:\\\\Program Files (x86)\\\\Eclipse\\\\Sumo\\\\bin\\\\sumo.exe";
    }

    static File resolveConfigFile(String path) {
        if (path == null || path.isEmpty()) return null;
        File f = Paths.get(path).toAbsolutePath().normalize().toFile();

        if (f.exists()) return f;
        // try also if path was relative to project parent (java-code/..)
        f = Paths.get("..").resolve(path).toAbsolutePath().normalize().toFile();
        if (f.exists()) return f;
        return null;
    }

    static File resolveNetFile(UI ui, String configPath) {
        if (configPath == null || configPath.isEmpty()) return null;
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(configPath));
            doc.getDocumentElement().normalize();
            NodeList netNodes = doc.getElementsByTagName("net-file");
            if (netNodes.getLength() > 0) {
                Element el = (Element) netNodes.item(0);
                String value = el.getAttribute("value");
                Path cfg = Paths.get(configPath).toAbsolutePath().normalize();
                Path base = cfg.getParent();
                File candidate = base.resolve(value).toFile();
                if (candidate.exists()) return candidate;
                // also try relative to CWD in case cfg path was relative without parent
                File alt = Paths.get(value).toFile();
                if (alt.exists()) return alt;
            }
        } catch (Exception e) {
            if (ui != null) ui.LOGGER.log(Level.FINE, "Failed to resolve net-file from config", e);
        }
        return null;
    }

    static List<File> resolveAdditionalFiles(UI ui, String configPath) {
        List<File> out = new ArrayList<>();
        if (configPath == null || configPath.isEmpty()) return out;
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(configPath));
            doc.getDocumentElement().normalize();
            NodeList nodes = doc.getElementsByTagName("additional-files");
            if (nodes.getLength() <= 0) return out;

            Element el = (Element) nodes.item(0);
            String value = el.getAttribute("value");
            if (value == null || value.trim().isEmpty()) return out;

            Path cfg = Paths.get(configPath).toAbsolutePath().normalize();
            Path base = cfg.getParent();
            String[] parts = value.split(",");
            for (String p : parts) {
                if (p == null) continue;
                String trimmed = p.trim();
                if (trimmed.isEmpty()) continue;
                File candidate = (base != null) ? base.resolve(trimmed).toFile() : new File(trimmed);
                if (candidate.exists()) {
                    out.add(candidate);
                }
            }
        } catch (Exception e) {
            if (ui != null) ui.LOGGER.log(Level.FINE, "Failed to resolve additional-files from config", e);
        }
        return out;
    }

    static List<String[]> parseBusStopsFromAdditionalFiles(UI ui, List<File> additionalFiles) {
        List<String[]> out = new ArrayList<>();
        if (additionalFiles == null || additionalFiles.isEmpty()) return out;
        for (File f : additionalFiles) {
            if (f == null || !f.exists()) continue;
            try {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
                doc.getDocumentElement().normalize();
                NodeList stops = doc.getElementsByTagName("busStop");
                for (int i = 0; i < stops.getLength(); i++) {
                    Element s = (Element) stops.item(i);
                    String id = s.getAttribute("id");
                    String laneId = s.getAttribute("lane");
                    if (laneId == null || laneId.isEmpty()) continue;
                    String name = s.hasAttribute("name") ? s.getAttribute("name") : "";

                    double startPos = parseDoubleOrDefault(s.getAttribute("startPos"), 0.0);
                    double endPos = parseDoubleOrDefault(s.getAttribute("endPos"), startPos + 10.0);
                    if (endPos < startPos) {
                        double tmp = startPos;
                        startPos = endPos;
                        endPos = tmp;
                    }

                    String label = (name != null && !name.isEmpty()) ? name : ((id != null && !id.isEmpty()) ? id : "busStop");
                    String stopId = (id != null && !id.isEmpty()) ? id : label;

                    out.add(new String[]{
                            stopId,
                            label,
                            laneId,
                            String.valueOf(startPos),
                            String.valueOf(endPos)
                    });
                }
            } catch (Exception e) {
                if (ui != null) ui.LOGGER.log(Level.FINE, "Failed to parse bus stops from " + f.getName(), e);
            }
        }
        return out;
    }

    static double parseDoubleOrDefault(String s, double def) {
        if (s == null) return def;
        try {
            String t = s.trim();
            if (t.isEmpty()) return def;
            return Double.parseDouble(t);
        } catch (Exception ignored) {
            return def;
        }
    }
}
