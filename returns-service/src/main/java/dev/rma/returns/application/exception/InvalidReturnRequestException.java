package dev.rma.returns.application.exception;

public class InvalidReturnRequestException extends RuntimeException {
    public InvalidReturnRequestException(Throwable cause) {
        super("Request body must be a JSON return creation request", cause);
    }
}
