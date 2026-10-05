package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.*;
import dev.rma.returns.domain.events.*;
import dev.rma.returns.domain.exceptions.InvalidRmaStateTransitionException;
import dev.rma.returns.domain.exceptions.RmaDomainException;
import dev.rma.returns.domain.policy.InspectionPolicy;
import dev.rma.returns.domain.policy.RefundPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        String approvedBy,
        InspectionPolicy policy) {
        if (status != RmaStatus.UNDER_INSPECTION) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.UNDER_INSPECTION);
        }
        validateCompletion(results);
        validateApproval(results, approvedBy, policy);
        InspectionCompleted inspectionCompleted = new InspectionCompleted(
            inspectorId,
            completedAt,
            results,
            approvedBy
        );

        apply(inspectionCompleted);
        return inspectionCompleted;
    }

    public RefundDecided decideRefund(
        Instant decidedAt,
        RefundPolicy policy
    ) {
        if (status != RmaStatus.UNDER_INSPECTION) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.REFUND_PENDING);
        }

        List<ItemRefundDecision> itemDecisions = this.items.stream()
            .map(item -> calculateItemRefundDecision(
                item,
                policy
            ))
            .toList();

        BigDecimal sumItems = calculateSumItems(itemDecisions);

        BigDecimal shippingFee = calculateShippingFee(
            sumItems,
            policy
        );

        BigDecimal refundAmount = sumItems
            .subtract(shippingFee)
            .max(BigDecimal.ZERO);

        BigDecimal deductionTotal = calculateDeductionTotal(
            policy,
            shippingFee
        );


        RefundDecision decision = determineRefundDecision(
            this.items,
            refundAmount
        );

        String rejectionReason = determineRejectionReason(
            decision,
            refundAmount
        );

        RefundDecided event = new RefundDecided(
            decision,
            new Money(refundAmount, this.currency),
            new Money(deductionTotal, this.currency),
            itemDecisions,
            rejectionReason,
            decidedAt
        );

        apply(event);

        return event;
    }

    public RefundExecuted executeRefund(
        String payoutId,
        Money amount,
        int attempt,
        Instant executedAt
    ) {
        if (status != RmaStatus.REFUND_PENDING) {
            throw new InvalidRmaStateTransitionException(
                status,
                RmaStatus.REFUNDED
            );
        }

        validateExecutedRefundAmount(amount);

        RefundExecuted event = new RefundExecuted(
            payoutId,
            amount,
            attempt,
            executedAt
        );

        apply(event);

        return event;
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
            case RefundDecided e -> apply(e);
            case RefundExecuted e -> apply(e);

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

    private void apply(RefundDecided refundDecided) {
        this.version += 1;
        this.status = refundDecided.decision() == RefundDecision.REJECTED
            ? RmaStatus.REJECTED
            : RmaStatus.REFUND_PENDING;

        this.refundAmount = refundDecided.refundAmount();
        this.deductionTotal = refundDecided.deductionTotal();
        this.items = this.items.stream()
            .map(item -> {
                ItemRefundDecision itemRefundDecision = refundDecided.itemDecisions().stream()
                    .filter(decision -> decision.itemId().equals(item.itemId()))
                    .findFirst()
                    .orElseThrow();

                return item.withRefundDecision(itemRefundDecision);
            })
            .toList();
    }

    private void apply(RefundExecuted event) {
        this.version += 1;
        this.status = RmaStatus.REFUNDED;
        this.payoutId = event.payoutId();
        this.payoutAttempt = event.attempt();
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

    private void validateApproval(List<InspectionResult> results, String approvedBy, InspectionPolicy policy) {
        results.forEach(result -> {
            RmaItem item = this.items.stream()
                .filter(i -> i.itemId().equals(result.itemId()))
                .findFirst()
                .orElseThrow();

                if (result.grade() == ItemGrade.D
                    && approvedBy == null
                    && item.totalValue().compareTo(policy.supervisorApprovalThreshold()) > 0) {
                    throw new RmaDomainException(RmaErrorCode.SUPERVISOR_APPROVAL_REQUIRED,
                        "Supervisor approval required");
                }
        });
    }

    private ItemRefundDecision calculateItemRefundDecision(
        RmaItem item,
        RefundPolicy policy
    ) {
        int deductionPercent = calculateDeductionPercent(
            item,
            policy
        );

        BigDecimal refundableAmount = calculateRefundableAmount(
            item,
            deductionPercent
        );

        return new ItemRefundDecision(
            item.itemId(),
            item.grade(),
            deductionPercent,
            new Money(refundableAmount, this.currency)
        );
    }

    private int calculateDeductionPercent(
        RmaItem item,
        RefundPolicy policy
    ) {
        if (this.reason != RmaReason.CHANGED_MIND) {
            return 0;
        }

        return switch (item.grade()) {
            case A -> policy.gradeAPercent();
            case B -> policy.gradeBPercent();
            case C -> policy.gradeCPercent();
            case D -> 0;
        };
    }

    private BigDecimal calculateRefundableAmount(
        RmaItem item,
        int deductionPercent
    ) {
        if (item.grade() == ItemGrade.D) {
            return new BigDecimal("0.00");
        }

        BigDecimal deduction = calculateItemDeduction(
            item,
            deductionPercent
        );

        return item.totalValue()
            .subtract(deduction)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateItemDeduction(
        RmaItem item,
        int deductionPercent
    ) {
        return item.totalValue()
            .multiply(BigDecimal.valueOf(deductionPercent))
            .divide(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateSumItems(
        List<ItemRefundDecision> itemDecisions
    ) {
        return itemDecisions.stream()
            .map(ItemRefundDecision::refundableAmount)
            .map(Money::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateShippingFee(
        BigDecimal sumItems,
        RefundPolicy policy
    ) {
        if (this.reason == RmaReason.CHANGED_MIND
            && sumItems.compareTo(BigDecimal.ZERO) > 0) {
            return policy.returnShippingFee();
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal calculateDeductionTotal(
        RefundPolicy policy,
        BigDecimal shippingFee
    ) {
        BigDecimal itemDeductions = this.items.stream()
            .map(item -> {
                int deductionPercent = calculateDeductionPercent(
                    item,
                    policy
                );

                return calculateItemDeduction(
                    item,
                    deductionPercent
                );
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return itemDeductions.add(shippingFee);
    }

    private RefundDecision determineRefundDecision(
        List<RmaItem> items,
        BigDecimal refundAmount
    ) {
        if (refundAmount.compareTo(BigDecimal.ZERO) == 0) {
            return RefundDecision.REJECTED;
        }

        long itemsWithGradeD = items.stream()
            .filter(item -> item.grade() == ItemGrade.D)
            .count();

        if (itemsWithGradeD == 0) {
            return RefundDecision.FULL;
        }

        if (itemsWithGradeD == items.size()) {
            return RefundDecision.REJECTED;
        }

        return RefundDecision.PARTIAL;
    }

    private String determineRejectionReason(
        RefundDecision decision,
        BigDecimal refundAmount
    ) {
        if (decision != RefundDecision.REJECTED) {
            return null;
        }

        boolean allItemsRejected = this.items.stream()
            .allMatch(item -> item.grade() == ItemGrade.D);

        if (allItemsRejected) {
            return "All items rejected after inspection";
        }

        if (refundAmount.compareTo(BigDecimal.ZERO) == 0) {
            return "Refund amount is zero";
        }

        return "Refund rejected";
    }

    private void validateExecutedRefundAmount(Money amount) {
        Objects.requireNonNull(amount, "amount must not be null");

        boolean sameCurrency =
            amount.currency().equals(refundAmount.currency());

        boolean sameAmount =
            amount.amount().compareTo(refundAmount.amount()) == 0;

        if (!sameCurrency || !sameAmount) {
            throw new IllegalArgumentException(
                "Executed refund amount must match decided refund amount"
            );
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
