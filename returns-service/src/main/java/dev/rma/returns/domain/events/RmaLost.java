package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;

public record RmaLost(String lastKnownStatus, Instant declaredLostAt) implements DomainEvent {
    public RmaLost {
        Objects.requireNonNull(lastKnownStatus, "lastKnownStatus must not be null");
        Objects.requireNonNull(declaredLostAt, "declaredLostAt must not be null");
    }
}
