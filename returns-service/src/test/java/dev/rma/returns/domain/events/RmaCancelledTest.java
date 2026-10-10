package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RmaCancelledTest {
    private static final UUID RMA_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");
    private static final Instant CANCELLED_AT = Instant.parse("2026-09-11T12:00:00Z");

    @Test
    void shouldRejectNullCancelledBy() {
        assertThatThrownBy(() -> new RmaCancelled(RMA_ID, null, CANCELLED_AT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cancelledBy must not be null");
    }

    @Test
    void shouldRejectNullCancelledAt() {
        assertThatThrownBy(() -> new RmaCancelled(RMA_ID, "customer-1", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cancelledAt must not be null");
    }

    @Test
    void shouldAllowNullReason() {
        assertThatCode(() -> new RmaCancelled(RMA_ID, "customer-1", CANCELLED_AT, null))
                .doesNotThrowAnyException();
    }
}
