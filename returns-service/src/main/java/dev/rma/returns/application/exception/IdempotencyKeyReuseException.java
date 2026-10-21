package dev.rma.returns.application.exception;

public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException() {
        super("Idempotency-Key has already been used with a different request body");
    }
}
