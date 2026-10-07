package dev.rma.returns.domain.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionPolicyTest {
    @Test
    void shouldRejectNullThreshold() {
        assertThatThrownBy(() -> new InspectionPolicy(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("supervisorApprovalThreshold must not be null");
    }

    @Test
    void shouldRejectNegativeThreshold() {
        assertThatThrownBy(() -> new InspectionPolicy(new BigDecimal("-0.01")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("supervisorApprovalThreshold must not be negative");
    }

    @Test
    void shouldRejectThresholdRequiringRounding() {
        assertThatThrownBy(() -> new InspectionPolicy(new BigDecimal("500.001")))
            .isInstanceOf(ArithmeticException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "500", "500.00", "500.000"})
    void shouldAcceptThresholdAndNormalizeScale(String amount) {
        InspectionPolicy policy = new InspectionPolicy(new BigDecimal(amount));

        assertThat(policy.supervisorApprovalThreshold()).isEqualByComparingTo(amount);
        assertThat(policy.supervisorApprovalThreshold().scale()).isEqualTo(2);
    }
}
