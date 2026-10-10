package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.rma.returns.domain.ItemRefundDecision;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.enums.RefundDecision;

class RefundDecidedTest {
    private static final Currency PLN = Currency.getInstance("PLN");

    private static final Money REFUND_AMOUNT = new Money(new BigDecimal("100.00"), PLN);

    private static final Money DEDUCTION_TOTAL = new Money(new BigDecimal("15.00"), PLN);

    private static final Instant DECIDED_AT = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void shouldRejectNullDecision() {
        assertThatThrownBy(() -> new RefundDecided(null, REFUND_AMOUNT, DEDUCTION_TOTAL, List.of(), null, DECIDED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("decision must not be null");
    }

    @Test
    void shouldRejectNullRefundAmount() {
        assertThatThrownBy(() ->
                        new RefundDecided(RefundDecision.FULL, null, DEDUCTION_TOTAL, List.of(), null, DECIDED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("refundAmount must not be null");
    }

    @Test
    void shouldRejectNullDeductionTotal() {
        assertThatThrownBy(
                        () -> new RefundDecided(RefundDecision.FULL, REFUND_AMOUNT, null, List.of(), null, DECIDED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("deductionTotal must not be null");
    }

    @Test
    void shouldRejectNullItemDecisions() {
        assertThatThrownBy(() ->
                        new RefundDecided(RefundDecision.FULL, REFUND_AMOUNT, DEDUCTION_TOTAL, null, null, DECIDED_AT))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullDecidedAt() {
        assertThatThrownBy(() ->
                        new RefundDecided(RefundDecision.FULL, REFUND_AMOUNT, DEDUCTION_TOTAL, List.of(), null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("decidedAt must not be null");
    }

    @Test
    void shouldAllowNullRejectionReasonForFullDecision() {
        RefundDecided event =
                new RefundDecided(RefundDecision.FULL, REFUND_AMOUNT, DEDUCTION_TOTAL, List.of(), null, DECIDED_AT);

        assertThat(event.rejectionReason()).isNull();
    }

    @Test
    void shouldAllowNullRejectionReasonForPartialDecision() {
        RefundDecided event =
                new RefundDecided(RefundDecision.PARTIAL, REFUND_AMOUNT, DEDUCTION_TOTAL, List.of(), null, DECIDED_AT);

        assertThat(event.rejectionReason()).isNull();
    }

    @Test
    void shouldRequireRejectionReasonForRejectedDecision() {
        assertThatThrownBy(() -> new RefundDecided(
                        RefundDecision.REJECTED, REFUND_AMOUNT, DEDUCTION_TOTAL, List.of(), null, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejectionReason must be provided for rejected decision");
    }

    @Test
    void shouldRejectBlankRejectionReasonForRejectedDecision() {
        assertThatThrownBy(() -> new RefundDecided(
                        RefundDecision.REJECTED, REFUND_AMOUNT, DEDUCTION_TOTAL, List.of(), "   ", DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejectionReason must be provided for rejected decision");
    }

    @Test
    void shouldAllowRejectionReasonForRejectedDecision() {
        RefundDecided event = new RefundDecided(
                RefundDecision.REJECTED, REFUND_AMOUNT, DEDUCTION_TOTAL, List.of(), "All items rejected", DECIDED_AT);

        assertThat(event.rejectionReason()).isEqualTo("All items rejected");
    }

    @Test
    void shouldRejectRejectionReasonForFullDecision() {
        assertThatThrownBy(() -> new RefundDecided(
                        RefundDecision.FULL, REFUND_AMOUNT, DEDUCTION_TOTAL, List.of(), "reason", DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejectionReason must be null for non-rejected decision");
    }

    @Test
    void shouldRejectRejectionReasonForPartialDecision() {
        assertThatThrownBy(() -> new RefundDecided(
                        RefundDecision.PARTIAL, REFUND_AMOUNT, DEDUCTION_TOTAL, List.of(), "reason", DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejectionReason must be null for non-rejected decision");
    }

    @Test
    void shouldDefensivelyCopyItemDecisions() {
        List<ItemRefundDecision> decisions = new ArrayList<>();

        RefundDecided event =
                new RefundDecided(RefundDecision.FULL, REFUND_AMOUNT, DEDUCTION_TOTAL, decisions, null, DECIDED_AT);

        decisions.add(null);

        assertThat(event.itemDecisions()).isEmpty();
    }

    @Test
    void shouldExposeImmutableItemDecisions() {
        RefundDecided event =
                new RefundDecided(RefundDecision.FULL, REFUND_AMOUNT, DEDUCTION_TOTAL, List.of(), null, DECIDED_AT);

        assertThatThrownBy(() -> event.itemDecisions().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
