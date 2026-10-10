package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RmaExpired(UUID rmaId, Instant expiredAt, int daysSinceRequest) implements DomainEvent {
    public RmaExpired {
        Objects.requireNonNull(rmaId, "rmaId must not be null");
        Objects.requireNonNull(expiredAt, "expiredAt must not be null");
        if (daysSinceRequest < 0) {
            throw new IllegalArgumentException("daysSinceRequest must be greater or equal to 0");
        }
    }

    @Override
    public Instant occurredAt() {
        return expiredAt;
    }
}
