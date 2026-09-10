package dev.rma.returns.domain.events;

import dev.rma.returns.domain.InspectionResult;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record InspectionCompleted(
    String inspectorId,
    Instant completedAt,
    List<InspectionResult> results,
    String approvedBy
) implements DomainEvent {
    public InspectionCompleted {
        Objects.requireNonNull(inspectorId, "inspectorId must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        results = List.copyOf(results);
    }
}
