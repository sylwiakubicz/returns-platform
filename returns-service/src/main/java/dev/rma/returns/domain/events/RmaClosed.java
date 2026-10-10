package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.rma.returns.domain.enums.ClosureReason;

public record RmaClosed(UUID rmaId, ClosureReason closureReason, Instant closedAt) implements DomainEvent {
    public RmaClosed {
        Objects.requireNonNull(rmaId, "rmaId must not be null");
        Objects.requireNonNull(closureReason, "closureReason must not be null");
        Objects.requireNonNull(closedAt, "closedAt must not be null");
    }

    @Override
    public Instant occurredAt() {
        return closedAt;
    }
}
