package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InspectionStarted(UUID rmaId, String inspectorId, Instant startedAt) implements DomainEvent {
    public InspectionStarted {
        Objects.requireNonNull(rmaId, "rmaId must not be null");
        Objects.requireNonNull(inspectorId, "inspectorId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
    }

    @Override
    public Instant occurredAt() {
        return startedAt;
    }
}
