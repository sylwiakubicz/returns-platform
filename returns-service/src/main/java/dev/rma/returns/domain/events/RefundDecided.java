package dev.rma.returns.domain.events;

import dev.rma.returns.domain.ItemRefundDecision;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.enums.RefundDecision;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RefundDecided(
    RefundDecision decision,
    Money refundAmount,
    Money deductionTotal,
    List<ItemRefundDecision> itemDecisions,
    String rejectionReason,
    Instant decidedAt
) implements DomainEvent {
    public RefundDecided {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(refundAmount, "refundAmount must not be null");
        Objects.requireNonNull(deductionTotal, "deductionTotal must not be null");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        Objects.requireNonNull(itemDecisions, "itemDecisions must not be null");

        if (decision == RefundDecision.REJECTED
            && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new IllegalArgumentException(
                "rejectionReason must be provided for rejected decision"
            );
        }

        if (decision != RefundDecision.REJECTED && rejectionReason != null) {
            throw new IllegalArgumentException(
                "rejectionReason must be null for non-rejected decision"
            );
        }

        itemDecisions = List.copyOf(itemDecisions);
    }
}
