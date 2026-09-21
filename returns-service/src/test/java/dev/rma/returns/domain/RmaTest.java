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
import static org.junit.jupiter.api.Assertions.assertAll;

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
        List<RequestedItem> items = List.of(validRequestedItem());

        RmaRequested event = Rma.request(
            RMA_ID,
            "RMA-2026-000001",
            "order-1",
            "customer-1",
            DELIVERED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            items,
            PLN,
            REQUESTED_AT,
            14);

        assertAll(
            () -> assertThat(event.rmaId()).isEqualTo(RMA_ID),
            () -> assertThat(event.businessNumber()).isEqualTo("RMA-2026-000001"),
            () -> assertThat(event.orderId()).isEqualTo("order-1"),
            () -> assertThat(event.customerId()).isEqualTo("customer-1"),
            () -> assertThat(event.customerNote()).isEqualTo("Nie pasuje"),
            () -> assertThat(event.orderDeliveredAt()).isEqualTo(DELIVERED_AT),
            () -> assertThat(event.requestedAt()).isEqualTo(REQUESTED_AT),
            () -> assertThat(event.reason()).isEqualTo(RmaReason.CHANGED_MIND),
            () -> assertThat(event.currency()).isEqualTo(PLN),
            () -> assertThat(event.items()).containsExactlyElementsOf(items)
        );
    }

    @Test
    void shouldRebuildRequestedStateFromRmaRequestedEvent() {
        // given
        RmaRequested event = validRmaRequested();

        // when
        Rma rma = Rma.fromHistory(List.of(event));

        // then
        assertAll(
            () -> assertThat(rma.status()).isEqualTo(RmaStatus.REQUESTED),
            () -> assertThat(rma.version()).isEqualTo(1),
            () -> assertThat(rma.rmaId()).isEqualTo(event.rmaId()),
            () -> assertThat(rma.businessNumber()).isEqualTo(event.businessNumber()),
            () -> assertThat(rma.orderId()).isEqualTo(event.orderId()),
            () -> assertThat(rma.customerId()).isEqualTo(event.customerId()),
            () -> assertThat(rma.currency()).isEqualTo(event.currency())
        );

        assertThat(rma.items()).hasSize(1);
        RmaItem item = rma.items().getFirst();
        assertAll(
            () -> assertThat(item.itemId()).isEqualTo("item-1"),
            () -> assertThat(item.sku()).isEqualTo("sku-1"),
            () -> assertThat(item.name()).isEqualTo("book"),
            () -> assertThat(item.unitPrice()).isEqualTo(new Money(new BigDecimal("30.00"), PLN)),
            () -> assertThat(item.grade()).isNull(),
            () -> assertThat(item.inspectionNote()).isNull(),
            () -> assertThat(item.photoKeys()).isEmpty(),
            () -> assertThat(item.deductionPercent()).isNull(),
            () -> assertThat(item.refundableAmount()).isNull()
        );
    }

    @Test
    void shouldRejectRequestWhenReturnWindowExpired() {}

    @Test
    void shouldAcceptRequestOnLastDayOfReturnWindow() {}

    @Test
    void shouldRejectRequestWithoutItems() {}

    @Test
    void shouldRejectRequestWithMixedCurrencies() {}

    @Test
    void shouldRejectEmptyHistory() {}

    @Test
    void shouldRequireRmaRequestedAsFirstEvent() {}

    private RmaRequested validRmaRequested() {
        List<RequestedItem> items = List.of(validRequestedItem());

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
