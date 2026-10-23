package dev.rma.returns.application.exception;

public class InvalidReturnRequestException extends RuntimeException {
    private final String field;

    public InvalidReturnRequestException(Throwable cause) {
        super("Request body must be a valid JSON object matching the endpoint schema", cause);
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
