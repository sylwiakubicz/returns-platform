package dev.rma.returns.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RefundPolicyTest {
    @ParameterizedTest
    @CsvSource({"-1,15,40", "101,15,40", "0,-1,40", "0,101,40", "0,15,-1", "0,15,101"})
    void shouldRejectPercentOutsideAllowedRange(int gradeA, int gradeB, int gradeC) {
        assertThatThrownBy(() -> new RefundPolicy(gradeA, gradeB, gradeC, new BigDecimal("15.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void shouldAcceptPercentAtBothBoundaries(int percent) {
        RefundPolicy policy = new RefundPolicy(percent, percent, percent, BigDecimal.ZERO);

        assertThat(policy.gradeAPercent()).isEqualTo(percent);
        assertThat(policy.gradeBPercent()).isEqualTo(percent);
        assertThat(policy.gradeCPercent()).isEqualTo(percent);
    }

    @Test
    void shouldRejectNullShippingFee() {
        assertThatThrownBy(() -> new RefundPolicy(0, 15, 40, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("returnShippingFee must not be null");
    }

    @Test
    void shouldRejectNegativeShippingFee() {
        assertThatThrownBy(() -> new RefundPolicy(0, 15, 40, new BigDecimal("-15.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("returnShippingFee must not be negative");
    }

    @Test
    void shouldRejectShippingFeeRequiringRounding() {
        assertThatThrownBy(() -> new RefundPolicy(0, 15, 40, new BigDecimal("15.001")))
                .isInstanceOf(ArithmeticException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "15", "15.00", "15.000"})
    void shouldAcceptShippingFeeAndNormalizeScale(String amount) {
        RefundPolicy policy = new RefundPolicy(0, 15, 40, new BigDecimal(amount));

        assertThat(policy.returnShippingFee()).isEqualByComparingTo(amount);
        assertThat(policy.returnShippingFee().scale()).isEqualTo(2);
    }
}
