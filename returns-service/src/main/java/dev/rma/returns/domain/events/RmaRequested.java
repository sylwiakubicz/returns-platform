package dev.rma.returns.domain.events;

import dev.rma.returns.domain.RequestedItem;
import dev.rma.returns.domain.RmaReason;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
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
        items = List.copyOf(items);
    }
}
