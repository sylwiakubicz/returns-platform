package dev.rma.returns.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import dev.rma.returns.domain.enums.*;
import dev.rma.returns.domain.events.*;
import dev.rma.returns.domain.exceptions.InvalidRmaStateTransitionException;
import dev.rma.returns.domain.exceptions.RmaDomainException;
import dev.rma.returns.domain.policy.InspectionPolicy;
import dev.rma.returns.domain.policy.RefundPolicy;

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
            int returnWindowDays) {
        validateRequest(orderDeliveredAt, requestedAt, items, currency, returnWindowDays);

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
                items);
    }

    public LabelIssued issueLabel(String labelId, String trackingNumber, String labelFileKey, Instant issuedAt) {
        if (status != RmaStatus.REQUESTED) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.LABEL_ISSUED);
        }
        LabelIssued labelIssued = new LabelIssued(rmaId, labelId, trackingNumber, labelFileKey, issuedAt);

        apply(labelIssued);

        return labelIssued;
    }

    public ParcelInTransit markParcelInTransit(Instant collectedAt) {
        if (status != RmaStatus.LABEL_ISSUED && status != RmaStatus.PICKUP_SCHEDULED) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.IN_TRANSIT);
        }
        ParcelInTransit parcelInTransit = new ParcelInTransit(rmaId, collectedAt);

        apply(parcelInTransit);

        return parcelInTransit;
    }

    public PickupScheduled schedulePickup(String pickupId, LocalDate scheduledFor, Instant occurredAt) {
        if (status != RmaStatus.LABEL_ISSUED) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.PICKUP_SCHEDULED);
        }

        PickupScheduled pickupScheduled = new PickupScheduled(rmaId, pickupId, scheduledFor, occurredAt);

        apply(pickupScheduled);

        return pickupScheduled;
    }

    public ParcelReceived receive(Instant receivedAt, String werehouseId) {
        if (status != RmaStatus.IN_TRANSIT) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.RECEIVED);
        }
        ParcelReceived parcelReceived = new ParcelReceived(rmaId, receivedAt, werehouseId);
        apply(parcelReceived);
        return parcelReceived;
    }

    public InspectionStarted startInspection(String inspectorId, Instant startedAt) {
        if (status != RmaStatus.RECEIVED) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.UNDER_INSPECTION);
        }
        InspectionStarted inspectionStarted = new InspectionStarted(rmaId, inspectorId, startedAt);
        apply(inspectionStarted);
        return inspectionStarted;
    }

    public List<DomainEvent> completeInspection(
            String inspectorId,
            Instant completedAt,
            List<InspectionResult> results,
            String approvedBy,
            InspectionPolicy policy,
            RefundPolicy refundPolicy) {
        if (status != RmaStatus.UNDER_INSPECTION) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.UNDER_INSPECTION);
        }
        if (inspectedAt != null) {
            throw new IllegalStateException("Inspection has already been completed");
        }
        validateCompletion(results);
        validateApproval(results, approvedBy, policy);
        InspectionCompleted inspectionCompleted =
                new InspectionCompleted(rmaId, inspectorId, completedAt, results, approvedBy);

        List<RmaItem> inspectedItems = items.stream()
                .map(item -> item.withInspection(results.stream()
                        .filter(result -> result.itemId().equals(item.itemId()))
                        .findFirst()
                        .orElseThrow()))
                .toList();
        RefundDecided refundDecided = calculateRefundDecision(inspectedItems, completedAt, refundPolicy);

        apply(inspectionCompleted);
        apply(refundDecided);
        return List.of(inspectionCompleted, refundDecided);
    }

    public RefundDecided decideRefund(Instant decidedAt, RefundPolicy policy) {
        if (status != RmaStatus.UNDER_INSPECTION) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.REFUND_PENDING);
        }

        if (inspectedAt == null || items.stream().anyMatch(item -> item.grade() == null)) {
            throw new RmaDomainException(
                    RmaErrorCode.INCOMPLETE_INSPECTION, "Inspection must be completed before deciding a refund");
        }

        RefundDecided event = calculateRefundDecision(items, decidedAt, policy);
        apply(event);
        return event;
    }

    private RefundDecided calculateRefundDecision(
            List<RmaItem> inspectedItems, Instant decidedAt, RefundPolicy policy) {
        List<ItemRefundDecision> itemDecisions = inspectedItems.stream()
                .map(item -> calculateItemRefundDecision(item, policy))
                .toList();

        BigDecimal sumItems = calculateSumItems(itemDecisions);

        BigDecimal shippingFee = calculateShippingFee(sumItems, policy);

        BigDecimal refundAmount = sumItems.subtract(shippingFee).max(BigDecimal.ZERO);

        BigDecimal deductionTotal = calculateDeductionTotal(inspectedItems, policy, shippingFee);

        RefundDecision decision = determineRefundDecision(inspectedItems, refundAmount);

        String rejectionReason = determineRejectionReason(inspectedItems, decision, refundAmount);

        RefundDecided event = new RefundDecided(
                rmaId,
                decision,
                new Money(refundAmount, this.currency),
                new Money(deductionTotal, this.currency),
                itemDecisions,
                rejectionReason,
                decidedAt);

        return event;
    }

    public RefundExecuted executeRefund(String payoutId, Money amount, int attempt, Instant executedAt) {
        if (status != RmaStatus.REFUND_PENDING) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.REFUNDED);
        }

        validateExecutedRefundAmount(amount);

        RefundExecuted event = new RefundExecuted(rmaId, payoutId, amount, attempt, executedAt);

        apply(event);

        return event;
    }

    public RefundFailed failRefund(
            FailureCode failureCode, String failureMessage, int attempt, boolean retryable, Instant failedAt) {
        if (status != RmaStatus.REFUND_PENDING) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.REFUND_FAILED);
        }

        RefundFailed event = new RefundFailed(rmaId, failureCode, failureMessage, attempt, retryable, failedAt);

        apply(event);

        return event;
    }

    public RefundDecided retryRefund(Instant decidedAt) {
        if (status != RmaStatus.REFUND_FAILED) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.REFUND_PENDING);
        }

        List<ItemRefundDecision> itemDecisions = items.stream()
                .map(item -> new ItemRefundDecision(
                        item.itemId(), item.grade(), item.deductionPercent(), item.refundableAmount()))
                .toList();

        RefundDecided event = new RefundDecided(
                rmaId, currentRefundDecision(), refundAmount, deductionTotal, itemDecisions, null, decidedAt);

        apply(event);

        return event;
    }

    public RmaExpired expire(Instant expiredAt, int daysSinceRequest) {
        if (status != RmaStatus.REQUESTED && status != RmaStatus.LABEL_ISSUED && status != RmaStatus.PICKUP_SCHEDULED) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.EXPIRED);
        }

        RmaExpired event = new RmaExpired(rmaId, expiredAt, daysSinceRequest);
        apply(event);
        return event;
    }

    public RmaLost declareLost(String lastKnownStatus, Instant declaredLostAt) {
        if (status != RmaStatus.IN_TRANSIT) {
            throw new InvalidRmaStateTransitionException(status, RmaStatus.LOST);
        }

        RmaLost event = new RmaLost(rmaId, lastKnownStatus, declaredLostAt);
        apply(event);
        return event;
    }

    public RmaCancelled cancel(String cancelledBy, Instant cancelledAt, String reason) {
        if (status != RmaStatus.REQUESTED && status != RmaStatus.LABEL_ISSUED && status != RmaStatus.PICKUP_SCHEDULED) {
            throw new RmaDomainException(
                    RmaErrorCode.CANCELLATION_NOT_ALLOWED, "Cancellation is not allowed in state " + status);
        }

        RmaCancelled event = new RmaCancelled(rmaId, cancelledBy, cancelledAt, reason);
        apply(event);
        return event;
    }

    public RmaClosed close(ClosureReason closureReason, Instant closedAt) {
        ClosureReason expectedReason =
                switch (status) {
                    case REFUNDED -> ClosureReason.REFUND_COMPLETED;
                    case REJECTED -> ClosureReason.REJECTED_AFTER_INSPECTION;
                    case EXPIRED -> ClosureReason.EXPIRED_NO_SHIPMENT;
                    case LOST -> ClosureReason.PARCEL_LOST;
                    case CANCELLED -> ClosureReason.CANCELLED_BY_CUSTOMER;
                    case REFUND_FAILED -> ClosureReason.CLOSED_BY_OPERATOR;
                    default -> throw new InvalidRmaStateTransitionException(status, RmaStatus.CLOSED);
                };

        RmaClosed event = new RmaClosed(rmaId, closureReason, closedAt);
        if (closureReason != expectedReason) {
            throw new IllegalArgumentException("Closure reason must match current RMA state");
        }

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
            case RefundFailed e -> apply(e);
            case RmaExpired e -> apply(e);
            case RmaLost e -> apply(e);
            case RmaCancelled e -> apply(e);
            case RmaClosed e -> apply(e);
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
                .map(item -> {
                    InspectionResult result = inspectionCompleted.results().stream()
                            .filter(r -> r.itemId().equals(item.itemId()))
                            .findFirst()
                            .orElseThrow();

                    return item.withInspection(result);
                })
                .toList();
    }

    private void apply(RefundDecided refundDecided) {
        boolean retryAfterFailure = this.status == RmaStatus.REFUND_FAILED;

        this.version += 1;

        this.status =
                refundDecided.decision() == RefundDecision.REJECTED ? RmaStatus.REJECTED : RmaStatus.REFUND_PENDING;

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

        if (retryAfterFailure) {
            this.payoutAttempt += 1;
        } else if (this.status == RmaStatus.REFUND_PENDING) {
            this.payoutAttempt = 1;
        }
    }

    private void apply(RefundExecuted event) {
        this.version += 1;
        this.status = RmaStatus.REFUNDED;
        this.payoutId = event.payoutId();
        this.payoutAttempt = event.attempt();
    }

    private void apply(RefundFailed event) {
        this.version += 1;
        this.status = RmaStatus.REFUND_FAILED;
        this.failureCode = event.failureCode();
        this.payoutAttempt = event.attempt();
    }

    private void apply(RmaExpired event) {
        this.status = RmaStatus.EXPIRED;
        this.version += 1;
    }

    private void apply(RmaLost event) {
        this.status = RmaStatus.LOST;
        this.version += 1;
    }

    private void apply(RmaCancelled event) {
        this.status = RmaStatus.CANCELLED;
        this.version += 1;
    }

    private void apply(RmaClosed event) {
        this.status = RmaStatus.CLOSED;
        this.closedAt = event.closedAt();
        this.closureReason = event.closureReason();
        this.version += 1;
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
                null);
    }

    private static void validateRequest(
            Instant orderDeliveredAt,
            Instant requestedAt,
            List<RequestedItem> items,
            Currency currency,
            int returnWindowDays) {
        Objects.requireNonNull(orderDeliveredAt, "orderDeliveredAt must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

        Instant returnDeadline = orderDeliveredAt.plus(Duration.ofDays(returnWindowDays));

        if (requestedAt.isAfter(returnDeadline)) {
            throw new RmaDomainException(RmaErrorCode.RETURN_WINDOW_EXPIRED, "Return window has expired");
        }

        if (items.isEmpty()) {
            throw new RmaDomainException(RmaErrorCode.NO_ITEMS, "There must be at least 1 item");
        }

        for (RequestedItem item : items) {
            if (!item.unitPrice().currency().equals(currency)) {
                throw new RmaDomainException(RmaErrorCode.MIXED_CURRENCIES, "Mixed currencies are forbidden");
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
                    && (approvedBy == null || approvedBy.isBlank())
                    && item.totalValue().compareTo(policy.supervisorApprovalThreshold()) > 0) {
                throw new RmaDomainException(RmaErrorCode.SUPERVISOR_APPROVAL_REQUIRED, "Supervisor approval required");
            }
        });
    }

    private ItemRefundDecision calculateItemRefundDecision(RmaItem item, RefundPolicy policy) {
        int deductionPercent = calculateDeductionPercent(item, policy);

        BigDecimal refundableAmount = calculateRefundableAmount(item, deductionPercent);

        return new ItemRefundDecision(
                item.itemId(), item.grade(), deductionPercent, new Money(refundableAmount, this.currency));
    }

    private int calculateDeductionPercent(RmaItem item, RefundPolicy policy) {
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

    private BigDecimal calculateRefundableAmount(RmaItem item, int deductionPercent) {
        if (item.grade() == ItemGrade.D) {
            return new BigDecimal("0.00");
        }

        BigDecimal deduction = calculateItemDeduction(item, deductionPercent);

        return item.totalValue().subtract(deduction).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateItemDeduction(RmaItem item, int deductionPercent) {
        return item.totalValue()
                .multiply(BigDecimal.valueOf(deductionPercent))
                .divide(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateSumItems(List<ItemRefundDecision> itemDecisions) {
        return itemDecisions.stream()
                .map(ItemRefundDecision::refundableAmount)
                .map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateShippingFee(BigDecimal sumItems, RefundPolicy policy) {
        if (this.reason == RmaReason.CHANGED_MIND && sumItems.compareTo(BigDecimal.ZERO) > 0) {
            return policy.returnShippingFee();
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal calculateDeductionTotal(
            List<RmaItem> inspectedItems, RefundPolicy policy, BigDecimal shippingFee) {
        BigDecimal itemDeductions = inspectedItems.stream()
                .map(item -> {
                    int deductionPercent = calculateDeductionPercent(item, policy);

                    return calculateItemDeduction(item, deductionPercent);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return itemDeductions.add(shippingFee);
    }

    private RefundDecision determineRefundDecision(List<RmaItem> items, BigDecimal refundAmount) {
        if (refundAmount.compareTo(BigDecimal.ZERO) == 0) {
            return RefundDecision.REJECTED;
        }

        long itemsWithGradeD =
                items.stream().filter(item -> item.grade() == ItemGrade.D).count();

        if (itemsWithGradeD == 0) {
            return RefundDecision.FULL;
        }

        if (itemsWithGradeD == items.size()) {
            return RefundDecision.REJECTED;
        }

        return RefundDecision.PARTIAL;
    }

    private String determineRejectionReason(
            List<RmaItem> inspectedItems, RefundDecision decision, BigDecimal refundAmount) {
        if (decision != RefundDecision.REJECTED) {
            return null;
        }

        boolean allItemsRejected = inspectedItems.stream().allMatch(item -> item.grade() == ItemGrade.D);

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

        boolean sameCurrency = amount.currency().equals(refundAmount.currency());

        boolean sameAmount = amount.amount().compareTo(refundAmount.amount()) == 0;

        if (!sameCurrency || !sameAmount) {
            throw new IllegalArgumentException("Executed refund amount must match decided refund amount");
        }
    }

    private RefundDecision currentRefundDecision() {
        boolean hasRejectedItem = items.stream().anyMatch(item -> item.grade() == ItemGrade.D);

        return hasRejectedItem ? RefundDecision.PARTIAL : RefundDecision.FULL;
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
