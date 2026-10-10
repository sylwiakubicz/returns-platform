package dev.rma.returns.domain.events;

import java.time.Instant;
import java.util.Objects;

import dev.rma.returns.domain.Money;

public record RefundExecuted(String payoutId, Money amount, int attempt, Instant executedAt) implements DomainEvent {
    public RefundExecuted {
        Objects.requireNonNull(payoutId, "payoutId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(executedAt, "executedAt must not be null");

        if (payoutId.isBlank()) {
            throw new IllegalArgumentException("payoutId must not be blank");
        }

        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be greater than or equal to 1");
        }
    }
}
