package dev.rma.returns.domain;

import dev.rma.returns.domain.enums.RmaErrorCode;
import dev.rma.returns.domain.enums.RmaReason;
import dev.rma.returns.domain.enums.RmaStatus;
import dev.rma.returns.domain.events.DomainEvent;
import dev.rma.returns.domain.events.LabelIssued;
import dev.rma.returns.domain.events.RmaRequested;
import dev.rma.returns.domain.exceptions.RmaDomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

public class RmaTest {

    private static final UUID RMA_ID =
        UUID.fromString("018f0000-0000-7000-8000-000000000001");

    private static final Instant REQUESTED_AT =
        Instant.parse("2026-09-10T12:00:00Z");

    private static final Instant DELIVERED_AT =
        Instant.parse("2026-09-01T12:00:00Z");

    private static final Currency PLN = Currency.getInstance("PLN");
    private static final Currency EUR = Currency.getInstance("EUR");

    private static final String BUSINESS_NUMBER = "RMA-2026-000001";
    private static final String ORDER_ID = "order-1";
    private static final String CUSTOMER_ID = "customer-1";

    private static final int RETURN_WINDOW_DAYS = 14;

    @Test
    void shouldCreateRequestedRma() {
        List<RequestedItem> items = List.of(validRequestedItem());

        RmaRequested event = Rma.request(
            RMA_ID,
            BUSINESS_NUMBER,
            ORDER_ID,
            CUSTOMER_ID,
            DELIVERED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            items,
            PLN,
            REQUESTED_AT,
            RETURN_WINDOW_DAYS);

        assertAll(
            () -> assertThat(event.rmaId()).isEqualTo(RMA_ID),
            () -> assertThat(event.businessNumber()).isEqualTo(BUSINESS_NUMBER),
            () -> assertThat(event.orderId()).isEqualTo(ORDER_ID),
            () -> assertThat(event.customerId()).isEqualTo(CUSTOMER_ID),
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
    void shouldRejectRequestWhenReturnWindowExpired() {
        Instant requestedAt = Instant.parse("2026-09-16T12:00:00Z");
        List<RequestedItem> items = List.of(validRequestedItem());

        assertThatThrownBy(() -> Rma.request(
            RMA_ID,
            BUSINESS_NUMBER,
            ORDER_ID,
            CUSTOMER_ID,
            DELIVERED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            items,
            PLN,
            requestedAt,
            RETURN_WINDOW_DAYS
        ))
            .isInstanceOf(RmaDomainException.class)
            .satisfies(exception ->
                assertThat(((RmaDomainException) exception).code())
                    .isEqualTo(RmaErrorCode.RETURN_WINDOW_EXPIRED)
            );
    }

    @Test
    void shouldAcceptRequestOnLastDayOfReturnWindow() {
        Instant requestedAt = Instant.parse("2026-09-15T12:00:00Z");
        List<RequestedItem> items = List.of(validRequestedItem());

        RmaRequested event = Rma.request(
            RMA_ID,
            BUSINESS_NUMBER,
            ORDER_ID,
            CUSTOMER_ID,
            DELIVERED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            items,
            PLN,
            requestedAt,
            RETURN_WINDOW_DAYS
        );

        assertThat(event).isNotNull();
    }

    @Test
    void shouldRejectRequestWithoutItems() {
        List<RequestedItem> items = List.of();

        assertThatThrownBy(() -> Rma.request(
            RMA_ID,
            BUSINESS_NUMBER,
            ORDER_ID,
            CUSTOMER_ID,
            DELIVERED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            items,
            PLN,
            REQUESTED_AT,
            RETURN_WINDOW_DAYS
        )).isInstanceOfSatisfying(
            RmaDomainException.class,
            exception -> assertThat(((RmaDomainException) exception).code())
                .isEqualTo(RmaErrorCode.NO_ITEMS)
        );
    }

    @Test
    void shouldRejectRequestWithMixedCurrencies() {
        Money moneyPln = new Money(new BigDecimal("30.00"), PLN);
        Money moneyEur = new Money(new BigDecimal("30.00"), EUR);

        List<RequestedItem> items = List.of(
            new RequestedItem(
                "item-1",
                "sku-1",
                "book-1",
                1,
                moneyEur),
            new RequestedItem(
                "item-2",
                "sku-2",
                "book-2",
                1,
                moneyPln)
            );

        assertThatThrownBy(() -> Rma.request(
            RMA_ID,
            BUSINESS_NUMBER,
            ORDER_ID,
            CUSTOMER_ID,
            DELIVERED_AT,
            RmaReason.CHANGED_MIND,
            "Nie pasuje",
            items,
            PLN,
            REQUESTED_AT,
            RETURN_WINDOW_DAYS
        ))
            .isInstanceOfSatisfying(
                RmaDomainException.class,
                exception -> assertThat(exception.code())
                    .isEqualTo(RmaErrorCode.MIXED_CURRENCIES)
            );
    }

    @Test
    void shouldRejectEmptyHistory() {
        assertThatThrownBy(() -> Rma.fromHistory(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Event history must not be empty");
    }

    @Test
    void shouldRequireRmaRequestedAsFirstEvent() {
        DomainEvent wrongFirstEvent = new LabelIssued(
            "label-1", "tracking-1", "fileKey", REQUESTED_AT);
        assertThatThrownBy(() -> Rma.fromHistory(List.of(wrongFirstEvent)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("RmaRequested must be the first event");;
    }

    private RmaRequested validRmaRequested() {
        List<RequestedItem> items = List.of(validRequestedItem());

        return new RmaRequested(
            RMA_ID,
            BUSINESS_NUMBER,
            ORDER_ID,
            CUSTOMER_ID,
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
