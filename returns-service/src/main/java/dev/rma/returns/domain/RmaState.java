package dev.rma.returns.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.rma.returns.domain.enums.ClosureReason;
import dev.rma.returns.domain.enums.FailureCode;
import dev.rma.returns.domain.enums.RmaReason;
import dev.rma.returns.domain.enums.RmaStatus;

/** Niemutowalna reprezentacja pełnego stanu RMA na konkretnej wersji. */
public record RmaState(
        UUID rmaId,
        String businessNumber,
        String orderId,
        String customerId,
        Instant orderDeliveredAt,
        RmaReason reason,
        String customerNote,
        RmaStatus status,
        int version,
        List<RmaItem> items,
        Currency currency,
        String labelId,
        String trackingNumber,
        LocalDate pickupScheduledFor,
        Instant parcelReceivedAt,
        Instant inspectedAt,
        String inspectorId,
        Money refundAmount,
        Money deductionTotal,
        Integer payoutAttempt,
        String payoutId,
        FailureCode failureCode,
        Instant closedAt,
        ClosureReason closureReason) {
    public RmaState {
        Objects.requireNonNull(rmaId, "rmaId must not be null");
        Objects.requireNonNull(businessNumber, "businessNumber must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(orderDeliveredAt, "orderDeliveredAt must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1");
        }
        items = List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
    }
}
