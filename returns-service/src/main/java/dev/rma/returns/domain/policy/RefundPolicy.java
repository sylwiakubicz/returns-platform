package dev.rma.returns.domain.policy;

import java.math.BigDecimal;

public record RefundPolicy(
    int gradeAPercent,
    int gradeBPercent,
    int gradeCPercent,
    BigDecimal returnShippingFee
) {
}
