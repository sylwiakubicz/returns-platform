package dev.rma.returns.domain.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ParcelReceivedTest {

    @Test
    void shouldRejectNullReceivedAt() {
        assertThatThrownBy(() -> new ParcelReceived(
            null,
            "W1"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("receivedAt must not be null");
    }

    @Test
    void shouldRejectNullWarehouseId() {
        Instant RECEIVED_AT =
            Instant.parse("2026-09-01T12:00:00Z");

        assertThatThrownBy(() -> new ParcelReceived(
             RECEIVED_AT,
            null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("warehouseId must not be null");
    }
}
