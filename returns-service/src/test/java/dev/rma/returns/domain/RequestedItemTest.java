package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.RmaErrorCode;
import dev.rma.returns.domain.exceptions.RmaDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class RequestedItemTest {
    private static final Currency PLN = Currency.getInstance("PLN");
    private static final Money MONEY = new Money(new BigDecimal("20.00"), PLN);


    @Test
    void shouldRejectNullItemId() {
        assertThatThrownBy(() -> new RequestedItem(
            null,
            "sku-123",
            "item",
            1,
            MONEY
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("itemId must not be null");
    }

    @Test
    void shouldRejectNullSku() {
        assertThatThrownBy(() -> new RequestedItem(
            "id-21",
            null,
            "item",
            1,
            MONEY
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("sku must not be null");
    }

    @Test
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new RequestedItem(
            "id-1",
            "sku-123",
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
            "id-1",
            "sku-123",
            "item",
            1,
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("unitPrice must not be null");
    }

    @Test
    void shouldRejectZeroQuantity() {
        assertThatThrownBy(() -> new RequestedItem(
            "id-1",
            "sku-123",
            "item",
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
            "id-1",
            "sku-123",
            "item",
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
            "id-1",
            "sku-123",
            "item",
            1,
            MONEY
        );

        assertThat(requestedItem.quantity()).isEqualTo(1);
    }
}
