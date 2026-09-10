package dev.rma.returns.domain.events;

import java.time.LocalDate;

public record PickupScheduled(
    String pickupId,
    LocalDate scheduledFor
) implements DomainEvent {
}
