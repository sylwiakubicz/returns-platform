package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.ItemGrade;

import java.util.List;
import java.util.Objects;

public record InspectionResult(
    String itemId,
    ItemGrade grade,
    String note,
    List<String> photoKeys
) {
    public InspectionResult {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(grade, "grade must not be null");

        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("note can not exceed 500 chars");
        }

        photoKeys = photoKeys == null
            ? List.of()
            : List.copyOf(photoKeys);
    }
}
