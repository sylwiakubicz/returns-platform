package dev.rma.returns.domain.events;

import dev.rma.returns.domain.Money;

import java.time.Instant;

public record RefundExecuted(
    String payoutId,
    Money amount,
    Integer attempt,
    Instant executedAt
) implements DomainEvent {
}
