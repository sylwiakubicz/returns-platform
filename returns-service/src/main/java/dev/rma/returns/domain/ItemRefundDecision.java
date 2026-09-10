package dev.rma.returns.domain;


public record ItemRefundDecision(
    String itemId,
    ItemGrade grade,
    int deductionPercent,
    Money refundableAmount
) {
}
