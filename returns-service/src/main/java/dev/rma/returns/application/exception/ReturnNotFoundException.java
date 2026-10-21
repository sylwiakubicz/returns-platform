package dev.rma.returns.application.exception;

public class ReturnNotFoundException extends RuntimeException {
    public ReturnNotFoundException(String businessNumber) {
        super("Return not found: " + businessNumber);
    }
}
