package dev.rma.returns.domain;


import dev.rma.returns.domain.enums.ItemGrade;

import java.util.Objects;

public record ItemRefundDecision(
    String itemId,
    ItemGrade grade,
    int deductionPercent,
    Money refundableAmount
) {
    public ItemRefundDecision {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(grade, "grade must not be null");
        Objects.requireNonNull(refundableAmount, "refundableAmount must not be null");

        if (deductionPercent < 0 || deductionPercent > 100) {
            throw new IllegalArgumentException("Deduction percent must be between 0 to 100");
        }
    }
}
