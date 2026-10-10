package dev.rma.returns.domain.events;

import java.time.LocalDate;
import java.util.Objects;

public record PickupScheduled(String pickupId, LocalDate scheduledFor) implements DomainEvent {
    public PickupScheduled {
        Objects.requireNonNull(pickupId, "pickupId must not be null");
        Objects.requireNonNull(scheduledFor, "scheduledFor must not be null");
    }
}
