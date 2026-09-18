package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.RmaReason;
import dev.rma.returns.domain.enums.RmaStatus;
import dev.rma.returns.domain.events.RmaRequested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RmaTest {

    private static final UUID RMA_ID =
        UUID.fromString("018f0000-0000-7000-8000-000000000001");

    private static final Instant REQUESTED_AT =
        Instant.parse("2026-09-10T12:00:00Z");

    private static final Instant DELIVERED_AT =
        Instant.parse("2026-09-01T12:00:00Z");

    private static final Currency PLN = Currency.getInstance("PLN");

    @Test
    void shouldCreateRequestedRma() {
        List<RequestedItem> items = List.of(validItem());

        RmaRequested event = new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            DELIVERED_AT,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            PLN,
            items
        );

        assertThat(event.rmaId()).isEqualTo(RMA_ID);
        assertThat(event.businessNumber()).isEqualTo("RMA-2026-000001");
        assertThat(event.orderId()).isEqualTo("order-1");
        assertThat(event.customerId()).isEqualTo("customer-1");
        assertThat(event.orderDeliveredAt()).isEqualTo(DELIVERED_AT);
        assertThat(event.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(event.reason()).isEqualTo(RmaReason.CHANGED_MIND);
        assertThat(event.currency()).isEqualTo(PLN);
        assertThat(event.items()).containsExactlyElementsOf(items);
    }

    private RmaRequested validRmaRequested() {
        List<RequestedItem> items = List.of(validItem());

        return new RmaRequested(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            DELIVERED_AT,
            REQUESTED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            PLN,
            items
        );
    }

    private RequestedItem validItem() {
        Money money = new Money(new BigDecimal(30), PLN);
        return new RequestedItem(
            "item-1",
            "sku-1",
            "book",
            1,
            money
        );
    }


}
