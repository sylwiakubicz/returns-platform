package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ParcelInTransitTest {
    @Test
    void shouldRejectNullCollectedAt() {
        assertThatThrownBy(() -> new ParcelInTransit(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("collectedAt must not be null");
    }
}
