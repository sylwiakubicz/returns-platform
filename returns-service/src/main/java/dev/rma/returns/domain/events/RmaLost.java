package dev.rma.returns.domain.events;

import java.time.Instant;

public record RmaLost(
    String lastKnownStatus,
    Instant declaredLostAt
) implements DomainEvent {
}
