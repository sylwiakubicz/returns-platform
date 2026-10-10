package dev.rma.returns.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import dev.rma.returns.domain.enums.ItemGrade;

public record RmaItem(
        String itemId,
        String sku,
        String name,
        int quantity,
        Money unitPrice,
        ItemGrade grade,
        String inspectionNote,
        List<String> photoKeys,
        Integer deductionPercent,
        Money refundableAmount) {
    public RmaItem {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(sku, "sku must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");

        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be greater than or equal to 1");
        }

        if (deductionPercent != null && (deductionPercent < 0 || deductionPercent > 100)) {
            throw new IllegalArgumentException("deductionPercent must be between 0 and 100");
        }

        photoKeys = photoKeys == null ? List.of() : List.copyOf(photoKeys);
    }

    RmaItem withInspection(InspectionResult result) {
        Objects.requireNonNull(result, "result must not be null");

        if (!itemId.equals(result.itemId())) {
            throw new IllegalArgumentException("inspection result belongs to different item");
        }

        return new RmaItem(
                itemId,
                sku,
                name,
                quantity,
                unitPrice,
                result.grade(),
                result.note(),
                result.photoKeys(),
                deductionPercent,
                refundableAmount);
    }

    RmaItem withRefundDecision(ItemRefundDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");

        if (!itemId.equals(decision.itemId())) {
            throw new IllegalArgumentException("refund decision belongs to different item");
        }

        return new RmaItem(
                itemId,
                sku,
                name,
                quantity,
                unitPrice,
                grade,
                inspectionNote,
                photoKeys,
                decision.deductionPercent(),
                decision.refundableAmount());
    }

    public BigDecimal totalValue() {
        return unitPrice.amount().multiply(new BigDecimal(quantity));
    }
}
