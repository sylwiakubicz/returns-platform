package dev.rma.returns.application.exception;

/** Poprawne dane JSON, ale polecenie nie zgadza się z danymi zwrotu. */
public class InvalidSimulationCommandException extends RuntimeException {
    public InvalidSimulationCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
