package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.*;
import dev.rma.returns.domain.events.DomainEvent;
import dev.rma.returns.domain.events.RmaRequested;
import dev.rma.returns.domain.exceptions.RmaDomainException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Rma {
    private UUID rmaId;
    private String businessNumber;
    private String orderId;
    private String customerId;
    private Instant orderDeliveredAt;
    private RmaReason reason;
    private String customerNote;
    private RmaStatus status;
    private int version;
    private List<RmaItem> items;
    private Currency currency;

    private String labelId;
    private String trackingNumber;
    private LocalDate pickupScheduledFor;
    private Instant parcelReceivedAt;
    private Instant inspectedAt;
    private String inspectorId;
    private Money refundAmount;
    private Money deductionTotal;
    private Integer payoutAttempt;
    private String payoutId;
    private FailureCode failureCode;
    private Instant closedAt;
    private ClosureReason closureReason;


    public static RmaRequested request(
        UUID rmaId,
        String businessNumber,
        String orderId,
        String customerId,
        Instant orderDeliveredAt,
        RmaReason reason,
        String customerNote,
        List<RequestedItem> items,
        Currency currency,
        Instant requestedAt,
        int returnWindowDays
    ) {
        validateRequest(
            orderDeliveredAt,
            requestedAt,
            items,
            currency,
            returnWindowDays
        );

        return new RmaRequested(
            rmaId,
            businessNumber,
            orderId,
            customerId,
            orderDeliveredAt,
            requestedAt,
            reason,
            customerNote,
            currency,
            items
        );
    }

    public static Rma fromHistory(List<DomainEvent> events) {
        // TODO: events nie może być puste
        // TODO: pierwsze zdarzenie powinno być requeste
        var rma = new Rma();

        for (DomainEvent event : events) {
            rma.apply(event);
        }

        return rma;
    }

    private void apply(DomainEvent event) {
        switch (event) {
            case RmaRequested e -> apply(e);

            // temporary solution -> pozostale eventy nie sa obslugiwane jeszcze
            default -> throw new IllegalStateException("Unexpected value: " + event);
        }
    }

    private void apply(RmaRequested event) {
        this.rmaId = event.rmaId();
        this.businessNumber = event.businessNumber();
        this.orderId = event.orderId();
        this.customerId = event.customerId();
        this.orderDeliveredAt = event.orderDeliveredAt();
        this.reason = event.reason();
        this.customerNote = event.customerNote();
        this.items = mapItems(event.items());
        this.currency = event.currency();
        this.status = RmaStatus.REQUESTED;
        this.version = 1;
    }

    private List<RmaItem> mapItems(List<RequestedItem> requestedItems) {
        return requestedItems.stream().map(this::mapItem).toList();
    }

    private RmaItem mapItem(RequestedItem requestedItem) {
        return new RmaItem(
            requestedItem.itemId(),
            requestedItem.sku(),
            requestedItem.name(),
            requestedItem.quantity(),
            requestedItem.unitPrice(),
            null,
            null,
            null,
            null,
            null
        );
    }

    private static void validateRequest(
        Instant orderDeliveredAt,
        Instant requestedAt,
        List<RequestedItem> items,
        Currency currency,
        int returnWindowDays
    ) {
        Objects.requireNonNull(orderDeliveredAt, "orderDeliveredAt must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

        Instant returnDeadline =
            orderDeliveredAt.plus(Duration.ofDays(returnWindowDays));

        if (requestedAt.isAfter(returnDeadline)) {
            throw new RmaDomainException(
                RmaErrorCode.RETURN_WINDOW_EXPIRED,
                "Return window has expired"
            );
        }

        if (items.isEmpty()) {
            throw new RmaDomainException(
                RmaErrorCode.NO_ITEMS,
                "There must be at least 1 item"
            );
        }

        for (RequestedItem item : items) {
            if (!item.unitPrice().currency().equals(currency)) {
                throw new RmaDomainException(
                    RmaErrorCode.MIXED_CURRENCIES,
                    "Mixed currencies are forbidden"
                );
            }
        }
    }

    public UUID rmaId() {
        return rmaId;
    }

    public String businessNumber() {
        return businessNumber;
    }

    public String orderId() {
        return orderId;
    }

    public String customerId() {
        return customerId;
    }

    public Instant orderDeliveredAt() {
        return orderDeliveredAt;
    }

    public RmaReason reason() {
        return reason;
    }

    public String customerNote() {
        return customerNote;
    }

    public RmaStatus status() {
        return status;
    }

    public int version() {
        return version;
    }

    public List<RmaItem> items() {
        return items;
    }

    public Currency currency() {
        return currency;
    }

    public String labelId() {
        return labelId;
    }

    public String trackingNumber() {
        return trackingNumber;
    }

    public LocalDate pickupScheduledFor() {
        return pickupScheduledFor;
    }

    public Instant parcelReceivedAt() {
        return parcelReceivedAt;
    }

    public Instant inspectedAt() {
        return inspectedAt;
    }

    public String inspectorId() {
        return inspectorId;
    }

    public Money refundAmount() {
        return refundAmount;
    }

    public Money deductionTotal() {
        return deductionTotal;
    }

    public Integer payoutAttempt() {
        return payoutAttempt;
    }

    public String payoutId() {
        return payoutId;
    }

    public FailureCode failureCode() {
        return failureCode;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public ClosureReason closureReason() {
        return closureReason;
    }
}
