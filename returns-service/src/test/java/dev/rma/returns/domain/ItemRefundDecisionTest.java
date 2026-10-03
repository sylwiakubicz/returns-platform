package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.ItemGrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemRefundDecisionTest {
    private static final Currency PLN = Currency.getInstance("PLN");

    private static final Money REFUNDABLE_AMOUNT =
        new Money(new BigDecimal("100.00"), PLN);

    @Test
    void shouldRejectNullItemId() {
        assertThatThrownBy(() -> new ItemRefundDecision(
            null,
            ItemGrade.A,
            0,
            REFUNDABLE_AMOUNT
        ))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullGrade() {
        assertThatThrownBy(() -> new ItemRefundDecision(
            "item-1",
            null,
            0,
            REFUNDABLE_AMOUNT
        ))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullRefundableAmount() {
        assertThatThrownBy(() -> new ItemRefundDecision(
            "item-1",
            ItemGrade.A,
            0,
            null
        ))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectDeductionPercentBelowZero() {
        assertThatThrownBy(() -> new ItemRefundDecision(
            "item-1",
            ItemGrade.A,
            -1,
            REFUNDABLE_AMOUNT
        ))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDeductionPercentAboveHundred() {
        assertThatThrownBy(() -> new ItemRefundDecision(
            "item-1",
            ItemGrade.A,
            101,
            REFUNDABLE_AMOUNT
        ))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowZeroDeductionPercent() {
        ItemRefundDecision decision = new ItemRefundDecision(
            "item-1",
            ItemGrade.A,
            0,
            REFUNDABLE_AMOUNT
        );

        assertThat(decision.deductionPercent()).isZero();
    }

    @Test
    void shouldAllowHundredDeductionPercent() {
        ItemRefundDecision decision = new ItemRefundDecision(
            "item-1",
            ItemGrade.C,
            100,
            REFUNDABLE_AMOUNT
        );

        assertThat(decision.deductionPercent()).isEqualTo(100);
    }
}
