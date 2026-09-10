package dev.rma.returns.domain;

import java.util.List;

public record InspectionResult(
    String itemId,
    ItemGrade grade,
    String note,
    List<String> photoKeys
) {
    public InspectionResult {
        photoKeys = List.copyOf(photoKeys);
    }
}
