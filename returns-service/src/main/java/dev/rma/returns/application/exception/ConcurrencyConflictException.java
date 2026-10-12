package dev.rma.returns.application.exception;

public class ConcurrencyConflictException extends RuntimeException {
    public ConcurrencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
