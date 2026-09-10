package dev.rma.returns.domain.events;

import java.time.Instant;

public record ParcelInTransit(
    Instant collectedAt
) implements DomainEvent{
}
