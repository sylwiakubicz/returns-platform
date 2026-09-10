package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;

public record InspectionStarted(
    String inspectorId,
    Instant startedAt
) implements DomainEvent {
    public InspectionStarted {
        Objects.requireNonNull(inspectorId, "inspectorId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
    }
}
