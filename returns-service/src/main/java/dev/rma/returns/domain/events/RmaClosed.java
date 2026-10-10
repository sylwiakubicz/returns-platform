package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;

import dev.rma.returns.domain.enums.ClosureReason;

public record RmaClosed(ClosureReason closureReason, Instant closedAt) implements DomainEvent {
    public RmaClosed {
        Objects.requireNonNull(closureReason, "closureReason must not be null");
        Objects.requireNonNull(closedAt, "closedAt must not be null");
    }
}
