package dev.rma.returns.domain.events;

import java.time.Instant;

public record LabelIssued(
    String labelId,
    String trackingNumber,
    String labelFileKey,
    Instant issuedAt
) implements DomainEvent{
}
