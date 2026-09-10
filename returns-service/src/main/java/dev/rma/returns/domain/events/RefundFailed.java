package dev.rma.returns.domain.events;

import dev.rma.returns.domain.enums.FailureCode;

import java.time.Instant;

public record RefundFailed(
    FailureCode failureCode,
    String failureMessage,
    Integer attempt,
    Boolean retryable,
    Instant failedAt
) implements DomainEvent{
}
