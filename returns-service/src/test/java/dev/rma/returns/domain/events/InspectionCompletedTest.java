package dev.rma.returns.domain.events;

import dev.rma.returns.domain.InspectionResult;
import dev.rma.returns.domain.enums.ItemGrade;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InspectionCompletedTest {
    private static final Instant COMPLETED_AT =
        Instant.parse("2026-09-18T13:00:00Z");
    private static final String INSPECTOR_ID = "inspector-1";

    private static final InspectionResult RESULT =
        new InspectionResult(
            "item-1",
            ItemGrade.A,
            "Item looks good",
            List.of()
        );

    @Test
    void shouldRejectNullInspectorId() {
        List<InspectionResult> results = new ArrayList<>();
        assertThatThrownBy(() -> new InspectionCompleted(
            null,
            COMPLETED_AT,
            results,
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("inspectorId must not be null");
    }

    @Test
    void shouldRejectNullCompletedAt() {
        assertThatThrownBy(() ->
            new InspectionCompleted(
                INSPECTOR_ID,
                null,
                List.of(RESULT),
                null
            )
        )
            .isInstanceOf(NullPointerException.class)
            .hasMessage("completedAt must not be null");
    }

    @Test
    void shouldRejectNullResults() {
        assertThatThrownBy(() ->
            new InspectionCompleted(
                INSPECTOR_ID,
                COMPLETED_AT,
                null,
                null
            )
        )
            .isInstanceOf(NullPointerException.class)
            .hasMessage("results must not be null");
    }
}
