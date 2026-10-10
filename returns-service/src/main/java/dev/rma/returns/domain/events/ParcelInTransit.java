package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ParcelInTransit(UUID rmaId, Instant collectedAt) implements DomainEvent {
    public ParcelInTransit {
        Objects.requireNonNull(rmaId, "rmaId must not be null");
        Objects.requireNonNull(collectedAt, "collectedAt must not be null");
    }

    @Override
    public Instant occurredAt() {
        return collectedAt;
    }
}
