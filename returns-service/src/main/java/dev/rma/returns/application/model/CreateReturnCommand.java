package dev.rma.returns.application.model;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

import dev.rma.returns.domain.RequestedItem;
import dev.rma.returns.domain.enums.RmaReason;

public record CreateReturnCommand(
        String orderId,
        String customerId,
        Instant orderDeliveredAt,
        RmaReason reason,
        String customerNote,
        Currency currency,
        List<RequestedItem> items) {
    public CreateReturnCommand {
        items = List.copyOf(items);
    }
}
