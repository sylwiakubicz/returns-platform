package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RmaCancelled(UUID rmaId, String cancelledBy, Instant cancelledAt, String reason) implements DomainEvent {
    public RmaCancelled {
        Objects.requireNonNull(rmaId, "rmaId must not be null");
        Objects.requireNonNull(cancelledBy, "cancelledBy must not be null");
        Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");
    }

    @Override
    public Instant occurredAt() {
        return cancelledAt;
    }
}
