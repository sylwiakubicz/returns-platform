package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ParcelReceived(UUID rmaId, Instant receivedAt, String warehouseId) implements DomainEvent {
    public ParcelReceived {
        Objects.requireNonNull(rmaId, "rmaId must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(warehouseId, "warehouseId must not be null");
    }

    @Override
    public Instant occurredAt() {
        return receivedAt;
    }
}
