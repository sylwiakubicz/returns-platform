package dev.rma.returns.domain.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RmaLostTest {
    @Test
    void shouldRejectNullLastKnownStatus() {
        Instant declaredLostAt = Instant.parse("2026-09-26T13:00:00Z");

        assertThatThrownBy(() -> new RmaLost(null, declaredLostAt))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("lastKnownStatus must not be null");
    }

    @Test
    void shouldRejectNullDeclaredLostAt() {
        assertThatThrownBy(() -> new RmaLost("IN_TRANSIT", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("declaredLostAt must not be null");
    }
}
