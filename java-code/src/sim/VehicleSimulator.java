import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VehicleSimulator
 * ----------------
 * Single-threaded SUMO vehicle simulator.
 * All interactions with SUMO (TraCI) are executed on a single-threaded executor
 * to avoid concurrent access issues.
 *
 * Features:
 * - Restartable: start() can recreate the executor after stop().
 * - Lightweight: designed to handle thousands of vehicles efficiently.
 * - Thread-safe start/stop using AtomicBoolean.
 *
 * Responsibilities:
 * - Step simulation periodically.
 * - Refresh vehicle list and update state snapshots via VehicleManager.
 * - Apply vehicle configuration via VehicleWrapper.
 */
public final class VehicleSimulator {

    private static final long DEFAULT_PERIOD_MS = 100;       // default step period in milliseconds
    private static final long SHUTDOWN_TIMEOUT_MS = 500;     // max wait for executor shutdown

    private final TraCIConnector traci;      // connection to SUMO via TraCI
    private final VehicleManager manager;    // manages vehicle wrappers and state snapshots
    private final VehicleWrapper vehicles;   // vehicle control + queries
    private final long periodMs;             // loop period in milliseconds

    private final AtomicBoolean running = new AtomicBoolean(false); // indicates if simulator is running

    // Guarded by synchronized start/stop
    private ScheduledExecutorService executor; // single-threaded executor for simulation loop
    private ScheduledFuture<?> loop;           // scheduled future for periodic loop

    /**
     * Constructor with default period.
     */
    public VehicleSimulator(TraCIConnector traci,
                            VehicleManager manager,
                            VehicleWrapper vehicles) {
        this(traci, manager, vehicles, DEFAULT_PERIOD_MS);
    }

    /**
     * Constructor with custom loop period.
     */
    public VehicleSimulator(TraCIConnector traci,
                            VehicleManager manager,
                            VehicleWrapper vehicles,
                            long periodMs) {
        this.traci = traci;
        this.manager = manager;
        this.vehicles = vehicles;
        this.periodMs = periodMs;
    }

    /**
     * Starts the simulator loop.
     * - Creates a new executor if needed.
     * - Schedules periodic execution of the simulation step.
     * Thread-safe: synchronized and uses AtomicBoolean.
     */
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;

        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = createSingleThreadExecutor();
        }

        loop = executor.scheduleAtFixedRate(this::safeStepOnce, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the simulator loop.
     * Cancels the scheduled task and shuts down the executor.
     */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) return;

        if (loop != null) {
            loop.cancel(true);
            loop = null;
        }

        shutdownExecutor();
    }

    /**
     * Alias for stop(); kept for API clarity.
     */
    public synchronized void shutdown() {
        stop();
    }

    /**
     * Returns whether the simulator is currently running.
     */
    public boolean isRunning() {
        return running.get();
    }

    // ---------------- internal ----------------

    /**
     * Executes one simulation step safely.
     * Catches all exceptions to prevent executor termination.
     */
    private void safeStepOnce() {
        try {
            stepOnce();
        } catch (Throwable t) {
            System.err.println("VehicleSimulator loop error: " + t);
            t.printStackTrace();
        }
    }

    /**
     * Performs one simulation step:
     * - Checks TraCI connection
     * - Advances the SUMO simulation
     * - Refreshes vehicle list and state snapshots
     * - Applies configuration to each vehicle
     */
    private void stepOnce() {
        if (traci == null || !traci.isConnected()) return;

        if (!traci.step()) {
            System.err.println("VehicleSimulator: traci.step() failed");
            return;
        }

        // Update vehicles and snapshots
        manager.refreshVehicles();
        manager.updateAllStates();

        // Apply vehicle configurations in batch
        Map<String, VehicleState> states = manager.getAllStates();
        for (VehicleState s : states.values()) {
            vehicles.configureVehicle(
                    s.id,
                    s.speed,
                    0.7,         // speed ratio applied to max speed
                    255, 0, 0, 255 // default RGBA color
            );
        }
    }

    /**
     * Creates a single-threaded executor for simulation loop.
     * - Daemon thread to not block JVM shutdown
     * - Custom uncaught exception handler for logging
     */
    private ScheduledExecutorService createSingleThreadExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vehicle-simulator-loop");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((th, ex) -> {
                System.err.println("Uncaught in simulator thread: " + ex);
                ex.printStackTrace();
            });
            return t;
        });
    }

    /**
     * Gracefully shuts down the executor.
     * Waits up to SHUTDOWN_TIMEOUT_MS, then forces shutdown if necessary.
     */
    private void shutdownExecutor() {
        if (executor == null) return;

        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        } finally {
            executor = null;
        }
    }
}
