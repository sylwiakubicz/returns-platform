package dev.rma.returns.domain;

import java.util.Objects;

import dev.rma.returns.domain.enums.RmaErrorCode;
import dev.rma.returns.domain.exceptions.RmaDomainException;

public record RequestedItem(String itemId, String sku, String name, int quantity, Money unitPrice) {
    public RequestedItem {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(sku, "sku must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");

        if (quantity < 1) {
            throw new RmaDomainException(RmaErrorCode.INVALID_QUANTITY, "Quantity must be greater than or equal to 1");
        }
    }
}
