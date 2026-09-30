package dev.rma.returns.domain.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InspectionStartedTest {
    private static final Instant STARTED_AT =
        Instant.parse("2026-09-17T13:00:00Z");

    @Test
    void shouldRejectNullInspectorId() {
        assertThatThrownBy(() -> new InspectionStarted(
            null,
            STARTED_AT))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("inspectorId must not be null");
    }

    @Test
    void shouldRejectNullStartedAt() {
        assertThatThrownBy(() -> new InspectionStarted(
            "id-1",
            null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("startedAt must not be null");
    }
}
