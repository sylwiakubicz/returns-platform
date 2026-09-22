package dev.rma.returns.domain.events;

import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.RequestedItem;
import dev.rma.returns.domain.enums.RmaReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RmaRequestedTest {
    private static final UUID RMA_ID =
        UUID.fromString("018f0000-0000-7000-8000-000000000001");

    private static final Instant REQUESTED_AT =
        Instant.parse("2026-09-10T12:00:00Z");

    private static final Instant DELIVERED_AT =
        Instant.parse("2026-09-01T12:00:00Z");

    private static final Currency PLN = Currency.getInstance("PLN");

    @Test
    void shouldRejectNullRmaId() {
        assertThatThrownBy(() -> new RmaRequested(
            null,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            DELIVERED_AT,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            PLN,
            List.of(validRequestedItem())
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("rmaId must not be null");
    }

    @Test
    void shouldRejectNullBusinessNumber() {
        assertThatThrownBy(() -> new RmaRequested(
            RMA_ID,
            null,
            "order-1",
            "customer-1",
            DELIVERED_AT,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            PLN,
            List.of(validRequestedItem())
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("businessNumber must not be null");
    }

    @Test
    void shouldRejectNullOrderId() {
        assertThatThrownBy(() -> new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            null,
            "customer-1",
            DELIVERED_AT,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            PLN,
            List.of(validRequestedItem())
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("orderId must not be null");
    }

    @Test
    void shouldRejectNullCustomerId() {
        assertThatThrownBy(() -> new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            null,
            DELIVERED_AT,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            PLN,
            List.of(validRequestedItem())
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("customerId must not be null");
    }

    @Test
    void shouldRejectNullOrderDeliveredAt() {
        assertThatThrownBy(() -> new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            null,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            PLN,
            List.of(validRequestedItem())
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("orderDeliveredAt must not be null");
    }

    @Test
    void shouldRejectNullRequestedAt() {
        assertThatThrownBy(() -> new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            DELIVERED_AT,
            null,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            PLN,
            List.of(validRequestedItem())
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("requestedAt must not be null");
    }

    @Test
    void shouldRejectNullReason() {
        assertThatThrownBy(() -> new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            DELIVERED_AT,
            REQUESTED_AT,
            null,
            "Nie pasuje",
            PLN,
            List.of(validRequestedItem())
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("reason must not be null");
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThatThrownBy(() -> new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            DELIVERED_AT,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            null,
            List.of(validRequestedItem())
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("currency must not be null");
    }

    @Test
    void shouldRejectNullItems() {
        assertThatThrownBy(() -> new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            DELIVERED_AT,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            PLN,
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("items must not be null");
    }

    @Test
    void shouldAllowNullCustomerNote() {
        RmaRequested event = new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            DELIVERED_AT,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            null,
            PLN,
            List.of(validRequestedItem())
        );

        assertThat(event.customerNote()).isNull();
    }

    @Test
    void shouldDefensivelyCopyItems() {
        List<RequestedItem> items = new ArrayList<>();
        items.add(validRequestedItem());

        RmaRequested event = new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            DELIVERED_AT,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            null,
            PLN,
            items
        );

        items.add(validRequestedItem());
        assertThat(event.items()).hasSize(1);

    }

    @Test
    void shouldExposeImmutableItems() {
        RmaRequested event = new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            DELIVERED_AT,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            null,
            PLN,
            List.of(validRequestedItem())
        );

        assertThatThrownBy(() ->
            event.items().add(validRequestedItem())
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    private RequestedItem validRequestedItem() {
        Money money = new Money(new BigDecimal("30.00"), PLN);
        return new RequestedItem(
            "item-1",
            "sku-1",
            "book",
            1,
            money
        );
    }
}
