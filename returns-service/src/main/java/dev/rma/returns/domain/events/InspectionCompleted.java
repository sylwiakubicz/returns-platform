package dev.rma.returns.domain.events;

import dev.rma.returns.domain.InspectionResult;

import java.time.Instant;
import java.util.List;

public record InspectionCompleted(
    String inspectorId,
    Instant completedAt,
    List<InspectionResult> results,
    String approvedBy
) implements DomainEvent {
    public InspectionCompleted {
        results = List.copyOf(results);
    }
}
