package dev.rma.returns.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import dev.rma.returns.domain.InspectionResult;
import dev.rma.returns.domain.Money;
import dev.rma.returns.domain.RequestedItem;
import dev.rma.returns.domain.Rma;
import dev.rma.returns.domain.RmaState;
import dev.rma.returns.domain.enums.*;
import dev.rma.returns.domain.policy.InspectionPolicy;
import dev.rma.returns.domain.policy.RefundPolicy;

class RmaStateJsonMapperTest {
    private final RmaStateJsonMapper mapper =
            new RmaStateJsonMapper(Jackson2ObjectMapperBuilder.json().build());

    @Test
    void shouldRoundTripInitialAndCompletedStatesWithNestedValues() {
        Instant at = Instant.parse("2026-10-01T12:00:00.123456Z");
        Currency currency = Currency.getInstance("PLN");
        Money price = new Money(new BigDecimal("100.00"), currency);
        var requested = Rma.request(
                UUID.randomUUID(),
                "RMA-2026-000001",
                "order",
                "customer",
                at.minusSeconds(86400),
                RmaReason.CHANGED_MIND,
                "Customer note",
                List.of(new RequestedItem("item", "sku", "Hobbit", 1, price)),
                currency,
                at,
                14);
        Rma rma = Rma.fromHistory(List.of(requested));
        assertRoundTrip(rma.state());

        rma.issueLabel("label", "tracking", "label.pdf", at);
        rma.schedulePickup("pickup", LocalDate.of(2026, 10, 3), at);
        rma.markParcelInTransit(at);
        rma.receive(at, "warehouse");
        rma.startInspection("inspector", at);
        rma.completeInspection(
                "inspector",
                at,
                List.of(new InspectionResult("item", ItemGrade.B, "Inspection note", List.of("photo.jpg"))),
                null,
                new InspectionPolicy(new BigDecimal("500.00")),
                new RefundPolicy(0, 15, 40, new BigDecimal("15.00")));
        rma.failRefund(FailureCode.TIMEOUT, "timeout", 1, true, at);
        assertRoundTrip(rma.state());
        rma.retryRefund(at);
        rma.executeRefund("payout", rma.refundAmount(), 2, at);
        rma.close(ClosureReason.REFUND_COMPLETED, at);
        assertRoundTrip(rma.state());
    }

    @Test
    void shouldRejectMalformedJsonAndJsonNull() {
        assertThatThrownBy(() -> mapper.fromJson("{broken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot deserialize RMA state")
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> mapper.fromJson("null"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RMA state payload must not be JSON null");
    }

    private void assertRoundTrip(RmaState original) {
        RmaState restored = mapper.fromJson(mapper.toJson(original));
        assertThat(restored).isEqualTo(original);
        assertThat(Rma.fromState(restored, List.of()).state()).isEqualTo(original);
    }
}
