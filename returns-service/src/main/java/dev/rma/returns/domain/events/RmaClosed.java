package dev.rma.returns.domain.events;

import dev.rma.returns.domain.enums.ClosureReason;

import java.time.Instant;
import java.util.Objects;

public record RmaClosed(
    ClosureReason closureReason,
    Instant closedAt
) implements DomainEvent {
    public RmaClosed {
        Objects.requireNonNull(closureReason, "closureReason must not be null");
        Objects.requireNonNull(closedAt, "closedAt must not be null");
    }
}
