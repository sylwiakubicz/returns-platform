package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class ParcelReceivedTest {
    private static final UUID RMA_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    @Test
    void shouldRejectNullReceivedAt() {
        assertThatThrownBy(() -> new ParcelReceived(RMA_ID, null, "W1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("receivedAt must not be null");
    }

    @Test
    void shouldRejectNullWarehouseId() {
        Instant RECEIVED_AT = Instant.parse("2026-09-01T12:00:00Z");

        assertThatThrownBy(() -> new ParcelReceived(RMA_ID, RECEIVED_AT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("warehouseId must not be null");
    }
}
