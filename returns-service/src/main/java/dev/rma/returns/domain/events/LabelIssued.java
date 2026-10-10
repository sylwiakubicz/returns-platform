package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LabelIssued(UUID rmaId, String labelId, String trackingNumber, String labelFileKey, Instant issuedAt)
        implements DomainEvent {
    public LabelIssued {
        Objects.requireNonNull(rmaId, "rmaId must not be null");
        Objects.requireNonNull(labelId, "labelId must not be null");
        Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
        Objects.requireNonNull(labelFileKey, "labelFileKey must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    }

    @Override
    public Instant occurredAt() {
        return issuedAt;
    }
}
