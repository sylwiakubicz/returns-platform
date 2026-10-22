package dev.rma.returns.application.exception;

public class InvalidReturnRequestException extends RuntimeException {
    private final String field;

    public InvalidReturnRequestException(Throwable cause) {
        super("Request body must be a JSON return creation request", cause);
        this.field = "$";
    }

    public InvalidReturnRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
