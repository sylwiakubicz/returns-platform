package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;

public record ParcelReceived(
    Instant receivedAt,
    String warehouseId
) implements DomainEvent{
    public ParcelReceived {
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(warehouseId, "warehouseId must not be null");
    }
}
