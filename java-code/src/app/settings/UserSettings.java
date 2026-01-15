import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Small helper for loading/saving user settings.
 *
 * This provides explicit stream + file access beyond the SUMO configuration file parsing, as required by the project specification.
 */
public final class UserSettings {
    private static final Logger LOGGER = Logger.getLogger(UserSettings.class.getName());

    private final Properties props = new Properties();
    private final Path settingsPath;

    /**
     * Creates a new settings instance stored under {@code settings/user.properties}.
     */
    public UserSettings() {
        this(defaultSettingsPath());
    }

    /**
     * Creates a new settings instance stored at the given path.
     *
     * @param settingsPath settings file location
     */
    public UserSettings(Path settingsPath) {
        this.settingsPath = settingsPath;
    }

    private static Path defaultSettingsPath() {
        // Keep runtime-generated files out of src/.
        // Always write settings under bin/settings.
        return Paths.get("bin", "settings", "user.properties");
    }

    /**
     * Loads settings from disk if the file exists.
     */
    public void load() {
        try {
            if (Files.exists(settingsPath)) {
                try (InputStream in = Files.newInputStream(settingsPath)) {
                    props.load(in);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load settings from " + settingsPath, e);
        }
    }

    /**
     * Saves settings to disk, creating parent directories if needed.
     */
    public void save() {
        try {
            Path parent = settingsPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream out = Files.newOutputStream(settingsPath)) {
                props.store(out, "Real-time traffic simulation settings");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save settings to " + settingsPath, e);
        }
    }

    /**
     * Reads a string value.
     *
     * @param key property key
     * @param defaultValue default when missing
     * @return stored value or default
     */
    public String getString(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    /**
     * Writes a string value.
     *
     * @param key property key
     * @param value value
     */
    public void putString(String key, String value) {
        if (value == null) return;
        props.setProperty(key, value);
    }
}
