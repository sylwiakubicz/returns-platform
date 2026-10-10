package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ParcelInTransitTest {
    private static final UUID RMA_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    @Test
    void shouldRejectNullCollectedAt() {
        assertThatThrownBy(() -> new ParcelInTransit(RMA_ID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("collectedAt must not be null");
    }
}
