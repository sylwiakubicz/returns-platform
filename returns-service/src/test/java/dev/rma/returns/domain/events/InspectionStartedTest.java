package dev.rma.returns.domain.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class InspectionStartedTest {
    private static final UUID RMA_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");
    private static final Instant STARTED_AT = Instant.parse("2026-09-17T13:00:00Z");

    @Test
    void shouldRejectNullInspectorId() {
        assertThatThrownBy(() -> new InspectionStarted(RMA_ID, null, STARTED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("inspectorId must not be null");
    }

    @Test
    void shouldRejectNullStartedAt() {
        assertThatThrownBy(() -> new InspectionStarted(RMA_ID, "id-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("startedAt must not be null");
    }
}
