package dev.rma.returns.domain.events;

import dev.rma.returns.domain.RequestedItem;
import dev.rma.returns.domain.enums.RmaReason;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RmaRequested(
    UUID rmaId,
    String businessNumber,
    String orderId,
    String customerId,
    Instant orderDeliveredAt,
    Instant requestedAt,
    RmaReason reason,
    String customerNote,
    Currency currency,
    List<RequestedItem> items
) implements DomainEvent{
    public RmaRequested {
        Objects.requireNonNull(rmaId, "rmaId must not be null");
        Objects.requireNonNull(businessNumber, "businessNumber must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(orderDeliveredAt, "orderDeliveredAt must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(items, "items must not be null");

        items = List.copyOf(items);
    }
}
