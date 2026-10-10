package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RmaLost(UUID rmaId, String lastKnownStatus, Instant declaredLostAt) implements DomainEvent {
    public RmaLost {
        Objects.requireNonNull(rmaId, "rmaId must not be null");
        Objects.requireNonNull(lastKnownStatus, "lastKnownStatus must not be null");
        Objects.requireNonNull(declaredLostAt, "declaredLostAt must not be null");
    }

    @Override
    public Instant occurredAt() {
        return declaredLostAt;
    }
}
