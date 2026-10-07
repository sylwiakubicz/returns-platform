package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.*;
import dev.rma.returns.domain.events.*;
import dev.rma.returns.domain.exceptions.InvalidRmaStateTransitionException;
import dev.rma.returns.domain.exceptions.RmaDomainException;
import dev.rma.returns.domain.policy.InspectionPolicy;
import dev.rma.returns.domain.policy.RefundPolicy;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class RmaLifecycleTest {
    private static final Currency PLN = Currency.getInstance("PLN");
    private static final Instant AT = Instant.parse("2026-10-01T12:00:00Z");
    private static final RefundPolicy REFUND_POLICY = new RefundPolicy(0, 15, 40, new BigDecimal("15.00"));
    private static final InspectionPolicy INSPECTION_POLICY = new InspectionPolicy(new BigDecimal("500.00"));

    private enum Command {
        ISSUE_LABEL, SCHEDULE_PICKUP, IN_TRANSIT, RECEIVE, START_INSPECTION,
        COMPLETE_INSPECTION, DECIDE_REFUND, EXECUTE_REFUND, FAIL_REFUND,
        RETRY_REFUND, EXPIRE, DECLARE_LOST, CANCEL, CLOSE
    }

    @ParameterizedTest(name = "{0}: {1} is forbidden")
    @MethodSource("forbiddenTransitions")
    void shouldRejectEveryForbiddenTransition(RmaStatus status, Command command) {
        Rma rma = Rma.fromHistory(historyFor(status));
        int version = rma.version();
        List<RmaItem> items = rma.items();
        Money refundAmount = rma.refundAmount();

        assertThatThrownBy(() -> execute(rma, command))
            .isInstanceOfAny(InvalidRmaStateTransitionException.class, RmaDomainException.class);

        assertThat(rma.status()).isEqualTo(status);
        assertThat(rma.version()).isEqualTo(version);
        assertThat(rma.items()).isEqualTo(items);
        assertThat(rma.refundAmount()).isEqualTo(refundAmount);
    }

    private static Stream<Arguments> forbiddenTransitions() {
        return Arrays.stream(RmaStatus.values()).flatMap(status -> Arrays.stream(Command.values())
            .filter(command -> !allowedStates(command).contains(status))
            .map(command -> Arguments.of(status, command)));
    }

    private static Set<RmaStatus> allowedStates(Command command) {
        return switch (command) {
            case ISSUE_LABEL -> Set.of(RmaStatus.REQUESTED);
            case SCHEDULE_PICKUP -> Set.of(RmaStatus.LABEL_ISSUED);
            case IN_TRANSIT -> Set.of(RmaStatus.LABEL_ISSUED, RmaStatus.PICKUP_SCHEDULED);
            case RECEIVE, DECLARE_LOST -> Set.of(RmaStatus.IN_TRANSIT);
            case START_INSPECTION -> Set.of(RmaStatus.RECEIVED);
            case COMPLETE_INSPECTION, DECIDE_REFUND -> Set.of(RmaStatus.UNDER_INSPECTION);
            case EXECUTE_REFUND, FAIL_REFUND -> Set.of(RmaStatus.REFUND_PENDING);
            case RETRY_REFUND -> Set.of(RmaStatus.REFUND_FAILED);
            case EXPIRE, CANCEL -> Set.of(RmaStatus.REQUESTED, RmaStatus.LABEL_ISSUED, RmaStatus.PICKUP_SCHEDULED);
            case CLOSE -> Set.of(RmaStatus.REFUND_FAILED, RmaStatus.REFUNDED, RmaStatus.REJECTED,
                RmaStatus.EXPIRED, RmaStatus.LOST, RmaStatus.CANCELLED);
        };
    }

    private void execute(Rma rma, Command command) {
        switch (command) {
            case ISSUE_LABEL -> rma.issueLabel("label", "tracking", "label.pdf", AT);
            case SCHEDULE_PICKUP -> rma.schedulePickup("pickup", LocalDate.of(2026, 10, 2));
            case IN_TRANSIT -> rma.markParcelInTransit(AT);
            case RECEIVE -> rma.receive(AT, "warehouse");
            case START_INSPECTION -> rma.startInspection("inspector", AT);
            case COMPLETE_INSPECTION -> rma.completeInspection("inspector", AT,
                results(ItemGrade.A), null, INSPECTION_POLICY, REFUND_POLICY);
            case DECIDE_REFUND -> rma.decideRefund(AT, REFUND_POLICY);
            case EXECUTE_REFUND -> rma.executeRefund("payout", money("85.00"), 1, AT);
            case FAIL_REFUND -> rma.failRefund(FailureCode.INVALID_ACCOUNT, "Invalid account", 1, false, AT);
            case RETRY_REFUND -> rma.retryRefund(AT);
            case EXPIRE -> rma.expire(AT, 14);
            case DECLARE_LOST -> rma.declareLost("IN_TRANSIT", AT);
            case CANCEL -> rma.cancel("customer", AT, null);
            case CLOSE -> rma.close(ClosureReason.CLOSED_BY_OPERATOR, AT);
        }
    }

    @Test
    void shouldCompletePositiveLifecycleAndReplayItToClosed() {
        List<DomainEvent> history = positiveHistory();
        Rma restored = Rma.fromHistory(history);

        assertThat(restored.status()).isEqualTo(RmaStatus.CLOSED);
        assertThat(restored.version()).isEqualTo(10);
        assertThat(restored.closureReason()).isEqualTo(ClosureReason.REFUND_COMPLETED);
        assertThat(restored.refundAmount()).isEqualTo(money("85.00"));
        assertThat(restored.payoutId()).isEqualTo("payout");
    }

    @Test
    void shouldIncreaseVersionByOneForEachEvent() {
        List<DomainEvent> history = positiveHistory();
        for (int size = 1; size <= history.size(); size++) {
            assertThat(Rma.fromHistory(history.subList(0, size)).version()).isEqualTo(size);
        }
    }

    @Test
    void shouldKeepItemsThroughoutEveryLifecycleBranch() {
        for (RmaStatus status : RmaStatus.values()) {
            assertThat(Rma.fromHistory(historyFor(status)).items()).hasSize(1);
        }
    }

    @Test
    void shouldKeepOneCurrencyThroughoutEveryLifecycleBranch() {
        for (RmaStatus status : RmaStatus.values()) {
            Rma rma = Rma.fromHistory(historyFor(status));
            assertThat(rma.currency()).isEqualTo(PLN);
            assertThat(rma.items()).allSatisfy(item -> {
                assertThat(item.unitPrice().currency()).isEqualTo(PLN);
                if (item.refundableAmount() != null) {
                    assertThat(item.refundableAmount().currency()).isEqualTo(PLN);
                }
            });
            if (rma.refundAmount() != null) {
                assertThat(rma.refundAmount().currency()).isEqualTo(PLN);
                assertThat(rma.deductionTotal().currency()).isEqualTo(PLN);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ItemGrade.class)
    void shouldNeverReturnNegativeAmount(ItemGrade grade) {
        Rma rma = inspectedReturn(RmaReason.CHANGED_MIND, grade, "10.00");
        assertThat(rma.refundAmount().amount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @EnumSource(ItemGrade.class)
    void shouldNeverReturnMoreThanItemValue(ItemGrade grade) {
        Rma rma = inspectedReturn(RmaReason.CHANGED_MIND, grade, "100.00");
        assertThat(rma.refundAmount().amount()).isLessThanOrEqualTo(new BigDecimal("100.00"));
    }

    @Disabled("Specification conflict: section 6.2 charges 15 PLN for a 10 PLN item, violating invariant 6")
    @Test
    void shouldNeverDeductMoreThanItemValue() {
        Rma rma = inspectedReturn(RmaReason.CHANGED_MIND, ItemGrade.A, "10.00");
        assertThat(rma.deductionTotal().amount()).isLessThanOrEqualTo(new BigDecimal("10.00"));
    }

    @ParameterizedTest
    @EnumSource(RmaReason.class)
    void shouldReturnZeroForGradeDForEveryReason(RmaReason reason) {
        Rma rma = inspectedReturn(reason, ItemGrade.D, "100.00");
        assertThat(rma.items().getFirst().refundableAmount().amount()).isZero();
        assertThat(rma.status()).isEqualTo(RmaStatus.REJECTED);
        assertThat(rma.payoutAttempt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = RmaReason.class, names = "CHANGED_MIND", mode = EnumSource.Mode.EXCLUDE)
    void shouldNotApplyGradeDeductionsForOtherReasons(RmaReason reason) {
        for (ItemGrade grade : ItemGrade.values()) {
            Rma rma = inspectedReturn(reason, grade, "100.00");
            assertThat(rma.items().getFirst().deductionPercent()).isZero();
            assertThat(rma.deductionTotal().amount()).isZero();
        }
    }

    @ParameterizedTest
    @EnumSource(Command.class)
    void shouldRejectEveryCommandAfterClosure(Command command) {
        Rma rma = Rma.fromHistory(positiveHistory());
        assertThatThrownBy(() -> execute(rma, command))
            .isInstanceOfAny(InvalidRmaStateTransitionException.class, RmaDomainException.class);
        assertThat(rma.status()).isEqualTo(RmaStatus.CLOSED);
        assertThat(rma.version()).isEqualTo(10);
    }

    @Test
    void shouldKeepBusinessNumberThroughoutEveryLifecycleBranch() {
        for (RmaStatus status : RmaStatus.values()) {
            assertThat(Rma.fromHistory(historyFor(status)).businessNumber()).isEqualTo("RMA-2026-000001");
        }
    }

    @Test
    void shouldRequireExecutedAmountToMatchDecisionIncludingCurrency() {
        for (Money wrong : List.of(money("84.99"), money("85.01"),
            new Money(new BigDecimal("85.00"), Currency.getInstance("EUR")))) {
            Rma rma = Rma.fromHistory(historyFor(RmaStatus.REFUND_PENDING));
            assertThatThrownBy(() -> rma.executeRefund("payout", wrong, 1, AT))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(rma.status()).isEqualTo(RmaStatus.REFUND_PENDING);
            assertThat(rma.version()).isEqualTo(8);
            assertThat(rma.payoutId()).isNull();
        }
    }

    @Test
    void shouldHaveNonblankPayoutIdWhenRefunded() {
        Rma rma = Rma.fromHistory(historyFor(RmaStatus.REFUND_PENDING));
        rma.executeRefund("payout", rma.refundAmount(), 1, AT);
        assertThat(rma.status()).isEqualTo(RmaStatus.REFUNDED);
        assertThat(rma.payoutId()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(value = RmaStatus.class,
        names = {"REFUNDED", "REJECTED", "EXPIRED", "LOST", "CANCELLED", "REFUND_FAILED"})
    void shouldAllowClosingEveryTerminalOutcome(RmaStatus status) {
        Rma rma = Rma.fromHistory(historyFor(status));
        int version = rma.version();
        rma.close(closureReason(status), AT);
        assertThat(rma.status()).isEqualTo(RmaStatus.CLOSED);
        assertThat(rma.version()).isEqualTo(version + 1);
    }

    @Test
    void shouldHandleThreeItemsWithOneRejectedAsPartialRefund() {
        RmaRequested requested = requested(RmaReason.CHANGED_MIND, List.of(
            item("one", "100.00"), item("two", "100.00"), item("three", "100.00")));
        List<DomainEvent> history = new ArrayList<>(historyFor(RmaStatus.UNDER_INSPECTION));
        history.set(0, requested);
        Rma rma = Rma.fromHistory(history);
        List<DomainEvent> events = rma.completeInspection("inspector", AT, List.of(
            new InspectionResult("one", ItemGrade.A, null, null),
            new InspectionResult("two", ItemGrade.C, null, null),
            new InspectionResult("three", ItemGrade.D, null, null)
        ), null, INSPECTION_POLICY, REFUND_POLICY);
        assertThat(((RefundDecided) events.getLast()).decision()).isEqualTo(RefundDecision.PARTIAL);
        assertThat(rma.refundAmount()).isEqualTo(money("145.00"));
        assertThat(rma.items().getLast().refundableAmount().amount()).isZero();
    }

    @Test
    void shouldRefundDamagedInTransitItemWithGradeCInFull() {
        Rma rma = inspectedReturn(RmaReason.DAMAGED_IN_TRANSIT, ItemGrade.C, "100.00");
        assertThat(rma.refundAmount()).isEqualTo(money("100.00"));
        assertThat(rma.deductionTotal().amount()).isZero();
        assertThat(rma.items().getFirst().grade()).isEqualTo(ItemGrade.C);
    }

    private static List<DomainEvent> positiveHistory() {
        List<DomainEvent> events = new ArrayList<>(historyFor(RmaStatus.REQUESTED));
        Rma rma = Rma.fromHistory(events);
        events.add(rma.issueLabel("label", "tracking", "label.pdf", AT));
        events.add(rma.schedulePickup("pickup", LocalDate.of(2026, 10, 2)));
        events.add(rma.markParcelInTransit(AT));
        events.add(rma.receive(AT, "warehouse"));
        events.add(rma.startInspection("inspector", AT));
        events.addAll(rma.completeInspection("inspector", AT, results(ItemGrade.A),
            null, INSPECTION_POLICY, REFUND_POLICY));
        events.add(rma.executeRefund("payout", rma.refundAmount(), 1, AT));
        events.add(rma.close(ClosureReason.REFUND_COMPLETED, AT));
        assertThat(rma.status()).isEqualTo(RmaStatus.CLOSED);
        return events;
    }

    private static Rma inspectedReturn(RmaReason reason, ItemGrade grade, String price) {
        List<DomainEvent> history = new ArrayList<>(historyFor(RmaStatus.UNDER_INSPECTION));
        history.set(0, requested(reason, List.of(item("item", price))));
        Rma rma = Rma.fromHistory(history);
        rma.completeInspection("inspector", AT, results(grade), null, INSPECTION_POLICY, REFUND_POLICY);
        return rma;
    }

    private static List<DomainEvent> historyFor(RmaStatus status) {
        List<DomainEvent> history = new ArrayList<>();
        history.add(requested(RmaReason.CHANGED_MIND, List.of(item("item", "100.00"))));
        if (status == RmaStatus.REQUESTED) return history;
        if (status == RmaStatus.EXPIRED) {
            history.add(new RmaExpired(AT, 14));
            return history;
        }
        if (status == RmaStatus.CANCELLED || status == RmaStatus.CLOSED) {
            history.add(new RmaCancelled("customer", AT, null));
            if (status == RmaStatus.CLOSED) history.add(new RmaClosed(ClosureReason.CANCELLED_BY_CUSTOMER, AT));
            return history;
        }
        history.add(new LabelIssued("label", "tracking", "label.pdf", AT));
        if (status == RmaStatus.LABEL_ISSUED) return history;
        history.add(new PickupScheduled("pickup", LocalDate.of(2026, 10, 2)));
        if (status == RmaStatus.PICKUP_SCHEDULED) return history;
        history.add(new ParcelInTransit(AT));
        if (status == RmaStatus.IN_TRANSIT) return history;
        if (status == RmaStatus.LOST) {
            history.add(new RmaLost("IN_TRANSIT", AT));
            return history;
        }
        history.add(new ParcelReceived(AT, "warehouse"));
        if (status == RmaStatus.RECEIVED) return history;
        history.add(new InspectionStarted("inspector", AT));
        if (status == RmaStatus.UNDER_INSPECTION) return history;
        Rma rma = Rma.fromHistory(history);
        history.addAll(rma.completeInspection("inspector", AT,
            results(status == RmaStatus.REJECTED ? ItemGrade.D : ItemGrade.A),
            null, INSPECTION_POLICY, REFUND_POLICY));
        if (status == RmaStatus.REFUND_FAILED) {
            history.add(rma.failRefund(FailureCode.INVALID_ACCOUNT, "Invalid account", 1, false, AT));
        } else if (status == RmaStatus.REFUNDED) {
            history.add(rma.executeRefund("payout", rma.refundAmount(), 1, AT));
        }
        return history;
    }

    private static ClosureReason closureReason(RmaStatus status) {
        return switch (status) {
            case REFUNDED -> ClosureReason.REFUND_COMPLETED;
            case REJECTED -> ClosureReason.REJECTED_AFTER_INSPECTION;
            case EXPIRED -> ClosureReason.EXPIRED_NO_SHIPMENT;
            case LOST -> ClosureReason.PARCEL_LOST;
            case CANCELLED -> ClosureReason.CANCELLED_BY_CUSTOMER;
            case REFUND_FAILED -> ClosureReason.CLOSED_BY_OPERATOR;
            default -> throw new IllegalArgumentException("Not a terminal outcome: " + status);
        };
    }

    private static RmaRequested requested(RmaReason reason, List<RequestedItem> items) {
        return Rma.request(UUID.fromString("018f0000-0000-7000-8000-000000000001"),
            "RMA-2026-000001", "order", "customer", AT.minusSeconds(86400),
            reason, null, items, PLN, AT, 14);
    }

    private static RequestedItem item(String id, String price) {
        return new RequestedItem(id, "sku", "name", 1, money(price));
    }

    private static List<InspectionResult> results(ItemGrade grade) {
        return List.of(new InspectionResult("item", grade, null, null));
    }

    private static Money money(String amount) {
        return new Money(new BigDecimal(amount), PLN);
    }
}
