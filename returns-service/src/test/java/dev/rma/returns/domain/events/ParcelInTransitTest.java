package dev.rma.returns.domain.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParcelInTransitTest {
    @Test
    void shouldRejectNullCollectedAt() {
        assertThatThrownBy(() -> new ParcelInTransit(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("collectedAt must not be null");
    }
}
