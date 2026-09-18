package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.ClosureReason;
import dev.rma.returns.domain.enums.FailureCode;
import dev.rma.returns.domain.enums.RmaReason;
import dev.rma.returns.domain.enums.RmaStatus;
import dev.rma.returns.domain.events.DomainEvent;
import dev.rma.returns.domain.events.RmaRequested;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
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

    private static void validateRequest(
        Instant orderDeliveredAt,
        Instant requestedAt,
        List<RequestedItem> items,
        Currency currency,
        int returnWindowDays
    ) {
        // todo: weryfikacja czy zwrot zmescil sie w czasie
        // todo: weryfikacja czy zwrot ma przynajmniej jeden item
        // todo: sprawdzenie czy kazda pozycja jest wieksza od 0
        // todo: sprawdzenie czy wszystkie pozycje maja taka sama walute
        // todo: powod zwrotu musi byc z enuma
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
