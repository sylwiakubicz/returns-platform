package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.RmaErrorCode;
import dev.rma.returns.domain.exceptions.RmaDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestedItemTest {
    private static final Currency PLN = Currency.getInstance("PLN");
    private static final Money MONEY = new Money(new BigDecimal("20.00"), PLN);
    private static final String ITEM_ID = "id-1";
    private static final String SKU = "sku-123";
    private static final String ITEM_NAME = "item";


    @Test
    void shouldRejectNullItemId() {
        assertThatThrownBy(() -> new RequestedItem(
            null,
            SKU,
            ITEM_NAME,
            1,
            MONEY
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("itemId must not be null");
    }

    @Test
    void shouldRejectNullSku() {
        assertThatThrownBy(() -> new RequestedItem(
            ITEM_ID,
            null,
            ITEM_NAME,
            1,
            MONEY
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("sku must not be null");
    }

    @Test
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new RequestedItem(
            ITEM_ID,
            SKU,
            null,
            1,
            MONEY
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("name must not be null");
    }

    @Test
    void shouldRejectNullUnitPrice() {
        assertThatThrownBy(() -> new RequestedItem(
            ITEM_ID,
            SKU,
            ITEM_NAME,
            1,
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("unitPrice must not be null");
    }

    @Test
    void shouldRejectZeroQuantity() {
        assertThatThrownBy(() -> new RequestedItem(
            ITEM_ID,
            SKU,
            ITEM_NAME,
            0,
            MONEY
        ))
            .isInstanceOfSatisfying(RmaDomainException.class,
                exception -> assertThat(exception.code())
                    .isEqualTo(RmaErrorCode.INVALID_QUANTITY));
    }

    @Test
    void shouldRejectNegativeQuantity() {
        assertThatThrownBy(() -> new RequestedItem(
            ITEM_ID,
            SKU,
            ITEM_NAME,
            -1,
            MONEY
        ))
            .isInstanceOfSatisfying(RmaDomainException.class,
        exception -> assertThat(exception.code())
            .isEqualTo(RmaErrorCode.INVALID_QUANTITY));
    }

    @Test
    void shouldAcceptQuantityOne() {
        RequestedItem requestedItem = new RequestedItem(
            ITEM_ID,
            SKU,
            ITEM_NAME,
            1,
            MONEY
        );

        assertThat(requestedItem.quantity()).isEqualTo(1);
    }
}
