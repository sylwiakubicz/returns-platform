package dev.rma.returns.domain.events;

import java.time.Instant;

public record RmaExpired(
    Instant expiredAt,
    Integer daysSinceRequest
) implements DomainEvent {
}
