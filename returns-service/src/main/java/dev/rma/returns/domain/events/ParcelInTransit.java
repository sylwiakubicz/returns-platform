package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;

public record ParcelInTransit(Instant collectedAt) implements DomainEvent {
    public ParcelInTransit {
        Objects.requireNonNull(collectedAt, "collectedAt must not be null");
    }
}
