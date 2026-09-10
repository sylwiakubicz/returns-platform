package dev.rma.returns.domain.events;

import dev.rma.returns.domain.enums.FailureCode;

import java.time.Instant;
import java.util.Objects;

public record RefundFailed(
    FailureCode failureCode,
    String failureMessage,
    int attempt,
    boolean retryable,
    Instant failedAt
) implements DomainEvent{
    public RefundFailed {
        Objects.requireNonNull(failureCode, "failureCode must not be null");
        Objects.requireNonNull(failureMessage, "failureMessage must not be null");
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
    }
}
