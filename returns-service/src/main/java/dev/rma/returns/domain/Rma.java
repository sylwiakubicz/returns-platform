package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.*;
import dev.rma.returns.domain.events.*;
import dev.rma.returns.domain.exceptions.InvalidRmaStateTransitionException;
import dev.rma.returns.domain.exceptions.RmaDomainException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

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

    public LabelIssued issueLabel(
        String labelId,
        String trackingNumber,
        String labelFileKey,
        Instant issuedAt
    ) {
        if (status != RmaStatus.REQUESTED) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.LABEL_ISSUED);
        }
        LabelIssued labelIssued =  new LabelIssued(
            labelId,
            trackingNumber,
            labelFileKey,
            issuedAt
        );

        apply(labelIssued);

        return labelIssued;
    }

    public ParcelInTransit markParcelInTransit(Instant collectedAt) {
        if (status != RmaStatus.LABEL_ISSUED && status != RmaStatus.PICKUP_SCHEDULED) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.IN_TRANSIT);
        }
        ParcelInTransit parcelInTransit = new ParcelInTransit(collectedAt);

        apply(parcelInTransit);

        return parcelInTransit;
    }

    public PickupScheduled schedulePickup(String pickupId, LocalDate scheduledFor) {
        if (status != RmaStatus.LABEL_ISSUED) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.PICKUP_SCHEDULED);
        }

        PickupScheduled pickupScheduled = new PickupScheduled(pickupId, scheduledFor);

        apply(pickupScheduled);

        return pickupScheduled;
    }

    public ParcelReceived receive(Instant receivedAt, String werehouseId) {
        if (status != RmaStatus.IN_TRANSIT) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.RECEIVED);
        }
        ParcelReceived parcelReceived = new ParcelReceived(receivedAt, werehouseId);
        apply(parcelReceived);
        return parcelReceived;
    }

    public InspectionStarted startInspection(String inspectorId, Instant startedAt) {
        if (status != RmaStatus.RECEIVED) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.UNDER_INSPECTION);
        }
        InspectionStarted inspectionStarted = new InspectionStarted(inspectorId, startedAt);
        apply(inspectionStarted);
        return inspectionStarted;
    }

    public InspectionCompleted completeInspection(
        String inspectorId,
        Instant completedAt,
        List<InspectionResult> results,
        String approvedBy) {
        if (status != RmaStatus.UNDER_INSPECTION) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.UNDER_INSPECTION);
        }
        validateCompletion(results);
        validateApproval(results, approvedBy);
        InspectionCompleted inspectionCompleted = new InspectionCompleted(
            inspectorId,
            completedAt,
            results,
            approvedBy
        );

        apply(inspectionCompleted);
        return inspectionCompleted;
    }

    public static Rma fromHistory(List<DomainEvent> events) {
        Objects.requireNonNull(events, "events must not be null");

        if (events.isEmpty()) {
            throw new IllegalArgumentException("Event history must not be empty");
        }

        if (!(events.getFirst() instanceof RmaRequested)) {
            throw new IllegalArgumentException("RmaRequested must be the first event");
        }

        var rma = new Rma();

        for (DomainEvent event : events) {
            rma.apply(event);
        }

        return rma;
    }

    private void apply(DomainEvent event) {
        switch (event) {
            case RmaRequested e -> apply(e);
            case LabelIssued e -> apply(e);
            case PickupScheduled e -> apply(e);
            case ParcelInTransit e -> apply(e);
            case ParcelReceived e -> apply(e);
            case InspectionStarted e -> apply(e);
            case InspectionCompleted e -> apply(e);

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

    private void apply(LabelIssued labelIssued) {
        this.labelId = labelIssued.labelId();
        this.trackingNumber = labelIssued.trackingNumber();
        this.status = RmaStatus.LABEL_ISSUED;
        this.version += 1;
    }

    private void apply(PickupScheduled pickupScheduled) {
        this.version += 1;
        this.status = RmaStatus.PICKUP_SCHEDULED;
        this.pickupScheduledFor = pickupScheduled.scheduledFor();
    }

    private void apply(ParcelInTransit parcelInTransit) {
        this.version += 1;
        this.status = RmaStatus.IN_TRANSIT;
    }

    private void apply(ParcelReceived parcelReceived) {
        this.version += 1;
        this.status = RmaStatus.RECEIVED;
        this.parcelReceivedAt = parcelReceived.receivedAt();
    }

    private void apply(InspectionStarted inspectionStarted) {
        this.version += 1;
        this.status = RmaStatus.UNDER_INSPECTION;
    }

    private void apply(InspectionCompleted inspectionCompleted) {
        this.version += 1;
        this.inspectorId = inspectionCompleted.inspectorId();
        this.inspectedAt = inspectionCompleted.completedAt();

        this.items = this.items.stream()
            .map( item -> {
                    InspectionResult result = inspectionCompleted.results().stream()
                    .filter(r -> r. itemId().equals(item.itemId()))
                    .findFirst()
                    .orElseThrow();

                    return item.withInspection(result);
                }
            )
            .toList();
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

    private void validateCompletion(List<InspectionResult> results) {
        if (this.items.size() != results.size()) {
            throw new RmaDomainException(RmaErrorCode.INCOMPLETE_INSPECTION, "All items must be checked");
        }
        for (RmaItem item : this.items) {
            if (results.stream().noneMatch(r -> Objects.equals(r.itemId(), item.itemId()))) {
                throw new RmaDomainException(RmaErrorCode.INCOMPLETE_INSPECTION, "All items must be checked");
            }
        }
    }

    private void validateApproval(List<InspectionResult> results, String approvedBy) {
        results.forEach(result -> {
            RmaItem item = this.items.stream()
                .filter(i -> i.itemId().equals(result.itemId()))
                .findFirst()
                .orElseThrow();

                if (result.grade() == ItemGrade.D
                    && approvedBy == null
                    && item.totalValue().compareTo(new BigDecimal(500)) > 0) {
                    throw new RmaDomainException(RmaErrorCode.SUPERVISOR_APPROVAL_REQUIRED,
                        "Supervisor approval required");
                }
        });
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
