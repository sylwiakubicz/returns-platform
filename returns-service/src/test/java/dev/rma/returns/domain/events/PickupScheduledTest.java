package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PickupScheduledTest {

    @Test
    void shouldRejectNullPickupId() {
        LocalDate scheduledFor = LocalDate.parse("2026-09-15");

        assertThatThrownBy(() -> new PickupScheduled(null, scheduledFor))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("pickupId must not be null");
    }

    @Test
    void shouldRejectNullScheduledFor() {
        assertThatThrownBy(() -> new PickupScheduled("pickup-id-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("scheduledFor must not be null");
    }
}
