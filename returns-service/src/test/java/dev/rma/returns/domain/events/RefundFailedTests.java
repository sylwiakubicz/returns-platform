package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.rma.returns.domain.enums.FailureCode;

class RefundFailedTest {

    private static final Instant FAILED_AT = Instant.parse("2026-09-20T15:00:00Z");

    @Test
    void shouldCreateRefundFailed() {
        RefundFailed event = new RefundFailed(FailureCode.TIMEOUT, "Payment provider timeout", 1, true, FAILED_AT);

        assertAll(
                () -> assertThat(event.failureCode()).isEqualTo(FailureCode.TIMEOUT),
                () -> assertThat(event.failureMessage()).isEqualTo("Payment provider timeout"),
                () -> assertThat(event.attempt()).isEqualTo(1),
                () -> assertThat(event.retryable()).isTrue(),
                () -> assertThat(event.failedAt()).isEqualTo(FAILED_AT));
    }

    @Test
    void shouldRejectNullFailureCode() {
        assertThatThrownBy(() -> new RefundFailed(null, "Payment provider timeout", 1, true, FAILED_AT))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectAttemptBelowOne(int attempt) {
        assertThatThrownBy(() ->
                        new RefundFailed(FailureCode.TIMEOUT, "Payment provider timeout", attempt, true, FAILED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullFailedAt() {
        assertThatThrownBy(() -> new RefundFailed(FailureCode.TIMEOUT, "Payment provider timeout", 1, true, null))
                .isInstanceOf(NullPointerException.class);
    }
}
