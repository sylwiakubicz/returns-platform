package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RmaLostTest {
    private static final UUID RMA_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    @Test
    void shouldRejectNullLastKnownStatus() {
        Instant declaredLostAt = Instant.parse("2026-09-26T13:00:00Z");

        assertThatThrownBy(() -> new RmaLost(RMA_ID, null, declaredLostAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("lastKnownStatus must not be null");
    }

    @Test
    void shouldRejectNullDeclaredLostAt() {
        assertThatThrownBy(() -> new RmaLost(RMA_ID, "IN_TRANSIT", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("declaredLostAt must not be null");
    }
}
