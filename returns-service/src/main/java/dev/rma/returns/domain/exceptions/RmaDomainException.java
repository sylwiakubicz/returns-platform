package dev.rma.returns.domain.exceptions;

import java.util.Objects;

import dev.rma.returns.domain.enums.RmaErrorCode;

public class RmaDomainException extends RuntimeException {
    private final RmaErrorCode code;

    public RmaDomainException(RmaErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public RmaErrorCode code() {
        return code;
    }
}
