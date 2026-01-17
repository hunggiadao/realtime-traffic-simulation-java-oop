import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Central logging setup for the application.
 */
public final class AppLogger {
    private static volatile boolean initialized;
    private static volatile Level currentLevel = Level.INFO;

    private AppLogger() {
    }

    /**
     * Initializes global logging configuration.
     * Safe to call multiple times.
     */
    public static void init() {
        if (initialized) return;
        synchronized (AppLogger.class) {
            if (initialized) return;

            Logger root = Logger.getLogger("");
            Level level = resolveDesiredLevel();
            currentLevel = level;
            root.setLevel(level);

            // Keep console logging (Gradle/terminal) but make sure it is not overly verbose.
            for (Handler h : root.getHandlers()) {
                h.setLevel(level);
            }

            try {
                Path logsDir = defaultLogsDir();
                Files.createDirectories(logsDir);

                // Rotate logs: up to 5 files, 1 MB each.
                FileHandler fh = new FileHandler(logsDir.resolve("app.log").toString(), 1_000_000, 5, true);
                fh.setLevel(level);
                fh.setFormatter(new SimpleFormatter());
                root.addHandler(fh);
            } catch (IOException e) {
                // If file logging fails, keep console logging.
                root.log(Level.WARNING, "Failed to configure file logging", e);
            }

            initialized = true;
        }
    }

    /**
     * Updates the global logging level at runtime.
     * Useful for enabling debug logs (e.g., Level.FINE) when investigating issues.
     */
    public static void setLevel(Level level) {
        if (level == null) return;
        init();

        Logger root = Logger.getLogger("");
        root.setLevel(level);
        for (Handler h : root.getHandlers()) {
            h.setLevel(level);
        }
        currentLevel = level;
    }

    public static Level getLevel() {
        init();
        return currentLevel;
    }

    private static Level resolveDesiredLevel() {
        // Allow overrides without touching code:
        // -Dapp.logLevel=FINE (or INFO/WARNING/SEVERE/etc)
        String raw = System.getProperty("app.logLevel");
        if (raw == null || raw.trim().isEmpty()) {
            return Level.INFO;
        }
        try {
            return Level.parse(raw.trim().toUpperCase());
        } catch (Exception e) {
            return Level.INFO;
        }
    }

    private static Path defaultLogsDir() {
        // Keep runtime-generated files out of src/.
        // Always write logs under bin/logs.
        return Paths.get("bin", "logs");
    }

    private static final class SimpleFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            String msg = record.getMessage();
            if (msg == null) msg = "";
            return String.format(
                    "%1$tF %1$tT [%2$-7s] %3$s - %4$s%n",
                    record.getMillis(),
                    record.getLevel().getName(),
                    record.getLoggerName(),
                    msg
            );
        }
    }
}
