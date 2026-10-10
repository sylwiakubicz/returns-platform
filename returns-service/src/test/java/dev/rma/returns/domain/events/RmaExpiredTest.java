package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RmaExpiredTest {
    @Test
    void shouldRejectNullExpiredAt() {
        assertThatThrownBy(() -> new RmaExpired(null, 14))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("expiredAt must not be null");
    }

    @Test
    void shouldRejectNegativeDaysSinceRequest() {
        Instant expiredAt = Instant.parse("2026-09-24T12:00:00Z");

        assertThatThrownBy(() -> new RmaExpired(expiredAt, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("daysSinceRequest must be greater or equal to 0");
    }
}
