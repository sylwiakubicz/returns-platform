package dev.rma.returns.domain.events;

import dev.rma.returns.domain.enums.ClosureReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RmaClosedTest {
    @Test
    void shouldRejectNullClosureReason() {
        Instant closedAt = Instant.parse("2026-09-21T12:00:00Z");

        assertThatThrownBy(() -> new RmaClosed(null, closedAt))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("closureReason must not be null");
    }

    @Test
    void shouldRejectNullClosedAt() {
        assertThatThrownBy(() -> new RmaClosed(ClosureReason.REFUND_COMPLETED, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("closedAt must not be null");
    }
}
