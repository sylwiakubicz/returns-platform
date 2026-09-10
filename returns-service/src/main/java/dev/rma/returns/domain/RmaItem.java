package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.ItemGrade;

import java.util.List;

public record RmaItem(
    String itemId,
    String sku,
    String name,
    int quantity,
    Money unitPrice,
    ItemGrade grade,
    String inspectionNote,
    List<String> photoKeys,
    int deductionPercent,
    Money refundableAmount
) {
    public RmaItem {
        photoKeys = List.copyOf(photoKeys);
    }
}
