package dev.rma.returns.domain;


public record RequestedItem(
    String itemId,
    String sku,
    String name,
    Integer quantity,
    Money unitPrice
) {
}
