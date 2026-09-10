package dev.rma.returns.domain.events;

import java.time.Instant;

public record ParcelReceived(
    Instant receivedAt,
    String warehouseId
) implements DomainEvent{
}
