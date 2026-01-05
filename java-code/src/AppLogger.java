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
            root.setLevel(Level.INFO);

            // Keep console logging (Gradle/terminal) but make sure it is not overly verbose.
            for (Handler h : root.getHandlers()) {
                h.setLevel(Level.INFO);
            }

            try {
                Path logsDir = Paths.get("logs");
                Files.createDirectories(logsDir);

                // Rotate logs: up to 5 files, 1 MB each.
                FileHandler fh = new FileHandler(logsDir.resolve("app.log").toString(), 1_000_000, 5, true);
                fh.setLevel(Level.INFO);
                fh.setFormatter(new SimpleFormatter());
                root.addHandler(fh);
            } catch (IOException e) {
                // If file logging fails, keep console logging.
                root.log(Level.WARNING, "Failed to configure file logging", e);
            }

            initialized = true;
        }
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
