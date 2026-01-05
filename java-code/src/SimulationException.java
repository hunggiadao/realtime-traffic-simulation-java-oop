/**
 * Application-specific exception type for simulation/runtime failures.
 *
 * <p>This is used to satisfy the project requirement of having at least one
 * custom exception type and to centralize error reporting.
 */
public class SimulationException extends Exception {
    /**
     * Creates a new exception with message.
     *
     * @param message human-readable error
     */
    public SimulationException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with message and cause.
     *
     * @param message human-readable error
     * @param cause root cause
     */
    public SimulationException(String message, Throwable cause) {
        super(message, cause);
    }
}
