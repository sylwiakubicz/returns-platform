package dev.rma.returns.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record RefundPolicy(
    int gradeAPercent,
    int gradeBPercent,
    int gradeCPercent,
    BigDecimal returnShippingFee
) {
    public RefundPolicy {
        validatePercent(gradeAPercent, "gradeAPercent");
        validatePercent(gradeBPercent, "gradeBPercent");
        validatePercent(gradeCPercent, "gradeCPercent");
        Objects.requireNonNull(returnShippingFee, "returnShippingFee must not be null");
        if (returnShippingFee.signum() < 0) {
            throw new IllegalArgumentException("returnShippingFee must not be negative");
        }
        returnShippingFee = returnShippingFee.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static void validatePercent(int percent, String name) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
    }
}
