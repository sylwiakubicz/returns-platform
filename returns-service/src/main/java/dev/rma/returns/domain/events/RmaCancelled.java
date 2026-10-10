package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;

public record RmaCancelled(String cancelledBy, Instant cancelledAt, String reason) implements DomainEvent {
    public RmaCancelled {
        Objects.requireNonNull(cancelledBy, "cancelledBy must not be null");
        Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");
    }
}
