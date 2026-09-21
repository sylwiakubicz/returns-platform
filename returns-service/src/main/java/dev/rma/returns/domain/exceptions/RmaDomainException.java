package dev.rma.returns.domain.exceptions;

import dev.rma.returns.domain.enums.RmaErrorCode;

import java.util.Objects;

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
