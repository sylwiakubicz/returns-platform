package dev.rma.returns.domain.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PickupScheduled(UUID rmaId, String pickupId, LocalDate scheduledFor, Instant occurredAt)
        implements DomainEvent {
    public PickupScheduled {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(rmaId, "rmaId must not be null");
        Objects.requireNonNull(pickupId, "pickupId must not be null");
        Objects.requireNonNull(scheduledFor, "scheduledFor must not be null");
    }
}
