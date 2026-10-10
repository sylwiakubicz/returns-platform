package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.rma.returns.domain.Money;

class RefundExecutedTest {

    private static final Currency PLN = Currency.getInstance("PLN");

    private static final Money AMOUNT = new Money(new BigDecimal("15.00"), PLN);

    private static final Instant EXECUTED_AT = Instant.parse("2026-09-20T15:00:00Z");

    @Test
    void shouldCreateRefundExecuted() {
        RefundExecuted event = new RefundExecuted("payout-1", AMOUNT, 1, EXECUTED_AT);

        assertAll(
                () -> assertThat(event.payoutId()).isEqualTo("payout-1"),
                () -> assertThat(event.amount()).isEqualTo(AMOUNT),
                () -> assertThat(event.attempt()).isEqualTo(1),
                () -> assertThat(event.executedAt()).isEqualTo(EXECUTED_AT));
    }

    @Test
    void shouldRejectNullPayoutId() {
        assertThatThrownBy(() -> new RefundExecuted(null, AMOUNT, 1, EXECUTED_AT))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void shouldRejectBlankPayoutId(String payoutId) {
        assertThatThrownBy(() -> new RefundExecuted(payoutId, AMOUNT, 1, EXECUTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> new RefundExecuted("payout-1", null, 1, EXECUTED_AT))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectAttemptBelowOne(int attempt) {
        assertThatThrownBy(() -> new RefundExecuted("payout-1", AMOUNT, attempt, EXECUTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullExecutedAt() {
        assertThatThrownBy(() -> new RefundExecuted("payout-1", AMOUNT, 1, null))
                .isInstanceOf(NullPointerException.class);
    }
}
