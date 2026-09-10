package dev.rma.returns.domain.events;

import dev.rma.returns.domain.ItemRefundDecision;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.RefundDecision;

import java.time.Instant;
import java.util.List;

public record RefundDecided(
    RefundDecision decision,
    Money refundAmount,
    Money deductionTotal,
    List<ItemRefundDecision> itemDecisions,
    String rejectionReason,
    Instant decidedAt
) implements DomainEvent {
    public RefundDecided {
        itemDecisions = List.copyOf(itemDecisions);
    }
}
