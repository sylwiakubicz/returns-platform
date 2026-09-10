package dev.rma.returns.domain.events;

import dev.rma.returns.domain.ClosureReason;

import java.time.Instant;

public record RmaClosed(
    ClosureReason closureReason,
    Instant closedAt
) implements DomainEvent {
}
