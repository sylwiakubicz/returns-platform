package dev.rma.returns.domain.events;

import java.time.Instant;

public record InspectionStarted(
    String inspectorId,
    Instant startedAt
) implements DomainEvent {
}
