package dev.rma.returns.domain;


import dev.rma.returns.domain.enums.ItemGrade;

public record ItemRefundDecision(
    String itemId,
    ItemGrade grade,
    int deductionPercent,
    Money refundableAmount
) {
}
