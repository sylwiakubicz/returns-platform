package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RmaExpiredTest {
    private static final UUID RMA_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    @Test
    void shouldRejectNullExpiredAt() {
        assertThatThrownBy(() -> new RmaExpired(RMA_ID, null, 14))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("expiredAt must not be null");
    }

    @Test
    void shouldRejectNegativeDaysSinceRequest() {
        Instant expiredAt = Instant.parse("2026-09-24T12:00:00Z");

        assertThatThrownBy(() -> new RmaExpired(RMA_ID, expiredAt, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("daysSinceRequest must be greater or equal to 0");
    }
}
