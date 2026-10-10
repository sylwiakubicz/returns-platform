package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PickupScheduledTest {
    private static final UUID RMA_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void shouldRejectNullRmaId() {
        assertThatThrownBy(() -> new PickupScheduled(null, "pickup", LocalDate.of(2026, 10, 2), OCCURRED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rmaId must not be null");
    }

    @Test
    void shouldRejectNullOccurredAt() {
        assertThatThrownBy(() -> new PickupScheduled(RMA_ID, "pickup", LocalDate.of(2026, 10, 2), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("occurredAt must not be null");
    }

    @Test
    void shouldRejectNullPickupId() {
        LocalDate scheduledFor = LocalDate.parse("2026-09-15");

        assertThatThrownBy(() -> new PickupScheduled(RMA_ID, null, scheduledFor, OCCURRED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("pickupId must not be null");
    }

    @Test
    void shouldRejectNullScheduledFor() {
        assertThatThrownBy(() -> new PickupScheduled(RMA_ID, "pickup-id-1", null, OCCURRED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("scheduledFor must not be null");
    }
}
