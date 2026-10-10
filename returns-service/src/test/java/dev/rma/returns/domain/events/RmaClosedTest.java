package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.rma.returns.domain.enums.ClosureReason;

class RmaClosedTest {
    private static final UUID RMA_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    @Test
    void shouldRejectNullClosureReason() {
        Instant closedAt = Instant.parse("2026-09-21T12:00:00Z");

        assertThatThrownBy(() -> new RmaClosed(RMA_ID, null, closedAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("closureReason must not be null");
    }

    @Test
    void shouldRejectNullClosedAt() {
        assertThatThrownBy(() -> new RmaClosed(RMA_ID, ClosureReason.REFUND_COMPLETED, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("closedAt must not be null");
    }
}
