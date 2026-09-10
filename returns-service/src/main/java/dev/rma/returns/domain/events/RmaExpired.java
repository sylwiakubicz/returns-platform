package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;

public record RmaExpired(
    Instant expiredAt,
    int daysSinceRequest
) implements DomainEvent {
    public RmaExpired {
        Objects.requireNonNull(expiredAt,"expiredAt must not be null");
        if (daysSinceRequest < 0) {
            throw new IllegalArgumentException("daysSinceRequest must be greater or equal to 0");
        }
    }
}
